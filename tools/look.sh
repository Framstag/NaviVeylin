#!/usr/bin/env bash
# look.sh — one call answers "where does this pattern occur", across the worktree and the submodule (issue #687).
#
# Why: of 16 987 `ctx_shell` calls over the loop's archive, 9 338 (55 %) carry a search or read verb — `grep` 4 997,
# `head` 4 955, `ls` 2 550, `sed` 2 208, `tail` 1 240, `wc` 1 077, `cat` 1 006, `awk` 533, `find` 393 — at a p50 of 52
# such calls per item, and the 2-grams that recur across the most items are these calls next to each other
# (`grep -> grep` 1 017x in 132 items, `grep -> read` 508x, `grep -> sed` 385x; `python3 tools/loop-spend.py`). One
# question is answered by three calls, and each call is one turn and one context re-read.
#
# The tools that answer it are there — `ctx_grep`, `ctx_ls`, `ctx_glob`, `ctx_read` — and unused (361 `ctx_grep` calls
# against ~1 700 shell `grep`s), so the habit is what is missing. This wrapper is the half a tool cannot serve: a
# pattern over the **whole tree at once**, the submodule included, printed once with the per-file counts and bounded
# like every other result. The other half — the refusal of the shell verbs where a tool does answer them — is
# `tools/pi-shell-refusal.mjs`, which every loop child is started with (`tools/pi-child-events.sh`).
#
# Usage: tools/look.sh <regex> [--files] [--context N]
#
#   <regex>      an ERE, as `grep -E` reads it (not a fixed string: quote it in the shell)
#   --files      the per-file counts only — "which files hold this", without the matched lines
#   --context N  N lines around each match, in `grep -C`'s own shape
#
#   Looked in: `LOOK_ROOT` when it names a tree (the seam the cases drive), else this repository. The libosmscout
#   submodule lives under the root (`app/src/main/cpp/libosmscout`), so one walk covers both and an unpopulated
#   submodule contributes nothing. Generated and machine-local trees are not the tree a search is answered from and
#   are skipped: `.git`, `.gradle`, `.kotlin`, `.idea`, `.cxx`, `build`, `.pi`, `node_modules`, `vcpkg`.
#
#   Bound: `LOOP_TOOL_RESULT_MAX_CHARS` when the environment names it (the adapter exports the recorded value into
#   every child), else the number `tools/loop/loop.conf` records — one home for it. `0` is no bound. Output at or
#   under the bound is printed whole; above it, the output is cut and one line says so, and what a cut leaves out is
#   a narrower pattern or `--files` away. The bound is applied here rather than left to
#   `tools/pi-trim-result.mjs`, so a cut line names the tool that cut it.
#
# Exit: 0 the pattern was found, 1 it was not, 2 the invocation or the pattern is unusable.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

pattern=""
files_only=0
context=""
while [ $# -gt 0 ]; do
  case "$1" in
    --files) files_only=1; shift ;;
    --context)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'look: %s needs a value\n' "$1" >&2; exit 2; }
      context="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    --) shift ;;
    -*) printf 'look: unknown argument %s\n' "$1" >&2; exit 2 ;;
    *) [ -z "$pattern" ] || { printf 'look: one pattern, and %s is a second\n' "$1" >&2; exit 2; }
      pattern="$1"; shift ;;
  esac
done

if [ -z "$pattern" ]; then
  printf "look: usage: %s <regex> [--files] [--context N]\n" "$(basename "$0")" >&2
  exit 2
fi
if [ -n "$context" ]; then
  case "$context" in
    ''|*[!0-9]*) printf 'look: --context takes a whole number of lines, got %s\n' "$context" >&2; exit 2 ;;
  esac
fi

root=${LOOK_ROOT:-$(cd "$here/.." && pwd)}
[ -d "$root" ] || { printf 'look: no tree at %s\n' "$root" >&2; exit 2; }

# The bound: the value the environment carries, else the project's recorded one, and `0` for "no bound".
bound=${LOOP_TOOL_RESULT_MAX_CHARS:-}
if [ -z "$bound" ]; then
  bound=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' \
    "$here/loop/loop.conf" 2>/dev/null | tail -1)
fi
case "$bound" in ''|*[!0-9]*) bound=0 ;; esac

excludes=(--exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.kotlin --exclude-dir=.idea
  --exclude-dir=.cxx --exclude-dir=build --exclude-dir=.pi --exclude-dir=node_modules --exclude-dir=vcpkg)
context_args=()
[ -z "$context" ] || context_args=(-C "$context")

found=$(mktemp) || { printf 'look: cannot create a temporary file\n' >&2; exit 2; }
errors=$(mktemp) || { printf 'look: cannot create a temporary file\n' >&2; exit 2; }
body=$(mktemp) || { printf 'look: cannot create a temporary file\n' >&2; exit 2; }
trap 'rm -f "$found" "$errors" "$body"' EXIT

# One walk, from the root, so every path in the answer is relative to it and can be cited as it stands. A pattern grep
# cannot read is a refusal and not "no match": the two must never read alike.
( cd "$root" && grep -rnI -E ${context_args[@]+"${context_args[@]}"} "${excludes[@]}" -e "$pattern" . ) \
  > "$found" 2> "$errors"
rc=$?
if [ "$rc" -gt 1 ]; then
  printf 'look: %s\n' "$(sed -n 1p "$errors")" >&2
  exit 2
fi

# What matched, per file, in the order the walk met them: a path on this line is `path:N:text` and a context line
# `-C` prints is `path-N-text`, so only the former is counted.
file_counts=$(awk -F: '
  /^[^:]+:[0-9]+:/ {
    p = $1; sub(/^\.\//, "", p)
    if (!(p in seen)) { seen[p] = 1; order[++k] = p }
    count[p]++
  }
  END { for (i = 1; i <= k; i++) printf "LOOK-FILE: %s %d\n", order[i], count[order[i]] }
' "$found")
matches=$(awk '/^[^:]+:[0-9]+:/ { n++ } END { print n + 0 }' "$found")
files=$(printf '%s' "$file_counts" | awk 'NF { n++ } END { print n + 0 }')

{
  printf "LOOK: %s matches in %s files of %s for '%s'\n" "$matches" "$files" "$root" "$pattern"
  [ -z "$file_counts" ] || printf '%s\n' "$file_counts"
  [ "$files_only" -eq 1 ] || sed 's|^\./||' "$found"
} > "$body"

size=$(wc -c < "$body")
if [ "$bound" -gt 0 ] && [ "$size" -gt "$bound" ]; then
  notice="[LOOK-BOUND: cut at $bound characters — narrow the pattern, or ask for --files]"
  # The notice's length is its bytes and not `${#notice}`, which counts characters in a UTF-8 locale: the em dash alone
  # would put the answer two bytes over the bound it claims to stay inside (measured by the case below).
  notice_bytes=$(printf '%s' "$notice" | wc -c)
  keep=$((bound - notice_bytes - 2))
  [ "$keep" -ge 1 ] || keep=1
  head -c "$keep" "$body"
  printf '\n%s\n' "$notice"
else
  cat "$body"
fi

[ "$rc" -eq 0 ] || exit 1
exit 0
