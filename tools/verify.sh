#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./tools/static_checks.sh
./gradlew --no-daemon clean lintDebug testDebugUnitTest assembleDebug bundleRelease
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/bundle/release/app-release.aab
if [[ -n "${MEDIAN_KEYSTORE:-}" ]]; then
  ./build.sh --release
fi
echo 'Verification passed.'
