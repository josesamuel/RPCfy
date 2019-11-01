package rpcfy.compiler;


import com.google.auto.service.AutoService;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;
import rpcfy.annotations.RPCfy;
import rpcfy.compiler.builder.BindingManager;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

/**
 * AnnotationProcessor that processes the @{@link RPCfy} annotations and
 * generates the Stub and Proxy classes enabling RPC for that interface
 *
 * @author js
 */
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(ISOLATING)
public class RpcfyProcessor extends AbstractProcessor {

    private BindingManager bindingManager;
    private Messager messager;


    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.messager = env.getMessager();
        bindingManager = new BindingManager(env.getElementUtils(), env.getFiler(), messager, env.getTypeUtils());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotation : getSupportedAnnotations()) {
            types.add(annotation.getCanonicalName());
        }
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Only one annotation is supported at class level - @{@link RPCfy}
     */
    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        annotations.add(RPCfy.class);
        return annotations;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        for (Element element : env.getElementsAnnotatedWith(RPCfy.class)) {
            if (element.getKind() == ElementKind.INTERFACE) {
                bindingManager.generateProxy(element);
                bindingManager.generateStub(element);
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "@RPCfy is expected only for interface. Ignoring " + element.getSimpleName());
            }
        }
        return false;
    }
}
