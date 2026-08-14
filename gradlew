#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

VERSION=8.13
EXPECTED_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
URL="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
GRADLE_HOME_BASE=${GRADLE_USER_HOME:-"$HOME/.gradle"}
BOOTSTRAP_DIR="$GRADLE_HOME_BASE/wrapper/median-bootstrap"
DIST_DIR="$BOOTSTRAP_DIR/gradle-${VERSION}"
ZIP="$BOOTSTRAP_DIR/gradle-${VERSION}-bin.zip"
GRADLE_BIN="$DIST_DIR/bin/gradle"

verify_sha256() {
  file=$1
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$file" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "$file" | awk '{print $1}')
  elif command -v python3 >/dev/null 2>&1; then
    actual=$(python3 - "$file" <<'PY'
import hashlib, pathlib, sys
print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)
  else
    echo "Cannot verify Gradle distribution: install sha256sum, shasum, or Python 3." >&2
    return 1
  fi
  [ "$actual" = "$EXPECTED_SHA256" ] || {
    echo "Gradle distribution checksum mismatch." >&2
    rm -f "$file"
    return 1
  }
}

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$BOOTSTRAP_DIR"
  if [ ! -f "$ZIP" ]; then
    TMP="$ZIP.part.$$"
    trap 'rm -f "$TMP"' EXIT HUP INT TERM
    if command -v curl >/dev/null 2>&1; then
      curl --fail --location --proto '=https' --tlsv1.2 --retry 3 --output "$TMP" "$URL"
    elif command -v wget >/dev/null 2>&1; then
      wget --https-only --tries=3 --output-document="$TMP" "$URL"
    else
      echo "Gradle is not installed and neither curl nor wget is available." >&2
      exit 1
    fi
    verify_sha256 "$TMP"
    mv "$TMP" "$ZIP"
    trap - EXIT HUP INT TERM
  else
    verify_sha256 "$ZIP"
  fi
  rm -rf "$DIST_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$BOOTSTRAP_DIR"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$ZIP" "$BOOTSTRAP_DIR" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    z.extractall(sys.argv[2])
PY
  else
    echo "Cannot unpack Gradle: install unzip or Python 3." >&2
    exit 1
  fi
fi

[ -x "$GRADLE_BIN" ] || { echo "Gradle bootstrap failed." >&2; exit 1; }
exec "$GRADLE_BIN" "$@"
