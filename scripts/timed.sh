#!/usr/bin/env bash
# Runs a command and appends its duration to build/check-times.log: timed.sh <step name> <command...>
set -uo pipefail
cd "$(dirname "$0")/.."
name=$1
shift
start=$(date +%s%N)
"$@"
status=$?
millis=$((($(date +%s%N) - start) / 1000000))
mkdir -p build
printf '%-28s%8s%7d.%01ds\n' "$name" "" $((millis / 1000)) $((millis % 1000 / 100)) >> build/check-times.log
exit $status
