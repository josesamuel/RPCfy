package sample.rpcfy

import junit.framework.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import rpcfy.*
import rpcfy.RPCProxy.RemoteListener
import rpcfy.json.GsonJsonify
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap
import kotlin.concurrent.thread


/**
 * To test, creating 2 threads that reads from respective queues.
 * Messages send at "server (MessageSender)" as simply put to the "client" queue
 * Messages send at "client (MessageSender)" as simply put to the "server" queue
 */
class JsonRPCfyTest {

    private var simulateMessageFailure = false
    private var simulateCustomJsonEntries = false
    private var simulateCustomJsonEntriesReturnedMessage: String? = ""

    //**********************************************************************************
    //Simulating a server
    private lateinit var serverThread: Thread

    //Queue that server thread reads from
    private val serverQueue = LinkedBlockingQueue<String>()

    //Handler for server
    private lateinit var serverHandler: JsonRPCMessageHandler

    private var serverHandler2: JsonRPCMessageHandler? = null

    //server handler uses this to send message, which simply puts that in client queue.
    //In real application, this message will be sent across network/process to client side
    private val serverMessageSender = MessageSender<String> { message ->
        println(" 1R > " + serverHandler.getMessageEntries(message))
        try {
            clientQueue.put(message)
        } catch (e: Exception) {
        }
    }
    //**********************************************************************************


    //**********************************************************************************
    //Simulating a client
    private lateinit var clientThread: Thread

    //Queue that client thread reads from
    private val clientQueue = LinkedBlockingQueue<String>()

    //Handler for client
    private lateinit var clientHandler: JsonRPCMessageHandler

    private var clientHandler2: JsonRPCMessageHandler? = null

    //server handler uses this to send message, which simply puts that in server queue.
    //In real application, this message will be sent across network/process to server side
    private val clientMessageSender = MessageSender<String> { message ->
        if (simulateMessageFailure) {
            throw IOException("Unable to send message")
        }
        println(" 1 > " + clientHandler.getMessageEntries(message))
        try {
            serverQueue.put(message)
        } catch (e: Exception) {
        }
    }
    //**********************************************************************************


    private var running: Boolean = false
    private lateinit var echoService: EchoService
    private val DEBUG = true
    private var throwExceptionFromDispatch = false

    private var messageInterceptor: (() -> Unit)? = null


    @Before
    fun setup() {
        throwExceptionFromDispatch = false
        simulateMessageFailure = false
        simulateCustomJsonEntries = false
        simulateCustomJsonEntriesReturnedMessage = ""
        running = true
        serverHandler = JsonRPCMessageHandler(serverMessageSender)
        //creates the stub for IEchoService wrapping the real implementation and register with handler
        serverHandler.registerStub(object : EchoService_JsonRpcStub(serverHandler, object : EchoServiceImpl() {
            override fun testDelegateIntercept(): Int {
                val rpcDelegate = RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcStub.METHOD_testDelegateIntercept_18, null)
                rpcDelegate.setInstanceId(this.hashCode())
                val originalMessage = serverHandler.getOriginalMessage(rpcDelegate)
                println("Original Message at impl $originalMessage)")
                return super.testDelegateIntercept()
            }
        }) {
            override fun onDispatchTransaction(methodDelegate: RPCMethodDelegate<*>) {
                super.onDispatchTransaction(methodDelegate)
                if (throwExceptionFromDispatch && methodDelegate.methodId == EchoService_JsonRpcStub.METHOD_echoString_3) {
                    throw java.lang.IllegalStateException("Call not allowed")
                }
                messageInterceptor?.invoke()
            }
        })
        serverThread = object : Thread() {
            override fun run() {
                while (running) {
                    try {
                        val message = serverQueue.take()
                        println(" 1 < " + serverHandler.getMessageEntries(message))
                        serverHandler.onMessage(message)

                        serverHandler2?.let {
                            println(" 2 < " + it.getMessageEntries(message))
                            it.onMessage(message)
                        }
                    } catch (ignored: Exception) {
                    }

                }
                println("Server exiting")
            }
        }
        serverThread.start()


        clientHandler = JsonRPCMessageHandler(clientMessageSender)
        clientThread = object : Thread() {
            override fun run() {
                while (running) {
                    try {
                        val response = clientQueue.take()
                        if (simulateCustomJsonEntries) {
                            simulateCustomJsonEntriesReturnedMessage = response
                        }
                        println(" 1R < " + clientHandler.getMessageEntries(response))
                        clientHandler.onMessage(response)

                        clientHandler2?.let {
                            println(" 2R < " + clientHandler2!!.getMessageEntries(response))
                            it.onMessage(response)
                        }
                    } catch (ignored: Exception) {
                    }

                }
                println("Client exiting")
            }
        }
        //Create the proxy for the IEchoService
        echoService = EchoService_JsonRpcProxy(clientHandler)
        clientThread.start()
        clientHandler.setLogEnabled(DEBUG)
        serverHandler.setLogEnabled(DEBUG)
        messageInterceptor = null
    }

    @After
    fun destroy() {
        running = false
        serverThread.interrupt()
        clientThread.interrupt()
        serverHandler.clear()
        clientHandler.setExtra(null)
        clientHandler.clear()

        serverHandler2?.clear()
        serverHandler2 = null
        clientHandler2?.setExtra(null)
        clientHandler2?.clear()
        clientHandler2 = null

    }

    @Test
    @Throws(Exception::class)
    fun testMultipleHandlerInstances() {

        serverHandler2 = JsonRPCMessageHandler { message ->
            println(" 2R > " + serverHandler2?.getMessageEntries(message))
            try {
                clientQueue.put(message)
            } catch (e: Exception) {
            }
        }
        //creates the stub for IEchoService wrapping the real implementation and register with handler
        serverHandler2!!.registerStub(EchoService_JsonRpcStub(serverHandler2, object : EchoServiceImpl(){
            override fun echoString(input: String?): String? {
                return if (input != null) "Result$input" else null
            }
        }).also { println("Echo2 Stub $it") })

        clientHandler2 = JsonRPCMessageHandler { message ->
            println(" 2 > " + clientHandler2?.getMessageEntries(message))
            try {
                serverQueue.put(message)
            } catch (e: Exception) {
            }
        }

        val echoService2 = EchoService_JsonRpcProxy(clientHandler2, GsonJsonify(), null, serverHandler2.hashCode())

        println("Echo2 Proxy $echoService2")

        var response = echoService2.getEchoService().echoString("World")
        assertEquals("ResultWorld", response)

        response = echoService2.echoString(null)
        assertNull(response)

        echoService = EchoService_JsonRpcProxy(clientHandler, GsonJsonify(), null, serverHandler.hashCode())

        response = echoService.getEchoService().echoString("World")
        assertEquals("WorldResult", response)

        response = echoService.echoString(null)
        assertNull(response)

    }



    @Test
    @Throws(Exception::class)
    fun testNoArgCall() {
        val registerLatch = CountDownLatch(1)
        val unregisterLatch = CountDownLatch(1)
        val callbackLatch = CountDownLatch(1)
        val listener = object : EchoServiceListener {

            override fun onUnRegistered(success: Boolean) {
                unregisterLatch.countDown()
            }

            override fun onRegistered() {
                registerLatch.countDown()
            }

            override fun onNoArgMethodCalled() {
                callbackLatch.countDown()
            }
        }

        echoService.registerListener(listener)
        registerLatch.await()
        println("Registered listener")

        echoService.noArgumentMethod()
        callbackLatch.await()
        println("noArgCallback complete")

        echoService.unregisterListener(listener)
        unregisterLatch.await()
        println("unregistered listener")
    }

    @Test
    @Throws(Exception::class)
    fun testEcho() {
        var response = echoService.echoString("World")
        assertEquals("WorldResult", response)

        response = echoService.echoString(null)
        assertNull(response)
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleListenersWhenOneDead() {

        val totalEchoResponse = AtomicInteger()
        var stubRemoved = false
        val listener1 = object : EchoServiceListener {
            override fun onEcho(input: String?) {
                println("onEcho in listener 1 $input")
                totalEchoResponse.incrementAndGet()
                if (stubRemoved) {
                    fail("Stub should have been removed")
                }
            }
        }

        val listener2 = object : EchoServiceListener {
            override fun onEcho(input: String?) {
                println("onEcho in listener 2 $input")
                totalEchoResponse.incrementAndGet()
            }
        }

        echoService.registerListener(listener1)
        echoService.registerListener(listener2)

        echoService.echoString("Hello")

        Thread.sleep(200)

        clientHandler.clearStubOfService(listener1)
        stubRemoved = true

        println("stub removed")

        echoService.echoString("Hello")

        Thread.sleep(200)

        assertEquals(3, totalEchoResponse.get())
    }


    @Test
    @Throws(Exception::class)
    fun testMultipleListenersOnMultipleHandlers() {

        val totalEchoResponse = AtomicInteger()
        var stubRemoved = false
        val listener1 = object : EchoServiceListener {
            override fun onEcho(input: String?) {
                println("onEcho in listener 1 $input")
                totalEchoResponse.incrementAndGet()
                if (stubRemoved) {
                    fail("Stub should have been removed")
                }
            }
        }

        clientHandler2 = JsonRPCMessageHandler { message ->
            println(" 2 > " + clientHandler2?.getMessageEntries(message))
            try {
                serverQueue.put(message)
            } catch (e: Exception) {
            }
        }

        val echoService2 = EchoService_JsonRpcProxy(clientHandler2)

        println("Echo2 Proxy $echoService2")

        val listener2 = object : EchoServiceListener {
            override fun onEcho(input: String?) {
                println("onEcho in listener 2 $input")
                totalEchoResponse.incrementAndGet()
            }
        }

        echoService.registerListener(listener1)
        echoService2.registerListener(listener2)

        echoService.echoString("Hello")

        Thread.sleep(200)

        clientHandler.clearStubOfService(listener1)
        stubRemoved = true

        println("stub removed")

        echoService.echoString("Hello")

        Thread.sleep(200)

        assertEquals(3, totalEchoResponse.get())
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleProxy() {
        val echoService2 = EchoService_JsonRpcProxy(clientHandler)
        val latch = CountDownLatch(2)
        thread {
            var response = echoService.echoString("World1")
            assertEquals("World1Result", response)

            response = echoService.echoString(null)
            assertNull(response)
            latch.countDown()
        }
        thread {
            var response = echoService2.echoString("World2")
            assertEquals("World2Result", response)

            response = echoService2.echoString(null)
            assertNull(response)
            latch.countDown()
        }

        latch.await()
    }


    @Test(expected = java.lang.RuntimeException::class)
    @Throws(Exception::class)
    fun testDispatchIntercept() {
        var response = echoService.echoString("World")
        assertEquals("WorldResult", response)

        response = echoService.echoString(null)
        assertNull(response)

        throwExceptionFromDispatch = true

        val input = MyObj("Foo", 1)
        val objRes = echoService.echoObject(input)
        assertEquals(input, objRes)

        echoService.echoString("World")
        fail("Should not happen")
    }

    @Test
    @Throws(Exception::class)
    fun testObject() {
        val input = MyObj("Foo", 1)
        var objRes = echoService.echoObject(input)
        assertEquals(input, objRes)

        objRes = echoService.echoObject(null)
        assertNull(objRes)
    }

    @Test(expected = RuntimeException::class)
    fun testException() {
        echoService.testException("hello")
        fail()
    }

    @Test
    @Throws(Exception::class)
    fun testListener() {
        val registerLatch1 = CountDownLatch(1)
        val unregisterLatch1 = CountDownLatch(1)
        val callbackLatch1 = CountDownLatch(1)

        val echoString1 = "Hello"
        val echoString2 = "World"

        val listener = object : EchoServiceListener {
            private var listenerUnregistered: Boolean = false

            override fun onUnRegistered(success: Boolean) {
                listenerUnregistered = true
                unregisterLatch1.countDown()
            }

            override fun onRegistered() {
                registerLatch1.countDown()
            }


            override fun onEcho(input: String) {
                assertFalse(listenerUnregistered)
                assertEquals(echoString1, input)
                callbackLatch1.countDown()
            }

        }

        val registerLatch2 = CountDownLatch(1)
        val unregisterLatch2 = CountDownLatch(1)
        val callbackLatch2 = CountDownLatch(1)

        val listener2 = object : EchoServiceListener {
            private var listenerUnregistered: Boolean = false

            override fun onUnRegistered(success: Boolean) {
                listenerUnregistered = true
                unregisterLatch2.countDown()
            }

            override fun onRegistered() {
                registerLatch2.countDown()
            }

            override fun onEcho(input: String) {
                assertFalse(listenerUnregistered)
                assertEquals(echoString2, input)
                callbackLatch2.countDown()
            }
        }

        echoService.registerListener(listener)
        echoService.registerListener(listener2)

        registerLatch1.await()
        registerLatch2.await()

        echoService.echoString(echoString1)
        echoService.echoObject(MyObj(echoString2, 0))

        callbackLatch1.await()
        callbackLatch2.await()


        echoService.unregisterListener(listener2)
        unregisterLatch2.await()

        echoService.echoObject(MyObj(echoString2, 0))
        Thread.sleep(50)

        echoService.unregisterListener(listener)
        unregisterLatch1.await()

        echoService.echoString(echoString1)
        Thread.sleep(50)
    }

    @Test
    fun testStubNotFound() {
        val stubNotFoundLatch = CountDownLatch(1)
        var gotErrorCallBack = false
        serverHandler.clear()
        (echoService as RPCProxy).setRPCRemoteListener(object : RemoteListener {
            override fun onRPCFailed(proxy: RPCProxy, methodID: Int, exception: RPCException) {
                println("onRPCFailed $methodID ${exception.message} ${exception.type}")
                gotErrorCallBack = true
                assertEquals(echoService, proxy)
                stubNotFoundLatch.countDown()
            }
        })

        echoService.noArgumentMethod()
        stubNotFoundLatch.await()
        assertTrue(gotErrorCallBack)
    }

    @Test
    fun testOnewayException() {
        val stubNotFoundLatch = CountDownLatch(1)
        var gotErrorCallBack = false
        (echoService as RPCProxy).setRPCRemoteListener(object : RemoteListener {
            override fun onRPCFailed(proxy: RPCProxy, methodID: Int, exception: RPCException) {
                println("onRPCFailed $methodID ${exception.message} ${exception.type}")
                gotErrorCallBack = true
                assertEquals(echoService, proxy)
                stubNotFoundLatch.countDown()
            }
        })

        echoService.oneWayThrowingException()
        stubNotFoundLatch.await()
        assertTrue(gotErrorCallBack)
    }

//    @Test
//    fun testOnewayTimeout() {
//        val stubNotFoundLatch = CountDownLatch(1)
//        var gotErrorCallBack = false
//        (echoService as RPCProxy).setRPCRemoteListener(object : RemoteListener {
//            override fun onRPCFailed(proxy: RPCProxy, methodID: Int, exception: RPCException) {
//                println("onRPCFailed $methodID ${exception.message} ${exception.type}")
//                gotErrorCallBack = true
//                assertEquals(echoService, proxy)
//                stubNotFoundLatch.countDown()
//            }
//        })
//        clientHandler.setOneWayRequestTimeout(10)
//        echoService.oneWayTimeout()
//        stubNotFoundLatch.await(15, TimeUnit.MILLISECONDS)
//        echoService.noArgumentMethod()
//        stubNotFoundLatch.await(5, TimeUnit.MILLISECONDS)
//        assertTrue(gotErrorCallBack)
//    }


    @Test
    @Throws(Exception::class)
    fun testList() {
        val strings = ArrayList<String>()
        strings.add("1")
        strings.add("2")
        val obj1 = ArrayList<MyObj>()
        obj1.add(MyObj("A", 1))
        obj1.add(MyObj("B", 2))
        val obj2 = ArrayList<MyObj>()
        obj2.add(MyObj("C", 10))
        obj2.add(MyObj("D", 20))

        var result = echoService.testMultipleListParams(strings, obj1, obj2)
        println("Result list $result")
        assertEquals(obj1, result)

        result = echoService.testMultipleListParams(strings, null, obj2)
        println("Result list $result")
        assertNull(result)
    }

    @Test
    @Throws(Exception::class)
    fun testArray() {
        val strings = arrayOf("1", "2")
        val obj1 = arrayOf(MyObj("A", 1), MyObj("B", 2))
        val obj2 = arrayOf(MyObj("C", 10), MyObj("D", 20))

        var result = echoService.testMultipleArrayParams(strings, obj1, obj2)
        assertEquals(obj2.size, result?.size)
        for (i in obj2.indices) {
            assertEquals(obj2[i], result?.get(i))
        }

        result = echoService.testMultipleArrayParams(strings, obj1, null)
        assertNull(result)
    }


    @Test
    @Throws(Exception::class)
    fun testMap() {
        val map1 = HashMap<Int, String>()
        map1[1] = "1"
        map1[2] = "2"

        val map2 = HashMap<String, MyObj>()
        map2["1"] = MyObj("A", 1)
        map2["2"] = MyObj("B", 2)

        val map3 = HashMap<Long, MyObj>()
        map3[1L] = MyObj("AB", 10)
        map3[2L] = MyObj("BB", 20)


        var resultMap = echoService.testMultipleMapParams(map1, map2, map3)
        assertEquals(map3, resultMap)

        resultMap = echoService.testMultipleMapParams(map1, map2, null)
        assertNull(resultMap)
    }


    @Test
    @Throws(Exception::class)
    fun testGetRpcInterface() {
        val echoServiceFromService = echoService.getEchoService()
        assertNotNull(echoServiceFromService)

        var response = echoServiceFromService.echoString("World")
        println("Response from echo from the proxy $response")
        assertEquals("WorldResult", response)

        response = echoServiceFromService.echoString(null)
        assertNull(response)
    }


    @Test
    @Throws(Exception::class)
    fun testComplexInstance() {
        val complexObject = ComplexObject()
        complexObject.sex = ComplexObject.SEX.MALE
        complexObject.name = "Name0"
        complexObject.family = ComplexObject.Family()
        complexObject.family.familyName = "Family0"

        val returnedComplex = echoService.echoComplexObject(complexObject)
        assertNotNull(returnedComplex)
        assertEquals(returnedComplex?.name, complexObject.name)
        assertEquals(returnedComplex?.sex, complexObject.sex)
        assertEquals(returnedComplex?.family?.familyName, complexObject.family.familyName)

        val returnedComplex2 = echoService.echoComplexObject(null)
        assertNull(returnedComplex2)
    }

    @Test
    @Throws(Exception::class)
    fun testVarArgs() {
        val returnedString = echoService.echovarargs("1", "2", "3")
        assertEquals(returnedString, "3")

        val returnedString2 = echoService.echovarargs()
        assertNull(returnedString2)

    }


    @Test(expected = java.lang.RuntimeException::class)
    @Throws(Exception::class)
    fun testTimeout() {
        val result = clientHandler.setRequestTimeout(2)
        echoService.callThatTimesout(4)
        fail("Expecting failure")
    }

    @Test
    fun testTimeoutOnClear() {
        var timedOut = false
        val startLatch = CountDownLatch(1)
        val latch = CountDownLatch(1)
        thread {
            try {
                startLatch.countDown()
                echoService.callThatTimesout(60000)
            } catch (exception: Exception) {
                timedOut = true
                latch.countDown()
            }
        }

        startLatch.await()
        Thread.sleep(100)
        println("Clearing")
        clientHandler.clear()
        latch.await(10, TimeUnit.MILLISECONDS)
        assertTrue(timedOut)
    }

    @Test(expected = java.lang.RuntimeException::class)
    @Throws(Exception::class)
    fun testMessageFailture() {
        simulateMessageFailure = true
        var response = echoService.echoString("World")
        fail("Expecting failure")
    }


    @Test
    fun testNullRpcParam() {
        val result = echoService.registerListener(null)
        assertFalse(result)
    }

    @Test
    fun testNullRpcReturn() {
        clientHandler.setLogEnabled(true)
        val result = echoService.getEchoServiceThatReturnsNull()
        assertNull(result)
    }

    @Test
    fun testExceptionThrown() {
        try {
            val result = echoService.testExceptionThrown(0)
        } catch (expected: IllegalStateException) {
            assertNotNull(expected.message)
            println("Got Exception $expected ${expected.message}")
        } catch (exception: java.lang.Exception) {
            fail("Expected IllegalState, got $exception")
        }

        try {
            val result = echoService.testExceptionThrown(1)
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
            println("Got Exception  $expected ${expected.message}")
        } catch (exception: java.lang.Exception) {
            fail("Expected IllegalArgumentException, got $exception")
        }

        try {
            val result = echoService.testExceptionThrown(2)
        } catch (expected: CustomException) {
            assertNotNull(expected.message)
            println("Got Exception  $expected ${expected.message}")
        } catch (exception: java.lang.Exception) {
            fail("Expected CustomException, got $exception")
        }

        //Only single arg constructor or default construcot exceptions are supported
        try {
            val result = echoService.testExceptionThrown(3)
        } catch (expected: java.lang.RuntimeException) {
            assertNotNull(expected.message)
            println("Got Exception  $expected ${expected.message}")
        } catch (exception: java.lang.Exception) {
            fail("Expected RuntimeException, got $exception")
        }

        try {
            val result = echoService.testExceptionThrown(100)
        } catch (expected: java.lang.RuntimeException) {
            assertNotNull(expected.message)
            println("Got Exception  $expected ${expected.message}")
        } catch (exception: java.lang.Exception) {
            fail("Expected RuntimeException, got $exception")
        }
    }


    @Test(expected = RPCNotSupportedException::class)
    fun testRPCNotSupported() {
        echoService.nonRpcCall()
    }

    @Test
    fun testDelegateProxy() {
        val nonDelegated = echoService.testDelegateIntercept()
        assertEquals(100, nonDelegated)

        clientHandler.addMethodDelegate(RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcProxy.METHOD_testDelegateIntercept_18,
                object : EchoService by echoService {
                    override fun testDelegateIntercept() = 200
                }))

        assertEquals(200, echoService.testDelegateIntercept())
    }

    @Test
    fun testDelegateProxyIntercept() {
        val nonDelegated = echoService.testDelegateIntercept()
        assertEquals(100, nonDelegated)

        clientHandler.addMethodDelegate(RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcProxy.METHOD_testDelegateIntercept_18,
                object : EchoService by echoService {
                    override fun testDelegateIntercept(): Int {
                        throw RPCMethodDelegate.DelegateIgnoreException()
                    }
                }))

        assertEquals(100, echoService.testDelegateIntercept())
    }

    @Test
    fun testDelegateStub() {
        val nonDelegated = echoService.testDelegateIntercept()
        assertEquals(100, nonDelegated)


        val echoDelegate = object : EchoService by echoService {
            override fun testDelegateIntercept(): Int {
                val rpcDelegate = RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcStub.METHOD_testDelegateIntercept_18, null)
                rpcDelegate.setInstanceId(this.hashCode())
                val originalMessage = serverHandler.getOriginalMessage(rpcDelegate)
                println("Original Message at delegate $originalMessage)")
                assertNotNull(originalMessage)
                return 300
            }
        }
        serverHandler.addMethodDelegate(RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcStub.METHOD_testDelegateIntercept_18, echoDelegate))
        assertEquals(300, echoService.testDelegateIntercept())

        val rpcDelegate = RPCMethodDelegate(EchoService::class.java, EchoService_JsonRpcStub.METHOD_testDelegateIntercept_18, null)
        rpcDelegate.setInstanceId(echoDelegate.hashCode())
        val originalMessage = serverHandler.getOriginalMessage(rpcDelegate)
        println("Original Message after call $originalMessage)")
        assertNull(originalMessage)
    }


    @Test
    fun testListenerSendingOriginalCustom() {

        val registerLatch0 = CountDownLatch(1)
        val unregisterLatch0 = CountDownLatch(1)
        val callbackLatch0 = CountDownLatch(1)


        val registerLatch1 = CountDownLatch(1)
        val unregisterLatch1 = CountDownLatch(1)
        val callbackLatch1 = CountDownLatch(1)

        val echoString1 = "Hello"
        var originalMessageInListenerCallBack: String? = null

        val listener0 = object : EchoServiceListener {
            private var listenerUnregistered: Boolean = false

            override fun onUnRegistered(success: Boolean) {
                listenerUnregistered = true
                unregisterLatch0.countDown()
            }

            override fun onRegistered() {
                registerLatch0.countDown()
            }


            override fun onEcho(input: String) {

                val rpcDelegate = RPCMethodDelegate(EchoServiceListener::class.java, EchoServiceListener_JsonRpcStub.METHOD_onEcho_3, null)
                rpcDelegate.setInstanceId(this.hashCode())
                originalMessageInListenerCallBack = clientHandler.getOriginalMessage(rpcDelegate)

                println("Original Message in first listener $originalMessageInListenerCallBack")
                callbackLatch0.countDown()
            }

        }

        val listener = object : EchoServiceListener {
            private var listenerUnregistered: Boolean = false

            override fun onUnRegistered(success: Boolean) {
                listenerUnregistered = true
                unregisterLatch1.countDown()
            }

            override fun onRegistered() {
                registerLatch1.countDown()
            }


            override fun onEcho(input: String) {

                val rpcDelegate = RPCMethodDelegate(EchoServiceListener::class.java, EchoServiceListener_JsonRpcStub.METHOD_onEcho_3, null)
                rpcDelegate.setInstanceId(this.hashCode())
                originalMessageInListenerCallBack = clientHandler.getOriginalMessage(rpcDelegate)

                println("Original Message $originalMessageInListenerCallBack")
                callbackLatch1.countDown()
            }

        }

        echoService.registerListener(listener0)
        registerLatch0.await()


        echoService.echoString(echoString1)
        callbackLatch0.await()

        val jsonify = GsonJsonify()
        //custom_xxx keys should come back
        assertNull(jsonify.fromJSON(originalMessageInListenerCallBack, "custom_string", String::class.java))
        assertNull(jsonify.fromJSON(originalMessageInListenerCallBack, "custom_int", Int::class.java))
        assertNull(jsonify.fromJSON(originalMessageInListenerCallBack, "custom_obj", MyObj::class.java)?.age)
        //non custom should not be back (unless other end adds it back)
        assertNull(jsonify.fromJSON(originalMessageInListenerCallBack, "foo", String::class.java))

        echoService.unregisterListener(listener0)
        unregisterLatch0.await()


        val customExtras = mutableMapOf<String, String>()
        customExtras["custom_string"] = "Hello"
        customExtras["custom_int"] = "1"
        val jsonify1 = GsonJsonify()
        val jsonObj = jsonify1.toJson(MyObj("Hello", 1))
        customExtras["custom_obj"] = jsonObj.toJson()
        customExtras["foo"] = "bar"
        clientHandler.setExtra(customExtras)
        println("Extras set $customExtras")

        echoService.registerListener(listener)
        registerLatch1.await()


        echoService.echoString(echoString1)
        callbackLatch1.await()


        //custom_xxx keys should come back
        assertEquals("Hello", jsonify.fromJSON(originalMessageInListenerCallBack, "custom_string", String::class.java))
        assertEquals(1, jsonify.fromJSON(originalMessageInListenerCallBack, "custom_int", Int::class.java))
        assertEquals(1, jsonify.fromJSON(originalMessageInListenerCallBack, "custom_obj", MyObj::class.java).age)
        //non custom should not be back (unless other end adds it back)
        assertNull(jsonify.fromJSON(originalMessageInListenerCallBack, "foo", String::class.java))



        echoService.unregisterListener(listener)
        unregisterLatch1.await()
    }


    @Test
    @Throws(Exception::class)
    fun testCustomJsonEntries() {
        simulateCustomJsonEntries = true

        val customExtras = mutableMapOf<String, String>()
        customExtras["custom_string"] = "Hello"
        customExtras["custom_int"] = "1"
        val jsonify1 = GsonJsonify()
        val jsonObj = jsonify1.toJson(MyObj("Hello", 1))
        customExtras["custom_obj"] = jsonObj.toJson()
        customExtras["foo"] = "bar"

        clientHandler.setExtra(customExtras)
        println("Extras set $customExtras")

        echoService.echoString("World")
        assertNotNull(simulateCustomJsonEntriesReturnedMessage)
        val jsonify = GsonJsonify()
        //custom_xxx keys should come back
        assertEquals("Hello", jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "custom_string", String::class.java))
        assertEquals(1, jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "custom_int", Int::class.java))
        assertEquals(1, jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "custom_obj", MyObj::class.java).age)
        //non custom should not be back (unless other end adds it back)
        assertNull(jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "foo", String::class.java))
    }


    @Test
    @Throws(Exception::class)
    fun testProperties() {
        simulateCustomJsonEntries = true
        messageInterceptor = {
            println("Intercepting message ")
            assertEquals("B1", serverHandler.getProperty("B"));
            assertEquals("A1", serverHandler.getProperty("A"));
            assertEquals("B1", serverHandler.getProperty("custom_B"));
            println("Got all properties")
        }

        clientHandler.addProperty("A", "A1", false);
        clientHandler.addProperty("B", "B1", true);

        echoService.echoString("World")
        assertNotNull(simulateCustomJsonEntriesReturnedMessage)
        val jsonify = GsonJsonify()
        //custom_xxx keys should come back
        assertEquals("B1", jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "custom_B", String::class.java))
        assertNull(jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "custom_A", String::class.java))
        assertNull(jsonify.fromJSON(simulateCustomJsonEntriesReturnedMessage, "A", String::class.java))

        assertNull(serverHandler.getProperty("B"));
        assertNull(serverHandler.getProperty("A"));
        assertNull(serverHandler.getProperty("custom_B"));

    }


}
