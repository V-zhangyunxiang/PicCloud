# 基本背景

目标：优化 ember 的 crash、anr、oom 性能指标，确定问题点，调研优化方案。
涉及代码：poseidon SDK、ember 业务代码。
指标数据：联系杨怀冬获取性能指标数据。

ANR 明显比 Crash 多，Crashlytics 没有开启堆转储，导致采集不到 hprof 数据。

ANR 集中在后台触发，Android 17 占比较高。

线下通过 leak Cancary 收集内存泄漏。

Your app is experiencing memory issues. Enable heap dump collection to troubleshoot them more effectively.

Crashlytics 发现你的 App 在“悄悄”因内存问题崩溃，但缺乏足够的数据来帮你定位具体的代码原因。它建议你开启堆转储收集，以便捕获内存快照，**从“知道发生了 OOM”进化到“知道 OOM 时是哪段代码占用内存过多”**。

### 如何开启与使用堆转储？

你需要通过 **Android CLI 工具**的 `android signals` 命令集来操作。

1. **登录授权**：运行 `android auth login`，授权访问你的 Firebase 项目。
    
2. **收集堆转储**：确保 Crashlytics 已启用。当应用发生 OOM 或内存限制器强杀时，Crashlytics 会自动收集堆转储并暂存于 Cloud Storage。
    
3. **查看列表**：运行 `android signals heaps list --firebase-app=你的AppID` 查看已收集的堆转储，列表中包含时间、版本、设备、API 等级和大小。
    
4. **下载分析**：使用 `android signals heaps download <heap-id> --output=./heaps` 将堆转储以 **Perfetto 轨迹**格式下载到本地，然后用 Android Studio 的 Profiler 等工具打开分析。

开启 Crashlytics 的堆转储收集**不需要额外集成 SDK 库**，Crashlytics 已经内置了这一能力，你只需通过开关或命令启用即可。而它收集到的堆转储文件，在经过一步简单转换后，完全可以使用 MAT 这类专业工具进行分析。

开启动作并非在代码中集成新库，而是通过 **Firebase 命令行工具（CLI）** 来控制的

```bash
firebase crashlytics:heapdumps:enable
```

管理命令还包括 `disable`（禁用）和 `status`（查看当前状态）

Crashlytics 收集的堆转储文件，其原始格式是 Android 平台标准的 **`.hprof`** 文件，但它与 Java 标准环境下的 hprof 格式略有不同。

**直接使用 MAT 打开可能会遇到兼容性问题**。标准的处理流程是：

1. **下载**：从 Crashlytics 或通过 `android signals heaps download` 命令获取堆转储文件。
    
2. **转换**：使用 Android SDK 自带的 **`hprof-conv`** 工具，将 Android 格式的 `.hprof` 转换为 Java 标准格式。
    
3. **分析**：转换后的文件即可导入 **MAT (Memory Analyzer Tool)** 或 Android Studio 的 Profiler 进行深度分析。MAT 在分析复杂内存泄漏时，通常被认为比 IDE 自带工具更强大。

`firebase crashlytics:heapdumps:enable` 命令需要在**终端（Terminal）** 里执行，并且要先定位到你的 **Android 项目根目录**。获取堆转储文件则需要在终端通过 `android signals` 命令从云端拉取。

执行 `enable` 命令：开启堆转储收集

这个命令属于 Firebase CLI 工具集，用于在服务端开启收集开关。

**操作步骤：**

1. **安装/更新 Firebase CLI**：确保安装了最新版的 Firebase CLI（`v15.34.0` 及以上版本才包含此命令）。
    
2. **登录 Firebase**：在终端执行 `firebase login` 完成授权。
    
3. **定位到项目根目录**：使用 `cd` 命令进入你 Android 项目的根目录（即包含 `firebase.json` 或 `.firebaserc` 的文件夹）。
    
4. **执行开启命令**：输入以下命令并回车：
    
    firebase crashlytics:heapdumps:enable
    
    执行成功后，Crashlytics 就会开始收集 OOM 等低内存事件的堆转储了。




# OOM 排查思路

## 看数据

ember 最近的全量版本近 7/14/30 天，拿这些数据：

1. OOM 率 / OOM 次数，按 App 版本
2. 按手机内存分档（如 ≤3G / 4–6G / ≥8G）
3. 按 前台 / 后台
4. Top10 页面或场景
5. 若有堆栈：Top 10 OOM 签名（异常文案 + 一两帧栈）

| 问题            | 动作                   |
| ------------- | -------------------- |
| 是不是某个新版本突然变差？ | 是 → 优先看这个版本改动        |
| 是不是低端机特别严重？   | 是 → 后面优先「限缓存 / 少预加载」 |
| 是不是后台很多？      | 是 → 优先查后台还在加载广告      |
**目的：搞清楚 OOM 主要发生在什么版本、什么机、前台还是后台**。

## 问题分类

**打开一条具体 OOM 日志，看异常第一行文案**：

1.Java 堆内存不够

含 `java.lang.OutOfMemoryError: Failed to allocate ...`

2.跟图片/Bitmap 强相关

栈里出现 `Bitmap`、`BitmapFactory`、`Canvas`、图片库。

修复时优先查大图/图片缓存。

3.几乎没有 Java 业务栈，或像系统杀进程

- 只有很少帧 / 直接被 LMK
- 文案含 `Native allocation`、pthread 等

**目的：每个 Top 问题都标成 A / B / C，先处理次数最多的 1/2 两类问题**。

## 复现或拿到「内存证据」(二选一)

### 路径 1：能复现

1. 用 低内存机 或 AS 模拟器限制内存
2. 按数据里的高频路径操作（进首页、刷内容、出广告、切后台…）
3. Android Studio → Profiler → Memory
4. 看着 Java 内存曲线：
    - 只升不降 → 像泄漏
    - 某一下猛冲然后崩溃 → 像尖峰（一次加载太多）

崩溃前点 Dump Java heap，保存 hprof。

### 路径 2：不能复现

向监控要：

- 该签名的 hprof
- 或崩溃时的 Java heap used / max、PSS
- 至少要有「OOM 前是否在播广告 / 加载广告」

**目的：手里有一份 hprof，或至少知道「泄漏型还是尖峰型」**。

## 看 hprof——只做这一件事

用 Android Studio 或 MAT 打开 hprof，只找：谁占用最大。

看 Dominator / Retained Size 排行，盯前几名：

| 你看到的大户                                                     | 你下一步去哪            |
| ---------------------------------------------------------- | ----------------- |
| `byte[]` / `Bitmap` 很大                                     | 图片、广告素材、大图未回收     |
| 类名含 `poseidon` / `anythink` / `admob` / `applovin` / `max` | **广告 SDK / 聚合**   |
| ember 业务包名的 Activity/Fragment/Presenter 还在，且下面挂着一堆东西       | **App 泄漏**        |
| `WebView` 一大坨                                              | 页面/广告 WebView 没销毁 |
再看一个关键点：这个**大对象被谁引用**（Reference chain）——  引用链尽头如果是已退出的 Activity，就是典型泄漏。

**目的：最大头是 XXX，被 YYY 握着，所以问题在 App / 在 Poseidon / 两边都有**。

## 归属判定

### 结论 1：主要在 Poseidon / 广告

特征：大户是广告缓存、广告对象、mediation SDK；或 OOM 集中在「刚 load/show 广告」。

去 poseidon 查这些（不用一次全看）：

1. 广告缓存有没有上限、过期有没有 `destroy`
2. 后台是否还在 preload（看 `PreloadMemoryGuard` 是否生效）
3. 压力高时会不会清缓存（`CriticalPressureBreaker` / scavenge 开关、远端配置）
4. ember 调用是否过于频繁（SDK 再强，App 狂 preload 也会炸）

### 结论 2：主要在 ember App

特征：大户是业务页面、业务图、业务单例；广告对象不大。

去 App 查：

1. 页面退出后是否还被单例/静态变量握着
2. 列表图片是否过大、是否无限缓存
3. 是否自己又做了一套广告预加载，和 SDK 叠在一起

### 结论 3：两边都有（广告 App 很常见）

特征：Activity 泄漏链上挂着 Ad；或 App 触发太猛 + SDK 缓存太大。

修法：App 保证生命周期调用 destroy/取消；SDK 保证缓存上限和低内存清理。

**这一步完成标准： Top1 OOM 已经有明确归属，不再纠结「先看哪个工程」**。

## 优化修复（按类型选，不要全上）

### 若是「尖峰」（曲线突然冲高）

按优先级做，做完一项就停下来看效果：

1. 降低同时 preload / load 的广告数
2. 低端机减少缓存个数
3. 大图/素材降级或延后加载
4. 避开启动后立刻狂加载

### 若是「泄漏」（曲线台阶式上升）

1. 页面 `onDestroy` 里销毁广告、清监听、WebView destroy
2. 去掉静态变量持有 Activity/Ad
3. 确认 SDK 的 show/load Cancelable、Destroyable 有没有按文档调用

### 若是「后台 OOM」

1. 后台停止或大幅降频 preload
2. 确认 poseidon 后台低内存 guard 开关是开的
3. App 自身后台任务也要降级

### 修完怎么验收（很重要）

灰度对比：

- OOM 率是否下降
- 同时看广告收入/填充（避免内存好了、钱没了）

