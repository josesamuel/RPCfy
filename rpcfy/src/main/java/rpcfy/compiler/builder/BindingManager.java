package rpcfy.compiler.builder;


import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import rpcfy.annotations.RPCfy;

import static javax.lang.model.type.TypeKind.DECLARED;


/**
 * Holds the mapping between the {@link RpcfyBuilder} for different types
 */
public final class BindingManager {

    private Elements elementUtils;
    private Messager messager;
    private Types typeUtils;
    private Filer filer;


    /**
     * Initialize with the given details from the annotation processing enviornment
     */
    public BindingManager(Elements elementUtils, Filer filer, Messager messager, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.filer = filer;
        this.messager = messager;
        this.typeUtils = typeUtils;
    }

    /**
     * Generates the Proxy for the given @{@link RPCfy} interface element
     */
    public void generateProxy(Element element) {
        try {
            getClassBuilder(element)
                    .buildProxyClass()
                    .build()
                    .writeTo(filer);
        } catch (Exception ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Error while generating Proxy " + ex.getMessage());
        }
    }

    /**
     * Generates the Stub for the given @{@link RPCfy} interface element
     */
    public void generateStub(Element element) {
        try {
            getClassBuilder(element)
                    .buildStubClass()
                    .build()
                    .writeTo(filer);
        } catch (Exception ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Error while generating Stub " + ex.getMessage());
        }
    }


    /**
     * Returns the {@link ClassBuilder} that generates the Builder for the Proxy and Stub classes
     */
    private ClassBuilder getClassBuilder(Element element) {
        ClassBuilder classBuilder = new ClassBuilder(messager, element);
        classBuilder.setBindingManager(this);
        return classBuilder;
    }

    /**
     * Returns the {@link FieldBuilder} that  adds fields to the class spec
     */
    FieldBuilder getFieldBuilder(Element element) {
        FieldBuilder fieldBuilder = new FieldBuilder(messager, element);
        fieldBuilder.setBindingManager(this);
        return fieldBuilder;
    }

    /**
     * Returns the {@link MethodBuilder} that adds methods to the class spec
     */
    MethodBuilder getMethodBuilder(Element element) {
        MethodBuilder methodBuilder = new MethodBuilder(messager, element);
        methodBuilder.setBindingManager(this);
        return methodBuilder;
    }

    boolean isParameterOfTypeTPCfy(TypeMirror typeMirror) {
        if (typeMirror.getKind() == DECLARED) {
            String typeName = typeMirror.toString();
            int templateStart = typeName.indexOf('<');
            if (templateStart != -1) {
                typeName = typeName.substring(0, templateStart).trim();
            }
            TypeElement typeElement = elementUtils.getTypeElement(typeName);
            if (typeElement != null) {
                if (typeElement.getKind() == ElementKind.INTERFACE && typeElement.getAnnotation(RPCfy.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
