#!/usr/bin/env bash
# The release APK guard. Fails when the release build pulls in Google Play Services, Firebase or a telemetry
# library, contains a hard-coded host outside the allow-list, or asks for a permission that is not allowed.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?ANDROID_HOME is not set}"
apk=app/build/outputs/apk/release/app-release-unsigned.apk
aapt2=$(find "$ANDROID_HOME/build-tools" -name aapt2 -type f | sort -V | tail -1)
forbidden='gms|firebase|play-services|datatransport|crashlytics|analytics|measurement|installreferrer|telemetry'
# meetagain.org is the app's server; the others appear only in library error messages.
allowed_hosts='^https?://(meetagain\.org|goo\.gle|youtrack\.jetbrains\.com|issuetracker\.google\.com|developer\.android\.com)$'
noise='schemas\.android\.com|w3\.org|xmlpull|apache\.org|ns\.adobe|json-schema'
# Beyond INTERNET: POST_NOTIFICATIONS is asked for in context when a push category is turned on; WAKE_LOCK comes
# from the UnifiedPush connector, which holds one while handing a push to the app; ACCESS_NETWORK_STATE and
# RECEIVE_BOOT_COMPLETED come from WorkManager, which needs them for the "only on a connection" rule and to put the
# timer back after a restart. FOREGROUND_SERVICE is removed in the manifest, so it must not appear here.
allowed_permissions='^(android\.permission\.(INTERNET|POST_NOTIFICATIONS|WAKE_LOCK|ACCESS_NETWORK_STATE|RECEIVE_BOOT_COMPLETED)|org\.meetagain\.app\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'
failed=0

[ -f "$apk" ] || { echo "No release APK; run ./gradlew :app:assembleRelease first."; exit 1; }

artifacts=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath \
    | grep -oE '[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:' | sed 's/:$//' | sort -u)
if bad=$(grep -iE "$forbidden" <<<"$artifacts"); then
    echo "Forbidden artifacts:"; echo "$bad"; failed=1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$tmp"
dex_strings=$(strings "$tmp"/classes*.dex)
if bad=$(grep -oE 'Lcom/google/(android/gms|firebase|android/datatransport)[^;]*' <<<"$dex_strings" | sort -u); then
    echo "Forbidden classes:"; echo "$bad"; failed=1
fi
if bad=$(grep -oE 'https?://[A-Za-z0-9.-]+' <<<"$dex_strings" | sort -u | grep -vE "$noise" | grep -vE "$allowed_hosts"); then
    echo "Hosts outside the allow-list:"; echo "$bad"; failed=1
fi

permissions=$("$aapt2" dump permissions "$apk" | grep -oE "name='[^']+'" | sed "s/name='//; s/'$//" | sort -u)
if bad=$(grep -vE "$allowed_permissions" <<<"$permissions"); then
    echo "Permissions not allowed:"; echo "$bad"; failed=1
fi

[ "$failed" -eq 0 ] && echo "Release APK: $(grep -c . <<<"$artifacts") artifacts, no forbidden library, host or permission."
exit "$failed"
