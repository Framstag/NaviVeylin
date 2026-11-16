#!/usr/bin/env bash
# Red-on-base for issue #929: this change's case and its cases file, run by the base commit's driver — the check the
# delta adds is absent, so the case's own nested suite (two bodies, two arms, one selection row) is read as a whole
# suite and the case fails. Nothing in the repository is written: the tree is built under a temporary directory.
set -uo pipefail
root=${1:?usage: red-on-base.sh <change root>}
base=d794277ba7506e287ea94f22223e76926f20bf18
probe=$(mktemp -d)
d="$probe/tree"; tmp="$probe/tmp"
mkdir -p "$d/tools/loop/tests" "$tmp"
for f in "$root/tools/loop"/*; do
  [ "${f##*/}" = tests ] && continue
  ln -s "$f" "$d/tools/loop/${f##*/}"
done
ln -s "$root/tools/issues" "$d/tools/issues"
git -C "$d" init -q
sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$root/tools/loop/loop.conf" > "$probe/loop.conf"
git -C "$root" show "$base:tools/loop/tests/loop-selftest.sh" > "$d/tools/loop/tests/loop-selftest.sh"
cp "$root/tools/loop/tests/loop-cases.sh" "$d/tools/loop/tests/loop-cases.sh"
chmod +x "$d/tools/loop/tests/loop-selftest.sh"

LOOP_CONF="$probe/loop.conf" bash "$d/tools/loop/tests/loop-selftest.sh" \
  --case 'driver#aCaseWithNoSelectionRowIsRefused' 2>&1
printf 'exit=%s\n' "$?"
rm -rf "$probe"
