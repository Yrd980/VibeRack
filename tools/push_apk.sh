#!/usr/bin/env bash
 # @author: BC
 # @date: 26/06/01
 # @lastEditTime: 26/06/01
 # @description: 
 # @note: 
 # @version: 0.1.0
### 

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

case "${1:-}" in
    debug)
        apk_path="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
        gradle_task=":app:assembleDebug"
        ;;
    release)
        apk_path="${REPO_ROOT}/app/build/outputs/apk/release/app-release.apk"
        gradle_task=":app:assembleRelease"
        ;;
    *)
        echo "Usage: $0 {debug|release}" >&2
        exit 1
        ;;
esac

if [ "${BUILD_APK:-1}" != "0" ]; then
    cd "${REPO_ROOT}"
    ./gradlew "${gradle_task}"
fi

if [ ! -f "$apk_path" ]; then
    echo "APK not found: $apk_path" >&2
    exit 1
fi

adb push "$apk_path" /sdcard/Download/
