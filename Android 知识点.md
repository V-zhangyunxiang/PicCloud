# Android 知识点

[TOC]

## <span id="toc">Android</span>

### Activity 启动流程

1.  系统解析 Intent，确定要启动的 Activity
2.  调用 startActivity() 或 startActivityForResult() 方法，传递 Intent 对象
3.  通过 binder 跨进程调用系统进程中的 AMS，AMS 接收到启动请求后，会执行一系列检查，根据启动模式和 flag 来决定是否创建新的 Task 栈，通知应用进程
4.  如果应用进程还未启动，系统将通过 Zygote 进程孵化出一个新的应用进程，并在新进程中加载目标 Activity
5.  ActivityThread(主线程)通过 Handler 机制接收到启动 Activity 的消息，依次调用 onCreate()、onStart()、onResume() 等生命周期方法，完成 Activity 的界面绘制和显示

![image](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/Activity%20%E5%90%AF%E5%8A%A8.png?raw=true) 

### onCreate 和 onRestoreInstance 方法中恢复数据时的区别

onSaveInstanceState 在系统可能会在销毁 Activity 时调用以保存当前 UI 状态

onCreate 中的 Bundle 可能为空，使用时需要判空。

onRestoreInstance 只有 activity 重建时，才会调用 ，所以它的 bundle 一定不为空。

onCreate 中往往需要做一些初始化的工作，而 onRestoreInstance 在 onStart 后 onResume() 前被调用，所以等初始化完毕再恢复数据相对方便

### Activity A 跳转 Activity B，再按返回键，生命周期执行的顺序

Activity A 的 onPause() → Activity B 的 onCreate() → onStart() → onResume() → Activity A 的 onStop()

如果 B 是透明主题或是个 DialogActivity，则不会回调 A 的 onStop

Activity B 的 onPause() -> ActivityA 的 onRestart() - onStart() - onResume() -> Activity B 的 onStop() - onDestroy()

### onStart 和 onResume、onPause 和 onStop 的区别

onStart 表示 Activity 可见，但不可交互

onResume 表示获取到焦点，可以与 Activity 交互

onPause 表示 Activity 失去焦点，但仍然可见

onStop 表示完全不可见，存在失去焦点但仍可见的情况，如非全屏 dialog

### Activity 之间传递数据的方式 Intent 是否有大小限制；如果传递的数据量偏大，有哪些方案

Intent 传递数据大小的限制大概在 1M 左右，超过这个限制就会崩溃，Intent 数据是通过 Binder 机制进行跨进程通信的，而 Binder 的事务缓冲区具有一个固定的大小限制，通常是 1MB

文件、SQLite 数据库、EventBus、AIDL 等可以传输大型数据

### Activity 的启动模式和使用场景

### Activity 的 onNewIntent() 方法什么时候会执行

standard : 标准模式，也是默认模式。不管这个实例是否已经存在，系统都会相应的创建一个实例。

singleTop：栈顶复用模式。启动的 Activity 已经处于栈的顶部，此时不会创建新的实例，而是复用当前实例对象，同时它的 onNewIntent() 方法会被执行，此时 Activity 的onCreate()，onStart() 方法不会被调用。如果不在栈顶，那就创建一个新的实例。

singleTask：栈内复用模式。如果栈中存在这个 Activity 的实例，不管它是否位于栈顶，都会复用这个 Activity，复用时，会将它上方的 Activity 全部出栈，回调该实例的 onNewIntent() 方法。这里还涉及一个任务栈的分配，设置 taskAffinity 属性，默认为包名，相同的 taskAffinity 会在同一个任务栈，不同时则在不同栈中。

singleInstance：单实例模式。Activity 会单独占用一个 task 栈，具有全局唯一性，即整个系统中就这么一个实例。

在 onNewIntent() 方法中，需要先手动调用 setIntent(intent) 更新 Activity 的 Intent，确保之后调用 getIntent() 能够获取到是最新的 Intent 数据。

### 显式启动和隐式启动

显示：直接指定要跳转的 Activity 类名，适用于同一个应用中的不同 Activity 跳转

隐式：通过 IntentFilter 匹配 Activity，一个 Intent 只有同时匹配某个 Activity 的 intent-filter 中的 action、category、data 才算完全匹配，常用于不同应用 Activity 的跳转

### Android Scheme 使用场景，协议格式，如何使用

是一种页面内跳转协议，通过定义自己的 scheme 协议，可以非常方便跳转 app 中的各个页面

**场景**

H5 跳转原生页面

Push 跳转

短信 URL 跳转

APP 根据 URL 跳转到另外一个 APP 指定页面

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
    <data android:scheme="urlscheme", android:host="auth_activity">
 </intent-filter> 
 
```

上面的 Scheme 协议为：urlscheme://auth\_activity

### Android 系统为什么会触发 ANR，它的机制是什么

当应用程序在处理用户交互或执行操作时长时间没有响应，系统会认为该应用已经无响应

*   用户输入事件(如触摸事件)，在 5 秒钟内没有处理完毕
*   BroadcastReceiver，在其 onReceive 方法中，10 秒钟内没有执行完成也会触发 ANR
*   一个前台服务在特定时间内没有处理完请求，通常是 20s

### Activty 间(传递/存储)数据的方式

1.  Intent (Serializable + Parcelable 传递对象)

2.  通过静态全局变量传递，但要注意静态变量的生命周期，不使用后，及时的将全局变量置为 null

3.  SharedPreferences

4.  SQLite 数据库

5.  File 文件

6.  广播

### 跨 App 启动 Activity 的方式，注意事项

*   知道准确类名信息时，直接显示启动，确保目标 Activity 设置了 android\:exported="true"

*   隐式 Intent 启动

注意目标 App 是否已安装，是否需要特定权限启动

### 有哪些 Activity 常用的标记位 Flags

FLAG\_ACTIVITY\_NEW\_TASK + FLAG\_ACTIVITY\_CLEAR\_TOP = singleTask

FLAG\_ACTIVITY\_SINGLE\_TOP  = singleTop

### Service 的生命周期，两种启动方式的区别

startService: onCreate() -> onStartCommand() -> onDestory

bindService: onCreate() -> onBind() -> onUnBind() -> onDestory

startService + bindService: onCreate() -> onStartCommnad() -> onBind() -> onUnBind() -> onDestory

startService 的 onCreate() 只会被调用一次，多次调用 startSercie 会执行多次 onStartCommand() 方法。调用 stopService() 或 stopSelf() 方法停止

bindService 的 onCreate()、onBind() 只会被调用一次，多次调用不会重复绑定。调用 unbindService() 方法后会执行 onUnbind() -> onDestroy()。

一个 Service 绑定了多个 Activity 时，只有当所有的 Activity 执行了 stopService/unBindService 后，Service 才会停止

### 能否在 Service 开启耗时操作

Service 默认执行在主线程，不能在 Service 里做耗时操作，如果必须，开一个子线程来处理耗时操作，如 Thread、IntentService

### Service 与 Activity 怎么实现通信

*   使用 Binder 对象。当 Activity 通过 bindService(Intent, ServiceConnection, int) 方法绑定 Service 时，Service onBind() 方法需要返回一个实现了 IBinder 接口的类对象，Activity 通过 ServiceConnection 的 onServiceConnected() 回调获取 IBinder 对象，通过该对象获取 Service 对象，然后在 Activity 中通过 Service 对象调用 Service 中的方法

*   Intent。在 Activity 中创建 Intent 对象，设置 Intent 参数，将 Intent 传递给 Service，Service 中可以通过 onStartCommand 方法或 onBind 方法获取 Intent 对象，并获取传递的数据

*   接口。Service 定义接口，Activity 实现接口监听方法，Service  回调。和单纯使用 Binder 对象相比，接口方式 Service 可以主动通知 Activity

*   广播

*   LiveData、EventBus 等

### IntentService 是什么，原理是什么，与 Service 的区别

用来进行处理异步请求的服务。

内部有一个工作线程，所有的任务都会放到一个队列中按序执行，在处理完所有任务后 IntentService 会自动停止

**与 Service 的区别**

IntentService 内部已经实现了线程，Service 需要自己创建线程

IntentService 在任务完成后会自动停止，Service 需要手动停止

IntentService 只能处理异步任务，Service 可以处理同步任务和异步任务

### Service 的 onStartCommand 方法有几种返回值，各代表什么意思

**START\_STICKY**

Service 被杀死后，会尝试重新创建该 Service，并调用 onStartCommand 方法，但不会重新传递 Intent

**START\_NOT\_STICKY**

Service 被杀死后，不会重启该 Service

**START\_REDELIVER\_INTENT**

同 sticky，但系统会保留最后一次传入 onStartCommand 方法中的 Intent，重启 Service 后，onStartCommand 会拿到该 Intent

### Service 如何保活

1. 在 onStartCommand 方法中返回 START\_STICKY

2. 提高 Service 的优先级

​       在 intent-filter 中可以设置 android\:priority= "1000" 属性提升优先级，1000 是最高值，如果数字越小则优先级越低，也适用于广播

3. 提升 Service 进程的优先级

​       前台进程 一 可视进程 一 服务进程 一 后台进程 一 空进程

​      使用 startForeground 将 Service 变成前台进程

4. 双进程守护

### 广播的分类和使用场景

普通广播: 调用 sendBroadcast() 发送，所有的广播接收者几乎会在同一时刻接收到广播信息，接收的先后顺序随机

有序广播: 调用 sendOrderBroadcast() 发送，广播接收者按优先级接收，优先级相同时，动态注册的优先；先收到的接收者可以对广播截断和修改

本地广播: LocalBroadcastManager 仅在自己的应用内发送接收广播，也就是只有自己的应用能收到，但只能采用动态注册的方式。使用 Handler 实现，利用 Intnet-Filter 匹配过滤

粘性广播: 调用 sendStickyBroadcast 发送的广播会一直滞留，有接收者注册时就会收到该广播。此方法在 API 级别 21 中已弃用

### [广播的两种注册方式的区别](#toc)

**静态注册**

常驻系统，不受组件生命周期影响，即便应用退出，广播还是可以被接收

缺点: 耗电、占内存

**动态注册**

跟随组件的生命变化，组件结束，广播结束

注意点：在组件结束前，需要先移除广播，否则容易造成内存泄漏

### 广播发送和接收的原理

1.  广播接收者向 AMS 注册

2.  广播发送者向 AMS 发送广播

3.  AMS 根据广播条件，寻找对应的广播接收者，将广播发送到该接收者的消息队列中

4.  接收者通过消息循环拿到广播，并回调 onReceive() 方法

### 什么是 ContentProvider

Android 系统提供的用于在不同应用程序之间共享数据的机制

*   允许一个应用程序将其数据暴露给其他应用程序访问和操作
*   提供了对数据增、删、改、查等操作的统一接口

**大致步骤**

创建：创建一个继承自 ContentProvider 的子类，并实现相应的方法来处理数据操作

注册：在 AndroidManifest.xml 文件中注册该内容提供者

获取：其他应用程序通过 ContentResolver 来获取内容提供者并进行数据操作

### ContentProvider、ContentResolver、ContentObserver 之间的关系

ContentProvider: 管理数据，实现各应用程序间的数据共享，提供对数据的增删改查操作

ContentResolver: 获取数据，为不同的 URI 操作不同的 ContentProvider 中的数据，外部进程可以通过 ContentResolver 与 ContentProvider 进行交互

ContentObserver: 观察 ContentProvider 中的数据变化

### ContentProvider 的优点

*   数据共享
*   统一的访问接口
*   访问的权限控制

### Uri 是什么

用于标识资源的字符串

唯一标识资源：可以准确地标识数据、文件、网络地址等各种资源
提供访问路径：通过 Uri 可以确定访问资源的具体路径和方式

### RecyclerView 的多级缓存机制，每一级缓存具体作用是什么，分别在什么场景下会用到哪些缓存

**屏幕内缓存**

包含 mAttachedScrap 和 mChangedScrap

mAttachedScrap 存储部分滑出屏幕但尚未完全离开的 ViewHolder

mChangedScrap 存放数据已经更改但还未重绘的 ViewHolder

结构: ArrayList\<ViewHolder>

性能: 复用 ViewHolder 重新绑定数据

**屏幕外缓存**

mCachedViews 缓存完全移除屏幕的 ViewHolder

根据 ViewType 和排序复用 ViewHolder，mCachedViews  默认缓存大小为 2，超出后最旧的 ViewHolder 会移到 RecyclerViewPool 中

结构: ArrayList\<ViewHolder>

性能: 复用 ViewHolder 重新绑定数据

**自定义的缓存**

mViewCacheExtension

针对特定应用场景优化缓存策略

**缓存池**

RecyclerViewPool

根据 ViewType 复用 ViewHolder，每种 type 存储最大值 5

结构: SparseArray 结构，key 是 ViewType，value 是 ViewHolder

性能: 复用 ViewHolder 且重新绑定数据

### RecyclerView 滑动回滚时的复用过程

RecyclerView 查看其 mCachedViews 缓存列表，如果列表中有与新位置类型匹配且未被其他地方使用的 ViewHolder，则复用，没有则查看 mAttachedScrap 中是否有可复用的，有则复用，没有则从 RecycledViewPool 中寻找，有则复用，没有重新创建一个新的 ViewHolder

### RecyclerView 与 ListView 的区别

**性能差异**

RecyclerView 使用 ViewHolder 和多级缓存提高了性能，ListView 需要通过 setTag 手动缓存且只有二层缓存

**刷新方式**

RecyclerView 支持局部刷新，而 ListView 不支持局部刷新

### RecyclerView 性能优化

1. 在 onBindViewHolder 中将视图绑定和数据处理分离，避免执行耗时操作，确保 UI 线程流畅。

2. 分页加载数据。

3. 使用局部刷新代替全局刷新。

```java
  DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallBack(oldDatas, newDatas), true);
  
  diffResult.dispatchUpdatesTo(mAdapter);
```

4. RecyclerView 列表包含图片时，滑动时不加载，滑动结束时恢复加载。
5. 加载更多时提前预加载固定数量的 item。
6. 避免在每个 item 中都创建新的监听器，例如 OnClickListener，可以全局初始化时创建一个。
7. 当 Item 的高度是固定的，使用 RecyclerView\.setHasFixedSize(true) ，当 adapter 内容改变时，避免布局重新测量。
8. 非必要关闭动画。
9. 加大缓存 mCacheViews 缓存数量。

### Fragment 的生命周期 & 结合 Activity 的生命周期

onAttach() → onCreate() → onCreateView()  → onViewCreated() → onActivityCreated() → onStart() → onResume() → onPause() →onStop() → onDestroyView() → onDestroy() → onDetach()

onAttach()

当 Fragment 和 Activity 建立关联时调用

onCreateView()

当 Fragment 创建视图调用，在 onCreate 之后

onViewCreated() 

在 `onCreateView()` 返回后并且视图层次结构已附加到屏幕上时调用

onActivityCreated()

当与 Fragment 相关联的 Activity 完成 onCreate() 之后调用

onDestroyView()

在 Fragment 中的布局被移除时调用

onDetach()

当 Fragment 和 Activity 解除关联时调用

### Activity 和 Fragment 的通信方式，Fragment 之间如何进行通信

1.setArguments(Bundle)

2.接口回调

3.共享 ViewModel

4.EventBus

### 为什么使用 Fragment.setArguments(Bundle) 传递参数

构造方法传递方式在 Fragment 重建时数据会丢失，而通过 setArguments 方式设置的 Bundle 数据会保留下来

### FragmentPageAdapter 和 FragmentStatePageAdapter 区别及使用场景

都继承 PagerAdapter，都会走 onDestroyView 销毁视图

FragmentPageAdapter 不会走 onDestroy、onDetach，页面存储在内存中，适用于页面较少的场景

FragmentStatePageAdapter 会走 onDestroy、onDetach，完全销毁 Fragment，适用于多页面场景

### Fragment 懒加载

在 add + show + hide 模式下使用 onHiddenChanged() 判断需要加载的 Fragment

> show、hide 方法控制 Fragment 时，Fragment 生命周期将不执行，通过 onHiddenChanged 来回调

在 ViewPager+Fragment 模式下使用 setUserVisibleHint() 判断需要加载的 Fragment

> ViewPager offScreenLimit 设置小于 1 时，内部会强制转为 1；大于 1 生效，提前加载 limit 个 Fragment 到 onResume 阶段，生命周期如下

> setUserVisableHint() -> onAttach() ->onCreate() ->onCreateView() -> onActivityCreated() -> onStart() -> onResume()

### ViewPager2 与 ViewPager 区别

*   ViewPager2 内部基于 RecyclerView 实现，ViewPager 内部并未使用已有的成熟控件，更多的是自定义的操作
*   ViewPager2 默认实现了懒加载，而 ViewPager 会进行预加载，懒加载需要自己去实现
*   ViewPager2 支持垂直方向滑动，而 ViewPager 仅支持水平方向滑动
*   ViewPager2 被声明成了 final，不可继承修改

### ViewPager 实现无限循环

*   将 getCount 返回值设为无限大的值，制造假循环，通过 Handler 延迟发送消息开启循环，触摸 ViewPager 时要停止发送

*   首尾各插入一条数据

### 动画的类型

帧动画

*   通过顺序播放一系列图片来实现动画效果

补间动画

*   位移、缩放、旋转、透明

属性动画

*   用于与属性相关、更加复杂的动画效果

### 补间动画和属性动画的区别

属性变化不同，补间动画只是改变显示效果，不会改变 View 的属性，而属性动画会改变对象的属性

作用对象不同，补间动画只能作用在 View 上，属性动画可以作用在任意对象上

扩展性不同，补间动画只能实现位移、缩放、旋转和透明度四种动画操作，而属性动画能实现自定义属性变化

### ObjectAnimator，ValueAnimator 的区别

ObjectAnimator 继承自 ValueAnimator，直接与对象属性绑定，自动更新属性值

ValueAnimator 需要手动控制动画进度

### TimeInterpolator 插值器，自定义插值器

插值器用于控制动画的执行速率

Android 系统提供了一些内置的插值器，LinearInterpolator（匀速）、AccelerateDecelerateInterpolator（先加速后减速）等

可以通过实现 TimeInterpolator 接口来自定义插值器

```java
public class MyInterpolator implements TimeInterpolator {
    @Override
    public float getInterpolation(float input) {
        // 在这里自定义动画执行速率的算法
        return input; 
    }
}
```

### TypeEvaluator 估值器

计算动画过程中属性值的变化

Android 内置了几种实现

IntEvaluator: 用于计算整数值的变化
FloatEvaluator: 用于计算浮点数值的变化
ArgbEvaluator: 专门用于颜色值（ARGB）的插值计算，能够平滑地在两种颜色间过渡

**自定义 TypeEvaluator**

1.  创建类并实现 TypeEvaluator&lt;T&gt; 接口：T 是你想要进行插值计算的数据类型
2.  重写 evaluate 方法
3.  应用自定义 TypeEvaluator

### Bitmap 内存占用的计算

Bitamp 占用内存 = 宽 \* (inTargetDensity/inDensity) \* 高 \* (inTargetDensity/inDensity) \* 一个像素占用的内存

inTargetDensity 为当前设备系统密度 DPI

inDensity 默认为图片所在文件夹对应的密度 DPI

**Bitmap.Config 图片编码方式**

ARGB8888 每个像素占用 4 个字节，存储透明度和颜色信息

RGB565 每个像素占用 2 个字节，只存储颜色信息，没有透明度

### getByteCount() & getAllocationByteCount() 的区别

getByteCount: 代表存储 Bitmap 的像素需要的最少内存

getAllocationByteCount: 代表在内存中为 Bitmap 分配的内存大小

正常两者是相等的，但当 bitmap 是被复用的，getByteCount 表示的是图片占用的内存大小(非实际内存大小)，getAllocationByteCount 表示的是被复用 bitmap 真实占用的内存大小

### Bitmap 的压缩方式

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

质量压缩不会减少图片的像素，所以其占内存大小是不会改变，但图片磁盘大小会变小，常用在上传限制大小的场景。

png 图片是无损的，不能进行质量压缩

**重新采样**

将 BitmapFactory.Options 的 inJustDecodeBounds 参数设为 true 并加载图片，当 inJustDecodeBounds为 true 时，BitmapFactory 只会解析图片的原始宽高信息，并不会真正的加载图片

从 BitmapFactory.Options 取出图片的原始宽高信息，跟 ImageView 的宽高比较后计算出合适的采样率 inSampleSize，赋值到 Options，然后将 BitmapFactory.Options 的 inJustDecodeBounds 参数设为 false 并重新加载图片

**缩放压缩**

使用 Matrix 矩阵对图像进行缩放

```java
    private Bitmap compressMatrix() {
        Matrix matrix = new Matrix();
        matrix.setScale(0.5f, 0.5f);
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.test);
        Bitmap result = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        return result
    }
```

**RGB565**

减少图片单位像素所占的字节数，减少内存大小

**createScaledBitmap**

将图片压缩成期望的大小，来减小内存

```java
    private Bitmap compressWithCreatedScaledBitmap(Bitmap bitmap, int dstWidth, int dstHeight) {
    Bitmap result = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true);
    return result;
    }
```

### LruCache & DiskLruCache

LruCache 是系统提供的一个内存缓存类

基于 LRU 算法，当缓存满时自动移除最近最少使用的条目

内部采用一个 LinkedHashMap 以强引用的方式存储外界的缓存对象，提供 get 和 put 方法来完成缓存的获取和添加操作，当缓存满时会移除最早使用的缓存对象，再添加新的缓存对象

DiskLruCache 是一个磁盘缓存实现，不直接属于 Android SDK

### 图片文件夹加载优先级

Android 系统定义的屏幕像素密度基准值是 160dpi，该基准值下 1dp 就等于 1px，依此类推 320dpi 下 1dp 就等于 2px

记载图片时根据设备的分辨率先去对应 DPI 的文件夹寻找，如果该文件夹内没找到，优先从**高密度**文件夹寻找

![image.png](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/%E5%88%86%E8%BE%A8%E7%8E%87dpi.png?raw=true) 

例如对于 320dpi 的设备来说，应用在选择图片时就会优先从 `drawable-xhdpi` 文件夹拿，如果该文件夹内没找到图片，就会依照 `xxhdpi -> xxxhdpi -> hdpi -> mdpi -> ldpi` 的顺序进行查找，优先使用高密度版本，然后从中选择最接近当前屏幕密度的图片资源

### MVC & MVP & MVVM

MVC

*   Activity/Fragment 除了 View 层的处理还包括业务逻辑，造成 V 层代码膨胀，复用性差

MVP

*   通过 Presenter + DataManager 组合将 View 和业务逻辑分离，P 层持有 View 和 DataManager 层的对象，DataManager 层持有 Model 层的接口对象，简化了 View 层

MVVM

*   使用 ViewModel 代替 Presenter，通过数据绑定的方式连接 View 和 Model，View 订阅 ViewModel 的数据源，当数据发生变化时，ViewModel 通过订阅通知 View 刷新视图。与 MVP 相比进一步简化了 P 层，解耦程度更高，生命周期管理更自动化。

### APK 打包流程

aapt 打包资源文件，生成 R.java 文件

aidl 处理 AIDL 文件，生成对应的 .java 文件

javac 编译 Java 文件，生成对应的 .class 文件

.class 文件被 D8 转化为 .dex 文件

apkbuilder 打包生成未签名的 .apk 文件

apksigner 对未签名 .apk 文件进行签名

zipalign 对签名后的 .apk 文件进行字节对齐处理

### Android 的签名机制，V2 相比于 V1 签名机制的改变

Android 的签名机制是用来确保应用程序的完整性和来源可靠性的

v2 签名机制相对于 v1 来说增强了安全性和验证效率，开发者在构建 APK 时往往会选择同时使用 v1 和 v2 签名，以确保应用能在各种Android 版本的设备上正常安装和运行。

### 系统启动流程

1. Linux 内核启动
2. init.rc 文件启动 init 进程，并启动 Zygote 进程，Zygote 会创建一个服务器端 Socket，监听来自 SystemServer 的请求
3. Zygote 孵化 SystemServer 后并启动，初始化各个系统服务
4. Home Launcher 应用程序将被启动，展示出用户界面

### SystemServer，ServiceManager，SystemServiceManager 之间的关系

- SystemServer 是 Android 系统中的一个重要的后台进程，它由 Zygote 进程孵化而来，是  Zygote  的第一个子进程。它的主要职责是启动和管理一系列系统服务
- ServiceManager  是一个全局的系统服务注册和查询中心，负责维护一个服务名称到服务实例的映射表。SystemServer 会通过 `ServiceManager.addService()` 方法将服务注册到 `ServiceManager` 中，需要使用这些服务的组件就可以通过 `ServiceManager.getService()` 获取服务的代理对象，进而调用服务提供的接口
- `SystemServiceManager` 是 `SystemServer` 内部用来管理和启动系统服务的组件，SystemServer 生命周期由 SystemServiceManager 控制

### 孵化应用进程为什么不交给 SystemServer 来做，而专门设计一个 Zygote

- 资源复用。Zygote 在启动时就加载了大量的系统资源和共享库，后续应用进程可以通过 fork 从 Zygote 中快速创建，避免重复加载资源，提高了性能
- 安全和隔离。`SystemServer` 专注于系统层面的服务管理，`Zygote`设计为一个非常干净、基础的服务，有助于实现安全隔离

### Zygote 的 IPC 通信机制为什么使用 socket 而不采用 binder

*   为了避免在 fork 子进程后可能出现的死锁问题
*   Socket 通信模型相对简单可靠，对于这类简单的请求-响应模式足够使用，无需引入 Binder 的复杂性

### 什么是序列化，为什么需要序列化和反序列化

**概念**

序列化是将对象转换为可以写入磁盘或在网络上传输的字节流；反序列化是从磁盘或网络读取字节流，并将其转换回原始对象

**为什么需要**

一是数据持久化，可以将对象序列化并保存到磁盘或数据库中

二是数据传输，将对象序列化后方便在不同的端之间传输

### Serializable 和 Parcelable 的区别

Serializable 是 Java 自带的方式，在硬盘中读写，通过反射序列化对象，产生大量临时对象会导致频繁的 GC。但实现简单，稳定性高，适合长期存储数据

Parcelable 是 Android 自带的方式，在内存中读写，性能优于 Serializable。但实现复杂，不适合长期存储

### 什么是 serialVersionUID，为什么要显示指定 serialVersionUID 的值

serialVersionUID 用来判断序列化对象的版本是否一致

当没有指定 serialVersionUID 的值的时候，JVM 会根据类结构自动生成 serialVersionUID，但相对耗时

自动计算的 `serialVersionUID` 可能因编译器或开发环境的不同而有所差异

### Art & Dalvik 及其区别

Art 和 Dalvik 是两种不同的运行时环境

4.4.4 以前，Android 使用 Dalvik 虚拟机，应用的每次运行都需要即时编译，安装速度快。缺点是 CPU 使用频率高，应用响应速度慢

5.0 开始，Android 使用 ART 虚拟机，应用在首次安装时用就将字节码转换为机器码，避免了后续重复编译，降低了 CPU 使用频率，应用响应速度快，已成为主流。缺点是安装时间更长、存储空间占用更大

### 什么是模块化和组件化，它们的区别是什么

**模块化**

是将整个项目按照不同的功能或业务逻辑划分为相对独立的模块，每个模块有自己明确的职责和边界，模块之间通过接口进行交互

**组件化**

是将应用拆分成可独立开发、测试、部署和维护的组件，这些组件可以在不同的项目中复用，并且能够灵活组合和配置

**区别**

组件化的粒度一般比模块化更细，组件关注单一功能点，如登录组件、分享组件等。而模块通常包含一整套相关的功能和服务，如用户模块、订单模块等

组件化在设计上要求更高的解耦，以达到高度的可复用性

### 组件化项目，不同组件间如何通信和传递数据

- **路由系统** 

  使用路由框架（如 ARouter、Alibaba's Router等）来管理组件间的跳转和参数传递。通过 URL 或路径名导航到其他组件，并携带必要的参数

- **EventBus** 、**LiveData** 等

  通过发布和订阅方式，响应时间从而实现通信和传递数据

- **接口**

​       定义一组接口来封装组件间的调用，然后通过依赖注入（如 Dagger/Hilt）将实现这些接口的服务注入到需要通信的组件中

### ARouter 等路由通信的原理是什么 

编译期使用 APT 扫描特定注解，根据注解信息动态生成相应 Map 映射文件，运行时会加载这些映射文件到内存中，需要路由时，通过路径找到映射表中对应的信息，根据组件类型启动该组件

### Jetpack 架构组件(AAC)

AAC 中的 LifeCycle、LiveData、ViewModel 是 Google JetPack 推出的实现 MVVM 的架构组件。

     MyViewModel model = ViewModelProviders.of(this).get(MyViewModel.class);

**LiveData**

**作用**：用于存储和管理被观察的数据，当数据发生变化时，自动通知观察者。

**特点**

- 感知生命周期，仅在活跃的生命周期状态下更新数据
- 确保数据的一致性和实时性

**ViewModel**

**作用**：用于存储和管理与界面相关的数据，避免数据在屏幕旋转等情况下的丢失

**特点**

- 与特定的界面生命周期绑定
- 为界面提供数据，并在配置更改时保留数据

**LifeCycle**

**作用**：提供了组件（如 Activity、Fragment）的生命周期状态信息

**特点**

- 帮助开发者在正确的生命周期阶段执行相应的操作
```java
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

public class MyObserver implements LifecycleObserver {

private static final String TAG = "MyObserver";

@OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
public void onCreate() {
    Log.d(TAG, "onCreate event received");
}

@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
public void onDestroy() {
    Log.d(TAG, "onDestroy event received");
  }
}

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

public class MainActivity extends AppCompatActivity {

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // 获取 Lifecycle 对象并添加观察者
    getLifecycle().addObserver(new MyObserver());

    // 如果是在Fragment中，则使用 getLifecycle() 方法获取 Lifecycle 对象
    // lifecycle.addObserver(new MyObserver());
  }
}
```

### 框架原理

#### Okhttp 源码流程

1. **创建 OkHttpClient 实例**

​        首先需要创建一个 OkHttpClient 实例。在构建过程中，可以通过 Builder 模式设置各种参数，如超时时间、缓存策略、拦截器等

2. **构建 Request**

​        创建一个 Request 对象，它包含了请求的 URL、HTTP 方法（GET、POST 等）、请求头以及请求体等信息

3. **创建 Call 实例**

​       使用 OkHttpClient 的 newCall 方法，将 Request 对象转换成一个 Call 对象。Call 是一个抽象概念，代表一个待执行的 HTTP 请求

4. **执行请求**

- **同步请求**: 通过调用 Call 对象的 execute 方法来执行请求，此方法会阻塞直到响应返回或者抛出异常

-  **异步请求**: 通过调用 Call 对象的 enqueue 方法来发起异步请求，当请求完成或失败时，Callback 的相应方法会被调用

5. **拦截器链**

​      OkHttp 使用责任链模式来组织拦截器，使得每个拦截器都能有机会处理请求或响应。

​      举例：我要请假先由主管审核，小于 3 天主管处理，主管设置了下一个处理人，大于 3 天主管处理不了则传递给经理审核，依次类推

#### LeakCanary 源码流程

1. LeakCanary 使用 `ActivityRefWatcher` 监听 Activity 的 onDestory 生命周期，对可能泄漏的对象进行标记并监视，添加到弱引用队列中
2. 检查被监视对象的弱引用是否被清除，如果对象仍然存在，表明可能存在泄漏，LeakCanary 会进行二次检查，确保对象的确未被回收
3. 确认存在泄漏后，触发一次堆转储，查找从 GC Roots 到疑似泄漏对象的引用路径，自动生成一份详细的泄露报告

### 性能优化

* 内存

  * **集合类泄漏**
    List 添加元素后，仍引用着**集合元素对象**，导致该集合元素对象不可被回收

    集合类添加集合元素对象后，在使用后必须从集合中删除
    objectList.clear()
    objectList = null

  * **静态变量造成的内存泄漏(如单例)**

    尽量避免 Static 成员变量引用资源耗费过多的实例
    用弱引用（WeakReference）代替强引用持有实例
    若需引用 Context，则尽量使用 Application 的 Context

  * **非静态内部类/匿名类**
  
    如 Thread、Handler

  * **Thread**
  
    改为静态内部类
    在外部类销毁时强制结束线程 Thread.stop()
    
  * **Handler**
    改为静态内部类 + 外部类对象保存为弱引用
    在外部类销毁时清空 Handler 内消息队列

   * **资源未关闭**
  
      注册反注册、监听反监听等要成对出现

  * **分析泄露工具**
    LeakCanary


* 布局/绘制

  *   选择性能更高的布局，如约束布局

  *   减少布局的层级，如 ReleativeLayout 代替多层的 LinearLayout

  *   使用 &lt;include&gt; + &lt;merge&gt; 标签复用公共布局并减少布局嵌套

  *   使用 &lt;ViewStub&gt; 按需加载

  *   尽可能少用 wrap\_content，用固定宽高值

  *   onDraw 中避免创建局部对象和执行耗时操作

  *   移除默认的 Window 背景，移除多层级中的重复背景

* 启动速度

  - 延迟初始化不必要的资源。
   - 使用异步加载来处理耗时的初始化任务。
   - 减少应用启动时的依赖项和库加载

* 包体积

  * 资源/代码优化
    使用 Lint 查找并删除无用的资源和代码文件
    开启混淆和缩减资源
    将图片转换为 WebP 格式，使用 TinyPng 等工具压缩图片，只提供如 xxhdpi 分辨率的资源
    使用 shape、layer-list 等代码方式替代图片

  * 移除不必要的 so 文件

    只使用 armeabi-v7a 和 arm64-v8a 架构的 so 文件

  * 使用 H5 等替换原生页面

### 事件分发机制

![View 树结构](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/View%20%E6%A0%91.png?raw=true) 

Window 是一个抽象类，是所有视图的最顶层容器，视图的外观和行为都归他管，不论是背景显示，标题栏还是事件处理都是他管理的范畴

PhoneWindow 作为 Window 的唯一实现类

DecorView 是 PhoneWindow 的一个内部类，PhoneWindow 的指示通过 DecorView 传递给下面的 View，setContentView() 方法就是将我们写的布局添加到 id 为 content 的 FrameLayout 中

![image](https://github.com/V-zhangyunxiang/PicCloud/blob/master/data/%E4%BA%8B%E4%BB%B6%E5%88%86%E5%8F%91.png?raw=true) 

| 类型   | 相关方法                                 | Activity | ViewGroup | View |
| ---- | ------------------------------------ | :------: | :-------: | :--: |
| 事件分发 | dispatchTouchEvent                   |     √    |     √     |   √  |
| 事件拦截 | onInterceptTouchEvent                |     X    |     √     |   X  |
| 事件消费 | onTouchEvent(dispatchTouchEvent 内调用) |     √    |     √     |   √  |

这个三个方法均有一个 boolean 类型的返回值，通过返回 true 和 false 来控制事件传递的流程

事件往下传递流程

> Activity －> PhoneWindow －> DecorView －> ViewGroup －> ... －> View

如果没有任何 View 消费掉事件，那么这个事件会按照反方向回传，最终传回给 Activity，如果最后 Activity 也没有处理，本次事件会被抛弃

> Activity <－ PhoneWindow <－ DecorView <－ ViewGroup <－ ... <－ View

| 事件             | 简介                      |
| :------------- | :---------------------- |
| ACTION\_DOWN   | 手指 **初次接触到屏幕** 时触发      |
| ACTION\_MOVE   | 手指 **在屏幕上滑动** 时触发，会多次触发 |
| ACTION\_UP     | 手指 **离开屏幕** 时触发         |
| ACTION\_CANCEL | 事件 **被上层拦截** 时触发        |

####  View 分发流程

1. View dispatchTouchEvent()

2. 是否注册了 **`onTouchListener`** 

​      \-> 未注册 -> View onTouchEvent

​      -> 已注册 -> View\.onTouch

&#x20;                          \-> 返回 true -> 事件被消费，不再传递

&#x20;                          \-> 返回 false -> View onTouchEvent

#### ViewGroup 分发流程

1. ViewGroup dispatchTouchEvent()，`dispatchTouchEvent()`方法首先会调用 `onInterceptTouchEvent()` 来判断是否应该拦截事件
2. ViewGroup onInterceptTouchEvent() 判断是否拦截该事件

​       \-> onInterceptTouchEvent() 返回 true 表示拦截 -> ViewGroup onTouchEvent 来处理

​      -> onInterceptTouchEvent() 返回 false 表示不拦截-> ViewGroup 遍历所有的子 View 找到被触摸的 View，调用该 View 的        

​          dispatchTouchEvent

#### 为什么 View 会有 dispatchTouchEvent

View 可以注册很多事件监听器，如点击(onClick)、长按(onLongClick)，触摸(onTouch)，View 自身也有 onTouchEvent 方法，这些方法的执行顺序就是由 dispatchTouchEvent 分发管理

View 事件分发顺序：OnTouchListener.onTouch > onTouchEvent > onLongClickListener > onClickListener

#### 在 ViewGroup 中的 onTouchEvent 中消费 ACTION_DOWN 事件，ACTION_UP 事件是怎么传递

后续不会再向子控件传递 `ACTION_DOWN` 消息，会直接剩下事件传递给这个 `ViewGroup` 的 `onTouchEvent` 进行响应

#### 一个 View B、View A，View A 50% 透明度，是盖在 View B 的上面的蒙层，View B 上有一个 Button B，如何在点击 Button B 时有响应

设置 View A `android:clickable="false"` 或 `android:focusable="false"`，表明它不消耗触摸事件，那么触摸事件会穿透 View A 传递给下方的 View，Button B 就能接收到点击事件并正常响应

#### onTouchEvent 返回 true 和 false 时，对 View 点击事件的影响

返回 true， 表示触摸事件在此处被消费，不再继续向下传递给其他触摸监听器（如`OnClickListener`的`onClick`方法）或者子 `View`

返回 false，触摸事件有机会被传递给下一个潜在的事件处理器，可能之后的`onClick`监听器，或者是父 `View` 及其子 `View`

#### 同时对父 View 和子 View 设置点击方法，优先响应哪个

当点击事件发生时，会先传递到子 View 进行处理，如果子 View 的点击方法没有消耗该事件（即没有返回`true`），则事件会继续传递到父 View 进行处理。父`View`可以通过重写`onInterceptTouchEvent`方法来改变这一默认行为，主动拦截事件。

#### 滑动冲突

1. 外部拦截法，重写 ViewGroup 的 onInterceptTouchEvent 方法

```java
   public class MyExternalInterceptorLayout extends FrameLayout {

    public MyExternalInterceptorLayout(Context context) {
        super(context);
        init();
    }

    public MyExternalInterceptorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyExternalInterceptorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {}

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 这里可以根据滑动方向等条件判断是否拦截事件
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录开始滑动的位置
                startX = ev.getX();
                startY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float endX = ev.getX();
                float endY = ev.getY();
                float deltaX = Math.abs(endX - startX);
                float deltaY = Math.abs(endY - startY);

                // 如果水平滑动距离大于垂直滑动距离，则拦截事件
                if (deltaX > deltaY) {
                    return true; // 拦截事件，由父View处理
                }
        }
        return super.onInterceptTouchEvent(ev); // 不拦截，交给子View处理
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 在这里处理被拦截的触摸事件
        // 例如，你可以实现滑动逻辑
        return true; // 表示事件已经被处理
    }
}
```

2. 内部拦截法

子 View 的 `onTouchEvent` 中决定是否需要父 View 处理滑动

```java
public class MyChildView extends View {

    public MyChildView(Context context) {
        super(context);
        init();
    }

    public MyChildView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyChildView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true); // 初始按下时不希望父View拦截
                break;
            case MotionEvent.ACTION_MOVE:
                // 这里可以添加逻辑判断是否需要让父 View 重新开始拦截
                // 例如，如果判断出此次滑动是垂直滑动，而父 View 负责处理水平滑动，则可以不禁止拦截
                if (/* 滑动条件判断 */) {
                    getParent().requestDisallowInterceptTouchEvent(false); // 允许父View拦截
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false); // 释放后允许父View再次拦截
                break;
        }
        // 处理子View自己的触摸事件
        return true; // 表示事件被处理
    }
}
```

### 消息机制

#### 什么是 Handler，作用是什么

用于子线程与主线程间的通讯，把子线程中的 **UI更新信息传递** 给主线程更新  UI

#### 为什么要用 Handler，不用行不行

不行，Android 要求在主线程更新 UI，并且 Android UI 更新被设计成单线程，所以子线程想要更新 UI 需要通过 Handler 发送消息到主线程

#### 为什么 Android UI 更新不设计成多线程

多个线程同时对同一个 UI 控件进行更新，容易发生不可控的错误，想解决这个问题就需要加锁，但加锁意味着耗时，导致 UI 卡顿

#### 子线程能不能更新 UI

可以

*   在子线程更新在子线程创建的 View
*   在 onCreate 中可能可以更新，因为此时 ViewRootImp 还没创建，来不及执行 checkThread 方法检查线程安全

#### 子线程中更新 UI 的其它方式

**activity.runOnUiThread()**

**view.post() 与 view.postDelay()**

#### Handler 怎么用

sendMessage() 发送 + handleMessage() 接收

#### Message对象创建的方式有哪些 & 区别

new Message()

Message.obtain() 及其衍生方法

**区别**

obtain 通过**加锁和消息池**，避免重复创建实例对象，节约内存。消息池最大容量 50，被使用的消息会被打上 FLAG\_IN\_USE 的标志，当 message 被 looper 分发完后，会调用 recycleUnchecked() 方法，回收没有在使用的 Message 对象

#### Looper.prepare 都做了什么

创建 Looper 对象，将创建的 Looper 对象保存在 ThreadLocal  的 value 中，并在 Looper 构造方法中创建了一个 MessageQueue 对象。由此得出线程间 Looper 对象是互相隔离的，一个线程只有一个 Looper 和 MessageQueue，可以对应多个 Handler

#### 为什么子线程中不能直接 new Handler()，而主线程可以

主线程与子线程不共享同一个 Looper 实例，主线程的 Looper 在 ActivityThread 的 main 函数中就通过 prepareMainLooper() 完成了初始化，而子线程还需要调用 Looper.prepare() 和 Looper.loop()，才能创建 handler 对象

#### Handler 有哪些发送消息的方法，区别是什么，发送过程是什么

*   sendMessage
*   post

sendMessage 发送的是 Message 对象，post 发送的是 Runnable 对象。

post 发送的 Runnable 会转换成 Message，Runnable 会赋值给 msg 的 callback 变量，在 dispatchMessage 时
msg.callback 不为空时，说明是 Runnable 对象，执行 Runnable 回调

sendMessage 在 dispatchMessage 时优先级低于 post，创建 Handler 时如果传递了 mCallback，则通过 mCallback.handlerMessage 或回调，否则调用 handlerMessage() 方法

**发送过程**

> sendMessage(Message msg) -> sendMessageDelayed(Message msg, long delayMillis) -> sendMessageAtTime(Message  msg，long uptimeMillis 系统时间+延迟时间) -> enqueueMessage(MessageQueue queue, long uptimeMillis) -> queue.enqueueMessage

#### Looper.loop() 过程

获得当前线程 Looper 对象，从中取出 MessageQueue 队列，next() 方法死循环遍历队列中的 Message，通过msg.target.dispatchMessage(msg) 将消息分发给对应的 Handler，没有消息时堵塞并进入休眠释放 CPU 资源，有消息时再唤醒线程

#### Looper 如何区分多个 Handler 的

通过 msg.target 来区分，发送消息时 msg.target 被当前 Handler 赋值，分发消息时也是通过 msg.target 进行分发

#### handler postDealy 假设先 postDelay 10s，再 postDelay 1s，队列怎么处理这 2 条消息

尽管 10s 的消息先被添加，但因为其执行时间晚于 1s 的消息，所以它在队列中排在 1s 的消息之后，当 1s 延迟时间到达后，先处理 1s 的消息，等到 10s 消息时间到达后，再处理 10s 的消息

#### IdleHandler 及其使用场景

IdleHanlder 是 MessageQueue 类中一个 static 的接口，当 MessageQueue 中无可处理的 Message 时回调

使用: handler.loop.queue.addIdleHandler(IdleHandler idlehandler)

场景: 

#### Looper 在主线程中死循环，为啥不会 ANR

- 所有代码都在死循环中执行，即使是死循环也不会有影响，目的是保证了 main 函数无法退出，不然 app 一启动就退出了

- 当 MessageQueue 中当前没有消息需要处理时，也会依靠 Linux epoll 机制挂起主线程，释放 CPU 资源

死循环没有用 wait/notify 来堵塞，而是通过 Linux 的 **epoll 机制**，是因为需要处理 **Native 侧**的事件

#### Handler 导致的内存泄露原因及其解决方案

1.  非静态内部类会持有一个外部类的隐式引用，可能会造成外部类无法被回收。所以使用静态内部类避免内存泄露，并使用弱引用持有外部类的对象

2.  延时消息，Activity 关闭了但消息还没处理完。需要在 Activity 的 onDestroy() 中调用 handler.removeCallbacksAndMessages(null) 移除 Callback 和 Message

#### 同步屏障机制

 创建 Handler  时设置 **async** 为 true，将使用一个内部的线程池来处理消息，而不是在创建 `Handler` 的线程中处理。不影响消息的属性（始终发送的是异步消息）。

1. **queue.postSyncBarrier** 开启同步屏障，插入一个特殊的同步屏障消息，遍历消息时判断 msg.target 为 null 说明是屏障消息会优先处理后续的异步消息，屏蔽同步消息。

​    2. 通过 queue.removeSyncBarrier() 关闭同步屏障，恢复处理同步消息

#### Looper.quit/quitSafely 的区别

quitSafely 不再接收新的消息，等待队列中的消息全部处理完毕后，才会停止循环

quit 会立即停止循环，移除队列中的所有消息

#### ThreadLocal 是什么，有什么作用

ThreadLocal 是一个创建线程局部变量的类，该变量只能被当前线程访问，其他线程无法访问

内部数据结构为 ThreadLocalMap，key 是 ThreadLocal，value 是变量的值

#### 如何从主线程往子线程发送消息

1. **在子线程中准备 Looper 和 Handler**

   ```java
   class MyWorkerThread extends Thread {
       Handler mHandler;
       
       @Override
       public void run() {
           Looper.prepare(); // 准备Looper
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
           Looper.loop(); // 开始Looper的消息循环
       }
   }
   ```

2. **在主线程中发送消息到子线程的 Handler**

   ```java
   MyWorkerThread myWorkerThread = new MyWorkerThread();
   myWorkerThread.start();
   // 等待 Looper 准备好
   while (!myWorkerThread.mHandler.hasMessages(0)) {
       // 等待直到子线程的 Handler 可用
   }
   // 发送消息到子线程
   Message msg = new Message();
   msg.what = MY_MESSAGE_ID;
   msg.obj = "我是从主线程发送的消息";
   myWorkerThread.mHandler.sendMessage(msg);
   ```

### View 绘制

#### View 绘制流程

**基本概念**

视图的结构顺序是 Windows - PhoneWindow - DecorView。

PhoneWindow 继承自 Window，是 Window 的实现类，内部包含了一个 DecorView 对象。

DecorView 是顶层根视图，是一个 FrameLyaout，内部包含了一个竖向的 Linearlayout ，LinearLayout 内包含了一个 titlebar 和 ContentView。

绘制流程可以分成 2 个阶段，DecorView 的创建、渲染过程。

**创建过程**

调用 setContentView 后，先实例化 DecorView 对象，(创建过程中先设置 Window Style 的属性，这里也解释了为什么 requestFeature 要在 setContentView 之前调用），然后通过 pull 方式解析 XML，通过 createViewFromTag 创建各个视图对象，遍历所有的子 View，通过 addView 将整个 XMl 所有的 View 对象添加到 DecorView 的 ContentView 中。

**渲染过程**

1. ActivityThread.handleResumeActivity()

2. WindowManagerImpl.addView() 
3. WindowManagerGlobal.addView() 
4. 初始化 ViewRootImpl -> 初始化 Choreographer -> ViewRootImpl.setView()
5. requestLayout() ，checkThread 检查线程合法性
6. scheduleTraversals()，开启同步屏障，通过 Choreographer.postCallback 方法注册一个 mTraversalRunnable，监听 VSync 信号并在下一个 VSync 信号到达时执行 Runnable 回调，回调中调用 performTraversals 执行 onMeasure 测量，onLayout 布局，onDraw 绘制三个步骤

#### MeasureSpec 是什么

MeasureSpec 是一个有 32 bit 的 Int 值，高 2 位表示测量模式 Mode，低 30 位表示数值大小 Size

它有 3 种模式

**MeasureSpec.EXACTLY**

表示父控件已经确切的指定了子 View 的大小(数值或 match\_parent)

**MeasureSpec.AT\_MOST**

表示子 View 大小由自身内容决定，但不能超过父 View 大小(wrap\_content)

**MeasureSpec.UNSPECIFIED(不常用)**

默认值，父控件没有给子 View 任何限制，子 View 可以设置为任意大小

```java
// 1. 获取测量模式（Mode）
int specMode = MeasureSpec.getMode(measureSpec)
// 2. 获取测量大小（Size）
int specSize = MeasureSpec.getSize(measureSpec)
// 3. 通过 Mode 和 Size 生成新的 SpecMode
int measureSpec=MeasureSpec.makeMeasureSpec(size, mode);
```

**View 的大小(MeasureSpec)是由自身 LayoutParams 和父容器的 MeasureSpec 共同决定的**

![img](https://upload-images.jianshu.io/upload_images/944365-c474d16d76c4ee2e.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

#### 自定义 View wrap\_content 为什么不起作用

在 onMeasure 默认的实现中，当 View 的测量模式是 AT\_MOST 或 EXACTLY 时，View 的大小都会被设置成 MeasureSpec 的 specSize，specSize 为父容器当前剩余空间大小，所以 wrap\_content 看起来和 match\_parent 效果相同

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
    // 设置 wrap_content 的默认宽/高值
    // 默认宽/高的设定并无固定依据，根据需要灵活设置
    // 类似 TextView，ImageView 等针对 wrap_content 均在 onMeasure() 对设置默认宽/高值有特殊处理
    int mWidth = 400;
    int mHeight = 400;
  // 当布局参数设置为 wrap_content 时，设置默认值
    if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT && getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(mWidth, mHeight);
    // 宽/高任意一个布局参数为 = wrap_content 时，都设置默认值
    } else if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(mWidth, heightSize);
    } else if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
        setMeasuredDimension(widthSize, mHeight);
}
```

#### 为什么 onCreate 获取不到 View 的宽高

因为此时 View 还没有开始绘制，没有宽高信息

#### 在 Activity 中获取某个 View 的宽高的方法

- **使用 `ViewTreeObserver.OnGlobalLayoutListener`**

- **在`Activity onWindowFocusChanged()`方法中获取**
- **View.post 方法，在 post 的 Runnable 中执行时，可以确保 View 已经测量完成**

- **手动调用 measure 方法**

​       需要注意正确设置 MeasureSpec，希望这个 View 能够自由扩展到适应其内容大小时，使用 UNSPECIFIED 模式

```java
final View myView = findViewById(R.id.my_view);
int specWidth = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
int specHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
myView.measure(specWidth, specHeight);
int width = myView.getMeasuredWidth();
int height = myView.getMeasuredHeight();
// 使用宽高进行后续操作
```

#### getWidth 方法和 getMeasureWidth 区别

getMeasureWidth 获取的是测量阶段结束后的值 ，而 getWidth 获取的是布局阶段结束后的值

#### View\.post 与 Handler.post 的区别

View\.post 需要等待 View 被添加到窗口后才会回调

Handler.post 没有这样的限制，且不局限于 View 操作，任何需要在主线程的任务都可以使用

#### invalidate 和 postInvalidate 的区别，它们与 requestLayout 区别

invalidate 在主线程中调用，而 postInvalidate 可在子线程中直接调用

invalidate 主要用于需要更新视图内容而不改变其大小和位置的情况，只走 onDraw() 方法

requestLayout 用于当 View 的大小或位置需要重新计算时，会重新走 onMeasure、onLayout ，onDraw 流程

#### Surface、Canvas、Window 之间的关系

 **Surface**

- Surface 是 Android 系统中用于显示图像的一块内存区域，可以看作是一个没有实际形状的画布，是图像生产者（如应用）和图像消费者（如硬件显示设备）之间的缓冲区。它独立于 UI 线程，可以高效地处理动画和视频播放等复杂图形操作。
- 应用可以在 Surface 上绘制图形，然后由 SurfaceFlinger 负责将 Surface 中的内容合成并显示到屏幕上。它支持多个缓冲区，允许一边绘制新的帧一边显示已绘制好的帧，从而实现平滑的视觉效果。

**Canvas**

- Canvas 直译为画布，在 Android 中它是一个绘制 2D 图形的接口。可以通过 Canvas 对象在指定的 Surface 或 Bitmap 上执行各种绘制操作，比如画线、填充颜色、绘制文本等。
- Canvas 是绘制图形的工具，而 Surface 是图形最终呈现的载体。

**Window**

- 在 Android 中，窗口是应用程序向用户展示界面的基本单位，它对应着屏幕上的一个矩形区域。每个 Activity 都有一个或多个 Window，每个 Window 都与一个 Surface 相关联，用于显示该窗口的内容。

应用通过操作 Canvas 在 Surface 上绘制，而这个 Surface 最终被系统用来更新显示内容，即实现了画面从**应用逻辑到物理屏幕**的映射。

#### SurfaceFlinger 、Surfaces、Choreographer 之间的关系是什么

**SurfaceFlinger** 接收来自各个`Surfaces`的数据，并负责将这些数据合成到一起，形成最终的显示帧，然后提交给显示硬件。

**Choreographer** 确保 UI 更新与屏幕的刷新频率同步，监听 VSync 信号通知更新 UI，保证了画面流畅。

#### 屏幕刷新率 HZ 和帧率 FPS 的关系

- **帧率**是产生画面的速度。
- **刷新率**是显示画面的速度。
- 两者匹配时，你得到最流畅和无延迟的体验。
- 当帧率超过刷新率，多余的信息被浪费。
- 当帧率低于刷新率，画面可能会被重复显示。

#### 什么是 SurfaceView，为什么使用 SurfaceView，与 View 的区别

SurfaceView 是 Android 系统提供的一个特殊类型的 View，它允许开发者在应用的 UI 界面上显示一个持续更新的内容，比如视频播放、游戏动画，拥有自己的 Surface，这是一个独立于主线程 UI 绘制的绘图表面，因此可以高效地进行后台渲染，而不会阻塞主线程

**优点**

SurfaceView 使用双缓冲机制，能够避免画面撕裂和闪烁现象，适合于需要频繁更新的场景，如游戏和视频播放

在单独的线程中进行绘制操作，确保了展示和交互的流畅性

**与 View 的区别**

- 普通 View 的更新通常在主线程中进行，而 SurfaceView 则可以在单独的后台线程中更新
- View 绘制时不使用双缓冲，而 SurfaceView 利用了双缓冲机制，减少了绘制过程中的闪烁和延迟
- 在需要高频率绘制的场景下，SurfaceView 更适合

> 双缓冲机制是一种图形渲染技术，它为每个 Surface 分配了两个缓冲区，通过将前台和后台缓冲区快速交换实现图形流畅展示

#### Surface 和 SurfaceView 之间的关系是什么

`SurfaceView` 通过内部维护的 `Surface` 来接收图像数据。当应用程序向这个 `Surface` 写入图像数据时，`SurfaceView `负责将这些数据适时地展示到应用界面的指定区域。通过 `SurfaceHolder` 接口与 `Surface` 进行交互，提供了控制 `Surface` 的方法，注册 `SurfaceHolder.Callback` 监听器，以便在 `Surface` 的创建、销毁或改变时收到通知。

### Binder 机制

#### Android 中进程和线程的区别

每个进程都拥有独立的内存空间和资源，线程是进程的一部分，一个进程可以包含多个线程，同一进程内的所有线程共享该进程的内存空间和资源

#### 什么是 IPC，为何需要 IPC 来通信

IPC 跨进程通信，指不同进程之间相互通信和数据交换的技术手段

**需要多进程的原因**

Android 应用可能需要同时运行多个任务，这些任务可能适合在不同的进程中执行，以保持应用的稳定性和响应速度

#### 什么是 Binder，为什么 Android 选择 Binder 作为 IPC 的方式

Android 系统中特有的一种高性能的进程间通信（IPC）机制，基于 C/S 架构

**传统的 IPC 方式**

一个进程空间分为**用户空间 & 内核空间**，用户空间的数据不可共享，内核空间的数据可共享，所有进程共用 1 个内核空间，进程间通信需要通过系统调用，将数据 Copy 到内核空间，再从内核空间 Copy 到目标进程的用户空间

**Binder 优点**

性能

> Binder 通过调用 mmap 方法在内核空间创建了一块缓存区，与用户空间存在映射关系，节省了一次数据复制的过程

安全

> Binder 为每个进程分配了唯一 UID 用来鉴别身份，而其它方式没有进行身份验证

#### Binder 传输过程

各个服务端向 ServiceManager 注册，客户端向 binder 驱动发起获取服务的请求，提供要获取服务的名称，binder 驱动将请求转发给 ServiceManager，ServiceManager 通过注册信息找到对应**代理服务引用**

#### 什么是 AIDL

**AIDL** 则是构建在 Binder 机制之上的一种高级抽象，屏蔽了 Binder 底层交互的复杂性，让开发者在实现 Binder IPC 时更加简单和高效

#### AIDL 的使用步骤

1. 创建 AIDL 接口

   在 Android Studio，在服务端模块的 `aidl` 目录下创建一个新的 AIDL 文件中

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

#### 如何优化多模块都使用 AIDL 的情况

使用 Binder 连接池，创建一个中转的 aidl 类，只需创建一个 Service 类，Stub 实现类中通过 binderCode 标识码判断返回哪一个 aidl 实例

```java
interface IBinderPool {
  IBinder queryBinder(int binderCode);
}
```

***

## Java

### JVM

####  JVM 的内存模型

- 堆
  - 内存最大的一块，主要用来存放对象实例和数组，是垃圾回收的主要区域，线程共享
  - 堆中又可以分成**年轻代**和**年老代**，年轻代又可以分成 Eden 空间(存放新建对象)、From Survivor 空间 + To Survivor 空间(存放 GC 后辛存的对象)

*   栈

    *   存放方法执行时的局部变量，包括各种基本数据类型、对象引用

    *   由垃圾回收器自动回收，线程私有

    *   **本地方法栈**处理 Native 方法
*   方法区

    *   存放类信息、常量、静态变量，**线程共享**

    *   包含一个运行时常量池，存放编译器的各种字面量和符号引用
*   程序计数器

    *   当前线程所执行字节码的行号指示器，实现线程恢复、异常处理等功能

    *   线程私有，为了线程切换后能恢复到正确的执行位置，每条线程都需要一个独立的程序计数器

    *   是虚拟机中唯一一个没有 OOM 的区域

#### 类加载过程，类加载器，双亲委派及其优势

- **类加载过程**

​      加载 - 验证 - 准备 - 解析 - 初始化

​     **加载**

​     将外部的 class 文件加载到虚拟机，存储到方法区

​     **验证**

​     验证 class 文件的合法性

​    **准备**

​    为静态变量分配内存，并设置初始值

​    **解析**

​    将常量池内的符号引用转为直接引用

​    **初始化**

​    初始化静态变量、静态代码块类记载器

- **类加载器**

​      实现类加载功能，确保类的唯一性

​      确定两个类是否相等的依据: 是否由同一个类加载器

​     **启动类加载器**

​     加载 \<JAVA\_HOME>\lib 目录中的类

​     **扩展类加载器**

​     加载 \<JAVA\_HOME>\lib\ext 目录中的类库

​     **应用程序类加载器**

​     加载用户类路径（ClassPath）上所指定的类库

​     **用户自定义类加载器**

​     实例化 ClassLoader 类，重写 loadClass 方法

- **双亲委派模型**

​      当一个类加载器收到加载请求时，它首先不会立即尝试加载这个类，而是把这个请求委托给它的父类加载器去完成，如果父类加载器 

​      能够完成加载，则成功返回；如果父类加载器无法加载，则子加载器才会尝试自己加载

​       **好处**

​       保证了类的唯一性

​       避免核心类被篡改。如 String 类只能由启动类记载器加载。

#### 对象创建、存储、使用过程

* **对象创建**

  1.  当执行 new 指令时，先去检查该 new 指令的参数是否能在常量池中定位到一个类的符号引用，并检查这个符号引用所代表的类是否已被加载、解析、初始化过。如果没有需要先执行类加载过程

  2.  类加载完毕后，为新生对象分配一块确定大小的内存，此时根据情况有 2 种分配方式

  - Java 堆内存是规整的

  ​       已使用的内存在一边，未使用内存在另一边，中间放一个指针作为分界点，分配内存时将指针向空闲方向移动一段与对象大小相  

  ​       等的距离，称为**指针碰撞**

  - Java 堆内存是不规整的

  ​       维护着一个记录可用内存块的列表，在分配时从列表中找到一块足够大的空间划分给对象实例，并更新列表上的记录，称为**空  **

  ​       **闲列表**

  **分配方式的选择由堆内存是否规整决定，而是否规整又由垃圾收集器是否支持压缩整理功能决定**

  对象创建在虚拟机中是非常频繁的操作，给对象分配内存会存在线程不安全的问题

  解决 **线程不安全** 有两种方案

  *   采用 **CAS + 失败重试** 的方式 保证更新操作的原子性

  *   把内存分配行为 **按照线程** 划分在不同的内存空间进行

  1.  将内存空间初始化为零值

  2.  对对象进行必要的设置，这些信息存放在对象的对象头中

*   **对象的内存布局**

    对象在 Java 内存中的存储布局可分为三块

    **对象头**

    *   存储自身的运行时数据(Mark Word)

        被设计成 1 个非固定的数据结构以便在极小的空间存储尽量多的信息

    *   类型指针

        确定这个对象是哪个类的实例。

        如果对象是数组，那么在对象头中还必须有一块用于记录数组长度的数据

    **实例数据**&#x20;

    存储对象定义的各类型字段内容

    **对齐填充**&#x20;

    占位符，对象的大小必须是 8 字节的整数倍，对象头部分正好是 8 字节的倍数，当实例数据部分没有对齐时，通过对齐填充来补全。

*   **对象的访问定位**

    Java 通过栈上的对象引用访问堆上的对象实例。有 2 种访问对象的方式

    **句柄**

    划出一块内存作为句柄池，栈中存的是对象的句柄地址，通过句柄地址去定位访问对象实例。

    对象被移动时，只需要改变句柄地址，引用本身不需要修改

    **直接指针**

    栈中存的是对象地址，直接定位访问对象。

    速度快，对象被移动时，需要修改引用地址

#### 如何判断对象是否要回收，有哪些垃圾回收算法

**判断一个 Java 对象是否存活有 2 种方式**

*   **引用计数法**

    每当有一个地方引用它，计数器 + 1，引用失效 - 1，当计数器为 0 时，表示该对象死亡可回收

    优点：简单高效

    缺点：无法解决互相引用问题

*   **可达性分析法**

    当一个对象到 GC Roots 没有任何引用链相连时，则判断该对象不可达，会被标记等待回收

    &#x20;可作为 GC Root 的对象有：
    &#x20;1\. 虚拟机栈中引用的对象
    &#x20;2\. 本地方法栈中 JNI 引用对象
    &#x20;3\. 方法区中常量、静态变量引用的对象

**垃圾回收算法**

不同的内存区域采用不同的垃圾收集算法，不同的算法决定了回收的效率

*   **标记-清除**

    标记出所有需要回收的对象，统一清除被标记的对象

    优点：实现简单

    缺点：清除过程效率不高，且会产生大量不连续的碎片，当需要较大空间时，需要多次触发回收

*   **复制算法**

    将内存分为大小相等的两块，每次使用其中一块，当使用的这块内存用完，就将这块内存上还存活的对象，复制到另一块未用过的内存上，清理掉使用的那一半内存

    优点：每次只回收内存的一半区域，解决了不连续内存碎片的问题

    缺点：只能使用一半的内存，当存活对象较多时，需要执行大量复制操作

    **优化方案**

    将 Eden、From Survivor、To Survivor 按 8：1：1 的比例划分，每次使用 Eden、From Survivor 区域，用完后将这两块区域存活的对象复制到 To Survivor，最后清理掉 Eden、From Survivor 区域。

    **Eden、From Survivor 区域上存活对象所需内存大小 > To Survivor 区域怎么办？**

    存不下来的对象会通过**内存分配担保机制**暂时保存在老年代

*   **标记-整理**

    在标记-清除基础上，标记后让所有存活的对象都向一端移动，统一清除端以外的对象

    解决了效率和不连续碎片问题

*   **分代收集算法(主流)**

    新生代老年代比例 1：2

    新生代对象存活时间短，回收频率高，采用复制算法，新生代 Eden + From Survivor 空间不足时，执行 Minor GC，仍然存活的对象复制到 To Survivor 区域，然后一次性清理 Eden、From Survivor 区域

    **From Survivor、To Survivor 角色是动态切换的**

    老年代对象存活时间长，回收频率低，采用标记清除或标记整理算法，老年代空间不足时，执行 Full GC

    **大对象、年龄达到阈值(默认 15)的对象都分配到老年代**

### 基础

#### Java 1.7 和 1.8 特性

**1.7**

switch 支持 string

创建泛型实例，可以通过类型推断简化代码，new后面的 <> 内不用再写泛型

单个 catch 捕捉多个异常，异常之间用 | 隔开

**1.8**

lambda 表达式

接口可以添加 default 方法

允许使用双引号调用方法或者构造函数

#### 对象的初始化顺序

父类的静态块 -> 子类的静态块 - 父类的构造块 -> 父类的构造方法 -> 子类的构造块 -> 子类的构造方法

**静态优于非静态，父类优于子类**

#### 抽象类和接口的区别

抽象类对事物的属性和行为抽象，包含抽象方法和非抽象方法，子类必须重写父类的抽象方法，可以继承类和实现多个接口

接口对事物某个行为抽象，可被多个类实现，属性都是常量，方法都是抽象的且为 public。接口可继承接口

#### 四种引用类型区别

强引用：默认引用，不会被垃圾回收器回收。可以赋值强引用 null 值，让其被回收

软引用：当内存不足时，会被回收，常用于缓存场景

弱引用：只要发现，无论内存是否不足，都会被回收。常用于防止内存泄漏

虚引用：任何时候都可能被垃圾回收器回收，常用于跟踪对象的 GC 情况

除了强引用，其它 3 种都可以与引用队列结合使用

#### 移除 List 中的元素为什么要使用迭代器而不是 for 循环

for 循环删除某个元素后，list 的大小发生了变化，索引也变化，可能导致移除错误

使用 iterator 的 remove 方法可正常循环移除

#### 什么是反射，有什么优缺点

运行时获取**类的完整结构信息 & 调用对象的方法**

正常情况下，`Java` 类在编译前，就已经被加载到 `JVM` 中；而反射机制使得程序运行时还可以动态地去操作类的变量、方法等信息

**优点**

灵活性高

**缺点**

- 执行效率低
  - 要按名称检索类和方法，有一定的时间开销
  - 编译器难以对动态调用的代码提前做优化
  - 需要装箱和拆箱，产生大量额外的对象和内存开销

- 破坏了类结构

#### 反射的实现方式有哪些

反射机制的实现 主要通过 操作`java.lang.Class`类，泛型形式为`Class<T>`，存放着对象的**运行时信息**

- getClass

  ```java
  Boolean carson = true; 
  Class<?> classType = carson.getClass(); 
  System.out.println(classType);
  //输出结果：class java.lang.Boolean  
  ```

- T.class 语法

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

getConstructor 获取构造方法类对象，getField 获取属性类对象，getMethod 获取方法类对象

getDeclaredXXX 获取的是**类自身**声明的所有方法，包含 public、protected 和 private 方法

getXXX 获取的是**类自身和父类**的所有 public 方法

反射机制的默认行为受限于`Java`的访问控制，需要调用 setAccessible(boolean flag)  flag = true 为反射对象设置可访问标志

```java
public class Student {

    public Student() {
        System.out.println("创建了一个Student实例");
    }

    // 无参数方法
    public void setName1 (){
        System.out.println("调用了无参方法：setName1（）");
    }

    // 有参数方法
    public void setName2 (String str){
        System.out.println("调用了有参方法setName2（String str）:" + str);
    }
}
 // 1. 获取Student类的Class对象
     Class studentClass = Student.class;
 // 2. 通过Class对象创建Student类的对象
     Object  mStudent = studentClass.newInstance();
 // 3.1 通过 Class 对象获取方法 setName1 的 Method 对象
       // 因为该方法 = 无参，所以不需要传入参数
        Method  msetName1 = studentClass.getMethod("setName1");
      // 通过 Method 对象调用 setName1
        msetName1.invoke(mStudent);
 // 3.2 通过 Clas s对象获取方法 setName2的 Method 对象
        Method msetName2 = studentClass.getMethod("setName2", String.class);
       // 通过Method对象调用setName2:需传入创建的实例 & 参数值
        msetName2.invoke(mStudent, "java");
```

#### 什么是泛型，为什么要用泛型

适配广泛的类型，即参数化类型。

**好处**

- 使编译器可在编译期间对类型进行检查以提高类型安全

- 运行时所有的转换都是隐式的，不需要强制类型转换

#### 什么是泛型擦除，为什么需要泛型擦除

Java 编译过程中，编译器会丢弃掉所有的类型参数信息。

**原因**

泛型是在 Java 5 中引入的特性，为了确保与旧版本的代码兼容

泛型擦除简化了虚拟机的实现，不需要对  JVM 进行大规模改动来支持泛型

#### 单例写法

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

### 集合

#### Collection

一个接口，是所有集合的父接口

包含 List、Set、Queue

#### ArrayList 和 LinkedList 区别

**存储的元素是有序的，可以重复**

ArrayList 基于数组，支持随机访问，查询快增删慢，需要移动大量元素，默认大小为 10，扩容操作需要调用把原数组整个复制到新数组中，大小是原有的 1.5 倍，非线程安全

LinkedList 基于双向链表，增删快，增删时只需要修改指针，查询慢，非线程安全

#### HashSet 与 TreeSet 区别

**Set 中元素是无序的，不可以重复**

**HashSet**

基于哈希表，无序，通过对象的 hashCode 和 equals 方法来判断对象是否相同。

如果对象的 hashCode 值不同，不用判断 equals 方法，就直接存到 HashSet 中

如果对象的 hashCode 值相同，再用 equals 方法进行比较，如果结果为 true，则视为相同元素，不存，如果结果为 false，视为不同元素，存储

**LinkedHashSet 使用双向链表维护元素的插入顺序，存入和输出时顺序一致**

**TreeSet**

基于红黑树，可排序，线程不安全的

根据比较方法的返回结果是否为 0 来判断对象是否相同，0 表示相同则不存，非 0 表示不同则存

**排序方式**

元素自身具备比较功能，即自然排序，需要实现 Comparable 接口，并覆盖其 compareTo 方法

元素自身不具备比较功能，则需要实现 Comparator 接口，并覆盖其 compare 方法

#### HashMap 与 TreeMap 区别

HashMap 按照 key 的 hashCode 值确定位置，是无序的 (存入时的顺序与输出的顺序不一致)

TreeMap 按照 key 升序排列，实现 Comparator 接口，重写 compare 方法可自定义排序

**LinkedHashMap 使用双向链表来维护元素的顺序，存入和输出时顺序一致**

#### HashMap 原理

**特点**

实现了 clonebale、Serializable 接口，允许键值为 null，无序，非线程安全

HashMap 采用 **数组+链表** 来存储键值对，包含了一个 Entry 类型的数组 table，Entry 链表存储着键值对，有 next 指针，指向下一个 Entry，初始化容量 16，负载因子 0.75

**put**

先计算 key 的 hash 值，根据 hash 值确定 table 下标，遍历该 table 的 Entry 链表，判断 hash 值和 key 是否存在，存在则用新值替换旧值，若不存在，创建一个新的 Entry 链表，并放到该 Entry 链表的头部

put key 为 null 的键值对时，因 null 无法计算 hash 值，会强制使用第 0 个 table 存放 key 为 null 的键值对

**get**

根据 key 计算 hash 值，通过 hash 值寻找 table 的位置，遍历该位置的链表，查找 key 是否存在，存在则返回 value，不存在则返回 null

**扩容**

当数组长度超出了阈值时，创建一个长度为原来 2 倍的新数组，重新计算 hash 值确定每个数据的位置，并重新设置 hashMap 的扩容阀值

##### 为什么不直接采用经过 hashCode 计算的值，还要用扰动函数额外处理

##### 为什么在计算数组下标前，需对哈希码进行二次处理

##### 为什么拿 hash 值与数组长度 -1 进行位运算

加大哈希码随机性，使得分布更均匀，减少冲突

hash 值默认的范围很大，拿 hash 值与数组长度位运算，使计算出来的值在数组长度范围内

##### 为什么数组长度必须是 2 的次幂

因为要用 hash & length -1 做位运算，数组长度为偶数时可以简化计算，减少 hash 冲突

##### JDK 1.8 HashMap 改动

一条的链表长度 >8 时会将链表转换为红黑树

提高了 put 和 get 的效率，时间复杂度从 O(n) 变为 O(logN)

**头插法变为尾插法**

1.7 为了查找的效率，使用了头插法，但扩容时新链表按照旧链表的逆序排列，多线程 put 时会形成环形链表

1.8 采用尾插法， 在扩容时会保持链表元素原本的顺序，就不会出现链表成环的问题了

**扩容方式不同**

1.7 是先扩容再插入，无论插入的 key 是否已存在，都进行扩容

1.8 是先插入再扩容，如果 key 相同直接替换 value 即可，就不需要扩容

##### 为什么不直接用红黑树存储

红黑树需要进行左旋，右旋等平衡操作，当链表很短的时候，没必要使用红黑树

小于 6 时，红黑树退化为链表，避免增删时转换过于频繁

##### 为什么 HashMap 中 String、Integer 这样的包装类适合作为 key 键

因为它们是不可变的，保证了 hash 不变，减少了 hash 碰撞的几率

##### 与 ConcurrentHashMap 的区别

- ConcurrentHashMap 对整个桶数组进行了分段，每一个分段上都用 Lock 锁进行保护，线程安全且粒度更细

- 不允许 null 键值

### 多线程

#### Java 中创建线程的方式

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

```java
public class MyRunnable implements Runnable {
    @Override
    public void run() {
        // 线程执行的代码
    }

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(5); // 创建一个固定大小的线程池
        for (int i = 0; i < 10; i++) {
            executorService.submit(new MyRunnable()); // 提交任务到线程池
        }
        executorService.shutdown(); // 关闭线程池
    }
}
```

#### 实现线程同步的方式

* synchronized 修饰代码块 & 实例方法 & 静态方法

  **作用:  保证一段代码同一时刻只能被一个线程执行**

  - 对象锁

  ​       只对同一个对象互斥， **同一个类型不同的对象是不互斥的**

  - 类锁

  ​        对该类的所有对象互斥

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

  当一个线程获取了某个对象的锁之后，其他线程无法获取该对象的锁，所以无法访问该对象的其他 synchronized 方法，但**可以访问非 synchronized 方法**，静态 synchronized 方法和非静态 synchronized 方法互相不互斥

*   Lock 锁

```java
Lock lock = new ReentrantLock();
lock.lock();
try{
    //处理任务
} catch (Exception ex) {
     
} finally {
    lock.unlock();   //释放锁
}
```

Lock 使用 Condition 对象来实现 await 和 signalAll 的功能

```java
Condition condition = lock.newCondition();
condition.await();
condition.signalAll()
```

#### synchronized 与 Lock 的区别

*   Lock 是一个接口，synchronized 是 Java 中的关键字
*   Lock 必须手动 unLock 释放锁，synchronized 会自动释放锁
*   Lock 等待锁过程中可以用 interrupt 来中断等待，而 synchronized 不能
*   Lock 可以设置是否为公平锁，而 synchronized 是非公平锁，它无法保证等待的线程获取锁的顺序

#### 并发的三大特性

**原子性**

一个操作要么全部执行并且执行的过程不会被任何因素打断，要么就都不执行

**可见性**

当多个线程访问同一个变量时，一个线程修改了这个变量的值，其他线程能够立即看得到修改的值

**有序性**

程序执行的顺序按照代码的先后顺序执行，指令重排序不会影响单个线程的执行，但是会影响到线程并发执行的正确性

#### 什么是 volatile，与 synchronized 的区别

**volatile 作用**

默认情况下线程中变量的修改，对于其他线程并不是立即可见。

1. 每次使用数据都必须去主内存中获取，每次修改完数据都必须马上同步到主内存，保证了可见性

​      在不影响执行结果的情况下，JVM 会更改代码的执行顺序，提高性能

2. 禁用指令重排，确保使用前初始化操作已经完成

**区别**

volatile 不能保证线程的原子性，且只能用于变量中

#### volatile 关键字这么好，可不可以每个地方都使用

不行，只有当需要保证线程安全时才去使用它，否则频繁写入读取主内存消耗性能

#### 多线程环境下 i++ 这种操作使用 volatile 能保证结果准确吗

由于 Java 运算的非原子性，volatile 不能保证运算的原子性，所以是不安全的

只有当计算的结果不依赖原来的状态 volatile 才是安全的

#### CAS 

CAS 叫做比较并交换，是一种非阻塞同步，乐观锁

CAS 指令需要有 3 个操作数，分别是内存地址 V、旧的预期值 A 和新值 B。当执行操作时，只有当 V 的值等于 A，才将 V 的值更新为 B

**原子类 Atomic 应用了 CAS 原理**

**ABA 问题**

某个值原始值为 A，被修改成了 B，然后又被改成了 A，CAS 认为该值没有变更过，可以通过添加版本号来加强验证解决 ABA 问题

#### sleep 与 wait 区别，notify 和 notifyAll 区别

sleep 是Thread 类的方法，而 wait 是 Object 类的方法

sleep 不会释放锁，到时间后会自动唤醒，而 wait 会释放锁并需要其它线程 notify/notifyAll 唤醒

sleep 通常用于让当前线程短暂停顿，wait 用于线程间的同步

notify      随机唤醒一个 wait 的线程

notifyAll 唤醒所有在 wait 的线程

**调用了 notify、notifyAll 不代表被唤醒的线程就会立即执行，要等到 synchronized 块执行完释放锁后，才会开始执行**

#### 两道经典题

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

## Kotlin

### kotlin 与 Java 相比有哪些优点和缺点

优点

- Kotlin 引入了可空类型，减少了空指针异常的风险
- 语法更加紧凑，减少了样板代码
- 函数式编程提供了更强大的扩展性和灵活性
- 扩展函数和属性
- 使用类型推断，减少了代码中的类型声明
- 支持原生的协程，提供了轻量级的并发解决方案

缺点

- Kotlin 的编译速度可能不如 Java，尤其是大型项目中

### 数据类 (data class) 的作用是什么，与普通类相比有何优势

数据类会自动生成一些常用的方法，如 `equals()`, `hashCode()`, `toString()` 和 `copy()`，这样就不必手动实现这些方法，减少了样板代码，确保了 **equals() 和 hashCode() 的一致性**。

### 扩展函数的原理是什么

扩展函数可以在不修改原有类定义的情况下，为类添加新的函数和属性。

Kotlin 编译器会在幕后生成相应的静态方法，并在调用点自动传递目标对象作为第一个参数。这确保了原有的类二进制代码不需要做任何改变就能使用这些新函数。

### Kotlin 中的高阶函数是什么

满足以下条件之一的函数

**接收一个或多个函数作为参数**

**返回一个函数作为结果**

### @JvmOverloads 注解的作用是什么

在一个 Kotlin 类的方法上使用了此注解，编译器会为该方法生成多个重载版本，每个重载版本对应于原方法签名中具有默认参数值的参数的一种组合，Java 代码使用时可以不用传递全部参数

### Kotlin与 Java 代码互操作的方式和注意事项

Kotlin 可直接调用 Java 类和方法，几乎不需要额外的转换或适配

Kotlin 支持 Java 的静态方法和成员变量，可以直接通过类名访问

Kotlin 代码会被编译成 Java 字节码，因此 Java 代码可以透明地调用 Kotlin 类和方法

**@JvmOverloads** 注解生成带有重载方法的类，以便 Java 代码调用

### Kotlin 的委托是什么

允许你定义一个属性的 getter 和 setter 方法的行为去委托给另一个对象，而不是直接在属性声明的地方实现这些行为

```java
语法
var property: Type by DelegateExpression
  
定义原始的 UserSettings 
class UserSettings {   
  var username: String = ""   
  var email: String = "" 
}
定义 ReadOnlyView 委托类，它将只实现 getValue 方法来提供只读访问
class ReadOnlyView<T>(private val delegate: T) {
   perator fun getValue(thisRef: Any?, property: KProperty<*>): T {
       return delegate
   }
}
使用委托创建 ReadOnlyUserSettings
class ReadOnlyUserSettings(private val settings: UserSettings) {
    val username: String by ReadOnlyView(settings::username)
    val email: String by ReadOnlyView(settings::email)
}  

fun main() {
    val originalSettings = UserSettings().apply {
        username = "Alice"
        email = "alice@example.com"
    }
    val readOnlyView = ReadOnlyUserSettings(originalSettings)
    println(readOnlyView.username) // 输出: Alice
    println(readOnlyView.email)    // 输出: alice@example.com
    // 尝试修改 readOnlyView 的属性值会编译错误，因为没有提供 setValue 方法
}
```

### 协程的基本概念是什么，如何在 Android 中使用协程进行异步编程

协程是一个线程框架，能够在一个代码块里进行多次线程切换。

- 在 Activity 或 Fragment 中使用 lifecycleScope

- 在 ViewModel 中使用 ViewModelScope

- 不与任何特定的 Android 生命周期相关联的场景下，使用自定义 CoroutineScope 来管理协程，需要你手动控制其生命周期，包括启动和取消协程。

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

使用 launch 或 async 函数启动协程**，**选择合适的调度器，协程体内部使用标准的`try-catch`结构来捕获异常，就像在同步代码中一样。

###  suspend 函数和普通函数的区别是什么

**suspend 函数**只能在协程或者另一个 `suspend` 函数内部调用，**挂起的是协程，挂起函数在执行完成之后，协程会重新切回它原先的线程**。suspend 并没有实际挂起的作用，仅仅是函数的创建者对函数的使用者的提醒，强制耗时操作必须在子线程中进行。

**非阻塞式指可以用看起来阻塞式的代码写非阻塞的操作。**

### inline 内联函数作用是什么

编译器会将 `inline` 函数的代码直接插入到调用该函数的地方，消除了创建匿名内部类的开销，适用于短小、频繁调用的函数。

支持非局部返回。想要直接结束整个外部函数，而不是仅仅结束 Lambda 表达式的执行。

## 网络

### http 不同版本有什么区别

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

### http 状态码分类有哪些

1.  1xx 信息性状态码，表示接收的请求正在处理，这类状态码用于提供关于请求的一些信息性响应，而不是指示成功或失败
2.  2xx 成功状态码，表示请求正常处理完毕
3.  3xx 重定向状态码，表示要对请求进行重定向操作，301 永久重定向，302 临时重定向
4.  4xx 客户端错误状态码，表示服务器无法处理请求
5.  5xx 服务器错误状态码，表示服务器处理请求时出错

### http 请求有哪些类型

GET、POST、PUT、DELETE、HEAD 等

1.  GET 用于向指定的 URL 请求资源，请求参数和对应的值附加在 URL 后面

2.  POST 主要用于向指定的 URL 提交数据，通常用于表单发送

3.  PUT 用于将信息放到请求的 URL 上，是幂等操作，即多次执行相同

4.  DELETE 用于请求服务器删除 Request-URL 所标识的资源

5.  HEAD 与 GET 请求相一致，只不过响应体将不会被返回

### 在浏览器中输入一个网址会发生什么

1.  浏览器会向 DNS 发送一个查询，将输入的网址转换成对应的 IP 地址。

2.  浏览器获得了服务器的 IP 地址后，它会向该地址发送一个请求，以建立一个 TCP 连接

3.  连接建立后，浏览器会向服务器发送一个 HTTP 请求

4.  服务器收到你的请求后，生成一个 HTTP 响应发回到浏览器

5.  浏览器收到响应后解析 HTML、CSS、JS 等代码，显示到网页上

### https 是什么，传输流程是什么

HTTPS 依赖于 SSL/TLS 协议通过将 HTTP 的内容进行加密，确保数据在传输过程中的安全性

使用非对称加密方式 + 对称加密混合的方式传输

流程如下:

1.  客户端向服务器发起 HTTPS 请求，连接到服务器的 443 端口
2.  服务器会返回一个证书，证书里面包含了公钥和证书信息
3.  客户端验证证书的合法性，如果证书验证通过，那么生成一个随机值
4.  客户端使用证书中的公钥将这个随机值加密，然后传送给服务器
5.  服务器使用自己的私钥解密这个随机值，然后使用这个随机值作为对称加密的密钥，用其将数据加密后传送给客户端
6.  客户端使用这个随机值解密服务器传送来的信息，获取到明文的 HTTP 数据

### TCP 三次连接、四次断开过程是什么，和 UDP 区别是什么

![示意图](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9pbWdjb252ZXJ0LmNzZG5pbWcuY24vYUhSMGNEb3ZMM1Z3Ykc5aFpDMXBiV0ZuWlhNdWFtbGhibk5vZFM1cGJ5OTFjR3h2WVdSZmFXMWhaMlZ6THprME5ETTJOUzAxTTJZMFlqTmlZekZsWkRSa01UZGxMbkJ1Wnc "示意图")

面向连接是指数据传输之前，发送方和接收方之间需要建立连接，TCP 是通过三次握手实现的，UDP 是无连接的协议，它不需要建立连接，而是直接发送数据

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

### TCP 是如何保证传输的可靠性的

1.  **序列号**：TCP 会为每个发送的数据包分配一个唯一的序列号。接收方会根据序列号对接收到的数据包进行排序，确保数据的顺序正确。如果接收方发现数据包的序列号不连续，它会要求发送方重新发送缺失的数据包

2.  **确认应答**：接收方在成功接收到数据包后，会向发送方发送一个确认应答（ACK）。发送方会根据确认应答来判断数据包是否成功传输。如果发送方在一定时间内没有收到确认应答，它会认为数据包丢失，并重新发送该数据包

3.  **流量控制**：TCP 使用滑动窗口机制进行流量控制，接收方会根据自己的处理能力，告诉发送方一个窗口大小，发送方只能在这个窗口大小内发送数据。当接收方的处理能力下降时，它会减小窗口大小，从而限制发送方的发送速度。如果网络状况良好，窗口可以变大，发送更多的数据

4.  **校验和**：TCP 在发送数据时，会对每个数据包进行校验和的计算。接收方在收到数据包后，会对数据包进行同样的校验和计算，然后与发送方的校验和进行比对。如果校验和不一致，接收方会丢弃该数据包，并要求发送方重新发送。这样可以确保数据在传输过程中没有被损坏

### 长链接和短链接区别

长连接，指的是在 TCP 连接建立后，不管是否使用都保持连接，可以在多次交互中重复使用，减少了建立和断开连接的开销，适用于需要频繁进行数据传输的场景，一个典型应用是 WebSocket 协议

短连接，通常只会在客户端和服务器间传递一次读写操作，然后立即断开连接，不会保持连接状态，适用于不需要频繁进行数据传输的场景，避免不必要的资源消耗，一个典型应用是 HTTP 协议
