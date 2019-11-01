package sample.rpcfy

import java.util.*


class EchoServiceImpl : EchoService {

    private val listeners = ArrayList<EchoServiceListener>()

    override fun getEchoService(): EchoService = this

    override fun getEchoServiceThatReturnsNull(): EchoService? = null


    override fun noArgumentMethod() {
        if (listeners.size >= 1) {
            listeners.get(0).onNoArgMethodCalled()
        }
    }

    override fun echoString(input: String?): String? {
        if (listeners.size >= 1) {
            listeners[0].onEcho(input)
        }
        return if (input != null) input + "Result" else null
    }

    override fun echoObject(input: MyObj?): MyObj? {
        println("echoObject $input")
        if (input != null) {
            println("echoObject " + input.name)
            if (listeners.size >= 2) {
                listeners[1].onEcho(input.name)
            }
        }
        return input
    }


    @Throws(Exception::class)
    override fun testNullInput(input: String?): String? {
        return "Got Null$input"
    }

    override fun testNullOutput(input: String?): String? {
        return null
    }

    override fun testException(input: String): String {
        throw NullPointerException("Null")
    }

    override fun testMultipleListParams(listOfStrings: List<String>, listofObjs1: List<MyObj>?, listofObjs2: List<MyObj>): List<MyObj>? {
        println("testMultipleParams : $listOfStrings $listofObjs1 $listofObjs2")
        return listofObjs1
    }

    override fun testMultipleArrayParams(strings: Array<String>?, obj1: Array<MyObj>?, obj2: Array<MyObj>?): Array<MyObj>? {
        println("testMultipleArrayParams : $strings $obj1 $obj2")
        if (strings != null) {
            for (s in strings) {
                println(s)
            }
        }
        if (obj1 != null) {
            for (s in obj1) {
                println(s)
            }
        }
        if (obj2 != null) {
            for (s in obj2) {
                println(s)
            }
        }

        return obj2
    }

    override fun testMultipleMapParams(strings: Map<Int, String>, obj1: Map<String, MyObj>, obj2: Map<Long, MyObj>?): Map<Long, MyObj>? {
        println("testMultipleMapParams : $strings $obj1 $obj2")
        return obj2
    }

    override fun registerListener(listener: EchoServiceListener) {
        listeners.add(listener)
        println("Remaining listener count " + listeners.size)
        listener.onRegistered()
    }

    override fun unregisterListener(listener: EchoServiceListener) {
        val removed = listeners.remove(listener)
        println("Remaining listener count " + listeners.size)
        listener.onUnRegistered(removed)
    }
}
