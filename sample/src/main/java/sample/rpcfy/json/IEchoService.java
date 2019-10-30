package sample.rpcfy.json;

import java.util.List;
import java.util.Map;

import rpcfy.annotations.RPCfy;


/**
 * Sample interface for testing
 */
@RPCfy
public interface IEchoService {

    void noArgumentMethod();

    String echoString(String input);

    MyObj echoObject(MyObj input);

    String testNullInput(String input) throws Exception;

    String testNullOutput(String input);

    String testException(String input);

    List<MyObj> testMultipleListParams(List<String> listOfStrings, List<MyObj> listofObjs1, List<MyObj> listofObjs2);

    MyObj[] testMultipleArrayParams(String[] strings, MyObj[] obj1, MyObj[] obj2);

    Map<Long, MyObj> testMultipleMapParams(Map<Integer, String> strings, Map<String, MyObj> obj1, Map<Long, MyObj> obj2);

    IEchoService getEchoService();

    IEchoService getEchoServiceThatReturnsNull();

    void registerListener(IEchoServiceListener listener);

    void unregisterListener(IEchoServiceListener listener);
}
