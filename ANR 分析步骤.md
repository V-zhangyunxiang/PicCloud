# ANR 分析步骤与优化方法论

## 一、ANR 概述与触发机制

### ANR 定义
Application Not Responding - 应用程序无响应。当主线程在特定时间内未能完成关键操作时触发。

### 触发条件
- **输入事件**：5秒内未响应
- **BroadcastReceiver**：10秒内未完成
- **Service**：20秒内未完成（前台服务）

## 二、ANR 日志结构解析

### 基本结构
```
----- pid XXXX at YYYY-MM-DD HH:MM:SS -----
CPU usage from XXXXms to YYYYms ago:
...
...
```

### 线程块格式与详细解释

```
"main" prio=5 tid=1 Native
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x72a6c3d8 self=0xb4000079b8e9a000
  | sysTid=12345 nice=-10 cgrp=default sched=0/0 handle=0x79b8f524f8
  | state=S schedstat=( 123456789 987654321 123 ) utm=10 stm=2 core=3 HZ=100
  | stack=0x7fe4e5a000-0x7fe4e5c000 stackSize=8192KB
  | held mutexes=
```

**字段详细解析**：

- **`"main"`**：线程名称，主线程是Android应用的UI线程
- **`prio=5`**：线程优先级（1-10，数值越大优先级越高）
- **`tid=1`**：线程ID，主线程通常为1
- **`Native`**：线程状态标识，表示正在执行Native代码

- **`group="main"`**：线程所属组
- **`sCount=1`**：挂起计数（suspend count）
- **`dsCount=0`**：调试器挂起计数
- **`flags=1`**：线程标志位
- **`obj=0x72a6c3d8`**：关联的Java对象地址
- **`self=0xb4000079b8e9a000`**：线程本身的Native地址

- **`sysTid=12345`**：系统级线程ID（在`ps`命令中可见）
- **`nice=-10`**：Nice值，影响CPU调度（-20到19，越小优先级越高）
- **`cgrp=default`**：CPU调度组
- **`sched=0/0`**：调度策略和实时优先级
- **`handle=0x79b8f524f8`**：系统句柄

- **`state=S`**：**关键字段** - 线程在内核中的状态
  - `S` = Sleeping（睡眠，等待事件）
  - `R` = Running（正在运行）
  - `D` = Uninterruptible Sleep（不可中断睡眠，通常为I/O操作）
  - `Z` = Zombie（僵尸进程）
  - `T` = Stopped（暂停）

- **`schedstat=( 123456789 987654321 123 )`**：调度统计信息
  - 格式：`(运行时间, 等待时间, 执行次数)`
  - 单位：jiffies（1 jiffy = 10ms）

- **`utm=10 stm=2`**：**关键字段** - CPU时间统计
  - `utm=10`：用户态CPU时间 = 10 jiffies = 100ms
  - `stm=2`：内核态CPU时间 = 2 jiffies = 20ms
  - 计算公式：`实际秒数 = (utm + stm) × 10ms`

- **`core=3`**：最近运行该线程的CPU核心编号
- **`HZ=100`**：系统时钟频率（每秒100个tick）

- **`stack=0x7fe4e5a000-0x7fe4e5c000`**：栈内存地址范围
- **`stackSize=8192KB`**：栈大小（主线程通常为8MB）

- **`held mutexes=`**：**关键字段** - 持有的互斥锁
  - 如果为空：没有持有任何锁
  - 如果有值：列出持有的锁，可能造成其他线程阻塞
  - 示例：`held mutexes= "mutator lock"(shared held)`

## 三、ANR 分析步骤

### 第一步：定位主线程状态

**目标**：确认主线程是否处于正常等待状态

**关键字段分析**：
- **`state=`**：线程内核状态
  - `S` = Sleeping（正常等待消息）
  - `R` = Running（正在运行）
  - `D` = Uninterruptible Sleep（I/O 阻塞）
  - `Z` = Zombie

- **堆栈顶部**：
  - 正常：`android.os.MessageQueue.nativePollOnce` → 主线程空闲
  - 异常：停留在业务代码 → 主线程自身阻塞

**ANR分析中的关键判断**：

1. **正常状态**：主线程`state=S` + 堆栈在`nativePollOnce` + `held mutexes=`为空
   - 表示主线程正常等待消息，问题在其他线程

2. **异常状态**：
   - `state=R` + 高`utm/stm` → 主线程自身执行耗时操作
   - `state=D` → 主线程在不可中断的I/O操作中
   - `held mutexes`不为空 → 主线程持有锁，可能造成死锁
   - 堆栈停留在业务代码 → 直接定位问题位置

**结论**：如果主线程状态为 `S` 且堆栈停在 `nativePollOnce`，问题出在其他线程。

### 第二步：扫描业务线程

**过滤策略**：
- 跳过系统线程（`Jit thread pool`, `HeapTaskDaemon` 等）
- 聚焦业务线程（包含项目包名、模块名的线程）

**可疑线程特征**：

#### 🚩 红旗1：异常线程状态
- `state=R` + 高 CPU 时间 → CPU 密集型任务卡死
- `state=D` → I/O 阻塞（文件、网络、数据库）

#### 🚩 红旗2：关键堆栈位置
- `java.net.SocketInputStream.socketRead0` → 网络阻塞
- `SQLiteConnection.nativeExecuteForCursorWindow` → 数据库慢查询
- `Object.wait()` / `LockSupport.park()` → 等待锁
- `FileInputStream.read()` → 文件读取阻塞

#### 🚩 红旗3：持有锁资源
- `held mutexes=` 不为空 → 可能造成死锁
- 结合其他线程的 `waiting on` 分析锁依赖

#### 🚩 红旗4：CPU 时间异常
- **`utm`（用户态时间）**：执行应用代码的耗时
- **`stm`（内核态时间）**：执行系统调用的耗时
- **时间单位**：`jiffies`（1 jiffy = 10ms，基于 HZ=100）
- 示例：`utm=8000` = 80秒用户态CPU时间 → 异常

### 第三步：死锁分析

**识别模式**：多个线程互相 `waiting on` 和 `locked` 同一对象

**死锁示例分析**：
```
"Thread-A" waiting on <0x0a1b2c3d>
"Thread-B" locked <0x0a1b2c3d> and waiting on <0x0d2e3f4a>
"Thread-A" locked <0x0d2e3f4a>
```

**死锁形成过程**：
1. **Thread-A** 持有锁 `<0x0d2e3f4a>`，同时尝试获取锁 `<0x0a1b2c3d>`
2. **Thread-B** 持有锁 `<0x0a1b2c3d>`，同时尝试获取锁 `<0x0d2e3f4a>`
3. 两个线程互相等待对方释放锁，形成**循环等待**，导致死锁

**实际堆栈表现**：
```
"Thread-A" prio=5 tid=15 Waiting
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x12c4b320 self=0xb4000079c8e9b000
  | sysTid=12356 nice=0 cgrp=default sched=0/0 handle=0x78a8f524f8
  | state=S schedstat=( 123456789 987654321 123 ) utm=10 stm=2 core=2 HZ=100
  | stack=0x7fe4d5a000-0x7fe4d5c000 stackSize=1039KB
  | held mutexes=
  at java.lang.Object.wait(Native method)
  - waiting on <0x0a1b2c3d> (a com.example.LockObject)  ← 等待Thread-B释放
  at com.example.ClassA.methodA(ClassA.java:50)
  - locked <0x0d2e3f4a> (a com.example.AnotherLock)     ← 持有锁，被Thread-B等待

"Thread-B" prio=5 tid=16 Waiting  
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x12c4b560 self=0xb4000079c8e9c000
  | sysTid=12357 nice=0 cgrp=default sched=0/0 handle=0x77a8f524f8
  | state=S schedstat=( 987654321 123456789 500 ) utm=8 stm=1 core=1 HZ=100
  | stack=0x7fe4c5a000-0x7fe4c5c000 stackSize=1039KB
  | held mutexes=
  at java.lang.Object.wait(Native method)
  - waiting on <0x0d2e3f4a> (a com.example.AnotherLock) ← 等待Thread-A释放
  at com.example.ClassB.methodB(ClassB.java:80)
  - locked <0x0a1b2c3d> (a com.example.LockObject)     ← 持有锁，被Thread-A等待
```

### 第四步：CPU 使用率分析

检查 ANR 日志开头的 CPU 使用统计：
- 如果 App 占用 100% CPU → 存在死循环或过度计算
- 如果系统占用过高 → 资源紧张导致调度延迟

## 四、ANR 优化方向

### 核心原则
**不是"不能在子线程做网络/IO"，而是"不能无限制、无超时、无管理地做阻塞式 I/O"**

### 常见错误模式与修复方案

#### 🚫 错误1：同步 I/O 无超时
```java
// ❌ 危险代码
threadPool.submit(() -> {
    String response = HttpUtils.get("https://xxx.com/metrics"); // 可能永久阻塞
});
```

**✅ 修复**：所有网络/IO 必须设置超时
```java
OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build();
```

#### 🚫 错误2：主线程等待子线程结果
```java
// ❌ 披着异步外衣的同步调用
Future future = threadPool.submit(this::doNetworkCall);
String result = future.get(); // 主线程阻塞！
```

**✅ 修复**：使用回调或协程
```java
// 异步回调
threadPool.submit(() -> {
    String result = doNetworkCall();
    runOnUiThread(() -> updateUI(result));
});

// Kotlin 协程
lifecycleScope.launch {
    val result = withContext(ioDispatcher) { doNetworkCall() }
    updateUI(result)
}
```

#### 🚫 错误3：线程池配置不当
```java
// ❌ 无界队列危险
ExecutorService badPool = new ThreadPoolExecutor(
    2, 2, 0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue() // 无界队列！
);
```

**✅ 修复**：有界队列 + 合理拒绝策略
```java
ExecutorService goodPool = new ThreadPoolExecutor(
    2, 4, 30L, TimeUnit.SECONDS,
    new ArrayBlockingQueue(50), // 有界队列
    new ThreadPoolExecutor.DiscardOldestPolicy() // 拒绝策略
);
```

#### 🚫 错误4：后台无降级策略
```java
// ❌ 后台仍高频执行
threadPool.scheduleAtFixedRate(uploadTask, 0, 1, TimeUnit.SECONDS);
```

**✅ 修复**：监听应用状态进行降级
```java
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onPause(owner: LifecycleOwner) {
        metricsUploader.pause(); // 后台暂停
    }
    override fun onResume(owner: LifecycleOwner) {
        metricsUploader.resume(); // 前台恢复
    }
});
```

### 线程池最佳实践

1. **核心参数配置**
   - 核心线程数：根据任务类型调整（CPU密集型 vs I/O密集型）
   - 最大线程数：避免创建过多线程导致上下文切换开销
   - 队列大小：设置合理的有界队列

2. **监控与告警**
```java
// 添加线程池监控
Log.d("THREAD_POOL", "Active: ${pool.activeCount}, Queue: ${pool.queue.size}");
if (pool.queue.size() > threshold) {
    Log.w("THREAD_POOL", "队列堆积警告！");
}
```

## 五、ANR 排查 Checklist

| 步骤  | 操作             | 目标                                                |
| --- | -------------- | ------------------------------------------------- |
| 1️⃣ | 定位 `"main"` 线程 | 确认状态是否为 `S` + `nativePollOnce`                    |
| 2️⃣ | 过滤系统线程         | 聚焦业务相关线程                                          |
| 3️⃣ | 检查线程 `state`   | 优先排查 `R`、`D` 状态线程                                 |
| 4️⃣ | 分析 `utm`/`stm` | 识别 CPU 占用异常的线程                                    |
| 5️⃣ | 查看堆栈最深处        | 定位阻塞的业务代码位置，ANR 的归因原则是：**谁在栈顶（或离栈顶最近的业务代码），谁背锅。** |
| 6️⃣ | 检查锁关系          | 分析 `held mutexes` 和 `waiting on`                  |
| 7️⃣ | 结合业务上下文        | 确认问题模块和触发场景                                       |

## 六、根本原因分类

### 直接原因
- 主线程执行耗时操作（数据库、文件、计算）
- 主线程等待子线程结果（`Future.get()`）
- 主线程死锁

### 间接原因
- 子线程资源竞争导致主线程饥饿
- CPU 被其他线程占满，主线程得不到调度
- I/O 阻塞导致系统资源紧张

## 七、预防措施

1. **代码层面**
   - 主线程只处理 UI 更新和轻量操作
   - 所有 I/O 操作必须设置超时
   - 避免在主线程等待异步结果

2. **架构层面**
   - 使用合理的线程池配置
   - 实现前后台任务差异化执行
   - 添加任务队列监控和告警

3. **监控层面**
   - 监控主线程卡顿
   - 监控线程池队列长度
   - 监控后台任务执行时间

通过这套完整的分析方法论，可以系统化地定位 ANR 根本原因，并从代码设计和架构层面预防类似问题发生。