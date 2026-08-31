#!/bin/bash
# DSH 手机端构建脚本
#
# 用法：
#   ./build.sh                # 构建 debug APK
#   ./build.sh assembleRelease # 传递任意 Gradle 任务
#
# 所有工具链路径均可通过环境变量覆盖，未设置时会自动探测常见安装位置，
# 因此在任何人的机器上都能直接运行（不依赖本机特定的目录结构）。
#
# 可覆盖的环境变量：
#   JAVA_HOME          JDK 17 安装路径
#   ANDROID_HOME       Android SDK 路径（ANDROID_SDK_ROOT 亦可）
#   GRADLE_USER_HOME   Gradle 缓存目录（默认使用仓库内 .gradle-gu）
#   GRADLE_CMD         gradle 可执行文件路径（默认优先探测，否则用 ./gradlew）

set -euo pipefail

# 解析脚本自身所在目录（支持从任意位置调用）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---- JDK 17 ----
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif [ -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  elif [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  else
    echo "错误：未找到 JDK 17，请先安装并设置 JAVA_HOME" >&2
    exit 1
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# ---- Android SDK ----
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME="$ANDROID_SDK_ROOT"
  else
    for candidate in \
      "$HOME/android-toolchain/android-sdk" \
      "$HOME/Android/Sdk" \
      "$HOME/Library/Android/sdk" \
      "/usr/local/android-sdk" \
      "/opt/android-sdk"
    do
      if [ -d "$candidate" ]; then
        ANDROID_HOME="$candidate"
        break
      fi
    done
  fi
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "错误：未找到 Android SDK，请安装后设置 ANDROID_HOME" >&2
  exit 1
fi
export ANDROID_HOME

# ---- Gradle 缓存目录 ----
# 默认放在仓库本地（相对路径），避免污染或损坏用户 ~/.gradle
if [ -z "${GRADLE_USER_HOME:-}" ]; then
  GRADLE_USER_HOME="$SCRIPT_DIR/.gradle-gu"
fi
export GRADLE_USER_HOME

# ---- gradle 可执行文件 ----
# 优先使用环境变量指定，其次探测本地工具链，最后回退到仓库自带的 Gradle Wrapper
if [ -z "${GRADLE_CMD:-}" ]; then
  if [ -x "$HOME/android-toolchain/gradle-8.11.1/bin/gradle" ]; then
    GRADLE_CMD="$HOME/android-toolchain/gradle-8.11.1/bin/gradle"
  else
    GRADLE_CMD="./gradlew"
    chmod +x ./gradlew 2>/dev/null || true
  fi
fi

echo "JAVA_HOME        = $JAVA_HOME"
echo "ANDROID_HOME     = $ANDROID_HOME"
echo "GRADLE_USER_HOME = $GRADLE_USER_HOME"
echo "GRADLE_CMD       = $GRADLE_CMD"
echo '=== assembleDebug ==='

# 仓库内 wrapper 首次运行较慢（需下载 Gradle 发行包），本地工具链则直接可用
"$GRADLE_CMD" --no-daemon --console=plain assembleDebug "$@"
echo "BUILD_EXIT=$?"
echo '==== APK ===='
ls -lh app/build/outputs/apk/debug/*.apk 2>/dev/null || true
