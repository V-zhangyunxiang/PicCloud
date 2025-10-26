## ✅ 第一步：理解 ANR 日志的基本结构

ANR 日志通常以 `----- pid XXXX at YYYY-MM-DD HH:MM:SS -----` 开头，然后列出所有线程的快照。

每个线程块的格式如下：

```
"main" prio=5 tid=1 Native
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x72a6c3d8 self=0xb4000079b8e9a000
  | sysTid=12345 nice=-10 cgrp=default sched=0/0 handle=0x79b8f524f8
  | state=S schedstat=( 123456789 987654321 123 ) utm=10 stm=2 core=3 HZ=100
  | stack=0x7fe4e5a000-0x7fe4e5c000 stackSize=8192KB
  | held mutexes=
  kernel: __switch_to+0xac/0xe0
  kernel: futex_wait_queue_me+0xd4/0x130
  kernel: futex_wait+0xdc/0x1a0
  native: #00 pc 0000000000083ba4  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+28)
  native: #01 pc 00000000000875b4  /apex/com.android.runtime/lib64/bionic/libc.so (__futex_wait_ex(void volatile*, bool, int, bool, timespec const*)+96)
  native: #02 pc 00000000000e5b58  /apex/com.android.runtime/lib64/bionic/libc.so (pthread_cond_wait+60)
  native: #03 pc 00000000003b9d74  /system/lib64/libhwui.so (???)
  ...
  at android.os.MessageQueue.nativePollOnce(Native method)
  at android.os.MessageQueue.next(MessageQueue.java:335)
  at android.os.Looper.loopOnce(Looper.java:161)
  at android.os.Looper.loop(Looper.java:288)
  at android.app.ActivityThread.main(ActivityThread.java:7829)
  ...
```

---

## ✅ 第二步：定位主线程 —— 它通常不是真凶，但能告诉你“案发时间”

找 `"main"` 线程块。

### 🔍 关键字段解读：
- **`state=`**：这是最重要的字段！它表示线程当前内核态的状态。
  - `R` = Running or Runnable（正在运行或可运行）
  - `S` = Sleeping（休眠，可中断）
  - `D` = Uninterruptible Sleep（不可中断睡眠，常出现在 I/O 阻塞）
  - `T` = Stopped（暂停）
  - `Z` = Zombie（僵尸）
  - `t` = Tracing stop
  - `X` = Dead

> ✅ **ANR 场景下，主线程的 `state` 几乎总是 `S`（Sleeping）**，因为它在 `nativePollOnce` 里等待消息，没活干 —— 这是“果”，不是“因”。

- **`held mutexes=`**：如果这里有值，说明主线程持有了某个锁，可能造成其他线程阻塞。
- **堆栈顶部**：如果是 `android.os.MessageQueue.nativePollOnce` → 说明主线程空闲，卡顿来自其他地方。

📌 **结论**：如果主线程状态是 `S` 且堆栈停在 `nativePollOnce`，那问题一定出在**其他线程**！

---

## ✅ 第三步：扫描所有 “Other Threads” —— 寻找异常状态和可疑堆栈

跳过系统线程（如 `Jit thread pool`, `HeapTaskDaemon`, `ReferenceQueueD`, `FinalizerDaemon`），重点看你自己 App 创建的线程，尤其是包含业务名、模块名、线程池名的线程。

比如：

```
"metrics-ioThreadPool-1" prio=5 tid=12 TimedWaiting
  | group="main" sCount=1 dsCount=0 flags=1 obj=0x12c4b320 self=0xb4000079c8e9b000
  | sysTid=12356 nice=0 cgrp=default sched=0/0 handle=0x78a8f524f8
  | state=S schedstat=( 123456789 987654321 123 ) utm=1000 stm=200 core=2 HZ=100
  | stack=0x7fe4d5a000-0x7fe4d5c000 stackSize=1039KB
  | held mutexes=
  at java.lang.Object.wait(Native method)
  - waiting on  (a java.lang.Object)
  at com.xxx.MetricsUploader.flush(MetricsUploader.java:245)
  - locked  (a java.lang.Object)
  at com.xxx.MetricsUploader.lambda$uploadBatch$0(MetricsUploader.java:180)
  at com.xxx.-$$Lambda$MetricsUploader$abcde.run(Unknown Source:2)
  at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1167)
  at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:641)
  at java.lang.Thread.run(Thread.java:923)
```

### 🔍 如何判断这个线程是否“可疑”？

#### 🚩 红旗 1：线程状态不是 `S` 或 `TIMED_WAITING`
- 如果看到 `state=R`（Runnable）并且 CPU 时间 (`utm`, `stm`) 很高 → **CPU 密集型任务卡死**
- 如果看到 `state=D`（Uninterruptible）→ **I/O 阻塞（文件、网络、数据库）**

#### 🚩 红旗 2：堆栈深处有你的业务代码，且停在 I/O 或锁上
- 停在 `java.net.SocketInputStream.socketRead0` → 网络读取卡住
- 停在 `android.database.sqlite.SQLiteConnection.nativeExecuteForCursorWindow` → 数据库查询慢
- 停在 `Object.wait()` 或 `LockSupport.park()` → 在等锁，检查是否死锁
- 停在 `FileInputStream.read()` → 文件读取慢

#### 🚩 红旗 3：`held mutexes=` 不为空
→ 该线程持有一个或多个锁。结合其他线程的 `waiting on `，可以手动构建“锁等待图”，排查死锁。

#### 🚩 红旗 4：线程名包含你的模块名（如 `metrics-ioThreadPool`）
→ 优先审查！平台不会无缘无故给它打标。

---

## ✅ 第四步：实战演练 —— 假设你看到以下线程块

```
"metrics-ioThreadPool-3" prio=5 tid=15 Runnable
  | group="main" sCount=0 dsCount=0 flags=0 obj=0x12c4b560 self=0xb4000079c8e9c000
  | sysTid=12360 nice=0 cgrp=default sched=0/0 handle=0x77a8f524f8
  | state=R schedstat=( 9876543210 123456789 500 ) utm=8000 stm=1000 core=1 HZ=100
  | stack=0x7fe4c5a000-0x7fe4c5c000 stackSize=1039KB
  | held mutexes= "mutator lock"(shared held)
  at com.xxx.DataProcessor.processHugeDataSet(DataProcessor.java:312)
  at com.xxx.DataProcessor.run(DataProcessor.java:150)
  at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1167)
  at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:641)
  at java.lang.Thread.run(Thread.java:923)
```

### 🕵️‍♂️ 分析过程：

1. **线程名**：`metrics-ioThreadPool-3` → 业务相关线程，重点对象。
2. **状态**：`state=R` → 正在运行，不是在睡觉！
3. **CPU 时间**：`utm=8000`（用户态 80 秒），`stm=1000`（内核态 10 秒）→ **消耗了巨量 CPU 时间**，极不正常。
4. **堆栈**：停在 `DataProcessor.java:312` → 你的业务代码，很可能是个超大循环或复杂算法。
5. **持有锁**：`held mutexes= "mutator lock"` → 可能阻止了 GC 或其他线程。

✅ **结论**：这个线程就是元凶！它在疯狂占用 CPU，导致其他线程（包括主线程的消息处理）得不到调度，最终触发 ANR。

### `utm` 和 `stm` 的定义

1. **utm（User Time）**
    
    - **含义**：线程在用户态（User Mode）的 CPU 执行时间，即执行应用程序代码（Java/Kotlin/Native 非系统调用）的耗时。
    - **典型场景**：业务逻辑计算、算法处理、非阻塞 I/O 等。
2. **stm（System Time）**
    
    - **含义**：线程在内核态（Kernel Mode）的 CPU 执行时间，即执行系统调用（如文件读写、网络通信、锁操作）的耗时。
    - **典型场景**：文件操作、Binder 通信、同步锁竞争等。
3. **时间单位是 `jiffies`，而非直接毫秒**
  - `1 jiffy = 10ms`（默认值，由系统时钟频率 `HZ=100` 决定）,所以 `8000` 对应 **80 秒**

---

## ✅ 第五步：高级技巧 —— 多线程死锁排查

如果你看到多个线程互相 `waiting on` 和 `locked` 同一个对象，就可能是死锁。

示例：

```
"Thread-A" ...
  at java.lang.Object.wait(Native method)
  - waiting on <0x0a1b2c3d> (a java.lang.Object)
  at com.xxx.Manager.getData(Manager.java:50)
  - locked <0x0a1b2c3d> (a java.lang.Object)

"Thread-B" ...
  at java.lang.Object.wait(Native method)
  - waiting on <0x0d2e3f4a> (a java.lang.Object)
  at com.xxx.Cache.update(Cache.java:80)
  - locked <0x0d2e3f4a> (a java.lang.Object)
  at com.xxx.Manager.notifyChange(Manager.java:120)
  - locked <0x0a1b2c3d> (a java.lang.Object) ← 它想拿 A 的锁！
```

Thread-B 持有锁 `<0x0d2e3f4a>`，并试图获取 `<0x0a1b2c3d>`；而 Thread-A 正持有 `<0x0a1b2c3d>` 并在等某个条件（可能依赖 B）。这就形成了死锁。

---

## ✅ 总结：你的 ANR 排查 Checklist

| 步骤  | 操作                               | 目标                                   |
| --- | -------------------------------- | ------------------------------------ |
| 1️⃣ | 找到 `"main"` 线程                   | 确认它是否卡在 `nativePollOnce` + `state=S` |
| 2️⃣ | 忽略系统线程                           | 聚焦你自己创建的线程（按名字筛选）                    |
| 3️⃣ | 查看 `state=` 字段                   | 优先排查 `R`（Running）、`D`（IO Block）      |
| 4️⃣ | 查看 `utm`/`stm`                   | 数值巨大 → CPU 占用过高                      |
| 5️⃣ | 查看堆栈最深的业务代码                      | 是否停在 I/O、锁、复杂计算？                     |
| 6️⃣ | 查看 `held mutexes` 和 `waiting on` | 手动排查死锁链                              |
| 7️⃣ | 结合线程名和业务逻辑                       | 锁定嫌疑最大的模块                            |


**ANR 的直接触发点永远是主线程未能及时响应**，但根本原因可能是：

- 🔄 主线程自身阻塞（直接原因）
- ⚡ 子线程资源竞争导致主线程饥饿（间接原因）

|因素|说明|对主线程的影响|
|---|---|---|
|**CPU 时间片分配**|每个线程按 `nice` 值和权重分配时间片|高 CPU 占用的子线程会挤占主线程时间片|
|**调度延迟**|就绪队列中线程需要等待被调度|主线程处理输入事件的延迟可能超过 5 秒阈值|
|**CPU 核心争抢**|多核设备中线程可能被迁移到不同核心|频繁的核心切换增加调度开销|

|概念|类比|现代典型配置|
|---|---|---|
|CPU|工厂|1个物理芯片|
|核心|车间|4/8/16个核心|
|线程|工人|每个核心可运行1-2个线程|


