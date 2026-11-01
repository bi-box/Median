#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
case "$(uname -s 2>/dev/null || printf '')" in
  MINGW*|MSYS*|CYGWIN*) CP_SEP=';' ;;
  *) CP_SEP=':' ;;
esac
# Windows developer images commonly expose only `python.exe` while Unix
# images expose `python3`. Resolve the interpreter once so every generated
# artifact uses the same runtime and the build remains reproducible.
PYTHON_BIN="${PYTHON_BIN:-}"
if [ -z "$PYTHON_BIN" ]; then
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  done
fi
[ -n "$PYTHON_BIN" ] || { echo "Missing Python (set PYTHON_BIN)" >&2; exit 2; }
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
BUILD_TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
APKSIGNER="$BUILD_TOOLS/apksigner"
if [ ! -x "$APKSIGNER" ] && [ -f "$APKSIGNER.bat" ]; then
  APKSIGNER="$APKSIGNER.bat"
fi
APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"
ANDROID_JAR="$SDK_ROOT/platforms/android-36/android.jar"
R8_JAR="${R8_JAR:-$SDK_ROOT/cmdline-tools/latest/lib/r8.jar}"
OUT="${OUT_DIR:-$ROOT/out/500kb}"
APPLICATION_ID="${APPLICATION_ID:-com.xinyv.median.compat}"
VERSION_CODE="${VERSION_CODE:-103}"
VERSION_NAME="${VERSION_NAME:-2.4.0}"
MAX_APK_BYTES="${MAX_APK_BYTES:-350000}"
export LD_LIBRARY_PATH="$BUILD_TOOLS/lib64${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

# A semicolon-separated classpath is required by the Windows JVM.  MSYS
# converts standalone path arguments, but it cannot convert a `/m/...` path
# embedded after a semicolon.  Convert the build directories explicitly for
# classpath values while keeping POSIX paths for shell-native tools.
if [ "$CP_SEP" = ';' ] && command -v cygpath >/dev/null 2>&1; then
  JAVA_ANDROID_JAR="$(cygpath -w "$ANDROID_JAR")"
  JAVA_CLASSES="$(cygpath -w "$OUT/classes")"
  JAVA_TEST_CLASSES="$(cygpath -w "$OUT/test-classes")"
  JAVA_SIGNER_CLASSES="$(cygpath -w "$OUT/sign-helper/classes")"
else
  JAVA_ANDROID_JAR="$ANDROID_JAR"
  JAVA_CLASSES="$OUT/classes"
  JAVA_TEST_CLASSES="$OUT/test-classes"
  JAVA_SIGNER_CLASSES="$OUT/sign-helper/classes"
fi
TEST_CP="$JAVA_ANDROID_JAR${CP_SEP}$JAVA_CLASSES${CP_SEP}$JAVA_TEST_CLASSES"
TEST_COMPILE_CP="$JAVA_ANDROID_JAR${CP_SEP}$JAVA_CLASSES"

: "${SDK_ROOT:?Set ANDROID_SDK_ROOT or ANDROID_HOME}"
for tool in aapt2 zipalign; do
  [ -x "$BUILD_TOOLS/$tool" ] || { echo "Missing $BUILD_TOOLS/$tool" >&2; exit 2; }
done
if [ ! -x "$APKSIGNER" ] && [ ! -f "$APKSIGNER" ]; then
  echo "Missing $BUILD_TOOLS/apksigner(.bat)" >&2
  exit 2
fi
[ -f "$APKSIGNER_JAR" ] || { echo "Missing $APKSIGNER_JAR" >&2; exit 2; }
[ -f "$R8_JAR" ] || R8_JAR="$BUILD_TOOLS/lib/d8.jar"
[ -f "$R8_JAR" ] || { echo "Missing R8/D8 jar" >&2; exit 2; }
[ -f "$ANDROID_JAR" ] || { echo "Missing $ANDROID_JAR" >&2; exit 2; }
: "${MEDIAN_KEYSTORE:?Set MEDIAN_KEYSTORE}"
: "${MEDIAN_STOREPASS:?Set MEDIAN_STOREPASS}"
: "${MEDIAN_KEY_ALIAS:?Set MEDIAN_KEY_ALIAS}"
MEDIAN_KEYPASS="${MEDIAN_KEYPASS:-$MEDIAN_STOREPASS}"

if [ -d "$OUT" ]; then
  find "$OUT" -depth -mindepth 1 -delete
fi
mkdir -p "$OUT"/{compiled,resgen,generated,classes,r8}

"$PYTHON_BIN" - "$ROOT/app/src/main/AndroidManifest.xml" "$OUT/AndroidManifest.xml" "$APPLICATION_ID" <<'PY'
import re, sys
src, dst, app_id = sys.argv[1:]
text = open(src, encoding='utf-8').read()
text = text.replace('${applicationId}', app_id)
text = re.sub(r'<manifest\b', '<manifest package="%s"' % app_id, text, count=1)
text = re.sub(r'android:name="\.(\w+)', r'android:name="com.xinyv.median.\1', text)
open(dst, 'w', encoding='utf-8').write(text)
PY

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/compiled/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/resgen" \
  --custom-package com.xinyv.median \
  --proguard "$OUT/aapt-proguard.pro" \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay \
  --enable-sparse-encoding \
  -o "$OUT/base-unsigned.apk" \
  "$OUT/compiled/resources.zip"

mkdir -p "$OUT/generated/com/xinyv/median"
cat > "$OUT/generated/com/xinyv/median/BuildConfig.java" <<JAVA
package com.xinyv.median;
public final class BuildConfig {
  public static final boolean DEBUG = false;
  public static final String APPLICATION_ID = "$APPLICATION_ID";
  public static final int VERSION_CODE = $VERSION_CODE;
  public static final String VERSION_NAME = "$VERSION_NAME";
  private BuildConfig() {}
}
JAVA

find "$ROOT/app/src/main/java" "$OUT/resgen" "$OUT/generated" -name '*.java' -type f | sort > "$OUT/sources.list"
mapfile -t SOURCES < "$OUT/sources.list"
# Release dex does not need Java local-variable tables; omitting them keeps the
# compact package small without changing runtime code or public diagnostics.
javac -g:none -encoding UTF-8 -source 17 -target 17 \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  "${SOURCES[@]}"

mkdir -p "$OUT/test-classes"
javac -encoding UTF-8 --release 17 -classpath "$TEST_COMPILE_CP" -d "$OUT/test-classes" \
  "$ROOT/tools/tests/DownloadCenterPolicySelfTest.java" \
  "$ROOT/tools/tests/DownloadRetryPolicySelfTest.java" \
  "$ROOT/tools/tests/DownloadFileTypesSelfTest.java" \
  "$ROOT/tools/tests/OmniboxInputSelfTest.java" \
  "$ROOT/tools/tests/HomeOpenPolicySelfTest.java" \
  "$ROOT/tools/tests/CustomHomeCssSelfTest.java" \
  "$ROOT/tools/tests/CustomHomeHtmlSelfTest.java" \
  "$ROOT/tools/tests/HomePageConfigSelfTest.java" \
  "$ROOT/tools/tests/LogoMarkupSelfTest.java" \
  "$ROOT/tools/tests/HomePageHtmlSelfTest.java" \
  "$ROOT/tools/tests/InternalNavigationPolicySelfTest.java" \
  "$ROOT/tools/tests/StartupNavigationPolicySelfTest.java" \
  "$ROOT/tools/tests/EdgeNavigationPolicySelfTest.java" \
  "$ROOT/tools/tests/NavigationGestureGuardSelfTest.java" \
  "$ROOT/tools/tests/MainFrameNavigationContextSelfTest.java" \
  "$ROOT/tools/tests/StartupReadinessSelfTest.java" \
  "$ROOT/tools/tests/InitialNavigationGuardSelfTest.java" \
  "$ROOT/tools/tests/DeviceProfileSelfTest.java" \
  "$ROOT/tools/tests/ScriptTabStateStoreSelfTest.java" \
  "$ROOT/tools/tests/AdBlockMemorySelfTest.java" \
  "$ROOT/tools/tests/AdBlockSemanticsSelfTest.java" \
  "$ROOT/tools/tests/AdBlockScaleSelfTest.java" \
  "$ROOT/tools/tests/MediaResourceSnifferSelfTest.java" \
  "$ROOT/tools/tests/MediaProbeScriptSelfTest.java" \
  "$ROOT/tools/tests/MediaManifestParserSelfTest.java" \
  "$ROOT/tools/tests/CredentialAutofillSelfTest.java" \
  "$ROOT/tools/tests/HexCodecSelfTest.java" \
  "$ROOT/tools/tests/SiteSettingsStoreSelfTest.java" \
  "$ROOT/tools/tests/BookmarkFolderStoreSelfTest.java" \
  "$ROOT/tools/tests/UserScriptInstallPolicySelfTest.java"
java -cp "$TEST_CP" com.xinyv.median.DownloadCenterPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.DownloadRetryPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.OmniboxInputSelfTest
java -cp "$TEST_CP" com.xinyv.median.HomeOpenPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.CustomHomeCssSelfTest
java -cp "$TEST_CP" com.xinyv.median.CustomHomeHtmlSelfTest
java -cp "$TEST_CP" com.xinyv.median.HomePageConfigSelfTest
java -cp "$TEST_CP" com.xinyv.median.LogoMarkupSelfTest
java -cp "$TEST_CP" com.xinyv.median.HomePageHtmlSelfTest "$OUT/home-page.js"
node --check "$OUT/home-page.js"
node "$ROOT/tools/tests/homepage_behavior_test.js" "$OUT/home-page.js"
java -cp "$TEST_CP" com.xinyv.median.InternalNavigationPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.StartupNavigationPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.EdgeNavigationPolicySelfTest
java -cp "$TEST_CP" com.xinyv.median.NavigationGestureGuardSelfTest
java -cp "$TEST_CP" com.xinyv.median.MainFrameNavigationContextSelfTest
java -cp "$TEST_CP" com.xinyv.median.StartupReadinessSelfTest
java -cp "$TEST_CP" com.xinyv.median.InitialNavigationGuardSelfTest
java -cp "$TEST_CP" com.xinyv.median.DeviceProfileSelfTest
java -cp "$TEST_CP" com.xinyv.median.ScriptTabStateStoreSelfTest
java -cp "$TEST_CP" com.xinyv.median.AdBlockMemorySelfTest
java -cp "$TEST_CP" com.xinyv.median.AdBlockSemanticsSelfTest
java -Xmx768m -cp "$TEST_CP" com.xinyv.median.AdBlockScaleSelfTest
java -cp "$TEST_CP" com.xinyv.median.MediaResourceSnifferSelfTest
java -cp "$TEST_CP" com.xinyv.median.MediaProbeScriptSelfTest
java -cp "$TEST_CP" com.xinyv.median.MediaProbeScriptSelfTest install > "$OUT/media-install.js"
java -cp "$TEST_CP" com.xinyv.median.MediaProbeScriptSelfTest build > "$OUT/media-probe.js"
node --check "$OUT/media-install.js"
node --check "$OUT/media-probe.js"
node "$ROOT/tools/tests/media_probe_behavior_test.js" "$OUT/media-install.js" "$OUT/media-probe.js"
java -cp "$TEST_CP" com.xinyv.median.MediaManifestParserSelfTest
java -cp "$TEST_CP" com.xinyv.median.CredentialAutofillSelfTest
java -cp "$TEST_CP" com.xinyv.median.CredentialAutofillSelfTest detect > "$OUT/credential-detect.js"
java -cp "$TEST_CP" com.xinyv.median.CredentialAutofillSelfTest fill > "$OUT/credential-fill.js"
java -cp "$TEST_CP" com.xinyv.median.CredentialAutofillSelfTest capture > "$OUT/credential-capture.js"
java -cp "$TEST_CP" com.xinyv.median.CredentialAutofillSelfTest fields > "$OUT/credential-fields.js"
node --check "$OUT/credential-detect.js"
node --check "$OUT/credential-fill.js"
node --check "$OUT/credential-capture.js"
node --check "$OUT/credential-fields.js"
node "$ROOT/tools/tests/credential_autofill_behavior_test.js" \
  "$OUT/credential-detect.js" "$OUT/credential-fill.js" "$OUT/credential-capture.js" "" "$OUT/credential-fields.js"
java -cp "$TEST_CP" com.xinyv.median.HexCodecSelfTest
java -cp "$TEST_CP" com.xinyv.median.SiteSettingsStoreSelfTest
java -cp "$TEST_CP" com.xinyv.median.BookmarkFolderStoreSelfTest
java -cp "$TEST_CP" com.xinyv.median.UserScriptInstallPolicySelfTest
"$ROOT/tools/tests/userscript_js_syntax_test.sh"
jar --create --file "$OUT/program.jar" -C "$OUT/classes" .

cat > "$OUT/r8.pro" <<PRO
-dontwarn org.chromium.**
-dontwarn java.lang.invoke.**
-allowaccessmodification
-overloadaggressively
-dontusemixedcaseclassnames
-repackageclasses ''

-keep,allowoptimization class com.xinyv.median.MainActivity { public <init>(); }
-keep,allowoptimization class com.xinyv.median.PrivateActivity { public <init>(); }
-keep,allowoptimization class com.xinyv.median.DownloadCenterActivity { public <init>(); }
-keep,allowoptimization class com.xinyv.median.AdaptiveDownloadService { public <init>(); }
-keep,allowoptimization class com.xinyv.median.OfflineContentProvider { public <init>(); }
-keep,allowoptimization class com.xinyv.median.DownloadContentProvider { public <init>(); }

-keep,allowoptimization interface org.chromium.support_lib_boundary.** { *; }
-keepnames interface org.chromium.support_lib_boundary.**
PRO

# The six manifest entry points above mirror aapt2's generated rules while
# allowing R8 to optimize their implementation. Their class names stay fixed.

java -Xmx3g -cp "$R8_JAR" com.android.tools.r8.R8 \
  --release \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$OUT/r8" \
  --pg-conf "$OUT/r8.pro" \
  --pg-map-output "$OUT/mapping.txt" \
  "$OUT/program.jar"

cp "$OUT/base-unsigned.apk" "$OUT/stage.apk"
touch -t 198001010000 "$OUT/r8/classes.dex"
jar --update --file "$OUT/stage.apk" -C "$OUT/r8" classes.dex
# Keep the project's original raw-DEFLATE packaging. Release size gains must come from code/R8,
# never from a different compressor or resource repacking strategy.
"$PYTHON_BIN" "$ROOT/tools/deflate_raw.py" "$OUT/r8/classes.dex" "$OUT/classes.dex.deflate"
"$PYTHON_BIN" "$ROOT/tools/repack_precompressed.py" "$OUT/stage.apk" classes.dex \
  "$OUT/classes.dex.deflate" "$OUT/stage-compact.apk"
mv "$OUT/stage-compact.apk" "$OUT/stage.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/stage.apk" "$OUT/aligned.apk"
FINAL="$OUT/MedianBrowser-$VERSION_NAME.apk"
# apksigner normally inserts a 4 KiB alignment pair before the v2 signing
# block. Avoid that build-only padding with a local patched signer; the
# patched jar is never packaged into the application.
ASM_ROOT="${ASM_ROOT:-$SDK_ROOT/cmdline-tools/latest/lib/external/org/ow2/asm}"
ASM_CLASSPATH="$ASM_ROOT/asm/9.7/asm-9.7.jar${CP_SEP}$ASM_ROOT/asm-tree/9.7/asm-tree-9.7.jar"
if [ ! -f "$ASM_ROOT/asm/9.7/asm-9.7.jar" ] || [ ! -f "$ASM_ROOT/asm-tree/9.7/asm-tree-9.7.jar" ]; then
  echo "Missing ASM jars under $ASM_ROOT; compact signing is required for the release build." >&2
  exit 2
fi
SIGNER_WORK="$OUT/sign-helper"
mkdir -p "$SIGNER_WORK/classes"
if [ "$CP_SEP" = ';' ] && command -v cygpath >/dev/null 2>&1; then
  JAVA_ASM_CLASSPATH="$(cygpath -w "$ASM_ROOT/asm/9.7/asm-9.7.jar")${CP_SEP}$(cygpath -w "$ASM_ROOT/asm-tree/9.7/asm-tree-9.7.jar")"
else
  JAVA_ASM_CLASSPATH="$ASM_CLASSPATH"
fi
JAVA_ASM_SIGNER_CP="$JAVA_ASM_CLASSPATH${CP_SEP}$JAVA_SIGNER_CLASSES"
javac -encoding UTF-8 --release 17 -classpath "$JAVA_ASM_CLASSPATH" \
  -d "$SIGNER_WORK/classes" "$ROOT/tools/patch_apksigner_padding.java"
PATCHED_APKSIGNER="$SIGNER_WORK/apksigner-patched.jar"
java -cp "$JAVA_ASM_SIGNER_CP" patch_apksigner_padding \
  "$APKSIGNER_JAR" "$PATCHED_APKSIGNER"
java -cp "$PATCHED_APKSIGNER" com.android.apksigner.ApkSignerTool sign \
  --ks "$MEDIAN_KEYSTORE" \
  --ks-pass "pass:$MEDIAN_STOREPASS" \
  --ks-key-alias "$MEDIAN_KEY_ALIAS" \
  --key-pass "pass:$MEDIAN_KEYPASS" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --v4-signing-enabled false \
  --verity-enabled false \
  --out "$FINAL" \
  "$OUT/aligned.apk"
"$PYTHON_BIN" "$ROOT/tools/strip_apk_signing_padding.py" "$FINAL" "$OUT/final-compact.apk"
mv "$OUT/final-compact.apk" "$FINAL"
"$APKSIGNER" verify --verbose --print-certs "$FINAL" > "$OUT/signature.txt"
"$BUILD_TOOLS/aapt" dump badging "$FINAL" > "$OUT/badging.txt"
unzip -t "$FINAL" > "$OUT/unzip-test.txt"
java -cp "$TEST_CP" com.xinyv.median.DownloadFileTypesSelfTest "$FINAL"
sha256sum "$FINAL" > "$FINAL.sha256"

SIZE=$(stat -c %s "$FINAL")
echo "APK: $FINAL"
echo "Size: $SIZE bytes"
if [ "$SIZE" -ge "$MAX_APK_BYTES" ]; then
  echo "$MAX_APK_BYTES-byte release budget: FAIL ($SIZE bytes)" >&2
  exit 3
else
  echo "$MAX_APK_BYTES-byte release budget: PASS ($((MAX_APK_BYTES - SIZE)) bytes remaining)"
fi
