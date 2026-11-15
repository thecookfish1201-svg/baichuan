#!/bin/bash
# 百川本机构建环境（容器内 arm64 Ubuntu）
# 用法：source tools/env.sh
export JAVA_HOME=/opt/toolchain/jdk-17.0.21+9
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_HOME=/opt/toolchain/gradle-8.11.1
export PATH=$JAVA_HOME/bin:$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
# JDK 默认编码不是 UTF-8，中文路径会导致 Gradle 崩溃，必须显式指定
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
# 注意：不要在全局导出 LD_LIBRARY_PATH，aapt2 的 bionic 库只在包装脚本内设置，
# 否则会污染所有 glibc 程序（java/python/gradle 都会崩溃）。
