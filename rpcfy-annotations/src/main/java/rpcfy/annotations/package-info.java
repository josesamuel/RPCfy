/**
 * <p>
 * <b>RPCfy</b> upgrades your normal java interface to be capable of doing RPC (Remote Procedure Call).
 * </p>
 * <br/>
 * <br/>
 * Example -
 * <br/>
 * <pre><code>
 *
 *
 * {@literal @}RPCfy
 *  public interface ISampleService {
 *
 * }
 * </code></pre>
 *
 * <p>
 *     At the <b>client</b> side :
 *     <br/>
 *     <br/>
 *     <ul>Use the auto-generated proxy</ul>
 *     <ul>Provide the transport to send/receive message</ul>
 *     <ul>Pass the received messages to the JsonRPCMessageHandler</ul>
 *<pre><code>
 *     MessageSender<String> clientMessageSender;//You provide the transport
 *
 * JsonRPCMessageHandler messageHandler = new JsonRPCMessageHandler(clientMessageSender);
 * IEchoService echoService = new IEchoService_JsonRpcProxy(messageHandler);
 *
 * //When you receive messages from server, pass it to messageHandler
 * messageHandler.onMessage(messageFromServer);
 *</code></pre>
 * </p>
 * <br/>
 *
 * <p>
 *     At the <b>service</b> side :
 *     <br/>
 *     <br/>
 *     <ul>Implement your interface</ul>
 *     <ul>Wrap your implementation with the auto-generated Stub</ul>
 *     <ul>Provide the transport to send/receive message</ul>
 *     <ul>Pass the received messages to the JsonRPCMessageHandler</ul>
 *<pre><code>
 MessageSender<String> serverMessageSender;//You provide the transpor
 JsonRPCMessageHandler messageHandler = new JsonRPCMessageHandler(serverMessageSender);

 IEchoService yourService = new EchoService(); //Your service implementation
 messageHandler.registerStub(new IEchoService_JsonRpcStub(messageHandler, yourService));

 //When you receive messages from client, pass it to messageHandler
 messageHandler.onMessage(messageFromServer);</code></pre>
 * </p>
 * <br/>
 *
 */
package rpcfy.annotations;
