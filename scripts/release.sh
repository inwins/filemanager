#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version>  (example: $0 1.0.1)" >&2
    exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -n "${GRADLE:-}" ]]; then
    :
elif [[ -x /opt/gradle-8.7/bin/gradle ]]; then
    GRADLE=/opt/gradle-8.7/bin/gradle
elif [[ -x "$ROOT_DIR/gradlew" ]]; then
    GRADLE="$ROOT_DIR/gradlew"
else
    GRADLE=gradle
fi

echo "==> Building v$VERSION"
"$GRADLE" assembleDebug --console=plain

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "==> Committing changes"
        git add -A
        git commit -m "Release v$VERSION"
    else
        echo "==> No changes to commit"
    fi

    if git rev-parse "v$VERSION" >/dev/null 2>&1; then
        echo "==> Tag v$VERSION already exists"
    else
        echo "==> Tagging v$VERSION"
        git tag -a "v$VERSION" -m "Release v$VERSION"
    fi
else
    echo "==> Not a git repository, skipping commit and tag"
fi

echo "==> Released v$VERSION"
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
