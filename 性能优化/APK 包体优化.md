# 瘦身优势

- 下载转化率。

- 头部 App 都有 lite 版。

- 渠道合作商要求。

# Apk 组成

代码相关: classes.dex。
资源相关: res、asserts、resource.arsc。
SO 相关: lib。

# Apk 分析

1. ApkTool 反编译工具
2. Analyze APK Android Studio 分析工具

# 代码混淆

**含义**

功能等价但改变形式

- 代码元素改成无意义的名字
- 以更难以理解的形式重写部分逻辑
- 打乱代码格式


**实现方式**

 Proguard 代码混淆

 - 配置 minifyEnable 为 true，debug 下不配置。
 - proguard-rules 中配置相应规则。

# 三方库处理

- 基础库统一，如网络、图片

- 选择更小的库

- 仅引入需要的部分代码

# 移除无用代码

- 使用 AOP 统计类使用情况，建议 HOOK 构造函数。

# 资源瘦身

冗余资源

 右键-Remove Unused Resource 发现使用方。
 shrinkResources 移除未引用资源。

图片压缩

- 使用 TinyPNG 压缩
- 格式转换为 WebP

# SO 瘦身

so 是 Android 上的动态链接库。

## 过滤 CPU 架构

有多种不同类型的 CPU 架构，build.gradle 中使用 abiFilters 设置支持的 So 架构，建议使用 armabi-v8a。

## SO 动态下载

在编译期把资源上传到服务端，运行时再对资源进行下载和解压。业务方使用时校验 so 版本号和 md5，可用则直接复用。

# 长期治理

管控发版组件体积，超过阈值必须优化。




