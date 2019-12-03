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

    /**
     * Set Extra key/value to be send with the messages
     */
    void setRPCfyCustomExtras(Map<String, String> customExtras);

}
