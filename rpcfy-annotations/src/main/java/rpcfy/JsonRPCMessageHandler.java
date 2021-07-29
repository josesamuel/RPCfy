package rpcfy;


import rpcfy.json.GsonJsonify;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static final String PREFIX_RELAY_PROPERTY = "custom_";

    private final long REQUEST_TIMEOUT = 120000;
    private MessageSender<String> sender;
    private final Map<String, Map<Integer, RPCStub>> stubMap = new ConcurrentHashMap<>();
    private final Map<Object, RPCStub> stubInstanceMap = new ConcurrentHashMap<>();
    private Map<RPCCallId, RPCCallId> waitingCallers = new ConcurrentHashMap<>();
    private JSONify jsoNify = new GsonJsonify();
    private boolean logEnabled;
    private long requestTimeout = REQUEST_TIMEOUT;
    private long oneWayRequestTimeout = REQUEST_TIMEOUT;
    private Map<String, String> requestExtras;
    private Map<RPCMethodDelegate, Object> delegates = new HashMap<>();
    private Map<RPCMethodDelegate, String> rpcMessage = new HashMap<>();
    private ThreadLocal<Map<String,String>> rpcParameters = new ThreadLocal<>();


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
        if (this.requestExtras != null && requestExtras != null) {
            this.requestExtras.putAll(requestExtras);
        } else {
            this.requestExtras = requestExtras;
        }
    }

    /**
     * Add a key value property to be sent with all the request.
     *
     * @param key   The key of the property
     * @param value The value of the property
     * @param relay Whether the property should be relayed back with the response
     */
    public void addProperty(String key, String value, boolean relay) {
        if (requestExtras == null) {
            requestExtras = new HashMap<>();
        }
        if (relay && !key.startsWith(PREFIX_RELAY_PROPERTY)) {
            key = PREFIX_RELAY_PROPERTY + key;
        }
        requestExtras.put(key, value);
    }

    /**
     * Returns any property that was send as part of the current rpc call within this thread if any.
     */
    public String getProperty(String key) {
        Map<String, String> params = rpcParameters.get();
        String value = null;
        if (params != null) {
            value = params.get(key);
            if (value == null && !key.startsWith(PREFIX_RELAY_PROPERTY)){
                value = params.get(PREFIX_RELAY_PROPERTY + key);
            }
        }
        return value;
    }

    /**
     * Called internally.
     */
    public final void onRPCParameters(Map<String, String> params) {
        if (params == null) {
            rpcParameters.remove();
        } else {
            rpcParameters.set(params);
        }
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
     * Called internally to set the original json message that resulted in a method invocation
     */
    public void setOriginalMessage(RPCMethodDelegate method, String message) {
        if (message == null) {
            rpcMessage.remove(method);
        } else {
            rpcMessage.put(method, message);
        }
    }

    /**
     * Returns the original json message that resulted in the invocation of the given method
     */
    public String getOriginalMessage(RPCMethodDelegate method) {
        return rpcMessage.get(method);
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
            Integer handlerId = jsoNify.fromJSON(message, "r_handler_id", int.class);
            boolean processMessage = (handlerId == null) || (hashCode() == handlerId);

            if (processMessage) {
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

                        JSONify.JObject jsonRPCObject = jsoNify.newJson();
                        jsonRPCObject.put("jsonrpc", "2.0");
                        jsonRPCObject.put("interface", jsoNify.fromJSON(message, "interface", String.class));
                        jsonRPCObject.put("method_id", jsoNify.fromJSON(message, "method_id", int.class));
                        jsonRPCObject.put("id", jsoNify.fromJSON(message, "id", int.class));
                        if (jsoNify.getJSONElement(message, "ins_id") != null) {
                            jsonRPCObject.put("ins_id", jsoNify.fromJSON(message, "ins_id", int.class));
                        }
                        JSONify.JObject jsonErrorObject = jsoNify.newJson();
                        jsonErrorObject.put("code", -32001);
                        jsonRPCObject.put("error", jsonErrorObject);

                        sendMessage(jsonRPCObject.toJson());
                    }
                } else {
                    //result call
                    int methodId = jsoNify.fromJSON(message, "method_id", int.class);
                    int callId = jsoNify.fromJSON(message, "id", int.class);
                    RPCCallId rpcCallId;
                    if (jsoNify.getJSONElement(message, "ins_id") != null) {
                        int instanceId = jsoNify.fromJSON(message, "ins_id", int.class);
                        rpcCallId = new RPCCallId(stubInterface, methodId, callId, instanceId);
                    } else {
                        rpcCallId = new RPCCallId(stubInterface, methodId, callId);
                    }
                    RPCCallId waitingReq = waitingCallers.get(rpcCallId);
                    if (waitingReq == null && !rpcCallId.compareInstanceId) {
                        for (RPCCallId req : waitingCallers.keySet()) {
                            if (req.equals(rpcCallId)) {
                                waitingReq = req;
                                break;
                            }
                        }
                    }
                    if (waitingReq != null) {
                        synchronized (waitingReq) {
                            waitingReq.result = message;
                            waitingReq.notifyAll();
                            if (waitingReq.proxyInstance != null) {
                                waitingReq.proxyInstance.onRPCOneWayResult(message);
                                waitingCallers.remove(waitingReq);
                            }
                        }
                    } else {
                        String result = jsoNify.fromJSON(message, "result", String.class);
                        if (result != null && !result.isEmpty()) {
                            loge("No Waiting request found for response " + message);
                        }
                    }
                }
            } else {
                logv("Ignoring message to different handler " + getMessageEntries(message));
            }
        } catch (Exception ex) {
            loge(ex);
        }
        rpcParameters.remove();
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
    public void sendMessage(String message, String interfaceName, int methodID, int rpcID, int proxyInstanceId, RPCProxy proxy) {
        clearTimedOutOneWayRequests();
        final RPCCallId rpcCallId = new RPCCallId(interfaceName, methodID, rpcID, proxyInstanceId);
        rpcCallId.proxyInstance = proxy;
        rpcCallId.requestTimeOut = oneWayRequestTimeout;
        logv("Sending " + message + " , " + rpcCallId);
        try {
            waitingCallers.put(rpcCallId, rpcCallId);
            sender.sendMessage(message);
        } catch (Exception ex) {
            loge(ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns a map of entries in the given message
     */
    public Map<String, String> getMessageEntries(String message) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("this", "" + hashCode());
        try {
            JSONify.JElement json = jsoNify.fromJson(message);
            for (String key : json.getKeys()) {
                entries.put(key, json.getJsonValue(key));
            }
        } catch (Exception ex) {
            loge(ex);
        }
        return entries;
    }

    /**
     * Clear out any pending timedout one way requests
     */
    private void clearTimedOutOneWayRequests () {
        for (RPCCallId req : waitingCallers.keySet()) {
            if (req.proxyInstance != null && req.hasTimedOut()) {
                waitingCallers.remove(req);
//                loge("One way call timed out for  " + req);
//                JSONify.JObject jsonRPCObject = jsoNify.newJson();
//                jsonRPCObject.put("jsonrpc", "2.0");
//                jsonRPCObject.put("interface", req.interfaceName);
//                jsonRPCObject.put("method_id", req.methodId);
//                jsonRPCObject.put("id", req.callId);
//                jsonRPCObject.put("ins_id", req.instanceId);
//                JSONify.JObject jsonErrorObject = jsoNify.newJson();
//                jsonErrorObject.put("code", -32001);
//                jsonRPCObject.put("error", jsonErrorObject);
//                req.proxyInstance.onRPCOneWayResult(jsonRPCObject.toJson());
            }
        }
    }

    /**
     * Used internally by generated Proxy/Stub to send the message using the {@link MessageSender} associated with this
     */
    public String sendMessageAndWaitForResponse(String message, String interfaceName, int methodID, int rpcID, int proxyInstanceId) {
        final RPCCallId rpcCallId = new RPCCallId(interfaceName, methodID, rpcID, proxyInstanceId);
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
     * Cancels all pending requests, and clears all the stubs registered with this.
     */
    public void clear() {
        for (RPCCallId waitingCall : new ArrayList<>(waitingCallers.keySet())) {
            synchronized (waitingCall) {
                waitingCall.notifyAll();
            }
        }
        waitingCallers.clear();
        stubMap.clear();
        stubInstanceMap.clear();
        requestExtras = null;
        delegates.clear();
        rpcMessage.clear();
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
     * Sets the request timeout for non blocking requests.
     * Default timeout is 2 minutes.
     * The listener will only be triggered on any next one way call.
     * @see RPCProxy#setRPCRemoteListener(RPCProxy.RemoteListener)
     */
    public void setOneWayRequestTimeout(long requestTimeout) {
        this.oneWayRequestTimeout = requestTimeout;
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
        System.err.println(message);
    }

    /**
     * Represents an request that is waiting for a response.
     */
    private static class RPCCallId {
        private String interfaceName;
        private int methodId;
        private int callId;
        private int instanceId;
        private boolean compareInstanceId;
        private String result;
        private RPCProxy proxyInstance;
        private long requestTime = System.currentTimeMillis();
        private long requestTimeOut = 60000;

        RPCCallId(String interfaceName, int methodId, int callId) {
            this.interfaceName = interfaceName;
            this.methodId = methodId;
            this.callId = callId;
        }

        RPCCallId(String interfaceName, int methodId, int callId, int instanceId) {
            this(interfaceName, methodId, callId);
            this.instanceId = instanceId;
            this.compareInstanceId = true;
        }

        @Override
        public int hashCode() {
            return interfaceName.hashCode() * 67 + methodId * 89 + callId * 97 + instanceId * 71;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RPCCallId) {
                RPCCallId other = (RPCCallId) o;
                return interfaceName.equals(other.interfaceName)
                        && methodId == other.methodId
                        && callId == other.callId
                        && (!compareInstanceId || !other.compareInstanceId || (instanceId == other.instanceId));
            }
            return false;
        }

        public boolean hasTimedOut() {
            return (System.currentTimeMillis() - requestTime) >= requestTimeOut;
        }

        @Override
        public String toString() {
            return interfaceName + " " + methodId + " " + callId + " " + instanceId;
        }
    }


}
