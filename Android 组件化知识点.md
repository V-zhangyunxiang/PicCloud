# 模块化&组件化定义

**模块化**：代码/功能层面的拆分，模块间仍存在 Gradle 编译依赖，是「物理拆分、逻辑耦合」。

**组件化**：独立应用层面的拆分，组件是独立可运行的 App，组件间无任何编译依赖，是「物理拆分、逻辑解耦」。

![[模块化 VS 组件化.png]]

按照单一职责、业务边界、功能复用，将单体 App 工程拆分为多个独立的 Module（模块），通过严格的依赖规则、职责隔离、公共下沉实现模块间解耦，是组件化的基础。

核心解耦思想：**职责拆分 + 单向依赖 + 公共下沉**，杜绝模块间交叉引用、代码混杂。

模块化是拆分，组件化是解耦：模块化解决「代码乱」，组件化解决「耦合深」，组件化是模块化的终极进阶形态。

# 组件化拆分流程

**标准化模块拆分**

基础层模块（BaseModule）：通用工具、基类、常量、第三方库依赖。

功能层模块（CommonModule）：网络、存储、图片加载、支付等通用功能。

业务层模块（UserModule/PayModule）：独立业务页面、逻辑。

App 壳工程：仅负责入口、生命周期、模块组装，无业务代码。

**严格单向依赖规则**

依赖方向：App 壳 → 业务模块 → 功能模块 → 基础模块，禁止反向依赖、同级业务模块互相依赖。

**代码与资源隔离**

每个模块**独立存放代码、资源**，命名空间前缀区分（如user_/pay_），避免资源冲突耦合。

**封装对外接口**

业务模块仅暴露必要的 API，内部实现私有化，外部无法直接访问私有代码。

# ARouter 路由如何实现跨组件页面跳转

ARouter 是阿里巴巴开源的 Android 组件化路由框架，基于**注解+编译时生成路由表+路径映射**，核心解耦思想：用「字符串路径」替代「显式类依赖」，跨组件跳转无需引用对方页面类，彻底消除组件间的代码耦合。

**编译期：生成路由表**  

目标组件（如Activity、Fragment）通过注解（如 @Route(path = "/order/detail")）标记唯一路由地址。  

框架的注解处理器（APT）在编译期扫描所有带 @Route 的组件，收集“路由地址→组件信息（全类名、参数类型、拦截器等）”的映射关系，生成 Java 代码形式的路由表。

**运行时：初始化路由表**

应用启动时，路由框架通过**反射或直接调用**生成的路由表类，将“地址→组件”的映射**加载到内存哈希表**中，形成可快速查询的路由索引。  

**跳转时：解析地址并执行**

调用方通过路由地址（如"/order/detail"）发起请求（如ARouter.getInstance().build("/order/detail").navigation()）。  

框架从**内存路由表**中查找对应的组件类，通过 **Intent 或反射** 创建实例，**自动传递参数**并完成跳转。

# 如何实现跨组件服务调用

跨组件服务调用，禁止直接依赖实现类，通过**接口下沉 + 服务发现 + 动态注册**实现解耦，核心思想：**依赖抽象接口，不依赖具体实现**。

问题本质是: **A module 如何拿到 B module 中接口实现的单例对象**，同时希望这个过程尽量对 A 透明、不引入 EventBus，也不破坏模块间的依赖关系。

核心思路是：**在 common 层提供一个“能力注册/发现中心”，B module 负责把实现注册进去，A module 只从中心获取**。

代码结构示例：
![[服务发现代码结构.png]]

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

依赖注入容器的核心是**由容器统一管理对象的创建、生命周期和依赖关系，替代手动 new 创建实例的硬编码方式**，其核心机制是“依赖声明→容器解析→自动注入”。

**依赖声明**：通过注解标记依赖的创建方式(如 @Inject 标记类的构造函数，表示该类可被容器实例化)。  

**依赖解析**：编译期通过注解处理器(APT)扫描注解，生成“依赖注入器”代码，自动解析依赖链(如 ViewModel→Repository→ApiService→Retrofit)。  

**自动注入**：运行时，容器通过生成的注入器代码，按依赖链顺序创建实例，并将其“注入”到需要的对象中(如通过 @Inject 注解字段，容器自动赋值)。

核心流程示例：
1. **在 `common` 模块中**定义接口 `IBiz`。
2. **B 模块**依赖 `common`，提供 `IBiz` 的实现类 `IBizImpl`，并通过 DI 注解声明“这个实现需要被注入”。
3. **A 模块**依赖 `common`(也可能同时依赖 B，取决于你的 DI 方案)，在需要的地方通过 `@Inject` 声明 `IBiz` 字段，DI 框架会自动将 `IBizImpl` 的单例注入进来。

这样，**A 模块无需知道 `IBizImpl` 的存在**，只需依赖 `IBiz` 接口，完全符合依赖倒置原则。

具体实现(以 Hilt 为例)

**1.在 `common` 模块定义接口**
```kotlin
interface IBiz {
    fun getParams(): String
}
```

**2.B 模块提供实现并暴露注入规则**

B 模块需要添加 Hilt 依赖，并通过 `@Module` + `@Provides` / `@Binds`声明如何提供 `IBiz` 实例。

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

A 模块只需依赖 `common` (并集成 Hilt)，直接 `@Inject` 字段即可：

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
**关键点**：A 模块**完全不需要依赖 B 模块**，也不需要知道 `IBizImpl`。但有一个前提：**整个编译时，所有使用了 `@Module` 的模块都需要被 DI 容器感知到**。

## Hilt 使用手册

### 什么是 Hilt

Hilt 是 Google 基于 Dagger 开发的依赖注入框架，专为 Android 应用设计，简化了在 Android 组件(如Activity、ViewModel、Fragment)中实现依赖注入的流程，减少模板代码。

### Hilt 注解有哪些，作用是什么

 **@Module**

- 表明这是一个 **Hilt 模块**，用来**提供依赖对象的构建方式**。
- Hilt 会在编译时扫描所有 `@Module` 注解的类，并从中收集“如何创建对象”的规则。

**@InstallIn**

- 指定这个模块**安装到哪个容器或作用域**中，如 ActivityComponent、SingletonComponent。

**@Provides**

在 @Module 中标记“提供依赖的方法”，告知 Hilt 如何创建某一类型的实例。

**@Binds**

- 这是核心：**接口绑定抽象方法**，专门用于**将接口的实现类绑定到接口**上。因为包含抽象方法，其所在的类也必须是抽象类。
- 它要求方法**只有一个参数**（这个参数是接口的实现类），返回值是接口类型。
- 参数类型 `IBizImpl` 必须能被 Hilt 自动创建（例如它用 `@Inject constructor()` 声明了构造器），否则 Hilt 不知道如何实例化它。

**@Singleton**

- 作用域注解，表示这个绑定提供的实例是**单例**。
- 配合 `@InstallIn`，确保整个 App 只有唯一的一个 `IBizImpl` 对象，并且由 Hilt 管理其生命周期。

> **为什么示例中用的 `@Binds` 而不用 `@Provides`？**

- `@Binds` 专门用于**接口和实现的绑定**，代码更简洁，**必须是抽象方法**。
- `@Provides` 用于创建复杂对象(例如需要手动构造、调用建造者模式等），**写在具体方法中，返回值直接是对象。**

**abstract fun bindBiz(impl: IBizImpl): IBiz**

- 方法名可以随便取，Hilt 不看名字，只看参数和返回值类型。
- 它定义了这样一个规则：**当注入 `IBiz` 类型时，用 `IBizImpl` 的实例来满足**。
- 因为 `IBizImpl` 是具体的实现，所以 Hilt 能够创建它（前提是它有可注入的构造函数），然后把这个实例作为 `IBiz` 返回。

**@HiltAndroidApp**

标记 Application 类，触发 Hilt 的初始化流程。

Hilt 会生成一个继承自 xxApplication 的代理类，负责创建和管理依赖注入容器，是整个应用依赖注入的入口。

**@Inject**

标记“可被注入的构造函数”或“需要注入的字段”，告知 Hilt **该类的实例或字段**需要通过依赖注入创建。

### Hilt 如何在 Android 项目中集成 

 **1.添加插件**
 
 在项目的根 `build.gradle` 文件和 app 模块的 `build.gradle` 文件中，添加 Hilt 的 Gradle 插件。
 
 >classpath "com.google.dagger:hilt-android-gradle-plugin:2.44"
 >apply plugin: 'dagger.hilt.android.plugin'
 
 **2.添加依赖**
 
**Hilt 的核心依赖(`hilt-android`) 必须通过 `common` 模块用 `api` 声明，而不能只放在 `app` 模块里。**  

原因很简单：业务模块(B、C、D 等)需要使用 `@Inject`、`@Module`、`@InstallIn` 等 Hilt 注解和 API，只有把这些库暴露给它们，代码才能编译通过。

**Hilt 分为“运行库”和“注解处理器”两部分**，两者的放置策略不同。

**依赖传递：`hilt-android` 要放在 `common` 且使用 `api`**

```kotlin
// common/build.gradle.kts
dependencies {
    // 用 api 声明，让所有依赖 common 的模块都能使用 Hilt 的注解和类
    api("com.google.dagger:hilt-android:2.x")
}
```

**注解处理器：每个用 Hilt 的模块都要自己加**

`hilt-android-compiler` 是编译时注解处理器，它不参与运行时，也不能通过 `api` 传递。**哪个模块写了 Hilt 注解，哪个模块就必须自己声明注解处理器依赖。**

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

> **app 模块**同样需要加注解处理器，因为它有 `@HiltAndroidApp` 和可能的 `@AndroidEntryPoint`。
> 
   **common 模块本身**如果定义了 Hilt 相关的抽象(例如接口但无实现绑定)，可能不需要处理器；但如果内部也写了 `@Module` 或 `@Inject`，同样需要加。

3.**创建入口**

创建一个继承自 `Application` 的类，并为其添加 `@HiltAndroidApp` 注解。这个注解会触发 Hilt 生成代码，创建整个应用的依赖容器。

### 定义“配方”：告诉 Hilt 如何创建对象

**方式一：直接使用 `@Inject` 注解构造函数**

这是最直接的方式。在你自定义的类（如 `UserRepository`）的构造函数上添加 `@Inject` 注解，Hilt 就知道该如何创建它的实例了。

```kotlin
// 例子：UserRepository 类
class UserRepository @Inject constructor(
    private val apiService: ApiService // 它可能还依赖其他对象
) { /* ... */ }
```

如果 `UserRepository` 还依赖 `ApiService`，只要 `ApiService` 的构造函数也有 `@Inject` 注解，Hilt 就会自动去解析并创建它，这个流程被称为**传递性依赖注入**。

**方式二：使用 Hilt 模块 (`@Module`)**

对于**接口（Interface）、第三方库类**（如 Retrofit、OkHttp）等无法直接修改构造函数的类，就需要使用 Hilt 模块来提供“配方”。

- 创建一个用 `@Module` 和 `@InstallIn` 注解的类。
    
- 在类中，使用 `@Provides` 注解的方法来告诉 Hilt 如何创建该类型的实例。

```kotlin
// 例子：提供一个 AnalyticsService 的实例
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAnalyticsService(): AnalyticsService {
        return AnalyticsService() // 或者更复杂的创建逻辑
    }
}
```

对通过 Hilt 模块来提供实例的方式，其方法返回的实例有几种使用场景。

场景一：注入到其他类的构造函数(最常用)
场景二：注入到 Activity / Fragment 的字段中
场景三：在另一个 `@Provides` 方法中继续依赖它
```kotlin
//场景三示例
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAnalyticsService(): AnalyticsService {
        return AnalyticsService()
    }

    @Provides
    fun provideNetworkMonitor(analytics: AnalyticsService): NetworkMonitor {
        // Hilt 会自动把上面的 AnalyticsService 实例传进来
        return NetworkMonitor(analytics) 
    }
}
```

### 触发“生产”：让 Hilt 自动注入实例

**方式一：构造函数注入**

这是最推荐的方式。在你的类（如 `HomeViewModel`）的构造函数中，直接声明需要的依赖。Hilt 会自动在创建这个类时，提供所有依赖的实例。

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() { /* ... */ }
```

**方式二：字段注入（Field Injection）**

对于 `Activity` 或 `Fragment` 这类由 Android 系统创建、无法修改其构造函数的组件，使用字段注入。

1. 用 `@AndroidEntryPoint` 注解该类，让 Hilt 能够介入其生命周期。
    
2. 在需要注入的字段（如 `analytics`）上添加 `@Inject` 和 `lateinit var` 注解。

```kotlin
@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject lateinit var analytics: AnalyticsService
    // ...
}
```

### 多 Module 场景下，如何使用 DI 提供 API 方法

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

# 组件如何实现源码和 AAR 动态切换

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

# 多组件 gradle 配置复用策略

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

// 导入 common_module
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

# 打包组件 AAR 并上传到 Maven 仓库

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
        //AGP 8.0+ 写法
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        //定义要发布什么（产物）
        publications {
            create<MavenPublication>("release") {
                //核心：指定发布 release 变体的 AAR
                from(components["release"])
                // 定义库的坐标，在依赖引用时会用到
                groupId = "com.example"
                artifactId = "mylibrary"
                version = "1.0.0"
                
                // 更通用的写法：直接添加源码和文档 JAR，与 AGP 8.0+ 写法二选一
                artifact(tasks.named("sourcesJar"))
                artifact(tasks.named("javadocJar"))
                
                // 可选：添加 POM 信息，用于发布到 Maven Central 等公共仓库
                pom {
                    name.set("My Library")
                    description.set("A description of what my library does")
                    url.set("https://github.com/example/my-library")
                }
            }
        }
        //定义发布到哪里（目标仓库）
        repositories {
            // 1. 本地 Maven 仓库，用于开发和调试
            mavenLocal()
            // 2. 远程 Maven 仓库，用于团队共享和发布
            maven {
                name = "RemoteNexus"
                url = uri("https://nexus.example.com/repository/maven-releases/")
                //访问凭证
                credentials {
                    username = findProperty("nexus_username") as String? ?: ""
                    password = findProperty("nexus_password") as String? ?: ""
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

**4.在壳工程引用**

```kotlin
// file: app-module/build.gradle.kts
dependencies {
    implementation("com.example.mylibrary:mylibrary:1.0.0")
}
```

# 解耦方式

## 布局解耦

遵循单一职责原则：一个 XML 布局只负责一个 UI 功能/模块，拒绝将所有控件、页面元素写在同一个布局文件中，通过拆分实现 UI 结构**模块化、复用化、隔离化**，从视图层面解除耦合。

**公共 UI 组件拆分**

抽取 APP **全局通用**的 UI(标题栏、底部导航、加载动画、空页面、错误页面)为独立布局，所有页面复用，不重复编写。

**功能区域拆分**

把一个完整页面拆分为：头部、内容区、侧边栏、列表项、底部操作栏等独立 XML 文件。

**业务模块隔离**

按业务划分布局文件夹(如用户模块、支付模块)，避免不同业务 UI 混杂。

**自定义组合控件封装**

将复用的控件组合(如手机号输入框+验证码按钮)封装为 CustomView，完全隐藏内部 UI 结构。

**include/merge/ViewStub** 

这是 Android 原生布局解耦三件套，分别从**复用、层级优化、懒加载**三个维度解决布局耦合问题。

**include**：布局复用，拆分公共 UI。将独立 XML 布局嵌入到主布局中，隔离公共 UI 与业务 UI，主布局无冗余代码。

**merge**：减少嵌套层级，解除冗余耦合。搭配 include 使用，删除多余的根布局，降低布局嵌套深度。避免因嵌套导致的 UI 渲染耦合、性能损耗。在被引入的布局根布局与父布局一致时使用。

**ViewStub**：懒加载，解耦初始化与备用 UI。默认不加载、不占用内存，调用 inflate() 才渲染视图。将错误页、空页面、弹窗等非首屏 UI 与主布局初始化隔离。

## Activity/Fragment 实现页面解耦

Activity/Fragment 的核心职责：生命周期管理、UI 渲染、用户交互分发。

解耦核心：剥离所有非核心职责(业务逻辑、数据处理、网络请求、控件逻辑)，让页面只做“控制器”，不做“实现器”。

**抽取自定义 View**

复杂控件的绘制、点击、状态逻辑全部放入独立 View，页面仅引用，不关心内部实现。

**抽取业务帮助类(Helper/Manager)**

网络请求、数据计算、权限处理、文件操作抽离为独立类，页面仅调用方法。

**拆分长方法**

把 onCreate 中的代码拆分为 initView()、initListener()、initData() 等细粒度方法。

**封装基类**

通用逻辑放入 BaseActivity/BaseFragment，子类只写业务代码。

**剥离数据操作**

数据库、SharedPreferences、网络请求绝不直接写在页面中。

**避免匿名内部类滥用**

减少对页面的强引用，降低耦合同时避免内存泄漏。

## 工具类/基类/接口抽象-实现代码解耦

**共性下沉，个性上浮**：将全项目通用的重复代码、模板代码集中管理，业务代码只关注自身业务，不依赖通用逻辑的具体实现，实现通用能力 ↔ 业务代码彻底解耦。

**工具类(Util)抽取解耦**

适用场景：全局通用无状态逻辑(Toast、SP、网络判断、日期格式化、图片加载)。

实现方式：静态方法/单例模式，对外暴露通用 API。

**基类(BaseActivity/BaseFragment)抽取解耦**

适用场景：页面通用生命周期逻辑、沉浸式状态栏、标题栏、弹窗管理、权限申请。

实现方式：抽象基类封装模板方法，子类实现抽象方法填写业务逻辑。

**接口抽象解耦**

遵循**面向接口编程 + 依赖倒置**原则：调用方只依赖接口约定，不依赖具体实现类，业务逻辑封装在实现类中，通过接口关联，实现调用方 ↔ 业务逻辑解耦。

**定义接口**：约定业务行为(不写具体逻辑)。

**编写实现类**：完成具体业务逻辑。

**调用方依赖接口**：传入实现类对象，不直接 new 实现类。

## 代码分层解耦

**层级拆分 + 职责隔离**：将代码按「数据流向」分为不同层级，层与层之间只通过接口通信，不直接依赖具体实现，实现各业务模块独立、无耦合，这是 Android 架构化解耦的基础。

**UI 层(View 层)**

角色：Activity/Fragment/自定义 View。

职责：仅负责 UI 展示、用户点击、生命周期管理。

禁忌：不写网络请求、不写数据计算、不写业务逻辑。

**业务逻辑层(ViewModel/Presenter 层)**

角色：ViewModel/Presenter 类。

职责：接收 UI 指令 → 调用数据层 → 处理结果 → 回调给 UI。

作用：作为 UI 与数据层的中间桥梁。

**数据层(Model 层)**

角色：Repository/DataSource 类。

职责：网络请求、数据库、SP、文件操作。

作用：为业务层提供数据，不关心 UI 展示。

**层间通信规则**

1、上层依赖下层接口，不依赖具体类。

2、数据单向流动：UI → 业务层 → 数据层。

3、禁止跨层调用(UI 绝不直接访问数据库)。

## Activity 与 Fragment 通信的解耦方案有哪些

**ViewModel + LiveData（Jetpack 官方最优解）**

原理：基于观察者模式，ViewModel 独立于生命周期，存储共享数据，LiveData 实现数据订阅，Activity/Fragment 都只依赖 ViewModel，互不持有对方引用。

解耦价值：彻底解除双向耦合，生命周期安全，无内存泄漏。

**Fragment Result API（AndroidX 官方推荐）**

原理：通过 setFragmentResult/setFragmentResultListener 实现跨 Fragment/Activity 结果传递，基于键值对通信，无需引用。

解耦价值：轻量无耦合，官方原生支持，适配所有场景。

**接口回调（传统标准解耦方案）**

原理：Fragment 定义回调接口，Activity 实现接口，Fragment 通过 onAttach 绑定接口，不直接持有 Activity 实例。

解耦价值：面向接口编程，无硬编码耦合。

**setArguments 带参实例化（数据传递）**

原理：Fragment 通过 setArguments 绑定数据，禁止直接写构造方法传参，Activity 仅通过 Bundle传递数据。

解耦价值：避免状态丢失，隔离实例依赖。

## 广播如何实现 Android 组件解耦

广播基于发布-订阅模式：组件作为发送方发送 Intent 广播，BroadcastReceiver 作为接收方监听广播，发送方与接收方完全无直接引用、互不依赖，**仅通过 Intent（消息载体）通信**。

跨 Activity、Fragment、Service、Application 全局通信，全局广播存在安全风险，**仅推荐配合本地广播使用。**

## 如何使用 EventBus 跨组件解耦

EventBus/Otto 是观察者模式的第三方事件总线框架，比广播更轻量、更易用，定义事件（Event）作为消息载体，发送方发送事件，接收方订阅事件，组件间无任何引用、无接口、无 Intent，彻底解耦，**跨所有组件、线程、页面自由通信**。

以 EventBus 为例，实现步骤如下：

定义事件类（空类，仅作为消息标识）

```kotlin
data class LoginEvent(val isLogin: Boolean)
```

接收方注册+订阅（Activity/Fragment/Service）

```kotlin
override fun onStart() {
  super.onStart()
  EventBus.getDefault().register(this)
}

@Subscribe(threadMode = ThreadMode.MAIN)
fun onLoginEvent(event: LoginEvent) { 
     // 接收消息
}
```

发送方发送事件（无任何依赖）

```kotlin
EventBus.getDefault().post(LoginEvent(true))
```

## Service 与调用方的解耦

传统方式：调用方直接 bindService 持有 Service 实例，强引用耦合，无法独立维护、测试。

**startService 命令式启动（基础解耦）**

原理：通过 Intent 传递命令，调用方不持有 Service 实例，仅发送指令，完全无引用耦合。

适用：无需与 Service 交互的场景（如后台下载、播放音乐）。

**Messenger 信使通信（轻量解耦）**

原理：基于 Handler 的跨进程通信，调用方与 Service 通过 Messenger 发送消息，无直接依赖。

**AIDL 接口通信（进阶解耦）**

原理：定义 AIDL 接口约定通信规则，调用方依赖接口，不依赖 Service 实现类。

**封装 ServiceManager 统一管理**

原理：抽取中间管理类，封装 Service 启动、绑定、通信逻辑，调用方仅依赖 Manager，彻底隔离Service。

## ContentProvider 与调用方的解耦

ContentProvider 是 Android 官方**数据访问代理组件**，遵循**单一职责+封装原则**：

1、底层封装数据库（SQLite）、文件、内存数据

2、对外提供统一的 ContentResolver API。

3、调用方（任何组件/应用）只依赖 API，不关心数据存储细节。

**实战解耦逻辑**

1、数据层：ContentProvider 实现增删改查，隐藏数据库实现。

2、调用方：通过 getContentResolver() 调用方法，无需知道表结构、数据库名称。

3、跨应用数据共享（如通讯录、相册），调用方与数据提供方完全解耦。

## 组件通信解耦总结

**基础通信**：Activity/Fragment 优先用 ViewModel + LiveData 彻底解耦。

**全局通信**：用本地广播替代全局广播，用 EventBus 简化跨组件通信。

**服务通信**：通过 Intent/Messenger/AIDL 隔离 Service 与调用方。

**数据通信**：ContentProvider 封装数据源，实现数据层解耦。

**通用方案**：回调接口是所有组件通信的基础解耦手段。

所有方案的核心思想：组件间不直接持有实例，通过抽象/消息/接口通信，实现**零耦合、高可用**。

## 设计模式驱动解耦

见[[Android 知识点#设计模式]]

## 主流架构模式解耦

MVC：无真正解耦，职责混乱，已淘汰。

MVP：接口抽象解耦 UI 与逻辑，解决 MVC 痛点，但接口冗余。

MVVM：ViewModel + LiveData 实现零引用解耦，官方首选。

Clean：多层级依赖倒置，业务与 Android 框架彻底隔离，大型项目终极方案。

MVI：单向数据流+单一状态，解耦最严谨，现代化架构趋势。

## 依赖注入解耦

见[[#Hilt 使用手册]]

## 异步&数据层解耦

**异步解耦**：协程（首选）/ RxJava 剥离线程管理与业务逻辑，生命周期安全无耦合。

**数据流解耦**：Flow/StateFlow 实现数据生产与UI消费的彻底隔离。

**数据来源解耦**：Repository 统一数据入口，屏蔽网络/本地/内存差异。

**网络解耦**：Retrofit 接口化请求，隔离底层网络实现。

**本地存储解耦**：Room + 封装 SP / 数据库，面向接口操作数据，屏蔽存储细节。

## AOP 实现通用逻辑与业务逻辑解耦

将**埋点、日志、权限校验、点击防抖、性能监控、异常捕获**等通用逻辑，从核心业务代码中剥离，通过切点、切面、通知在不修改业务代码的前提下，自动植入通用逻辑。

AspectJ 是 AOP 的主流方案，见[[启动耗时优化#AspectJ 使用]]。

