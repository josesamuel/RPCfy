package rpcfy.compiler.builder;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import rpcfy.JSONify;
import rpcfy.JsonRPCMessageHandler;
import rpcfy.RPCProxy;
import rpcfy.RPCStub;


/**
 * A {@link RpcfyBuilder} that knows how to generate the fields for stub and proxy
 */
class FieldBuilder extends RpcfyBuilder {


    protected FieldBuilder(Messager messager, Element element) {
        super(messager, element);
    }


    public void addProxyFields(TypeSpec.Builder classBuilder) {
        classBuilder.addField(FieldSpec.builder(JsonRPCMessageHandler.class, "rpcHandler")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(JSONify.class, "jsonify")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(AtomicInteger.class, "idGenerator")
                .addModifiers(Modifier.PRIVATE)
                .initializer("new AtomicInteger(0)")
                .build());
        classBuilder.addField(FieldSpec.builder(Integer.class, "remoteID")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(Integer.class, "remoteHandlerID")
                .addModifiers(Modifier.PRIVATE).build());

        classBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(Map.class, String.class, String.class), "customExtras")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(RPCProxy.RemoteListener.class, "remoteListener")
                .addModifiers(Modifier.PRIVATE).build());


        final int[] lastMethodIndex = {0};
        processRemoterElements(classBuilder, new ElementVisitor() {
            @Override
            public void visitElement(TypeSpec.Builder classBuilder, Element member, int methodIndex, MethodSpec.Builder methodBuilder) {
                addCommonFields(classBuilder, member, methodIndex);
                lastMethodIndex[0] = methodIndex;
            }
        }, null);


    }

    public void addStubFields(TypeSpec.Builder classBuilder) {
        classBuilder.addField(FieldSpec.builder(JsonRPCMessageHandler.class, "rpcHandler")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(JSONify.class, "jsonify")
                .addModifiers(Modifier.PRIVATE).build());
        classBuilder.addField(FieldSpec.builder(Integer.class, "remoteID")
                .addModifiers(Modifier.PRIVATE).build());

        classBuilder.addField(FieldSpec.builder(TypeName.get(getRemoterInterfaceElement().asType()), "service")
                .addModifiers(Modifier.PROTECTED).build());

        final int[] lastMethodIndex = {0};

        processRemoterElements(classBuilder, new ElementVisitor() {
            @Override
            public void visitElement(TypeSpec.Builder classBuilder, Element member, int methodIndex, MethodSpec.Builder methodBuilder) {
                addCommonFields(classBuilder, member, methodIndex);
                lastMethodIndex[0] = methodIndex;
            }
        }, null);

        lastMethodIndex[0]++;
    }

    private void addCommonFields(TypeSpec.Builder classBuilder, Element member, int methodIndex) {
        String methodName = member.getSimpleName().toString();
        classBuilder.addField(FieldSpec.builder(TypeName.INT, "METHOD_" + methodName + "_" + methodIndex)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("" + methodIndex).build());
    }
}
