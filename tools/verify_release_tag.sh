#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TAG="${1:-${GITHUB_REF_NAME:-}}"
[[ -n "$TAG" ]] || { echo 'Usage: verify_release_tag.sh v2.1.6' >&2; exit 2; }
VERSION="$(sed -n "s/.*medianVersionName = '\([^']*\)'.*/\1/p" app/build.gradle | head -1)"
[[ -n "$VERSION" ]] || { echo 'Cannot read versionName from app/build.gradle' >&2; exit 1; }
[[ "$TAG" == "v$VERSION" ]] || { echo "Tag $TAG does not match version v$VERSION" >&2; exit 1; }
echo "Release tag matches version: $TAG"
