#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SIGNING_ZIP="${1:-}"
[[ -n "$SIGNING_ZIP" && -f "$SIGNING_ZIP" ]] || {
  echo "需要签名备份 zip：./tools/build_signed_update.sh /path/to/MedianBrowser-signing-backup.zip" >&2
  exit 2
}

TMP="$(mktemp -d)"
trap 'find "$TMP" -depth -delete' EXIT
unzip -q "$SIGNING_ZIP" -d "$TMP"
KEYSTORE="$TMP/MedianBrowser-update-key.p12"
README="$TMP/MedianBrowser-signing-key-README.txt"
[[ -f "$KEYSTORE" && -f "$README" ]] || { echo "签名备份缺少 key 或 README。" >&2; exit 2; }

STOREPASS="$(awk -F': ' '/^Store password:/ {print $2}' "$README" | head -n1)"
KEYPASS="$(awk -F': ' '/^Key password:/ {print $2}' "$README" | head -n1)"
ALIAS="$(awk -F': ' '/^Alias:/ {print $2}' "$README" | head -n1)"
[[ -n "$STOREPASS" && -n "$KEYPASS" && -n "$ALIAS" ]] || { echo "无法从签名 README 读取密码或 alias。" >&2; exit 2; }

export MEDIAN_KEYSTORE="$KEYSTORE"
export MEDIAN_STOREPASS="$STOREPASS"
export MEDIAN_KEY_ALIAS="$ALIAS"
export MEDIAN_KEYPASS="$KEYPASS"

if [[ -z "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]]; then
  echo "请先设置 ANDROID_SDK_ROOT 或 ANDROID_HOME。" >&2
  exit 2
fi

# Fast, self-contained signed APK build path. Override VERSION_CODE/NAME only if you know what you are doing.
export VERSION_CODE="${VERSION_CODE:-92}"
export VERSION_NAME="${VERSION_NAME:-2.3.0}"
./tools/build_500kb_apk.sh

echo
echo "已生成签名更新 APK：out/500kb/MedianBrowser-${VERSION_NAME}.apk"
echo "SHA-256：$(cat "out/500kb/MedianBrowser-${VERSION_NAME}.apk.sha256")"
