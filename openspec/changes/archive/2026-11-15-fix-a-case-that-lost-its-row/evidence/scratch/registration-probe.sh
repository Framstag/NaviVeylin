#!/usr/bin/env bash
# Red-on-base probe for issue #929: the same nested suite the new case builds — two bodies, two arms and one
# selection row — driven by the base commit's driver and by this tree's, so the refusal the delta adds is shown to be
# what flips the answer. Writes the nested tree under a temporary directory and touches nothing in the repository.
set -uo pipefail
root=${1:?usage: registration-probe.sh <change root>}
base=d794277ba7506e287ea94f22223e76926f20bf18
probe=$(mktemp -d)
d="$probe/nested"; tmp="$probe/tmp"
mkdir -p "$d/tools/loop/tests" "$tmp"
for f in "$root/tools/loop"/*; do
  [ "${f##*/}" = tests ] && continue
  ln -s "$f" "$d/tools/loop/${f##*/}"
done
ln -s "$root/tools/issues" "$d/tools/issues"
git -C "$d" init -q
sed "s|^LOOP_TMP_DIR=.*|LOOP_TMP_DIR=\"$tmp\"|" "$root/tools/loop/loop.conf" > "$probe/loop.conf"

cat > "$d/tools/loop/tests/loop-cases.sh" <<'CASES'
LOOP_SHELL_CASES=('stub#kept')
loop_shell_cases() { printf '%s\n' "${LOOP_SHELL_CASES[@]}"; }
loop_run_shell_case() {
  case "$1" in
    'stub#kept') fn=case_stub_kept ;;
    'stub#lost') fn=case_stub_lost ;;
  esac
  "$fn"
}
case_stub_kept() {
  # files: tools/loop/gate.sh
  :
}
case_stub_lost() {
  # files: tools/loop/run.sh
  :
}
CASES

run() { # run <label> <driver: git-ref:path | file>
  local label="$1" from="$2" out status
  case "$from" in
    *:*) git -C "$root" show "$from" > "$d/tools/loop/tests/loop-selftest.sh" ;;
    *) cp "$from" "$d/tools/loop/tests/loop-selftest.sh" ;;
  esac
  out=$(LOOP_CONF="$probe/loop.conf" bash "$d/tools/loop/tests/loop-selftest.sh" --case 'stub#kept' 2>&1); status=$?
  printf '=== %s driven by %s: exit %s\n%s\n\n' "$label" "$from" "$status" "$out"
}
run base "$base:tools/loop/tests/loop-selftest.sh"
run "this tree" "$root/tools/loop/tests/loop-selftest.sh"
rm -rf "$probe"
