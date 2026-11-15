#!/bin/bash
# 发布前检查与构建记录。工作区必须干净（已跟踪文件无修改）。
# 用法：scripts/release.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "[release] 错误：工作区存在未提交修改，不能发布："
  git status --short
  exit 1
fi

VERSION="$(grep -m1 'versionName' app/app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')"
CODE="$(grep -m1 'versionCode' app/app/build.gradle.kts | sed 's/[^0-9]//g')"
COMMIT="$(git rev-parse HEAD)"
source "$ROOT/tools/env.sh"

cd "$ROOT/app"
echo "[release] 构建 $VERSION ($CODE) @ $COMMIT"
gradle --no-daemon --offline --console=plain assembleDebug

APK="$ROOT/app/app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK" || { echo "[release] 未找到 APK"; exit 1; }
SHA="$(sha256sum "$APK" | awk '{print $1}')"
SIZE="$(stat -c%s "$APK")"
DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
INFO="$ROOT/app/app/build/outputs/apk/debug/build-info.txt"
{
  echo "versionName=$VERSION"
  echo "versionCode=$CODE"
  echo "commit=$COMMIT"
  echo "builtAt=$DATE"
  echo "apk=$APK"
  echo "size=$SIZE"
  echo "sha256=$SHA"
} | tee "$INFO"
