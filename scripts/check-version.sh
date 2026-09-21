#!/usr/bin/env bash
# Fails when the app version in app/build.gradle.kts, the version badge in README.md and, when given, a release
# tag's version disagree. check-version.sh [version], e.g. check-version.sh 0.2.0 for the tag v0.2.0.
set -euo pipefail
cd "$(dirname "$0")/.."

app=$(sed -nE 's/^val appVersion = "([^"]+)"$/\1/p' app/build.gradle.kts)
badge=$(sed -nE 's#.*img\.shields\.io/badge/Version-([0-9]+\.[0-9]+\.[0-9]+)-.*#\1#p' README.md)
tag=${1:-$app}

# versionCode is major * 10000 + minor * 100 + patch, so minor and patch must stay below 100.
if ! [[ $app =~ ^[0-9]+\.[0-9]{1,2}\.[0-9]{1,2}$ ]]; then
    echo "appVersion '$app' is not major.minor.patch with minor and patch below 100."; exit 1
fi
if [ "$badge" != "$app" ] || [ "$tag" != "$app" ]; then
    echo "Versions disagree: appVersion $app, README badge ${badge:-missing}, tag $tag."; exit 1
fi
echo "Version $app everywhere."
