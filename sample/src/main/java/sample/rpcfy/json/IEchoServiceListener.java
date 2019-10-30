package sample.rpcfy.json;

import rpcfy.annotations.RPCfy;


@RPCfy
public interface IEchoServiceListener {

    default void onUnRegistered(boolean success) {}

    default void onRegistered()  {}

    default void onNoArgMethodCalled()  {}

    default void onEcho(String input)  {}
}
