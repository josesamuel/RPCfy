package rpcfy;


/**
 * Processes a given message of given type.
 * <p/>
 * {@link JsonRPCMessageHandler} is a {@link MessageReceiver} for String type
 *
 * @see JsonRPCMessageHandler
 */
public interface MessageReceiver<T> {

    /**
     * Called to processes given message
     */
    void onMessage(T message);
}
