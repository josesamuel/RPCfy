package sample.rpcfy.json;

import rpcfy.annotations.RPCfy;


@RPCfy
public interface IEchoServiceListener {

    void onUnRegistered(boolean success);

    void onRegistered();

    void onNoArgMethodCalled();

    void onEcho(String input);
}
