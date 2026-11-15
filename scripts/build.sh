#!/bin/bash
# 构建百川 Android 工程（本机容器环境）
# 用法：scripts/build.sh [assembleDebug|assembleRelease|clean ...]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/tools/env.sh"
cd "$ROOT/app"
ARG="${1:-assembleDebug}"
echo "[build] gradle $ARG"
gradle --no-daemon --offline --console=plain "$ARG"
