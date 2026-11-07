#!/usr/bin/env bash
# The source-size row's measurement for the path the tracker entry names, with the condition the
# entry carries (`tools/sensors/sensors.json` owns the bound; `tools/sensors/lib.sh` owns the
# fingerprint), so the finding this prints is the one the row would file or close.
set -uo pipefail
cd "${1:-.}" || exit 2
PATH_TO_FILE="auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt"
FINGERPRINT="60ebe0d1c07f9c2521c7c9031a6d809b0b97190a93eb7acc7d944de316d90518"
CONDITIONS=$(jq -nc --arg p "$PATH_TO_FILE" --arg f "$FINGERPRINT" \
  '[{state:"OPEN",fingerprint:$f,condition:{path:$p}}]')
echo "# source-size probe — $PATH_TO_FILE"
echo "# bound=1500 comparison=greater-than; the open condition's fingerprint is the entry's own"
echo "\$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[<the entry's condition>]' bash tools/sensors/metrics/source-size.sh"
SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS="$CONDITIONS" bash tools/sensors/metrics/source-size.sh 2>/dev/null |
  grep 'AutoMapRendererTest\.kt'
echo "# taken $(date -u +%Y-%m-%dT%H:%M:%SZ) at $(git rev-parse --short HEAD), $(git status --porcelain | wc -l) changed path(s) in the tree"
