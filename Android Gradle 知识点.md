
# 什么是 Gradle

**Gradle 是一个基于 JVM 的现代化自动化构建工具**，它解决了传统构建工具（如 Ant、Maven）在灵活性、性能和可维护性上的痛点，并凭借其强大的特点，成为了 Android 开发的官方构建系统。

在软件工程中，**构建(Build) 是指将源代码(.java/.kt)转换为可执行软件(.apk/.jar)的过程**。这个过程涉及编译、资源处理、字节码混淆、打包、测试、部署等一系列繁琐步骤。

在Gradle出现之前，主流构建工具有两个，但它们都有明显的硬伤：

1. **Apache Ant(老古董)**：极度灵活，用 XML 写脚本，但**缺乏标准化**。你写你的，我写我的，构建脚本天差地别，且**没有内置的依赖管理**（需要自己下载 jar 包放进去）。
2. **Apache Maven(规范者)**：引入了“约定优于配置”，标准化了项目结构，并内置了**依赖管理**（从中央仓库拉取jar包）。但它的致命伤是**僵硬**——如果你想做点超出标准生命周期的事，就得写复杂的插件，XML 配置极其臃肿。

**Gradle 就是来“救场”的**，它完美地融合了 Ant 的**灵活性** 和 Maven 的**规范化/依赖管理**，并在此基础上解决了它们共同的难题。

# Gradle 的核心作用是什么

作为一个构建工具，它的具体工作就是**自动化执行以下流程**：

- **编译与打包**：调用 Java/Kotlin 编译器将源码编译成字节码，将资源文件(图片、布局)打包成 APK/AAR/JAR 文件。
- **依赖管理**：自动从远程仓库（Maven Central、Google 等）下载你的项目所需要的第三方库（如 Retrofit、Glide），并处理它们之间错综复杂的版本冲突。
- **发布部署**：将生成的产物上传到私有仓库，或签名打包成最终的上线版本。
- **测试执行**：自动运行单元测试和集成测试，并生成测试报告。

# Gradle 的核心特点是什么

**1.基于 DSL 的脚本，告别 XML 臃肿**

Maven 用 XML 配置，结构臃肿且逻辑表达能力极差。Gradle 直接用了真正的编程语言（**Groovy DSL** 或 **Kotlin DSL**）来写构建脚本。这意味着你可以在 `build.gradle` 里写 `if`、`for` 循环、甚至定义函数，构建逻辑瞬间变得清晰而灵活。

**2. 极致的性能(增量构建 & 构建缓存)**

这是Gradle的“杀手锏”。大型项目每次全量编译要几分钟，Gradle引入了 **增量构建(Incremental Build) 机制：

- 它会记录每个任务（Task）的输入（源码）和输出（class文件）的指纹（哈希值）。
    
- 如果输入没变，下次构建时**直接跳过该任务**。
    
- 配合 **构建缓存(Build Cache)**，甚至可以将其他人编译好的产物直接拿来复用，在大型团队中构建速度能提升数十倍。

**3. 基于任务(Task)的有向无环图(DAG)**

Gradle 的构建工作不是线性执行的，而是由一个个 **Task**（如 `compileJava`、`test`、`assemble`）组成。你运行 `gradle build` 时，Gradle 会先计算所有 Task 之间的依赖关系，形成一张**有向无环图(DAG)**。

- **好处**：你可以精确控制 Task 何时运行。比如 `gradle.taskGraph.whenReady` 就是在这个图构建好之后、Task 执行之前给你一个干预的机会。

**4.强大的多项目(Multi-Project)构建支持**

在大型应用中（比如微服务或 Android 多模块项目），Gradle 允许你在根目录下统一管理所有子模块。你可以在根目录的脚本中一键构建所有子模块，也可以单独构建某个子模块，子模块之间还能互相引用依赖，极其适合模块化开发。

# Gradle 配置介绍

`Gradle`的配置主要是用来管理 Gradle 自己的运行环境和我们的项目。

一个新建的 Android 项目初始的配置结构如下：

```text
├── README.md
├── app
│   ├── build.gradle
│   ├── ...
├── build.gradle
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── local.properties
└── settings.gradle
```
## gradle-wrapper

```text
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
└── gradlew.bat
```

`wrapper`是对 Gradle 的一层封装，封装的意义在于可以使 Gradle 的版本跟着项目走，可以很方便的在不同的设备上运行。

- `gradle-wrapper.jar`：主要是 Gradle 的运行逻辑，包含下载 Gradle
- `gradle-wrapper.properties`：gradle-wrapper 的配置文件，核心是定义了 Gradle 版本
- `gradlew`：gradle wrapper 的简称，linux 下的执行脚本
- `gradlew.bat`：windows 下的执行脚本

重点看下 gradle-wrapper.properties：

```gradle
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https://services.gradle.org/distributions/gradle-7.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- distributionBase：下载的 Gradle 的压缩包解压后的主目录
- distributionPath：相对于 distributionBase 的解压后的 Gradle 的路径，为 wrapper/dists
- distributionUrl：Gradle 版本的下载地址
- zipStoreBase：同 distributionBase，不过是存放 zip 压缩包的主目录
- zipStorePath：同 distributionPath，不过是存放 zip 压缩包的

Gradle版本的下载地址，可以查看Gradle官方的[版本发布](https://link.juejin.cn/?target=https%3A%2F%2Fservices.gradle.org%2Fdistributions%2F "https://services.gradle.org/distributions/")。

会有几种类型，分别是 all、bin、doc：

- `doc`：顾名思义，用户文档；
- `bin`：即 binary，可运行并不包含多余的东西；
- `all`：包含所有，除了 bin 之外还有用户文档、sample 等；

所以一般选择 bin 就可以了。

## build.gradle(Project)

位于项目的根目录下，用于定义适用于项目中所有模块的依赖项。

Gradle7.0 之后，project 下的 `build.gradle` 文件变动很大，默认只有 plugin 的引用了，其他原有的配置挪到`settings.gradle`文件中了。

```gradle
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id 'com.android.application' version '7.3.0' apply false
    id 'com.android.library' version '7.3.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.7.10' apply false
    
    //或者引用 libs.versions.toml 
    alias(libs.plugins.android.application) apply false
}
```

**`apply false` 到底要不要加？**

- **在根目录 `build.gradle` 中：必须加 `apply false`**。因为根项目不是一个 Android 应用，不能应用 Android 插件。
    
- **在 `app/build.gradle` 中：绝对不能加 `apply false`**。否则插件不生效，`android {}` 块会报错。
## build.gradle(Module)

位于每个 **project**/**module**/ 目录下，用于为其所在的特定模块配置 build 设置。

7.0 前后差别不大。以下是 7.0 后的初始配置：

```groovy
plugins {  
    alias(libs.plugins.android.application)  //libs.versions.toml 配置
}  
  
android {  
    namespace 'com.example.myapplication'  //应用程序的命名空间,主要用于访问应用程序资源。
    compileSdk 36  
  
    defaultConfig {  
        applicationId "com.example.myapplication"  
        minSdk 24  
        targetSdk 36  
        versionCode 1  
        versionName "1.0"  
  
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"  
    }  
  
    buildTypes {  
        release {  
            minifyEnabled false  
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'  
        }  
    }  
    compileOptions {  
        sourceCompatibility JavaVersion.VERSION_11  
        targetCompatibility JavaVersion.VERSION_11  
    }  
}  
  
dependencies {  
    //libs.versions.toml 配置
    implementation libs.androidx.appcompat  
    implementation libs.androidx.core.ktx  
    implementation libs.material  
    testImplementation libs.junit  
    androidTestImplementation libs.androidx.espresso.core  
    androidTestImplementation libs.androidx.junit  
}
```

常见配置的含义和用法随时查询即可，不在此处大量介绍了。
## settings.gradle

位于项目的根目录下，用于定义项目级代码库设置。

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()   // 插件门户
        google()
        mavenCentral()
        // 如果有私有插件仓库，也放在这里
        // maven { url 'https://my-private-plugin-repo.com' }
    }
}

dependencyResolutionManagement {
    // 可选模式：FAIL_ON_PROJECT_REPOS（默认）表示如果模块中还写了 repositories 会报错
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://my-private-repo.com/repo' }  // 所有模块共享
    }
}

rootProject.name = "MyAndroidNote"
include ':app'
```

## gradle.properties

位于项目的根目录下，用于指定 Gradle 构建工具包本身的设置，也可用于项目版本管理。

Gradle 本身配置：比如 Gradle 守护程序的最大堆大小、编译缓存、并行编译、是否使用 Androidx 等等。

版本管理：可以从 `gradle.properties` 文件中读取版本，不仅可以作用于依赖库，也可作用于依赖插件。本质上是`key-value`形式的参数。

```text
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
#并行编译 
org.gradle.parallel=true 
#构建缓存 
org.gradle.caching=true
//版本设置
zyxPluginVersion="1.0.0"
```
## local.properties

位于项目的根目录下，用于指定 Gradle 构建配置本地环境属性，也可用于项目环境管理。

```text
//本地配置
sdk.dir=/Users/zhangyunxiang/Library/Android/sdk
ndk.dir=/Users/zhangyunxiang/Library/Android/ndk

//环境管理，可以用作项目本地调试的一些开关
isRelease=true 
#isDebug=false 
#isH5Debug=false
```

# Gradle(Project)+setting.gradle 7.x 后配置变化

## 一、老版本中的 `buildscript` 和 `allprojects`

1.`buildscript` 块

**作用**：配置 Gradle 构建脚本**自身**所需的依赖和仓库。也就是说，它负责管理 Gradle 构建过程所依赖的插件、类库等（例如 Android Gradle Plugin、Kotlin Gradle Plugin）。

**典型内容**：
- `repositories`：指定去哪里下载这些构建依赖（如 google()、mavenCentral()、jcenter() 等）。
- `dependencies`：声明具体的构建插件，如 `classpath 'com.android.tools.build:gradle:4.2.2'`。

**注意**：`buildscript` 块中的依赖**不会**被你的应用代码（app 模块）所使用，只影响 Gradle 构建本身。

2.`allprojects` 块

**作用**：为**所有项目**（根项目 + 所有子模块）统一配置公共的仓库和任务。通常用于声明每个模块都需要使用的 Maven 仓库（比如 google()、mavenCentral() 或自定义的私有仓库）。

**典型内容**：
- `repositories`：每个模块在解析自己的依赖（如 androidx.appcompat）时会用到这些仓库。
- 偶尔也会包含一些通用的任务或插件应用。

## 二、为什么在 7.x 后迁移到 `settings.gradle`？

- **分离关注点**：`build.gradle` 主要关注具体模块的构建逻辑，而 `settings.gradle` 本就负责项目结构（模块列表、项目名称）的配置，将仓库管理和插件管理放到 `settings.gradle` 更符合单一职责原则。
- **避免配置泄漏**：老版本中 `allprojects` 块的配置会无条件应用到所有模块，有时子模块并不需要某些仓库，这种隐式传播容易引发依赖解析问题。
- **性能优化**：集中式仓库声明可以让 Gradle 在配置阶段就确定所有仓库列表，减少重复解析。
- **支持版本目录（Version Catalogs）**：新版本引入的集中依赖管理功能，与 `settings.gradle` 中的 `dependencyResolutionManagement` 更紧密集成。

## 三、`pluginManagement` 和 `dependencyResolutionManagement` 的含义及对应关系

1.`pluginManagement`

- **含义**：专门用于管理 Gradle 插件的**解析规则**和**版本**。它告诉 Gradle 去哪里下载插件（如 `com.android.application`、`org.jetbrains.kotlin.android`），以及这些插件的默认版本。
- **对应关系**：它并不完全替代 `buildscript` 块，而是与 `buildscript` 中的插件依赖**协同工作**。在旧写法中，插件通过 `buildscript { dependencies { classpath ... } }` 声明；**新写法中，推荐在 `settings.gradle` 的 `pluginManagement` 中统一声明插件仓库，然后在根 `build.gradle` 的 `plugins` 块中直接使用 `id` 和可选版本**，不再需要 `buildscript` 块。
  - **旧模式**：`buildscript` 负责下载插件 JAR，然后通过 `apply plugin: '...'` 应用。
  - **新模式**：`pluginManagement` 负责提供插件解析的仓库和版本约束，`plugins { id '...' version '...' }` 直接使用。
- **注意**：`pluginManagement` 是**可选的**，如果没有它，Gradle 会使用默认的插件解析机制（从 Gradle 插件门户、`buildscript` 仓库等）。

2.`dependencyResolutionManagement`

- **含义**：集中管理**项目依赖的仓库**（即应用程序模块所依赖的第三方库，如 `androidx.appcompat` 从哪里下载）。它替代了老版本中 `allprojects` 块里的 `repositories` 声明。
- **对应关系**：`dependencyResolutionManagement` 中的 `repositories` 块与 `allprojects { repositories }` **功能等效**，但作用域更可控（可以设置 `repositoriesMode` 来禁止模块单独声明仓库）。
  - **旧模式**：每个模块（或通过 `allprojects`）各自声明仓库。
  - **新模式**：所有模块共享 `settings.gradle` 中定义的仓库，无法在模块级再轻易覆盖（除非设置 `FAIL_ON_PROJECT_REPOS` 或 `PREFER_PROJECT` 模式）。
- **好处**：统一管理，避免模块私自添加不安全或重复的仓库，使构建更可预测。

## 四、新旧配置对比示例

下面给出一个典型的 Android 项目（包含 `app` 模块）在升级前后的配置文件对比。

示例项目结构

```
MyAndroidNote/
├── build.gradle          (根)
├── settings.gradle       (或 settings.gradle.kts)
├── app/
│   └── build.gradle
```

### 1️⃣ 旧版本配置（Gradle < 7.0，AGP < 7.0）

`settings.gradle`（通常很简单，只包含模块列表）
```groovy
include ':app'
```

 根目录 `build.gradle`
```groovy
// Top-level build file
buildscript {
    repositories {
        google()
        jcenter()  // 老版本常用 jcenter
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:4.2.2'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31'
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        maven { url 'https://my-private-repo.com/repo' }
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

### 2️⃣ 新版本配置（Gradle 7.x+，AGP 7.x+）

`settings.gradle`（集中管理插件和依赖仓库）
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()   // 插件门户
        google()
        mavenCentral()
        // 如果有私有插件仓库，也放在这里
        // maven { url 'https://my-private-plugin-repo.com' }
    }
}

dependencyResolutionManagement {
    // 可选模式：FAIL_ON_PROJECT_REPOS（默认）表示如果模块中还写了 repositories 会报错
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://my-private-repo.com/repo' }  // 所有模块共享
    }
}

rootProject.name = "MyAndroidNote"
include ':app'
```

根目录 `build.gradle`（变得非常简洁，只留 plugins 块和任务）
```groovy
plugins {
    // 推荐写法：只在 settings.gradle 中管理版本，这里只写 id。见下方可选内容
    id 'com.android.application' apply false
    id 'com.android.library' apply false
    id 'org.jetbrains.kotlin.android' apply false
}

// 如果还有一些全局的任务，可以保留
task clean(type: Delete) {
    delete rootProject.buildDir
}
```

> **注意**：在新模式中，`buildscript` 块通常不再需要，因为 `pluginManagement` 已经接管了插件仓库，而 `plugins` 块中的插件会自动从 `pluginManagement` 的仓库中解析。如果你仍需要使用一些自定义的构建脚本类库（不属于标准插件），可以保留 `buildscript`，但通常不推荐。

### 可选：如果要在 `settings.gradle` 中指定插件版本

可以在 `pluginManagement` 内增加 `plugins` 块来统一版本：
```groovy
pluginManagement {
    repositories { ... }
    plugins {
        id 'com.android.application' version '7.4.2'
        id 'com.android.library' version '7.4.2'
        id 'org.jetbrains.kotlin.android' version '1.8.0'
    }
}
```
然后在根 `build.gradle` 中只需：
```groovy
plugins {
    id 'com.android.application' apply false
    id 'com.android.library' apply false
    id 'org.jetbrains.kotlin.android' apply false
}
```
这样所有模块使用相同版本，集中管理。

## 五、总结对比表格

| 概念 | 旧版本（<7.0） | 新版本（7.x+） |
|------|----------------|----------------|
| **插件仓库** | `buildscript { repositories }` | `settings.gradle` 中的 `pluginManagement { repositories }` |
| **插件依赖** | `buildscript { dependencies { classpath } }` | 不再需要 `classpath`，改用 `plugins { id }`，配合 `pluginManagement` 中的版本声明 |
| **模块依赖仓库** | `allprojects { repositories }` 或每个模块单独声明 | `settings.gradle` 中的 `dependencyResolutionManagement { repositories }` |
| **根 `build.gradle`** | 包含 `buildscript` 和 `allprojects` 等大量配置 | 仅保留 `plugins` 块和通用任务，通常非常简洁 |
| **`settings.gradle`** | 只包含 `include` 等基本项目结构 | 新增 `pluginManagement` 和 `dependencyResolutionManagement`，成为配置核心 |

# 如何开发配置 Gradle

面向开发者的脚本语言是`Groovy`和`Kotlin`，最后的表现是 `DSL`。

## DSL (领域特定语言) —— 构建脚本的“装修风格”

DSL 的全称是 Domain Specific Language，即**领域特定语言**。它不是一门像 Java 那样通用的编程语言，而是为解决特定领域问题而设计的“小语言”。

在 Gradle 的语境下，DSL 就是一套让你能用更**简洁、自然**的方式（而非复杂的标准编程语法）来描述“项目该怎么构建”的规则。它就像是建筑图纸上的符号，让建筑师能快速表达设计意图。

## Groovy DSL vs Kotlin DSL —— 两种“施工方法”

| 特性       | Groovy DSL          | Kotlin DSL                                  |
| -------- | ------------------- | ------------------------------------------- |
| **脚本文件** | `build.gradle`      | `build.gradle.kts`                          |
| **语言类型** | **动态类型**语言          | **静态类型**语言                                  |
| **优点**   | 语法极其灵活，可省略大量符号，代码简洁 | 强大的** IDE 支持**（代码补全、错误提示）、**编译时类型检查**，更安全可靠 |
| **缺点**   | IDE 支持较弱，错误不易在编写时发现 | 语法相对严格，构建速度可能稍慢                             |
| **趋势**   | 传统默认语言，目前仍广泛使用      | **Google 官方推荐**，新项目默认选项                     |

**语法差异示例**：

| 场景              | Groovy (`build.gradle`)        | Kotlin (`build.gradle.kts`)          | 记忆口诀                              |
| --------------- | ------------------------------ | ------------------------------------ | --------------------------------- |
| **1. 插件 ID**    | `id 'com.android.application'` | `id("com.android.application")`      | Kotlin **必须加括号**（函数调用）            |
| **2. 版本赋值**     | `compileSdk 32`                | `compileSdk(32)` 或 `compileSdk = 32` | Kotlin **加等号或括号二选一**              |
| **3. 依赖声明**     | `implementation 'xxx:yyy:1.0'` | `implementation("xxx:yyy:1.0")`      | Kotlin **必须加括号**                  |
| **4. 字符串引号**    | 单引号 `'` 或双引号 `"` 都行            | **必须双引号 `"`**                        | Kotlin **只认双引号**（单引号报错）           |
| **5. 字符串插值**    | `"Hello $name"`                | `"Hello ${name}"`                    | Kotlin **必须加花括号**（除非简单变量）         |
| **6. 变量定义**     | `def myVar = "hello"`          | `val myVar = "hello"` 或 `var`        | Groovy 用 `def`，Kotlin 用 `val/var` |
| **7. List/Map** | `[1, 2, 3]`                    | `listOf(1, 2, 3)`                    | Kotlin **必须用标准库函数**               |
| **8. 扩展属性**     | `ext.myProp = "value"`         | `extra["myProp"] = "value"`          | Kotlin 操作 `extra` 有专用 API         |

## 闭包 (Closure) / Lambda —— 通用的“标准件”

如果说 DSL 是“风格”，语言是“方法”，那么闭包(Groovy 中)或 Lambda(Kotlin 中)就是这两种方法都离不开的“标准件”。

- **它是什么？** 你可以把它理解为一个**可以像数据一样传来传去的代码块**，或者一个**匿名函数**。
    
- **它的作用？** Gradle 脚本中大量的配置，都是通过将“闭包/Lambda”作为参数传递给相应的方法来实现的。这是实现 DSL 简洁风格的关键。
    
- **具体是如何工作的？**  
    以 `android { ... }` 为例，`android`是一个方法，而它后面花括号`{...}`里的所有内容，就是一个闭包/Lambda。这个方法接收这个代码块作为参数，然后在内部执行它，从而完成对Android构建的各种配置。
    
    Gradle 为了让你写起来更舒服，还允许你**省略方法调用的括号**。所以，你看到的：

```groovy
android {
    compileSdk 32
}
```

在 Gradle 眼中，它本质上就是：

```groovy
android({ compileSdk(32) })
```

只是括号被省略了，让它看起来更像是一段“配置”，而不是“代码”。

## 总结三者的关系

**DSL** 是 Gradle 为了简化构建配置而设计的**书写风格**。
**Groovy** 和 **Kotlin** 是两种不同的**实现语言**。
**闭包/Lambda** 则是实现这种风格所依赖的**核心语法机制**。

# 闭包和 lambda 之间的关系是什么，lambda 有哪些语法规则

在 Gradle 的语境里，**闭包(Closure) 和 Lambda** 既可以说“差不多”，又可以说“完全不同”。这取决于你站在**概念层面还是技术实现层面来看**。

## 概念层面的关系

如果你是一个**使用Gradle的开发者**，在日常交流中，这两个词几乎可以**互换**。它们都指代那些写在花括号 `{}` 里、作为参数传给 Gradle API 的“代码块”。

- **功能上**：它们的作用完全一样，都是为了**延迟执行**。Gradle 先把你的 `{ ... }` 代码块收起来，等到执行到相应阶段（比如解析`android`配置时），再回过头来运行它。
    
- **称呼上**：说“把这个闭包传进去”或“把这个 Lambda 传进去”，大家在沟通中都能明白。

## 技术实现层面的关系

一旦深入到编程语言层面，它们就**截然不同**，因为 Groovy 和 Kotlin 是两门完全独立的语言。

**简单总结两者的关系：** 在 Gradle 中，它们就是解决同一类问题（配置构建）的两种不同语言的**语法糖**。**概念上互为替代品，技术上毫无血缘关系。**

## Kotlin Lambda 的核心语法规则

## 1. 基本形态：花括号 + 箭头

Lambda 必须用花括号 `{}` 包起来。参数列表在 `->` 左边，执行体在右边。

```kotlin
// 标准写法：两个Int参数，返回Int
val sum = { a: Int, b: Int -> a + b }
```

## 2. 只有一个参数时，可用 `it` 隐式代替

如果Lambda只有一个参数，Kotlin允许你省略它并直接用 `it` 指代。

```kotlin
// 完整写法
list.filter { item -> item > 0 }
// 简写（使用it）
list.filter { it > 0 } // 这行在Gradle中非常常见
```

## 3. 如果Lambda没有参数，`->` 可以完全省略

```kotlin
val runnable = { println("Hello") } // 没有箭头，直接写执行体
```

## 4. 最后一行表达式是返回值(自动返回)

Lambda内部不需要写 `return`，最后一行代码的结果自动作为返回值。

```kotlin
val result = { a: Int -> 
    val b = a * 2
    b + 10  // 这行就是最终返回值
}
println(result(5)) // 输出 15
```

## 5. ⭐️ 最重要的规则：尾随 Lambda(Trailing Lambda)

如果函数的**最后一个参数**是一个Lambda，你可以把它**移到括号外面**，甚至如果只有这一个参数，括号都可以省略。

```kotlin
android({ 
    compileSdk(32) 
})

android() { 
    compileSdk(32) 
}

android { 
    compileSdk(32) 
}
```

# 为什么需要 lambda 解决了什么问题，只有 kotlin 才有 lamdba 的概念吗，Java  有吗

1. **需要 Lambda，是为了解决“把代码像数据一样传递”的刚需，并消灭冗余的“样板代码”。**
    
2. **绝对不是**，Lambda(或匿名函数)是几乎所有现代主流语言的标配。
    
3. **Java 有**，但 **Java 的 Lambda 是“残缺的接口糖”**，而 **Kotlin 的 Lambda 是“完整的函数体”**。

在没有 Lambda 的时代（比如 Java 7 或更早），如果你想给一个按钮设置点击事件，或者想对一组数据进行排序，你必须写**匿名内部类**（Anonymous Inner Class）。

```java
// 古代写法（Java 7）
button.setOnClickListener(new OnClickListener() {
    @Override
    public void onClick(View v) {
        System.out.println("Hello");
    }
});

//真正有业务价值的只有 System.out.println("Hello") 这一行

// Lambda 写法(Java 8+)，Lambda 就是为了干掉冗余的匿名内部类而生的语法糖。
button.setOnClickListener(v -> System.out.println("Hello"));
```

**Java Lambda VS Kotlin Lambda**

虽然都叫 Lambda，但它们的**实现原理**不同，这直接影响了在 Gradle 脚本中的体验。

1.Java 的 Lambda：基于“接口”（SAM 转换）

Java 本质上还是面向对象的语言。Java 的 Lambda 必须依附于一个**只有一个抽象方法的接口**（叫函数式接口，比如 `Runnable`, `Callable`, `Comparator`）。Java 编译器在背后会把它**自动转型**成该接口的实例，你不能在 Java 中定义一个“纯粹的函数类型”变量，只能定义为接口类型。

2.Kotlin 的 Lambda：基于“函数类型”（First-class citizen）

Kotlin 把函数提升到了和 Int、String 同等的地位(一等公民)。在 Kotlin 中，**函数本身就是一种类型**。

- 你可以写：`val sum: (Int, Int) -> Int = { a, b -> a + b }`
- 这里的 `(Int, Int) -> Int` 就是一个实实在在的**类型**，不需要任何接口来包装。

# Gradle 的生命周期

Gradle 的生命周期是一个非常重要的概念，不同阶段抛出的“钩子”（Hooks），用于让我们执行自定义逻辑，能在生命周期的各个阶段做一些切面处理的「黑科技」。

Gradle 分三个阶段评估和运行构建，分别是 `Initialization (初始化)`、`Configuration (配置)` 和 `Execution (执行)`，且任何的构建任务都会执行这个三个阶段。

1. **初始化阶段 (Initialization)**：解析`settings.gradle(.kts)`，确定有哪些项目(Project)参与构建。
    
2. **配置阶段 (Configuration)**：解析每个项目的`build.gradle(.kts)`，配置项目并构建所有任务(Task)的依赖关系图(DAG)。
    
3. **执行阶段 (Execution)**：根据依赖关系图，按顺序执行被请求的任务(Task)及其依赖。

一次完整的 Gradle 构建，这些事件的触发顺序如下：

1. **初始化阶段开始**
    
2. ➡️ **`gradle.settingsEvaluated`**：`settings.gradle`执行完毕。
    
3. ➡️ **`gradle.projectsLoaded`**：所有`Project`对象创建完成，初始化阶段结束。
    
4. **配置阶段开始**（对每个项目循环执行）
    
5. ➡️ **`project.beforeEvaluate`**：当前项目的`build.gradle`执行前。
    
6. **执行当前项目的 `build.gradle` 脚本**。
    
7. ➡️ **`project.afterEvaluate`**：当前项目的`build.gradle`执行后。
    
8. **配置阶段结束**（所有项目配置完毕）
    
9. ➡️ **`gradle.projectsEvaluated`**：所有项目配置完成。
    
10. ➡️ **`gradle.taskGraph.whenReady`**：任务依赖图构建完成。
    
11. **执行阶段开始**（对每个被请求的任务循环执行）
    
12. ➡️ **`TaskExecutionListener.beforeExecute`**：当前任务执行前。
    
13. ➡️ **`TaskActionListener.beforeActions`**：当前任务的动作执行前。
    
14. **执行当前任务的 Actions**。
    
15. ➡️ **`TaskActionListener.afterActions`**：当前任务的动作执行后。
    
16. ➡️ **`TaskExecutionListener.afterExecute`**：当前任务执行后。
    
17. **执行阶段结束**
    
18. ➡️ **`gradle.buildFinished`**：整个构建结束。

**注意事项**

1. **注册位置至关重要**：`settingsEvaluated`、`projectsLoaded`以及`project.beforeEvaluate`都发生在项目脚本（`build.gradle`）执行之前。因此，**`project.beforeEvaluate`不能在当前的`build.gradle`中注册**，因为脚本执行时，这个事件已经错过了。它必须在`settings.gradle`或`init script`中注册。
2. **`afterEvaluate`的多次调用**：如果一个项目配置了多个`afterEvaluate`回调，它们的执行顺序与注册顺序一致。
3. **`TaskExecutionListener` vs `TaskActionListener`**：
    
    - `TaskExecutionListener`监听的是**整个任务**的生命周期。
    - `TaskActionListener`监听的是任务内部具体的**动作（Actions）**。一个任务可能包含多个Action，`TaskActionListener`会为每个 Action 触发`beforeActions`和`afterActions`。
    
4. **`gradle.taskGraph.whenReady`的时机**：这个事件标志着**配置阶段正式结束，执行阶段即将开始**。此时，所有任务的依赖关系已经确定，但还没有任何任务开始执行。

## 生命周期方法应该在哪里注册

**在 Gradle 中，每一个`build.gradle`文件都对应且仅对应一个`Project`对象。**  
根目录下的`build.gradle`代表**根项目（Root Project）**，`app/`下的`build.gradle`代表**子项目(Subproject/Module)**。

|生命周期事件 / 监听器|推荐注册文件|理由（为什么不能放其他地方）|
|---|---|---|
|`gradle.settingsEvaluated`|**`settings.gradle`**|此事件发生时，连`build.gradle`都没加载，别处注册不到。|
|`gradle.projectsLoaded`|**`settings.gradle`**|同上，属于初始化阶段专属。|
|`project.beforeEvaluate`|**`settings.gradle`**  <br>（用`gradle.beforeProject`）|若写在某个`build.gradle`里，注册时机已晚，无法捕捉到自己的`before`事件。|
|`project.afterEvaluate`  <br>（针对当前单个模块）|该模块自身的 **`build.gradle`**|只为这一个模块定制逻辑，清晰且隔离。|
|`project.afterEvaluate`  <br>（针对所有子模块）|根目录 **`build.gradle`**  <br>（结合`subprojects`块）|避免在每个子模块里复制粘贴，统一维护。|
|`gradle.projectsEvaluated`|根目录 **`build.gradle`**|全局配置完成标志，理应放在全局入口（根项目）。|
|`gradle.taskGraph.whenReady`|根目录 **`build.gradle`**|全局任务图构建，只需监听一次。|
|`TaskExecutionListener`|根目录 **`build.gradle`**  <br>或 独立的 **Plugin**|全局执行监控，放在子模块会造成重复实例，浪费性能。|
|`gradle.buildFinished`|根目录 **`build.gradle`**  <br>或 独立的 **Plugin**|收尾工作放在全局根目录，确保无论构建哪个模块都能捕获到。|

# Gradle 常用命令与参数

Gradle执行命令行主要用到的是`Gradle Wrapper`，使用 `./gradlew [taskName...] [--option-name...]`(Mac) 格式触发。

命令大全：./gradlew --help
查看版本：./gradlew -v
检查依赖并编译打包：./gradlew build
执行 task：./gradlew taskname
编译并打出 Debug/release 包：./gradlew assembleDebug/assembleRelease
Debug/Release 编译并打印日志: ./gradlew assembleDebug --info 或 ./gradlew assembleRelease --info
编译并打印堆栈日志：./gradlew assembleDebug -s
查看依赖输出到文件：./gradlew app:dependencies > dependencies.txt

## 动态参数

`--project-prop`，我们一般常用`-P`表示，用来设置根项目的项目属性。

./gradlew assembleDebug -PisTest=true，用 -P 传了一个`isTest`字段，并赋值为`true`。

判断参数：hasProperty("isTest")
获取参数值：getProperty('isTest')

# Gradle 依赖管理和版本决议

## 依赖管理

在构建过程中，Gradle 首先会先从本地检索，找不到就挨个从远端仓库(中央仓库)找，找到之后会下载下来缓存到本地，默认缓存 24h，可以加速下次构建，也避免了不必要的网络下载。

依赖类型

```groovy
dependencies { 
// Dependency on a local library module 
implementation project(':mylibrary') 
// Dependency on local binaries 
implementation fileTree(dir: 'libs', include: ['*.jar']) 
// Dependency on a remote binary 
implementation 'com.example.android:app-magic:12.3' }
```
- 本地模块：需要在 settings.gradle 中 include 声明。
- 本地二进制文件：需要在 build.gradle 声明路径。
- 远端二进制文件：上述示例，也是用的最多的一种。

GAV

```groovy
dependencies { 
   implementation 'com.google.android.material:material:1.8.0' 
}
```

这种依赖声明有一套明确的规则，每个依赖需要有唯一的 id 才能被准确定位，也就是**GAV(坐标): groupId + artifactId + version** 。

- **groupId：** 组织名称，一般是公司域名倒写，包名；
- **artifactId：** 项目名称，如果 groupId 包含了项目名称，这里就是子项目名称；
- **version：** 版本号，一般由 3 位数字组成（x.y.z）；
![[GAV 依赖示意图.png]]

依赖传递

**`api` 会将依赖“泄露”给上游模块，而 `implementation` 会把依赖“隐藏”在模块内部**，这直接决定了**编译速度**和**模块耦合度**。

两者对比

1. **编译速度**（最显著的差异）：
    
    - 使用 **`implementation`** 能极大**缩短增量编译时间**。因为模块间依赖被隔离，修改底层库时不会触发上游模块的重新编译。
        
    - 滥用 **`api`** 会导致轻微的底层库改动就引发**全项目重编译**（级联反应）。
        
2. **代码隔离与封装**：
    
    - `implementation` 强制你遵守**最少知识原则**。模块 B 的内部实现细节（用了什么网络库、数据库）不应暴露给 A，这有利于后续重构。
        
    - `api` 破坏了封装，将 B 的内部结构暴露给了外部。

最佳实践

- **默认首选 `implementation`**：除非有特殊理由，否则一律使用它。这是 Google 官方推荐的**最佳实践**。
    
- **何时必须用 `api`**：
    
    - 你的模块 B **拆分了接口和实现**，需要让 A 感知到 B 依赖的某个库类型（例如 B 继承自 C，A 需要直接操作这个继承关系）。
        
    - 多模块工程中的**底层基础库**（例如 `common` 模块）需要暴露第三方库给上层，但这种情况更推荐在 `common` 中用 `api`，其余层都用 `implementation`。

## 版本决议

**版本决议是指在某个依赖出现多个版本的时候（版本冲突），Gradle 如何选择最终的版本来参与编译。**

- 同一个模块的多个相同依赖，优先选择最高版本。

- 多个模块的多个相同依赖，在没有约束条件的情况下，Gradle 会选择「最新策略」的决议方式，即选择最高版本。

- 版本号带字母后缀，优先看前面的基础版本大小。它会先比较“基础版本”（如 `1.2.3`），如果相同再比较“限定符”（如 `-alpha`, `-beta`）。例如 `1.2.3-alpha` < `1.2.3` < `1.2.3-beta`。

- force 优先级高于 strictly，如果二者同时显式声明，则会报错。**`strictly` 是更现代、更推荐的方式**

- **`strictly` (严格版本)**：作用于**单个依赖声明**，会覆盖传递依赖中的其他 `strictly` 声明。如果冲突无法解决，Gradle 会报错。
    
- **`force` (强制版本)**：作用于**整个配置（Configuration）**，如 `compileClasspath`。它会强制该配置中所有依赖都使用指定版本，甚至能覆盖其他 `strictly` 声明。

- 同时使用 force 强制依赖版本时，版本决议的结果跟依赖顺序有关，最早 force 的版本优先。

**现代 Gradle 官方的最佳实践是：**

> 优先使用 `strictly` 来精确控制版本，并尽量避免使用 `force`。

# Gradle Task 

## 什么是 Task

`Task`是一个任务，是`Gradle`中最小的`构建单元`。Gradle 构建的核心就是由 Task 组成的有向无环图，Task 管理了一个`Action`的 List，你可以在 List 前面插入一个 Action（doFirst），也可以从 list后面插入（doLast），Action 是最小的`执行单元`。

## Task 写在哪

Task 是由`Project`对象决定的，所以 Task 的创建是在 Project 中，一个 build.gradle 文件对应一个Project 对象，所以我们可以直接在 build.gradle 文件中创建 Task，**只要运行的上下文在 Project 中就可以**。

## 创建 Task

创建 Task 需要使用 TaskContainer 的 register 方法。

register的几种方式：

```text
1. register(String name, Action<? super Task> configurationAction)
2. register(String name, Class type, Action<? super T> configurationAction)
3. register(String name, Class type)
4. register(String name, Class type, Object... constructorArgs)
5. register(String name)
```

比较常用的是 1 和 2。

- configurationAction 指的是 Action，也就是该 Task 的操作，会在编译时执行。
- type 类型指的是 Task 类型，可以是自定义类型，也可以指定自带的 Copy、Delete、Zip、Jar等类型。

在 build.gradle 文件中创建一个 task：
```grovvy
tasks.register("zyx") { 
  println("Task Name = " + it.name) 
}
//使用 ./gradlew zyx 执行 task，多个 task 用空格间隔
```

通过 Plugin 的方式也可以创建 Task，重写的 apply 方法会有 Project 对象。

## Task Action

Task的 Action 就是编译时所需的操作，它不是一个，它是一组，可以有多个。一个 Task 内部维护了一个**有序的 Action 列表**。当执行 `./gradlew myTask` 时，Gradle 会**按顺序**遍历这个列表并执行。

这个列表的顺序是如何构建的？

1. **首先**，Gradle 把你在自定义类中用 `@TaskAction` 标记的方法，封装成一个 Action，**放在列表的最中间（作为核心）**。
    
2. **然后**，你调用 `doFirst`，Gradle 会把你的代码**插入到列表的头部**（类似压栈，所以写得越晚，反而越靠前）。
    
3. **最后**，你调用 `doLast`，Gradle 会把你的代码**追加到列表的尾部**。

所以，一个 Task 执行时的**真实执行顺序**是：

> **所有 `doFirst` 闭包（按注册倒序） -> `@TaskAction` 方法（如果有且仅有一个） -> 所有 `doLast` 闭包（按注册正序）**

示例：
```groovy
class DemoTask extends DefaultTask {

    @Internal   // 表示这个属性不参与 Gradle 的 up-to-date 检查
    def taskName = "default"

    @TaskAction
    def MyAction() {  // 只写一个
        println("$taskName -- 执行核心动作")
    }
}

tasks.register("zyx", DemoTask) {
    taskName = "我是传入的"
    doFirst { println "前置校验 1" }
    doFirst { println "前置校验 2" } // 这个会先执行（因为是倒序插入头部）
    doLast { println "后置清理" }
}

输出结果：
 前置校验 2
 前置校验 1
 我是传入的 -- 执行核心动作
 后置清理
```

注意事项：
1. **自定义 Task 类中，永远只写一个 `@TaskAction` 方法。**
2. **如果需要多个步骤，在注册 Task 时使用 `doFirst` 和 `doLast` 灵活追加。**

## Task 属性

```grovvy
String TASK_NAME = "name";

String TASK_DESCRIPTION = "description";

String TASK_GROUP = "group";

String TASK_TYPE = "type";

String TASK_DEPENDS_ON = "dependsOn";

String TASK_OVERWRITE = "overwrite";

String TASK_ACTION = "action";
```

## Task 依赖

dependsOn(依赖)

```grovvy
def zyx111 = tasks.register("zyx111") {
    it.doLast {
        println("${it.name}")
    }
}

def zyx222 = tasks.register("zyx222") {
    it.doLast {
        println("${it.name}")
    }
}

zyx111.configure {
    dependsOn zyx222
}

Task zyx111 在目标 Task zyx222 之后执行。
```

finalizedBy(收尾)

指定下一个执行的 Task，与 dependsOn 相反。

## 查找 Task

- findByPath/findByName(String path)，参数可空。
- getByPath/getByName(String path)，参数可空，找不到 Task 会抛异常 UnknownTaskException。

## 增量构建

**只要记住这三句话就够了：**

1. Gradle 会给每个 Task 的输入(源码)和输出(class文件)打指纹(MD5)。
    
2. 如果指纹没变，下次构建直接**跳过**该 Task(这就是增量)。
    
3. 如果你自定义 Task 操作了文件，忘记用 `@Input` 声明，Gradle 就会误以为文件没变，导致**改了代码却没编译的灵异事件**。知道这一点，能解决你未来 90% 的构建缓存坑。

## 跳过 Task

1.onlyIf 或 enabled = false 表示跳过当前任务

**场景**：我们创建一个任务，只有当你明确传入 `-PdoHeavy` 参数时才执行，否则直接跳过。

```grovvy
// 在 app/build.gradle 中

tasks.register("heavyTask") {
    // 1. 设置跳过条件：只有 project.hasProperty('doHeavy') 为 true 时才执行
    onlyIf { project.hasProperty('doHeavy') }
    
    doLast {
        println ">>>> 执行了巨耗时的任务（比如全量资源压缩）<<<<"
    }
}
```
只有明确执行了 ./gradlew heavyTask -PdoHeavy，doLast 才会执行。

# Gradle Plugin(插件)

## 什么是 Gradle 插件

Gradle 插件是一个**可复用的构建逻辑单元**。它本质上是一段代码，被打包成 JAR 文件，可以被不同的 Gradle 项目引入和使用。插件通过将通用的构建逻辑（如编译、打包、代码检查）封装起来，让构建脚本（`build.gradle`）变得更简洁、更可维护。

## 为什么需要 Gradle 插件？它的核心作用是什么？

插件主要解决了代码复用和标准化的问题。

它的核心作用包括：

- **复用构建逻辑**：避免在每个项目中重复编写相同的任务配置。
    
- **引入约定**：提供“约定优于配置”的能力。例如，Android Gradle 插件（AGP）引入了标准的 Android 项目结构（`src/main/java`， `src/main/res`），开发者无需手动配置。
    
- **扩展 Gradle 模型**：为 `Project` 对象添加新的功能（如创建新的 Task）和新的配置 DSL（如 `android {}` 块）。

## Gradle 插件有哪几种类型

主要有两种类型：**脚本插件** 和 **二进制插件**。

1. **脚本插件（Script Plugin）**
    
    - **定义**：直接写在 `build.gradle` 文件中的一段构建逻辑。或者是一个独立的 `.gradle` 文件，可以在主脚本中通过 `apply from: 'other.gradle'` 引入。
        
    - **作用**：快速、轻量，适合在同一个项目内抽取重复的配置片段。
        
    - **缺点**：无法跨项目复用，维护成本高。
        
2. **二进制插件（Binary Plugin）**
    
    - **定义**：实现了 `Plugin<Project>` 接口的类，被打包成 Jar 文件。它可以是 `.jar` 文件、在 `buildSrc` 目录中，或发布到 Maven 仓库。
        
    - **作用**：这是官方推荐的**最佳实践**。它易于测试、版本管理，并且可以被多个项目安全地共享和使用。

## 自定义 Gradle 插件

**第一步：实现 `Plugin<Project>` 接口**

位于插件**源码目录**。

- **具体路径**：
    
    - 如果是在 `buildSrc` 中：`buildSrc/src/main/kotlin/com/example/MyCustomPlugin.kt` （或 groovy/java）
        
    - 如果是在独立插件模块（如 `build-logic`）：`build-logic/plugin/src/main/kotlin/com/example/MyCustomPlugin.kt`
        
- **说明**：这里只定义 `apply` 方法里的业务逻辑。

```kotlin
// 1. 创建一个类，实现 Plugin<Project> 接口
class MyCustomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 在这里编写你的构建逻辑
        project.tasks.register("myHelloTask") {
            doLast {
                println("Hello from my custom plugin!")
            }
        }
    }
}
```

**第二步：在 `build.gradle.kts` 中注册插件（定义 ID）**

位于插件**自身的构建脚本**中。

- **具体路径**：
    
    - `buildSrc/build.gradle.kts` （如果使用 buildSrc）
        
    - `build-logic/plugin/build.gradle.kts` （如果使用独立模块）

```kotlin
plugins {
    id 'java-gradle-plugin' // 提供 gradlePlugin {} DSL
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "com.example.my-plugin" // 用户将使用这个 ID 来引用
            implementationClass = "com.example.MyCustomPlugin" // 映射到你的实现类
        }
    }
}
```

`java-gradle-plugin` 是 Gradle 官方提供的一个**核心插件**，它的作用就是**极大地简化 Gradle 插件的开发和发布流程**。主要有以下关键作用:

1. 只需在 `build.gradle` 的 `gradlePlugin` 块中声明插件的 `id` 和 `implementationClass`，它就会在编译时**自动生成**所有必需的元数据文件（包括 `.properties` 文件和 Plugin Marker Artifacts），完全不需要你手动创建。
2. **自动**将 `gradleApi()` 依赖添加到你的项目中。你不需要在 `build.gradle` 中写任何关于 Gradle API 的依赖声明。
3. **自动**为你在 `gradlePlugin` 块中声明的每个插件创建对应的 Plugin Marker 工件，并与主 Jar 包一起发布。用户可以直接使用 `plugins { id("com.example.my-plugin") version "1.0" }` 引用，无需任何额外配置。
4. 提供了 `gradlePlugin {}` 这个类型安全的 DSL 块。你在其中配置 `id` 和 `implementationClass` 时，IDE 会提供自动补全和类型检查，减少拼写错误。

**第三步：在目标项目的 `build.gradle.kts` 中应用插件**

- **文件位置**：位于**目标业务模块**（如 `app`）的构建脚本中。
    
- **具体路径**：`app/build.gradle.kts` （或 `lib/build.gradle.kts`）

```kotlin
// 用户项目的 build.gradle.kts
plugins {
    id("com.example.my-plugin") // 通过 ID 应用
}
```

## 发布自定义插件

### 第一步：在插件模块中应用插件

在插件模块的 `build.gradle.kts` 中：

```kotlin
// file: plugin-module/build.gradle.kts
plugins {
    id("java-gradle-plugin")     // 开发 Gradle 插件的核心插件
    id("maven-publish")           // 发布插件
}

// 定义插件的基本信息（自动生成 META-INF 和 Plugin Marker）
gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "com.example.my-plugin"
            implementationClass = "com.example.MyPlugin"
        }
    }
}

// 配置发布信息（和 AAR 类似）
group = "com.example"
version = "1.0.0"

publishing {
    //定义要发布什么（产物）
    publications {
        create<MavenPublication>("myPlugin") {
            // 自动包含插件 JAR + Plugin Marker
            from(components["java"])
            artifactId = "my-plugin" //如果不设置，默认取 module name 值。
            // 或者简写为：from(components["kotlin"]) 如果使用 Kotlin
        }
    }
    //定义发布到哪里（目标仓库）
    repositories {
        mavenLocal()
        maven {
            name = "RemoteNexus"
            url = uri("https://nexus.example.com/repository/maven-releases/")
            //访问凭证
            credentials {
                username = project.findProperty("nexusUsername") as? String ?: ""
                password = project.findProperty("nexusPassword") as? String ?: ""
            }
        }
    }
}
```

### 第二步：执行发布任务

```bash
./gradlew publishToMavenLocal    # 发布到本地 .m2 仓库
./gradlew publish                # 发布到配置的所有远程仓库
```

发布后，本地仓库中的目录结构如下：

```text
~/.m2/repository/com/example/my-plugin/1.0.0/
├── my-plugin-1.0.0.jar                 # 插件主 JAR
├── my-plugin-1.0.0.pom                 # 主 POM
└── my-plugin-1.0.0.module              # Gradle 模块元数据（包含 Plugin Marker）
```

Gradle 还会自动生成一个“标记文件”用于 `plugins {}` DSL 查找：

```text
~/.m2/repository/com/example/my-plugin/my-plugin.gradle.plugin/1.0.0/
└── my-plugin.gradle.plugin-1.0.0.pom   # 这个 POM 指向主 JAR
```
### 第三步：在业务项目中使用

**方式一：`plugins {}` 块（两段式）**
```kotlin
// app/build.gradle.kts
plugins {
    id("com.example.my-plugin") version "1.0.0"
}
```

**方式二：旧式 `buildscript`（三段式）**
```kotlin
// 根 build.gradle
buildscript {
    dependencies {
        classpath("com.example:my-plugin:1.0.0")
     }
}
// app/build.gradle.kts
apply(plugin = "com.example.my-plugin")
```
### AAR 和 Gradle Plugin 上传对比

|对比维度|**AAR 上传**|**Gradle 插件上传**|
|---|---|---|
|**核心产物**|`.aar` 文件（含资源、字节码）|`.jar` 文件（含插件类）+ Plugin Marker|
|**应用的核心插件**|`com.android.library` + `maven-publish`|`java-gradle-plugin` + `maven-publish`|
|**声明坐标**|手动指定 `groupId` / `artifactId` / `version`|`group` 和 `version` 手动指定，`artifactId` 默认取项目名|
|**发布变体**|`from(components["release"])`|`from(components["java"])`|
|**Plugin Marker**|不需要|**自动生成**（由 `java-gradle-plugin` 完成）|
|**使用者引用方式**|`implementation("group:name:version")`|`classpath` 或 `plugins { id(...) version(...) }`|
|**敏感信息管理**|支持 `gradle.properties` 中的 `nexusUsername`|完全相同|
|**典型场景**|发布 SDK / 公共库给 App 使用|发布构建工具 / 约定插件给团队使用|

## 什么是“插件扩展”（Extension）

插件扩展是一个**暴露给用户的配置 DSL 块**。它允许用户通过 `build.gradle` 脚本来自定义插件的行为。

**如何编写：**

1.**创建扩展类**：用于存储用户配置的数据类。

```kotlin
class MyPluginExtension {
    var message: String = "Default Message"
    var isEnabled: Boolean = true
}
```

2.**在插件中注册扩展**：在 `apply` 方法中用 `project.extensions.create` 注册。

```kotlin
class MyCustomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
     // 注册一个名为 "myConfig" 的扩展
     project.extensions.create("myConfig", MyPluginExtension::class.java)
        // ... 其他逻辑
    }
}
```

3.**在构建脚本中使用扩展**：

```kotlin
// app/build.gradle.kts
myConfig {
    message = "Hello from build script!"
    isEnabled = false
}
```

4.**在插件中读取配置**：

**`create` 只负责“注册”，`getByType` 负责“使用”**。

```kotlin
project.tasks.register("printMessage") {
    doLast {
        val ext = project.extensions.getByType(MyPluginExtension::class.java)
        if (ext.isEnabled) {
            println("Message: ${ext.message}")
        }
    }
}
```

# SO 文件归属分析

包大小占比重，so 往往是占比大头，但 so 文件属于哪个依赖引入的我们确很难直接看出来，所以需要清楚的知道 so 的来源，才好准确的针对性优化。

build.gradle 中加入下面的配置，打印每个 jar/aar 中包含的 so 文件

```groovy
Configuration configuration = project.getConfigurations().getByName(applicationVariant.getName() + "CompileClasspath");
configuration.forEach(file -> {
    String fineName = file.getName();
    System.out.println(TAG + "fine name = " + fineName);
    if (fineName.endsWith(".jar") || fineName.endsWith(".aar")) {
        try {
            JarFile jarFile = new JarFile(file);
            for (Enumeration enums = jarFile.entries(); enums.hasMoreElements(); ) {
                JarEntry jarEntry = (JarEntry) enums.nextElement();
                if (jarEntry.getName().endsWith(".so")){
                    System.out.println(TAG + "----- so name = " + jarEntry.getName());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
});

```

# Gradle 编译优化

## 为什么要做编译优化

1. 提升开发效率：开发者可以更快地编译和运行代码，这意味着对代码的变更可以更快的得到反馈，从而加速开发周期，提升个人和整个团队的生产力。
2. 提升开发体验：减少了等待时间，提升个人和整个团队的幸福感和满意度。

## 影响编译速度的因素

- 硬件性能：CPU、内存等。
- 构建配置：缓存、增量编译等。
- 项目：项目的大小和复杂度，代码量，模块化，依赖管理等。
- 其他：网络速度，下载慢或者找不到等。

## 优化方案

### 常规优化点

1. 升级 Gradle 版本
   `Gradle`作为一个构建工具，提升构建性能可以说是基础操作，基本每个大版本都会带来各种各样的性能提升，虽然升级 Gradle 有一定的适配成本，但是如果不升，长此以往，技术负债只会越来越多。
2. 升级 Java 版本
   Gradle 是运行在 JVM 上的，Java 性能的提升也会有利于 Gradle。
3. 升级 Gradle Plugin 版本
   同 Gradle，一般也都是跟随着 Gradle 升。
4. 开启并行编译
   Gradle 默认一次只执行一个 Task，即串行，那我们就可以通过配置让 Gradle 并行来执行Task，从而提升构建效率，缩短构建时间。
5. 开启守护进程
   开启守护进程之后，Gradle 不仅可以更好的缓存构建信息，而且运行在后台，不用每次构建都去初始化然后再启动 JVM了。
6. 启用配置缓存
   当没有构建配置发生变化的时候，比如构建脚本，Gradle 会直接跳过配置阶段，从而带来性能的提升。
7. 启用构建缓存
   同一个 Task 的输入不变的情况下，Gradle 直接去检索缓存中检索输出，就不用再次执行该 Task 了。
8. 增加 Gradle 内存、Android Studio 内存。

在`gradle.properties`文件中添加：

```text
   org.gradle.parallel=true //并行编译
   org.gradle.daemon=true   //守护进程
   org.gradle.unsafe.configuration-cache=true //启动配置缓存
   org.gradle.caching=true //启用构建缓存
```

### 其他优化点

1. 删除无用的依赖
2. 优化依赖的下载速度
   使用一些国内的镜像来提升下载速度。
3. 避免编译和打包不测试的资源，按需编译。
4. 使用非传递 R 类
   使用非传递 R 类可为具有多个模块的应用构建更快的 build，确保每个模块的 R 类仅包含对其自身资源的引用，而不会从其依赖项中提取引用，从而帮助防止资源重复，Gradle 插件 8.0.0 及更高版本中的默认开启。
5. 停用 Jetifier 标志。
   `Jetifier`是把 support 包转成 AndroidX 的工具，如果已经适配完毕，可以关闭。
6. 使用 KSP 代替 kapt
   `kapt`是 Kotlin 注解处理工具，kapt 的运行速度明显慢于 KSP。
7. AAR 模块化
   AAR 已经是编译后产物，减少了工程参与编译的代码。

在`gradle.properties`文件中添加：

```text
android.nonTransitiveRClass=true // 使用非传递 R 类
android.enableJetifier=false  // 停用 Jetifier 标志
```

# 依赖动态切换

module 有源码和 aar 依赖两种方式，实际开发调试过程中需要将对应模块通过源码依赖进行开发调试，此时就涉及到源码 和 aar 模式的切换。

在多仓库场景下，每个组件都是一个独立的 Git 仓库。当 `useLocal = true` 时，通过 `includeBuild` 把本地组件仓库引入，并用 `dependencySubstitution` 把 Maven 坐标替换成本地项目。

配置文件 `module-switch.json`:
```json
{
  "modules": [
    {
      "name": "biz-library",
      "localPath": "../SABiz",
      "gav": "com.didiglobal:sa-biz:1.0.0",
      "useLocal": true
    },
    {
      "name": "network-library", 
      "localPath": "../NetworkLib",
      "gav": "com.didiglobal:network:2.0.0",
      "useLocal": false
    }
  ]
}
```
- `name`：本地项目名称（即 `includeBuild` 内部的子项目名，通常是 `:library` 或 `:biz-library`）
- `localPath`：相对壳工程的路径
- `gav`：完整的 Maven 坐标（`groupId:artifactId:version`）
- `useLocal`：`true` 切源码，`false` 用 AAR

```groovy
// settings.gradle
import groovy.json.JsonSlurper

// 1. 读取配置文件
def configFile = file('module-switch.json')
if (!configFile.exists()) {
    println "module-switch.json not found, all modules use remote AAR."
    return
}

def config = new JsonSlurper().parse(configFile)
def modules = config.modules ?: []

// 2. 遍历处理
modules.each { module ->
    def useLocal = module.useLocal as boolean
    def localPath = module.localPath as String
    def gav = module.gav as String
    def name = module.name as String

    if (useLocal && localPath) {
        // 关键：用 includeBuild 引入本地仓库，并做坐标替换
        includeBuild(localPath) {
            dependencySubstitution {
                substitute(module(gav)).using(project(":$name"))
            }
        }
        println "✅ $name -> 使用本地源码 ($localPath)"
    } else {
        println "📦 $name -> 使用远程 AAR ($gav)"
    }
}
```

# 多渠道打包

多渠道打包（Multi-channel Packaging）是指为同一个应用生成多个不同的安装包，每个安装包可以包含不同的配置、资源或元数据。

## 为什么需要多渠道打包

1. 数据统计：根据渠道区分来源，统计各渠道的下载量以及覆盖率。
2. 精细化运营：根据数据分析来做营销和推广，提升应用的曝光和转化。
3. 厂商适配：适配不同厂商的系统 API 以及合规要求等。
## buildTypes

buildTypes 是`构建类型`，用来定义构建类型配置，比如是否开启代码混淆、是否开启调试模式等，通常包含 debug 和 release 两种类型。

在多渠道配置中，构建类型与产品变种(productFlavors)一起使用，可以形成不同组合的构建变体(Variants)。
##  productFlavors 

`productFlavors`中文翻译过来是`产品变种`，用来定义不同变种的包，每个风格可以有不同的配置和资源。

defaultConfig {}中的配置为应用默认配置，都可以在渠道配置 productFlavors {} 中进行覆写或追加。

flavorDimensions 表示产品变种的`维度`(Dimensions)，是「组」的概念，这里定义的是「version」，名字可以自定义，也可以有多个。每个维度可以包含一个或多个产品变种，同一个维度即为一个产品变种组，Dimensions 就是用来将某个产品变种归类到特定维度中。**每个维度中的产品变种可以相互组合，生成不同的构建变体。**

```kotlin
android {
    namespace = "com.xx.xx"
    compileSdk = 33

    defaultConfig {
        applicationId = "xx.xx.xx"
    }

    // 多渠道打包配置
    flavorDimensions += listOf("version, environment")
    productFlavors {
        create("huawei") {
            dimension = "version"
            applicationIdSuffix = ".huawei"
            versionNameSuffix = "-huawei"
            versionCode = 1
            buildConfigField("int", "CHANNEL_CODE", "1001")
        }
        create("oppo") {
            dimension = "environment"
            applicationIdSuffix = ".oppo"
            versionNameSuffix = "-oppo"
            versionCode = 1
            buildConfigField("int", "CHANNEL_CODE", "1002")
        }
    }
```

## 多渠道依赖方式

- 默认依赖：implementation、api
- 变体依赖：变体+Implementation，如 huaweiImplementation
- 构建类型：类型+Implementation，如 debugImplementation
- 构建变体：变体+类型+Implementation，如 huaweiDebugImplementation

## 渠道资源

如果存在一个 huawei 渠道的变体，想配置该渠道特殊资源，则执行如下操作，这样，在构建 huawei 渠道变体的时候，Gradle 会根据构建变体来找对应的目录文件。

在app/src/目录下新建 huawei 文件夹，在 huawei 文件夹下再新增 res 文件夹。

- 在 res 文件夹下新增 mipmap 文件夹(mipmap-xxhdpi)，并放置华为版的应用图标 ic_launcher。
- 在 res 文件夹下新增 values 文件夹，values 文件夹下新建 strings.xml 文件，并配置华为版的应用名称。

```text
app/
└── src/
    ├── huawei/
    │   ├── res/
    │   │   ├── mipmap-xxhdpi/
    │   │   │   └── ic_launcher.png
    │   │   └── values/
    │   │       └── strings.xml
    └── main/
        └── res/
            ├── mipmap-xxhdpi/
            │   └── ic_launcher.png
            └── values/
                └── strings.xml

```

**合并规则**

- 渠道构建时，渠道变体(huawei)会跟主变体(main)目录下的资源进行合并。
- 如有同名配置资源，例如 strings.xml 文件中的 app_name，则优先取渠道(huawei)配置资源进行覆盖，其他不同名的则进行合并。
- layout 文件、assets 文件则是替换，渠道资源(huawei)优先于主变体(main)资源。

## 渠道代码

代码文件是不支持合并的，也不支持同名。main 作为主变体，渠道可以引用 main 中的代码类，main 也可以引用渠道中的代码类，但是当渠道变换时，main 则会出现找不到之前渠道代码类的异常，因为渠道中的代码为该渠道专属，只有在该渠道编译时才会与主变体 main 中的代码进行融合。**如果想要多渠道代码融合，则需要使用 SourceSets。**

## SourceSets 

`sourceSets` 的本质是**多层级的目录合并**，遵循 **“Build Type > Flavor > Main”** 的优先级进行合并。

- **Main (`src/main`)**：基础配置。
- **Flavor (`src/oppo`)**：覆盖或增量补充 Main。
- **Build Type (`src/debug`)**：最高优先级，覆盖前两者。

Gradle 默认会自动识别 `src/[flavorName]/java` 等目录，如果你的代码就在 `src/oppo/java`，不需要在 `sourceSets` 里写它，Gradle 会自动包含。

- **`srcDirs("path")`**：**替换**当前 SourceSet 已有的路径列表。但由于 `oppo` 和 `main` 是两个独立的 SourceSet，`oppo` 的替换不会影响 `main`。
- **`srcDir("path")`**：向当前 SourceSet **增量添加**一个路径。

**清单文件是“合并”而非“叠加”**：每个 SourceSet 只能有一个 `manifest.srcFile`。

示例
```groovy
android {
    sourceSets {
        // 以特定的 flavor（如 oppo）或 main 为例
        oppo {
            // 1. Java/Kotlin 代码目录 (复用其他渠道代码)
            java.srcDirs = ['src/oppo/java', 'src/huawei/java']
            kotlin.srcDirs = ['src/oppo/kotlin', 'src/huawei/kotlin']
            // 2. 资源文件目录 (多目录合并，常用于资源拆分)
            res.srcDirs = ['src/oppo/res', 'src/common/res']
            // 3. 资产目录 (如预置数据库、字体、离线包)
            assets.srcDirs = ['src/oppo/assets']
            // 4. 预编译库目录 (存放 .so 文件)
            jniLibs.srcDirs = ['libs'] 
            // 5. 清单文件 (注意：这是 srcFile，不是 srcDirs，只能指定一个)
            manifest.srcFile 'src/oppo/AndroidManifest.xml'
            // 6. AIDL 接口定义目录
            aidl.srcDirs = ['src/oppo/aidl']
        }
    }
}
```

## 渠道统计

### meta-data

meta-data 标签通常在 AndroidManifest.xml 文件中使用，通过键值对的方式为组件提供附加配置信息。

```xml
<application ...>
    <meta-data android:name="UMENG_CHANNEL" android:value="${UMENG_CHANNEL_NAME}"/>
    ...
</application>
```

通过 `manifestPlaceholders` 来替换 AndroidManifest.xml 文件中 `value` 的值，UMENG_CHANNEL_NAME 要对应上。在代码中则通过 PackageManager 获取并读取 meta-data 信息。
```groovy
    productFlavors {
        create("huawei") {
            manifestPlaceholders["UMENG_CHANNEL_NAME"] = "huawei"
        }
        create("oppo") {
            manifestPlaceholders["UMENG_CHANNEL_NAME"] = "oppo"
        }
    }
```

### BuildConfig

BuildConfig 通常用来存储一些常量信息，比如版本号，或者在 buildTypes 中根据构建环境来定义接口请求的域名地址等，BuildConfig 会在编译时生成 class 文件。

BuildConfig文件的位置在`<project-root>/app/build/generated/source/buildConfig/<variant-name>/<package-name>/BuildConfig.java`。

在 Gradle8.+ 版本中，需要开启 BuildConfig 功能：
```groovy
android {
	buildFeatures {
		buildConfig = true
	}
}
```

buildConfigField 参数格式：

fun buildConfigField(type: String, name: String, value: String);

type 为数据格式，name 为 key，value 为值，在 build.gradle 中使用。

配置完之后，重新 Sync 就会生成对应的常量了。

## ABI 分包

`ABI`全称为 Application Binary Interface，即应用二进制接口，是应用程序和操作系统在二进制级别交互的一种接口标准。不同的 Android 设备使用不同的 CPU，而不同的 CPU 支持不同的指令集。

在 Android 生态系统中，x86 架构(Intel)的非常少见，常用于模拟器或是早期的“古董”设备，主流架构是`ARM`。而在 ARM 架构中，又以 64 位架构(arm64-v8a)为主流。

![[ABI CPU.png]]

分包是指由传统的构建一个 APK 文件变为根据架构来构建出多个 APK 文件，通常是 arm64-v8a 的64 位包、armeabi-v7a 的 32 位包、以及 arm64-v8a 加 armeabi-v7a 的**合包**(整包) 。

分包有如下优点：

- 减少 APK 大小：每个分包的 APK 只包含对应 ABI 的共享库(.so文件)，减少了APK的大小。
- 提高安装速度：用户设备只会下载和安装与其架构匹配的 APK，可以提高下载和安装的速度，并节省下载资源。
- 提升性能：64 位架构相比于 32 位架构，在运算能力和执行速度上有显著提升。

splits 是分裂/分开的意思，支持**屏幕密度(Density)** 和 **应用二进制接口(ABI)** 两种方式构建多个 APK。

```groovy
android {
    // ABI 分包
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
}
```

`abi{}`闭包中的属性配置介绍：

- isEnable，是否开启 ABI 分包，true表 示开启，默认关。
- reset()，清除默认的 ABI 列表，要与 include 属性结合使用，毕竟不能没有 ABI 配置，清除了要再添加。
- include()，指定生成哪些 APK 的 ABI 列表，多个由逗号分割。
- isUniversalApk，除了按照 include 配置的 ABI 列表生成多个 APK 之外，是否生成一个包含所有 ABI 列表的合包，true 表示开启，默认关。

在使用`splits`来配置 `abi { }`时，需要注释或移除 defaultConfig{ } 中的 ndk { } 配置，否则会有冲突。

# 依赖版本管理

版本管理(Version Management)是指在开发过程中对项目依赖的各个库、框架、插件等版本进行管理和控制，确保项目中的所有组件以正确的版本组合在一起运行，以避免兼容性问题、漏洞和其他潜在问题。对于一个复杂的项目来说，良好的版本管理是至关重要的，它能显著提高项目的可维护性和稳定性。

## 直接指定版本号

在`dependencies{ }`中声明并直接写具体的版本号。这种方式简单直观，但随着项目的依赖变多时，版本管理分散，升级版本也会比较繁琐，维护成本较高。

## 变量占位符

把版本号抽出来统一管理，即`val room_version = "2.6.1"`，使用 $ 引用。

```kotlin
dependencies {
    val room_version = "2.6.1"

    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // To use Kotlin annotation processing tool (kapt)
    kapt("androidx.room:room-compiler:$room_version")
    // To use Kotlin Symbol Processing (KSP)
    ksp("androidx.room:room-compiler:$room_version")
}
```

## ext

`ext`全称Extra Properties Extension，是额外扩展属性的意思，以键值对的形式进行存储。

## version catalogs

`Version Catalogs`全称是version catalog libs，中文是「版本目录」的意思，是`Gradle 7.0`引入的一项新特性，允许你在一个集中式文件中管理所有依赖版本和插件版本。

version catalogs 支持两种使用方式，一种是在 settings.gradle(.kts) 文件中声明，另一种是在单独的 libs.versions.toml 文件中声明，这种更解耦一些，推荐使用。

在根项目的 gradle 文件夹中，创建一个名为 `libs.versions.toml`的文件。Gradle 默认会在 libs.versions.toml 文件中查找目录，因此建议使用此默认名称，这个叫`约定大于协议`。

在 libs.versions.toml 文件中，添加以下配置：

```toml
[versions]
agp = "7.5.0"
coreKtx = "1.10.1"
junit = "4.13.2"
# ... 等等
[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

**`[versions]` 定义版本号常量，供其他部分引用。**

 >这里定义了**版本号的别名**(如 `agp`、`coreKtx`)，右侧是具体的版本字符串。
   这些别名可以被 `[libraries]` 和 `[plugins]` 中的 `version.ref` 引用。
   版本号可以是任何字符串，但必须是 Maven 仓库中存在的有效版本。
   
**常见陷阱**：版本号前后不能有空格，否则 Gradle 会解析失败(例如 `agp = " 7.5.0"` 是错的)。
 
**`[libraries]` 定义依赖库的坐标(group + name + version)，供 `dependencies {}` 块使用。**

- `group`：Maven 的 groupId（如 `androidx.core`）
- `name`：Maven 的 artifactId（如 `core-ktx`）
- `version.ref`：引用 `[versions]` 中定义的版本别名（如 `coreKtx`）

**使用方式：**
在 `build.gradle` 的 `dependencies {}` 中写 `implementation libs.androidx.core.ktx`。

别名中的短横线 `-` 会转换为点号 `.` 访问，所以 `androidx-core-ktx` 变成 `libs.androidx.core.ktx`。

**`[plugins]` 定义插件的坐标（id + version），供 `plugins {}` 块使用。**

- `id`：插件的唯一 ID（如 `com.android.application`）
- `version.ref`：引用 `[versions]` 中定义的版本别名（如 `agp`）

**使用方式**：
在 `build.gradle` 的 `plugins {}` 块中写 `alias(libs.plugins.android.application)`)

# 为什么 Gradle 7.0+ 需要 Java 11，而且国内大部分项目不是 Java8 就是 Java 11，为什么其它版本不常用

## 1. 为什么 Gradle 7.0+ (尤其是 AGP 7.0+) 强制要求 Java 11？

这并非 Gradle 单方面的“任性”，而是 **Android Gradle Plugin (AGP) 7.0** 的一次重大架构升级导致的，主要原因有两点：

*   **构建工具自身的进化**：
    Gradle 和 AGP 本身也是用 Java 编写的程序。为了提升构建速度、利用更高效的内存管理（如 ZGC）和新的语言特性（如 `var`、模块化系统），AGP 开发团队决定在 7.0 版本将**运行环境**的最低要求提升到 Java 11。这就像你的 APP 升级了最低 Android 版本以使用新 API 一样，构建工具也需要升级底座。
*   **统一开发环境**：
    在 AGP 7.0 之前，开发者经常混淆“编译代码用的 JDK”和“运行 Gradle 用的 JDK”。从 Android Studio Arctic Fox (2020.3.1) 开始，Google 直接在 IDE 中捆绑了 JDK 11，并将 AGP 7.0 默认配置为使用该 JDK 运行，以减少环境配置错误。

**关键区分：** 这里指的是**运行 Gradle 构建工具**需要 Java 11，而不是说你的 APP 代码必须写成 Java 11。你仍然可以在 Java 11 的环境下编译 Java 8 的代码（通过 `sourceCompatibility = JavaVersion.VERSION_1_8`）。
```gradle
android { 
 compileOptions { 
   // 告诉编译器：虽然你很强，但请把代码处理成 Java 8 的样子 
   sourceCompatibility JavaVersion.VERSION_1_8 
   targetCompatibility JavaVersion.VERSION_1_8 
  } 
 kotlinOptions { 
   jvmTarget = '1.8' 
  } 
}
```

## 2. 为什么国内项目大多是 Java 8 或 Java 11，其他版本很少？

这主要由 **LTS(长期支持版)策略** 和 **Android 系统限制** 共同决定：

1.  **LTS 的统治力**：
    企业级开发极其看重稳定性。
    *   **Java 8 (2014)**：是目前存量最大的版本，生态最完善，许多老旧的银行、国企项目不敢轻易迁移。
    *   **Java 11 (2018)**：是 Java 8 之后的第一个 LTS 版本，是目前新项目的首选标准。
    *   **Java 17(2021) & 21 (2023)**：虽然也是 LTS，但国内迁移速度较慢，通常只有大厂的基建部门或追求极致性能的新项目（如使用 Spring Boot 3）才会跟进。
2.  **Android 的特殊性（Desugaring）**：
    Android 系统并不直接运行 Java 字节码，而是运行 Dex 字节码。Android 设备上的虚拟机（ART/Dalvik）对新版 Java 语法的支持是滞后的。
    
    虽然 AGP 提供了“脱糖”（Desugaring）功能，让旧手机也能运行新 Java 语法，但这种支持是有限的。Java 8 的特性支持最完美，Java 11 次之，更高版本的特性在 Android 上往往难以完全发挥，甚至导致兼容性崩溃。

## 3. 为什么用了 Java 21 等新版本就会报错？

这是因为**Gradle 版本过低，无法“认识”新版 JDK 产生的字节码格式**。

核心原因：Class File Version 冲突
Java 的每个版本都有对应的类文件版本号（Class File Major Version）。
*   Java 8 = 52
*   Java 11 = 55
*   Java 17 = 61
*   **Java 21 = 65**

当你用 JDK 21 运行一个老版本的 Gradle（比如 Gradle 7.4）时，Gradle 的守护进程（Daemon）会尝试读取 JDK 21 的信息。但 Gradle 7.4 发布时，Java 21 还没出生，Gradle 内部的代码检查到版本号 `65` 时，发现自己不认识，就会直接抛出 `Unsupported class file major version 65` 错误。

Gradle 与 Java 版本的兼容性矩阵

要使用新版 Java，必须升级 Gradle。以下是关键节点的对照表：

| Java 版本     | 要求的最低 Gradle 版本 | 备注                  |
| :---------- | :-------------- | :------------------ |
| **Java 8**  | Gradle 2.0+     | 经典组合                |
| **Java 11** | Gradle 5.0+     | AGP 7.0+ 的强制要求      |
| **Java 17** | **Gradle 7.3+** | Spring Boot 3 的最低要求 |
| **Java 21** | **Gradle 8.5+** | 2026年很多新框架的标准       |

**结论：**
如果你想在项目中使用 **Java 21**，你必须将 `gradle-wrapper.properties` 中的 Gradle 版本升级到 **8.5** 或更高。如果你还在用 Gradle 7.x 或 8.0，它根本无法理解 Java 21 的环境，自然会报错。

## 总结建议

1.  **对于老项目**：保持 **AGP 7.x + Gradle 7.5 + JDK 11** 是最稳妥的“养老”配置，兼容性最好。
2.  **对于新项目**：推荐直接上 **AGP 8.x + Gradle 8.7+ + JDK 17/21**，以享受构建速度提升。

# Android Studio 版本、AGP、Gradle、JDK 这四个版本的关联关系如何记忆

**Android Studio** (限制) ➔ **AGP** (依赖) ➔ **Gradle** (依赖) ➔ **JDK**

1. **AS 决定 AGP**：
    - 你装了**最新版 Android Studio**（包工头），它就强制要求你用**较新的 AGP**（图纸）。
    - 比如：AS 2025 强行要求 AGP 4.0+。
2. **AGP 决定 Gradle**：
    - 你升级了 **AGP**（图纸），图纸上写着“需要高级工艺”，所以必须升级 **Gradle**（工人）。
    - 比如：AGP 4.0 强行要求 Gradle 6.1+。
3. **Gradle 决定 JDK**：
    - 你升级了 **Gradle**（工人），新工人的身体构造变了，必须运行在 **新版 JDK**（环境）上。
    - 比如：Gradle 8.0+ 强行要求 JDK 17+ 才能跑起来。
