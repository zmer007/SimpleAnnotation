* [1 简介](#1-简介)
* [2 分类](#2-分类)
    * [2.1 元注解](#21-元注解)
    * [@Target](#target)
    * [@Retention](#retention)
    * [@Inherited](#inherited)
    * [@Repeatable (Java 8)](#repeatable-java-8)
    * [2.2 普通注解](#22-普通注解)
* [3 注解处理器](#3-注解处理器)
    * [3.1 运行机制](#31-运行机制)
    * [3.2 编写处理器](#32-编写处理器)
    * [3.3 注册处理器](#33-注册处理器)
* [4 常用工具](#4-常用工具)
* [5 注意事项](#5-注意事项)
* [6 进阶学习](#6-进阶学习)


## 1 简介
许多 API 都有很多固定套路的代码，比如 Android 里绑定 View 的操作 `findViewById(R.id.xxx)`，添加点击事件 `setOnclickListener(new OnClickedListener(){...})`，数据库增删改查操作等。这些代码都是必要的，但是它们与业务逻辑没太大关系，而且都是些大块大块的重复代码，很不优雅。于是很多框架利用 java 注解技术将这些近乎模板的代码自动“挪到”逻辑代码之外，比如 ButterKnife, Dagger, Retrofit 等。Java 语言很早就有丰富的注解机制，不过它们都是以 ad hoc 的形式存在，直到 Java 5.0 版本才将注解公开给大众使用，并提供注解处理工具（Annotation Processing Tool, apt），并在 6.0 版本正式将 apt 集成到 javac 成为编译器的一部分。

注解可以理解成给编译器“看”的注释，它本身没有任何功能性作用，只有通过编译器解读才会产生相应行为，比如生成源码，添加资源文件或必要时终止编译等。除了使代码更加优雅外，注解至少还有以下优点：
- **速度快**：相比传统通过反射处理信息，注解会更快，别为它在编译时将相关操作写入 Java 文件，以源码的行为打包到应用中
- **错误少**：大量[调查](https://hal.inria.fr/hal-02091516/document)发现，使用注解的程序出错概率往往比没有用注解的程序低
- **无反射操作**：注解是通过 Mirror API 处理的而非反射处理。Mirror API 用于模块化 java 源码或字节码，它对于 java 源码大致相当于 gson 对于 json 文件

## 2 分类

### 2.1 元注解
注解分为元注解与普通注解。元注解用于修饰注解，用于限制注解作用域，限定注解生存域等。标准元注解位于 `java.lang.annotation` 包下，各元注解基本含义如下：
#### @Target
用于限定注解的作用域，比如 
```java
@Target(ElementType.METHOD)
public @interface YourAnnotation {
    // ...
}
```
代表 `YourAnnotation` 只能修饰方法，其它 `ElementType` 有

- **ANNOTATION_TYPE：** 该注解（YourAnnotation）用于修饰注解，此时的 YourAnnotation 也叫元注解
- **TYPE：** 该注解用于修饰 类、接口 或 枚举。
- **CONSTRUCTOR：** 该注解用于修饰构造器
- **METHOD：** 该注解用于修饰构造器
- **FIELD：** 该注解用于修饰属性或枚举中的成员常量
- **LOCAL_VARIABLE：** 该注解用于修饰局部变量
- **PARAMETER：** 该注解用于修饰方法参数的
- **PACKAGE：** 该注解用于修饰包名

#### @Retention
用于限定注解生存域，比如
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface YourAnnotation {
    // ...
}
```
强调一下，RetentionPolicy 只有三个值，含义为
- **SOURCE：** 该注解（YourAnnotation）只存在于源文件中，javac 在编译时会处理该注解，但不会将它编译到 class 文件中
- **CLASS：** 该注解会被编译到 class 文件中，但 jvm 不会将它加载到运行时（Runtime），即程序不能通过反射获取到该注解，此值为默认值。
- **RUNTIME：** jvm 会将它加载到运行时，可以通过反射获取该注解

举个例子，如
```java
@YourAnnotation
public class YourClass {
    // ...
}
```
将 `YourClass.java` 文件编译成 `YourClass.class` 文件后，如果 `YourAnnotation` 使用 `RetentionPolicy.SOURCE` 限定，则在 `YourClass.class` 中不会找到 `@YourAnnotation` 注解；如果 `YourAnnotation` 使用 `RetentionPolicy.CLASS` 限定，则会在 `YourClass.class` 中发现 `@YourAnnotation` 注解。

#### @Inherited
用于自动在继承类中添加注解，比如
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited
public @interface YourAnnotation {
    // ...
}

@YourAnnotation
public class Sup {
    // ...
}

public class Base extends Sup {
    // ...
}
```
此时，Base 相当于也添加了 `@YourAnnotation` 注解。

#### @Repeatable (Java 8)
用于表示该注解可以重复声明，比如，要实现这样的功能：
```java
@YourAnnotation("Hello")
@YourAnnotation("RepeatableAnnotation")
public class YourClass {
}
```
就需要添加 `@Repeatable` 注解，如下
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Inherited
@Repeatable(YourAnnotation.List.class)
public @interface YourAnnotation {
    String value();

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @interface List {
        YourAnnotation[] value();
    }
}
```
需要注意，`@Repeatable` 需要传入的注解（`Class<? extends Annotation>`）类型的 `class` 对象。

### 2.2 普通注解
- **@Deprecated：** 用于标记方法，属性，构造器等过期，编译时会有警告输出
- **@Override：** 检查方法是否为重写方法，如果该注解修饰方法不是重写方法会中断编译，抛出编译错误信息
- **@SafeVarargs：** 忽略方法或构造器中泛型可变参数引起的警告
- **@SuppressWarnings：** 通知编译器忽略此注解修饰的参数中的警告信息

## 3 注解处理器
前面说过注解自身是没有任何功能性作用，只有通过处理器解释时（后）才有具体意义。这一小节将简单介绍注解处理流程，包括注解运行机制及一个简单的用于判断当前类是否为单例的 Demo。

### 3.1 运行机制
简介中说过，注解处理工具（apt）做为 javac 编译器的一部分参与编译过程。默认情况下，javac 会去 `META-INF/services/javax.annotation.processing.Processor` 文件中加载注解处理器，然后使用此处理器处理注解。在处理过程中如果生成了新的 java 文件，则会被记录，本轮处理结束后会对新生成的文件再执行一次处理逻辑；如果第二轮新生成文件包含注解，且该注解的处理器也生成了新文件，那么此轮结束后将会进行第三轮处理，直到没有新文件产生，然后编译器会编译所有源文件。大致过程如下图所示：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191202000640508.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3N1aWZlbmdkcmVhbQ==,size_16,color_FFFFFF,t_70)

### 3.2 编写处理器
为了方便理解，我们一步一步实现一个简单示例 Demo，该 Demo 中包含一个注解 `@Singleton` 用于判断其宿主类是否为单例，如果不是单例则终止编译并抛出错误信息。

开始写代码之前先梳理一下自定义注解处理器工作流程，首先它需要继承 `AbstractProcessor` 类，此基类中有处理注解的关键工具 `ProcessingEnvironment`（在 `init` 方法中获取），通过此对象可以得到
- **Filer：** 用于生成文件，比如生成 .java 文件或其它资源文件
- **Messager：** 用于输出日志
- **Elements：** 用于操作程序元素（如类，构造器，方法，属性等都叫元素）的工具类
- **Types：** 用于操作类型（type）的工具类

`AbstractProcessor` 需要覆写 `getSupportSourceVersion()` 方法，用于说明此处理器支持的最新 Java 版本，默认返回 RELEASE_6，一般会写最新版本 `return SourceVersion.latestSupported()`，不然轻则会有警告产生，重则会产生线上问题；还需要将本处理器能处理的注解添加进来，覆写 `getSupportedAnnotationTypes()` 并将 `@Singleton` 全限定名回传，这样的话只有添加过的注解才会被此处理器处理。

上面三个方法（`init、getSupportedAnnotationTypes、getSupportSourceVersion`）使用方式基本不会有太大变化，就是处理（设置）一些基础信息。`process` 方法才是展现各路神通的地方，敲黑板：

```java
boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
```
此方法用于处理前一轮中未被处理的注解 -- `annotations`，并将结果回传，即处理完成就返回 `true`，代表此注解已经被本处理器声明并处理了，后续的其它处理器不需要再次处理此注解；返回 `false` 代表此注解未被本处理器声明，后续处理器应该处理它们。`RoundEnvironment` 获取处理轮中的信息，比如标记本轮生成的文件是否需要下一轮处理，上一轮处理是否有问题抛出等。

OK，基本方法都已经说完了，现在再谈谈业务 -- 判断当前类是否为单例。简单起见对单例对象的定义为：
1. 没有公开的构造器（禁止使用 `new` 关键字生成对象）
2. 有一个非私有静态 `getInstance` 方法用于获取单例对象

**开始写代码**

第一步，添加注解 `@Singleton`，其定义如下：

**com.iyh.processor.Singleton**
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Singleton {
}
```
第二步，编写 `@Singleton` 处理器，以下代码存在于：**com.iyh.processor.SingletonCheckerProcessor**

1 初始化工具类
```java
private Messager mMessager;
private Types mTypes;
private Filer mFiler;

@Override
public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    mMessager = processingEnvironment.getMessager();
    mTypes = processingEnvironment.getTypeUtils();
    mFiler = processingEnvironment.getFiler();
}
```
2 添加最新 Java 版本支持以及此处理器可处理的注解类型
```java
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
```
canonical name 意为“规范名”，它与 `getName()` 十分相似，都是返回 `class` 的全限定名，如 `java.lang.String`。不同点在与

1. 对于内部类，`getName()` 返回 `com.iyh.OuterClass$InnerClass`，而 `getCanonicalName()` 返回 `com.iyh.OuterClass.InnerClass`
2. 对于匿名对象，`getName()` 返回 `com.iyh.YourRunningClass$1`，而 `getCannoicalName()` 返回 `null`
3. 对于对象数组，`getName()` 返回的是 `[Ljava.lang.String`，而 `getCannoicalName()` 返回 `java.lang.String[]`

3 处理 `@Singleton` 注解
```java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
    // annotations 代表是传入此 process 的注解，如 [com.iyh.processor.Singleton]
    // roundEnvironment.getElementsAnnotatedWith(Singleton.class) 代表获取使用 Singleton.class 修饰的类、方法、属性等
    // ElementFilter.typesIn(Set<? extends Element> set) 代表过滤 set 中的元素类型，即只返回类型为 type 的元素。type 代表：类，枚举，接口或注解类。
    for (TypeElement typeElement : ElementFilter.typesIn(roundEnvironment.getElementsAnnotatedWith(Singleton.class))) {
        // 此时 typeElement 代表 type 类型元素（类）
        if (!checkForPrivateConstructors(typeElement)) return false;
        if (!checkForGetInstanceMethod(typeElement)) return false;
    }
    return true;
}

private boolean checkForPrivateConstructors(TypeElement typeElement) {
    // typeElement.getEnclosedElements() 代表获取该 type 元素的所有封闭元素，如 type 代表类的话，它会将该类中的成员属性元素，构造器元素，方法元素等传回
    // ElementFilter.constructorsIn(...) 代表从那些封闭元素中抽取构造器元素
    List<ExecutableElement> constructors = ElementFilter.constructorsIn(typeElement.getEnclosedElements());
    for (ExecutableElement constructor : constructors) {
        if (constructor.getModifiers().isEmpty() || !constructor.getModifiers().contains(Modifier.PRIVATE)) {
            // 此处：Diagnostic.Kind.ERROR 会导致编译中断，并将输出 "单例构造器必需为私有" 日志及出错位置
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

                // 检查修饰符是否为非私有且静态
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
```


### 3.3 注册处理器
上面所有的功能代码已经写完了，但此时可运行不了这段程序，因为编译器完全不知道有这段代码。所以需要提前将此处理器注册到 jvm 中。本节介绍如何手动注册注解处理器（**常用工具**中有自动注册的插件）。

以 gralde java library 项目为例，java 源码位于 `src/main/java/your.package.xxx` 下

1. 在 `src/main` 下创建目录 `resources/META-INF/services`，即 `src/main/resources/META-INF/services`
2. 在 `services` 目录下创建 `javax.annotation.processing.Processor` 文件
3. 在文件中添加 `your.package.YourProcessor`（以 `SingletonCheckerProcessor` 为例就应为 `com.iyh.processor.SingletonCheckerProcessor`）

到此，注册完成。现在可以在 `app` 工程下使用 `annotationProcessor`（注意，gralde 2.2 版本及以上才支持此语法）添加 `SingletonCheckerProcessor` 注解处理器依赖了。执行 `./gradlew build` 就会调用该处理器。

## 4 常用工具
1. [AutoService](https://github.com/google/auto/tree/master/service)：自动注册 jvm 服务，比如可以自动化上文的手动注册注解处理器服务，使用方法：
```java
import javax.annotation.processing.Processor;

@AutoService(Processor.class)
public class SingletonCheckerProcessor extends AbstractProcessor {
    // ...
}
```
如何添加 AutoService 依赖请参考[示例 Demo](https://github.com/zmer007/SimpleAnnotation/blob/master/singletoncheckerprocessor/build.gradle)

2. [JavaPoet](https://github.com/square/javapoet)：用于生成 java 源码，比如正常写如下代码：
```java
package com.iyh.simpleannotation;

public final class HelloWorld {
    public static void sayHello() {
        System.out.println("Hello, world!");
    }
}
```

需要一行一行将代码写到文件中，如
```java
JavaFileObject javaFileObject = mFiler.createSourceFile("com.iyh.simpleannotation.HelloWorld");
Writer writer = javaFileObject.openWriter();
writer.write("" +
        "package com.iyh.simpleannotation;\n" +
        "\n" +
        "public final class HelloWorld {\n" +
        "    public static void sayHello() {\n" +
        "        System.out.println(\"Hello, world!\");\n" +
        "    }\n" +
        "}");
writer.close();
```

使用 JavaPoet 就会变成这样：
```java
MethodSpec sayHello = MethodSpec.methodBuilder("sayHello")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(void.class)
        .addStatement("$T.out.println($S)", System.class, "Hello, World!")
        .build();

TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addMethod(sayHello)
        .build();

JavaFile javaFile = JavaFile.builder("com.iyh.simpleannotation", helloWorld)
        .build();

javaFile.writeTo(mFiler);
```
是的，引入 `JavaPoet` 框架后程序变长了，写法似乎更复杂了。但是也得注意到，它将纯文本信息转换成指令信息，然后通过指令生成文本信息，细品后就会发现它的灵活性。这只是 `JavaPoet` 的冰山一角，我会在下一篇文章《注解实践》中详细介绍它。

## 5 注意事项
1. AbstractProcessor 类在 javax 包中，所以 Android Module 不可使用此类，需要创建 Java library
2. Android Gradle 2.2 插件以上内置 apt 工具，使用 androidProcessor 引用依赖即可；在 Gradle 2.1 及以下使用 android-apt 工具
3. 注解处理器只能生产新代码，不能修改已存在的代码。（不是十分绝对的，但标准处理器是无法修改原文件）
4. 最好将注解与注解处理器分别存放两个不同的 Module，放在一起会出现以下问题
	- 要写两个相同的依赖 project，annotationProcessor 'your.processor.module' 和 implementation 'your.processor.module'，看起来很怪异
	- 会将只用于编译期的代码（处理器程序）打包到 apk 中，增加无谓的包体积

## 6 进阶学习
读源码
1. 本文所述 [Demo](https://github.com/zmer007/SimpleAnnotation)
2. [AutoService](https://github.com/google/auto/tree/master/service)
3. [ButterKnife](https://github.com/JakeWharton/butterknife)

**参考链接**
> https://en.wikipedia.org/wiki/Java_annotation

> https://medium.com/androidiots/writing-your-own-annotation-processors-in-android-1fa0cd96ef11

> https://github.com/google/auto/tree/master/service

> https://docs.oracle.com/javase/1.5.0/docs/guide/language/annotations.html

> https://stackoverflow.com/a/23973331/7785373

> http://www.lordofthejars.com/2018/02/repeatable-annotations-in-java-8.html

> https://docs.oracle.com/javase/7/docs/technotes/tools/solaris/javac.html#processing

> https://stackoverflow.com/a/15203417/7785373
