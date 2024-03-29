package rpcfy.compiler.builder;


import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

import rpcfy.JSONify;
import rpcfy.JsonRPCMessageHandler;
import rpcfy.RPCProxy;
import rpcfy.RPCStub;
import rpcfy.json.GsonJsonify;


/**
 * A {@link RpcfyBuilder} that knows how to build the proxy and stub classes.
 * This uses other builders internally to build the fields and methods.
 */
class ClassBuilder extends RpcfyBuilder {

    static final String PROXY_SUFFIX = "_JsonRpcProxy";
    static final String STUB_SUFFIX = "_JsonRpcStub";

    protected ClassBuilder(Messager messager, Element element) {
        super(messager, element);
    }

    /**
     * Builds the proxy
     */
    public JavaFile.Builder buildProxyClass() {
        ClassName proxyClassName = getProxyClassName();

        TypeSpec.Builder proxyClassBuilder = TypeSpec
                .classBuilder(proxyClassName.simpleName())
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(RPCProxy.class))
                .addSuperinterface(TypeName.get(getRemoterInterfaceElement().asType()));


        for (TypeParameterElement typeParameterElement : ((TypeElement) getRemoterInterfaceElement()).getTypeParameters()) {
            proxyClassBuilder.addTypeVariable(TypeVariableName.get(typeParameterElement.toString()));
        }


        //constructor
        proxyClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getProxyClassName().simpleName() + "} with the given {@link JsonRPCMessageHandler}\n\n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages\n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addStatement("this(rpcHandler, new $T())", GsonJsonify.class)
                .build());
        //constructor
        proxyClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getProxyClassName().simpleName() + "} with the given {@link JsonRPCMessageHandler}\n\n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages\n")
                .addJavadoc("@param jsonify A custom implementation of  {@link JSONify} \n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addParameter(JSONify.class, "jsonify")
                .addStatement("this(rpcHandler, jsonify, null)")
                .build());
        //constructor
        proxyClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getProxyClassName().simpleName() + "} with the given {@link JsonRPCMessageHandler}\n\n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages\n")
                .addJavadoc("@param jsonify A custom implementation of  {@link JSONify} \n")
                .addJavadoc("@param remoteID A unique id to represent this instance \n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addParameter(JSONify.class, "jsonify")
                .addParameter(Integer.class, "remoteID")
                .addStatement("this(rpcHandler, jsonify, remoteID, null)")
                .build());

        //constructor
        proxyClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getProxyClassName().simpleName() + "} with the given {@link JsonRPCMessageHandler}\n\n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages\n")
                .addJavadoc("@param jsonify A custom implementation of  {@link JSONify} \n")
                .addJavadoc("@param remoteID A unique id to represent this instance \n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addParameter(JSONify.class, "jsonify")
                .addParameter(Integer.class, "remoteID")
                .addParameter(Integer.class, "remoteHandlerID")
                .addStatement("this.rpcHandler = rpcHandler")
                .addStatement("this.jsonify = jsonify")
                .addStatement("this.remoteID = remoteID")
                .addStatement("this.remoteHandlerID = remoteHandlerID")
                .build());


        getBindingManager().getFieldBuilder(getRemoterInterfaceElement()).addProxyFields(proxyClassBuilder);
        getBindingManager().getMethodBuilder(getRemoterInterfaceElement()).addProxyMethods(proxyClassBuilder);

        proxyClassBuilder.addJavadoc("An RPC Proxy for {@link " + getRemoterInterfaceElement() + "} interface\n");
        proxyClassBuilder.addJavadoc("<p>\n");
        proxyClassBuilder.addJavadoc("Autogenerated by <a href=\"http://bit.ly/RPCfy\">RPCfy</a>\n");
        proxyClassBuilder.addJavadoc("@see " + getStubClassName().simpleName() + "\n");

        return JavaFile.builder(proxyClassName.packageName(), proxyClassBuilder.build());
    }


    /**
     * Builds the stub
     */
    public JavaFile.Builder buildStubClass() {
        ClassName stubClassName = getStubClassName();

        TypeSpec.Builder stubClassBuilder = TypeSpec
                .classBuilder(stubClassName.simpleName())
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(RPCStub.class));

        //constructor
        stubClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getStubClassName().simpleName() + "} with the given {@link " + getRemoterInterfaceElement() + "}.\n<p/>\n")
                .addJavadoc("This will also registers this stub instance with the given {@link JsonRPCMessageHandler}. \n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages and process the incoming messages\n")
                .addJavadoc("@param service An implementation of {@link " + getRemoterInterfaceClassName() + "}\n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addParameter(TypeName.get(getRemoterInterfaceElement().asType()), "service")
                .addStatement("this(rpcHandler, service, new $T())", GsonJsonify.class)
                .build());
        //constructor
        stubClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Initialize this {@link " + getStubClassName().simpleName() + "} with the given {@link " + getRemoterInterfaceElement() + "}.\n<p/>\n")
                .addJavadoc("This will also registers this stub instance with the given {@link JsonRPCMessageHandler}. \n")
                .addJavadoc("@param rpcHandler A {@link JsonRPCMessageHandler} to send the generated JSONRPC messages and process the incoming messages\n")
                .addJavadoc("@param service An implementation of {@link " + getRemoterInterfaceClassName() + "}\n")
                .addJavadoc("@param jsonify A custom implementation of  {@link JSONify} \n")
                .addParameter(JsonRPCMessageHandler.class, "rpcHandler")
                .addParameter(TypeName.get(getRemoterInterfaceElement().asType()), "service")
                .addParameter(JSONify.class, "jsonify")
                .addStatement("this.rpcHandler = rpcHandler")
                .addStatement("this.jsonify = jsonify")
                .addStatement("this.service = service")
                .addStatement("this.remoteID = service.hashCode()")
                .addStatement("rpcHandler.registerStub(this)")
                .build());

        getBindingManager().getFieldBuilder(getRemoterInterfaceElement()).addStubFields(stubClassBuilder);
        getBindingManager().getMethodBuilder(getRemoterInterfaceElement()).addStubMethods(stubClassBuilder);


        stubClassBuilder.addJavadoc("An RPC Stub for {@link " + getRemoterInterfaceElement() + "} interface\n");
        stubClassBuilder.addJavadoc("<p>\n");
        stubClassBuilder.addJavadoc("Autogenerated by <a href=\"http://bit.ly/RPCfy\">RPCfy</a>\n");
        stubClassBuilder.addJavadoc("@see " + getProxyClassName().simpleName() + "\n");


        return JavaFile.builder(stubClassName.packageName(), stubClassBuilder.build());
    }

    private ClassName getStubClassName() {
        return ClassName.get(getRemoterInterfacePackageName(), getRemoterInterfaceClassName() + STUB_SUFFIX);
    }

    private ClassName getProxyClassName() {
        return ClassName.get(getRemoterInterfacePackageName(), getRemoterInterfaceClassName() + PROXY_SUFFIX);
    }

}
