package rpcfy;


/**
 * Sends the given message to the remote.
 */
public interface MessageSender<T> {

    /**
     * Called to send given message to the other side of RPC.
     */
    void sendMessage(T message);
}
