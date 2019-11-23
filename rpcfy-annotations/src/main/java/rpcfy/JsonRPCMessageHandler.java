package rpcfy;


import rpcfy.json.GsonJsonify;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Takes care of sending the JSONRPC messages using the provided {@link MessageSender}, and
 * handles the incoming JSON RPC messages when delivered to {@link #onMessage(String)}.
 * <p/>
 *
 * @see rpcfy.annotations.RPCfy
 */
public final class JsonRPCMessageHandler implements MessageReceiver<String> {

    private final long REQUEST_TIMEOUT = 120000;
    private MessageSender<String> sender;
    private final Map<String, Map<Integer, RPCStub>> stubMap = new ConcurrentHashMap<>();
    private final Map<Object, RPCStub> stubInstanceMap = new ConcurrentHashMap<>();
    private Map<RPCCallId, RPCCallId> waitingCallers = new ConcurrentHashMap<>();
    private JSONify jsoNify = new GsonJsonify();
    private boolean logEnabled;
    private long requestTimeout = REQUEST_TIMEOUT;
    private Map<String, String> requestExtras;
    private Map<RPCMethodDelegate, Object> delegates = new HashMap<>();

    /**
     * Creates an instance of {@link JsonRPCMessageHandler}.
     * <p/>
     * Use a single instance for client side, and another single instance at server side.
     *
     * @param messageSender The generated messages will be send using this.
     */
    public JsonRPCMessageHandler(MessageSender<String> messageSender) {
        if (messageSender != null) {
            this.sender = messageSender;
        } else {
            throw new RuntimeException("MessageSender cannot be null");
        }
    }

    /**
     * Enable/disable debug loging
     */
    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    /**
     * Put any extra json key/value to be send with the request message.
     * <p>
     * Any key starting with custom_ will also be relayed back in the response
     */
    public void setExtra(Map<String, String> requestExtras) {
        this.requestExtras = requestExtras;
    }

    /**
     * Adds a delegate for a given method in a interface.
     * If this is added to the proxy side, the method will be called on the given instance without proxying to stub.
     * If this is added to the stub side, the method will be called on the given instance instead of the implementation
     * that the stub is wrapping
     */
    public void addMethodDelegate(RPCMethodDelegate methodDelegate) {
        delegates.put(methodDelegate, methodDelegate.getDelegate());
    }

    /**
     * Returns any method delegate set for the given method
     */
    public Object getMethodDelegate(RPCMethodDelegate delegate) {
        return delegates.get(delegate);
    }

    /**
     * Return any extras set
     */
    public Map<String, String> getExtras() {
        return requestExtras;
    }

    @Override
    public void onMessage(String message) {
        try {
            logv("onMessage " + message);
            String stubInterface = jsoNify.fromJSON(message, "interface", String.class);
            String methodName = jsoNify.fromJSON(message, "method", String.class);

            if (methodName != null) {
                RPCStub stub = null;
                Map<Integer, RPCStub> stubs = null;

                if (stubInterface != null) {
                    stubs = stubMap.get(stubInterface);

                    if (stubs != null) {
                        Integer stubId = jsoNify.fromJSON(message, "remote_id", int.class);
                        if (stubId == null) {
                            if (!stubs.isEmpty()) {
                                stub = stubs.values().iterator().next();
                            }
                        } else {
                            stub = stubs.get(stubId);
                        }
                    }
                }

                if (stub != null) {
                    int methodId = jsoNify.fromJSON(message, "method_id", int.class);
                    sendMessage(stub.onRPCCall(methodId, message));
                } else {
                    loge("No Matching Stub found to serve the request " + message + " " + stubMap);
                }
            } else {
                //result call
                int methodId = jsoNify.fromJSON(message, "method_id", int.class);
                int callId = jsoNify.fromJSON(message, "id", int.class);
                RPCCallId rpcCallId = new RPCCallId(stubInterface, methodId, callId);
                RPCCallId waitingReq = waitingCallers.get(rpcCallId);
                if (waitingReq != null) {
                    synchronized (waitingReq) {
                        waitingReq.result = message;
                        waitingReq.notifyAll();
                    }
                } else {
                    String result = jsoNify.fromJSON(message, "result", String.class);
                    if (result != null && !result.isEmpty()) {
                        logv("No Waiting request found for response " + message);
                    }
                }
            }
        } catch (Exception ex) {
            loge(ex);
        }


    }

    /**
     * Used internally by generated Proxy/Stub to send the message using the {@link MessageSender} associated with this
     */
    public void sendMessage(String message) {
        logv("Sending " + message);
        try {
            sender.sendMessage(message);
        } catch (Exception ex) {
            loge(ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Used internally by generated Proxy/Stub to send the message using the {@link MessageSender} associated with this
     */
    public String sendMessageAndWaitForResponse(String message, String interfaceName, int methodID, int rpcID) {
        final RPCCallId rpcCallId = new RPCCallId(interfaceName, methodID, rpcID);
        logv("Sending and waiting " + message + " , " + rpcCallId);
        try {
            waitingCallers.put(rpcCallId, rpcCallId);
            synchronized (rpcCallId) {
                sender.sendMessage(message);
                rpcCallId.wait(requestTimeout);
                waitingCallers.remove(rpcCallId);
                if (rpcCallId.result == null) {
                    throw new RuntimeException("Request timed out");
                }
            }
        } catch (Exception ex) {
            loge(ex);
            waitingCallers.remove(rpcCallId);

            JSONify.JObject jsonRPCObject = jsoNify.newJson();
            jsonRPCObject.put("jsonrpc", "2.0");
            JSONify.JObject jsonErrorObject = jsoNify.newJson();
            jsonErrorObject.put("code", -32000);
            jsonErrorObject.put("message", ex.getMessage());
            jsonErrorObject.put("exception", ex.getClass().getName());
            jsonRPCObject.put("error", jsonErrorObject);
            rpcCallId.result = jsonRPCObject.toJson();
            loge(ex.getMessage());
        }
        return rpcCallId.result;
    }


    /**
     * Register a {@link RPCStub} with this handler, so that any message intended
     * for the stub can be delivered.
     */
    public void registerStub(RPCStub stub) {
        if (stub != null) {
            String stubInterface = stub.getStubInterfaceName();
            Map<Integer, RPCStub> stubs = stubMap.get(stubInterface);
            if (stubs == null) {
                stubs = new ConcurrentHashMap<>();
                stubMap.put(stubInterface, stubs);
            }
            stubs.put(stub.getStubId(), stub);
            registerStub(stub.getService(), stub);
        }
    }

    /**
     * Register a stub associated with an instance
     */
    private void registerStub(Object instance, RPCStub stub) {
        synchronized (stubInstanceMap) {
            stubInstanceMap.put(instance, stub);
        }
    }


    /**
     * Clears any previously registered {@link RPCStub}
     *
     * @see #registerStub(RPCStub)
     */
    public void clearStub(RPCStub stub) {
        for (Map<Integer, RPCStub> stubs : stubMap.values()) {
            stubs.remove(stub.getStubId());
            stubInstanceMap.remove(stub.getService());
        }
    }

    /**
     * Clears all the stubs registered with this.
     */
    public void clear() {
        stubMap.clear();
        stubInstanceMap.clear();
        requestExtras = null;
        delegates.clear();
    }

    /**
     * Clears any stub created for the given instance
     *
     * @param object
     */
    public void clearStub(Object object) {
        if (object != null) {
            synchronized (stubInstanceMap) {
                RPCStub stub = stubInstanceMap.get(object);
                if (stub != null) {
                    clearStub(stub);
                    stubInstanceMap.remove(object);
                }
            }
        }
    }

    /**
     * Returns any stub assosiated with given object
     */
    public RPCStub getStub(Object object) {
        if (object != null) {
            synchronized (stubInstanceMap) {
                return stubInstanceMap.get(object);
            }
        }
        return null;
    }

    /**
     * Sets the request timeout for blocking requests.
     * Default timeout is 2 minutes
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /**
     * Internal use to convert exception
     */
    public static <T> T asException(String exceptionName, String exceptionMessage, Class<T> exception) {
        if (exception.getName().equals(exceptionName)) {

            try {
                Constructor<T> constructorWithMessage = exception.getConstructor(String.class);
                return constructorWithMessage.newInstance(exceptionMessage);
            } catch (Throwable ignored) {
            }

            try {
                Constructor<T> constructorWithNoMessage = exception.getConstructor();
                return constructorWithNoMessage.newInstance();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void logv(String message) {
        if (logEnabled) {
            System.out.println("RPCfy: " + message);
        }
    }

    private void loge(Exception exception) {
        exception.printStackTrace();
    }

    private void loge(String message) {
        System.err.println("");
    }

    /**
     * Represents an request that is waiting for a response.
     */
    private static class RPCCallId {
        private String interfaceName;
        private int methodId;
        private int callId;
        private String result;

        RPCCallId(String interfaceNamem, int methodId, int callId) {
            this.interfaceName = interfaceNamem;
            this.methodId = methodId;
            this.callId = callId;
        }

        @Override
        public int hashCode() {
            return interfaceName.hashCode() * 67 + methodId * 89 + callId * 97;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RPCCallId) {
                RPCCallId other = (RPCCallId) o;
                return interfaceName.equals(other.interfaceName) && methodId == other.methodId && callId == other.callId;
            }
            return false;
        }

        @Override
        public String toString() {
            return interfaceName + " " + methodId + " " + callId;
        }
    }


}
