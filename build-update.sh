#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

SIGNING_ZIP="${1:-}"
if [[ -z "$SIGNING_ZIP" ]]; then
  if [[ -f "$ROOT/MedianBrowser-signing-backup.zip" ]]; then
    SIGNING_ZIP="$ROOT/MedianBrowser-signing-backup.zip"
  elif [[ -f "$ROOT/_private_signing/MedianBrowser-signing-backup.zip" ]]; then
    SIGNING_ZIP="$ROOT/_private_signing/MedianBrowser-signing-backup.zip"
  elif [[ -f "$ROOT/../MedianBrowser-signing-backup.zip" ]]; then
    SIGNING_ZIP="$ROOT/../MedianBrowser-signing-backup.zip"
  else
    echo "找不到 MedianBrowser-signing-backup.zip。" >&2
    echo "用法：./build-update.sh /path/to/MedianBrowser-signing-backup.zip" >&2
    exit 2
  fi
fi

./tools/build_signed_update.sh "$SIGNING_ZIP"
