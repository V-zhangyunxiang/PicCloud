# 基本背景

目标：优化 ember 的 crash、anr、oom 性能指标，确定问题点，调研优化方案。
涉及代码：poseidon SDK、ember 业务代码。
指标数据：联系杨怀冬获取性能指标数据。

ANR 明显比 Crash 多，Crashlytics 没有开启堆转储，导致采集不到 hprof 数据。

ANR 集中在后台触发，Android 17 占比较高。

线下通过 leak Cancary 收集内存泄漏。

Your app is experiencing memory issues. Enable heap dump collection to troubleshoot them more effectively.

Crashlytics 发现你的 App 在“悄悄”因内存问题崩溃，但缺乏足够的数据来帮你定位具体的代码原因。它建议你开启堆转储收集，以便捕获内存快照，**从“知道发生了 OOM”进化到“知道 OOM 时是哪段代码占用内存过多”**。

# 如何开启与使用堆转储？

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