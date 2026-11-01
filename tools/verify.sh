#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash tools/static_checks.sh
bash gradlew --no-daemon clean lintDebug lintRelease testDebugUnitTest assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest "$@"
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/bundle/release/app-release.aab
echo 'Verification passed.'
