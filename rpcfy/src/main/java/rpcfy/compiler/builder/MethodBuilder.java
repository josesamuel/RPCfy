package rpcfy.compiler.builder;

import com.google.gson.reflect.TypeToken;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import rpcfy.JSONify;
import rpcfy.RPCStub;


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

        //methodBuilder.beginControlFlow("try");

        methodBuilder
                .addStatement("$T jsonRPCObject = jsonify.newJson()", JSONify.JObject.class);
        methodBuilder.addStatement("String interfaceName = \"" + getRemteInterfaceFQName() + "\"");
        methodBuilder.addStatement("int methodID = METHOD_" + methodName + "_" + methodIndex);
        methodBuilder.addStatement("int rpcCallId = idGenerator.incrementAndGet()");

        methodBuilder.addStatement("jsonRPCObject.put(\"jsonrpc\", \"2.0\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"method\", \"" + methodName + "\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"interface\", interfaceName )");
        methodBuilder.addStatement("jsonRPCObject.put(\"method_id\", methodID)");
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
                methodBuilder.beginControlFlow("synchronized (stubMap)");
                methodBuilder.addStatement(stubName + " = stubMap.get(" + paramNameWithIndex + ")");
                methodBuilder.beginControlFlow("if (" + stubName + " == null)");
                methodBuilder.addStatement(stubName + " = new " + param.asType() + ClassBuilder.STUB_SUFFIX + "(rpcHandler, " + paramName + "_" + paramIndex + ", jsonify)");
                methodBuilder.addStatement("stubMap.put(" + paramNameWithIndex + ", " + stubName + ")");
                methodBuilder.endControlFlow();

                methodBuilder.addStatement("paramsObject.put(\"" + paramName + "\", " + paramName + "_" + paramIndex + ".hashCode())");
                methodBuilder.addStatement("rpcHandler.registerStub(" + stubName + ")");
                methodBuilder.endControlFlow();
                methodBuilder.endControlFlow();


            } else {
                methodBuilder.addStatement("paramsObject.put(\"" + paramName + "\", jsonify.toJson(" + paramName + "_" + paramIndex + "))");
            }
            paramIndex++;
        }


        methodBuilder.addStatement("jsonRPCObject.put(\"params\", paramsObject)");
        methodBuilder.addStatement("jsonRPCObject.put(\"id\", rpcCallId)");

        if (isOneWay) {
            methodBuilder.addStatement("rpcHandler.sendMessage(jsonRPCObject.toJson())");
        } else {
            methodBuilder.addStatement("String result");
            methodBuilder.addStatement("result = rpcHandler.sendMessageAndWaitForResponse(jsonRPCObject.toJson(), interfaceName, methodID, rpcCallId)");

            methodBuilder.addStatement("String exception = jsonify.getJSONElement(result, \"error\")");
            methodBuilder.beginControlFlow("if (exception != null)");
            methodBuilder.addStatement("throw new RuntimeException(jsonify.getJSONElement(exception, \"message\"))");
            methodBuilder.endControlFlow();

            String returnType = executableElement.getReturnType().toString();

            if (executableElement.getReturnType().getKind() == TypeKind.DECLARED) {
                methodBuilder.addStatement("$T genericType = new $T<"+ returnType +">(){}.getType()", Type.class, TypeToken.class);
                methodBuilder.addStatement("return jsonify.fromJSON(result, \"result\", genericType)");
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

        methodBuilder.addStatement("$T jsonRPCObject = jsonify.newJson()", JSONify.JObject.class);
        methodBuilder.addStatement("jsonRPCObject.put(\"jsonrpc\", \"2.0\")");
        methodBuilder.addStatement("jsonRPCObject.put(\"interface\", getStubInterfaceName())");
        methodBuilder.addStatement("jsonRPCObject.put(\"method_id\", methodID)");
        methodBuilder.addStatement("jsonRPCObject.put(\"id\", jsonify.fromJSON(message, \"id\", int.class))");

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
        methodBuilder.addStatement("jsonErrorObject.put(\"exception\", re.getClass().getSimpleName())");
        methodBuilder.addStatement("jsonRPCObject.put(\"error\", jsonErrorObject)");
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

        methodBuilder.addStatement("String paramsElement = jsonify.getJSONElement(message, \"params\");");

        //pass parameters
        for (final VariableElement param : executableElement.getParameters()) {

            String paramName = "arg_stb_" + paramIndex;

            if (getBindingManager().isParameterOfTypeTPCfy(param.asType())) {
                methodBuilder.addStatement("int " + paramName + "_id = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", int.class)");

                ClassName proxy = ClassName.bestGuess(param.asType().toString() + ClassBuilder.PROXY_SUFFIX);
                methodBuilder.addStatement("$T " + paramName + " = new $T(rpcHandler, jsonify, " + paramName + "_id)", proxy, proxy);

            } else {
                String pType = param.asType().toString();

                if (param.asType().getKind() == TypeKind.DECLARED) {
                    methodBuilder.addStatement("$T genericType"+ paramIndex +" = new $T<"+ pType +">(){}.getType()", Type.class, TypeToken.class);
                    methodBuilder.addStatement("$T " + paramName + " = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", genericType"+ paramIndex + ")", param.asType());
                } else {
                    methodBuilder.addStatement("$T " + paramName + " = jsonify.fromJSON(paramsElement, \"" + param.getSimpleName() + "\", $T.class)", param.asType(), param.asType());
                }
            }
            paramNames.add(paramName);
            paramIndex++;
        }

        String methodCall = "service." + methodName + "(";
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
            methodBuilder.addStatement("jsonRPCObject.put(\"result\", jsonify.toJson(result))");

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
                .addStatement("return remoteID != null ? remoteID : 0");

        classBuilder.addMethod(methodBuilder.build());

    }

    /**
     * Add other extra methods
     */
    private void addProxyExtras(TypeSpec.Builder classBuilder) {
        addHashCode(classBuilder);
        addEquals(classBuilder);
        addProxyDestroyMethods(classBuilder);
    }


    /**
     * Add proxy method for destroystub
     */
    private void addProxyDestroyMethods(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("destroyStub")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(Object.class, "object")
                .returns(TypeName.VOID)
                .beginControlFlow("if(object != null)")
                .beginControlFlow("synchronized (stubMap)")
                .addStatement("RPCStub stub = stubMap.get(object)")
                .beginControlFlow("if (stub != null)")
                .addStatement("rpcHandler.clearStub(stub)")
                .addStatement("stubMap.remove(object)")
                .endControlFlow()
                .endControlFlow()
                .endControlFlow();
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
