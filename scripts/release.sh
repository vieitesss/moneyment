#!/usr/bin/env bash
# Build the signed release APK and publish it as a GitHub Release.
#
# The version comes from versionName in app/build.gradle.kts. Bump versionName
# and versionCode there, commit, then run this script.
#
# Usage: scripts/release.sh [--build-only]
set -euo pipefail

if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--build-only" ) ]]; then
    echo "Usage: scripts/release.sh [--build-only]" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

keystore_properties="$HOME/.android/moneyment-keystore.properties"
if [[ ! -f "$keystore_properties" ]]; then
    echo "Missing $keystore_properties: the release APK cannot be signed." >&2
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    export JAVA_HOME
fi

version="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
tag="v$version"
apk="build/moneyment-$version.apk"

./gradlew --quiet testDebugUnitTest assembleRelease
mkdir -p "$(dirname "$apk")"
cp app/build/outputs/apk/release/app-release.apk "$apk"
echo "Built $apk"

if [[ "${1:-}" == "--build-only" ]]; then
    exit 0
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Uncommitted changes: commit them before publishing $tag." >&2
    exit 1
fi

git push origin HEAD
gh release create "$tag" "$apk" --title "$tag" --generate-notes
