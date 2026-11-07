#!/usr/bin/env bash
# The line accounting: a move keeps every statement, so the old file's code lines and the new files'
# code lines are the same multiset once the split's own transformations are normalised away —
#   the declaration prefix (`private fun x` -> `internal fun MapCanvasViewModel.x`),
#   the visibility widening on a property or a constructor parameter
#     (`private val x` -> `internal val x`, `@param:ApplicationContext private val context` likewise),
#   a companion member now reached by its qualified name (`TAG` -> `MapCanvasViewModel.TAG`),
#   an explicit receiver label written as a plain `this` (`this@MapCanvasViewModel.x` -> `this.x`),
#   the names the split is forced to change (`poiEntryFitMagnification`, and the local in `initMap` that
#   shadowed the `rendererScope` property, renamed `scope` so the assignment needs no label), and
#   the `init` block's groups, which are the same statements inside a named `wire*` function.
#
# Comment and KDoc lines are outside the comparison: they are documentation, and the split adds one
# KDoc per `wire*` function and rewrites the init block's trailing note. Brace-only lines are outside
# it too — `init {` and its 15 `wire*` wrappers have different brace counts, and a lost brace cannot
# compile, which the build already proves.
#
# Usage: line-accounting.sh [<tree>] [<base-ref>]
set -uo pipefail
cd "${1:-.}" || exit 2
BASE="${2:-HEAD}"
OLD=app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt
DIR=app/src/main/java/com/naviveylin/ui/map
NEW="$DIR/MapCanvasViewModel.kt $DIR/MapCanvasUiState.kt $DIR/MapCanvasViewModelMapInit.kt \
$DIR/MapCanvasViewModelSearch.kt $DIR/MapCanvasViewModelRoute.kt $DIR/MapCanvasViewModelFollow.kt \
$DIR/MapCanvasViewModelCamera.kt $DIR/MapCanvasViewModelSettings.kt"

norm() {
  sed -E \
    -e 's/^[[:space:]]+//' -e 's/[[:space:]]+$//' \
    -e '/^$/d' -e '/^package /d' -e '/^import /d' \
    -e '/^[{}]$/d' \
    -e '/^(\/\/|\/\*|\*|\*\/)/d' \
    -e 's/^@param:[A-Za-z]+ //' \
    -e 's/^(private|internal|public) //' \
    -e 's/^(suspend )?fun MapCanvasViewModel\./\1fun /' \
    -e 's/this@[A-Za-z0-9_]+/this/g' \
    -e 's/MapCanvasViewModel\.//g' \
    -e 's/poiEntryFitMagnification/poiFitMagnification/g' \
    -e 's/^val scope = CoroutineScope\(/val rendererScope = CoroutineScope(/' \
    -e 's/^rendererScope = scope$/this.rendererScope = rendererScope/' \
    -e 's/^val renderer = MapRenderer\(client, density, scope\)$/val renderer = MapRenderer(client, density, rendererScope)/'
}

before=$(mktemp); after=$(mktemp)
git show "$BASE:$OLD" | norm | sort > "$before"
# shellcheck disable=SC2086
cat $NEW | norm | grep -vE '^(fun wire[A-Za-z]+\(\) \{|wire[A-Za-z]+\(\))$' | sort > "$after"
echo "# line accounting — $BASE's single file against the files the split produced"
echo "#   code lines before: $(wc -l < "$before")"
echo "#   code lines after:  $(wc -l < "$after") (excluding the 15 wire* declarations and their 15 init calls)"
echo "# \$ diff <(git show $BASE:$OLD | norm | sort) <(cat $DIR/MapCanvasViewModel*.kt $DIR/MapCanvasUiState.kt | norm | sort)"
if diff "$before" "$after"; then
  echo "#   identical — no statement was dropped, added or rewritten by the move"
else
  echo "#   DIFFERENT — the move changed a statement"
  rm -f "$before" "$after"
  exit 1
fi
echo "# taken $(date -u +%Y-%m-%dT%H:%M:%SZ) at $(git rev-parse --short HEAD)"
rm -f "$before" "$after"
