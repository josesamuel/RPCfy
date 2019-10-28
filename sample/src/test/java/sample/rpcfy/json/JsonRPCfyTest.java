package sample.rpcfy.json;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import rpcfy.JsonRPCMessageHandler;
import rpcfy.MessageSender;


/**
 * To test, creating 2 threads that reads from respective queues.
 * Messages send at "server (MessageSender)" as simply put to the "client" queue
 * Messages send at "client (MessageSender)" as simply put to the "server" queue
 */
public class JsonRPCfyTest {

    //**********************************************************************************
    //Simulating a server
    private Thread serverThread;
    //Queue that server thread reads from
    private LinkedBlockingQueue<String> serverQueue = new LinkedBlockingQueue<>();
    //Handler for server
    private JsonRPCMessageHandler serverHandler;
    //server handler uses this to send message, which simply puts that in client queue.
    //In real application, this message will be sent across network/process to client side
    private MessageSender<String> serverMessageSender = new MessageSender<String>() {
        @Override
        public void sendMessage(String message) {
            try {
                clientQueue.put(message);
            } catch (Exception e) {
            }
        }
    };
    //**********************************************************************************


    //**********************************************************************************
    //Simulating a client
    private Thread clientThread;
    //Queue that client thread reads from
    private LinkedBlockingQueue<String> clientQueue = new LinkedBlockingQueue<>();
    //Handler for client
    private JsonRPCMessageHandler clientHandler;
    //server handler uses this to send message, which simply puts that in server queue.
    //In real application, this message will be sent across network/process to server side
    private MessageSender<String> clientMessageSender = new MessageSender<String>() {
        @Override
        public void sendMessage(String message) {
            try {
                serverQueue.put(message);
            } catch (Exception e) {
            }
        }
    };
    //**********************************************************************************


    private boolean running;
    private IEchoService echoService;
    private boolean DEBUG = false;


    @Before
    public void setup() {
        running = true;
        serverHandler = new JsonRPCMessageHandler(serverMessageSender);
        //creates the stub for IEchoService wrapping the real implementation and register with handler
        serverHandler.registerStub(new IEchoService_JsonRpcStub(serverHandler, new IEchoServiceImpl()));
        serverThread = new Thread() {
            @Override
            public void run() {
                while (running) {
                    try {
                        serverHandler.onMessage(serverQueue.take());
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("Server exiting");
            }
        };
        serverThread.start();


        clientHandler = new JsonRPCMessageHandler(clientMessageSender);
        clientThread = new Thread() {
            @Override
            public void run() {
                while (running) {
                    try {
                        clientHandler.onMessage(clientQueue.take());
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("Client exiting");
            }
        };
        //Create the proxy for the IEchoService
        echoService = new IEchoService_JsonRpcProxy(clientHandler);
        clientThread.start();
        clientHandler.setLogEnabled(DEBUG);
        serverHandler.setLogEnabled(DEBUG);
    }

    @After
    public void destroy() {
        running = false;
        serverThread.interrupt();
        clientThread.interrupt();
        serverHandler.clear();
        clientHandler.clear();
    }

    @Test
    public void testNoArgCall() throws Exception {
        final CountDownLatch registerLatch = new CountDownLatch(1);
        final CountDownLatch unregisterLatch = new CountDownLatch(1);
        final CountDownLatch callbackLatch = new CountDownLatch(1);
        IEchoServiceListener listener = new IEchoServiceListener() {

            @Override
            public void onUnRegistered(boolean success) {
                unregisterLatch.countDown();
            }

            @Override
            public void onRegistered() {
                registerLatch.countDown();
            }

            @Override
            public void onNoArgMethodCalled() {
                callbackLatch.countDown();
            }

            @Override
            public void onEcho(String input) {

            }
        };

        echoService.registerListener(listener);
        registerLatch.await();
        System.out.println("Registered listener");

        echoService.noArgumentMethod();
        callbackLatch.await();
        System.out.println("noArgCallback complete");

        echoService.unregisterListener(listener);
        unregisterLatch.await();
        System.out.println("unregistered listener");
    }

    @Test
    public void testEcho() throws Exception {
        String response = echoService.echoString("World");
        Assert.assertEquals("WorldResult", response);

        response = echoService.echoString(null);
        Assert.assertNull(response);
    }

    @Test
    public void testObject() throws Exception {
        MyObj input = new MyObj("Foo", 1);
        MyObj objRes = echoService.echoObject(input);
        Assert.assertEquals(input, objRes);

        objRes = echoService.echoObject(null);
        Assert.assertNull(objRes);
    }

    @Test(expected = RuntimeException.class)
    public void testException() {
        echoService.testException("hello");
        Assert.fail();
    }

    @Test
    public void testListener() throws Exception {
        final CountDownLatch registerLatch1 = new CountDownLatch(1);
        final CountDownLatch unregisterLatch1 = new CountDownLatch(1);
        final CountDownLatch callbackLatch1 = new CountDownLatch(1);

        final String echoString1 = "Hello";
        final String echoString2 = "World";

        IEchoServiceListener listener = new IEchoServiceListener() {
            boolean listenerUnregistered;

            @Override
            public void onUnRegistered(boolean success) {
                listenerUnregistered = true;
                unregisterLatch1.countDown();
            }

            @Override
            public void onRegistered() {
                registerLatch1.countDown();
            }

            @Override
            public void onNoArgMethodCalled() {

            }

            @Override
            public void onEcho(String input) {
                Assert.assertFalse(listenerUnregistered);
                Assert.assertEquals(echoString1, input);
                callbackLatch1.countDown();
            }

        };

        final CountDownLatch registerLatch2 = new CountDownLatch(1);
        final CountDownLatch unregisterLatch2 = new CountDownLatch(1);
        final CountDownLatch callbackLatch2 = new CountDownLatch(1);

        IEchoServiceListener listener2 = new IEchoServiceListener() {
            boolean listenerUnregistered;

            @Override
            public void onUnRegistered(boolean success) {
                listenerUnregistered = true;
                unregisterLatch2.countDown();
            }

            @Override
            public void onRegistered() {
                registerLatch2.countDown();
            }

            @Override
            public void onNoArgMethodCalled() {

            }

            @Override
            public void onEcho(String input) {
                Assert.assertFalse(listenerUnregistered);
                Assert.assertEquals(echoString2, input);
                callbackLatch2.countDown();
            }
        };

        echoService.registerListener(listener);
        echoService.registerListener(listener2);

        registerLatch1.await();
        registerLatch2.await();

        echoService.echoString(echoString1);
        echoService.echoObject(new MyObj(echoString2, 0));

        callbackLatch1.await();
        callbackLatch2.await();


        echoService.unregisterListener(listener2);
        unregisterLatch2.await();

        echoService.echoObject(new MyObj(echoString2, 0));
        Thread.sleep(50);

        echoService.unregisterListener(listener);
        unregisterLatch1.await();

        echoService.echoString(echoString1);
        Thread.sleep(50);
    }


    @Test
    public void testList() throws Exception {
        List<String> strings = new ArrayList<>();
        strings.add("1");
        strings.add("2");
        List<MyObj> obj1 = new ArrayList<>();
        obj1.add(new MyObj("A", 1));
        obj1.add(new MyObj("B", 2));
        List<MyObj> obj2 = new ArrayList<>();
        obj2.add(new MyObj("C", 10));
        obj2.add(new MyObj("D", 20));

        List<MyObj> result = echoService.testMultipleListParams(strings, obj1, obj2);
        System.out.println("Result list " + result);
        Assert.assertEquals(obj1, result);

        result = echoService.testMultipleListParams(strings, null, obj2);
        System.out.println("Result list " + result);
        Assert.assertNull(result);
    }

    @Test
    public void testArray() throws Exception {
        String[] strings = new String[]{"1", "2"};
        MyObj[] obj1 = new MyObj[]{new MyObj("A", 1), new MyObj("B", 2)};
        MyObj[] obj2 = new MyObj[]{new MyObj("C", 10), new MyObj("D", 20)};

        MyObj[] result = echoService.testMultipleArrayParams(strings, obj1, obj2);
        Assert.assertEquals(obj2.length, result.length);
        for (int i = 0; i < obj2.length; i++) {
            Assert.assertEquals(obj2[i], result[i]);
        }

        result = echoService.testMultipleArrayParams(strings, obj1, null);
        Assert.assertNull(result);
    }


    @Test
    public void testMap() throws Exception {
        Map<Integer, String> map1 = new HashMap<>();
        map1.put(1, "1");
        map1.put(2, "2");

        Map<String, MyObj> map2 = new HashMap<>();
        map2.put("1", new MyObj("A", 1));
        map2.put("2", new MyObj("B", 2));

        Map<Long, MyObj> map3 = new HashMap<>();
        map3.put(1L, new MyObj("AB", 10));
        map3.put(2L, new MyObj("BB", 20));


        Map<Long, MyObj> resultMap = echoService.testMultipleMapParams(map1, map2, map3);
        Assert.assertEquals(map3, resultMap);

        resultMap = echoService.testMultipleMapParams(map1, map2, null);
        Assert.assertNull(resultMap);
    }

}
