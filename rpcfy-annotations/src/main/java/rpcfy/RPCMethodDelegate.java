package rpcfy;

/**
 * Represents a delegate to be called for a method in an interface is marked as {@link rpcfy.annotations.RPCfy}.
 * <p>
 * Use this to selectively delegate a method either at the proxy side or at stub side
 * <p>
 * If the delegated method throws {@link DelegateIgnoreException}, then the normal RPC method call will be attempted.
 *
 * @param <T> The interface that is marked as {@link rpcfy.annotations.RPCfy}
 * @see JsonRPCMessageHandler#addMethodDelegate(RPCMethodDelegate)
 */
public class RPCMethodDelegate<T> {
    private Class<T> interfaceClass;
    private T delegate;
    private int methodId;
    private Integer instanceId;

    /**
     * Initialize this instance with the interface class and method id
     *
     * @param interfaceClass The class of interface.
     * @param methodId       Method id, defined in the generated Proxy class.
     * @param delegate       The instance to which this method needs to be delegated.
     */
    public RPCMethodDelegate(Class<T> interfaceClass, int methodId, T delegate) {
        this.interfaceClass = interfaceClass;
        this.methodId = methodId;
        this.delegate = delegate;
    }

    /**
     * Sets the id of the instance on which this is called if any
     */
    public void setInstanceId(Integer instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public int hashCode() {
        return 13 * interfaceClass.hashCode() + 19 * methodId + (instanceId != null ? instanceId : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RPCMethodDelegate) {
            return ((RPCMethodDelegate) obj).interfaceClass.equals(interfaceClass)
                    && ((RPCMethodDelegate) obj).methodId == methodId
                    && isSameInstance(((RPCMethodDelegate) obj).instanceId);
        }
        return false;
    }

    private boolean isSameInstance(Integer otherInstanceId) {
        if (instanceId == null) {
            return (otherInstanceId == null);
        } else {
            if (otherInstanceId != null) {
                return instanceId.equals(otherInstanceId);
            } else {
                return false;
            }
        }

    }

    /**
     * Returns the delegated instance
     */
    T getDelegate() {
        return delegate;
    }

    /**
     * An exception that can be thrown from any delegated method so that the normal rpc call can instead be attempted.
     */
    public static class DelegateIgnoreException extends RuntimeException {
    }
}
