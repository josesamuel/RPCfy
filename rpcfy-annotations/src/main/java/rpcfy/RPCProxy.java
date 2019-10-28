package rpcfy;


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
     * Destroys any stub created while sending the given object through this proxy.
     */
    void destroyStub(Object stub);
}
