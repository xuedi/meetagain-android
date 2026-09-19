#!/usr/bin/env bash
# Fails when a user-visible text in the app's Kotlin sources is a string literal instead of a string resource.
set -euo pipefail
cd "$(dirname "$0")/.."

pattern='(\bText\(\s*"|\b(text|contentDescription|label|title|placeholder|supportingText|headlineContent|stateDescription|onClickLabel)\s*=\s*")'
if hits=$(grep -rnE "$pattern" app/src/main --include='*.kt'); then
    echo "User-visible string literals; move them to res/values/strings.xml and translate them:"
    echo "$hits"
    exit 1
fi
echo "No string literals in the UI."
