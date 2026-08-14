#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
GRADLE="$ROOT/gradlew"

if [[ "${1:-}" == "--release" ]]; then
  for name in MEDIAN_KEYSTORE MEDIAN_STOREPASS MEDIAN_KEY_ALIAS MEDIAN_KEYPASS; do
    [[ -n "${!name:-}" ]] || { echo "Missing release signing variable: $name" >&2; exit 1; }
  done
  [[ -f "$MEDIAN_KEYSTORE" ]] || { echo "Release keystore not found: $MEDIAN_KEYSTORE" >&2; exit 1; }
  "$GRADLE" clean lintRelease testReleaseUnitTest assembleRelease bundleRelease
  echo "Release outputs: app/build/outputs/apk/release and app/build/outputs/bundle/release"
else
  "$GRADLE" clean lintDebug testDebugUnitTest assembleDebug
  echo "Debug output: app/build/outputs/apk/debug/app-debug.apk"
  echo "For signed APK+AAB: ./build.sh --release"
fi
