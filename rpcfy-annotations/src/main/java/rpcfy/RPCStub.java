package rpcfy;


/**
 * Represents a remote stub. This will be implemented by the RPCfy generated Stub classes.
 * <p/>
 * When an interface is marked as {@link rpcfy.annotations.RPCfy}, an {@link RPCStub} for it will
 * be auto generated named as XXXX_JsonRpcStub.
 *
 * @see rpcfy.annotations.RPCfy
 */
public interface RPCStub {

    /**
     * Returns the interface name that this stub implements
     */
    String getStubInterfaceName();

    /**
     * Returns the id of this stub if any
     */
    int getStubId();


    /**
     * Called to deliver the message from the proxy.
     *
     * @param methodId The id of the method being called
     * @param message  The JSON RPC message
     * @return Returns the JSON RPC response
     */
    String onRPCCall(int methodId, String message);

    /**
     * Returns the service that this stub wraps
\     */
    Object getService();
}
