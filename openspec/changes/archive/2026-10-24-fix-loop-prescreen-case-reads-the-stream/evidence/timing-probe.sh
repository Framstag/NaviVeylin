#!/usr/bin/env bash
# timing: how long after start does `loop: pre-screen` reach the console? Measured on a tree given as $1.
set -uo pipefail
R="$1"
cd "$R" || exit 1
mkdir -p "$R/.pi/tmp"
d=$(mktemp -d "$R/.pi/tmp/dbgt.XXXXXX") || exit 1
p="$d/repo"
mkdir -p "$p/tools" "$p/app/src/main/cpp/libosmscout" \
         "$p/.pi/skills/fix-loop" "$p/.pi/skills/openspec-apply-change" "$p/openspec/changes/archive"
cp tools/tooling.json "$p/tools/tooling.json"
[ -f tools/pi-child-events.sh ] && cp tools/pi-child-events.sh "$p/tools/pi-child-events.sh"
printf '.pi/\napp/build/\nbuild/\ncore/build\nlocal.prop*\n' > "$p/.gitignore"
printf 'cmake_minimum_required(VERSION 3.22)\n' > "$p/app/src/main/cpp/libosmscout/CMakeLists.txt"
printf '# machine-local SDK/NDK/Java paths\n' > "$p/local.properties"
printf -- '---\nname: fix-loop\n---\nfixture skill\n' > "$p/.pi/skills/fix-loop/SKILL.md"
printf 'fixture skill\n' > "$p/.pi/skills/openspec-apply-change/SKILL.md"
git -C "$p" init -q -b main >/dev/null 2>&1
git -C "$p" config user.email selftest@example.invalid
git -C "$p" config user.name selftest
git -C "$p" add -A >/dev/null 2>&1
git -C "$p" commit -qm init >/dev/null 2>&1
tools="$d/tools"
cp -r "$R/tools" "$tools"
{
  printf '#!/usr/bin/env bash\n'
  printf 'printf "pre-screen: first record\\n"\n'
  printf 'sleep 3\n'
  printf 'printf "pre-screen: second record\\n"\n'
} > "$tools/loop/prescreen.sh"
javac --release 17 -d "$d/classes" "$R"/tools/issues/core/*.java >/dev/null 2>&1 || { echo "javac failed"; exit 1; }
export ISSUES_CORE_CLASSES="$d/classes"
export GH_STUB_DIR="$R/tools/loop/fixtures/scenarios/one"
export PATH="$R/tools/loop/fixtures:$R/tools/issues/fixtures:$PATH"
start=$(date +%s%N)
( cd "$p" && LOOP_ENGINE="$tools/loop" bash "$tools/loop/run.sh" --items 1 --minutes 90 --review off ) > "$d/console.log" 2>&1 &
pid=$!
phase=""
header=""
for _ in $(seq 400); do
  if [ -z "$header" ] && grep -q '^agent-issue-loop:' "$d/console.log" 2>/dev/null; then
    header=$(( ($(date +%s%N) - start) / 1000000 ))
  fi
  if [ -z "$phase" ] && grep -q '^loop: pre-screen · ' "$d/console.log" 2>/dev/null; then
    phase=$(( ($(date +%s%N) - start) / 1000000 ))
    break
  fi
  sleep 0.02
done
kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
printf '%s: header %sms, pre-screen line %sms\n' "$R" "${header:-none}" "${phase:-none}"
rm -rf "$d"
