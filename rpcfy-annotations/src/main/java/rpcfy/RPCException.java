package rpcfy;

/**
 * Exception that represents something went wrong during an RPC Call
 */
public class RPCException extends RuntimeException {

    /**
     * Represents the type for the exception
     */
    public enum Type {
        REMOTE_STUB_NOT_FOUND,
        REMOTE_EXCEPTION;
    }

    private Type type;

    public RPCException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
