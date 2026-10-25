#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "${GRADLE:-}" ]]; then
    :
elif [[ -x /opt/gradle-8.7/bin/gradle ]]; then
    GRADLE=/opt/gradle-8.7/bin/gradle
elif [[ -x ./gradlew ]]; then
    GRADLE=./gradlew
else
    GRADLE=gradle
fi

"$GRADLE" testDebugUnitTest --console=plain "$@"
