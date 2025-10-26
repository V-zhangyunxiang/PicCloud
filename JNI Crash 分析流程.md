我将通过一个完整的示例来演示 JNI Crash 的分析步骤和工具使用。

## 🐞 示例：一个典型的空指针 JNI Crash

### 1. 编写有问题的 JNI 代码

**native-lib.cpp**
```cpp
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "JNIDemo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 一个有问题的结构体
struct DataHolder {
    int importantValue;
    char* buffer;
};

// 全局变量
DataHolder* g_dataHolder = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_example_jnicrash_MainActivity_initNativeData(JNIEnv* env, jobject thiz) {
    // 忘记初始化 g_dataHolder!
    // g_dataHolder = new DataHolder();  // 这行被注释掉了，制造问题
    LOGI("initNativeData called");
}

extern "C" JNIEXPORT void JNICALL  
Java_com_example_jnicrash_MainActivity_useNativeData(JNIEnv* env, jobject thiz) {
    // 这里会崩溃：访问未初始化的指针
    g_dataHolder->importantValue = 42;  // SIGSEGV here!
    LOGI("Value set to: %d", g_dataHolder->importantValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_jnicrash_MainActivity_cleanupNativeData(JNIEnv* env, jobject thiz) {
    if (g_dataHolder) {
        delete g_dataHolder;
        g_dataHolder = nullptr;
    }
    LOGI("cleanupNativeData called");
}
```

**MainActivity.java**
```java
package com.example.jnicrash;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button crashButton = findViewById(R.id.crash_button);
        crashButton.setOnClickListener(v -> {
            initNativeData();      // 初始化（但实际没初始化）
            useNativeData();       // 这里会崩溃！
        });
    }

    public native void initNativeData();
    public native void useNativeData();
    public native void cleanupNativeData();
}
```

### 2. 编译并获取崩溃日志

应用运行后点击按钮，会在 logcat 中得到类似这样的崩溃信息：

```
--------- beginning of crash
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 12345 (com.example.jnicrash), pid 12345 (com.example.jnicrash)
Build fingerprint: 'google/sdk_gphone64_arm64/emulator64_arm64:13/...'
Revision: '0'
ABI: 'arm64'
pid: 12345, tid: 12345, name: com.example.jnicrash  >>> com.example.jnicrash <<<
uid: 10123
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
    x0  0000000000000000  x1  0000000000000000  x2  0000000000000000  x3  0000000000000000
    x4  0000000000000000  x5  0000000000000000  x6  0000000000000000  x7  0000000000000000
    x8  0000000000000000  x9  0000000000000000  x10 0000000000000000  x11 0000000000000000
    x12 0000000000000000  x13 0000000000000000  x14 0000000000000000  x15 0000000000000000
    x16 0000007ae3a8dfc0  x17 0000007ae3a2e9a0  x18 0000007acb0e4000  x19 0000000000000000
    x20 0000007acb1c9c20  x21 0000000000000000  x22 0000000000000000  x23 0000000000000000
    x24 0000000000000000  x25 0000000000000000  x26 0000000000000000  x27 0000000000000000
    x28 0000000000000000  x29 0000007fc9d93bd0
    sp  0000007fc9d93bb0  lr  0000007acb0d8f6c  pc  0000007acb0d8f88

backtrace:
    #00 pc 000000000000ef88  /apex/com.android.runtime/lib64/bionic/libc.so (__memcpy+56) (BuildId: ...)
    #01 pc 0000000000000f64  /data/app/~~.../lib/arm64/libnative-lib.so (_Z15some_c_functionv+40) (BuildId: ...)
    #02 pc 0000000000000f34  /data/app/~~.../lib/arm64/libnative-lib.so (Java_com_example_jnicrash_MainActivity_useNativeData+24) (BuildId: ...)
    #03 pc 0000000000001234  /apex/com.android.art/lib64/libart.so (art_quick_generic_jni_trampoline+148) (BuildId: ...)
```

### 3. 分析步骤

#### 步骤 1：保存崩溃日志到文件
```bash
# 将 logcat 中的崩溃信息保存为文件
adb logcat -d > crash_log.txt
```

#### 步骤 2：准备符号文件
确保你有编译时生成的未剥离的 `.so` 文件。在 Android Studio 项目中，它们通常位于：
```
app/build/intermediates/cmake/debug/obj/arm64-v8a/libnative-lib.so
```
**必须使用与编译 .so 文件时相同版本的 NDK！**
#### 步骤 3：使用 ndk-stack 分析（推荐首选）

```bash
# 在 NDK 工具目录下执行（或配置环境变量）
/Users/zhangyunxiang/Library/Android/sdk/ndk/版本号/ndk-stack -sym app/build/intermediates/cmake/debug/obj/arm64-v8a/ -dump crash_log.txt
```

**输出结果：**
```
********** Crash dump: **********
Build fingerprint: 'google/sdk_gphone64_arm64/emulator64_arm64:13/...'
pid: 12345, tid: 12345, name: com.example.jnicrash  >>> com.example.jnicrash <<<
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0

Stack frame #00 pc 000000000000ef88  /apex/com.android.runtime/lib64/bionic/libc.so (__memcpy+56)
Stack frame #01 pc 0000000000000f64  /data/app/~~.../lib/arm64/libnative-lib.so (some_c_function()+40): Routine some_c_function() at /path/to/project/app/src/main/cpp/native-lib.cpp:25
Stack frame #02 pc 0000000000000f34  /data/app/~~.../lib/arm64/libnative-lib.so (Java_com_example_jnicrash_MainActivity_useNativeData+24): Routine Java_com_example_jnicrash_MainActivity_useNativeData at /path/to/project/app/src/main/cpp/native-lib.cpp:35
Stack frame #03 pc 0000000000001234  /apex/com.android.art/lib64/libart.so (art_quick_generic_jni_trampoline+148)
```

**关键发现：**
- 崩溃发生在 `native-lib.cpp` 第 25 行
- 在函数 `Java_com_example_jnicrash_MainActivity_useNativeData` 中
- 错误是 `SIGSEGV`，地址 `0x0` → 典型的空指针解引用

#### 步骤 4：使用 addr2line 精确分析（备选方法）

如果需要更详细的分析，可以使用 addr2line：

```bash
# 找到崩溃的地址（从 backtrace #02 的 pc 值）
# 对于 arm64 架构，使用对应的工具链
/Users/zhangyunxiang/Library/Android/sdk/ndk/版本号/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-addr2line \
  -f -e app/build/intermediates/cmake/debug/obj/arm64-v8a/libnative-lib.so 0000000000000f34
```

**输出结果：**
```
Java_com_example_jnicrash_MainActivity_useNativeData
/path/to/project/app/src/main/cpp/native-lib.cpp:35
```

#### 步骤 5：使用 objdump 反汇编分析（高级）

```bash
# 反汇编查看具体指令
/path/to/your/ndk/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-objdump \
  -S app/build/intermediates/cmake/debug/obj/arm64-v8a/libnative-lib.so > disassembly.txt
```

在反汇编文件中搜索相关函数，可以看到具体的汇编指令。

### 4. 问题定位与修复

通过上述分析，我们定位到问题：

**问题文件：** `native-lib.cpp` 第 25 行
**问题代码：** `g_dataHolder->importantValue = 42;`
**根本原因：** `g_dataHolder` 未初始化，值为 `nullptr`

**修复方案：**
```cpp
extern "C" JNIEXPORT void JNICALL
Java_com_example_jnicrash_MainActivity_initNativeData(JNIEnv* env, jobject thiz) {
    // 修复：正确初始化
    if (!g_dataHolder) {
        g_dataHolder = new DataHolder();
        g_dataHolder->importantValue = 0;
        g_dataHolder->buffer = nullptr;
    }
    LOGI("initNativeData called - properly initialized");
}

extern "C" JNIEXPORT void JNICALL  
Java_com_example_jnicrash_MainActivity_useNativeData(JNIEnv* env, jobject thiz) {
    // 添加空指针检查
    if (!g_dataHolder) {
        LOGI("Error: g_dataHolder is null!");
        return;
    }
    g_dataHolder->importantValue = 42;
    LOGI("Value set to: %d", g_dataHolder->importantValue);
}
```

### 5. 预防措施

#### 使用 AddressSanitizer 检测内存问题

在 `CMakeLists.txt` 中启用：
```cmake
target_compile_options(native-lib PRIVATE -fsanitize=address -fno-omit-frame-pointer)
target_link_options(native-lib PRIVATE -fsanitize=address)
```

在 `build.gradle` 中：
```groovy
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_TOOLCHAIN=clang",
                          "-DANDROID_STL=c++_shared"
            }
        }
    }
}
```

#### 添加 JNI 调试辅助函数

```cpp
// 调试辅助函数
void checkJNIException(JNIEnv* env, const char* location) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, "JNIDebug", 
                           "JNI Exception at: %s", location);
    }
}

void logNativePointer(const char* name, void* ptr) {
    __android_log_print(ANDROID_LOG_DEBUG, "JNIDebug", 
                       "%s pointer: %p", name, ptr);
}
```

### 📋 总结：系统性分析流程

1. **捕获日志** - 保存完整的崩溃日志
2. **识别特征** - 确认是 JNI Crash（SIGSEGV/SIGABRT + .so 文件）
3. **准备符号** - 找到对应的未剥离 .so 文件
4. **工具分析** - 使用 `ndk-stack` 或 `addr2line` 定位问题
5. **代码修复** - 根据分析结果修复代码
6. **预防加固** - 添加检查和使用检测工具

这个完整示例展示了从问题产生到分析定位的全过程，遵循这个流程可以系统性地解决大多数 JNI Crash 问题。

