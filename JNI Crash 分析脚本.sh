#!/bin/bash

# 设置 NDK 路径（修改为你的实际路径）
export NDK_HOME=/Users/yourname/Library/Android/sdk/ndk/25.1.8937393

# 检查参数
if [ "$#" -ne 2 ]; then
    echo "用法: $0 <崩溃日志文件> <架构>"
    echo "架构选项: arm64-v8a, armeabi-v7a, x86_64"
    exit 1
fi

CRASH_LOG=$1
ARCH=$2
SYM_DIR="app/build/intermediates/cmake/debug/obj/${ARCH}"

echo "=== 开始分析 JNI Crash ==="
echo "崩溃日志: $CRASH_LOG"
echo "架构: $ARCH"
echo "符号目录: $SYM_DIR"
echo

# 检查文件是否存在
if [ ! -f "$CRASH_LOG" ]; then
    echo "错误: 崩溃日志文件不存在: $CRASH_LOG"
    exit 1
fi

if [ ! -d "$SYM_DIR" ]; then
    echo "错误: 符号目录不存在: $SYM_DIR"
    echo "请确保已构建 Debug 版本并保留未剥离的 .so 文件"
    exit 1
fi

echo "1. 使用 ndk-stack 分析..."
$NDK_HOME/ndk-stack -sym $SYM_DIR -dump $CRASH_LOG

echo
echo "2. 提取关键内存地址进行详细分析..."
# 从崩溃日志中提取 pc 地址
PC_ADDRESS=$(grep "pc" $CRASH_LOG | head -1 | awk '{print $NF}')
if [ ! -z "$PC_ADDRESS" ]; then
    echo "关键地址: $PC_ADDRESS"
    
    # 使用 addr2line 详细分析
    TOOLCHAIN_DIR="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
    case $ARCH in
        "arm64-v8a")
            ADDR2LINE="$TOOLCHAIN_DIR/bin/aarch64-linux-android-addr2line"
            ;;
        "armeabi-v7a") 
            ADDR2LINE="$TOOLCHAIN_DIR/bin/arm-linux-androideabi-addr2line"
            ;;
        "x86_64")
            ADDR2LINE="$TOOLCHAIN_DIR/bin/x86_64-linux-android-addr2line"
            ;;
    esac
    
    if [ -f "$ADDR2LINE" ]; then
        SO_FILE="$SYM_DIR/libnative-lib.so"
        echo "使用 addr2line 分析地址:"
        $ADDR2LINE -f -e $SO_FILE $PC_ADDRESS
    fi
fi

echo
echo "=== 分析完成 ==="