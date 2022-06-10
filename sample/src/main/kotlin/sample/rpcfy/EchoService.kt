package sample.rpcfy


import rpcfy.annotations.RPCfy
import rpcfy.annotations.RPCfyNotSupported
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException


/**
 * Sample interface for testing
 */
@RPCfy
interface EchoService {

    fun getEchoService(): EchoService

    fun getEchoServiceThatReturnsNull(): EchoService?

    fun noArgumentMethod()

    fun echoString(input: String?): String?

    fun echovarargs(vararg input: String): String?

    fun echoObject(input: MyObj?): MyObj?

    @Throws(Exception::class)
    fun testNullInput(input: String?): String?

    fun testNullOutput(input: String?): String?

    fun testException(input: String): String

    fun testMultipleListParams(listOfStrings: List<String>, listofObjs1: List<MyObj>?, listofObjs2: List<MyObj>): List<MyObj>?

    fun testMultipleArrayParams(strings: Array<String>?, obj1: Array<MyObj>?, obj2: Array<MyObj>?): Array<MyObj>?

    fun testMultipleMapParams(strings: Map<Int, String>, obj1: Map<String, MyObj>, obj2: Map<Long, MyObj>?): Map<Long, MyObj>?

    fun registerListener(listener: EchoServiceListener?) : Boolean

    fun unregisterListener(listener: EchoServiceListener)

    fun echoComplexObject(complexObject: ComplexObject?) : ComplexObject?

    fun callThatTimesout(timeout:Int) : Int

    @Throws(IllegalStateException::class, IllegalArgumentException::class, CustomException::class, CustomException2Arg::class)
    fun testExceptionThrown(input:Int):Int

    @RPCfyNotSupported
    fun nonRpcCall():Int

    fun testDelegateIntercept():Int

    fun oneWayThrowingException()

    fun oneWayTimeout()

    fun testUniqueListeners(listener: EchoServiceListener): Boolean

}
