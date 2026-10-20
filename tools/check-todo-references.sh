#!/usr/bin/env bash
# Resolve every reference to the migrated backlog, and find every instruction that still points at the file it
# was migrated from (design D11).
#
# Two halves, and they mean different things:
#
#   * an **instruction** is a live file's line that names `TODO.md` and cites no item — it tells a reader or a
#     process to use a file that the migration deletes, so it FAILS. A line that names the file *and* cites an
#     item (`TODO.md §113`) is a citation: provenance for a claim, kept byte-identical;
#   * a **citation** is a `§<number>` on a line that also names `TODO`. Every one must resolve — through the
#     recorded `§ → issue` table, or through `tools/issues/list.sh --id <number>` with `--api` — because the
#     migration's own justification is that a `§N` written today still finds its entry after the file is gone.
#
# The exclusions are explicit, printed on every run, and each is a reason rather than a convenience: the
# archive and the four provenance specs are history (their text records what was known when it was written),
# and `.pi/tmp/**` is machine-local state. A file outside that list is scanned, so an exclusion cannot hide a
# failure.
#
# Reads text files only: no Gradle, no device, no token. With no `--api` the check needs no network at all —
# the table is the record — and with `--api` it additionally asks the tracker, which is the cross-read.
#
# Usage: check-todo-references.sh [--root <dir>] [--table <file>] [--api] [--quiet]
# Exit:  0 every instruction is rewired and every citation resolves, 1 one does not, 2 a usage or layout error.
set -uo pipefail

root=""
table_arg=""
use_api=0
quiet=0
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    --table) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; table_arg="${2:-}"; shift 2 ;;
    --api) use_api=1; shift ;;
    --quiet) quiet=1; shift ;;
    -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "check-todo-references: unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
[ -d "$root" ] || { echo "check-todo-references: no such directory: $root" >&2; exit 2; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/todo-refs.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

# The exclusions, each as `<glob>|<reason>`. A new one is a line here and a line in the printed list, so a
# reader sees what the check skipped and why; nothing else is skipped.
ALLOWLIST=(
  'openspec/changes/archive/*|history: the artifacts of a change record what was known when they landed'
  '.pi/tmp/*|machine-local state, never a project document'
  'openspec/specs/compass-button/spec.md|provenance: names where an item came from'
  'openspec/specs/osmscout-jni/spec.md|provenance: names where an item came from'
  'openspec/specs/route-analysis/spec.md|provenance: names where an item came from'
  'openspec/specs/route-planning-session/spec.md|provenance: names where an item came from'
  'tools/check-todo-references.sh|the check itself: it names the file in order to look for it'
  'tools/check-todo-references-selftest.sh|the fixtures of that check: the citations it must resolve and refuse live in it'
  'tools/issues/import/todo-md.sh|the source adapter: the old record is its subject, not an instruction'
  'tools/issues/core/TodoAdapter.java|the adapter half of that grammar'
  'tools/issues/core/TodoSections.java|that grammar'
  'Agentisches-Coding-NaviVeylin.md|the untracked strategy document that proposed this migration'
)

printf 'check-todo-references: excluded, and why:\n'
for entry in "${ALLOWLIST[@]}"; do
  printf '  %-56s %s\n' "${entry%%|*}" "${entry#*|}"
done

is_excluded() {
  local glob
  for entry in "${ALLOWLIST[@]}"; do
    glob="${entry%%|*}"
    # shellcheck disable=SC2254 # the glob is the pattern, deliberately
    case "$1" in $glob) return 0 ;; esac
  done
  return 1
}

# The file set: what the tree holds and the project means to keep. In a git working tree that is the tracked
# files plus the untracked ones git would carry (a new script is scanned before its first commit); the Ignored
# paths are the machine-local ones. Outside a repository — under the fixtures of the self-test — it is the plain
# tree.
scanned=()
skipped=0
if [ "$(git -C "$root" rev-parse --show-toplevel 2>/dev/null)" = "$root" ]; then
  while IFS= read -r -d '' path; do
    if is_excluded "$path"; then skipped=$((skipped + 1)); else scanned+=("$root/$path"); fi
  done < <(git -C "$root" ls-files --cached --others --exclude-standard -z)
else
  while IFS= read -r -d '' abs; do
    rel="${abs#"$root"/}"
    case "$rel" in .git/*) continue ;; esac
    if is_excluded "$rel"; then skipped=$((skipped + 1)); else scanned+=("$abs"); fi
  done < <(find "$root" -type f -not -path '*/.git/*' -print0)
fi

if [ "${#scanned[@]}" -eq 0 ]; then
  echo "check-todo-references: no file to scan under $root" >&2
  exit 2
fi

# Every line of every scanned file that mentions TODO, with `file:line:text`, in one grep. `-I` skips binary
# files, `-H` keeps the path on every hit, and the exit status is ignored: no hit is a fine answer.
findings="$tmp/hits"
: > "$findings"
printf '%s\0' "${scanned[@]}" | xargs -0 grep -nHI -e 'TODO' 2>/dev/null | sed "s|^$root/||" > "$findings" || true

# 1. the instruction half: a line naming the file must cite an item
awk -F: '
  {
    path = $1; line = $2; text = $0; sub(/^[^:]*:[0-9]*:/, "", text)
    if (text ~ /TODO\.md/ && text !~ /§/) {
      printf "::error file=%s,line=%s::an instruction survived the rewiring — this line names TODO.md and cites no item; name the tracker instead, or cite the item it means (D2, D11)\n", path, line
      bad++
    }
  }
  END { exit (bad > 0) ? 1 : 0 }' "$findings" > "$tmp/instructions"
cat "$tmp/instructions"
instructions_bad=$(grep -c . "$tmp/instructions" || true)

# 2. the citation half: every §<number> on a TODO-bearing line
awk -F: '
  {
    path = $1; line = $2; text = $0; sub(/^[^:]*:[0-9]*:/, "", text)
    while (match(text, /§[0-9]+/)) {
      # `§` is two bytes in UTF-8 and one character in a UTF-8 locale, so `RSTART + 1` can land inside it
      # (`§` + byte + digits under LC_ALL=C — measured 2026-10-17 in a token-less run, which then read
      # "?100" as the number and resolved nothing). Strip everything that is not a digit.
      num = substr(text, RSTART + 1, RLENGTH - 1)
      gsub(/[^0-9]/, "", num)
      if (num != "") print num "\t" path ":" line
      text = substr(text, RSTART + RLENGTH)
    }
  }' "$findings" > "$tmp/cited"
sort -u "$tmp/cited" > "$tmp/cited.uniq"

# the recorded table: `| § | issue |`, the migration's own second record of what each item became
if [ -n "$table_arg" ]; then
  tables=("$table_arg")
else
  tables=()
  for candidate in "$root"/openspec/changes/*/import-evidence.md "$root"/openspec/changes/archive/*/import-evidence.md; do
    [ -f "$candidate" ] && tables+=("$candidate")
  done
fi
: > "$tmp/table"
for candidate in ${tables[@]+"${tables[@]}"}; do
  [ -f "$candidate" ] || { echo "check-todo-references: no such table: $candidate" >&2; exit 2; }
  awk -F'|' '/^\|/ {
      n = $2; issue = $3
      gsub(/[^0-9]/, "", n); gsub(/[^0-9]/, "", issue)
      if (n != "" && issue != "") print n "\t#" issue
    }' "$candidate" >> "$tmp/table"
done
sort -u -o "$tmp/table" "$tmp/table"
table_rows=$(grep -c . "$tmp/table" || true)

resolve() { # $1 = the number; 0 when the recorded table or the tracker carries it
  local num="$1"
  grep -q "^$num	" "$tmp/table" && return 0
  [ "$use_api" -eq 1 ] || return 1
  bash "$root/tools/issues/list.sh" --id "$num" >/dev/null 2>&1
}

: > "$tmp/unresolved"
: > "$tmp/resolved"
while IFS=$'\t' read -r num where; do
  if resolve "$num"; then
    printf '%s\n' "$num" >> "$tmp/resolved"
  else
    printf '::error file=%s::§%s resolves to nothing — no issue carries <!-- nvid: %s --> and the recorded table has no row for it (D3, D11)\n' "$where" "$num" "$num" >> "$tmp/unresolved"
  fi
done < "$tmp/cited.uniq"
cat "$tmp/unresolved"
unresolved_bad=$(grep -c . "$tmp/unresolved" || true)

cited_distinct=$(sort -u "$tmp/resolved" | grep -c . || true)
[ "$quiet" -eq 1 ] || printf 'check-todo-references: %d line(s) scanned, %d distinct citation(s), %d in the recorded table\n' \
  "$(grep -c . "$findings" || true)" "$cited_distinct" "$table_rows"

if [ "$unresolved_bad" -gt 0 ] || [ "$instructions_bad" -gt 0 ]; then
  printf 'check-todo-references: %d instruction(s) survived, %d citation(s) do not resolve\n' \
    "$instructions_bad" "$unresolved_bad"
  exit 1
fi

if [ "$use_api" -eq 1 ]; then
  printf 'check-todo-references: %d citation(s) resolve, cross-read against the recorded table and the tracker; %d file(s) excluded by the allowlist above\n' \
    "$cited_distinct" "$skipped"
else
  printf 'check-todo-references: %d citation(s) resolve through the recorded table; %d file(s) excluded by the allowlist above\n' \
    "$cited_distinct" "$skipped"
fi
exit 0
