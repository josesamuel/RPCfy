package rpcfy;

/**
 * Represents a delegate to be called for a method in an interface is marked as {@link rpcfy.annotations.RPCfy}.
 * <p>
 * Use this to selectively delegate a method either at the proxy side or at stub side
 *
 * @param <T> The interface that is marked as {@link rpcfy.annotations.RPCfy}
 *
 * @see JsonRPCMessageHandler#addMethodDelegate(RPCMethodDelegate)
 */
public class RPCMethodDelegate<T> {
    private Class<T> interfaceClass;
    private T delegate;
    private int methodId;

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

    @Override
    public int hashCode() {
        return 13 * interfaceClass.hashCode() + 19 * methodId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RPCMethodDelegate) {
            return ((RPCMethodDelegate) obj).interfaceClass.equals(interfaceClass) && ((RPCMethodDelegate) obj).methodId == methodId;
        }
        return false;
    }

    /**
     * Returns the delegated instance
     */
    T getDelegate() {
        return delegate;
    }
}
