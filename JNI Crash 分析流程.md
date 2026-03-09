# JNI Crash 分析流程与优化方法论

## 一、JNI Crash 概述与特征识别

### JNI Crash 与 Java Crash 的区别

| 特征 | Java/Kotlin Crash | JNI Crash |
|------|-------------------|-----------|
| **异常类型** | Java Exception | 信号（SIGSEGV, SIGABRT等） |
| **堆栈格式** | Java 方法调用栈 | Native 二进制堆栈 |
| **错误信息** | Exception 类型和消息 | 信号代码、内存地址、寄存器状态 |
| **分析工具** | Android Studio, 日志分析 | ndk-stack, addr2line, objdump |

### JNI Crash 典型特征
- **信号类型**：`SIGSEGV`（段错误）、`SIGABRT`（中止信号）、`SIGBUS`（总线错误）
- **关键标识**：包含 `.so` 库文件的调用栈
- **内存地址**：`fault addr` 显示具体的内存地址
- **寄存器状态**：完整的 CPU 寄存器转储

## 二、JNI Crash 分析工具链

### 必备工具
1. **NDK 工具链** - 必须与编译时使用的 NDK 版本一致
2. **带符号的 .so 文件** - 编译时生成的未剥离符号的库文件
3. **崩溃日志** - 完整的 logcat 输出

### 工具定位
```bash
# NDK 工具路径示例
/Users/username/Library/Android/sdk/ndk/版本号/

# 主要工具：
- ndk-stack                    # 首选分析工具
- toolchains/*/bin/addr2line   # 地址行号转换
- toolchains/*/bin/objdump     # 反汇编分析
```

## 三、完整分析流程

### 步骤 1：捕获并保存崩溃日志

```bash
# 方法1：实时捕获
adb logcat | grep -E "SIGSEGV|SIGABRT|Fatal signal"

# 方法2：保存完整日志
adb logcat -d > crash_log.txt

# 方法3：仅保存崩溃相关
adb logcat -d | grep -A 50 -B 5 "Fatal signal" > jni_crash.txt
```

### 步骤 2：定位符号文件

**符号文件位置**：
```
app/build/intermediates/cmake/debug/obj/
├── arm64-v8a/libnative-lib.so     # 64位 ARM
├── armeabi-v7a/libnative-lib.so   # 32位 ARM  
└── x86_64/libnative-lib.so        # x86 64位
```

**关键要求**：
- 必须使用**编译时生成的原始 .so 文件**
- NDK 版本必须与编译时完全一致
- 架构必须匹配（arm64-v8a, armeabi-v7a等）

### 步骤 3：使用 ndk-stack 分析（推荐首选）

```bash
# 基本用法
ndk-stack -sym <符号文件目录> -dump <崩溃日志文件>

# 具体示例
/Users/zhangyunxiang/Library/Android/sdk/ndk/25.1.8937393/ndk-stack \
  -sym app/build/intermediates/cmake/debug/obj/arm64-v8a/ \
  -dump crash_log.txt
```

**输出解析**：
```
Stack frame #00 pc 000000000000ef88  /apex/com.android.runtime/lib64/bionic/libc.so (__memcpy+56)
Stack frame #01 pc 0000000000000f64  /data/app/~~.../lib/arm64/libnative-lib.so 
  (some_c_function()+40): Routine some_c_function() at /path/to/native-lib.cpp:25
Stack frame #02 pc 0000000000000f34  /data/app/~~.../lib/arm64/libnative-lib.so 
  (Java_com_example_jnicrash_MainActivity_useNativeData+24): 
  Routine Java_com_example_jnicrash_MainActivity_useNativeData at /path/to/native-lib.cpp:35
```

**关键信息**：
- **文件路径**：精确到源码文件和行号
- **函数名称**：C++ 函数或 JNI 函数名
- **内存地址**：崩溃时的程序计数器值

### 步骤 4：使用 addr2line 精确分析（备选方法）

当需要更精确的定位或 ndk-stack 输出不清晰时使用：

```bash
# 根据架构选择对应的工具链
# arm64
aarch64-linux-android-addr2line -f -e libnative-lib.so <pc_address>

# armeabi-v7a  
arm-linux-androideabi-addr2line -f -e libnative-lib.so <pc_address>

# 示例
/Users/zhangyunxiang/Library/Android/sdk/ndk/25.1.8937393/toolchains/
aarch64-linux-android-4.9/prebuilt/darwin-x86_64/bin/aarch64-linux-android-addr2line \
  -f -e app/build/intermediates/cmake/debug/obj/arm64-v8a/libnative-lib.so \
  0000000000000f34
```

**输出**：
```
Java_com_example_jnicrash_MainActivity_useNativeData
/path/to/project/app/src/main/cpp/native-lib.cpp:35
```

### 步骤 5：使用 objdump 反汇编分析（高级调试）

当需要分析汇编指令级别的崩溃时使用：

```bash
# 生成反汇编文件
aarch64-linux-android-objdump -S libnative-lib.so > disassembly.txt

# 搜索特定函数
grep -A 20 "Java_com_example_jnicrash_MainActivity_useNativeData" disassembly.txt
```

## 四、常见 JNI Crash 模式分析

### 1. 空指针解引用（SIGSEGV, fault addr 0x0）
```cpp
// 崩溃代码
DataHolder* holder = nullptr;
holder->value = 42;  // SIGSEGV here!

// 分析特征
// fault addr 0x0, 访问空指针
```

### 2. 内存越界（SIGSEGV, fault addr 非零）
```cpp
// 崩溃代码  
char buffer[10];
buffer[20] = 'x';  // 栈溢出

// 分析特征
// fault addr 指向无效内存区域
```

### 3. 使用已释放内存（SIGSEGV）
```cpp
// 崩溃代码
delete[] data;
data[0] = 1;  // 使用已释放内存
```

### 4. JNI 环境错误（SIGABRT）
```cpp
// 崩溃代码
JNIEnv* env = nullptr;
env->CallVoidMethod(obj, methodID);  // 无效的 JNIEnv
```

## 五、寄存器状态分析

### ARM64 寄存器关键含义
```
x0-x7    : 函数参数传递
x8       : 间接结果寄存器
x9-x15   : 临时寄存器
x16-x17  : 链接寄存器
x18      : 平台寄存器
x19-x28  : 校准寄存器
x29      : 帧指针 (FP)
x30      : 链接寄存器 (LR)  
sp       : 栈指针
pc       : 程序计数器 ← 关键！
```

### 寄存器分析示例
```
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
    x0  0000000000000000  ← 参数1为0，可能是空指针
    x19 0000000000000000  ← 寄存器x19为0
    ...
    pc  0000007acb0d8f88  ← 崩溃时的指令地址
```

## 六、实战案例：空指针崩溃分析

### 崩溃代码
```cpp
struct DataHolder {
    int importantValue;
    char* buffer;
};

DataHolder* g_dataHolder = nullptr;  // 未初始化

extern "C" JNIEXPORT void JNICALL
Java_com_example_MainActivity_useNativeData(JNIEnv* env, jobject thiz) {
    g_dataHolder->importantValue = 42;  // 崩溃行
}
```

### 崩溃日志关键信息
```
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
backtrace:
    #00 pc 000000000000ef88  /apex/com.android.runtime/lib64/bionic/libc.so
    #01 pc 0000000000000f64  /data/.../libnative-lib.so (some_c_function()+40)
    #02 pc 0000000000000f34  /data/.../libnative-lib.so (Java_com_example_MainActivity_useNativeData+24)
```

### 分析过程
1. **信号分析**：`SIGSEGV` + `fault addr 0x0` → 空指针访问
2. **堆栈定位**：`Java_com_example_MainActivity_useNativeData+24`
3. **源码映射**：使用 ndk-stack 定位到具体文件和行号
4. **问题确认**：全局变量 `g_dataHolder` 未初始化

### 修复方案
```cpp
// 修复：添加初始化和空指针检查
extern "C" JNIEXPORT void JNICALL
Java_com_example_MainActivity_initNativeData(JNIEnv* env, jobject thiz) {
    if (!g_dataHolder) {
        g_dataHolder = new DataHolder();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_MainActivity_useNativeData(JNIEnv* env, jobject thiz) {
    if (!g_dataHolder) {
        // 错误处理或初始化
        return;
    }
    g_dataHolder->importantValue = 42;
}
```

## 七、预防与调试最佳实践

### 1. 启用 AddressSanitizer
**CMakeLists.txt**:
```cmake
target_compile_options(native-lib PRIVATE 
    -fsanitize=address 
    -fno-omit-frame-pointer
)
target_link_options(native-lib PRIVATE -fsanitize=address)
```

### 2. JNI 调试辅助函数
```cpp
// JNI 异常检查
void checkJNIException(JNIEnv* env, const char* location) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();  // 输出到 logcat
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, "JNIDebug", 
                           "JNI Exception at: %s", location);
    }
}

// 指针安全检查
template<typename T>
bool validatePointer(T* ptr, const char* name) {
    if (ptr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "JNIDebug", 
                           "Null pointer: %s", name);
        return false;
    }
    return true;
}
```

### 3. 结构化错误处理
```cpp
extern "C" JNIEXPORT jint JNICALL
Java_com_example_MainActivity_safeNativeCall(JNIEnv* env, jobject thiz) {
    // 输入验证
    if (!thiz) {
        __android_log_print(ANDROID_LOG_ERROR, "JNI", "Null jobject");
        return -1;
    }
    
    // 资源初始化
    if (!g_dataHolder && !initDataHolder()) {
        return -2;
    }
    
    // 主逻辑
    int result = processData();
    
    // 异常检查
    checkJNIException(env, "safeNativeCall");
    
    return result;
}
```

## 八、系统化分析流程

### 快速排查 Checklist

| 步骤 | 操作 | 目标 |
|------|------|------|
| 1️⃣ | 确认信号类型 | 识别是 SIGSEGV、SIGABRT 还是其他信号 |
| 2️⃣ | 检查 fault addr | 0x0 → 空指针，非零 → 内存越界 |
| 3️⃣ | 定位 .so 文件 | 确认崩溃发生在哪个原生库 |
| 4️⃣ | 准备符号文件 | 找到对应架构的未剥离 .so |
| 5️⃣ | 使用 ndk-stack | 快速获取源码文件和行号 |
| 6️⃣ | 验证修复 | 重现问题并确认修复有效 |

### 工具选择指南

| 场景 | 推荐工具 | 优点 |
|------|----------|------|
| **快速定位** | ndk-stack | 一键分析，输出友好 |
| **精确行号** | addr2line | 直接映射地址到源码行 |
| **指令分析** | objdump | 查看汇编指令，深入分析 |
| **内存检测** | AddressSanitizer | 运行时内存错误检测 |

通过这套完整的 JNI Crash 分析方法论，可以系统性地定位和修复 Native 层的崩溃问题。关键是掌握工具链的使用和理解各种崩溃模式的特征。