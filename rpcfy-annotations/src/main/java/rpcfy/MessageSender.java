package rpcfy;


import java.io.IOException;

/**
 * Sends the given message to the remote.
 */
public interface MessageSender<T> {

    /**
     * Called to send given message to the other side of RPC.
     *
     * @throws IOException if message could not be sent.
     */
    void sendMessage(T message) throws IOException;
}
