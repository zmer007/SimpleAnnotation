package com.iyh.processor;

import com.google.auto.service.AutoService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class SingletonCheckerProcessor extends AbstractProcessor {

    private Messager mMessager;
    private Types mTypes;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mMessager = processingEnvironment.getMessager();
        mTypes = processingEnvironment.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (TypeElement typeElement : ElementFilter.typesIn(roundEnvironment.getElementsAnnotatedWith(Singleton.class))) {
            if (!checkForPrivateConstructors(typeElement)) return false;
            if (!checkForGetInstanceMethod(typeElement)) return false;
        }
        return true;
    }

    private boolean checkForPrivateConstructors(TypeElement typeElement) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(typeElement.getEnclosedElements());
        for (ExecutableElement constructor : constructors) {
            if (constructor.getModifiers().isEmpty() || !constructor.getModifiers().contains(Modifier.PRIVATE)) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "单例构造器必需为私有", constructor);
                return false;
            }
        }
        return true;
    }

    private boolean checkForGetInstanceMethod(TypeElement typeElement) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(typeElement.getEnclosedElements());
        if (methods.isEmpty()) {
            mMessager.printMessage(Diagnostic.Kind.ERROR, "单例类需要一个公有静态方法：getInstance");
            return false;
        }

        boolean isSingleton = false;
        for (ExecutableElement method : methods) {
            // 检查是否包含 getInstance 方法
            if (method.getSimpleName().contentEquals("getInstance")) {

                // 检查返回值是否是本类对象
                if (mTypes.isSameType(method.getReturnType(), typeElement.asType())) {

                    // check for modifiers
                    if (method.getModifiers().contains(Modifier.PRIVATE)) {
                        mMessager.printMessage(Diagnostic.Kind.ERROR, "getInstance 方法不能用 private 修饰", method);
                        return false;
                    }
                    if (!method.getModifiers().contains(Modifier.STATIC)) {
                        mMessager.printMessage(Diagnostic.Kind.ERROR, "getInstance 应该有个公有静态方法", method);
                        return false;
                    }
                    isSingleton = true;
                }
            }
        }

        if (isSingleton) {
            return true;
        } else {
            mMessager.printMessage(Diagnostic.Kind.ERROR, "单例必须满足两个条件：1. 构造器为私有；2. 包含公有静态方法 getInstance 且其返回值为单例对象");
            return false;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<String>() {{
            add(Singleton.class.getCanonicalName());
        }};
    }
}
