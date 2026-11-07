#!/usr/bin/env bash
# The member roster: every function the old single file declared must exist after the split, under its
# own name. The split turns a class member into `internal fun MapCanvasViewModel.<name>` in a sibling
# file, so the roster compares the names with the receiver, the modifiers and the visibility stripped,
# and the only name it lets the two sides differ by is the one rename the split forces (`design.md`).
#
# The `wire*` functions are the exception the count reports: they are the init block's collector
# groups, which had no name before (they were statements inside `init`).
#
# Usage: member-roster.sh [<tree>] [<base-ref>]
set -uo pipefail
cd "${1:-.}" || exit 2
BASE="${2:-HEAD}"
OLD=app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt
DIR=app/src/main/java/com/naviveylin/ui/map
NEW="$DIR/MapCanvasViewModel.kt $DIR/MapCanvasUiState.kt $DIR/MapCanvasViewModelMapInit.kt \
$DIR/MapCanvasViewModelSearch.kt $DIR/MapCanvasViewModelRoute.kt $DIR/MapCanvasViewModelFollow.kt \
$DIR/MapCanvasViewModelCamera.kt $DIR/MapCanvasViewModelSettings.kt"

before=$(mktemp); after=$(mktemp)
git show "$BASE:$OLD" |
  sed -n '/^class MapCanvasViewModel @Inject constructor(/,/^    companion object {/p' |
  grep -oE '^    ((private|internal|public|override) )*(suspend )?fun [A-Za-z0-9_]+' |
  sed -E 's/^ *((private|internal|public|override) )*//; s/^(suspend )?fun //' |
  grep -vx 'onCleared' | sort > "$before"
# shellcheck disable=SC2086
grep -hoE '^(private |internal |public )?(suspend )?fun MapCanvasViewModel\.[A-Za-z0-9_]+' $NEW |
  sed -E 's/^(private |internal |public )?//; s/^((suspend) )?fun MapCanvasViewModel\.//' |
  sort > "$after"
wires=$(grep -c '^wire' "$after")
grep -v '^wire' "$after" | sed 's/^poiEntryFitMagnification$/poiFitMagnification/' > "$after.1"
mv "$after.1" "$after"

echo "# member roster — $BASE's class against the files the split produced"
echo "#   members before:   $(wc -l < "$before")"
echo "#   extensions after: $(grep -c . "$after") (excluding the $wires wire* functions the init block became)"
echo "#   \$ git show $BASE:$OLD | grep -oE '^    ((private|internal|public|override) )*(suspend )?fun [A-Za-z0-9_]+'"
echo "#   \$ grep -hoE '^(private |internal |public )?(suspend )?fun MapCanvasViewModel\\.[A-Za-z0-9_]+' $DIR/MapCanvasViewModel*.kt"
echo "# diff <before> <after> (empty = every member survived under its own name):"
if diff "$before" "$after"; then
  echo "#   identical — no member added, renamed or dropped"
else
  echo "#   DIFFERENT — a member was added, renamed or dropped"
  rm -f "$before" "$after"
  exit 1
fi
echo "# taken $(date -u +%Y-%m-%dT%H:%M:%SZ) at $(git rev-parse --short HEAD)"
rm -f "$before" "$after"
