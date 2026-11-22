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
# Usage: tools/look.sh <regex> [--path <path>] [--files] [--context N]
#
#   <regex>      an ERE, as `grep -E` reads it (not a fixed string: quote it in the shell)
#   --path <p>   the walk is this file or this directory, named relative to the root — one document's headings
#                (`--path guidelines/Build.md`) instead of every document's
#   --files      the per-file counts only — "which files hold this", without the matched lines
#   --context N  N lines around each match, in `grep -C`'s own shape
#   --section <regex>
#                one section of the file `--path` names: the `## ` heading whose line the regex matches and the body
#                that follows it, up to the document's next `## ` or `# ` heading — so the route a document map takes,
#                "list the sections, then read the one that owns the rule", is two calls and never a page range. A
#                regex that matches no `## ` heading is exit 1 and names the file and the regex, because an empty
#                answer and a refused one must not read alike
#   --case <name>
#                one `case_*` body of the shell suite `--path` names, `name` with or without its `case_` prefix:
#                printed from its `case_<name>() {` line through the `}` that closes it, which is the unit
#                `tools/loop/tests/*` is organised by. A name the file does not hold is exit 1 and names it
#
#   Both modes read one file, so `--path` is theirs and a directory is a refusal; both print the file's lines as
#   `path:N:text`, as a match does, and both are cut by the bound below. A name prints the ranges it matches and
#   no other, and `--files`/`--context` belong to a pattern's matches rather than to a range.
#
#   Looked in: `LOOK_ROOT` when it names a tree (the seam the cases drive), else this repository. The libosmscout
#   submodule lives under the root (`app/src/main/cpp/libosmscout`), so one walk covers both and an unpopulated
#   submodule contributes nothing. Generated and machine-local trees are not the tree a search is answered from and
#   are skipped: `.git`, `.gradle`, `.kotlin`, `.idea`, `.cxx`, `build`, `.pi`, `node_modules`, `vcpkg`. A `--path`
#   the root does not hold, or one that carries `..`, is a refusal and not an empty answer: nothing found and
#   nothing searched must never read alike, and a scope is one path of the tree.
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
mode=""
files_only=0
context=""
scope=""
while [ $# -gt 0 ]; do
  case "$1" in
    --files) files_only=1; shift ;;
    --path)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'look: %s needs a value\n' "$1" >&2; exit 2; }
      scope="${2:-}"; shift 2 ;;
    --context)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'look: %s needs a value\n' "$1" >&2; exit 2; }
      context="${2:-}"; shift 2 ;;
    --section|--case)
      [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'look: %s needs a value\n' "$1" >&2; exit 2; }
      [ -z "$mode" ] \
        || { printf 'look: --%s and --%s both print one range, and one call prints one\n' "$mode" "${1#--}" >&2; exit 2; }
      mode=${1#--}; pattern="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,/^# Exit:/p' "$0"; exit 0 ;;
    --) shift ;;
    -*) printf 'look: unknown argument %s\n' "$1" >&2; exit 2 ;;
    *) [ -z "$pattern" ] || { printf 'look: one pattern, and %s is a second\n' "$1" >&2; exit 2; }
      pattern="$1"; shift ;;
  esac
done

if [ -z "$pattern" ]; then
  printf "look: usage: %s <regex> [--path <path>] [--files] [--context N] | --section <regex> --path <file> | --case <name> --path <file>\n" "$(basename "$0")" >&2
  exit 2
fi
if [ -n "$context" ]; then
  case "$context" in
    ''|*[!0-9]*) printf 'look: --context takes a whole number of lines, got %s\n' "$context" >&2; exit 2 ;;
  esac
fi

root=${LOOK_ROOT:-$(cd "$here/.." && pwd)}
[ -d "$root" ] || { printf 'look: no tree at %s\n' "$root" >&2; exit 2; }

# A mode's scope is one file and nothing else: `--section` prints a section of the document `--path` names, and `--case`
# a case of the suite it names, so a call without `--path` is missing the value the mode is about and a directory is
# not a scope either. `--files` and `--context` are a pattern's matches, and a range is not a pattern: refused here
# rather than silently ignored.
if [ -n "$mode" ]; then
  if [ -n "$context" ]; then
    printf 'look: --%s prints one range, and --context belongs to a pattern\n' "$mode" >&2; exit 2
  fi
  if [ "$files_only" -eq 1 ]; then
    printf 'look: --%s prints one range, and --files belongs to a pattern\n' "$mode" >&2; exit 2
  fi
  if [ "$mode" = case ]; then
    case "$pattern" in
      ''|*[!A-Za-z0-9_]*) printf 'look: --case takes a case name, and %s is not one\n' "$pattern" >&2; exit 2 ;;
    esac
  fi
fi

# The walk's target, and the label the answer's first line carries: the whole tree, or the one path `--path` names —
# a file is one document's own sections, a directory a subtree. A target the tree does not hold is a refusal rather
# than an empty answer, because "nothing was searched" and "nothing matched" must not read alike.
target=.
where="$root"
if [ -n "$scope" ]; then
  case "$scope" in
    /*) printf 'look: --path takes a path of the tree, not %s\n' "$scope" >&2; exit 2 ;;
  esac
  case "/$scope/" in
    */../*) printf 'look: --path carries .., and a scope is one path of the tree\n' >&2; exit 2 ;;
  esac
  [ -e "$root/$scope" ] || { printf 'look: no path %s in %s\n' "$scope" "$root" >&2; exit 2; }
  target=${scope%/}
  where="$root/$target"
fi
if [ -n "$mode" ]; then
  [ -n "$scope" ] || { printf 'look: --%s prints one range of one file, and --path names none\n' "$mode" >&2; exit 2; }
  [ -f "$where" ] || { printf 'look: --%s prints one range of one file, and %s is not one\n' "$mode" "$scope" >&2; exit 2; }
fi

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

# One walk, from the root, so every path in the answer is relative to it and can be cited as it stands; the target is
# the tree itself unless `--path` scoped the walk. A pattern grep cannot read is a refusal and not "no match": the two
# must never read alike.
if [ "$mode" = section ]; then
  # The section the regex names: `## ` at the document's own level starts one, `# ` ends the one before it, and a
  # `###` heading is a sub-section of the section it sits in and not a section of its own — the level the listing
  # shows (`'^## '`) is what a section is. The pattern and the label travel in the environment, not in `-v`, whose
  # own escape processing would rewrite a regex a reader quoted.
  pat="$pattern" rel="$target" awk '
    /^# / || /^## / {
      if (on) on = 0
      if ($0 ~ /^## / && $0 ~ ENVIRON["pat"]) { on = 1; hits++ }
    }
    on { printf "%s:%d:%s\n", ENVIRON["rel"], NR, $0 }
    END { if (hits + 0 == 0) exit 3 }
  ' "$where" > "$found" 2> "$errors"
  rc=$?
elif [ "$mode" = case ]; then
  # The case the name names: its `case_<name>() {` line through the `}` that closes it, and neither the case before
  # it nor the one after it. A suite is a flat list of these, so the first `}` at the left margin is the end.
  pat="^case_${pattern#case_}\\(\\)[[:space:]]*\\{" rel="$target" awk '
    $0 ~ ENVIRON["pat"] { on = 1; hits++ }
    on { printf "%s:%d:%s\n", ENVIRON["rel"], NR, $0; if ($0 == "}") on = 0 }
    END { if (hits + 0 == 0) exit 3 }
  ' "$where" > "$found" 2> "$errors"
  rc=$?
else
  ( cd "$root" && grep -rnIH -E ${context_args[@]+"${context_args[@]}"} "${excludes[@]}" -e "$pattern" "$target" ) \
    > "$found" 2> "$errors"
  rc=$?
fi
# The one answer a mode can give that a walk cannot: the name it was given is not in the file it was given. It is a
# not-found — exit 1, as a pattern the tree does not hold — and it names both halves, so a reader re-runs nothing.
if [ "$rc" -eq 3 ]; then
  if [ "$mode" = section ]; then
    printf "look: no section of %s matches '%s'\n" "$target" "$pattern" >&2
  else
    printf "look: no case %s in %s\n" "$pattern" "$target" >&2
  fi
  exit 1
fi
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
mode_hits=0
if [ -n "$mode" ]; then
  # A range's answer is its own lines: the count its first line carries is how many sections or cases the name
  # printed, and the per-file counts a walk prints are not part of it — one file was read, and the count above says
  # nothing a reader of a section wants.
  file_counts=""; files=0
  if [ "$mode" = section ]; then
    mode_hits=$(awk '/^[^:]+:[0-9]+:## / { n++ } END { print n + 0 }' "$found")
  else
    mode_hits=$(awk '/^[^:]+:[0-9]+:case_[A-Za-z0-9_]+\(\)/ { n++ } END { print n + 0 }' "$found")
  fi
fi

{
  if [ "$mode" = section ] || [ "$mode" = case ]; then
    plural=""; [ "$mode_hits" -eq 1 ] || plural="s"
    printf "LOOK: %s %s%s of %s for '%s'\n" "$mode_hits" "$mode" "$plural" "$where" "$pattern"
  else
    printf "LOOK: %s matches in %s files of %s for '%s'\n" "$matches" "$files" "$where" "$pattern"
  fi
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
