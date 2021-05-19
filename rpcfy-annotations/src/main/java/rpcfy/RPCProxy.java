package rpcfy;


import java.util.Map;

/**
 * Represents a proxy side of an interface that is RPCfy'ed.
 * <p/>
 * <p/>
 * When an interface is marked as {@link rpcfy.annotations.RPCfy}, an {@link RPCProxy} for it will
 * be auto generated named as XXXX_JsonRpcProxy
 *
 * @see rpcfy.annotations.RPCfy
 */
public interface RPCProxy {

    interface RemoteListener {
        /**
         * Called to notify an rpc call failure happened for a one way call (method with void return)
         *
         * @param proxy The instance of proxy on which the call failed.
         * @param methodID  Which method failed
         * @param exception Exception of the failure
         */
        void onRPCFailed(RPCProxy proxy, int methodID, RPCException exception);
    }

    /**
     * Set Extra key/value to be send with the messages
     */
    void setRPCfyCustomExtras(Map<String, String> customExtras);

    /**
     * Sets/Resets a listener to listen for any oneway call failures
     */
    void setRPCRemoteListener(RemoteListener remoteListener);

    void onRPCOneWayResult(String result);

}
