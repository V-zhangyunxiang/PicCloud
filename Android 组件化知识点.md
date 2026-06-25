
# 如何解决 Module 间互相调用的问题

问题本质是: **A module 如何拿到 B module 中接口实现的单例对象**，同时希望这个过程尽量对 A 透明、不引入 EventBus，也不破坏模块间的依赖关系。

核心思路是：**在 common 层提供一个“能力注册/发现中心”，B module 负责把实现注册进去，A module 只从中心获取**。

## 静态工厂 + 手动注册（最简单）

**原理**：在 common 中定义一个全局可访问的工厂类，A 通过它获取实例；B 在自身初始化时往工厂里塞入实现。

```kotlin
// ===== common 模块 =====
interface IBiz {
    fun getParams(): String
}

object BizFactory {
    private var biz: IBiz? = null

    fun register(impl: IBiz) {
        biz = impl
    }

    fun get(): IBiz {
        return biz ?: throw IllegalStateException("IBiz 未注册")
    }
}

// ===== B 模块 =====
class IBizImpl : IBiz {
    override fun getParams() = "来自 B 的参数"
    companion object {
        // 供外部初始化的入口
        fun init() {
            BizFactory.register(IBizImpl())
        }
    }
}

// ===== A 模块 ===== (依赖了 B)
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A 仍然需要知道 B 的 init 方法，但实现与接口已分离
        IBizImpl.init()
    }
}

// 在 A 任意处使用
val params = BizFactory.get().getParams()
```

**优点**：简单直白，无黑科技。  
**缺点**：A 仍需主动调用 B 的初始化逻辑（耦合了 B 的 `init`），不够自动化。
## ContentProvider 自动注册（最推荐）

利用 ContentProvider 的 `onCreate` 会在 `Application.onCreate` 之前自动执行的特性，让 B 模块**无侵入地**把实现注册到 common 的工厂中。

```kotlin
// ===== common 依然有 BizFactory =====
object BizFactory {
    private val bizList = mutableListOf<IBiz>() // 支持多实现

    fun register(impl: IBiz) {
        bizList.add(impl)
    }

    fun <T : IBiz> get(clazz: Class<T>): T {
        return bizList.filterIsInstance(clazz).firstOrNull()
            ?: throw IllegalStateException("${clazz.simpleName} 未注册")
    }
}

// ===== B 模块 =====
class IBizImpl : IBiz {
    override fun getParams() = "来自 B 的参数"
}

// 在 B 模块的 AndroidManifest.xml 中声明 ContentProvider
class BizInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        BizFactory.register(IBizImpl())
        return true
    }
    // 以下方法可空实现
    override fun query(...): Cursor? = null
    override fun getType(...): String? = null
    override fun insert(...): Uri? = null
    override fun delete(...): Int = 0
    override fun update(...): Int = 0
}

//<!-- B 模块的 AndroidManifest.xml -->
<provider
    android:name=".BizInitProvider"
    android:authorities="${applicationId}.bizinit"
    android:exported="false" />

//A 模块无需任何显式调用，直接用：
val biz = BizFactory.get(IBiz::class.java)
val params = biz.getParams() 
```

**优点**：
- 完全解耦：A 不直接引用 B 的任何类（包括 `init` 方法）。
- 自动初始化：即使 App 进程被杀后恢复，ContentProvider 也会重新触发注册。
**缺点**：ContentProvider 会增加一点启动耗时，单一模块这样做是完全可以接受的。

##  ServiceLoader（纯 Java 标准方案）

如果项目偏向纯 Java/Kotlin 且不想引入 Android 特定组件，可以用 `ServiceLoader`。

```kotlin
// ===== common 模块 =====
interface IBiz {
    fun getParams(): String
}

object BizLoader {
    val biz: IBiz by lazy {
        ServiceLoader.load(IBiz::class.java).firstOrNull()
            ?: throw IllegalStateException("未找到 IBiz 实现")
    }
}

// ===== B 模块 =====
class IBizImpl : IBiz {
    override fun getParams() = "来自 B 的参数"
}

//在 B 模块的 src/main/resources/META-INF/services/ 下创建文件，文件名是 common 包名.IBiz，内容为：

B 模块包名.IBizImpl

//A 模块使用：
val params = BizLoader.biz.getParams()
```
**优点**：标准 Java SPI，不依赖 Android 框架。  
**缺点**：需要手动维护配置文件；多模块合并时需处理冲突；初始化时机不可控（首次使用时加载）。

## 依赖注入(DI)

**依赖注入（DI）就是解决这类“接口与实现分离，并自动注入实例”场景的标准方案**。DI 框架（如 Hilt/Dagger）可以帮你**自动完成“注册”和“获取”这两个动作**，避免手动写工厂或利用 ContentProvider。

核心流程：
1. **在 `common` 模块中**定义接口 `IBiz`。
2. **B 模块**依赖 `common`，提供 `IBiz` 的实现类 `IBizImpl`，并通过 DI 注解声明“这个实现需要被注入”。
3. **A 模块**依赖 `common`（也可能同时依赖 B，取决于你的 DI 方案），在需要的地方通过 `@Inject` 声明 `IBiz` 字段，DI 框架会自动将 `IBizImpl` 的单例注入进来。
这样，**A 模块无需知道 `IBizImpl` 的存在**，只需依赖 `IBiz` 接口，完全符合依赖倒置原则。

具体实现（以 Hilt 为例）

**1.在 `common` 模块定义接口**
```kotlin
interface IBiz {
    fun getParams(): String
}
```

**2.B 模块提供实现并暴露注入规则**

B 模块需要添加 Hilt 依赖，并通过 `@Module` + `@Provides`（或 `@Binds`）声明如何提供 `IBiz` 实例。

使用 @Binds（更简洁，适用于实现类可被 DI 管理的情况）：
```kotlin
// B 模块
class IBizImpl @Inject constructor() : IBiz {
    override fun getParams() = "来自 B 的参数"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BizModule {
    @Binds
    @Singleton
    abstract fun bindBiz(impl: IBizImpl): IBiz
}
```
这里 `IBizImpl` 用 `@Inject constructor()` 告诉 Hilt 它可以被自动构造（构造函数无额外依赖），而 `BizModule` 将 `IBizImpl` 绑定到 `IBiz` 接口上。

**3.A 模块中注入使用**

A 模块只需依赖 `common`（并集成 Hilt），直接 `@Inject` 字段即可：

```kotlin
@AndroidEntryPoint
class SomeActivity : AppCompatActivity() {
    @Inject lateinit var biz: IBiz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val params = biz.getParams()
    }
}
```
**关键点**：A 模块**完全不需要依赖 B 模块**，也不需要知道 `IBizImpl`。但有一个前提：**整个编译时，所有使用了 `@Module` 的模块都需要被 DI 容器感知到**。在 Hilt 中，这通常通过 **多模块 + `@InstallIn`** 自动发现机制实现，无需手工注册。

## Hilt 使用手册

 1.`@Module`

- 表明这是一个 **Hilt 模块**，用来提供依赖对象的构建方式或绑定关系。
- Hilt 会在编译时扫描所有 `@Module` 注解的类，并从中收集“如何创建对象”的规则。

2.`@InstallIn(SingletonComponent::class)`

- 指定这个模块**安装到哪个容器/作用域**中。
- `SingletonComponent` 是 Hilt 中最顶层的组件，它的生命周期跟随整个 **Application**。
- 这意味着该模块提供的所有绑定，在 App 存活期间只会存在一份，并且可以在任何需要单例的地方被注入。

3.`abstract class BizModule`

- 因为用了 `@Binds`，类必须是**抽象类**，Hilt 会生成实现代码，不需要你写具体方法体。

4.`@Binds`

- 这是核心：**接口绑定抽象方法**，专门用于将接口的实现类绑定到接口上。
- 它要求方法**只有一个参数**（这个参数是接口的实现类），返回值是接口类型。
- 参数类型 `IBizImpl` 必须能被 Hilt 自动创建（例如它用 `@Inject constructor()` 声明了构造器），否则 Hilt 不知道如何实例化它。

5.`@Singleton`

- 作用域注解，表示这个绑定提供的实例是**单例**。
- 配合 `@InstallIn(SingletonComponent::class)`，确保整个 App 只有唯一的一个 `IBizImpl` 对象，并且由 Hilt 管理其生命周期。

6.`abstract fun bindBiz(impl: IBizImpl): IBiz`

- 方法名可以随便取，Hilt 不看名字，只看参数和返回值类型。
- 它定义了这样一个规则：**当注入 `IBiz` 类型时，用 `IBizImpl` 的实例来满足**。
- 因为 `IBizImpl` 是具体的实现，所以 Hilt 能够创建它（前提是它有可注入的构造函数），然后把这个实例作为 `IBiz` 返回。

为什么用 `@Binds` 而不用 `@Provides`？

- `@Binds` 专门用于**接口和实现的绑定**，代码更简洁，必须是抽象方法。
- `@Provides` 用于创建复杂对象（例如需要手动构造、调用建造者模式等），写在具体方法中，返回值直接是对象。

**Hilt 的核心依赖（`hilt-android`）必须通过 `common` 模块用 `api` 声明，而不能只放在 `app` 模块里。**  

原因很简单：业务模块（B、C、D 等）需要使用 `@Inject`、`@Module`、`@InstallIn` 等 Hilt 注解和 API，只有把这些库暴露给它们，代码才能编译通过。

**Hilt 分为“运行库”和“注解处理器”两部分**，两者的放置策略不同。

1.依赖传递：`hilt-android` 要放在 `common` 且使用 `api`

```kotlin
// common/build.gradle.kts
dependencies {
    // 用 api 声明，让所有依赖 common 的模块都能使用 Hilt 的注解和类
    api("com.google.dagger:hilt-android:2.x")
}
```

- 如果使用 `implementation`，Hilt 的类只对 `common` 模块内部可见，其他依赖 `common` 的模块看不到，会产生编译错误。
- 一旦用 `api` 导出，任何依赖 `common` 的业务模块都可以直接使用 `@Inject` 等注解，无需重复声明 `hilt-android` 依赖。

2.注解处理器：每个用 Hilt 的模块都要自己加

`hilt-compiler`（或 `hilt-android-compiler`）是编译时注解处理器，它不参与运行时，也不能通过 `api` 传递。**哪个模块写了 Hilt 注解（`@Module`、`@Inject constructor`、`@AndroidEntryPoint` 等），哪个模块就必须自己声明注解处理器依赖。**

```kotlin
// 每个业务模块（如 B、C、D）的 build.gradle.kts
plugins {
    id("kotlin-kapt") // 或使用 KSP
}

dependencies {
    // 业务模块只需引入 common，hilt-android 已经被 api 带过来了，不用重复写
    implementation(project(":common"))

    // 注解处理器必须每个模块单独加
    kapt("com.google.dagger:hilt-android-compiler:2.x")
    // 如果用 KSP: ksp("com.google.dagger:hilt-android-compiler:2.x")
}
```

- **app 模块**同样需要加注解处理器，因为它有 `@HiltAndroidApp` 和可能的 `@AndroidEntryPoint`。
- **common 模块本身**如果定义了 Hilt 相关的抽象（例如接口但无实现绑定），可能不需要处理器；但如果内部也写了 `@Module` 或 `@Inject`，同样需要加。

# 多 Module 场景下，如何使用 DI 提供 API 方法

当**多个模块都需要向 A 模块提供各自的功能方法**时，原则上每个提供能力的模块都需要：

1. **在 `common` 中声明自己专属的接口**（而不是共用一个 `IBiz`）
2. **提供该接口的实现类**
3. **通过 Hilt 的 `@Module` + `@Binds` 绑定该实现**

这样才能保证类型安全、职责清晰，并且让 A 模块可以按需注入不同的能力，而不会产生歧义。

推荐方案：按能力定义接口

假设场景：

- B 模块提供**用户信息**相关能力
- C 模块提供**支付**相关能力
- D 模块提供**日志上报**能力

**第一步：在 `common` 模块中，为每个能力定义接口**
```kotlin
// common
interface IUserService {
    fun getUserName(): String
}

interface IPayService {
    fun startPay(amount: Long)
}

interface ILogService {
    fun log(event: String)
}
```

**第二步：各模块实现自己的接口，并添加 Hilt 绑定**

```kotlin
// B 模块
class UserServiceImpl @Inject constructor() : IUserService {
    override fun getUserName() = "User from B"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BModule {
    @Binds
    @Singleton
    abstract fun bindUserService(impl: UserServiceImpl): IUserService
}

// C 模块
class PayServiceImpl @Inject constructor() : IPayService {
    override fun startPay(amount: Long) { /* ... */ }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CModule {
    @Binds
    @Singleton
    abstract fun bindPayService(impl: PayServiceImpl): IPayService
}

//D 模块类似，绑定 `ILogService`。
```

**第三步：A 模块按需注入**
```kotlin
@AndroidEntryPoint
class SomeActivity : AppCompatActivity() {
    @Inject lateinit var userService: IUserService
    @Inject lateinit var payService: IPayService
    @Inject lateinit var logService: ILogService

    // 使用时直接调用各自方法，完全解耦
}
```

- **每个模块都需要创建自己专属的接口（在 common 中定义）和对应的 `@Module` + `@Binds` 绑定。**
- 最好不要让不同模块共享同一个接口，而是“一个模块一个接口”，这样注入点明确、无歧义，符合接口隔离原则。

# 组件化如何实现源码和 AAR 动态切换

## includeBuild + dependencySubstitution(复合构建)

在 `settings.gradle` 中，通过条件判断引入一个复合构建，并用 `substitute` 将原有的 Maven 坐标替换为本地项目路径。

```kotlin
// settings.gradle.kts
if (hasLocalProperty("sourceDepSwitch.SABiz")) {
    includeBuild("../SABiz") {
        dependencySubstitution {
            substitute(module("com.didiglobal:sa-biz")).using(project(":biz-library"))
        }
    }
}
```
- `hasLocalProperty()` 通常检查 `local.properties` 或自定义配置文件中的开关。
- 开启时，Gradle 会用 `:biz-library` 项目替换所有 `com.didiglobal:sa-biz` 的依赖，该项目的源码也会被纳入构建中。
- 关闭时，则从 Maven 仓库下载 AAR。

优点

- **依赖声明不变**：壳工程和各业务模块的 `build.gradle` 中依然写 `implementation("com.didiglobal:sa-biz:1.0.0")`，无需改动。
- **切换干净**：只需改一个配置文件（如 `local.properties` 中加一行），不污染 Git 提交。
- **完全可调试**：本地源码可修改，支持断点、跳转，增量编译快。

缺点

- 需要各组件仓库与壳工程在同一开发目录下，且路径相对固定。
- 所有替换的模块都是复合构建的一部分，可能影响构建配置时间（可接受范围）。

## 手动切换：project vs AAR 的动态依赖

是最原始但也最灵活的方式，直接在使用方模块的 `build.gradle` 中根据变量切换依赖声明。

```kotlin
// 壳工程 build.gradle.kts
dependencies {
    if (project.hasProperty("useLocalBiz")) {
        implementation(project(":biz-library")) // 需要把模块源码直接放在壳工程中，或通过 settings 引入
    } else {
        implementation("com.didiglobal:sa-biz:1.0.0")
    }
}
```

优点

- 逻辑直观，不需要复合构建的知识。
- 适合模块数量很少的项目。

缺点

- **侵入性强**：每个依赖处都要写 `if-else`，模块多了以后代码会变得很乱。
- 需要同时管理 `settings.gradle` 的 `include`，容易出错。
- **依赖坐标不统一**：源码依赖时用的是 `project`，AAR 时用的是 Maven 坐标，如果切换时疏忽，容易提交错误的代码。

## 分辨率策略替换：resolutionStrategy + dependencySubstitution

在 `settings.gradle` 或项目根 `build.gradle` 中，使用 `resolutionStrategy` 全局替换依赖，这样可以集中管理。

```kotlin
// settings.gradle.kts
if (hasLocalProperty("useLocalBiz")) {
    include(":biz-library")   // 需要确保 settings 里有这个子项目
    gradle.allprojects {
        configurations.all {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.didiglobal:sa-biz")).using(project(":biz-library"))
            }
        }
    }
}
```

这种方式其实和复合构建类似，但不需要 `includeBuild`，模块直接作为壳工程的子项目存在。**注意**：因为这样会把所有业务模块都变成壳工程的子项目，会导致耦合加重，适用于模块本就在同一个代码仓库（单仓多模块）的情况。

# 多模块 gradle 配置复用策略

**把公共配置抽取到一个 Gradle 脚本文件里，让各个业务模块通过 `apply from` 引用，是 Android 多模块项目中非常经典且广泛使用的做法**，它的确能有效减少重复代码、统一配置管理（包括 `hilt-compiler`）。

- **传统做法**：`apply from: "../scripts/common_module.gradle"`。
- **更现代、主流推荐的做法**：**约定插件（Convention Plugins）**，通常放在 `buildSrc` 或独立的复合构建里。

## 传统 `apply from` 方式

假设你有一个 `gradle/scripts` 目录，放一个 `common_module.gradle` 文件：

```groovy
// gradle/scripts/common_module.gradle

// 1. 应用常用的插件（根据模块需要）
apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-kapt'          // 必须，因为要使用 hilt-compiler

// 2. 统一 Android 配置
android {
    compileSdkVersion 34
    defaultConfig {
        minSdkVersion 24
        targetSdkVersion 34
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 3. 统一依赖（包括 hilt-compiler）
dependencies {
    // common 模块已经用 api 导出了 hilt-android，这里不再需要重复添加
    // 但每个业务模块必须加 hilt-compiler（注解处理器）
    kapt "com.google.dagger:hilt-android-compiler:2.50"

    // 其他所有模块都需要的公共依赖也可以放这里，例如：
    implementation project(":common") // 如果所有业务模块都依赖 common
    testImplementation "junit:junit:4.13.2"
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
}
```

在某个业务模块（如 `module_b`）的 `build.gradle` 中：

```groovy
plugins {
    id 'com.android.library'
    id 'kotlin-android'
    // 注意：kotlin-kapt 已经在 common_module.gradle 中 apply 了
    // 但这里需要声明应用脚本，它会带入插件
}

apply from: "$rootDir/gradle/scripts/common_module.gradle"

android {
    // 如果有特殊配置，可以覆盖，比如 namespace
    namespace "com.example.moduleb"
}

dependencies {
    // 业务特有的依赖
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

## 更现代的主流实践：约定插件（Convention Plugins）

Gradle 官方和 Android 团队现在强烈推荐**使用预编译的约定插件**替代 `apply from`，因为它有以下优势：

- **类型安全**：可以在 Kotlin 写插件，享受 IDE 补全和编译检查。
- **复用性更强**：插件可以通过 Gradle 依赖管理机制共享，甚至发布到远程仓库。
- **易于维护**：逻辑内聚在插件的 `project.configure` 中，清晰明确。

```text
buildSrc/
  src/main/kotlin/
    convention.android-library.gradle.kts
build.gradle.kts (项目根)
```

**convention.android-library.gradle.kts**：

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 每个模块必须的注解处理器
    add("kapt", "com.google.dagger:hilt-android-compiler:2.50")
    // 公共运行时依赖也可以在这里通过 api/implementation 添加
    add("implementation", project(":common"))
}
```

**业务模块使用**（`module_b/build.gradle.kts`）：

```kotlin
plugins {
    id("convention.android-library")  // 这就是自定义的插件
}

android {
    namespace = "com.example.moduleb"
}

dependencies {
    // 模块特有依赖
    implementation("androidx.appcompat:appcompat:1.6.1")
}
```

这样，所有公共配置（包括 `hilt-compiler`）都封装在插件里，业务模块极简洁。

## 约定插件的实现为什么与传统二进制插件实现不同

**并非遗漏了 Plugin 类和 properties 文件，而是使用了 Gradle 的“预编译脚本插件”（Precompiled Script Plugins）特性**，它省去了手动编写 `Plugin` 实现类和注册文件。

### 1.传统二进制插件（需要 Plugin 类 + properties 文件）

这是你描述的常规路径，需要手动：

- 实现 `org.gradle.api.Plugin` 接口
- 在 `META-INF/gradle-plugins/xxx.properties` 中声明 `implementation-class`

**文件结构示例**：

```text
buildSrc/src/main/kotlin/
   com/example/MyPlugin.kt
   resources/META-INF/gradle-plugins/my-plugin.properties
```

**MyPlugin.kt**：

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 应用 android library 插件
        project.plugins.apply("com.android.library")
        // 配置 project
    }
}
```

**my-plugin.properties**：

```text
implementation-class=com.example.MyPlugin
```

使用：`plugins { id("my-plugin") }`

### 预编译脚本插件(只需一个 `.gradle.kts` 文件)

Gradle 支持直接在 `buildSrc/src/main/kotlin/` 下放置一个 `.gradle.kts` 文件，**文件名即插件 ID**，无需写 `Plugin` 类和注册文件。

Gradle 会把 `buildSrc/src/main/kotlin/` 下的所有 `.gradle.kts` 文件识别为**预编译脚本插件**，编译时自动生成对应的 `Plugin` 实现类和 `META-INF` 注册文件。整个过程对开发者完全透明。

### 什么情况下还需要写传统的 Plugin 类？

预编译脚本插件也有局限：比如需要接收外部参数、复杂的动态逻辑、多插件组合封装等。此时就需要实现 `Plugin` 接口。举例如：

```kotlin
abstract class MyConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("myConfig", MyExtension::class.java)
        project.afterEvaluate {
            val config = project.extensions.getByType(MyExtension::class.java)
            // 动态逻辑
        }
    }
}
```

# apply from 和 module 的 Gradle 配置优先级

在 Gradle 的多模块构建里，**配置的优先级直接取决于代码的执行顺序，也就是 `apply from` 在业务模块 `build.gradle` 中的位置**。这本质上与直接复制粘贴脚本内容是一样的。

为了避免配置优先级混乱，推荐采用下面的模式：

```groovy
// 业务模块 build.gradle

// 1. 首先应用通用脚本，建立好基准配置
apply from: "$rootDir/gradle/common_module.gradle"

// 2. 然后在下面按需覆盖或添加业务特有的配置
android {
    // 覆盖 compileSdk（如果你真的需要与通用配置不同）
    compileSdk 33

    defaultConfig {
        // 覆盖 minSdk，但 common 里设置的其他属性（如 targetSdk）依然保留
        minSdk 26
    }

    buildTypes {
        // 这里可以为已存在的 buildType 添加额外属性，AGP 会自动合并
        release {
            // 仅追加特定的 proguard 文件，不会破坏 common 中已有的 release 配置
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules-custom.pro'
        }
    }
}

dependencies {
    // 添加业务模块特有的依赖，common 里已声明的公共依赖不受影响
    implementation 'com.example:some-custom-lib:1.0'
}
```

这种做法非常清晰：

- **先建立基线**（通用配置）
- **再进行特殊化**（业务配置覆盖或追加）

能够有效避免通用脚本在无意中覆盖业务脚本的个性化设定。同时，因为顺序明确，其他开发者在阅读业务模块的 `build.gradle` 时也能一眼看出最终生效的配置值。

# 如何打包 module aar 并上传到 maven 仓库

要将 Android 模块打包成 AAR 并上传到 Maven 仓库，主流的做法是使用 `maven-publish` 这个 Gradle 插件。

整个过程可以分为三步：

1. **应用插件**：在模块的 `build.gradle.kts` 文件中应用 `maven-publish` 插件。
2. **配置发布**：定义要发布的产物（AAR）和仓库地址（本地、私有或公共仓库，如 Maven Central）。
3. **执行发布**：运行特定的 Gradle 任务，将 AAR 上传到仓库。

**1.在构建脚本中应用并配置 `maven-publish` 插件**

在你的 Library 模块的 `build.gradle.kts`（或 `build.gradle`）文件中添加以下配置：

```kotlin
// file: your-library-module/build.gradle.kts

plugins {
    id("com.android.library")
    id("maven-publish") // 1. 应用 maven-publish 插件
}

android {
    // ... 你的 android 配置
    publishing {
        singleVariant("release") { // 2. 可选配置，指定发布变体并包含源码和文档
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// 3. 声明发布配置
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"]) // 核心：指定发布 release 变体的 AAR

                // 定义库的坐标，在依赖引用时会用到
                groupId = "com.example.mylibrary"
                artifactId = "mylibrary"
                version = "1.0.0"

                // 可选：添加 POM 信息，用于发布到 Maven Central 等公共仓库
                pom {
                    name.set("My Library")
                    description.set("A concise description of my library")
                    url.set("https://github.com/example/my-library")
                }
            }
        }
    }
}
```

2.**指定发布目标仓库**

仓库配置同样放在 `publishing` 块内，你可以配置多个仓库地址。

- **发布到本地 `.m2` 仓库**

- **发布到自定义私有/远程仓库（如 Nexus、Artifactory）**
   团队协作的标准流程。需要提供仓库的 HTTP URL 和访问凭证。

完整示例：

```kotlin
// file: your-library-module/build.gradle.kts

plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.example.mylibrary"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.example"
                artifactId = "mylibrary"
                version = "1.0.0"
                pom {
                    name.set("My Library")
                    description.set("A description of what my library does")
                    url.set("https://github.com/example/my-library")
                }
            }
        }
        repositories {
            // 1. 本地 Maven 仓库，用于开发和调试
            mavenLocal()
            // 2. 远程 Maven 仓库，用于团队共享和发布
            maven {
                name = "RemoteNexus"
                url = uri("https://nexus.example.com/repository/maven-releases/")
                credentials {
                    username = findProperty("nexus_username") as String? ?: "default_user"
                    password = findProperty("nexus_password") as String? ?: "default_password"
                }
            }
        }
    }
}
```

**3.执行上传任务**

配置完毕后，在 Android Studio 的 Terminal 中执行以下 Gradle 任务:

```bash
./gradlew publishToMavenLocal    # 发布到本地 Maven 仓库
./gradlew publish                # 发布到 build.gradle 中配置的所有远程仓库
```

4.在壳工程引用

```kotlin
// file: app-module/build.gradle.kts
dependencies {
    implementation("com.example.mylibrary:mylibrary:1.0.0")
}
```
