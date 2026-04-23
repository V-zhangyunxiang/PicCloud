[TOC]

# <span id="toc">Android</span>

## Activity 启动流程

当应用调用 `startActivity` 时，通过 `Instrumentation` 跨进程请求系统的 **AMS**，AMS 接收到请求后解析 Intent、检查 Activity 权限，并**通知前一个 Activity 执行 `onPause`**，随后根据启动模式确定 Activity 所在栈，若进程不存在则由 **Zygote** fork 新进程并执行 `ActivityThread.main`，新进程通过 `attachApplication` 向系统报到，系统随后回传事务指令，应用进程通过 **Handler H** 切换到主线程，利用 `Instrumentation` **反射**创建 Activity 实例并触发从 `onCreate` 到 `onResume` 的生命周期。
![](Activity%20启动.png)
## onSaveInstanceState，onRestoreInstanceState 的调用时机

`onSaveInstanceState()` 在 Activity 被非主动销毁前调用（`onPause() -> onSaveInstanceState() -> onStop()`）。

`onRestoreInstanceState()` 在重建后的 Activity `onStart()` 之后、`onResume()` 之前被调用，用于恢复状态。

## onCreate 和 onRestoreInstance 恢复数据的区别

`onCreate` 负责初始化和恢复非视图数据，`onRestoreInstanceState` 专注于在视图就绪后恢复视图状态。

## Activity A 跳转 Activity B，再按返回键，生命周期执行的顺序

Activity A 的 onPause → Activity B 的 onCreate → onStart → onResume → Activity A 的 onStop。如果 B 是透明主题或是个 DialogActivity，则不会回调 A 的 onStop。

Activity B 的 onPause -> ActivityA 的 onRestart - onStart - onResume -> Activity B 的 onStop - onDestroy。

## onStart 和 onResume、onPause 和 onStop 的区别

onStart 表示 Activity 可见，但不可交互。

onResume 表示获取到焦点，可以与 Activity 交互。

onPause 表示 Activity 失去焦点，但仍然可见。

onStop 表示完全不可见。

> 1. 当前 Activity 中弹出一个普通的 `AlertDialog` 或 `DialogFragment`，**当前 Activity 不会回调 onPause**，因为 Dialog 是 Activity 的一部分，并没有导致失去焦点。
> 2. 启动了一个**透明或半透明**的 Activity，**只走 onPause 不走 onStop**。

## Activity 之间传递数据的方式 Intent 是否有大小限制；如果超出了限制，如何解决。

Intent 传输数据的限制源于 Binder 的事务缓冲区，大小约为 1MB 且为进程内所有 Binder 调用共享，一旦超过，会抛出 `TransactionTooLargeException`。

对于大数据传输，可使用以下方案
- **轻量化传参**：在 Intent 中仅传递数据的 **ID 或路径**，目标 Activity 启动后通过单例、ViewModel 加载。
- **持久化方案**：利用文件、SP、SQLite 数据库存储，适用于数据量极大的场景。

## Activity 的启动模式和使用场景；onNewIntent 方法什么时候会执行

standard : 默认模式，每次启动都创建新实例。

singleTop：栈顶复用模式。若 Activity 已在栈顶则会复用并回调 onNewIntent，常用于通知栏跳转。如果不在会创建一个新的实例。

singleTask：栈内复用模式。若栈中存在这个 Activity 的实例，不管它是否位于栈顶，都会被复用，并将它上方的 Activity 清空，回调 onNewIntent，常用于 App 主页。还可以设置 taskAffinity 属性，默认为包名，相同的 taskAffinity 会在同一个任务栈，不同则在不同栈中。

singleInstance：单实例模式。该 Activity 独占一个任务栈，且该栈内**永远只有这一个实例**。常用于**通话界面**、**闹钟**等系统级应用，这些功能在整个系统中应该是全局唯一的。

在 onNewIntent 方法中，建议先手动调用 setIntent(intent) 更新 Activity 的 Intent，确保之后调用 getIntent 能够获取到是最新的 Intent 数据。

## 除了 Manifest 设置，还有别的方法改变启动模式吗?

可以通过 Intent 的 **Flags**。

FLAG\_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TOP = singleTask

FLAG_ACTIVITY_SINGLE_TOP  = singleTop

代码中设置 Flags 的优先级高于 Manifest 中的静态设置。

## Activity 的显式启动和隐式启动

显式启动通过指定类名实现，简单高效，常用于应用内部。

隐式启动则通过 intent-filter 匹配 `Action`、`Category` 和 `Data` 实现。匹配时有三个核心细节：

1. **Action** 匹配其中一个即可。
2. **Category** 必须被 filter 完全包含(filter 可以超出 Intent 的 category 数量)。Filter 中**必须**声明 `DEFAULT` 类别，匹配才能成功。因为 `startActivity()` 方法在执行时，会自动为 Intent 补上一个 `android.intent.category.DEFAULT`。
3. **Data** 需匹配 URI 协议。

隐式启动需要先通过 `intent.resolveActivity` 进行检查是否存在，对于设置了 Filter 的 Activity，还需要注意其 `exported` 属性带来的安全风险。

## Android Scheme 使用场景，协议格式，如何使用

Scheme 是一种基于 URI 的页面跳转协议，主要用于 H5 唤起原生页面、Push/短信跳转以及 App 间的页面调用。

**格式**

\<scheme>://\<host>:\<port>/\<path>?\<query>

**使用**

在 manifest 中配置能接受 Scheme 方式启动的 Activity

```
<intent-filter> 
    <!--必有项-->
    <action android:name="android.intent.action.VIEW"/>
    <!--如果希望该应用可以通过浏览器的连接启动，则添加该项-->
    <category android:name="android.intent.category.BROWSABLE"/>
    <!--表示该页面可以被隐式调用，必须加上该项-->
    <category android:name="android.intent.category.DEFAULT"/>
    <!--协议部分-->
    <data android:scheme="urlscheme" android:host="auth_activity">
 </intent-filter> 
 
```

上面的 Scheme 协议为：urlscheme://auth_activity，在 Activity 启动后，通过 `getIntent().getData()` 解析参数

## **Deep Link**、App Links 和 Scheme 之间的关系是什么

**Deep Link** 是一个统称，指的是跳转到 App 内部页面的技术。

**Scheme** 是 Deep Link 的基础实现，可自定义协议，但缺点是当多个 App 注册相同协议时会弹出选择框，且安全性较低。

**App Links** 是 Android 6.0 推出的高级 Deep Link。使用标准的 **HTTP/HTTPS 链接**，并通过服务器端的 **assetlinks.json** 文件进行域名所有权验证，可直接唤起 App 而无需用户选择。

## Android 系统为什么会触发 ANR，它的机制是什么

ANR 的本质是**主线程在规定时间内没有完成特定的任务**。

其触发场景主要分为四类：

1. **输入事件**（如点击、触摸）超过 5s；
2. **广播**在前台 10s 或后台 60s 未处理完；
3. **服务**在前台 20s 或后台 200s 未处理完；
4. **ContentProvider** 在 10s 内未响应。

**底层机制**可以理解为一种‘埋炸弹’机制：系统在发起请求时会发送一个延时消息，如果应用及时处理完并回执，系统就‘拆除炸弹’；否则，延时消息触发，系统就会收集 `traces.txt` 文件并弹出 ANR 警告。

## Activty 间(传递/存储)数据的方式

内存传递
- **Intent + Bundle**：最基础，适合少量、简单数据
- **ViewModel (Shared)**：Jetpack 架构组件，适合 Activity 与其内部 Fragment，或多个 Fragment 间共享数据。
- **单例 / 全局缓存**：适合传递大对象或复杂对象，但需注意内存管理和进程回收后的空指针检查。

持久化传递
- **SharedPreferences / DataStore**：适合存储用户的配置信息、登录状态等。
- **数据库 (Room/SQLite)**：适合结构化的大量数据。
- **文件 (File)**：适合图片、音视频等二进制大数据。

跨进程传递
- **ContentProvider**：Android 官方推荐的跨应用数据共享方式。
- **AIDL / Messenger**：基于 Binder 的跨进程调用。

## 跨 App 启动 Activity 的方式，注意事项

跨 App 启动 Activity 主要有三种方式：

1. **隐式 Intent**：通过 Action 和 Data 匹配，是最常用的解耦方式。
2. **显式 Intent**：通过 `ComponentName` 指定包名和类名，前提是已知对方精确路径。
3. **Scheme 协议**：通过自定义 URI 唤起，适用于 H5 或第三方 App 跳转。

**在实践中，有以下注意事项：**

1. **权限：** **目标 Activity** 必须声明 `android:exported="true"`。如果对方设置了自定义权限，调用方还必须在 Manifest 中申请该权限。
2. **软件包可见性**：在 Android 11 及以上版本，必须在 `<queries>` 标签中声明目标包名 或 Action，否则无法检测到目标 App。
3. **稳定性保护**：跨 App 调用极易因对方未安装或版本更迭导致崩溃，必须配合 `resolveActivity` 检查或 `try-catch ActivityNotFoundException`。

## Service 的生命周期

**startService:** onCreate -> onStartCommand -> onDestroy

**bindService:** onCreate -> onBind -> onUnBind -> onDestroy

**startService + bindService 混合模式:**

- 启动流程: onCreate -> onStartCommand -> onBind -> onUnBind -> onDestroy
- 销毁流程: 必须**同时满足**两个条件，Service 才会执行 `onDestroy()`：
1. 调用了 `stopService()` 或 `stopSelf()`。
2. **所有**绑定的客户端都执行了 `unbindService()`。

## 为什么要有两种启动方式

| 特性             | startService               | bindService                  |
| :------------- | :------------------------- | :--------------------------- |
| **场景**         | 执行后台独立任务                   | 需要与 Service 进行交互/通信          |
| **与启动者关系(关键)** | **解耦**。启动者销毁后，Service 依然运行 | **绑定**。启动者销毁，Service 自动解绑并销毁 |
| **数据交互**       | 只能通过 Intent 传值，交互不便        | 通过 `IBinder` 接口进行双向通信        |
| **停止方式**       | `stopService` / `stopSelf` | `unbindService`              |

## Service 的 onStartCommand 方法有几种返回值含义

**START\_STICKY**

Service 被系统杀掉后，尝试重新创建并回调 `onStartCommand`，但 Intent 为 null。

**START\_NOT\_STICKY**

被杀掉后不重启。

**START_REDELIVER_INTENT**

重启并重传最后的 Intent。

## 能否在 Service 执行耗时操作？如何处理耗时操作？

Service 默认运行在应用的主线程中，因此**不能直接在其中执行耗时操作**，否则会阻塞 UI 导致 ANR。

针对耗时任务，有以下几种处理方案:

1. **简单异步**：在 Service 内部手动开启子线程或使用 `HandlerThread`。
2. **持久化后台任务**：官方目前强烈推荐使用 **Jetpack WorkManager**，它能保证任务在进程被杀或重启后依然可靠执行，并能灵活配置约束条件。
3. **长时运行任务**：如果任务需要长时间保持，必须开启**前台服务(Foreground Service)** 并绑定通知，以防止被系统回收。

## Service 与 Activity 怎么实现通信

在**同进程**内，最标准的方式是使用 `bindService`。Activity 通过 `ServiceConnection` 获取 Service 提供的 `Binder` 对象，从而直接调用其方法。如果需要 Service 主动通知 Activity，通常会在 Binder 中定义一个设置回调接口的方法，让 Activity 注入一个 Listener。

对于**解耦要求较高**的场景，使用 `LiveData` 或 `SharedFlow` 这种观察者模式，让 Activity 自动感知 Service 的数据变化。

如果涉及**跨进程通信**，根据复杂度选择 `Messenger` 或 `AIDL`。

## **为什么不直接在 Activity 里 new 一个 Service 对象来通信？**

Service 是 Android 的四大组件之一，必须由系统（AMS）负责实例化并管理其生命周期，手动 `new` 出来的只是一个普通 Java 对象，它没有 Context 环境，无法调用 `startService` 等系统方法，不是真正的 Service。

## 广播的分类和使用场景

Android 广播主要分为四类：

1. **普通广播**：完全异步，效率高但不可拦截。
2. **有序广播**：按优先级同步传递，支持拦截和数据修改，常用于系统级拦截场景。
3. **本地广播**：仅限应用内通信，安全性高。但目前 `LocalBroadcastManager` 已被官方废弃，建议使用 `LiveData` 或 `Flow` 替代。
4. **粘性广播**：已废弃，不再推荐使用。

**在实际开发中，需要特别注意 Android 8.0 之后的限制：**  
绝大多数隐式广播不再支持静态注册，必须改为动态注册，以减少系统功耗。

## **广播接收者的 onReceive 方法里能做耗时操作吗？**

`onReceive` 运行在主线程，且生命周期极短（通常只有 10 秒左右）。如果执行耗时操作会触发 **ANR**。

## 广播动态注册和静态注册的区别

**静态注册**

写在 Manifest 中，App 未启动也能接收（受 8.0 限制），常驻系统，耗电。

**动态注册**

在代码中调用 `registerReceiver`，生命周期跟随 Context(如 Activity)，灵活性高，必须记得在 `onDestroy` 中 `unregister` 防止内存泄漏。

## **广播的发送是同步还是异步**

对发送者来说，`sendBroadcast` 是**异步**的。它把 Intent 交给 AMS 后就立即返回了，不会等待接收者执行完。

## 广播原理

Android 广播的底层实现是一套典型的 **Binder 跨进程通信机制**。

1. 接收者通过 Binder 向系统服务 **AMS** 注册其 `IntentFilter`；

2. 当发送者调用 `sendBroadcast` 时，请求会跨进程传给 AMS。AMS 负责在全局注册表中进行**匹配**，并将匹配到的接收者放入 **BroadcastQueue** 队列中。

3. AMS 通过接口跨进程回调应用进程。应用进程在收到回调后，利用内部的 **Handler** 机制将逻辑切换到**主线程**，最终触发 `onReceive` 方法。

## 什么是 ContentProvider

是 Android 跨进程共享数据的标准组件。它通过 **URI** 唯一标识数据资源，并向外部提供统一的 CRUD 接口，屏蔽了底层如 SQLite 或文件存储的实现细节。

**工作流程**
1. **服务端**：继承 `ContentProvider` 并实现核心方法，利用 `UriMatcher` 进行路由解析，并在 Manifest 中声明唯一的 `authorities`。
2. **客户端**：通过 `ContentResolver` 配合 URI 发起请求。

## 外部应用怎么定位到你的 ContentProvider？

**URI 格式**：`content://authority/path/id`

- `content://`：标准前缀。
- `authority`：授权信息，通常是包名，用于唯一标识一个 Provider。
- `path`：数据表名或路径。
- `id`：可选，指向某条具体数据。

## **ContentProvider 的 onCreate() 什么时候执行？**

它在 **Application.onCreate() 之前**执行（在 `attachBaseContext` 之后）。系统启动进程后，会先加载并初始化所有的 ContentProvider。所以不要在 Provider 的 `onCreate` 里做耗时操作。

## AIDL 也能跨进程，为什么要用 ContentProvider？

AIDL 适合跨进程的方法调用(行为)；而 ContentProvider 专门为**结构化数据共享**设计，它内置了对数据库、文件的支持，且配合 `CursorLoader` 或 `ContentObserver` 能更方便地处理 UI 与数据的绑定。

## ContentProvider、ContentResolver、ContentObserver 之间的关系

**ContentProvider** 是数据的**提供方**，它封装了底层存储并通过 URI 暴露接口。

**ContentResolver** 是数据的**消费方接口**，它充当客户端代理，负责根据 URI 寻找并操作对应的 Provider，实现了调用方与提供方的解耦。

**ContentObserver** 则是**反馈机制**，它允许客户端监听特定 URI 的数据变化。

## RecyclerView 每一级缓存具体作用是什么，分别在什么场景下会用到哪些缓存

RecyclerView 的缓存机制本质上是为了**减少反射创建 View** 和 **减少数据绑定** 的开销。

第一级：Scrap (屏幕内缓存)

- **包含**：`mAttachedScrap` 和 `mChangedScrap`。
- **作用**：在 **布局过程中（onLayoutChildren）** 临时存放。
- **场景**：
    - `mAttachedScrap`：处理普通的 `notifyDataSetChanged` 或滑动时的布局计算。
    - `mChangedScrap`：专门用于**动画**。比如 `notifyItemChanged` 时，受影响的 ViewHolder 会先进这里。
- **性能：不需要重新绑定数据**。它们直接复用，不走 `onBind`。

第二级：mCachedViews (屏幕外缓存)

- **作用**：缓存 **刚滑出屏幕** 的 ViewHolder。默认大小为 2。
- **场景**：**快速回滚**。比如你往下滑了两个条目，马上又往上滑，这两个条目会直接从这里拿。
- **性能：不需要重新绑定数据**。它是按 **position(位置)** 匹配的。只要位置对得上，直接拿来用，不走 `onBind`。Cache 是强引用，它持有的 ViewHolder 越多，占用的内存就越高，所以 2 是为了平衡内存和性能。

第三级：ViewCacheExtension (自定义缓存)

- **作用**：留给开发者自定义。
- **现状**：极少使用。

第四级：RecycledViewPool (缓存池)

- **作用**：当 `mCachedViews` 满了(超过 2 个），最旧的 ViewHolder 会被推入此池。
- **结构**：`SparseArray`，按 **ViewType** 存储，每种类型默认存 5 个。
- **场景**：**大量滑动** 或 **多个 RecyclerView 共享缓存**。
- **性能：必须重新绑定数据**。它只缓存了 View 的结构(ViewHolder 实例)，但不保证数据正确，所以必须回调 `onBindViewHolder`。

## 为什么 mCachedViews 按 Position 找，而 Pool 按 ViewType 找？

因为 `mCachedViews` 里的 View 刚离开屏幕，数据还是热的，按位置找可以完全跳过绑定逻辑。而 `Pool` 里的 View 可能已经离开很久了，数据已经失效，只能保证“长得一样”（ViewType 相同），所以必须重新绑定。

## RecyclerView 复用 ViewHolder 的过程

当 RecyclerView 需要一个 ViewHolder 时，查找顺序如下：

1. **mAttachedScrap** (位置匹配，无需 onBind)
2. **mCachedViews** (位置匹配，无需 onBind)
3. **mViewCacheExtension** (自定义)
4. **RecycledViewPool** (类型匹配，**需要 onBind**)
5. **onCreateViewHolder** (全部失效，彻底新建)

## RecyclerView 性能优化

### 一、 减少布局与测量开销

1. **setHasFixedSize(true)**：固定高度时跳过 requestLayout。
2. **布局扁平化**：使用 ConstraintLayout 等手段减少 View 层级。
3. **共享缓存池**：多个列表间共享 `RecycledViewPool`。
### 二、 优化数据绑定逻辑

1. **避免耗时操作**：`onBind` 只做纯赋值，不计算、不解析。
2. **监听器复用**：在 `onCreate` 中设置点击事件，不在 `onBind` 中 new 对象。
3. **Payload 局部刷新**：只刷新变动的 UI 元素，不刷新整个 Item。
### 三、 提升滑动流畅度

1. **DiffUtil 异步计算**：使用 `ListAdapter` 避免全局刷新带来的卡顿。
2. **图片加载策略**：滑动时暂停加载（Glide.pauseRequests），停止后恢复。
3. **加大预取与缓存**：调大 `mCachedViews` 数量。
### 四、 内存与资源管理

1. **及时释放资源**：在 `onViewDetachedFromWindow` 中释放动画或视频资源。
2. **分页预加载**：在滑动到底部前提前加载下一页。

## Fragment 的生命周期 & 结合 Activity 的生命周期

onAttach → onCreate → onCreateView  → onViewCreated → onStart → onResume → onPause → onStop → onDestroyView → onDestroy → onDetach。

| Activity 状态   | 触发的 Fragment 生命周期                                  |
| :------------ | :------------------------------------------------- |
| **onCreate**  | onAttach → onCreate → onCreateView → onViewCreated |
| **onStart**   | **onStart**                                        |
| **onResume**  | **onResume**                                       |
| **onPause**   | **onPause**                                        |
| **onStop**    | **onStop**                                         |
| **onDestroy** | **onDestroyView → onDestroy → onDetach**           |

**onAttach**

当 Fragment 和 Activity 建立关联时调用

**onCreateView**

当 Fragment 创建视图调用，在 onCreate 之后。

**onViewCreated**

在 `onCreateView` 成功返回 View **之后立即调用**的，是进行 `findViewById` 或 **ViewBinding 绑定**的最佳时机

**onDestroyView**

在 Fragment 中的布局被移除时调用。

**onDetach**

当 Fragment 和 Activity 解除关联时调用。

Fragment 最特殊的地方在于它有**两个生命周期**：

1. **Fragment 实例的生命周期**（onCreate 和 onDestroy）
2. **Fragment 视图的生命周期**（onCreateView 和 onDestroyView）

## add 和 replace 的区别

- `add`：直接叠加一个 Fragment，原 Fragment 不会销毁，其生命周期不走 `onPause/onStop`。
>  **Fragment** 只是 Activity 内部的一个 View 层级结构，add 新 Fragment 时旧 Fragment 依然保留在层级中，且宿主 Activity 仍处于 `Resumed` 状态，所以旧 Fragment 也会保持 `Resumed`。
- `replace`：移除旧的再加新的。旧 Fragment 会走 `onDestroyView`，如果没被加到回退栈，还会走 `onDestroy`。

## Activity 和 Fragment 的通信方式？Fragment 之间如何进行通信？

1. 初始化传参 (Activity → Fragment)
    **setArguments(Bundle)**：最基础、最稳健。
2. 双向实时通信 (Fragment ↔ Activity / Fragment)
   **共享 ViewModel + LiveData (首选)**：
    - **优势**：生命周期安全、代码简洁、支持复杂数据流。
   **接口回调 (经典)**：
    - **优势**：逻辑直观，适合简单的事件通知。

## 为什么使用 Fragment.setArguments(Bundle) 传递参数，而不是构造函数？

在 Fragment 重建时，通过 setArguments 设置的参数会被系统自动保存并在重建时恢复，而构造参数会丢失。

## FragmentPageAdapter 和 FragmentStatePageAdapter 区别

FragmentPagerAdapter (视图销毁，实例保留)

> 只销毁 Fragment 的视图，但保留实例在内存中。这种方式适合页面较少的场景。

FragmentStatePagerAdapter (完全销毁，状态保存)

> 完全销毁 Fragment 实例，但在销毁前会保存其状态。这种方式适合页面较多或动态生成的场景。

目前官方已经废弃了它们，转而推荐使用 **ViewPager2**。ViewPager2 统一使用 **FragmentStateAdapter**。

## Fragment 懒加载

实现懒加载的核心目标是：**只有当 Fragment 对用户可见，且视图已创建，且是第一次加载时，才触发数据请求**。

**add + show/hide 模式**，每个 Fragment 第一次加载会走 `onResume`，后续切换会走 `onHiddenChanged`，在 `onHiddenChanged` 和 `onResume` 中分别使用`isFirstLoad` 标记位，防止重复加载。

> show/hide 方法控制 Fragment 时，Fragment 生命周期将不再执行，通过 onHiddenChanged 回调监听切换事件。

show/hide 模式懒加载公式：`onResume` (配合 `!isHidden`) + `onHiddenChanged` (配合 `!hidden`) + `isFirstLoad` 标记位。

**Fragment + ViewPager2 模式**，ViewPager2 默认使用了 `setMaxLifecycle` 机制，只有当前可见的 Fragment 才会进入 `RESUMED` 状态，不可见的 Fragment 会被限制在 `STARTED` 状态。

> 所以直接在 `onResume` 中判断 `isFirstLoad` 即可实现懒加载。

两种模式 isFirstLoad 使用后需要置为 false。

## ViewPager2 与 ViewPager 区别

ViewPager2 是对 ViewPager 的彻底重构，最本质的变化是它**基于 RecyclerView 实现**。

带来了三个核心优势：
第一，**性能更强**，它复用了 RecyclerView 的 ViewHolder 机制和 `DiffUtil` 局部刷新；  
第二，**功能更全**，原生支持垂直滑动和 RTL 布局；  
第三，**懒加载更简单**，它通过 `setMaxLifecycle` 确保只有当前可见 Fragment 才会触发 `onResume`。

## ViewPager 如何实现无限循环

方案 A：伪无限循环(设置最大值)

- **做法**：`getCount()` 返回 `Integer.MAX_VALUE`，初始位置设在中间 `MAX_VALUE / 2`。
- **优点**：实现极其简单。
- **缺点**：虽然理论上滑不到头，但并不是真正的循环，初始位置计算稍显暴力。

方案 B：首尾添加辅助页(真正的循环)

- **做法**：
    1. 假设有 3 张图 (1, 2, 3)，在首尾各加一张，变为：**(3), 1, 2, 3, (1)**。
    2. 当滑动到最后的 (1) 时，静默跳转（`setCurrentItem(1, false)`）到真正的 1。
    3. 当滑动到最前的 (3) 时，静默跳转到真正的 3。
- **优点**：真正的无限循环，用户感知不到跳转。
- **缺点**：逻辑稍复杂，需要处理好 `onPageScrollStateChanged` 的跳转时机。

## **ViewPager2 怎么禁止用户滑动？**

设置 setUserInputEnabled(false)。

## 动画有哪几种类型，区别是什么

帧动画

*   通过顺序播放一系列图片来实现动画效果，使用 `AnimationDrawable` 实现，如果图片过多、过大，容易导致 `OOM`，复杂的帧动画通常推荐用 **Lottie** 或 **SVGA** 替代。

补间动画

*   它通过不断调用 `View.draw()` 并应用 **Transformation(变换矩阵)** 来实现。它改变的是 View 的**绘制位置**，而不是 View 的**真实位置**。包括位移、缩放、旋转、透明四种效果。

属性动画

*   通过反射不断调用对象的 `setXXX()` 方法来修改属性值，核心类是 `ValueAnimator` 和 `ObjectAnimator`。

## 补间动画和属性动画的区别

- 视觉和真实的区别(核心)。补间动画只是改变显示效果，不会改变 View 的属性，而属性动画会改变对象的属性。

- 作用对象不同。补间动画只能作用在 View 上，属性动画可以作用在任意对象上。

- 扩展性不同。补间动画只能实现位移、缩放、旋转和透明度四种动画操作，而属性动画支持自定义 **Interpolator(插值器)** 和 **TypeEvaluator(估值器)**。只要对象有 `set` 和 `get` 方法，就能做动画。

在现代开发中，除非是极其简单的 View 变换，否则优先使用属性动画。而对于复杂的业务动画，考虑使用 **Lottie** 或 **SVGA** 实现。

## **属性动画会导致内存泄漏吗？**

**会**。如果设置了无限循环动画(`INFINITE`)，在 Activity 销毁时没有调用 `animator.cancel()`，由于 Animator 持有 View 的引用，而 View 持有 Activity 的引用，会导致 Activity 无法被回收。

## ObjectAnimator 和 ValueAnimator 两种属性动画的区别

**ValueAnimator** 是属性动画的核心，它只负责根据时间曲线计算数值。它不与任何对象绑定，开发者需要**通过 `onAnimationUpdate` 监听器手动将数值应用到目标上**。它的优点是**高度灵活**，适合处理**复杂的逻辑或非属性的数值变化**。

**ObjectAnimator** 则是对 `ValueAnimator` 的封装。它通过**反射机制**，根据传入的属性名自动调用对象的 `set` 方法来更新状态。它的优点是**代码简洁**，是开发中最常用的动画实现方式。

如果只是简单的 View 变换（如透明度、位移），优先使用 `ObjectAnimator`；如果需要在一个动画循环里同时操作多个不相关的对象，或者对象没有提供 `set` 方法，选择 `ValueAnimator`。

## Activity/Fragment 跳转动画的实现方式

### Activity 跳转动画

在 `startActivity()` 或 `finish()` 之后立即调用  `overridePendingTransition`

```java
// 在 A 中跳转到 B
Intent intent = new Intent(A.this, B.class);
startActivity(intent);
overridePendingTransition(R.anim.enter_alpha, R.anim.exit_alpha);
```
- **参数含义：**
    - `enterAnim`: 进入页面的动画（B 页面如何进来）。
    - `exitAnim`: 退出页面的动画（A 页面如何离开）。
- **注意：** 在 Android 14 (API 34) 中，此方法已被废弃，建议使用 `overrideActivityTransition`。
###  Fragment 跳转动画

 在切换 Fragment 的事务中设置 `FragmentTransaction.setCustomAnimations`
 
```java
parentFragmentManager.beginTransaction()
    .setCustomAnimations(
        R.anim.slide_in,  // B 进入
        R.anim.fade_out,  // A 退出
        R.anim.fade_in,   // 返回时：A 进入
        R.anim.slide_out  // 返回时：B 退出
    )
    .replace(R.id.container, fragmentB)
    .addToBackStack(null)
    .commit()
```

## TimeInterpolator 插值器

用于控制动画的执行速率。解决的是 **“快慢”** 问题。它将“时间流逝的百分比”转变为“动画进度的百分比”

Android 系统提供了一些内置的插值器，LinearInterpolator（匀速）、AccelerateDecelerateInterpolator（先加速后减速）等

可以通过实现 TimeInterpolator 接口来自定义插值器

```java
public class MyInterpolator implements TimeInterpolator {
    @Override
    public float getInterpolation(float input) {
        // 入参 input 是匀速流逝的时间，返回值为 fraction，提供给估值器计算使用。
        return input; 
    }
}
```

## TypeEvaluator 估值器

解决的是 **“具体数值”** 问题。它将“动画进度的百分比”转变为“具体的属性值”。

Android 内置了几种实现：
- IntEvaluator: 用于计算整数值的变化。
- FloatEvaluator: 用于计算浮点数值的变化。
- ArgbEvaluator: 专门用于颜色值（ARGB）的插值计算，能够平滑地在两种颜色间过渡。

**自定义 TypeEvaluator**

1.创建类并实现 `TypeEvaluator<T>` 接口：T 是你想要进行插值计算的数据类型。
2.重写 evaluate 方法。
3.应用自定义 TypeEvaluator。
>在 `evaluate` 方法中，最基本的线性计算逻辑是：  
`result = startValue + fraction * (endValue - startValue)`

## 图片宽高和图片分辨率的关系是什么

在计算机图形学中，我们需要区分三个概念：

1. **像素尺寸 (Pixel Dimensions)：** 图片的宽度和高度（例如 800px × 600px）。它决定了图片的**数据量**。
2. **物理尺寸 (Physical Size)：** 图片在屏幕或纸张上显示的实际大小（例如 5英寸 × 3英寸）。
3. **像素密度 (PPI/DPI)：** 每英寸包含的像素数量。**它是连接像素尺寸和物理尺寸的桥梁**。

它们之间有一个核心公式：

> **像素尺寸 = 物理尺寸 × 像素密度 (PPI)**

**像素尺寸是绝对的，而物理大小是相对的**。同一张图片，在像素密度越高的屏幕上，显示的物理面积就越小，但画面越细腻

## 图片的大小与加载图片时所占用的内存有关系吗

图片在内存中占用的空间与它在磁盘上的文件大小(压缩包大小)**没有直接关系**，而是由图片**分辨率**和**像素格式**决定的，图片在磁盘上通过 JPEG 或 PNG 算法进行了压缩，但显示时必须解压成位图。 在实际开发中（如 Android/iOS），如果显示区域比原图小，我们会进行“采样缩放”（Downsampling），只加载缩放后的像素到内存中，从而节省空间。

内存占用的计算公式 = **`图片实际加载的宽 × 图片实际加载的高 × 每像素字节数`**。

**内存占用 = (原始宽 × 缩放比例) × (原始高 × 缩放比例) × 每像素字节数**。

缩放比例 = 设备密度 / 资源目录密度。网络图片**没有资源目录，系统不缩放(1:1 加载)**。

## 质量压缩图片后，其加载到内存中的大小是不变的吗

质量压缩只是改变了图片在**磁盘上**的压缩率（即文件大小，KB）。它的原理是丢弃掉图像中的一些视觉细节来换取更高的压缩比，从而减小 KB 数。但是，一旦这张图片被解码成 Bitmap 加载到内存中，系统依然会按照图片的**原始宽高**和**指定的像素格式**（如 ARGB_8888）来分配内存空间。

## JPEG、PNG、WEBP 三种图片格式有什么区别

这三种格式的选择本质上是在**压缩率、透明度、解码性能**之间做权衡。

1. **JPEG** 是有损压缩，不支持透明，但兼容性最好，解码最快，适合复杂的摄影图。
2. **PNG** 是无损压缩，支持透明，边缘清晰，适合图标和文字，但体积最大。
3. **WebP** 支持有损/无损和透明，体积比 JPEG 还要小 30% 左右。

在 Android 工程中基本推行 **WebP 全量化**。但 WebP 的解码算法更复杂，对 CPU 的消耗比 JPEG 高。

最新的 **AVIF** 压缩率比 WebP 还要高 20% 以上，是未来的趋势，目前 Android 12 及以上版本已经原生支持了。

## Android ImageView 的 layout_width 和 layout_height 设为 200dp/200dp，就表示它的分辨率是 200X200 吗

不，ImageView 的 200dpX200dp 只是定义了一个"容器"的大小，它并不会自动改变图片的分辨率。

1.  200dp 在高清屏上(如 3x)会被转换为 600px。但如果你直接加载一张 2000px 的大图，Android 默认依然会在内存中创建一个完整的 2000px 的 Bitmap，这会导致严重的内存浪费甚至 OOM。
2. Glide 会自动获取 ImageView 的 `layout_width/height`（即那 200dp 转换后的像素值）。它在解码图片前，会计算出一个**采样率（inSampleSize）**，只从原图中读取必要的像素点。即使原图是 2000px，Glide 最终加载进内存的 Bitmap 可能只有 600px 左右。

## 什么是采样率，为什么重新采样可以减少图片内存占用

重新采样本质上是**改变图片的像素总数**。

在 Android 开发中，它主要通过 `inSampleSize` 实现。它改变的是图片的**像素分辨率**和**内存占用**。

它的核心意义在于：**让 Bitmap 的分辨率去适配 View 的物理像素大小**。通过在解码阶段丢弃多余的像素信息，可以成倍地降低内存开销。像 Glide 这样的库，通过自动计算 View 尺寸并进行精准的重新采样，才保证了我们在加载大量高清图时依然能保持丝滑且不崩溃。

Glide 结合了 **倍数缩放(1)**、**精准缩放(2)** 两种方式:

1. 使用 `inSampleSize` 将大图解码成一个**接近**目标尺寸但略大于它的 Bitmap。这一步保证了内存不会溢出。
2. 使用 `createScaledBitmap` 将这个稍微大一点的 Bitmap 裁剪或拉伸到 ImageView 的**精确像素尺寸**。

## Glide into 传递几种 Target 的区别

在 Glide 4 中，这三者的区别主要在于**应用场景**和**资源安全性**：

1. **`SimpleTarget`** 已经被废弃了，因为它容易引发内存泄漏和资源复用问题。
2. **`CustomTarget`** 适用于**非 View 展示场景**（比如后台下载图片处理）。使用它时**必须手动指定尺寸**，**并且一定要在 `onLoadCleared` 中释放对 Bitmap 的引用**，否则会导致 Bitmap 复用冲突显示错乱或程序崩溃。
3. **`CustomViewTarget`** 是为**自定义 View** 量身定制的。它的优势在于能**自动测量 View 的大小进行采样**，并且能监听 View 的 Attach/Detach 状态。当 View 被销毁或移除时，它会自动取消加载，是处理自定义 View 图片加载最安全的方式。

简单来说，**要拿数据用 `CustomTarget`，要给自定义 View 显示用 `CustomViewTarget`**。

## 像素格式 ARGB_8888、RGB565 有什么区别

ARGB_8888 和 RGB565 的本质区别在于**每个像素占用的内存大小**。

ARGB_8888 每个像素占 **4 字节**，支持透明度，色彩最细腻，是目前的系统默认格式。

RGB565 每个像素只占 **2 字节**，内存直接减半。它不支持透明度，且由于每种颜色的色阶较少，在显示渐变色时可能会有‘色彩断层’。之所以叫 565，是因为人眼对绿色更敏感，所以给绿色分配了 6 位。

在优化内存时，如果一张图不需要透明度(比如用户头像、照片预览)，可考虑将其解码格式设为 RGB565，这样能瞬间释放一半的 Bitmap 内存压力。

## Glide into 方法直接传递 View 和传递 Target 有什么区别，使用场景是什么

`into` 全自动模式
- 自动获取 View 宽高，重新采样。
- 自动监听 View 生命周期，当 View 被销毁时，加载任务自动取消。
- 自动设置资源， 加载成功后，它会自动设置资源到图片上。
- 自动处理回收，自动处理 `onLoadCleared`，确保旧的 Bitmap 安全回到复用池。

`Target` 手动定制模式。

**如何选择 into 传递什么对象**

 **场景 A：标准 UI 显示**
- **需求：** 图片直接显示在 `ImageView` 中。
- **选择：** **`into(ImageView)`**。
- **理由：** 最简单、最安全、内存管理最自动化。

**场景 B：自定义 UI 绘制**
- **需求：** 图片显示在自定义 View 中(比如通过 `onDraw` 绘制），或者需要监听 View 的生命周期。
- **选择：** **`into(CustomViewTarget)`**。
- **理由：** 它能自动测量 View 尺寸，且能随 View 的 Attach/Detach 自动开始/停止加载。

**场景 C：纯数据处理 / 非 View 场景**
- **需求：** 只需要拿到 Bitmap 对象去做处理（如保存文件、滤镜加工、显示在通知栏）。
- **选择：** **`into(CustomTarget)`**。
- **理由：** 它最轻量，不强制绑定 View。但记得手动传尺寸和处理 `onLoadCleared`。

## Glide 图片缓存策略是什么

当 Glide 加载一个 URL 时，它会按以下顺序找图：

1. **Active Resources (活动资源)：** 检查当前是否有页面正在显示这张图。如果正在显示，直接从这个弱引用集合中获取。这保证了同一张图在多个地方显示时，内存中只有一份。
2. **Memory Cache (内存缓存/LRU)：** 如果活动资源里没有，去 LRU 缓存里找。这里存放的是最近使用过但当前没在显示的图片。
3. **Disk Cache - Resource (资源磁盘缓存)：** 如果内存没有，去磁盘找。这里存的是**已经根据 ImageView 大小缩放、转换过**的图片。直接加载它最快，不需要重新计算。
4. **Disk Cache - Data (原始磁盘缓存)：** 如果上面没有，找**原始下载的图片文件**。拿到后需要重新进行缩放和解码。
5. **Network (网络下载)：** 以上全无，才去网络下载。

## Bitmap 的压缩方式

### 磁盘大小压缩

**质量压缩**

```java
  private Bitmap compressWithQuality(Bitmap bitmap, int quality) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      // 把压缩后的数据存放到 ByteArrayOutputStream 中
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
      byte[] bytes = byteArrayOutputStream.toByteArray();
      Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      return result;
  }
```

通过降低图片位深和透明度(丢弃部分像素信息)来减小**文件大小**，常用在上传限制大小的场景，**PNG 是无损压缩**，设置 `quality` 无效。

**格式转换**

将 PNG 转换为 WebP。

### 内存占用压缩

**重新采样**

在图片**加载进内存之前**，通过设置 `BitmapFactory.Options.inSampleSize` 来减少像素点。

如果 `inSampleSize = 2`，则宽变成 1/2，高变成 1/2，总像素变成 1/4，内存占用也变成 1/4。

流程:
1. `inJustDecodeBounds = true` 获取原始宽高。
2. 计算 `inSampleSize`。
3. `inJustDecodeBounds = false` 真正加载。

**像素格式压缩 (RGB_565)**

减少图片单位像素所占的字节数，减少内存大小

**缩放压缩 createScaledBitmap**

图片**已经加载到内存后**，通过矩阵变换生成一张新的、尺寸更小的图片，因为是"后处理"，如果原图极大，在调用缩放方法之前可能就已经 OOM 了。

```java
private Bitmap compressWithCreatedScaledBitmap(Bitmap bitmap, int dstWidth, int dstHeight) {
 Bitmap result = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight,  true); 
 return result;
}
```

## LruCache & DiskLruCache

LruCache 是内存缓存，基于 LRU 算法，利用了 `LinkedHashMap` 的**访问顺序(accessOrder)特性**。当缓存达到预设的内存上限时，会自动移除链表头部的最近最少使用的对象。如果是缓存 Bitmap，必须重写 `sizeOf()`方法返回 `bitmap.getByteCount()`，表示每张图占用的内存大小，否则缓存大小计算会出错，导致 OOM。

DiskLruCache 是磁盘缓存，不直接属于 Android SDK，但是 Google 官方推荐的磁盘缓存方案。

## 图片文件夹加载优先级

Android 系统定义的屏幕像素密度基准值是 160dpi，该基准值下 1dp 就等于 1px，依此类推 320dpi 下 1dp 就等于 2px

记载图片时根据设备的分辨率先去对应 DPI 的文件夹寻找，如果该文件夹内没找到，优先从**高密度**文件夹寻找

![image.png](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/%E5%88%86%E8%BE%A8%E7%8E%87dpi.png?raw=true) 

例如对于 320dpi 的设备来说，应用在选择图片时就会优先从 `drawable-xhdpi` 文件夹拿，如果该文件夹内没找到图片，就会依照 `xxhdpi -> xxxhdpi -> hdpi -> mdpi -> ldpi` 的顺序进行查找，优先使用高密度版本，然后从中选择最接近当前屏幕密度的图片资源

## MVC & MVP & MVVM 介绍下概念

MVC(Model-View-Controller)-**传统模式**

- **Model**：数据模型与业务逻辑。
- **View**：XML 布局文件。
- **Controller**：Activity/Fragment。

Activity 既负责 View 的显示，又负责 Controller 的逻辑。这导致 **Activity 代码极其臃肿，V 和 C 耦合严重**。

MVP(Model-View-Presenter)-**接口驱动模式**

- **View**：Activity/Fragment 只负责 UI 显示。
- **Presenter**：中间人，通过**接口**分别与 View 和 Model 通信。
- **Model**：提供数据。

通过 Presenter + DataManager 组合将 UI 展示和业务逻辑分离，P 层持有 View 和 DataManager 层的对象，DataManager 层持有 Model 层的接口对象。缺点是每个页面都要定义一堆 IView、IPresenter 接口。

MVVM(Model-View-ViewModel)-**数据驱动模式**

- **Model**：数据源(Repository)。
- **View**：Activity/Fragment，通过 **观察者模式** 订阅数据。
- **ViewModel**：持有 UI 状态和业务逻辑，**不持有 View 的引用**。

使用 ViewModel 代替 Presenter，管理 UI 状态和业务逻辑，View 通过 LiveData 等方式订阅 ViewModel 的数据源，当数据发生变化时，ViewModel 通知 View 刷新视图，不持有 View 引用，低耦合且自动管理生命周期。

## APK 打包流程

1. **AAPT2** 负责编译资源并生成 `R.java` 和资源索引表。
2. Java 或 Kotlin 源码被编译成 `.class` 字节码。
3. 使用 **R8** 工具将 `.class` 转为 **`.dex`** 的同时，完成代码压缩、混淆和优化。
4. 将所有 dex、资源和 so 库打包成初始 APK。
5. 先通过 **zipalign** 进行字节对齐以优化内存性能，再使用 **apksigner** 进行签名。

## D8/R8 区别是什么，有关联吗

D8 和 R8 是 Android 构建流程中的两个核心工具，协同工作以优化 APK 的构建速度和输出质量。

- **D8**：Dex 编译器。将 Java 字节码（.class）转换为 Dalvik 可执行格式（.dex）。它替代了旧的 dx 工具，具有更快的编译速度和更小的 Dex 体积，并支持 Java 8 语言特性。
- **R8**：代码资源缩减、混淆和优化工具。**它将 D8 的 Dex 转换功能与代码优化/混淆整合在了一起**，实现更小的 APK 体积、更快的运行速度、更强的代码安全性，替代传统的 `ProGuard`。

## Android 签名机制的作用，V2 相比于 V1 签名机制的变化

Android 的签名机制是用来确保应用程序的完整性和可靠性的。

- V2 签名是对**整个 APK 文件的二进制内容**进行校验，任何改动都会导致签名失效。V1 **校验范围不完整**，只保护文件内容，不保护 ZIP 容器的元数据。
- V2 签名直接校验**整个文件的哈希值，无需解压**，安装速度显著提升。V1 需要解压 APK 逐个校验文件，安装速度慢。

## Android 系统启动流程

1. Linux 内核启动并开启 **init 进程**。
2. init 启动 **Zygote**，Zygote **预加载**系统资源，并通过 **Socket** 机制等待孵化请求。
3. Zygote 孵化 SystemServer，SystemServer 初始化多个系统服务。
4. 当 AMS 检测到系统就绪后，会启动 **Launcher** 进程，展示桌面。

## Zygote 的 IPC 通信为什么使用 socket 而不用 binder

 Binder 通信需要线程池支持， fork 操作在多线程环境可能会出现死锁，Socket 单线程通信在孵化时更简单可靠。

## 孵化应用进程为什么不交给 SystemServer 来做，而专门设计一个 Zygote

- **性能优化**。Zygote 在启动时就预加载了大量的系统资源，应用进程可以通过 fork 从 Zygote 中快速创建，避免重复加载资源，提高了性能。
- **规避多线程死锁风险**。`SystemServer` 是一个拥有几十个线程的复杂进程，在多线程环境下执行 `fork` 极易导致子进程因无法获取父进程中未释放的锁而产生死锁。

## 什么是序列化，为什么需要序列化和反序列化

**概念**

序列化是将**内存对象**转化为**可传输或可存储格式**的过程。

## Serializable 和 Parcelable 的区别

**Serializable** 是 Java 序列化方式，基于反射实现，虽然实现简单，但性能开销大，产生大量临时对象会导致频繁的 GC。优点是稳定性高，适合持久化存储。

**Parcelable** 来自 Android SDK，在内存中读写，性能优于 Serializable，适合 Activity 间传参或跨进程通信。但由于与 Android 版本耦合不适合持久化存储。

## 什么是 serialVersionUID，为什么要显示指定 serialVersionUID 的值

serialVersionUID 用来判断序列化对象是否一致。

当没有指定 serialVersionUID 的值的时候，JVM 会根据类结构自动生成 serialVersionUID，**增加或删除了一个字段**，自动生成的 ID 就会改变。反序列化旧数据时，系统发现 ID 不一致，会直接抛出 **`InvalidClassException`**。

## Art & Dalvik 及其区别

Art 和 Dalvik 是两种不同的运行时环境

4.4.4 以前，Android 使用 Dalvik 虚拟机，应用的每次运行都需要即时编译，安装速度快。缺点是 CPU 使用频率高，应用响应速度慢

5.0 开始，Android 使用 ART 虚拟机，应用在首次安装时用就将字节码转换为机器码，避免了后续重复编译，降低了 CPU 使用频率，应用响应速度快，已成为主流。缺点是安装时间更长、存储空间占用更大

## 热修复原理

Android 类加载器 `PathClassLoader` 继承自 `BaseDexClassLoader`，其内部维护了一个 `DexPathList` 对象。`DexPathList` 中包含一个 `Element[] dexElements` 数组，每个 `Element` 对应一个 Dex 文件。需要加载一个类时，`PathClassLoader` 会遍历 `dexElements` 数组，一旦在某个 Dex 中找到类，立即返回，**停止后续遍历**。

**修复过程**

- 将修复好的类打包成一个单独的 Dex 文件（补丁 Dex）。
    
- 通过反射获取当前 `PathClassLoader` 的 `dexElements` 数组。
    
- 将补丁 Dex 对应的 `Element` 插入到数组的**最前面**。
    
- 这样，当系统再次加载有问题的类时，会优先在补丁 Dex 中找到修复后的版本，从而屏蔽旧的错误类，实现“修复”。

## 什么是模块化和组件化

**模块化**
 
  一种基础的拆分方式，主要关注**分离关注点**和**代码复用**。例如，按功能将网络请求、图片加载、工具类等抽成独立模块（`network`, `imageloader`, `common`）。模块可以是**基础库**或**业务无关的通用库**。

**组件化**

一种更高级、更彻底的架构模式，通常特指**业务维度**的拆分。每个业务组件（如 `home`, `user`, `video`）都是一个可以**独立开发、编译、测试、运行**的完整“小应用”（即 `com.android.application`）。通过**路由框架**进行通信，最终由 **"主 App 壳工程"** 组装成一个完整的应用。它解决了**业务耦合**和**团队并行开发**的问题。

## 项目仓库 **Mono-Repo(单仓)** 和 **Multi-Repo(多仓)** 的应用场景，怎么选择

- **Mono-Repo**
   所有项目代码(多个模块)都存放在一个统一的代码仓库中，通过构建工具(如 Gradle）的 `:module-name` 进行源码依赖，一次提交可以跨多个模块进行修改。
 - **Multi-Repo**
   每个独立的模块都拥有自己单独的代码仓库，通过远程二进制依赖各仓库组件，每个模块有自己独立的版本和发布节奏。
   
建议采用“**主干 Mono-Repo + 外部依赖**”的模式。
 - 将 **稳定、变更不频繁、被多个项目使用** 的底层库放到独立的 Multi-Repo 中，以二进制形式依赖。    
- 将 **当前 App 强相关、高频变更** 的业务组件和模块放在一个主 Mono-Repo 中，使用源码依赖。

## 组件化项目，不同组件间如何通信和传递数据

**页面跳转**主要依赖 **ARouter**，它通过路径名替代了显式的类名引用，实现了 UI 层的解耦。  

**功能调用**则采用**接口下沉**方案：在公共模块定义接口，在业务模块实现，并通过 ARouter 的 `IProvider` 机制动态获取实例。

也可使用依赖注入框架(如 Hilt)。

**数据同步**则利用具备生命周期感知的 **LiveData 或 Flow**。
   
## ARouter 等路由通信的原理是什么 

1. 在**编译期**利用 APT 生成路由映射表。
2. 在**运行期**通过**分组加载机制**将映射关系存入内存。
3. 发起跳转时，它会**根据路径匹配到目标 Class**，并支持通过**拦截器**实现全局的权限控制。
> 为了防止一次性加载几千个路由导致启动卡顿，ARouter 采用了**分组逻辑**。只有当你跳转到某个组，才会加载该组下的所有路由信息。

## Jetpack 架构组件(AAC)

Jetpack AAC 组件是实现 MVVM 架构的官方标准方案。

     MyViewModel model = ViewModelProvider.of(this).get(MyViewModel.class);

**LiveData**

**作用**：感知生命周期的可观察数据容器。

**特点**

- 只有当观察者的状态是 `STARTED` 或 `RESUMED` 时，它才会发送数据。
- 生命周期进入 `DESTROYED` 时，它会自动移除观察者，**彻底杜绝内存泄漏**。

**ViewModel**

**作用**：存储 UI 相关数据，且不受配置更改影响。

**特点**

- **不能持有 View 或 Activity 的引用**。如果需要 Context，应继承 `AndroidViewModel` 使用 `getApplication()`。

**LifeCycle**

**作用**：将生命周期状态抽象为 **State**(状态)和 **Event**(事件)，通过观察者模式让普通类也能感知 Activity 的状态。

**特点**

- **LifecycleOwner**：被观察者（Activity/Fragment）。
- **LifecycleObserver**：观察者。

**使用示例**
### 1. ViewModel + LiveData (数据层)

`CounterViewModel` 负责持有数据和业务逻辑。

```kotlin
class CounterViewModel : ViewModel() { 
// 1. 定义私有的 MutableLiveData，防止外部随意修改 
private val _count = MutableLiveData<Int>() 
// 2. 暴露只读的 LiveData 给外部观察 
val count: LiveData<Int> get() = _count 

init { 
   _count.value = 0 // 初始化 
} 

fun increment() { 
// 3. 修改数据（如果在子线程用 postValue）
  _count.value = (_count.value ?: 0) + 1 
 } 
}
```

### 2. Lifecycle (生命周期监听层)

```kotlin
// 实现 DefaultLifecycleObserver 接口
class MyTimerObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        // 当 Activity 启动时，开始计时逻辑
        Log.d("Lifecycle", "计时器开始...")
    }

    override fun onStop(owner: LifecycleOwner) {
        // 当 Activity 停止时，自动暂停计时，防止内存泄漏
        Log.d("Lifecycle", "计时器暂停...")
    }
}
```

### Activity 串联

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Lifecycle 串联 ---
        // 将观察者绑定到当前 Activity 的生命周期
        lifecycle.addObserver(MyTimerObserver())

        // --- ViewModel 串联 ---
        // 获取 ViewModel 实例（屏幕旋转时，此实例不会销毁）
        val viewModel = ViewModelProvider(this).get(CounterViewModel::class.java)

        // --- LiveData 串联 ---
        // 观察数据变化。'this' 作为 LifecycleOwner 传入
        // 只有在 Activity 活跃时（Started/Resumed）才会收到回调
        viewModel.count.observe(this) { newCount ->
            // 自动更新 UI，无需手动判空或检查 Activity 是否还在
            findViewById<TextView>(R.id.tv_count).text = "当前计数: $newCount"
        }

        // 按钮点击触发逻辑
        findViewById<Button>(R.id.btn_add).setOnClickListener {
            viewModel.increment()
        }
    }
}
```

## Android 的 16KB so 升级是什么意思，如何适配

**1. 什么是“16KB so 升级”？**

在 Linux 内核（Android 的基础）中，内存是以“页”为单位进行管理的。过去 15 年，Android 设备一直固定使用 **4KB** 的页面大小。

- **升级含义**：所谓的“16KB so 升级”，是指开发者必须重新编译应用中的 Native 动态链接库（`.so` 文件），使其在内存中按照 **16KB（16384 字节）** 的边界进行对齐。
- **核心目的**：
    1. **性能飞跃**：根据 Google 实测，16KB 页面可使应用启动速度平均提升 **3.16% - 30%**，系统整体响应效率提升 **5% - 10%** 14。
    2. **降低开销**：减少了 CPU 管理内存页表（TLB）的频率，显著降低了高负载下的功耗 114。
- **强制时间线**：自 **2025 年 11 月 1 日**起，所有提交至 Google Play 且针对 Android 15+ 的新应用和更新，**必须**支持 16KB 页面大小，否则将无法上架或在现代设备上崩溃
---
**2.哪些应用需要适配？**

1. **纯 Java/Kotlin 应用**：如果你的项目不包含任何 `.so` 文件，且不依赖包含 Native 代码的第三方 SDK，则**无需手动适配**，系统会自动处理。
2. **含 Native 代码的应用**：只要你的项目中 `lib/` 目录下存在 `.so` 文件（无论是自己写的 C/C++ 代码，还是集成的第三方 SDK 如 FFmpeg、OpenSSL、地图 SDK 等），都**必须适配** 16。
---
**3.如何进行 16KB 适配？**

适配工作主要分为“编译对齐”和“代码逻辑修正”两个部分：

1.升级构建环境与工具链
   1. **Android Gradle 插件 (AGP)**：建议升级至 **8.5.1 或更高版本**。
   2. **NDK 版本**：建议升级至 **r27 或更高版本**。新版 NDK 默认支持 16KB 对齐，能大幅简化配置工作。

2.修改编译配置（针对自己的代码）

你需要告诉链接器（Linker）使用 16KB 的最大页面大小。

- **CMake 项目**：在 `CMakeLists.txt` 中添加以下标志：
```java
# 强制 16KB (16384 字节) 对齐
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
```
- **ndk-build 项目**：在 `Android.mk` 中添加：
```java
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
```

3.修正 Native 代码中的硬编码假设

检查 C/C++ 源码中是否存在对页面大小为 `4096` 的硬编码。

- **错误做法**：直接使用 `4096` 或 `PAGE_SIZE` 宏进行内存偏移计算。
- **正确做法**：使用系统函数动态获取页面大小。
```c++
// 推荐方式
long pageSize = sysconf(_SC_PAGESIZE); 
// 或者
int pageSize = getpagesize();
```
 
 4.更新第三方 SDK

这是最难控制的一环。你需要检查项目中所有的第三方 `.so` 库。如果它们未进行 16KB 对齐，应用在 16KB 模式下会报 `dlopen failed` 错误。

- **操作**：联系 SDK 供应商获取已适配 16KB 的新版本，或者寻找开源库的最新源码重新编译。
## 从 android.support  迁移到 AndroidX 如何适配，AndroidX 是什么

### 什么是 AndroidX？

**AndroidX**（Android Extension Library）是 Google 推出的一套用于替代旧版 `android.support`（Support 库）的全新开源架构库，它是 Android Jetpack 的核心组成部分。

要理解 AndroidX，我们需要先了解它的“前身”——**Support 库**：
*   **历史包袱**：早期 Android 系统版本碎片化严重，为了让新功能（如 Material Design、Fragment 等）能在老版本手机上运行，Google 推出了 `android.support.v4`、`v7` 等兼容库。但随着时间推移，Support 库的命名变得极其混乱（例如 v7 库实际上早就要求最低 API 14 了），且所有库的依赖版本必须严格一致，导致开发者经常遭遇版本冲突。
*   **AndroidX 的诞生**：为了彻底解决这些问题，Google 对 Support 库进行了全面重构和整理，推出了 **AndroidX**。
*   **核心优势**：
    1.  **包名统一**：所有类库的包名统一从 `android.support.*` 变更为 `androidx.*`，结构更加清晰。
    2.  **独立版本控制**：AndroidX 中的每个库（如 `androidx.fragment`、`androidx.recyclerview`）都可以独立更新和维护，不再强制要求所有库版本号一致。
    3.  **官方唯一主推**：**Support 库在版本 28.0.0（对应 Android 9.0）之后已经彻底停止更新**，所有的新功能、新组件（如 Compose、Room、ViewModel 等）全部只在 AndroidX 中提供。

### 如何从 Support 库迁移到 AndroidX？

如果你的老项目仍在使用 `android.support`，迁移过程虽然涉及面广，但 Google 提供了非常完善的自动化工具。请按照以下 4 个核心步骤进行适配：

1.升级编译版本 (前提准备)
在迁移之前，必须确保你的项目编译目标版本足够高。打开项目底层的 `build.gradle` 文件，将 `compileSdkVersion`（或现代 AGP 中的 `compileSdk`）修改为 **28 或更高版本**（建议直接升级到当前最新的稳定版本，如 34 或 35），因为 AndroidX 的底层二进制文件是与 Support 28.0.0 对齐的。

2.修改 gradle.properties 开启 AndroidX
在项目的根目录下找到 `gradle.properties` 文件，添加以下两行极其关键的配置：
```properties
# 强制要求项目使用 AndroidX 相关的包，而非 Support 包
android.useAndroidX=true

# 开启 Jetifier 工具。它的作用是：如果你引入的第三方旧 SDK 还在使用 Support 库，
# 编译时它会自动将这些旧 SDK 里的 Support 引用动态替换为 AndroidX 的引用，但拖慢编译速度。
android.enableJetifier=true
```

3.使用 Android Studio 一键迁移
Android Studio 提供了官方的自动化重构工具，可以帮你完成 90% 以上的迁移工作：
1. 在 Android Studio 的顶部菜单栏中，依次点击 **`Refactor`** -> **`Migrate to AndroidX...`**。
2. 此时 IDE 会弹出一个提示框，强烈建议你勾选“备份项目为 Zip 文件”（以防迁移失败代码混乱）。
3. 点击 `Migrate` 后，IDE 会在底部弹出一个 `Refactoring Preview`（重构预览）窗口。
4. 点击 **`Do Refactor`**。IDE 会自动扫描全局代码，将所有的 `android.support.*` 导包替换为 `androidx.*`，并将 `build.gradle` 中的旧依赖替换为对应的 AndroidX 依赖。

## 框架原理

### OkHttp 源码流程

1.**创建 OkHttpClient 实例**

首先需要创建一个 OkHttpClient 实例。在构建过程中，可以通过 Builder 模式设置各种参数，如超时时间、缓存策略、拦截器等

2.**构建 Request**

创建一个 Request 对象，它包含了请求的 URL、HTTP 方法（GET、POST 等）、请求头以及请求体等信息

3.**创建 Call 实例**

**使用 OkHttpClient 的 newCall 方法，将 Request 对象转换成一个 Call 对象**。Call 是一个抽象概念，代表一个待执行的 HTTP 请求。

4.**执行请求**

**Dispatcher** 调度器介入。

- **同步请求**: 通过调用 Call 对象的 execute 方法来执行请求，此方法会阻塞直到响应返回或者抛出异常

-  **异步请求**: 通过调用 Call 对象的 enqueue 方法来发起异步请求，当请求完成或失败时，Callback 的相应方法会被调用

5.**拦截器责任链**

OkHttp 使用责任链模式来组织拦截器，使得每个拦截器都能有机会处理请求或响应。

> 举例：我要请假先由主管审核，小于 3 天主管处理，主管设置了下一个处理人，大于 3天主管处理不了则传递给经理审核，依次类推。

### App Interceptor 和 Network Interceptor 区别

- **App Interceptor（应用拦截器）**：位于责任链最顶端。只会被调用一次，不关心重试或重定向。
- **Network Interceptor（网络拦截器）**：位于 `Connect` 和 `CallServer` 之间。如果发生重定向，它会被调用多次；它能看到最真实的、即将发往服务器的网络字节流。

### LeakCanary 源码流程

1. LeakCanary 监听 Activity 的 onDestroy 生命周期，把该对象创建一个弱引用，并关联到引用队列。
2. 在 5 秒延时和手动触发 GC 后，如果该对象仍未出现在引用队列中，系统就认为它发生了泄漏。
3. 当泄漏对象达到阈值，LeakCanary 会触发一次堆转储，计算出从 GC Roots 到泄漏对象的最短引用路径，自动生成一份详细的泄露报告。

## 性能优化

### 内存

1. 防止内存泄露

    原因是长生命周期对象持有短生命周期对象。常见场景有:

- 长生命周期对象(单例、静态变量)严禁持有 Activity Context，统一使用 `ApplicationContext`。
- 非静态内部类/匿名内部类默认持有外部类引用，需改为**静态内部类 + 弱引用**。
- IO 流、注册的 Receiver/Listener 必须在对应的生命周期(如 `onStop/onDestroy`)成对释放。
- 无限循环的属性动画如果没有在 `onDestroy` 中 `cancel`，Animator 会持有 View 的引用，View 持有 Activity，导致泄漏。
- 在 `onDestroyView` 中必须置空 `_binding`引用。
- 不要在 XML 中直接写 WebView，而是动态 `add`，先从父容器 `remove`，再调用 `webView.removeAllViews()`，最后 `webView.destroy()`。
- 使用 **LeakCanary** 监控，早发现早修复。


2. 内存优化

   2.1 图片优化(大头)

- **采样压缩**：使用 `inSampleSize` 仅加载 View 大小的像素。
- **像素格式优化**：非透明图使用 `RGB_565` 替代 `ARGB_8888`。
- 使用 `WebP` 格式。
- **缓存管理**：合理设置 `LruCache` 大小。

  2.2 严禁在 **`onDraw` 或循环**中创建临时对象，避免内存抖动。

### 布局+绘制

  *   减少布局层级，如 **`ConstraintLayout`** 约束布局。
  *   使用 &lt;include/&gt; + &lt;merge/&gt; 标签复用公共布局**消除冗余的父容器**。
  *   使用 &lt;ViewStub/&gt; 在合适时机加载布局。
  *   避免嵌套使用 wrap_content，用固定值或 `match_parent`。
---
  *   onDraw 中避免创建局部对象、执行耗时操作。
  *   移除默认的 Window 背景，移除 View 中的重复背景。

### 启动速度

- 将非必须在主线程执行的 SDK 初始化通过**线程池**异步加载。
- 利用 **`IdleHandler`**。当主线程空闲时再去执行一些次要的初始化任务。
- 减少应用启动时的依赖项数量。

### 包体积

- 开启 `shrinkResources`(移除未引用资源) + `minifyEnabled`(代码混淆、压缩、优化)使用。
   > `shrinkResources` 默认只是**替换**无用资源为空壳，并非物理删除，需要额外配置 `keep.xml` 精确控制。
- 本地图片使用 WebP 格式并压缩质量，远程图片 URL 支持动态压缩，格式转换。
- 只保留 `xxhdpi` 或 `xxxhdpi`目录的资源，系统会自动缩放。
- CPU ABI 过滤，只保留 **`arm64-v8a`**。
- so 动态下发。

## 事件分发机制

![View 树结构](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/View%20%E6%A0%91.png?raw=true) 
![image](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91.png?raw=true) 

**核心点: 每一个状态(如 `DOWN` 或 `MOVE`)产生时，都会触发一次完整的方法分发流程**。

Android 事件分发机制遵循 **"责任链模式"**，主要由 `dispatchTouchEvent`、`onInterceptTouchEvent` 和 `onTouchEvent` 三个方法协作完成。

事件往下传递流程

> Activity －> PhoneWindow －> DecorView －> ViewGroup －> ... －> View

如果没有任何 View 消费掉事件，那么这个事件会按照反方向回传，最终传回给 Activity，如果最后 Activity 也没有处理，本次事件会被抛弃。

| 类型   | 相关方法                                 | Activity | ViewGroup | View |
| ---- | ------------------------------------ | :------: | :-------: | :--: |
| 事件分发 | dispatchTouchEvent                   |     √    |     √     |   √  |
| 事件拦截 | onInterceptTouchEvent                |     X    |     √     |   X  |
| 事件消费 | onTouchEvent(dispatchTouchEvent 内调用) |     √    |     √     |   √  |

| 事件             | 简介                      |
| :------------- | :---------------------- |
| ACTION\_DOWN   | 手指 **初次接触到屏幕** 时触发      |
| ACTION\_MOVE   | 手指 **在屏幕上滑动** 时触发，会多次触发 |
| ACTION\_UP     | 手指 **离开屏幕** 时触发         |
| ACTION\_CANCEL | 事件 **被上层拦截** 时触发        |
### ViewGroup 分发流程

在 **ViewGroup** `dispatchTouchEvent` 中，每次事件都会先调用 `onInterceptTouchEvent`，默认返回 `false`，会遍历所有的子 View，调用其 `dispatchTouchEvent`。如果返回 `true`，事件留给自己的 `onTouchEvent` 处理。

>   最顶层的子 View 开始往下遍历，对每个子 View 做两个判断：  
  1. **View 是否可见**。
  2. **触摸坐标是否在 View 的矩形范围内**。

###  View 分发流程

在 **View** 中，`dispatchTouchEvent` 会先检查是注册了 `OnTouchListener.onTouch`方法，如果有且返回 `true`，事件消费结束；否则走 `onTouchEvent`，其中 `ACTION_UP` 时会触发 `onClick`。

View `dispatchTouchEvent` 事件分发顺序：**`OnTouchListener.onTouch` > `onTouchEvent`(内部：`onLongClick` > `onClick`)**。

### 理解 ACTION 事件和 Event 方法的关系

整个事件分发，只需要搞清楚 **`ACTION_DOWN`** 时发生了什么，后续的 `MOVE` 和 `UP` 基本上是照着 DOWN 的结果执行的。

用一句话总结：

> **DOWN 负责"找到接收者"，MOVE 和 UP 负责"直接找到那个接收者"。**

只需要按这个顺序思考：

第一步：DOWN 时，谁的 onTouchEvent 返回了 true？
    → 找到这个"认领者"

第二步：认领者找到了吗？
    → 找到了：MOVE 和 UP 直接给它，不用再想
    → 没找到：MOVE 和 UP 直接给 Activity，不用再想

第三步（可选）：有没有父布局会中途拦截？
    → 有：子 View 收到 CANCEL，后续给父布局
    → 没有：按第二步结论执行

### 几个重要点

- 如果某个 View(或 ViewGroup) 在 **`ACTION_DOWN`** 时，**`onTouchEvent` 返回了 `true`，系统认为它认领了这个序列，后续的 `MOVE` 和 `UP` 都会优先传给它**；如果一个 View 在 `ACTION_DOWN` 时返回 `false`，系统就认为它不想要这个事件序列，后续的 `ACTION_MOVE` 和 `ACTION_UP` 都不会再传给它。
- `dispatchTouchEvent` 的返回值不是开发者直接控制的，而是由内部逻辑(其它 Event 方法)的执行结果决定的。
- ViewGroup 一旦在某个事件中调用了 `onIntercept` 返回 `true`，后续**同一序列(三个 Action 合在一起 = 一个事件序列)**的事件**不再询问** `onIntercept`，直接交给自己的 `onTouchEvent`。
 - 子 View 可以使用 `getParent().requestDisallowInterceptTouchEvent(true);` 阻止父布局拦截后续事件。

### 一个 View B、View A，View A 50% 透明度，是盖在 View B 的上面的蒙层，View B 上有一个 Button B，如何在点击 Button B 时有响应

设置 View A `android:clickable="false"`，表明它不消耗触摸事件，那么触摸事件会穿透 View A 传递给下方的 View，Button B 就能接收到点击事件并正常响应。

### 滑动冲突

当一个可以滑动的父布局里，嵌套了一个也可以滑动的子 View，手指滑动时，系统不知道该让谁滚动。

**外部法是父布局主动抢**，**内部法是子 View 主动护**。

1.外部拦截法，重写 ViewGroup 的 onInterceptTouchEvent 方法，符合事件分发原生逻辑，**优先使用**。

```java
   public class MyExternalInterceptorLayout extends FrameLayout {
   
   private int mLastX, mLastY;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercepted = false;
        int x = (int) ev.getX(); 
        int y = (int) ev.getY();
        
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
               // DOWN 事件绝对不能拦截
               // 否则子 View 永远收不到事件
             intercepted = false;
             break;
            case MotionEvent.ACTION_MOVE:
               int dx = Math.abs(x - mLastX); 
               int dy = Math.abs(y - mLastY);
               // 判断滑动方向：如果是竖向滑动，父布局拦截
                if (dy > dx) {
                    intercepted = true; // 父 View 处理
                } else {
                   intercepted = false; // 子 View 处理
                }
            case MotionEvent.ACTION_UP: 
            intercepted = false; //防止子 View 的 onClick 失效
            break; 
        }
        
        mLastX = x;
        mLastY = y;
        return intercepted;
    }
}
```

2.内部拦截法

父布局需要拦截除 DOWN 外所有事件，子 View 在需要时调用 `requestDisallowInterceptTouchEvent(true)` 阻止父布局拦截。

```java
public class MyScrollView extends ViewGroup {
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // DOWN 不拦截（否则子 View 拿不到入场券）
        // 其他事件默认拦截（等子 View 通知我要不要放行）
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            return false;
        }
        return true; // MOVE 和 UP 默认拦截，一旦子 View 放开拦截权，父 View 立刻接管。
    }
}
```

```java
public class MyChildView extends View {

    private int mLastX, mLastY;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int x = (int) ev.getX(); 
        int y = (int) ev.getY();
        
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
          // 按下时，先告诉父布局：你别拦截，我来处理
          getParent().requestDisallowInterceptTouchEvent(true); 
          break;
          case MotionEvent.ACTION_MOVE:
                int dx = Math.abs(x - mLastX); 
                int dy = Math.abs(y - mLastY);
                if (dy > dx) {
                // 竖向滑动，子 View 不需要，放开拦截权给父布局
                getParent().requestDisallowInterceptTouchEvent(false); 
                } else {
                  // 横向滑动，子 View 自己处理，继续阻止父布局拦截   
              getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
          case MotionEvent.ACTION_UP:
             break;
        }
       
        mLastX = x; 
        mLastY = y; 
        return super.dispatchTouchEvent(ev);
    }
}
```

## 消息机制

### 什么是 Handler

Handler 本质上是**线程间通信**的机制，可以实现任意两个线程之间的通信。

### 为什么要用 Handler

Android UI 是单线程模型，所有 UI 操作必须在主线程执行，所以子线程想要更新 UI 需要通过 Handler 发送消息到主线程，否则会触发异常。

### 为什么 Android UI 要设计为单线程

多个线程同时对同一个 UI 控件进行更新，容易发生不可控的错误，想解决这个问题就需要加锁，但加锁意味着耗时，导致 UI 卡顿。

### 子线程能不能更新 UI

可以。

*   在子线程更新在子线程创建的 View。
*   **`checkThread`** 是在 onResume 之后执行的，所以如果在 onCreate 中开子线程更新 UI，如果执行速度够快，是可能不报错的，但不可靠。

### 子线程中更新 UI 的方式

- handler.post / postDelay。
- activity.runOnUiThread。
- view.post / postDelay。
- kotlin 协程。

### Handler 常见的使用方式有哪些

```java
// 方式 A：sendMessage + handleMessage（处理复杂数据）
Handler handler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                // 处理消息
                break;
        }
    }
};
// 发送
Message msg = Message.obtain();
msg.what = 1;
msg.obj = "数据";
handler.sendMessage(msg);
handler.sendMessageDelayed(msg, 2000); // 2秒后处理

// 方式 B：post Runnable（处理简单逻辑，更常用）
handler.post(() -> {
    // 直接写逻辑，不需要 handleMessage
    textView.setText("更新UI");
});

// 方式 C：延时发送
handler.postDelayed(() -> { }, 2000);
```

### Message对象创建的方式有哪些 & 区别

new Message()

Message.obtain() 及其衍生方法

**obtain 作用**

它通过一个**单链表结构的消息池**(最大容量 50)复用已回收的 Message 对象，避免频繁 GC。消息被 Looper 分发处理后，会自动清空内容并归还回消息池中。

### Looper.prepare 作用是什么

- 创建 Looper 对象，将创建的 Looper 对象保存当前线程的 ThreadLocal 中。所以不同线程的 Looper 完全隔离，一个线程只能有一个 Looper，但可以有多个 Handler。
- 创建 MessageQueue 对象。

### 为什么 ThreadLocal 能隔离线程

ThreadLocal 是一个创建线程局部变量的类，该变量只能被当前线程访问，其他线程无法访问。内部数据结构为 ThreadLocalMap，key 是 ThreadLocal，value 是 Looper 对象。

### 为什么子线程中不能直接 new Handler，而主线程可以使用

因为子线程没有 Looper，主线程的 Looper 在 ActivityThread.main 方法中通过 prepareMainLooper 已经创建，子线程需要调用 Looper.prepare 和 Looper.loop，子线程才能使用 Handler。

### Looper.loop 过程做了什么

获得当前线程 Looper 对象，取出 MessageQueue 队列，通过 next 方法循环遍历队列中的 Message，通过 msg.target.dispatchMessage 将消息分发给对应的 Handler，没有消息时进入休眠释放 CPU 资源，有消息时再唤醒线程。

### Looper 如何区分多个 Handler 的

通过 msg.target 来区分，发送消息时 msg.target 被当前 Handler 赋值，分发消息时使用 msg.target 进行分发。

### handler postDealy 假设先 postDelay 10s，再 postDelay 1s，队列怎么处理这 2 条消息

尽管 10s 的消息先被添加，但因为其执行时间晚于 1s 的消息，所以它在队列中排在 1s 的消息之后，当 1s 延迟时间到达后，先处理 1s 的消息，等到 10s 消息时间到达后，再处理 10s 的消息。

### IdleHandler 概念及使用场景

IdleHanlder 是 MessageQueue 类的接口，当 MessageQueue 处于空闲时回调，常用于延迟执行初始化任务。

```java
// 正确使用方式
Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
    @Override
    public boolean queueIdle() {
        // 执行空闲时的任务（如预加载数据）
        preloadData();
        /** 返回 true：保留，下次空闲时继续回调 
            返回 false：执行一次后自动移除 
        */
        return false; // 只执行一次，执行完自动移除
    }
});
```

### Looper 在主线程中死循环，为啥不会 ANR？

- 保证主线程永不退出，所有的 Activity 生命周期、UI 绘制、用户输入，本质上都是一条条消息，在这个循环里被依次处理。

- 当 MessageQueue 中当前没有消息时，底层通过 Linux epoll 机制挂起主线程，释放 CPU 资源，有新消息时再次被唤醒。
> **为什么用 epoll 而不是 wait/notify**?
> 需要监听 Native 层事件，`wait/notify` 只能在 Java 层使用，无法被 Native 层唤醒。

### Handler 导致的内存泄露原因及其解决方案

1.  非静态内部类会持有一个外部类的隐式引用，可能会造成外部类无法被回收。所以使用静态内部类避免内存泄露，并使用弱引用持有外部类的对象。

2.  延时消息，Activity 关闭了但消息还没处理完，需要在 Activity 的 onDestroy 中调用 handler.removeCallbacksAndMessages(null) 移除 Callback 和 Message。

### 同步屏障机制

1. **开启屏障 (postSyncBarrier)**  
    调用 `queue.postSyncBarrier()` 会在消息队列插入一个特殊的**屏障消息**（其 `target` 属性为 `null`）。
2.  **发送异步消息**  
    创建 Handler 时设置 `async` 为 `true`，该 Handler 发送的所有消息自动标记为**异步消息**(Asynchronous Message)。也可以通过 msg.setAsynchronous(true) 单独设置某一条消息为异步。
3. **屏障的作用**  
    当 `MessageQueue` 遍历到屏障消息时，**暂停处理所有普通的同步消息**，查找并优先处理**异步消息**。如果没有异步消息，线程会进入休眠，直到有异步消息到来或屏障被移除。
4. **关闭屏障 (removeSyncBarrier)**  
    通过 `queue.removeSyncBarrier(token)` 移除屏障后，消息队列恢复正常逻辑，按时间顺序处理之前被拦截的同步消息。

`postSyncBarrier` 和 `removeSyncBarrier` 都是 `@hide` 的系统 API，普通开发者无法直接调用，因为如果屏障插入后忘记移除，会导致所有同步消息永久阻塞。

### Looper.quit 和 quitSafely 的区别

quitSafely 不再接收新的消息，等待队列中的消息全部处理完毕后，才会停止循环。

quit 会立即停止循环，移除队列中的所有消息。

### 如何从主线程往子线程发送消息

1. **在子线程中准备 Looper 和 Handler**

   ```java
   class MyWorkerThread extends Thread {
       Handler mHandler;
       
       @Override
       public void run() {
           Looper.prepare(); // 准备 Looper
           mHandler = new Handler() {
               @Override
               public void handleMessage(Message msg) {
                   // 在这里处理从主线程接收到的消息
                   switch (msg.what) {
                       case MY_MESSAGE_ID:
                           Object data = msg.obj;
                           // 处理data
                           break;
                       default:
                           super.handleMessage(msg);
                           break;
                   }
               }
           };
           Looper.loop(); // 开始Looper 的消息循环
       }
   }
   ```

2. **在主线程中发送消息到子线程的 Handler**

   ```java
   MyWorkerThread myWorkerThread = new MyWorkerThread();
   myWorkerThread.start();
   // 等待 Looper 准备好
   while (!myWorkerThread.mHandler.hasMessages(0)) {
       // 等待子线程的 Handler 可用
   }
   // 发送消息到子线程
   Message msg = new Message();
   msg.what = MY_MESSAGE_ID;
   msg.obj = "我是从主线程发送的消息";
   myWorkerThread.mHandler.sendMessage(msg);
   ```

## View 绘制

### View 绘制流程

**基本概念**

视图的结构顺序是 Windows(抽象类) - PhoneWindow- DecorView。

PhoneWindow 继承自 Window，是 Window 的唯一实现类，内部包含了一个 DecorView 对象。

DecorView 是顶层根视图，是一个 FrameLayout，内部包含了一个竖向的 LinearLayout ，LinearLayout 内包含了一个 titlebar 和 ContentView。

绘制流程可以分成 2 个阶段，DecorView 的创建、渲染过程。

**创建阶段**
调用 `setContentView()` 时，PhoneWindow 先实例化 DecorView（这也是为什么 `requestFeature` 必须在它之前调用），然后通过 XML Pull 解析器递归创建 View 树，并将所有 View 添加到 ContentView 中。

**绘制阶段**

在 `onResume` 之后，`ActivityThread` 调用 `WindowManagerGlobal.addView()`，创建 `ViewRootImpl`。它是整个 View 树的管理者，负责协调绘制和与 WMS 的通信。

`ViewRootImpl` 调用 `scheduleTraversals()`，通过 `Choreographer` 注册 VSync 回调，并插入**同步屏障**确保绘制任务优先执行。VSync 信号到来时，触发 `performTraversals()`，依次执行: 

- **Measure**：通过 `MeasureSpec` 递归测量每个 View 的宽高；
- **Layout**：递归确定每个 View 的位置（left/top/right/bottom）；
- **Draw**：按照背景→自身→子 View→前景的顺序完成绘制。

绘制完成后移除同步屏障，消息队列恢复正常。

### MeasureSpec 是什么

MeasureSpec 是一个有 32 bit 的 Int 值，高 2 位表示测量模式 Mode，低 30 位表示数值大小 Size。

它有 3 种模式：

**MeasureSpec.EXACTLY**

- 触发条件：子 View 设置了**精确数值**（如 `100dp`）或 **`match_parent`**。
- 含义：父容器已经确定了子 View 的精确大小，子 View 的最终大小就是 SpecSize。

**MeasureSpec.AT\_MOST**

- 触发条件：子 View 设置了 **`wrap_content`**。
- 含义：子 View 不能超过父容器给定的 SpecSize，但可以比它小。

**MeasureSpec.UNSPECIFIED(不常用)**

触发条件：`ScrollView`、`ListView` 等可滚动容器在测量子 View 时使用，表示对子 View 没有任何限制，子 View 想多大就多大。

```java
两者互相转换。
// 获取测量模式（Mode）
int specMode = MeasureSpec.getMode(measureSpec)
// 获取测量大小（Size）
int specSize = MeasureSpec.getSize(measureSpec)

// 通过 Mode 和 Size 生成新的 SpecMode
int measureSpec=MeasureSpec.makeMeasureSpec(size, mode);
```

### 子 View 的 MeasureSpec 和最终大小是如何确定的

**子 View 的 MeasureSpec 是由自身 LayoutParams 和父 View 的 MeasureSpec 共同决定的**。公式如下所示:

MeasureSpec ≠ View 的最终大小。
MeasureSpec = 父容器给子 View 的"约束条件" 。
View 的最终大小 = 子 View 在约束条件内"自己决定"的结果。

![img](https://upload-images.jianshu.io/upload_images/944365-c474d16d76c4ee2e.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)
文字版总结:
① 子 View `LayoutParams` 是固定值：无论父布局是什么模式，子 View 都是 EXACTLY。
② 子 View `LayoutParams` 是 match_parent：子 View 继承父布局的模式和大小。
③ 子 View `LayoutParams` 是 wrap_content：子 View 模式为 AT_MOST，大小不超过父布局。

### 自定义 View 设置 wrap_content 为什么不起作用

在 onMeasure 默认的实现中，当 View 的测量模式是 AT_MOST 或 EXACTLY 时，View 的大小会被设置成 specSize，specSize 为父容器大小，所以 wrap_content 看起来和 match_parent 效果相同。

**解决方案**：可以重写 onMeasure 自定义其大小 /在 XML 中给其一个确定值

```java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    // 获取宽-测量规则的模式和大小
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    // 获取高-测量规则的模式和大小
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    // 设置 wrap_content 的默认宽/高值。
    // 默认宽/高的设定并无固定依据，根据需要灵活设置。
    // 类似 TextView，ImageView 等针对 wrap_content 均在 onMeasure 对设置默认宽/高值有特殊处理。
    int mWidth = 400;
    int mHeight = 400;
  // 当布局参数设置为 wrap_content 时，设置默认值
    if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT && getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(mWidth, mHeight);
    // 宽/高任意一个布局参数为 = wrap_content 时，都设置默认值。
    } else if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(mWidth, heightSize);
    } else if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(widthSize, mHeight);
}
```

### 为什么 onCreate 获取不到 View 的宽高

因为 View 的绘制流程在 `onResume` 之后才开始，onCreate 阶段 Layout 还未执行。

### 如何在 onCreate 中正确获取 View 的宽高

- View.post 方法，等待 View 测量完成后执行。
- 使用 `ViewTreeObserver.OnGlobalLayoutListener` 监听布局完成事件。
- 手动调用 measure 方法，主动测量。

### getWidth 方法和 getMeasureWidth 区别

getMeasureWidth 获取的是测量结束后的值。而 getWidth 获取的是布局结束后的值，等于 `right - left`，是实际宽度。

### View\.post 与 Handler.post 的区别

- View.post 需要等待 View 被添加到窗口后才会回调，保证了执行时 View 已完成测量和布局，所以能正确获取宽高。

- Handler.post 没有这样的保证，只要 Looper 在运行就会执行。

### invalidate 和 postInvalidate 的区别，与 requestLayout 区别。

**invalidate 和 postInvalidate** 都只触发 `onDraw` 重绘，不重新测量和布局。区别是 `invalidate` 必须在主线程调用，`postInvalidate` 可以在子线程调用。

**requestLayout** 用于 View 的大小或位置发生变化时，会重新触发 `onMeasure` 和 `onLayout`。但 `onDraw` 不一定触发，只有当尺寸或位置真正改变时才会重绘。

### Surface、Canvas、Window 之间的关系是什么

Window 是屏幕上的矩形区域，每个 Window 对应一个 Surface(内存缓冲区)，Canvas 是在 Surface 上绘制的工具。

### SurfaceFlinger、Choreographer 和 Surface 之间的关系是什么

Choreographer 监听 VSync 信号，SurfaceFlinger 在下一个 VSync 信号时收集**所有 Surface 的内容进行合成**，提交给显示硬件。

### 什么是 SurfaceView，作用是什么

**SurfaceView** 的核心价值是拥有独立的 Surface 和渲染线程，适合游戏、视频等高频绘制场景，它利用双缓冲机制避免画面撕裂。但它的 Surface 在 View 层级的下方，不支持动画变换。

> 双缓冲机制是一种图形渲染技术，它为每个 Surface 分配了两个缓冲区，通过将前台和后台缓冲区快速交换实现图形流畅展示。

### SurfaceView 和 Surface 之间的关系是什么

`SurfaceView` 通过内部维护的 `Surface` 来接收图像数据，当应用程序向 `Surface` 写入图像数据时，`SurfaceView `负责将这些数据展示。通过 `SurfaceHolder` 接口与 `Surface` 进行交互，提供了控制 `Surface` 的方法，注册 `SurfaceHolder.Callback` 监听器，监听 `Surface` 的创建、销毁或变更。

### 屏幕刷新率 HZ 和帧率 FPS 的关系

- **帧率**是产生画面的速度。
- **刷新率**是显示画面的速度。
- 两者匹配时，你得到最流畅和无延迟的体验。
- 当帧率超过刷新率，多余的信息被浪费。
- 当帧率低于刷新率，画面可能会被重复显示。

## Binder 机制

### Android 中进程和线程的区别

每个进程都拥有独立的内存空间和资源，线程是进程的一部分，一个进程可以包含多个线程，同一进程内的所有线程共享该进程的内存空间和资源。

### **什么是多进程，目的是什么**

在Android中，默认情况下，每个应用都运行在自己的单一进程中。但我们可以通过配置，让应用的不同组件（如 Activity、Service 等）运行在独立的进程中。其主要目的有：

1. **增加应用的内存上限**
    - 系统为每个进程分配的内存上限是独立的，如果一个应用需要大量内存（例如处理大图片、视频编辑），可以将内存密集型的组件（如图库Activity、渲染Service）放到单独的进程，从而突破**单进程的内存限制**，避免`OutOfMemoryError`。
2. **隔离关键组件，提升稳定性**
    - 将核心、关键的业务(如音乐播放的Service)与不稳定的、复杂的业务(如网页浏览的Activity)分离开，隔离崩溃的影响。
3. **保活与进程优先级**
     - 后台进程(如一个播放音乐的服务)的优先级比空进程(没有任何活动组件的进程)高。
     - 通过将后台服务设置为前台服务（`startForeground`）并放在独立进程中，可以降低其被系统杀死的概率。即使这个后台进程被杀死，主UI进程也可能不受影响，用户体验更好。

### 多进程会带来哪些问题

1.Application 多次创建
- 每个进程都会独立创建`Application`实例，`onCreate()`执行多次。
- **注意**：需根据进程名区分初始化逻辑，避免重复加载库或资源。
2.静态成员与单例失效
- 静态变量、单例等属于进程私有，无法跨进程共享。
- **注意**：跨进程数据必须通过 IPC（如`ContentProvider`、AIDL）传递。
3.SharedPreferences 并发读写风险
- SP 基于XML文件，无跨进程锁，多进程同时写入会导致数据损坏或丢失。
- **注意**：改用`ContentProvider`、MMKV 或数据库 + `ContentProvider`。
4.数据库并发问题
- 多进程直接操作同一 SQLite 文件可能引发 `SQLiteDatabaseLockedException`。
- **注意**：通过 `ContentProvider` 封装数据库，确保线程安全和进程安全。
 5.内存开销增大
- 每个进程有独立内存空间和虚拟机，重复加载资源导致内存占用翻倍。
- **注意**：子进程应只加载必要资源，避免滥用多进程。
6.进程创建耗时
- 首次启动独立进程组件时，需 fork 新进程、初始化虚拟机，可能引发界面延迟。
- **注意**：预创建进程或推迟非关键启动，避免阻塞UI。
7.IPC 通信复杂度
- Binder 调用需处理序列化、异常、事务大小限制（1MB）等问题。
- **注意**：避免在主线程同步调用耗时IPC，传输大对象改用文件描述符。
8.进程被系统回收
- 独立进程可能因优先级低被杀死，导致服务中断。
- **注意**：前台服务提升优先级，设计合理的恢复机制（如重启服务）。
9.调试困难
- 日志交错，断点需附加到正确进程。
- **注意**：日志添加进程 ID/名，使用`adb shell ps`查看进程列表。

### **如何开启多进程**

在`AndroidManifest.xml`中为组件添加`android:process`属性。
```xml
<activity
    android:name=".GalleryActivity"
    android:process=":gallery" /> <!-- 私有进程 -->

<service
    android:name=".RemoteService"
    android:process="com.yourapp.remoteservice" /> <!-- 全局进程 -->
```
- `:gallery`：冒号开头，表示这是一个**私有进程**，进程名是`包名:gallery`。其他应用组件不能和它跑在同一个进程里。
- `com.yourapp.remoteservice`：完整包名形式，表示这是一个**全局进程**。允许其他应用通过相同的`sharedUserId`和签名，和它跑在同一个进程里，以实现数据共享。
- 为了完成协同工作，这些进程之间需要一种通信方式 —— **IPC(进程间通信)**。

### 什么是 IPC(进程间通信）

IPC 是一套技术方案的总称，用于实现**不同进程之间的数据交换和方法调用**。在 Linux 系统中，传统的 IPC 方式有 Socket、管道、消息队列、共享内存等。

### 什么是 Binder

Android 系统中特有的一种高性能的进程间通信机制，基于 C/S 架构，Android 的四大组件、系统服务（如 ActivityManagerService）之间的通信底层都依赖于 Binder，**是 Android 整个系统的“血管”**。

**传统的 IPC 方式过程**

进程间的内存隔离是通过用户空间与内核空间的划分实现的。每个进程拥有独立的用户空间，无法直接访问彼此数据；而内核空间是所有进程共享的，作为可信的中转区域。  

因此，一次典型的 IPC 通信需要两次数据拷贝：发送方将数据从用户空间拷贝到内核空间；接收方再从内核空间拷贝到自己的用户空间。这两次拷贝带来了额外的性能损耗，是传统 IPC 的性能瓶颈。

**为什么 Android 选择 Binder 作为 IPC 的方式，Binder 的优点是什么**

性能

> Binder 在内核空间引入一个 **Binder 驱动**，将用户空间的一块内存与内核空间缓冲区进行了映射，发送方将数据从用户空间拷贝到内核空间缓冲区，接收方通过内存映射直接读取，只需一次拷贝。

安全

> Binder 为每个进程分配了唯一 UID 用来鉴别身份，支持权限控制。

### Binder 服务获取过程

在 Binder 通信中，服务端首先向 ServiceManager 注册自身服务（提供服务名和 Binder 实体引用）；客户端发起服务请求时，Binder 驱动将请求转发给 ServiceManager 查询；ServiceManager 返回对应服务的 Binder 实体引用后，驱动在客户端进程中创建该引用的代理对象，供客户端后续跨进程调用。

### 什么是 AIDL

是一种**接口定义语言**，它允许我们定义客户端和服务端之间通信的接口。直接使用 Binder进行 IPC 需要编写大量的模板代码，AIDL则通过一个`.aidl`文件自动生成这些模板代码，大大简化了开发工作，**是 Binder 的一种便捷使用方式**。

### AIDL 的使用步骤

1. 创建 AIDL 接口

   在 Android Studio，在服务端模块的 **`aidl` 目录下创建一个新的 AIDL 文件**。

   ```java
   package com.example.mathservice;
   
   // Declare any non-default package here if used in .aidl file
   import com.example.mathservice.Person;
   
   // Interface definition for the math service.
   interface IMathService {
       // Adds two integers and returns the sum.
       int add(int a, int b);
   
       // Demonstrates passing and returning a custom Parcelable object.
       Person getPerson();
   }
   ```

2. 创建 Parcelable 对象

   AIDL 接口中如果涉及到了自定义对象，确保该对象实现了`Parcelable`接口

   ```java
   package com.example.mathservice;
   
   import android.os.Parcel;
   import android.os.Parcelable;
   
   public class Person implements Parcelable {
       private String name;
       private int age;
   
       public Person(String name, int age) {
           this.name = name;
           this.age = age;
       }
   
       // Parcelable implementation methods (Constructor, describeContents, writeToParcel, CREATOR)
       // ... (Omitted for brevity, but you should implement these)
   }
   ```

3. 实现服务端

   ```java
   package com.example.mathservice;
   
   import android.app.Service;
   import android.content.Intent;
   import android.os.IBinder;
   import android.os.ParcelFileDescriptor;
   import android.util.Log;
   
   public class MathService extends Service {
       private final IBinder mBinder = new IMathService.Stub() {
           public int add(int a, int b) {
               Log.d("MathService", "Adding " + a + " + " + b);
               return a + b;
           }
   
           public Person getPerson() {
               return new Person("Alice", 30); // Example Person object
           }
       };
   
       @Override
       public IBinder onBind(Intent intent) {
           return mBinder;
       }
   }
   ```

4. 客户端绑定服务

   ```java
   package com.example.clientapp;
   
   import android.content.ComponentName;
   import android.content.Context;
   import android.content.Intent;
   import android.content.ServiceConnection;
   import android.os.Bundle;
   import android.os.IBinder;
   import android.os.RemoteException;
   import android.widget.TextView;
   import androidx.appcompat.app.AppCompatActivity;
   
   import com.example.mathservice.IMathService;
   import com.example.mathservice.Person;
   
   public class MainActivity extends AppCompatActivity {
       private TextView mResultText;
       private IMathService mMathService;
   
       @Override
       protected void onCreate(Bundle savedInstanceState) {
           super.onCreate(savedInstanceState);
           setContentView(R.layout.activity_main);
   
           mResultText = findViewById(R.id.result_text);
   
           Intent intent = new Intent();
           intent.setComponent(new ComponentName("com.example.mathservice", "com.example.mathservice.MathService"));
           bindService(intent, connection, Context.BIND_AUTO_CREATE);
       }
   
       private ServiceConnection connection = new ServiceConnection() {
           @Override
           public void onServiceConnected(ComponentName className, IBinder service) {
               mMathService = IMathService.Stub.asInterface(service);
               try {
                   int result = mMathService.add(5, 7);
                   mResultText.setText("Result: " + result);
   
                   Person person = mMathService.getPerson();
                   // Display person details or use them as needed
               } catch (RemoteException e) {
                   e.printStackTrace();
               }
           }
   
           @Override
           public void onServiceDisconnected(ComponentName arg0) {
               mMathService = null;
           }
       };
   }
   ```

### 如何优化多模块都使用 AIDL 的情况

使用 Binder 连接池只需创建一个 Service 类，Stub 实现类中通过 binderCode 标识码返回对应 AIDL 实例。

1.定义各业务 AIDL。
```java
// 业务1：用户服务
interface IUserService {
    String getUserInfo();
}

// 业务2：订单服务
interface IOrderService {
    String getOrderList();
}
```

2.定义连接池 AIDL。
```java
// IBinderPool.aidl
interface IBinderPool {
  IBinder queryBinder(int binderCode);
}
```

3.实现连接池 Service。
```java
public class BinderPoolService extends Service {

    // 业务标识码常量
    public static final int BINDER_USER = 1;
    public static final int BINDER_ORDER = 2;

    private IBinder mBinderPool = new BinderPoolImpl();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinderPool;
    }

    // 连接池的核心实现
    static class BinderPoolImpl extends IBinderPool.Stub {
        @Override
        public IBinder queryBinder(int binderCode) {
            switch (binderCode) {
                case BINDER_USER:
                    // 返回用户服务的 Binder
                    return new UserServiceImpl();
                case BINDER_ORDER:
                    // 返回订单服务的 Binder
                    return new OrderServiceImpl();
                default:
                    return null;
            }
        }
    }
}
```
***
# 主题

## style 和 theme 的关系是什么

- **`Style` (样式)**：是**一组 View 属性的集合**。它的作用是**复用**。它直接应用于**单个 View**，用来定义这个 View 的长相。例如，定义一个 `Button` 的样式，包含它的背景色、文字颜色、圆角等。
- **`Theme` (主题)**：是**一组资源的集合**（这些资源包括颜色、字体、形状，甚至也包括 Style）。它的作用是**定义全局的视觉风格**。它被应用到整个 **Activity** 或 **Application**，所有隶属于它的 View 都可以访问其中定义的资源。
   - **关键**：Theme 中定义的属性，可以被 View 通过 `?attr/` 语法来引用。

**它们的关系是：**
1. **Theme 包含 Style**：一个 Theme 内部会引用很多预定义好的 Style（例如，它规定按钮的样式应该是 `Widget.MaterialComponents.Button` 这个 Style）。
2. **Theme 为 Style 提供“素材”**：Style 中经常会引用来自 Theme 的属性值（例如 `?attr/colorPrimary`），而不是一个固定的颜色值。这样，Style 的外观会随着应用 Theme 的改变而动态变化。
3. **应用层级不同**：Style 用在单个 View 上 (`<Button style="..."/>`)，Theme 用在 `AndroidManifest.xml` 的 `<application>` 或 `<activity>` 标签上。

`Style` 和 `Theme` 是 **“批量操作”** 和 **“动态换肤”** 的利器。

## Theme.AppCompat 和 MD1、2 、3 之间的区别和关系是什么

Theme.AppCompat
- **来源**：来自 AndroidX AppCompat 库（`androidx.appcompat:appcompat`）。
- **本质**：一个**向后兼容的基础主题**。它最早是为了在低版本 Android 系统上实现 Material Design 之前的视觉风格（如 Holo）而设计的，后来随着 Material Design 的演进，它成为了所有 Material 主题的“地基”。

Material Design 2（MD2）
- **来源**：来自 Material Components 库（`com.google.android.material:material`，版本通常低于 1.5.0）。
- **实现主题**：`Theme.MaterialComponents` 及其变体（如 `Theme.MaterialComponents.DayNight`）。
- **本质**：实现了 **Material Design 2 设计语言**的第一代官方主题。它**在 AppCompat 的基础上**增加了 MD2 特有的视觉属性(如颜色角色、组件样式、形状系统等)。

Material Design 3（MD3）
- **来源**：同样来自 Material Components 库（版本 1.5.0+ 开始引入 MD3 主题）。
- **实现主题**：`Theme.Material3` 及其变体（如 `Theme.Material3.DayNight`）。
- **本质**：实现了 **Material Design 3 设计语言**的最新官方主题。它**在 AppCompat 的基础上**增加了 MD3 特有的新特性，例如**动态颜色、新的颜色系统、更新的组件样式、新的组件等。

**三者的关系**

三者实际上是**层层继承**的：

- `Theme.AppCompat` 是最基础的兼容性主题，它本身继承自系统原生主题（如 `Theme` 或 `Theme.Material`，取决于 API 级别）。
- `Theme.MaterialComponents`（MD2 主题）**直接继承自 `Theme.AppCompat`**，并在此基础上添加 MD2 特定的属性定义和默认值。
- `Theme.Material3`（MD3 主题）同样**直接继承自 `Theme.AppCompat`**（或其特定的变体），而非继承自 `Theme.MaterialComponents`。这意味着 MD3 和 MD2 是并列的两个分支，它们都建立在 AppCompat 的兼容性基础上，但各自实现不同的设计规范。

## Android Theme 的发展历程，目前使用的策略是什么

### 1. Theme 系列（最古老，API 1+）
- **代表主题**：
    - `android:Theme`（深色基础版）
    - `android:Theme.Light`（浅色版）
    - `android:Theme.Translucent`（透明背景）
    - `android:Theme.Dialog`（对话框风格）
    - `android:Theme.NoTitleBar.Fullscreen`（全屏无标题）
 仅在 Android 1.0 到 2.3 时代使用，现在已经完全过时。
 
### 2. Holo 主题（Android 3.0+，API 11+）
- **代表主题**：
    - `android:Theme.Holo`
    - `android:Theme.Holo.Light`
    - `android:Theme.Holo.Light.DarkActionBar`
Holo 主题从 API 11 开始可用，无法在更低版本上直接使用。

### 3. DeviceDefault 主题（Android 4.0+，API 14+）

DeviceDefault 是一个特殊的主题类别，它的特点是：**在不同厂商的设备上，自动呈现该厂商定制的系统风格**。

- **代表主题**：
    - `android:Theme.DeviceDefault`
    - `android:Theme.DeviceDefault.Light`
    - `android:Theme.DeviceDefault.Dialog`
让应用能够融入各厂商（如三星、HTC、小米）的定制系统 UI，而不是强加 Google 的 Holo 风格。

### 4. Material 主题（Android 5.0+，API 21+）
这就是 Material Design 1（MD1）的系统原生实现。

- **代表主题**：
    - `android:Theme.Material`
    - `android:Theme.Material.Light`
    - `android:Theme.Material.Light.DarkActionBar`
只能在 API 21+ 的设备上使用，无法覆盖低版本用户。

### 使用策略
- 在 Material Design 普及之前，Android 主要有三大系统主题体系：最古老的 `Theme`，Holo 主题，以及 DeviceDefault，到了 Android 5.0，Google 推出了原生的 `Material` 主题。

- `Theme.AppCompat` 是一个"通用兼容层"，它能自动处理从最早期的 `Theme` 到 `Material`（MD1）的版本兼容，因为这些都是系统内置的。但它本身不包含 MD2 和 MD3 的完整设计实现。要使用 MD2 或 MD3，必须显式继承对应的 `Theme.MaterialComponents` 或 `Theme.Material3`，并引入 Material Components 库。

# Java

## JVM

###  JVM 的内存模型

 一、线程私有区域 (生命周期随线程)

**1. 程序计数器 (Program Counter Register)**
*   **核心作用**：记录当前线程执行字节码的**行号**。
*   **关键点**：
    *   **唯一无 OOM**：JVM 中唯一不会报 `OutOfMemoryError` 的区域。
    *   **线程切换**：保证线程切换后能恢复到正确的执行位置。

**2. 虚拟机栈 (Java Virtual Machine Stack)**
*   **核心作用**：描述 Java 的方法执行。
*   **存储内容**：局部变量表（基本数据类型、对象引用）、操作数栈、动态链接、方法出口。
*   **核心点**：**栈内存不需要 GC(垃圾回收)**。每个方法执行都会创建一个**栈帧**，栈帧随方法调用入栈，随方法结束出栈，自动释放。
*   **异常**：
    *   `StackOverflowError`（栈深度过大，如死递归）。
    *   `OutOfMemoryError`（无法申请到足够内存）。

**3. 本地方法栈 (Native Method Stack)**
*   **核心作用**：与虚拟机栈类似，区别在于它服务于 **Native 方法**（C/C++编写的底层方法）。
---
 二、 线程共享区域 (生命周期随虚拟机)

**1. 堆 (Heap)**
*   **核心作用**：**最大**的一块内存，**GC(垃圾回收)的主要场所**。
*   **存储内容**：几乎所有的**对象实例**和**数组**。
*   **分代模型**（经典）：
    *   **年轻代 (Young Gen)**：
        *   **Eden 区**：新对象出生地。
        *   **Survivor 区 (From/To)**：存放 GC 后幸存的对象（通常比例 8:1:1）。
    *   **年老代 (Old Gen)**：存放生命周期长的对象或大对象。

**2. 方法区 (Method Area)**
*   **核心作用**：存储已被虚拟机加载的**类信息**。
*   **存储内容**：常量、静态变量、即时编译器编译后的代码、**运行时常量池**。
*   **版本演变**（面试高频）：
    *   **JDK 1.7 及之前**：称为“永久代” (PermGen)，位于堆内存中。
    *   **JDK 1.8 及之后**：称为**元空间(Metaspace)**，使用**本地内存 (Native Memory)**，不再受 JVM 堆大小限制，只受本机物理内存限制。

---
**3.记忆口诀（一分钟速记）**

*   **私有区**：**栈**管运行（无GC），**计数器**指路（无OOM）。
*   **共享区**：**堆**存对象（GC主战场），**方法区**存类信息（1.8变元空间）。
*   **分代**：新对象在 Eden，幸存者去 Survivor，老家伙进 Old。

### Android 的内存有哪些分类，Java Heap 和 Native Heap 是什么区别

Android 应用进程运行在 Linux 用户空间，其内存主要被划分为两个“世界”：**Java 世界（受管内存）** 和 **Native 世界（本地内存）**。

1.核心分类对比

这是开发者最需要关注的两大块内存区域。

A. Java Heap (Java 堆内存)
*   **定义**：由 ART (Android Runtime) 虚拟机分配和管理的内存区域。
*   **存放内容**：
    *   所有的 Java 类对象实例（`new Object()`, `ArrayList`, `Activity` 实例等）。
    *   Kotlin 中创建的对象（最终也是编译为 Java 字节码运行）。
*   **管理方式**：**托管式（Managed）**。
    *   开发者负责分配（`new`），**垃圾回收器（GC）** 负责回收。
    *   GC 会定期扫描，标记不再引用的对象并释放内存。
*   **内存上限 (Hard Limit)**：**有严格限制**。
    *   系统根据设备物理内存大小（RAM）和屏幕分辨率，为每个 App 设定了一个阈值（Heap Size Limit）。
    *   常见阈值：192MB, 256MB, 512MB 等。
    *   **OOM 表现**：一旦 Java 对象总大小超过此阈值，直接抛出 `java.lang.OutOfMemoryError`，导致应用崩溃。
    *   注：可以通过在 Manifest 中设置 `android:largeHeap="true"` 来申请更大的 Java 堆阈值，但应谨慎使用。

B. Native Heap (Native 堆内存)
*   **定义**：C/C++ 代码通过 JNI (Java Native Interface) 直接向操作系统内核申请的内存。
*   **存放内容**：
    *   **C/C++ 库数据**：`.so` 库中通过 `malloc` / `new` 分配的内存。
    *   **Bitmap 像素数据 (Android 8.0+)**：这是最大的变化。为了减轻 Java 堆的 GC 压力，Bitmap 的像素缓冲区（byte[]）被移到了 Native 层，但 Java 层仍持有其引用。
    *   **NIO DirectByteBuffer**：用于高效 I/O 操作的缓冲区。
    *   **Webview & 浏览器内核**：复杂的网页渲染数据。
    *   **音视频编解码缓冲区**：FFmpeg 等库使用的内存。
*   **管理方式**：**手动管理 (Manual)**。
    *   原则上需要开发者手动调用 `free` / `delete`。
    *   特例：对于 Bitmap 和 DirectBuffer，Android 使用了 `NativeAllocationRegistry` 机制，使得 Java 对象被 GC 时，能辅助触发 Native 内存的回收，但机制较为复杂，不如纯 Java 回收及时。
*   **内存上限**：**无特定阈值，受物理环境限制**。
    *   **64位进程**：几乎只受限于设备的总物理内存（RAM + Swap/zRAM）。
    *   **32位进程**：受限于虚拟地址空间（通常只有 3GB - 4GB），这是老旧设备上 Native OOM 的主要原因。
*   **OOM 表现**：通常导致进程直接被系统信号杀死（Signal 6 SIGABRT 或 Signal 11 SIGSEGV），Logcat 中可能看不到明显的 Java 堆栈信息，排查难度大。
---
2.对比总结表

| 特性 | Java Heap (Java 堆) | Native Heap (Native 堆) |
| :--- | :--- | :--- |
| **主要使用者** | Java / Kotlin 业务代码 | C/C++ 底层库, 图形引擎, 系统框架 |
| **分配方式** | `new` 关键字 | `malloc()`, `calloc()`, `new`, `mmap` |
| **回收机制** | **GC 自动回收** (会触发 STW 暂停) | **手动释放** 或 依赖底层引用机制 |
| **内存瓶颈** | **虚拟机设定的阈值** (如 256MB) | **物理内存总量** 或 **虚拟地址空间** (32位) |
| **崩溃形式** | `java.lang.OutOfMemoryError` (可捕获) | Native Crash / System Kill (难以捕获) |
| **Bitmap 存储** | Android 8.0 之前 | **Android 8.0 及之后** (主要占用源) |
3.其他关键内存区域

除了堆内存，`dumpsys meminfo` 还会显示以下重要分类：

1.  **Code (代码区)**
    *   **内容**：映射到内存中的 `.dex` (Dalvik Executable)、`.so` (Native Library)、`.jar`、`.apk` 资源文件。
    *   **特点**：通常是只读的。如果应用引用了大量庞大的第三方 SDK，这部分内存会显著增加。

2.  **Stack (栈内存)**
    *   **内容**：每个线程（Thread）运行时的方法调用链、局部变量、基本数据类型。
    *   **特点**：每个线程有独立的栈空间（通常默认为 1MB 左右）。
    *   **异常**：递归过深或死循环会导致 `java.lang.StackOverflowError`。

3.  **Graphics / GL / EGL (图形显存)**
    *   **内容**：GPU 渲染所需的纹理（Textures）、顶点数据（Vertex Buffers）、显示列表。
    *   **重要性**：游戏、地图、相机应用的大户。这部分内存通常不计入 Java Heap，但在某些设备上会计入 Native Heap 或单独列为 `Gfx dev`。
    *   **风险**：如果显存泄漏，会导致花屏、黑屏或系统界面卡死。
4.  **System / Private Other**
    *   系统为了管理该进程而分配的额外内存（如页表 PageTables）。

### Graphics / GL Memory (显存/图形内存) 属于 Native Heap (Native 堆) 吗？

在 Android 的内存管理视角下，**Graphics / GL Memory 通常不属于 Native Heap**。它们是两个独立统计的内存类别，尽管在物理硬件上(特别是手机上)它们都消耗着同一个系统 RAM。

 **物理硬件：Unified Memory Architecture (UMA)**

虽然逻辑上分开，但它们的关系非常紧密：

*   **PC 架构:** 有独立的显卡和显存 (VRAM)。比如 16GB 内存条 + 8GB 显卡显存。Native Heap 用内存条，Graphics 用显卡显存。
*   **手机架构 (SoC):** 采用 **统一内存架构 (UMA)**。CPU 和 GPU **共享** 同一块物理 RAM。

**这意味着：**
Native Heap 用多了，留给 Graphics 的就少了；反之亦然。它们在同一个池子里抢水喝。

**3.总结对比表**

| 特性                      | Native Heap                 | Graphics / GL Memory                       |
| :---------------------- | :-------------------------- | :----------------------------------------- |
| **主要内容**                | C++ 对象, Bitmap (8.0+), 逻辑数据 | OpenGL 纹理, Mesh, Render Targets            |
| **分配器**                 | `malloc` / `new`            | GPU Driver (`kgsl`, `mali` 等)              |
| **系统调用**                | `brk` 或 `mmap` (匿名映射)       | `ioctl` -> `mmap` (设备文件映射 / ION / DMA-BUF) |
| **Android Profiler 分类** | **Native**                  | **Graphics**                               |

### 内存上限 512M 如何理解，是这个 app 使用过程中内存占用的上限吗

**是的，这是该 App `Java 堆内存（Java Heap）` 的上限。**

 **1.什么是“Java 堆内存上限”？**

Android 系统为了保证手机流畅，不能让一个 App 把所有物理内存(比如手机的 8GB 或 12GB RAM)都吃光。因此，系统会给每个 App 设定一个**配额**。

*   **这个 512MB 就是系统给这个 App 划定的“Java 对象专用资金池”的最高额度。**
*   **包含什么：** 你在代码里创建的几乎所有对象(比如 `new String()`, `new ArrayList()`, `new Bitmap()` 等 Java 对象)都必须住在这个池子里。
*   **不包含什么：** App 运行所需的代码本身（Code）、加载的 C++ 本地库(Native Heap)、显存(Graphics)等，通常**不**算在这个 512MB 里(或者是分开计算的，取决于 Android 版本)。

**结论：** 这 512MB 是 App 存放数据的核心仓库。一旦这个仓库塞满了，哪怕手机还有 4GB 的总空闲内存，App 也会因为“仓库爆仓”而崩溃（OOM）。

 **2.为什么是 512MB？**

这个数字不是 App 自己定的，而是**手机厂商在出厂时写在系统里的**。

*   **配置项：** `dalvik.vm.heapgrowthlimit`
*   **动态调整：** 这个值取决于手机的配置。
    *   老旧手机可能只有 192MB 或 256MB。
    *   配置较好的手机，系统会大方地给 **512MB** 的额度。

**场景还原：**
App 就像一个气球。
*   它一直在吹大（申请内存）。
*   吹到了 **512MB**（Target Footprint），碰到了天花板（Growth Limit）。
*   此时，App 还要再往里吹 **7MB** 的气（申请 `7423248 byte`）。
*   气球无法再变大，也没有空间容纳这口气，于是 **“嘭”** 的一声，炸了（OOM 崩溃）。

**3.常见疑问解答**

**Q: 开发者能突破这个 512MB 限制吗？**
**A:** 可以，但有限制。
开发者可以在 `AndroidManifest.xml` 中开启 `android:largeHeap="true"` 选项。
*   **开启前：** 受 `heapgrowthlimit` 限制（比如你的 512MB）。
*   **开启后：** 受 `heapsize` 限制（通常会比 512MB 更大，比如 768MB 或 1GB）。
*   **但是：** 即使开启了，如果代码写得烂（有内存泄漏），给多大内存最终都会填满崩溃。

### 类加载过程，类加载器，双亲委派及其优势

1.**类加载过程**

​      加载 - 验证 - 准备 - 解析 - 初始化。

​     **加载**

​     将外部的 class 文件加载到虚拟机，存储到方法区。

​     **验证**

​     验证 class 文件的合法性。

​    **准备**

​    为静态变量分配内存，并设置初始值。

​    **解析**

​    将常量池内的符号引用转为直接引用。

​    **初始化**

​    初始化静态变量、静态代码块类记载器。

2.**JVM 类加载器**

​      两个类相等的前提是：**同一个类文件 + 同一个类加载器**。

​     **启动类加载器**

​     加载 \<JAVA\_HOME>\lib 目录中的类。

​     **扩展类加载器**

​     加载 \<JAVA\_HOME>\lib\ext 目录中的类库。

​     **应用程序类加载器**

​     加载用户类路径（ClassPath）上所指定的类库。

​     **自定义类加载器**

​     实例化 ClassLoader 类，重写 loadClass 方法。

3.**双亲委派模型**

   当一个类加载器收到加载请求时，它首先不会立即尝试加载这个类，而是把这个请求委托给它的父类加载器去完成，如果父类加载器能够完成加载，则成功返回；如果父类加载器无法加载，则子加载器才会尝试自己加载。

​       **作用**

​      保证了类的唯一性和核心类的安全性。

### 对象创建、存储、访问过程

1.对象创建

  1.  JVM 先检查类是否已加载，未加载则触发类加载流程。

  2.  加载完成后分配内存，分配方式取决于堆是否规整:

  - Java 堆内存是规整的
    已使用的内存在一边，未使用内存在另一边，中间放一个指针作为分界点，分配内存时将指针向空闲方向移动一段与对象大小相等的距离，称为**指针碰撞**。

  - Java 堆内存是不规整的
    维护着一个记录可用内存块的列表，在分配时从列表中找到一块足够大的空间划分给对象实例，并更新列表上的记录，称为**空闲列表**。

2.对象的内存布局

对象在 Java 内存中的存储布局可分为三块

**对象头**

* 存储自身的运行时数据(Mark Word)，被设计成 1 个非固定的数据结构以便在极小的空间存储尽量多的信息。

*  类型指针

   确定这个对象是哪个类的实例，如果对象是数组，那么在对象头中还必须有一块用于记录数组长度的数据。

**实例数据**

存储对象定义的各类型字段内容。

**对齐填充**

占位符，对象的大小必须是 8 字节的整数倍，对象头部分正好是 8 字节的倍数，当实例数据部分没有对齐时，通过对齐填充来补全。

3.对象的访问定位

Java 通过**栈上的对象引用访问堆上的对象实例**。有 2 种访问对象的方式

**句柄**

划出一块内存作为句柄池，栈中存的是对象的句柄地址，通过句柄地址再去访问对象实例。优点是对象被移动时，只需要改变句柄地址，引用本身不需要修改。但需要二次寻址，速度慢。

**直接指针**

栈中存的是对象地址，直接定位访问对象。访问速度快，但对象被移动时需要修改引用地址。

### 如何判断对象是否要回收，有哪些垃圾回收算法

**判断一个 Java 对象是否存活有 2 种方式**

*   **引用计数法**
    每当有一个地方引用它，计数器 + 1，引用失效 - 1，当计数器为 0 时，表示该对象死亡可回收。
    
    优点：简单高效。
    缺点：无法解决互相引用问题。

*   **可达性分析法**
    当一个对象到 GC Roots 没有任何引用链相连时，则判断该对象不可达，会被标记等待回收。
    
    可作为 GC Root 的对象有：
     1.虚拟机栈中引用的对象。
     2.本地方法栈中 JNI 引用对象。
     3.方法区中常量、静态变量引用的对象。

**垃圾回收算法**

不同的内存区域采用不同的垃圾收集算法，不同的算法决定了回收的效率。

*   **标记-清除**

    标记出所有需要回收的对象，统一清除被标记的对象。

    优点：实现简单。

    缺点：清除效率低，且会产生大量不连续的碎片，当需要较大空间时，需要多次触发回收。

*   **复制算法**

    将内存分为大小相等的两块，每次使用其中一块，当使用的这块内存用完，就将这块内存上还存活的对象，复制到另一块未用过的内存上，清理掉使用的那一半内存。

    优点：解决了不连续内存碎片的问题。
    缺点：浪费空间。

*   **标记-整理**

    在标记-清除基础上，标记后让所有存活的对象都向一端移动，统一清除端以外的对象。

    优点： 解决了不连续碎片问题，不需要额外空间。
    缺点：移动需要更新引用，开销大。

*   **分代收集算法(主流)**
    年轻代、年老代占比为 1:2。

    新生代对象存活时间短，回收频率高，采用复制算法，Eden:From:To = 8:1:1，新生代 Eden + From Survivor 空间不足时，执行 Minor GC，仍然存活的对象复制到 To Survivor 区域，然后一次性清理 Eden、From Survivor 区域，**From Survivor、To Survivor 角色是动态切换的**。

    **Eden、From Survivor 区域上存活对象所需内存大小 > To Survivor 区域怎么办？**

    存不下来的对象会通过**内存分配担保机制**暂时保存在年老代。

    年老代对象存活时间长，回收频率低，采用标记-清除或标记-整理算法，老年代空间不足时，执行 Major GC。**大对象、年龄达到阈值(默认 15)的对象都分配到老年代**。

## 基础

### Java 1.7 和 1.8 特性

**1.7**

switch 支持 string

创建泛型实例，可以通过类型推断简化代码，new后面的 <> 内不用再写泛型

单个 catch 捕捉多个异常，异常之间用 | 隔开

**1.8**

lambda 表达式

接口可以添加 default 方法

允许使用双引号调用方法或者构造函数

### 对象的初始化顺序

父类的静态块 -> 子类的静态块 - 父类的构造块 -> 父类的构造方法 -> 子类的构造块 -> 子类的构造方法

**静态优于非静态，父类优于子类**

### 抽象类和接口的区别

抽象类对事物的属性和行为抽象，含抽象方法的一定是抽象类，但抽象类不一定含有抽象方法，必须通过子类继承，子类必须重写父类的所有抽象方法。

接口对事物某个行为抽象，可被多个类实现，属性都是全局常量，方法都是抽象的且为 public。接口不可被实例化，需通过子类实现。接口可继承接口。

### 四种引用类型区别

强引用：默认引用，不会被垃圾回收器回收。可以赋 null 值，让其被 GC 回收。

软引用：当内存不足时会被回收。常用于缓存场景。

弱引用：下次 GC 时被回收。常用于防止内存泄漏。

虚引用：任何时候都可能被回收。常用于跟踪对象的 GC 情况。

除了强引用，其它 3 种都可以与引用队列结合使用，当引用对象被回收时，引用本身会被放入队列。

### 移除 List 中的元素为什么要使用迭代器而不是 for 循环

 ArrayList 内部用 `modCount` 记录修改次数，直接使用 `list.remove()` 会让 `modCount` 和迭代器的 `expectedModCount` 不一致，触发快速失败机制。但迭代器的 `remove()` 方法，它会同步更新 `expectedModCount`。

### 什么是反射

**作用**

**运行时**获取类的完整信息。

**缺点**

1. 执行效率低
  - 方法查找需要字符串匹配。
  - 无法被 JIT 编译器内联优化。
  - 需要装箱和拆箱。
2. 破坏封装性，绕过了编译器的权限检查，可以访问 private 字段和方法。
3. 类型不安全，编译期无法检查，运行时才报错。

### 反射的实现方式有哪些

反射机制的实现主要通过操作`java.lang.Class`类，泛型形式为`Class<T>`，存放着对象的**运行时信息**。

- 对象.getClass

  ```java
  Boolean carson = true; 
  Class<?> classType = carson.getClass(); 
  System.out.println(classType);
  //输出结果：class java.lang.Boolean  
  ```

- 类名.class

  ```java
  Class<?> classType = Boolean.class; 
  System.out.println(classType);
  //输出结果：class java.lang.Boolean  
  ```

- Class.forName("包括包名的类名")

  ```java
  try {
      Class<?> clazz = Class.forName("com.example.MyClass");
  } catch (ClassNotFoundException e) {
      e.printStackTrace();
  }
  ```

### 反射有哪些常用 API 方法

getDeclaredXXX 获取的是**类自身**声明的所有方法，包含 public、protected 和 private 方法。

getXXX 获取的是**类自身和父类**的所有 public 方法。

```java
Class<?> clazz = Class.forName("com.example.User");

// 1. 创建实例
Object obj = clazz.newInstance(); // 已废弃
Object obj2 = clazz.getDeclaredConstructor().newInstance(); // 推荐

// 2. 获取字段
Field field = clazz.getDeclaredField("name"); // 包括 private
field.setAccessible(true); // 突破访问限制
field.set(obj2, "张三"); // 修改字段值
Object value = field.get(obj2); // 获取字段值

// 3. 获取方法
Method method = clazz.getDeclaredMethod("setName", String.class);
method.setAccessible(true); // 突破访问限制
method.invoke(obj2, "李四"); // 调用方法

// 4. 获取注解
Annotation annotation = clazz.getAnnotation(MyAnnotation.class);
```

### 什么是泛型，为什么要用泛型

适配广泛的类型，即参数化类型。

**类型**

泛型类、方法、接口。

**作用**

- 在编译期间对类型进行检查。

- 不需要强制类型转换。

### 什么是泛型擦除，为什么需要泛型擦除

Java 编译过程中，编译器会丢弃掉所有的类型参数信息。

**作用**

泛型是在 Java 5 中引入的特性，为了确保与旧版本的代码兼容。

泛型擦除简化了虚拟机的实现，不需要对  JVM 进行大规模改动来支持泛型。

### 泛型擦除的局限性有哪些

- 不能用基本类型作为泛型参数，必须用包装类。
- 运行时无法获取泛型的具体类型。
- 不能用 instanceOf 判断泛型类型。

### 什么时候用 `? extends T`，什么时候用 `? super T`，为什么？

1.`?` 无界通配符

表示任意类型，只能读(读出来是 Object)，不能写。

2.`? extends T` 上界通配符

`?` 是 T 或 T 的**子类**(上界是 T)，因为不知道存入的是什么子类，所以只能读不能写。

3.`? super T` 下界通配符

`?` 是 T 或 T 的**父类**(下界是 T)，可写不可精确读(读出来是 Object)。

### 单例写法

```java
静态内部类
public class Singleton {

    private Singleton() {}

    private static class InstanceHelp {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return InstanceHelp.INSTANCE;
    }
}

双重检查
public class Singleton {
    private static volatile Singleton single;
    private Singleton() {}
    
     public static Singleton getInstance() {
        if(single == null){
           synchronized(Singleton.class){
               if(single == null){
                   single == new Singleton();
               }
           }
        }
        return single;
    }
}
```

## 集合

### Collection

一个接口，是所有集合的父接口，包含 List、Set、Queue。

### ArrayList 和 LinkedList 区别

**存储的元素是有序的，可以重复**。

- ArrayList 基于数组，支持随机访问，查询快增删慢，需要移动大量元素，默认大小为 10。扩容操作需要调用把原数组整个复制到新数组中，大小是原有的 1.5 倍。非线程安全。

- LinkedList 基于双向链表，增删时只需要修改指针，但查询慢。非线程安全。

### HashSet 与 TreeSet 区别

**Set 中元素是无序的，不可以重复**。

**HashSet**

基于哈希表，无序。对象的 hashCode 和 equals 都不同才判断是不同对象。

**LinkedHashSet 使用双向链表维护元素的插入顺序，存入和输出时顺序一致**。

**TreeSet**

基于红黑树，可排序，非线程安全。

根据比较方法的返回结果是否为 0 来判断对象是否相同，0 表示相同则不存，非 0 表示不同则存。

**排序方式**

- 元素自身具备比较功能，即自然排序，需要实现 Comparable 接口，并覆盖其 compareTo 方法。

- 元素自身不具备比较功能，则需要实现 Comparator 接口，并覆盖其 compare 方法。

### HashMap 与 TreeMap 区别

- HashMap 按照 key 的 hashCode 值确定位置，是无序的 (存入时的顺序与输出的顺序不一致)。
  LinkedHashMap 使用双向链表来维护元素的顺序，存入和输出时顺序一致。

- TreeMap 按照 key 升序排列，实现 Comparator 接口，重写 compare 方法可自定义排序。

### HashMap 原理

**特点**

实现了 cloneable、Serializable 接口，允许键值为 null，无序，非线程安全。

HashMap 采用 **数组+链表** 来存储键值对，包含了一个 Entry 类型的数组 table，Entry 链表存储着键值对，有 next 指针指向下一个 Entry，初始化容量 16，负载因子 0.75。

**put**

使用 key 计算 hashCode 并使用扰动函数处理，再用 `hash & (n-1)` 位运算确定数组下标， key 和 hashCode 不存在时，插入链表头部，存在时则替换旧值。当链表长度 >= 8 **且数组长度 >= 64** 时转为红黑树。

**get**

根据 key 计算 hash 值，通过 hash 值寻找 table 的位置，遍历该位置的链表，查找 key 和 hashCode 是否存在，存在则返回 value，不存在则返回 null。

**扩容**

当数组长度超出了阈值时，创建一个长度为原来 2 倍的新数组，重新计算 hash 值确定每个数据的位置，并重新设置 hashMap 的扩容阀值。

#### 为什么不直接采用经过 hashCode 计算的值，还要用扰动函数额外处理

扰动函数让高位也参与运算，减少碰撞。

#### 为什么用位运算而不是取模

位运算比取模运算快得多。

#### 为什么数组长度必须是 2 的次幂

- 让位运算精准等于取模，保证哈希分布均匀。
- 扩容时可以高效迁移。

#### JDK 1.8 HashMap 改动

- 引入红黑树优化长链表查询。
- 头插法改尾插法解决多线程扩容死循环。
- 先插入后扩容避免无意义扩容。

#### 与 ConcurrentHashMap 的区别

- ConcurrentHashMap 对整个桶数组进行了分段，每一个分段上都用 Lock 锁进行保护，线程安全且粒度更细。

- 不允许 null 键值。

## 多线程

### Java 中创建线程的方式有哪些

*   继承 Thread 类

多个线程各自完成各自任务，各线程互相独立，但由于 Java 单继承机制，局限性高

```java
public class MyThread extends Thread {
    @Override
    public void run() {
        // 线程执行的代码
    }

    public static void main(String[] args) {
        MyThread myThread = new MyThread();
        myThread.start(); // 启动线程
    }
}
```

*   实现 Runnable 接口

各线程协作完成一个任务，可被多个 Thread 对象共享 Runnable 资源，避免了单继承的局限性

```java
public class MyRunnable implements Runnable {
    @Override
    public void run() {
        // 线程执行的代码
    }

    public static void main(String[] args) {
        Thread thread = new Thread(new MyRunnable());
        thread.start(); // 启动线程
    }
}
```

*   使用线程池

任务提交时按照'核心线程→队列→非核心线程→拒绝策略'的顺序处理。

线程的创建和销毁开销很大，线程池通过复用线程降低了这个开销。

**线程池的核心线程数如何设置？**

- **CPU 密集型任务**：核心线程数 = CPU 核心数 + 1。
- **IO 密集型任务**：核心线程数 = CPU 核心数 × 2。

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    2,                                    // corePoolSize：核心线程数
    5,                                    // maximumPoolSize：最大线程数
    60,                                   // keepAliveTime：非核心线程空闲存活时间
    TimeUnit.SECONDS,                     // 时间单位
    new LinkedBlockingQueue<>(10),        // workQueue：等待队列
    Executors.defaultThreadFactory(),     // threadFactory：线程工厂
    new ThreadPoolExecutor.AbortPolicy()  // handler：拒绝策略
);

public class MyRunnable implements Runnable {
    @Override
    public void run() {
        // 线程执行的代码
    }

    public static void main(String[] args) {
        // 创建一个固定大小的线程池
        ExecutorService executorService = Executors.newFixedThreadPool(5); 
        for (int i = 0; i < 10; i++) {
            executorService.submit(new MyRunnable()); // 提交任务到线程池
        }
        executorService.shutdown(); // 关闭线程池
    }
}
```

### 实现线程同步的方式有哪些

* synchronized 修饰代码块 

  修饰代码块时，需要指定锁对象，同一时刻只有持有该锁对象的线程才能执行该代码块。
  
- 对象锁 & 实例方法

  同一个实例的多个 synchronized 方法互斥；不同实例之间不互斥；非 synchronized 方法不需要锁，任何时候都可以访问。

- 类锁 & 静态方法

  类锁锁的是 `类名.class` 对象，全局唯一。静态 synchronized 方法和非静态 synchronized 方法之间不互斥。
   
  ```java
  public void Method(){ 
    synchronized (this){ 
     System.out.println("我是对象锁"); 
     try{ 
           Thread.sleep(500); 
        } catch (InterruptedException e){ 
           e.printStackTrace(); 
          } 
       } 
     } 
  
  public void Method2(){ 
    synchronized (Test.class){ 
     System.out.println("我是类锁"); 
     try{ 
           Thread.sleep(500); 
        } catch (InterruptedException e){ 
           e.printStackTrace(); 
          } 
      } 
   } 
  ```

*   Lock 锁

```java
// Lock 的完整使用方式
Lock lock = new ReentrantLock();

lock.lock(); // 获取锁
try {
    // 临界区代码
} finally {
    lock.unlock(); // 必须在 finally 中释放，防止死锁
}

// Lock 的高级特性
ReentrantLock reentrantLock = new ReentrantLock();

// 1. 可中断锁
lock.lockInterruptibly(); // 等待过程中可被中断

// 2. 超时锁
boolean acquired = lock.tryLock(3, TimeUnit.SECONDS); // 等待 3 秒，超时放弃

// 3. 公平锁（按等待顺序获取锁）
Lock fairLock = new ReentrantLock(true);

// 4. Condition（精确唤醒）
Condition condition = lock.newCondition();
condition.await();     // 等待（释放锁）
condition.signal();    // 唤醒一个等待线程
condition.signalAll(); // 唤醒所有等待线程
```

### synchronized 与 Lock 的区别

*   synchronized 是 Java 中的关键字，Lock 是一个接口。
*   synchronized 会自动释放锁，Lock 必须手动释放锁。
*    synchronized 不能中断锁，Lock 可以用 interrupt 来中断等待。
*   synchronized 是非公平锁，它无法保证等待的线程获取锁的顺序，Lock 可以设置是否为公平锁。

### 并发的三大特性

**原子性**

一个操作要么全部执行并且执行的过程不会被任何因素打断，要么就都不执行。

**可见性**

当多个线程访问同一个变量时，一个线程修改了这个变量的值，其他线程能够立即看得到修改的值。

**有序性**

程序执行的顺序按照代码的先后顺序执行，指令重排序不会影响单个线程的执行，但是会影响到线程并发执行的正确性。

### 什么是 volatile，与 synchronized 的区别

**volatile 作用**

默认情况下线程中变量的修改，对于其他线程并不是立即可见。

1. 保证可见性。修改后立即写入主内存，其他线程立即可见。

2. 保证有序性。禁用指令重排。

**重要区别**

volatile 不能保证线程的原子性，且只能用于变量中。

### volatile 关键字这么好，可不可以每个地方都使用

不行，只有当需要保证线程安全时才去使用它，否则频繁写入读取主内存消耗性能。

### 多线程环境下 i++ 这种操作使用 volatile 能保证结果准确吗

由于 Java 运算的非原子性，volatile 不能保证运算的原子性，所以是不安全的。

### CAS 

CAS 叫做比较并交换，是一种非阻塞同步，乐观锁。

CAS 指令需要有 3 个操作数，分别是内存地址 V、旧的预期值 A 和新值 B。当执行操作时，只有当 V 的值等于 A，才将 V 的值更新为 B。**原子类 Atomic 应用了 CAS 原理**。

**ABA 问题**

某个值原始值为 A，被修改成了 B，然后又被改成了 A，CAS 认为该值没有变更过，可以通过添加版本号来加强验证解决 ABA 问题。

### sleep 与 wait 区别，notify 和 notifyAll 区别

sleep 是Thread 类的方法，wait 是 Object 类的方法。

sleep 不会释放锁，到时间后会自动唤醒，wait 会释放锁并需要其它线程 notify/notifyAll 唤醒。

sleep 通常用于让当前线程短暂停顿，wait 用于线程间的同步。

notify 随机唤醒一个 wait 的线程。

notifyAll 唤醒所有在 wait 的线程。

### 两道经典题

- 2 个线程交替打印 1-100

```java
class MyRunnable implements Runnable {
    private int number = 1;
    @Override
    public void run() {
        while (true) {
            synchronized (this) 
                // 唤醒另一个等待的线程
                notify();  
                if (number <= 100) {
                    System.out.println(Thread.currentThread().getName() + ": " + number);
                    number++;
                    try {
                     //当前线程等待并释放锁
                     wait();
                    } catch (InterruptedException e) {}
                } else {
                  break;
                }
            }
        }
    }
}

public static void main(String[] args) {
     MyRunnable myRunnable = new MyRunnable();
     Thread t1 = new Thread(myRunnable, "线程 1");
     Thread t2 = new Thread(myRunnable, "线程 2");
     t1.start();
     t2.start();
}
```

2 个线程交替打印 1-100 的奇偶数

```java
class MyRunnable implements Runnable {
    private int number = 1;
    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                // 唤醒另一个等待的线程
                notify();  
                if (number <= 100) {
                    System.out.println(Thread.currentThread().getName() + ": " + number);
                    number++;
                    try {
                     //当前线程等待并释放锁
                     wait();
                    } catch (InterruptedException e) {}
                } else {
                  break;
                }
            }
        }
    }
}

public static void main(String[] args) {
     MyRunnable myRunnable = new MyRunnable();
     Thread t1 = new Thread(myRunnable, "奇数");
     Thread t2 = new Thread(myRunnable, "偶数");
     t1.start();
     t2.start();
}
```

- 生产者消费者模型

  - BlockingQueue

  - wait + notify
```java
class Bakery {
    private int breadCount = 0;  // 面包数量
    private final int MAX_BREAD = 5;  // 最大容量
    
    // 生产者方法
    public synchronized void produce() {
        while (breadCount >= MAX_BREAD) {
            try {
                System.out.println(Thread.currentThread().getName() + ": 面包满了，等待消费...");
                wait();  // 生产者等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        breadCount++;
        System.out.println(Thread.currentThread().getName() + ": 生产了一个面包，当前数量: " + breadCount);
        notifyAll();  // 唤醒可能等待的消费者
    }
    
    // 消费者方法
    public synchronized void consume() {
        while (breadCount <= 0) {
            try {
                System.out.println(Thread.currentThread().getName() + ": 没有面包了，等待生产...");
                wait();  // 消费者等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        breadCount--;
        System.out.println(Thread.currentThread().getName() + ": 消费了一个面包，当前数量: " + breadCount);
        notifyAll();  // 唤醒可能等待的生产者
    }
}

public class BakeryTest {
    public static void main(String[] args) {
        Bakery bakery = new Bakery();
        
        // 生产者线程
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                bakery.produce();
                try {
                    Thread.sleep(100);  // 模拟生产时间
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "生产者");
        
        // 消费者线程
        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                bakery.consume();
                try {
                    Thread.sleep(150);  // 模拟消费时间
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "消费者");
        
        producer.start();
        consumer.start();
    }
}
```

# Kotlin

## kotlin 与 Java 相比有哪些优点和缺点

优点

- Kotlin 引入了可空类型，减少了空指针异常的风险。
- 函数式编程提供了更强大的扩展性和灵活性。
- 类型推断减少了代码中的类型声明。
- 支持扩展函数、属性。
- 支持原生的协程，提供了轻量级的并发解决方案。
- 语法更加紧凑，减少了样板代码。

缺点

- 编译速度可能不如 Java。
- 会生成比 Java 更多的字节码，对 APK 体积有一定影响。

## 数据类 (data class) 的作用是什么，与普通类相比有何优势

数据类会自动生成一些常用的方法，一行搞定。
包括 `set/get`, `equals()`, `hashCode()`, `toString()`,`copy()`,`componentN`(支持解构)。

## 扩展函数的原理是什么

扩展函数可以在不修改原有类定义的情况下，为类添加新的函数和属性。

编译器将其转换为静态方法，目标对象作为第一个参数传入，所以不会修改原有类的字节码。

**局限性**
- 无法访问私有成员
- 不能被子类重写
- 成员函数优先级高于扩展函数

## 高阶函数是什么

满足以下条件之一的函数：

- **接收一个或多个函数作为参数**。

- **返回一个函数作为结果**。

 Lambda 参数在编译后会生成匿名类对象，频繁调用会有内存开销。
 加上 `inline` 关键字，编译器会将函数体和 Lambda 直接内联到调用处，消除匿名对象创建开销。

## Kotlin与 Java 代码互操作的方式和注意事项

Java 返回值在 Kotlin 中是平台类型，既可能是 String 也可能是 String? ，需要开发者自己判断是否为 null。

@JvmStatic：让伴生对象的方法可以像 Java 静态方法一样调用。

@JvmField：暴露属性为 Java 字段(跳过 getter/setter)。

@JvmOverloads： 注解生成带有重载方法的类，以便 Java 代码调用。

## Kotlin 的委托是什么

Kotlin 委托分两种

- 类委托(接口委托)：把接口的实现委托给另一个对象。
- 属性委托：把属性的 get/set 逻辑委托给另一个对象。
  自定义属性委托：需要实现 getValue/setValue。

Kotlin 内置的三种属性委托

1.lazy（懒加载）

2.Delegates.observable（可观察）

3.Delegates.vetoable（可拦截）

## 协程是什么，与线程的关系是什么，如何在 Android 中使用协程

协程是 Kotlin 提供的**用户态轻量级并发单元**，运行在线程之上。与线程相比，协程创建开销极小，可以创建成千上万个；挂起时释放线程，线程可以去执行其他协程，极大提升了线程利用率。

**核心特点**
 - 用同步的写法处理异步逻辑，告别回调地狱。
 - **结构化并发**。父协程取消时，所有子协程自动取消，不会有协程泄漏的问题。

**在 Android 中的使用方式**

- 在 Activity 或 Fragment 中使用 lifecycleScope。

- 在 ViewModel 中使用 ViewModelScope。

- 自定义 CoroutineScope。手动创建 CoroutineScope 来管理协程，需要手动控制其生命周期，包括启动和取消协程(一般不推荐)。

  ```java
  class MySingleton {
      // 创建一个协程作用域，使用 IO 调度器，并持有顶级的 Job 以便于手动控制生命周期
      private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
  
      // 示例方法，演示如何在单例中启动协程
      fun performBackgroundTask() {
          coroutineScope.launch {
              try {
                  // 这里执行你的后台任务，例如网络请求、数据库操作等
                  val result = withContext(Dispatchers.IO) {
                      // 模拟耗时操作
                      delay(1000)
                      "Task completed"
                  }
                  // 如果需要在主线程更新 UI，可以切换到主线程
                  withContext(Dispatchers.Main) {
                      // 更新UI逻辑
                      println(result)
                  }
              } catch (e: Exception) {
                  // 异常处理
                  println("An error occurred: ${e.message}")
              }
          }
      }
  
      // 当不再需要此单例时，应该取消所有关联的协程以避免内存泄漏
      fun cleanUp() {
          coroutineScope.cancel()
      }
  }
  ```

## 协程中的 CoroutineScope、CoroutineContext、Job、Dispatchers 概念是什么意思

**CoroutineScope = CoroutineContext + 生命周期控制能力**。

### CoroutineContext

一个**集合**(Set)，一个**执行环境**。

定义了协程**如何运行**，里面存放了协程运行所需的各种关键信息。集合里的每一个元素都是一个 `Element`，最重要的几个 `Element` 包括:

- **Job**: 控制协程的生命周期（比如取消）。
    
- **CoroutineDispatcher**: 决定协程在哪个或哪些线程上运行（比如 `Dispatchers.IO`）。
    
- **CoroutineName**: 给协程起个名字，方便调试。
    
- **CoroutineExceptionHandler**: 处理协程中未捕获的异常。

**关键特性：** CoroutineContext 有一个非常重要的特性：**叠加**（通过 `+` 操作符）。当两个 Context 相加时，如果它们有相同 `Key` 的 Element（比如两个 Job），那么右边的会覆盖左边的。

### Job

一个**可取消的、有生命周期的任务**。启动一个协程，它会返回一个 Job 对象，通过 Job 对象可以执行：

- 取消协程(`job.cancel()`)。
    
- 等待协程执行完毕(`job.join()`)。
    
- 查询协程的状态(是否活跃、是否完成、是否取消等)。

包含 2 种类型：

- 普通 `Job`：子协程失败会取消父协程，进而取消所有兄弟协程。
- `SupervisorJob`：子协程失败不影响父协程和其他兄弟协程，各子协程独立。

###  Dispatchers

**决定了协程运行的线程**。

- `Dispatchers.Default`: **CPU 密集型**任务。它使用一个与CPU核心数相关的线程池。例如：排序、复杂计算。
    
- `Dispatchers.IO`: **I/O 密集型**任务。例如：网络请求、读写文件、数据库操作。它使用一个共享的、更大的线程池。
    
- `Dispatchers.Main`: **在主线程（UI线程）上运行**。用于更新UI。需要在Android、Swing等有UI主循环的平台中使用。
    
- `Dispatchers.Unconfined`: 一个特殊的调度器，不限制任何特定线程，**不常用**。

### CoroutineScope

**定义了作用域的接口**，所有协程都必须通过一个 Scope 来启动。

**管理协程的生命周期**，启动协程会创建一个新的 **Job**，这个新 Job 会成为父 Job 的**子项**。正是这种父子关系，构成了结构化并发的基础：取消父协程/Scope，会递归地取消所有子协程。

##  launch 和 async 作用和区别

用于启动一个新的协程。

launch

**执行一段“一劳永逸”的工作**，**不关心**最终结果。

async

**执行一段工作并“异步获取”它的返回值**。调用 `Deferred.await()` 来**获取结果**（`T` 是结果的类型）。`await()` 是一个挂起函数，它会等待计算完成并返回结果。

## withContext 作用是什么

一个**挂起函数**，**临时改变当前协程的执行上下文**(通常是线程)，用于**安全地在不同线程间切换**，并**返回一个结果**。

代码模版

```kotlin
fun loadData() {
    viewModelScope.launch { // 1. 在主线程启动一个父协程
        try {
            // 2. 使用 coroutineScope 组织并发任务
            val (resultA, resultB) = coroutineScope {
                // 3. 启动并发子协程，无需指定Dispatcher
                val deferredA = async { repository.getDataA() }
                val deferredB = async { repository.getDataB() }
                // 4. 等待所有结果
                Pair(deferredA.await(), deferredB.await())
            }
            // 5. 在主线程处理结果
            _uiState.value = UiState.Success(resultA, resultB)
        } catch (e: Exception) {
            // 精准捕获 coroutineScope 内部抛出的任何异常
            _uiState.value = UiState.Error(e.message)
        }
    }
}
```

## Flow、StateFlow、ShareFlow 关系和区别

| 特性             | `Flow` (冷流)        | `StateFlow` (热流)                              | `SharedFlow` (热流)                            |
| -------------- | ------------------ | --------------------------------------------- | -------------------------------------------- |
| **核心用途**       | **处理数据流**          | **管理状态**                                      | **共享事件**                                     |
| **热度**         | **冷流**             | **热流**                                        | **热流**                                       |
| **含义**         | 对每个收集者，**独立**启动执行。 | **始终处于活跃状态**，存储状态值，并向**所有**收集者广播**当前和未来**的状态。 | **始终处于活跃状态**，向**所有**收集者广播**事件**。             |
| **必须有初始值？**    | 否                  | **是**                                         | 否                                            |
| **重播**         | 每次收集都从头开始。         | 自动保留**最后1个**状态值，并立即提供给新收集者。                   | 可配置**重播缓存**（replay），向新收集者发射最近N个值。            |
| **Android 类比** | 执行一次数据库查询。         | `LiveData` 的现代化、协程替代品。                        | 事件总线（如 `RxBus`）的替代品，用于一次性事件（如 `Toast`、导航命令）。 |
**冷流**像点播视频，没有订阅者时不产生数据，每个订阅者都会触发一次完整的数据流，是一对一的关系。适合网络请求、数据库查询等按需加载的场景。

**热流**像直播，无论有没有订阅者都持续产生数据，多个订阅者共享同一个数据源，新订阅者只能收到订阅后的数据，是一对多的关系。

##  suspend 函数和普通函数的区别是什么

- **suspend 函数**只能在协程或者另一个 `suspend` 函数内部调用，因为需要 Continuation(书签) 对象来支持挂起和恢复。
- **非阻塞挂起**。`suspend` 关键字本身不能挂起协程，真正触发挂起的是函数内部调用的挂起点。
- 编译器会对 suspend 函数进行 **CPS 变换**，将其转换为带有 `Continuation` 参数的普通函数，并生成**状态机代码**。

# 网络

## http 不同版本有什么区别

**http 1.0**

1.  短连接，每个请求都需要与服务器建立新的 TCP 连接

**Http 1.1**

1.  支持长连接，建立一次 TCP 连接就能进行多次 HTTP 通信

2.  管道机制，同一个 TCP 连接里面，客户端可以同时发送多个请求，提高了传输效率，但**请求和响应仍然是串行的**，会产生队头堵塞问题

**Http 2.0**

1.  二进制传输，比文本格式速度更快

2.  多路复用，允许在单个 TCP 连接上并行发送多个请求和响应，服务端不用按照顺序返回，避免了队头堵塞

3.  首部压缩，压缩请求头信息，且相同的信息只发送一次

4.  服务端推送

## http 状态码分类有哪些

1.  1xx 信息性状态码，表示接收的请求正在处理，这类状态码用于提供关于请求的一些信息性响应，而不是指示成功或失败
2.  2xx 成功状态码，表示请求正常处理完毕
3.  3xx 重定向状态码，表示要对请求进行重定向操作，301 永久重定向，302 临时重定向
4.  4xx 客户端错误状态码，表示服务器无法处理请求
5.  5xx 服务器错误状态码，表示服务器处理请求时出错

## http 请求有哪些类型

GET、POST、PUT、DELETE、HEAD 等

1.  GET 用于向指定的 URL 请求资源，请求参数和对应的值附加在 URL 后面

2.  POST 主要用于向指定的 URL 提交数据，通常用于表单发送

3.  PUT 用于将信息放到请求的 URL 上，是幂等操作，即多次执行相同

4.  DELETE 用于请求服务器删除 Request-URL 所标识的资源

5.  HEAD 与 GET 请求相一致，只不过响应体将不会被返回

## 在浏览器中输入一个网址会发生什么

1.  浏览器会向 DNS 发送一个查询，将输入的网址转换成对应的 IP 地址。

2.  浏览器获得了服务器的 IP 地址后，它会向该地址发送一个请求，以建立一个 TCP 连接。

3.  连接建立后，浏览器会向服务器发送一个 HTTP 请求。

4.  服务器收到你的请求后，生成一个 HTTP 响应发回到浏览器。

5.  浏览器收到响应后解析 HTML、CSS、JS 等代码，显示到网页上。

## https 是什么，传输流程是什么

HTTPS 依赖于 SSL/TLS 协议通过将 HTTP 的内容进行加密，使用非对称加密方式 + 对称加密混合的方式传输，确保数据在传输过程中的安全性。

流程如下:

1.  客户端向服务器发起 HTTPS 请求，连接到服务器的 443 端口
2.  服务器会返回一个证书，证书里面包含了公钥和证书信息
3.  客户端验证证书的合法性，如果证书验证通过，那么生成一个随机值
4.  客户端使用证书中的公钥将这个随机值加密，然后传送给服务器
5.  服务器使用自己的私钥解密这个随机值，然后使用这个随机值作为对称加密的密钥，用其将数据加密后传送给客户端
6.  客户端使用这个随机值解密服务器传送来的信息，获取到明文的 HTTP 数据

## TCP 三次连接、四次断开过程是什么，和 UDP 区别是什么

![示意图](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9pbWdjb252ZXJ0LmNzZG5pbWcuY24vYUhSMGNEb3ZMM1Z3Ykc5aFpDMXBiV0ZuWlhNdWFtbGhibk5vZFM1cGJ5OTFjR3h2WVdSZmFXMWhaMlZ6THprME5ETTJOUzAxTTJZMFlqTmlZekZsWkRSa01UZGxMbkJ1Wnc "示意图")

面向连接是指数据传输之前，发送方和接收方之间需要建立连接，TCP 是通过三次握手实现的，UDP 是无连接的协议，它不需要建立连接，而是直接发送数据。

**三次握手过程**

1.  客户端发送连接请求报文段，请求建立连接，SYN=1，ACK=0

2.  服务器收到请求后向客户端发送确认信息，表示同意建立连接，SYN=1，ACK=1

3.  客户端收到服务端的同意信息后再次向服务端发送确认信息，表示连接已经建立，ACK=1

**为什么要三次握手**

1.  可以确认对方的接收与发送能力正常
2.  防止已失效的请求报文段传送到服务端
3.  帮助双方同步初始的序列号，从而确定数据包的传输顺序。

**四次挥手**

1.  客户端发送终止请求，请求关闭连接，进入 FIN\_WAIT\_1 状态，FIN=1，ACK=0

2.  服务器收到请求并回送一个确认报文，服务端进入**CLOSE_WAIT**状态，FIN=1，ACK=1，客户端收到后进入 FIN\_WAIT\_2 状态

3.  当服务端处理完所有数据后，发送终止请求报文给客户端，进入**LAST_ACK**状态

4.  客户端收到报文后发送确认报文给服务端，进入**TIME_WAIT**状态，客户端关闭连接，服务端收到报文后也关闭连接，TCP 关闭

**四次挥手的原因**

因为 TCP 连接是全双工的，因此需要保证通信双方都能断开连接

**为什么客户端关闭连接前要等待 2MSL 时间**

保证客户端发送的最后 1 个断开连接的确认报文能到达服务端，让服务端能断开连接

## TCP 是如何保证传输的可靠性的

1.  **序列号**：TCP 会为每个发送的数据包分配一个唯一的序列号。接收方会根据序列号对接收到的数据包进行排序，确保数据的顺序正确。如果接收方发现数据包的序列号不连续，它会要求发送方重新发送缺失的数据包

2.  **确认应答**：接收方在成功接收到数据包后，会向发送方发送一个确认应答（ACK）。发送方会根据确认应答来判断数据包是否成功传输。如果发送方在一定时间内没有收到确认应答，它会认为数据包丢失，并重新发送该数据包

3.  **流量控制**：TCP 使用滑动窗口机制进行流量控制，接收方会根据自己的处理能力，告诉发送方一个窗口大小，发送方只能在这个窗口大小内发送数据。当接收方的处理能力下降时，它会减小窗口大小，从而限制发送方的发送速度。如果网络状况良好，窗口可以变大，发送更多的数据

4.  **校验和**：TCP 在发送数据时，会对每个数据包进行校验和的计算。接收方在收到数据包后，会对数据包进行同样的校验和计算，然后与发送方的校验和进行比对。如果校验和不一致，接收方会丢弃该数据包，并要求发送方重新发送。这样可以确保数据在传输过程中没有被损坏

## 长链接和短链接区别

长连接，指的是在 TCP 连接建立后，不管是否使用都保持连接，可以在多次交互中重复使用，减少了建立和断开连接的开销，适用于需要频繁进行数据传输的场景，一个典型应用是 WebSocket 协议。

短连接，通常只会在客户端和服务器间传递一次读写操作，然后立即断开连接，不会保持连接状态，适用于不需要频繁进行数据传输的场景，避免不必要的资源消耗，一个典型应用是 HTTP 协议。

# 跨端

## RN 跨端的原理是什么？JS 引擎在其中的作用是什么？

**跨端原理**

1. **统一代码层**  
    通过 React Native（或 Vue/React 等 JS 框架）编写业务逻辑，形成统一的 JS 层代码。
2. **端上容器**  
    在 iOS、Android 以及 Web 端分别实现容器（Container），这个容器负责加载、运行 JS 代码，生成原生 UI 结构，提供 JS 与原生之间的通信。
3.  **JS 引擎的作用**  
    容器内嵌 JS 引擎（如 Hermes、JavaScriptCore、V8 等），负责解释和执行 JS 代码(Bundle 形式)。JS 引擎是跨端的核心，因为它保证了业务 JS 代码能在各端一致运行。
4. **Bridge 桥接机制**  
    JS 代码如需调用原生能力（如摄像头、定位、支付等），通过 Bridge 机制，JS 调用会被转发到原生实现，原生再将结果回传 JS。这样业务侧只需关注 JS 层逻辑，平台差异通过 Bridge 屏蔽。
5. **资源和包管理**  
    业务代码、资源文件以独立包（MiniApp/Bundle）形式下发，容器动态加载。

**常见的 JS 引擎**
1. **JavaScriptCore（JSC）**
    - **平台**：iOS（原生自带）、Android（可集成）
    - **特点**：苹果开源的 JS 引擎，性能稳定，React Native 默认在 iOS 使用。
2. **V8**
    - **平台**：Android（主要）、Node.js、Chrome 浏览器
    - **特点**：Google 开源，性能极强，支持 JIT 编译，广泛用于 Web 和 Node.js，也可嵌入原生 App。
3. **Hermes**
    - **平台**：React Native（主推 Android，部分 iOS 支持）
    - **特点**：Facebook 开源，专为移动端优化，支持 AOT 编译，内存占用低，启动快，提升 RN 体验。
4. **QuickJS**
    - **平台**：轻量级嵌入式场景（如 IoT、微端应用）
    - **特点**：极小的体积，支持 ES2020，适合资源受限场景。

## JS 引擎和渲染引擎在跨端动态容器中的区别和联系

| 特征       | JS 引擎 (The Brain)                                     | 渲染引擎 (The Painter)                                                   |
| -------- | ----------------------------------------------------- | -------------------------------------------------------------------- |
| **主要职责** | 解析并执行 JavaScript 代码，处理业务逻辑、状态管理、事件循环。                 | 解析布局指令（如 Flexbox）、计算几何位置、处理绘制、合成像素并显示。                               |
| **处理对象** | AST（抽象语法树）、字节码、内存中的变量与函数。                             | DOM/组件树、CSS/样式表、Layer（图层）、GPU 指令。                                    |
| **代表产品** | V8 (Chrome/Node), JSC (Safari), Hermes (RN), QuickJS. | Blink (Chrome), WebKit (Safari), **Yoga (RN 布局引擎)**, Skia (Flutter). |
| **在跨端中** | 决定“**要做什么**”（如：点击按钮后数据加一）。                            | 决定“**长什么样**”（如：按钮在屏幕中心，红色背景）。                                        |
在动态容器中，两者不是孤立的，必须通过**通信机制**（Bridge 或 JSI）进行协作：

1. **逻辑驱动 UI**：JS 引擎执行业务逻辑后，产生 UI 更新指令（如 React 的 Virtual DOM Diff），通过**桥接层**发送给渲染引擎。
2. **事件反馈**：渲染引擎捕捉到用户的物理触摸事件，将其包装后传递回 JS 引擎，触发对应的 JS 回调函数。
3. **布局计算(关键点)**：
    - 在 **WebView** 中，布局计算完全由渲染引擎（如 WebKit）内部完成。
    - 在 **React Native** 中，JS 引擎不负责布局，它将样式信息发给 **Yoga 引擎**（C++ 实现的 Flex 布局引擎），Yoga 计算出精确坐标后，再交给原生系统进行渲染。

## FlexBox 是什么跨端方式，为什么可以不依赖 JS 引擎？

Flexbox 是一个跨平台的动态布局框架。可以做到一次布局，多端使用。 相比 RN、Weex 等框架，MTFlexbox 实现更轻量，运行更稳定，加载渲染速度更快。

MTFlexbox 不对 JS 引擎产生依赖，布局的解析、创建和渲染均基于双端的 Native API ，在性能以及稳定性上有天然的优势。

RN / Weex 的方式：
DSL/JSX → JS 引擎执行 → Bridge → Native 组件
> JS 引擎这一层是性能瓶颈，也是不稳定的来源。

MTFlexbox 的方式：
DSL 模板 → Native 解析器直接解析 → FlexBox 算法 → Native 组件
> 纯 Native 处理，没有 JS 引擎这一层。

**DSL 模板是什么**

开发者写的不是 Java/Kotlin/Swift 代码，而是一份**声明式的布局描述文件**，就是一份**纯粹的布局描述**，双端都能读懂。

```xml
<FlexBox direction="row" justifyContent="center">
    <Image src="{{imageUrl}}" width="100" height="100"/>
    <FlexBox direction="column" flex="1">
        <Text text="{{title}}" fontSize="16" color="#333"/>
        <Text text="{{subTitle}}" fontSize="12" color="#999"/>
    </FlexBox>
</FlexBox>
```

| | RN / Weex | MTFlexbox |
|------|------|------|
| **跨端的载体** | JS 代码 | DSL 模板文件 |
| **解析方式** | JS 引擎执行 | Native 直接解析 |
| **业务逻辑** | JS 写，JS 引擎执行 | Native 写 |
| **布局计算** | Yoga（C++） | Flexbox 算法（C++） |
| **最终渲染** | Native 组件 | Native 组件 |
| **跨端范围** | UI + 业务逻辑 | 只有 UI 布局 |
Flexbox 跨端的本质是：**用一份 DSL 模板描述 UI 布局，双端 Native 各自解析渲染**，完全绕过了 JS 引擎这一层，所以比 RN/Weex 更轻量、更稳定。它只解决了 **UI 布局跨端**的问题，业务逻辑跨端不在它的范围内。

## RN、Flutter、MTFlexBox 三者的区别是什么

**核心定位**

| | RN | Flutter | MTFlexBox |
|------|------|------|------|
| **出品方** | Meta | Google | 美团 |
| **核心定位** | 业务逻辑 + UI 跨端 | 业务逻辑 + UI 跨端 | 仅 UI 布局跨端 |
| **开发语言** | JavaScript / TypeScript | Dart | DSL 模板 + Native |
| **业务逻辑** | JS 编写 | Dart 编写 | 各端 Native 编写 |

---

**渲染方式**

这是三者**最本质的区别**：

```
RN：
  JS 代码 → JS 引擎 → Bridge → 调用 Native 组件渲染
                                    ↑
                           依赖系统的 TextView / UILabel 等

Flutter：
  Dart 代码 → Dart VM → Skia/Impeller 自己绘制每个像素
                                    ↑
                           只向系统要一块画布，其余全自己画

MTFlexBox：
  DSL 模板 → Native 解析器 → Flexbox 算法 → 调用 Native 组件渲染
                                    ↑
                           和 RN 一样依赖 Native 组件，但没有 JS 引擎
```

---

**详细对比**

| | RN | Flutter | MTFlexBox |
|------|------|------|------|
| **渲染方式** | 依赖 Native 组件 | 自绘引擎（Skia） | 依赖 Native 组件 |
| **是否需要 JS 引擎** | ✅ 需要 | ❌ 不需要 | ❌ 不需要 |
| **UI 一致性** | 各端可能有差异 | 像素级完全一致 | 各端可能有差异 |
| **接近原生体验** | ✅ 是（用原生组件） | ⚠️ 需要模拟 | ✅ 是（用原生组件） |
| **业务逻辑跨端** | ✅ 支持 | ✅ 支持 | ❌ 不支持 |
| **UI 布局跨端** | ✅ 支持 | ✅ 支持 | ✅ 支持 |
| **性能** | 中（Bridge 开销） | 高（自绘，无 Bridge） | 高（纯 Native） |
| **包体积** | 中 | 大（带渲染引擎） | 小 |
| **稳定性** | 中（JS 引擎风险） | 高 | 高 |
| **上手难度** | 中（会 JS 即可） | 高（需学 Dart） | 低（DSL 简单） |
| **动态化能力** | ✅ 强（JS 动态下发） | ⚠️ 弱 | ✅ 中（DSL 下发） |

---

**适用场景**

```
RN
├── 需要业务逻辑跨端
├── 团队有 JS/React 背景
├── 需要动态下发更新
└── 典型案例：Facebook、微软 Office

Flutter  
├── 追求极致 UI 一致性
├── 复杂动画和自定义 UI
├── 新项目从零开始
└── 典型案例：闲鱼、豆包、Google Pay

MTFlexBox
├── 已有 Native 项目，只想让 UI 布局跨端
├── 不想引入 JS 引擎，追求轻量稳定
├── 业务逻辑复杂，不适合迁移到 JS
└── 典型案例：美团 App 内的动态卡片
```

---

**类比总结**

```
MTFlexBox  ≈  只解决"排版"问题，像一个跨端的布局模板引擎
RN         ≈  前端开发者用 JS 写 App，运行时翻译给 Native
Flutter    ≈  完全自己造了一套 UI 体系，和 Native 几乎无关
```

> 💡 **一句话区分**
> - **MTFlexBox**：只管怎么排版，业务你自己写。
> - **RN**：用 JS 把业务和 UI 都跨端了，但依赖 Native 组件渲染。
> - **Flutter**：把业务和 UI 都跨端了，而且连渲染也自己做。Flutter **几乎完全脱离了 Android View 绘制机制**，它只向系统借了一块 `SurfaceView` 画布，所有的 UI 组件、布局计算、绘制、事件分发全部由 Flutter 引擎自己接管，直接操作 GPU 渲染像素，和 Android View 那套 measure/layout/draw 流程完全无关。

