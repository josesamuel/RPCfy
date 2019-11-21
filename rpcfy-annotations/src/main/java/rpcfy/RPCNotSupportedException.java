package rpcfy;

/**
 * Thrown when a method is called in the {@link RPCProxy} that is marked as not supporting RPC
 */
public class RPCNotSupportedException extends RuntimeException {

    public RPCNotSupportedException() {
        super("Method doesn't support RPC");
    }

    public RPCNotSupportedException(String message) {
        super(message);
    }

}
