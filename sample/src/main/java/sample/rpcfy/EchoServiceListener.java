package sample.rpcfy;

import rpcfy.annotations.RPCfy;


@RPCfy
public interface EchoServiceListener {

    default void onUnRegistered(boolean success) {}

    default void onRegistered()  {}

    default void onNoArgMethodCalled()  {}

    default void onEcho(String input)  {}
}
