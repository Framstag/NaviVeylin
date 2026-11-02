#!/usr/bin/env bash
# check-openspec-hygiene.sh — the four silent-failure classes of the OpenSpec artifacts, in one script so
# that the pipeline's step and the self-test that mutates each of them run the same code.
#
# All four are silent in the OpenSpec CLI: the command exits 0 and the artifact is wrong.
#
#   1. a `tasks.md` whose items are bare `1. [x]` lines — the task parser only reads `- [x]` / `- [ ]`, so
#      the change reports `completedTasks: 0, totalTasks: 0, status: "no-tasks"` while `openspec status`
#      simultaneously reports `isPlanningComplete: true`. A change can look stalled, or finished, with no
#      diagnostic (issue #45, TODO.md §17).
#   2. an `operations.*.guidance` list entry containing `: ` that is not quoted — it parses as a YAML
#      mapping, the array fails the array-of-strings check and the CLI drops that operation's whole
#      guidance behind a single warning (it hid the `apply` guidance until 2026-10-04).
#   3. a capability spec whose `## Purpose` is still the sentence `openspec archive` writes for a
#      capability it creates — `openspec validate <id> --type spec` only warns and exits 0, and no delta
#      can repair it, so nothing else would ever report it.
#      The pass is a `find` over `openspec/specs/` because capability specs sit one level deeper as well
#      (`auto/*`, `share/*`) and a flat glob cannot see them.
#   4. a relocation record whose `owner:` line cites a directory listing — a glob such as
#      `.pi/skills/*/SKILL.md` — in place of the owners the removed block named. The CLI validates the
#      change and exits 0, so the record's claim then holds for a set its reader cannot identify, and
#      nothing reports it (issue #732).
#      Only the open changes are read: an archived record is history, and it is neither inspected nor
#      rewritten here. A citation inside a fenced block is a quotation, not a citation.
#
# Usage: tools/check-openspec-hygiene.sh [--root <dir>]
# Exit:  0 every artifact is consistent; 1 findings (each printed as a GitHub `::error` annotation);
#        2 usage, or a root that carries no `openspec/`.
set -uo pipefail

root=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0")" "$1" >&2; exit 2; }; root="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "$(basename -- "$0"): unknown argument '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$root" ] || root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d "$root/openspec" ] || { echo "$(basename -- "$0"): no $root/openspec" >&2; exit 2; }

fail=0
changes=0

for f in "$root"/openspec/changes/*/tasks.md; do
  [ -e "$f" ] || continue
  changes=$((changes + 1))
  rel="${f#"$root"/}"
  if ! grep -qE '^- \[[ x]\]' "$f"; then
    echo "::error file=$rel::no '- [x]' / '- [ ]' task markers — the parser reports 0 tasks"
    fail=1
  fi
  if grep -qE '^[0-9]+\. \[[ x]\]' "$f"; then
    echo "::error file=$rel::bare numbered task marker — use '- [x] N.M' under a '## N.' heading"
    fail=1
  fi
done

config="$root/openspec/config.yaml"
if [ -f "$config" ]; then
  bad=$(awk '/^  (apply|archive):/{inop=1;next} /^  [a-z-]+:/{inop=0} inop && /^      - /{ line=$0; sub(/^      - /,"",line); if (line !~ /^"/ && line ~ /: /) print line }' "$config")
  if [ -n "$bad" ]; then
    echo "::error file=openspec/config.yaml::unquoted guidance entry containing ': ' (parses as a mapping; the operation's guidance is dropped)"
    printf '%s\n' "$bad"
    fail=1
  fi
fi

# 4. a relocation record that cites its owners as a directory listing (issue #732). The glob is the form a
# listing takes, and it stands in for the set the removed block named, so a reader cannot check the claim for
# that set — the owner lines are read, the archived changes are not, and a citation inside a fenced block is a
# quotation rather than a citation, exactly as `tools/check-doc-routes.sh` reads a quoted section reference.
owner_offenders=""
for change_dir in "$root"/openspec/changes/*/; do
  [ -d "$change_dir" ] || continue
  [ "$(basename "$change_dir")" = "archive" ] && continue
  found=$(find "$change_dir" -type f \( -name '*.md' -o -name '*.txt' \) -exec awk '
    FNR == 1 { fence = 0 }
    /^[[:space:]]*(```|~~~)/ { fence = !fence; next }
    !fence && /^[[:space:]]*owners?:/ && /\*/ { printf "%s:%d\n", FILENAME, FNR }' {} + 2>/dev/null)
  [ -n "$found" ] && owner_offenders="$owner_offenders$found"$'\n'
done
if [ -n "$(printf '%s' "$owner_offenders" | tr -d '[:space:]')" ]; then
  echo "::error::a relocation record cites its owners as a directory listing — name the owners the removed block named, one path per owner, so a reader can check the claim for the set it is about"
  printf '%s' "$owner_offenders" | sed '/^$/d; s|^'"$root"'/||'
  fail=1
fi

spec_bad=$(find "$root/openspec/specs" -name spec.md -exec awk '/^## Purpose/{inp=1;next} inp&&/^## /{inp=0} inp&&/TBD - created by archiving/{printf "%s:%d\n", FILENAME, FNR}' {} + 2>/dev/null)
if [ -n "$spec_bad" ]; then
  echo "::error::a capability spec's '## Purpose' is still the archive placeholder — write what the capability is for (a delta's Purpose is read only when the capability is created)"
  printf '%s\n' "$spec_bad" | sed "s|^$root/||"
  fail=1
else
  # The sentence the requirement names (`documentation-ownership` — "A capability spec states what the
  # capability is for"), printed whether or not the other two classes found something: it reports this pass,
  # and the classes below report their own.
  echo "Every capability spec states its Purpose."
fi

if [ "$fail" -eq 0 ]; then
  echo "OpenSpec artifacts are consistent: $changes open change(s) carry task markers, the operation guidance is quoted, and every capability spec states its Purpose."
fi
exit "$fail"
