package rpcfy;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rpcfy.json.GsonJsonify;

/**
 * Takes care of sending the JSONRPC messages using the provided {@link MessageSender}, and
 * handles the incoming JSON RPC messages when delivered to {@link #onMessage(String)}.
 * <p/>
 *
 * @see rpcfy.annotations.RPCfy
 */
public final class JsonRPCMessageHandler implements MessageReceiver<String> {


    private MessageSender<String> sender;
    private Map<String, Map<Integer, RPCStub>> stubMap = new ConcurrentHashMap<>();
    private Map<RPCCallId, RPCCallId> waitingCallers = new ConcurrentHashMap<>();
    private JSONify jsoNify = new GsonJsonify();
    private boolean logEnabled;

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
            loge("Exception while parsing input message");
            ex.printStackTrace();
        }


    }

    /**
     * Used internally by generated Proxy/Stub to send the message using the {@link MessageSender} associated with this
     */
    public void sendMessage(String message) {
        logv("Sending " + message);
        sender.sendMessage(message);
    }

    public String sendMessageAndWaitForResponse(String message, String interfaceName, int methodID, int rpcID) {
        final RPCCallId rpcCallId = new RPCCallId(interfaceName, methodID, rpcID);
        logv("Sending and waiting " + message + " , " + rpcCallId);
        try {
            waitingCallers.put(rpcCallId, rpcCallId);
            synchronized (rpcCallId) {
                sender.sendMessage(message);
                rpcCallId.wait();
                waitingCallers.remove(rpcCallId);
            }

        } catch (Exception ex) {
            loge(ex.getMessage());
        }
        return rpcCallId.result;
    }


    /**
     * Register a {@link RPCStub} with this handler, so that any message intended
     * for the stub can be delivered.
     */
    public void registerStub(RPCStub stub) {
        String stubInterface = stub.getStubInterfaceName();
        Map<Integer, RPCStub> stubs = stubMap.get(stubInterface);
        if (stubs == null) {
            stubs = new ConcurrentHashMap<>();
            stubMap.put(stubInterface, stubs);
        }
        stubs.put(stub.getStubId(), stub);
    }

    /**
     * Clears any previously registered {@link RPCStub}
     *
     * @see #registerStub(RPCStub)
     */
    public void clearStub(RPCStub stub) {
        for (Map<Integer, RPCStub> stubs : stubMap.values()) {
            stubs.remove(stub.getStubId());
        }
    }

    /**
     * Clears all the stubs registered with this.
     */
    public void clear() {
        stubMap.clear();
    }

    private void logv(String message) {
        if (logEnabled) {
            System.out.println("RPCfy: " + message);
        }
    }

    private void loge(String message) {
        System.err.println("RPCfy: " + message);
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
