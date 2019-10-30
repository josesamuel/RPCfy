package sample.rpcfy.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sample.rpcfy.json.IEchoService;
import sample.rpcfy.json.IEchoServiceListener;
import sample.rpcfy.json.MyObj;


public class IEchoServiceImpl implements IEchoService {

    private List<IEchoServiceListener> listeners = new ArrayList<>();

    @Override
    public void noArgumentMethod() {
        if(listeners.size() >= 1) {
            listeners.get(0).onNoArgMethodCalled();
        }
    }

    @Override
    public String echoString(String input) {
        if(listeners.size() >= 1) {
            listeners.get(0).onEcho(input);
        }
        return input != null ? input + "Result" : null;
    }

    @Override
    public MyObj echoObject(MyObj input) {
        System.out.println("echoObject " + input);
        if (input != null) {
            System.out.println("echoObject " + input.getName());
        }
        if(listeners.size() >= 2) {
            listeners.get(1).onEcho(input.getName());
        }
        return input;
    }



    @Override
    public String testNullInput(String input) throws Exception {
        return "Got Null" + input;
    }

    @Override
    public String testNullOutput(String input) {
        return null;
    }

    @Override
    public String testException(String input) {
        throw new NullPointerException("Null");
    }

    @Override
    public List<MyObj> testMultipleListParams(List<String> listOfStrings, List<MyObj> listofObjs1, List<MyObj> listofObjs2) {
        System.out.println("testMultipleParams : " + listOfStrings + " " + listofObjs1 + " " + listofObjs2);
        return listofObjs1;
    }

    @Override
    public MyObj[] testMultipleArrayParams(String[] strings, MyObj[] obj1, MyObj[] obj2) {
        System.out.println("testMultipleArrayParams : " + strings + " " + obj1 + " " + obj2);
        if (strings != null){
            for(String s:strings){
                System.out.println(s);
            }
        }
        if (obj1 != null){
            for(MyObj s:obj1){
                System.out.println(s);
            }
        }
        if (obj2 != null){
            for(MyObj s:obj2){
                System.out.println(s);
            }
        }

        return obj2;
    }

    @Override
    public Map<Long, MyObj> testMultipleMapParams(Map<Integer, String> strings, Map<String, MyObj> obj1, Map<Long, MyObj> obj2){
        System.out.println("testMultipleMapParams : " + strings + " " + obj1 + " " + obj2);
        return obj2;
    }

    @Override
    public IEchoService getEchoService() {
        return this;
    }

    @Override
    public IEchoService getEchoServiceThatReturnsNull() {
        return null;
    }

    @Override
    public void registerListener(IEchoServiceListener listener) {
        listeners.add(listener);
        System.out.println("Remaining listener count " + listeners.size());
        listener.onRegistered();
    }

    @Override
    public void unregisterListener(IEchoServiceListener listener) {
        boolean removed = listeners.remove(listener);
        System.out.println("Remaining listener count " + listeners.size());
        listener.onUnRegistered(removed);
    }
}
