#!/usr/bin/env bash
# The source-size row's measurement for the path the tracker entry names, with the condition the
# entry carries (`tools/sensors/sensors.json` owns the bound; `tools/sensors/lib.sh` owns the
# fingerprint), so the finding this prints is the one the row would file or close.
#
# Usage: source-size-probe.sh [<tree>]
set -uo pipefail
cd "${1:-.}" || exit 2
PATH_TO_FILE="app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt"
FINGERPRINT="44a78d48ed1eb15b6501fe7ffdb328e516e279e52405d64844b16d65d3cb89ac"
CONDITIONS=$(jq -nc --arg p "$PATH_TO_FILE" --arg f "$FINGERPRINT" \
  '[{state:"OPEN",fingerprint:$f,condition:{path:$p}}]')
echo "# source-size probe — $PATH_TO_FILE"
echo "# bound=1500 comparison=greater-than; the open condition's fingerprint is the entry's own"
echo "\$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[<the entry's condition>]' bash tools/sensors/metrics/source-size.sh"
SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS="$CONDITIONS" bash tools/sensors/metrics/source-size.sh 2>/dev/null |
  grep 'MapCanvasViewModel\.kt"'
echo "# taken $(date -u +%Y-%m-%dT%H:%M:%SZ) at $(git rev-parse --short HEAD), $(git status --porcelain | wc -l) changed path(s) in the tree"
