package rpcfy.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Marks an interface to be RPCfy'ed
 * <p>
 * An RPCfy'ed interface generates Proxy and Stubs which enables the interface to be
 * used for RPC
 * <p>
 * The generated XXXX__JsonRpcProxy class implements this given interface and maps the
 * calls to it to the a JSONRPC messages. These messages can then be transported to the
 * actual implementation using the provided {@link rpcfy.JsonRPCMessageHandler}.
 * <p>
 * Once the JSONRPC message reaches the server side, it is passed on to the generated instance
 * of XXXX_JsonRpcStub class, which de-serializes the given JSONRPC message and calls the remote
 * method and passes the result back.
 *
 * @see rpcfy.JsonRPCMessageHandler
 */
@Retention(CLASS)
@Target(TYPE)
public @interface RPCfy {
}
