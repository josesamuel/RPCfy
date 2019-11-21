package rpcfy.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Marks a method within an interface marked as @{@link RPCfy} as not supporting RPC
 * <p>
 * A call to this method will result in {@link rpcfy.RPCNotSupportedException} getting thrown.
 *
 * @see RPCfy
 */
@Retention(CLASS)
@Target(METHOD)
public @interface RPCfyNotSupported {
}
