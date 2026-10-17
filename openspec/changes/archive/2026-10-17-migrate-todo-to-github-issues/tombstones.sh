#!/usr/bin/env bash
# Build the tombstone findings for the backlog ids whose entry was removed before the migration.
#
# 87 live citations name 23 ids that no `## N.` section carries any more — the entries were closed and removed
# from the file by earlier cleanup passes, and the citations are provenance for a rule or a test. Rewriting
# them would touch 64 sites inside compiled sources and lose "which entry proved this"; instead every removed
# id gets one `status/retired` issue carrying its recovered text, so `§N` resolves as it always did (change
# `migrate-todo-to-github-issues`, decision of 2026-10-17).
#
# The text comes from git, never from memory: each id's last revision in `TODO.md` is read from the parent of
# the commit that removed it, and the entry's own metadata line supplies class and category. An entry that
# predates the metadata convention (15 of them) gets the tombstone's own labels — `class/improvement`,
# `category/specs-and-process` — and its body says so.
#
# Usage: tombstones.sh [--out <file>]        # writes the findings document; creates nothing
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../.." && pwd)
out="$here/tombstones.json"

while [ $# -gt 0 ]; do
  case "$1" in
    --out) out="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "tombstones: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

ids=(13 26 31 33 43 45 51 59 68 80 85 87 88 98 100 104 105 117 121 122 169)
tmp=$(mktemp -d "${TMPDIR:-/tmp}/tombstones.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT

findings="$tmp/findings.jsonl"
: > "$findings"

for id in "${ids[@]}"; do
  commit=$(git -C "$root" log --format=%H -S"## ${id}." -- TODO.md | head -1)
  if [ -z "$commit" ]; then
    echo "tombstones: §${id} has no revision in TODO.md's history — skipping" >&2
    continue
  fi
  git -C "$root" show "${commit}^:TODO.md" > "$tmp/parent.md" || {
    echo "tombstones: cannot read TODO.md at ${commit}^ — skipping §${id}" >&2
    continue
  }
  # the section: its heading, then everything up to the next heading
  awk -v n="$id" '
    $0 ~ ("^## " n "\\.") { inside = 1 }
    inside && /^## / && $0 !~ ("^## " n "\\.") { exit }
    inside { print }
  ' "$tmp/parent.md" > "$tmp/section.md"
  [ -s "$tmp/section.md" ] || { echo "tombstones: §${id} recovered nothing — skipping" >&2; continue; }

  heading=$(sed -n "1s/^## ${id}\. *//p" "$tmp/section.md")
  meta=$(grep -m1 '^\*\*id:\*\*' "$tmp/section.md" || true)
  if [ -n "$meta" ]; then
    clazz=$(sed -E 's/.*\*\*class:\*\* *([^·]*[^ ·]).*/\1/' <<< "$meta")
    category=$(sed -E 's/.*\*\*category:\*\* *([^·]*[^ ·]).*/\1/' <<< "$meta")
    recorded=$(sed -E 's/.*\*\*status:\*\* *([^ ·]*).*/\1/' <<< "$meta")
  else
    clazz="improvement"
    category="specs-and-process"
    recorded=""
  fi
  # the profile's two renames, applied as the adapter applies them
  case "$category" in rendering) category="map-rendering" ;; build) category="build-and-harness" ;; esac

  body=$(cat "$tmp/section.md")
  provenance=$(printf '\n\n---\n\n- **Retired** ℹ: removed from the backlog by commit `%s` (%s, "%s").\n' \
    "$(git -C "$root" log -1 --format=%h "$commit")" \
    "$(git -C "$root" log -1 --format=%ad --date=short "$commit")" \
    "$(git -C "$root" log -1 --format=%s "$commit")")
  if [ -n "$meta" ]; then
    provenance+=$(printf '  Its recorded state at removal was `%s`; the labels on this issue are that record'"'"'s.\n' "$recorded")
  else
    provenance+=$(printf '  This entry predates the metadata convention, so it carries no recorded class or category:\n  the labels here are the tombstone'"'"'s own, and its recorded state is the issue'"'"'s `status/retired`.\n')
  fi
  provenance+=$(printf '  The issue exists so that a live citation of `§%s` still resolves after the file was deleted\n  (change `migrate-todo-to-github-issues`); it is not work to be done.\n' "$id")

  jq -nc --arg sid "$id" --arg title "§${id} ${heading}" --arg cls "$clazz" --arg cat "$category" \
    --arg claim "$heading" --arg anchor "removed-in-${commit}" --arg body "${body}${provenance}" \
    '{sourceId: $sid, title: $title, class: $cls, category: $cat, claim: $claim, anchor: $anchor,
      labels: ["class/\($cls)", "category/\($cat)", "status/retired", "source/todo-import"],
      body: $body}' >> "$findings"
done

jq -n --slurpfile f "$findings" '{findings: $f}' > "$out" 2>/dev/null || {
  # --slurpfile on a JSONL file yields one object per line
  jq -s '{findings: .}' "$findings" > "$out"
}
count=$(jq '.findings | length' "$out")
printf 'tombstones: %s finding(s) written to %s\n' "$count" "$out"
jq -r '.findings[] | "  §\(.sourceId)  class=\(.class)  category=\(.category)  \(.title[0:70])"' "$out"
