#!/usr/bin/env bash
# 打包司南 release APK：Gradle 构建 -> zipalign -> apksigner 签名 -> 校验 -> 输出 dist/。
#
# 默认使用本机 debug keystore 签名（内测/自用，不可上架）；
# 传入 --keystore 等参数可改用正式 keystore。密码通过文件传给 apksigner（file: 语法），
# 脚本自身不读取、不回显密码内容。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
用法: tools/package_release.sh [选项]

构建 release APK 并用 keystore 签名，默认输出到 dist/sinan-<版本>-release.apk。

签名（默认）:
  不指定 --keystore 时，使用 ~/.android/debug.keystore 签名，产出可直接安装的包，
  但不能用于应用商店上架。

选项:
  --keystore PATH        正式 keystore 路径
  --ks-alias NAME        keystore 别名（使用 --keystore 时必填；debug 默认 androiddebugkey）
  --ks-pass-file PATH    含 keystore 密码的文件（每行一个密码，取第一行；使用 --keystore 时必填）
  --key-pass-file PATH   含私钥密码的文件（缺省同 keystore 密码）
  --output PATH          输出 APK 路径（默认 dist/sinan-<versionName>-release.apk）
  --lint                 额外执行 :app:lintRelease（assembleRelease 已含 lintVital）
  -h, --help             显示本帮助
EOF
}

KEYSTORE=""
KS_ALIAS=""
KS_PASS_FILE=""
KEY_PASS_FILE=""
OUTPUT=""
RUN_FULL_LINT=0

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore)      KEYSTORE="${2:?--keystore 需要参数}"; shift 2 ;;
    --ks-alias)      KS_ALIAS="${2:?--ks-alias 需要参数}"; shift 2 ;;
    --ks-pass-file)  KS_PASS_FILE="${2:?--ks-pass-file 需要参数}"; shift 2 ;;
    --key-pass-file) KEY_PASS_FILE="${2:?--key-pass-file 需要参数}"; shift 2 ;;
    --output)        OUTPUT="${2:?--output 需要参数}"; shift 2 ;;
    --lint)          RUN_FULL_LINT=1; shift ;;
    -h|--help)       usage; exit 0 ;;
    *) echo "错误：未知参数 $1" >&2; usage >&2; exit 2 ;;
  esac
done

# -------- 定位 Android SDK 与 build-tools --------
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
  echo "错误：找不到 Android SDK，请设置 ANDROID_HOME/ANDROID_SDK_ROOT 或 local.properties 的 sdk.dir" >&2
  exit 1
fi

BT_DIR="$(ls -d "$SDK_DIR"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
BT_DIR="${BT_DIR%/}"
if [ -z "$BT_DIR" ] || [ ! -x "$BT_DIR/apksigner" ] || [ ! -x "$BT_DIR/zipalign" ]; then
  echo "错误：在 $SDK_DIR/build-tools 下找不到可用的 apksigner/zipalign" >&2
  exit 1
fi
echo ">> Android SDK : $SDK_DIR"
echo ">> Build Tools : $BT_DIR"

# -------- 签名策略 --------
SIGN_MODE="debug"
if [ -z "$KEYSTORE" ]; then
  KEYSTORE="$HOME/.android/debug.keystore"
  KS_ALIAS="${KS_ALIAS:-androiddebugkey}"
  if [ ! -f "$KEYSTORE" ]; then
    echo "错误：找不到 debug keystore: ${KEYSTORE}（先运行一次 debug 构建或指定 --keystore）" >&2
    exit 1
  fi
  SIGN_ARGS=(--ks-pass "pass:android" --key-pass "pass:android")
else
  SIGN_MODE="custom"
  [ -f "$KEYSTORE" ] || { echo "错误：找不到 keystore: $KEYSTORE" >&2; exit 1; }
  [ -n "$KS_ALIAS" ] || { echo "错误：使用 --keystore 时必须提供 --ks-alias" >&2; exit 1; }
  [ -n "$KS_PASS_FILE" ] || { echo "错误：使用 --keystore 时必须提供 --ks-pass-file" >&2; exit 1; }
  [ -f "$KS_PASS_FILE" ] || { echo "错误：找不到密码文件: $KS_PASS_FILE" >&2; exit 1; }
  SIGN_ARGS=(--ks-pass "file:$KS_PASS_FILE")
  if [ -n "$KEY_PASS_FILE" ]; then
    [ -f "$KEY_PASS_FILE" ] || { echo "错误：找不到私钥密码文件: $KEY_PASS_FILE" >&2; exit 1; }
    SIGN_ARGS+=(--key-pass "file:$KEY_PASS_FILE")
  fi
fi

# -------- Gradle 构建 --------
GRADLE_TASKS=(:app:assembleRelease)
if [ "$RUN_FULL_LINT" = 1 ]; then
  GRADLE_TASKS+=(:app:lintRelease)
fi
echo ">> 构建: ./gradlew ${GRADLE_TASKS[*]}"
./gradlew "${GRADLE_TASKS[@]}" --console=plain

RELEASE_DIR="app/build/outputs/apk/release"
UNSIGNED=""
if [ -f "$RELEASE_DIR/app-release-unsigned.apk" ]; then
  UNSIGNED="$RELEASE_DIR/app-release-unsigned.apk"
elif [ -f "$RELEASE_DIR/app-release.apk" ]; then
  # 构建脚本已配置签名时，产物即 app-release.apk；仍重新对齐并签名。
  UNSIGNED="$RELEASE_DIR/app-release.apk"
else
  echo "错误：未找到 release APK 产物（${RELEASE_DIR}）" >&2
  exit 1
fi

VERSION_NAME="$("$BT_DIR/aapt2" dump badging "$UNSIGNED" 2>/dev/null \
  | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" | head -1)"
VERSION_NAME="${VERSION_NAME:-unknown}"

# -------- zipalign + 签名 --------
ALIGNED="$RELEASE_DIR/app-release-aligned.apk"
echo ">> zipalign"
"$BT_DIR/zipalign" -p -f 4 "$UNSIGNED" "$ALIGNED"

if [ -z "$OUTPUT" ]; then
  mkdir -p dist
  OUTPUT="dist/sinan-${VERSION_NAME}-release.apk"
else
  mkdir -p "$(dirname "$OUTPUT")"
fi

echo ">> 签名（${SIGN_MODE}）"
"$BT_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" "${SIGN_ARGS[@]}" --out "$OUTPUT" "$ALIGNED"

rm -f "$ALIGNED"

# -------- 校验与摘要 --------
echo ">> 校验签名"
"$BT_DIR/apksigner" verify --print-certs "$OUTPUT"

echo
echo "==================================================="
echo " 打包完成"
echo " 文件    : $OUTPUT"
echo " 版本    : $VERSION_NAME"
echo " 签名模式: $SIGN_MODE"
if [ "$SIGN_MODE" = "debug" ]; then
  echo " 注意    : 使用 debug 密钥，仅内测/自用，不可上架"
fi
ls -l "$OUTPUT"
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$OUTPUT"
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUTPUT"
fi
echo "==================================================="
