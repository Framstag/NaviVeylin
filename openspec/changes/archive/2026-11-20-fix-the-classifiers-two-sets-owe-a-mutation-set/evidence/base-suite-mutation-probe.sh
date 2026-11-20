#!/usr/bin/env bash
# The premise probe for item #926: does the base suite (HEAD's selftest, no `--case` filter) notice a swapped set
# or a count read from the other set? The harness does the apply/restore; the case command is the whole suite,
# because the suite is what the item claims prints PASS.
set -u
src=/home/tim/projects/NaviVeylin/.pi/worktrees/loop
item=/home/tim/projects/NaviVeylin/.pi/issue-loop/items/926
tree="$item/scratch/base-tree"

rm -rf "$tree"
mkdir -p "$tree/tools/tests"
cp "$src/tools/gen-feature-list.sh" "$tree/tools/"
cp "$src/tools/mutate.sh" "$tree/tools/"
cp -r "$src/tools/feature-list" "$tree/tools/"
git -C "$tree" init -q
git -C "$src" show HEAD:tools/tests/gen-feature-list-selftest.sh > "$tree/tools/tests/gen-feature-list-selftest.sh"

cd "$tree" || exit 1

bash tools/tests/gen-feature-list-selftest.sh > control.log 2>&1
printf 'control: exit=%s  %s\n' "$?" "$(tail -1 control.log)"

cp "$item/scratch/base-mutations.json" "$item/scratch/base-mutations.probe.json"
bash tools/mutate.sh --mutations "$item/scratch/base-mutations.probe.json" --root "$tree" --timeout 300 \
  > "$item/scratch/base-mutation-probe.log" 2>&1
printf 'probe: exit=%s\n' "$?"
tail -3 "$item/scratch/base-mutation-probe.log"
