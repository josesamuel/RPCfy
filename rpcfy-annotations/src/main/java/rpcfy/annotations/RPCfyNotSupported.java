package rpcfy.annotations;

import rpcfy.RPCMethodDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Marks a method within an interface marked as @{@link RPCfy} as not supporting RPC
 * <p>
 * A call to this method will result in {@link rpcfy.RPCNotSupportedException} getting thrown.
 * <p>
 * To selectively handle specific methods locally instead of being RPCfied, use {@link rpcfy.JsonRPCMessageHandler#addMethodDelegate(RPCMethodDelegate)}
 *
 * @see RPCfy
 */
@Retention(CLASS)
@Target(METHOD)
public @interface RPCfyNotSupported {
}
