#!/usr/bin/env bash
# The case roster: every public `fun` in the old single file is a JUnit case; the union of the new
# files must name exactly the same set (a helper is `private`/`protected fun` and so is out of it).
set -uo pipefail
cd "${1:-.}" || exit 2
OLD="auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt"
DIR="auto/src/test/java/com/naviveylin/auto"
head_cases=$(mktemp); new_cases=$(mktemp)
git show "HEAD:$OLD" | grep -o '^    fun [A-Za-z0-9_]*' | sed 's/^    fun //' | grep -vx 'setUp' | sort > "$head_cases"
grep -ho '^    fun [A-Za-z0-9_]*' "$DIR"/AutoMapRendererTest.kt "$DIR"/AutoMapRendererLifecycleTest.kt \
     "$DIR"/AutoMapRendererBlitTest.kt "$DIR"/AutoMapRendererFollowAnchorTest.kt \
     "$DIR"/AutoMapRendererZoomWalkTest.kt | sed 's/^    fun //' | sort > "$new_cases"
echo "# case roster — HEAD's single AutoMapRendererTest against the five classes that replaced it"
echo "#   old cases: $(wc -l < "$head_cases")   new cases: $(wc -l < "$new_cases")"   # the shared @Before setUp is not a case
echo "#   \$ git show HEAD:$OLD | grep -o '^    fun [A-Za-z0-9_]*' | sort"
echo "#   \$ grep -ho '^    fun [A-Za-z0-9_]*' $DIR/AutoMapRenderer{,Test,LifecycleTest,BlitTest,FollowAnchorTest,ZoomWalkTest}.kt | sort   (brace form: five files + support)"
echo "# diff <old> <new> (empty = every case survived the split under its own name):"
if diff "$head_cases" "$new_cases"; then
  echo "#   identical — no case added, renamed or dropped"
else
  echo "#   DIFFERENT — a case was added, renamed or dropped"; exit 1
fi
echo "# taken $(date -u +%Y-%m-%dT%H:%M:%SZ) at $(git rev-parse --short HEAD)"
rm -f "$head_cases" "$new_cases"
