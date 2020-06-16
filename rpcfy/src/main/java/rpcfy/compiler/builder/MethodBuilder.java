package rpcfy.compiler.builder;

import com.google.gson.reflect.TypeToken;
import com.squareup.javapoet.*;
import rpcfy.JSONify;
import rpcfy.RPCMethodDelegate;
import rpcfy.RPCNotSupportedException;
import rpcfy.RPCStub;
import rpcfy.annotations.RPCfyNotSupported;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Type;
import java.util.*;


/**
 * A {@link RpcfyBuilder} that knows how to generate methods for Stub and Proxy
 */
class MethodBuilder extends RpcfyBuilder {

    protected MethodBuilder(Messager messager, Element element) {
        super(messager, element);
    }

    /**
     * Build the proxy methods
     */
    public void addProxyMethods(TypeSpec.Builder classBuilder) {
        processRemoterElements(classBuilder, new ElementVisitor() {
            @Override
            public void visitElement(TypeSpec.Builder classBuilder, Element member, int methodIndex, MethodSpec.Builder methodBuilder) {
                addProxyMethods(classBuilder, member, methodIndex);
            }
        }, null);

        addProxyExtras(classBuilder);
    }

    /**
     * Build the proxy methods
     */
    private void addProxyMethods(TypeSpec.Builder classBuilder, Element member, int methodIndex) {
        ExecutableElement executableElement = (ExecutableElement) member;
        String methodName = executableElement.getSimpleName().toString();
        boolean isOneWay = executableElement.getReturnType().getKind() == TypeKind.VOID;

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.get(executableElement.getReturnType()));

        //add Exceptions
        for (TypeMirror exceptions : executableElement.getThrownTypes()) {
            methodBuilder.addException(ClassName.bestGuess(exceptions.toString()));
        }

        //add parameters
        int paramIndex = 0;
        for (VariableElement params : executableElement.getParameters()) {
            methodBuilder.addParameter(TypeName.get(params.asType()), params.getSimpleName().toString() + "_" + paramIndex);
            paramIndex++;
        }

        boolean rpcNotSupported = member.getAnnotation(RPCfyNotSupported.class) != null;

        if (rpcNotSupported) {
            methodBuilder.addStatement("throw new $T(\"Method '" + methodName + "' does not support RPC call\")", RPCNotSupportedException.class);
            classBuilder.addMethod(methodBuilder.build());
            return;
        }

        methodBuilder.addStatement(getRemoterInterfaceClassName() + " methodDelegate = (" + getRemoterInterfaceClassName() + ")rpcHandler.getMethodDelegate(new $T(" + getRemoterInterfaceClassName() + ".class, METHOD_" + methodName + "_" + methodIndex + ", null))", RPCMethodDelegate.class);
        methodBuilder.beginControlFlow("if (methodDelegate != null)");
        methodBuilder.beginControlFlow("try");
        StringBuilder delegateCall = new StringBuilder();
        if (!isOneWay) {
            delegateCall.append("return ");
        }
        delegateCall.append("methodDelegate.");
        delegateCall.append(methodName);
        delegateCall.append("(");
        paramIndex = 0;
        int totalParams = executableElement.getParameters().size();
        for (VariableElement params : executableElement.getParameters()) {
            delegateCall.append(params.getSimpleName().toString() + "_" + paramIndex);
            paramIndex++;
            if (paramIndex < totalParams) {
                delegateCall.append(",");
            }
        }
        delegateCall.append(")");
        methodBuilder.addStatement(delegateCall.toString());
        if (isOneWay) {
            methodBuilder.addStatement("return");
        }
        methodBuilder.endControlFlow();
        methodBuilder.beginControlFlow("catch($T delegateException)", RPCMethodDelegate.DelegateIgnoreException.class);
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();

        //methodBuilder.beginControlFlow("try");

        methodBuilder
                .addStatement("$T jsonRPCObject = jsonify.newJson()", JSONify.JObject.class);
        methodBuilder.addStatement("String interfaceName = \"" + getRemteInterfaceFQName() + "\"");
        methodBuilder.addStatement("int methodID = METHOD_" + methodName + "_" + methodIndex);
        methodBuilder.addStatement("int rpcCallId = idGenerator.incrementAndGet()");
        methodBuilder.addStatement("int proxyInstanceId = super.hashCode()");

        methodBuilder.addStatement("jsonRPCObject.put(\"jsonrpc\", \"2.0\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"method\", \"" + methodName + "\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"interface\", interfaceName )");
        methodBuilder.addStatement("jsonRPCObject.put(\"method_id\", methodID)");
        methodBuilder.addStatement("jsonRPCObject.put(\"ins_id\", proxyInstanceId)");
        methodBuilder.beginControlFlow("if (remoteID != null)");
        methodBuilder.addStatement("jsonRPCObject.put(\"remote_id\", remoteID)");
        methodBuilder.endControlFlow();

        methodBuilder.addStatement("JSONify.JObject paramsObject = jsonify.newJson()");


        //pass parameters
        paramIndex = 0;
        for (VariableElement param : executableElement.getParameters()) {

            String paramName = param.getSimpleName().toString();

            if (getBindingManager().isParameterOfTypeTPCfy(param.asType())) {

                String paramNameWithIndex = paramName + "_" + paramIndex;
                String stubName = paramNameWithIndex + "_stub";
                methodBuilder.addStatement("$T " + stubName + " = null", RPCStub.class);


                methodBuilder.beginControlFlow("if (" + paramNameWithIndex + " != null)");
                methodBuilder.addStatement(stubName + " = rpcHandler.getStub(" + paramNameWithIndex + ")");
                methodBuilder.beginControlFlow("if (" + stubName + " == null)");
                methodBuilder.addStatement(stubName + " = new " + param.asType() + ClassBuilder.STUB_SUFFIX + "(rpcHandler, " + paramName + "_" + paramIndex + ", jsonify)");
                methodBuilder.addStatement("rpcHandler.registerStub(" + stubName + ")");
                methodBuilder.endControlFlow();

                methodBuilder.addStatement("paramsObject.put(\"" + paramName + "\", " + paramName + "_" + paramIndex + ".hashCode())");
                methodBuilder.endControlFlow();


            } else {
                methodBuilder.addStatement("paramsObject.put(\"" + paramName + "\", jsonify.toJson(" + paramName + "_" + paramIndex + "))");
            }
            paramIndex++;
        }


        methodBuilder.addStatement("jsonRPCObject.put(\"params\", paramsObject)");
        methodBuilder.addStatement("jsonRPCObject.put(\"id\", rpcCallId)");

        methodBuilder.addStatement("$T<String, String> _jsonrpc_req_extras = rpcHandler.getExtras()", Map.class);
        methodBuilder.beginControlFlow("if (_jsonrpc_req_extras != null)");
        methodBuilder.beginControlFlow("for (String key:_jsonrpc_req_extras.keySet())");
        methodBuilder.addStatement("jsonRPCObject.putJson(key, _jsonrpc_req_extras.get(key))");
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();

        methodBuilder.beginControlFlow("if (this.customExtras != null)");
        methodBuilder.beginControlFlow("for (String key:this.customExtras.keySet())");
        methodBuilder.addStatement("jsonRPCObject.putJson(key, this.customExtras.get(key))");
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();


        if (isOneWay) {
            methodBuilder.addStatement("rpcHandler.sendMessage(jsonRPCObject.toJson())");
        } else {
            methodBuilder.addStatement("String result");
            methodBuilder.addStatement("result = rpcHandler.sendMessageAndWaitForResponse(jsonRPCObject.toJson(), interfaceName, methodID, rpcCallId, proxyInstanceId)");

            methodBuilder.addStatement("String exception = jsonify.getJSONElement(result, \"error\")");
            methodBuilder.beginControlFlow("if (exception != null)");

            methodBuilder.addStatement("String exceptionClassName = jsonify.fromJSON(exception, \"exception\", String.class)");
            methodBuilder.addStatement("String exceptionMessage = jsonify.fromJSON(exception, \"message\", String.class)");
            int exceptionIndex = 0;
            for (TypeMirror exceptions : executableElement.getThrownTypes()) {
                methodBuilder.addStatement(exceptions.toString() + " exception_" + exceptionIndex + " = JsonRPCMessageHandler.asException(exceptionClassName, exceptionMessage, " + exceptions.toString() + ".class)");
                methodBuilder.beginControlFlow("if (exception_" + exceptionIndex + " != null)");
                methodBuilder.addStatement("throw exception_" + exceptionIndex);
                methodBuilder.endControlFlow();
                exceptionIndex++;
            }


            methodBuilder.addStatement("throw new RuntimeException(jsonify.getJSONElement(exception, \"message\"))");
            methodBuilder.endControlFlow();

            String returnType = executableElement.getReturnType().toString();

            if (executableElement.getReturnType().getKind() == TypeKind.DECLARED) {
                methodBuilder.addStatement("$T genericType = new $T<" + returnType + ">(){}.getType()", Type.class, TypeToken.class);

                if (getBindingManager().isParameterOfTypeTPCfy(executableElement.getReturnType())) {

                    methodBuilder.addStatement("String resultJson = jsonify.getJSONElement(result, \"result\")");
                    methodBuilder.beginControlFlow("if (resultJson != null && !resultJson.isEmpty())");
                    methodBuilder.addStatement("int return_id = jsonify.fromJSON(resultJson, int.class)");

                    ClassName returnProxyCName = ClassName.bestGuess(executableElement.getReturnType().toString() + ClassBuilder.PROXY_SUFFIX);
                    methodBuilder.addStatement("return new $T(rpcHandler, jsonify, return_id)", returnProxyCName);

                    methodBuilder.endControlFlow();
                    methodBuilder.beginControlFlow("else");
                    methodBuilder.addStatement("return null");
                    methodBuilder.endControlFlow();
                } else {
                    methodBuilder.addStatement("return jsonify.fromJSON(result, \"result\", genericType)");
                }
            } else {
                methodBuilder.addStatement("return jsonify.fromJSON(result, \"result\", " + returnType + ".class)");
            }

        }

        //methodBuilder.endControlFlow();
        //methodBuilder.beginControlFlow("catch (Exception ex)");
        //methodBuilder.addStatement("throw new RuntimeException(ex)");
        //methodBuilder.endControlFlow();

        classBuilder.addMethod(methodBuilder.build());
    }


    /**
     * Build the stub methods
     */
    public void addStubMethods(TypeSpec.Builder classBuilder) {

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("onRPCCall")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .addParameter(int.class, "methodID")
                .addParameter(String.class, "message");

        methodBuilder.addStatement("$T rpc_method_delegate = null", RPCMethodDelegate.class);
        methodBuilder.addStatement("$T customExtras = null", ParameterizedTypeName.get(Map.class, String.class, String.class));
        methodBuilder.addStatement("$T jsonRPCObject = jsonify.newJson()", JSONify.JObject.class);
        methodBuilder.addStatement("jsonRPCObject.put(\"jsonrpc\", \"2.0\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"interface\", getStubInterfaceName())");
        methodBuilder.addStatement("jsonRPCObject.put(\"method_id\", methodID)");
        methodBuilder.addStatement("jsonRPCObject.put(\"id\", jsonify.fromJSON(message, \"id\", int.class))");
        methodBuilder.beginControlFlow("if (jsonify.getJSONElement(message, \"ins_id\") != null)");
        methodBuilder.addStatement("jsonRPCObject.put(\"ins_id\", jsonify.fromJSON(message, \"ins_id\", int.class))");
        methodBuilder.endControlFlow();
        //add custom entries back

        methodBuilder.beginControlFlow("if (message.contains(\"custom_\"))");
        methodBuilder.addStatement("customExtras = new $T<>()", HashMap.class);
        methodBuilder.addStatement("$T requestElement = jsonify.fromJson(message)", JSONify.JElement.class);
        methodBuilder.addStatement("$T<String> requestParams = requestElement.getKeys()", Set.class);
        methodBuilder.beginControlFlow("if (requestParams != null)");
        methodBuilder.beginControlFlow("for (String key : requestElement.getKeys())");
        methodBuilder.beginControlFlow("if (key.startsWith(\"custom_\"))");
        methodBuilder.addStatement("String _custom_value = requestElement.getJsonValue(key)");
        methodBuilder.addStatement("jsonRPCObject.putJson(key, _custom_value)");
        methodBuilder.addStatement("customExtras.put(key, _custom_value)");
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();


        methodBuilder.beginControlFlow("try");
        methodBuilder.beginControlFlow("switch (methodID)");

        processRemoterElements(classBuilder, new ElementVisitor() {
            @Override
            public void visitElement(TypeSpec.Builder classBuilder, Element member, int methodIndex, MethodSpec.Builder methodBuilder) {
                addStubMethods(classBuilder, member, methodIndex, methodBuilder);
            }
        }, methodBuilder);

        //end switch
        methodBuilder.endControlFlow();
        //end of try
        methodBuilder.endControlFlow();
//        //catch rethrow
        methodBuilder.beginControlFlow("catch ($T re)", Throwable.class);

        methodBuilder.addStatement("JSONify.JObject jsonErrorObject = jsonify.newJson()");
        methodBuilder.addStatement("jsonErrorObject.put(\"code\", -32000)");
        methodBuilder.addStatement("jsonErrorObject.put(\"message\", re.getMessage())");
        methodBuilder.addStatement("jsonErrorObject.put(\"exception\", re.getClass().getName())");
        methodBuilder.addStatement("jsonRPCObject.put(\"error\", jsonErrorObject)");
        methodBuilder.endControlFlow();

        methodBuilder.beginControlFlow("if (rpc_method_delegate != null)");
        methodBuilder.addStatement("rpcHandler.setOriginalMessage(rpc_method_delegate, null)");
        methodBuilder.endControlFlow();


        methodBuilder.addStatement("return jsonRPCObject.toJson()");

        classBuilder.addMethod(methodBuilder.build());

        addStubExtras(classBuilder);

    }

    /**
     * Called from the {@link ElementVisitor} callback
     */
    private void addStubMethods(TypeSpec.Builder classBuilder, Element member, int methodIndex, MethodSpec.Builder methodBuilder) {
        ExecutableElement executableElement = (ExecutableElement) member;
        String methodName = executableElement.getSimpleName().toString();
        boolean isOneWay = executableElement.getReturnType().getKind() == TypeKind.VOID;


        methodBuilder.beginControlFlow("case METHOD_" + methodName + "_" + methodIndex + ":");

        List<String> paramNames = new ArrayList<>();
        int paramIndex = 0;

        methodBuilder.addStatement("String paramsElement = jsonify.getJSONElement(message, \"params\")");

        //pass parameters
        for (final VariableElement param : executableElement.getParameters()) {

            String paramName = "arg_stb_" + paramIndex;

            if (getBindingManager().isParameterOfTypeTPCfy(param.asType())) {
                ClassName proxy = ClassName.bestGuess(param.asType().toString() + ClassBuilder.PROXY_SUFFIX);
                methodBuilder.addStatement("$T " + paramName + " = null", proxy);
                methodBuilder.addStatement("String " + paramName + "_id_json = jsonify.getJSONElement(paramsElement, \"" + param.getSimpleName() + "\")");
                methodBuilder.beginControlFlow("if (" + paramName + "_id_json != null)");
                methodBuilder.addStatement("int " + paramName + "_id = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", int.class)");
                methodBuilder.addStatement(paramName + " = new $T(rpcHandler, jsonify, " + paramName + "_id)", proxy);
                methodBuilder.addStatement(paramName + ".setRPCfyCustomExtras(customExtras)");
                methodBuilder.endControlFlow();
            } else {
                String pType = param.asType().toString();

                if (param.asType().getKind() == TypeKind.DECLARED) {
                    methodBuilder.addStatement("$T genericType" + paramIndex + " = new $T<" + pType + ">(){}.getType()", Type.class, TypeToken.class);
                    methodBuilder.addStatement("$T " + paramName + " = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", genericType" + paramIndex + ")", param.asType());
                } else {
                    methodBuilder.addStatement("$T " + paramName + " = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", $T.class)", param.asType(), param.asType());
                }
            }
            paramNames.add(paramName);
            paramIndex++;
        }

        methodBuilder.addStatement(getRemoterInterfaceClassName() + " methodImpl = service");
        methodBuilder.addStatement("rpc_method_delegate = new $T(" + getRemoterInterfaceClassName() + ".class, METHOD_" + methodName + "_" + methodIndex + ", null)", RPCMethodDelegate.class);
        methodBuilder.addStatement(getRemoterInterfaceClassName() + " methodDelegate = (" + getRemoterInterfaceClassName() + ")rpcHandler.getMethodDelegate(rpc_method_delegate)");
        methodBuilder.beginControlFlow("if (methodDelegate != null)");
        methodBuilder.addStatement("methodImpl =  methodDelegate");
        methodBuilder.endControlFlow();

        methodBuilder.addStatement("rpc_method_delegate.setInstanceId(methodImpl.hashCode())");
        methodBuilder.addStatement("onDispatchTransaction(rpc_method_delegate)");
        methodBuilder.addStatement("rpcHandler.setOriginalMessage(rpc_method_delegate, message)");

        String methodCall = "methodImpl." + methodName + "(";
        int paramSize = paramNames.size();
        for (int paramCount = 0; paramCount < paramSize; paramCount++) {
            methodCall += paramNames.get(paramCount);
            if (paramCount < paramSize - 1) {
                methodCall += ", ";
            }
        }
        methodCall += ")";

        if (executableElement.getReturnType().getKind() != TypeKind.VOID) {
            methodBuilder.addStatement("$T result = " + methodCall, executableElement.getReturnType());

            if (getBindingManager().isParameterOfTypeTPCfy(executableElement.getReturnType())) {

                methodBuilder.addStatement("$T returnStub = null", RPCStub.class);


                methodBuilder.beginControlFlow("if (result  != null)");
                methodBuilder.addStatement("returnStub = rpcHandler.getStub(result)");
                methodBuilder.beginControlFlow("if (returnStub  == null)");
                methodBuilder.addStatement("returnStub = new " + executableElement.getReturnType() + ClassBuilder.STUB_SUFFIX + "(rpcHandler, result, jsonify)");
                methodBuilder.addStatement("rpcHandler.registerStub(returnStub)");
                methodBuilder.endControlFlow();

                methodBuilder.addStatement("jsonRPCObject.put(\"result\", result.hashCode())");

                methodBuilder.endControlFlow();


            } else {
                methodBuilder.addStatement("jsonRPCObject.put(\"result\", jsonify.toJson(result))");
            }

        } else {
            methodBuilder.addStatement(methodCall);
            methodBuilder.addStatement("jsonRPCObject.put(\"result\", \"\")");
        }

        methodBuilder.endControlFlow();
        methodBuilder.addStatement("break");

    }

    private void addStubExtras(TypeSpec.Builder classBuilder) {
        addRPCStubMethods(classBuilder);
    }

    /**
     * Add stub method that  get called for each transaction from where an exception could be thrown
     */
    private void addRPCStubMethods(TypeSpec.Builder classBuilder) {

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("getStubInterfaceName")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .addStatement("return " + getRemoterInterfaceClassName() + ".class.getName()");

        classBuilder.addMethod(methodBuilder.build());

        methodBuilder = MethodSpec.methodBuilder("getStubId")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(int.class)
                .addStatement("return remoteID");

        classBuilder.addMethod(methodBuilder.build());

        methodBuilder = MethodSpec.methodBuilder("getService")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(Object.class)
                .addStatement("return service");

        classBuilder.addMethod(methodBuilder.build());

        methodBuilder = MethodSpec.methodBuilder("onDispatchTransaction")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(RPCMethodDelegate.class, "methodDelegate")
                .addJavadoc("Override to intercept before stub method is called\n");

        classBuilder.addMethod(methodBuilder.build());

    }

    /**
     * Add other extra methods
     */
    private void addProxyExtras(TypeSpec.Builder classBuilder) {
        addHashCode(classBuilder);
        addEquals(classBuilder);
        addRpcProxyMethods(classBuilder);
    }

    /**
     * Add proxy method to set hashcode to uniqueu id of binder
     */
    private void addRpcProxyMethods(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("setRPCfyCustomExtras")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addAnnotation(Override.class)
                .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), "customExtras")
                .addStatement("this.customExtras = customExtras");
        classBuilder.addMethod(methodBuilder.build());
    }

    /**
     * Add proxy method to set hashcode to uniqueu id of binder
     */
    private void addHashCode(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("hashCode")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addAnnotation(Override.class)
                .addStatement("return remoteID != null ? remoteID : super.hashCode()");
        classBuilder.addMethod(methodBuilder.build());
    }

    /**
     * Add proxy method for equals
     */
    private void addEquals(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("equals")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(Object.class), "obj")
                .returns(boolean.class)
                .addAnnotation(Override.class)
                .addStatement("return (obj instanceof " + getRemoterInterfaceClassName() + ClassBuilder.PROXY_SUFFIX + ") && obj.hashCode() == hashCode()");
        classBuilder.addMethod(methodBuilder.build());
    }

}
