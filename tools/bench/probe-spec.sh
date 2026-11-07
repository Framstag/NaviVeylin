#!/usr/bin/env bash
# probe-spec.sh — write one model-probe spec and its answer key for an archived loop item.
#
# The probe is two parts in one session: (A) replay the item's recorded reviewer round from the rendered prompt
# the loop actually handed that round, (B) read a second diff whole and answer anchors spread over it. Part A
# judges capability against the round's own recorded report; part B judges the window on a load the shape of the
# archive's p99 diff. Nothing here runs a model — `SKILL.md` says how the children are started and graded.
#
# Usage: probe-spec.sh <item-dir> <spec-out.md> <key-out.md> [second-diff]
#
#   <item-dir>     `.pi/issue-loop/items/<n>` — needs `review-1.diff`, `reviewer-prompt-1.md`, `work-package.md`
#   <second-diff>  a second review diff to load (default `review-2.diff` when the item has one)
#
# Exit: 0 written; 2 the item directory lacks a file the probe needs.
set -uo pipefail

item=${1:-}
spec=${2:-}
key=${3:-}
second=${4:-}
if [ -z "$item" ] || [ -z "$spec" ] || [ -z "$key" ]; then
  echo "usage: probe-spec.sh <item-dir> <spec-out.md> <key-out.md> [second-diff]" >&2
  exit 2
fi
[ -d "$item" ] || { echo "probe-spec: no such item directory: $item" >&2; exit 2; }
d1=$item/review-1.diff
[ -f "$d1" ] || { echo "probe-spec: no $d1" >&2; exit 2; }
[ -f "$item/reviewer-prompt-1.md" ] || { echo "probe-spec: no $item/reviewer-prompt-1.md" >&2; exit 2; }
[ -f "$item/work-package.md" ] || { echo "probe-spec: no $item/work-package.md" >&2; exit 2; }
if [ -z "$second" ] && [ -f "$item/review-2.diff" ]; then second=$item/review-2.diff; fi
[ -n "$second" ] && [ -f "$second" ] || { echo "probe-spec: no second diff (pass one, or keep review-2.diff)" >&2; exit 2; }

lines1=$(wc -l < "$d1"); bytes1=$(wc -c < "$d1")
lines2=$(wc -l < "$second"); bytes2=$(wc -c < "$second")
files1=$(grep -c '^diff --git ' "$d1"); files2=$(grep -c '^diff --git ' "$second")

# Anchors: spread over both files so a model that read only the head cannot answer them, and each one distinctive —
# a bare `+`, a lone space or a one-character line is guessable and grades nothing. `pick_line` walks forward from
# the wanted line until it finds a line long enough to quote.
pick_line() {  # file start → a line number whose text is distinctive
  local f=$1 n=$2 i=0 t
  while [ $i -lt 60 ]; do
    t=$(sed -n "$(( n + i ))p" "$f")
    if [ ${#t} -ge 25 ] && ! printf '%s' "$t" | grep -qE '^[-+ ]$'; then printf '%s\n' "$(( n + i ))"; return; fi
    i=$(( i + 1 ))
  done
  printf '%s\n' "$n"
}

a1_line=5
a2_line=$(pick_line "$d1" $(( lines1 * 5 / 6 )))   # late in the first diff, far from the head
a3_line=$(pick_line "$second" $(( lines2 / 2 )))    # middle of the second diff
last_hunk=$(grep -n '^@@' "$second" | tail -1 | cut -d: -f1)
first_plus=$(tail -n "+$last_hunk" "$second" | grep -n '^+' | head -1 | cut -d: -f1)
first_plus=$(( last_hunk + first_plus - 1 ))

emit_anchor() {  # file line → one fenced-quoted answer line for the key
  printf '%s\n' "\`$(sed -n "${2}p" "$1")\`" | sed 's/^/    /'
}

cat > "$spec" <<SPEC
# Model probe — reviewer role, two parts in one session

You are being measured, not employed. Read only: write no file, run no version control, no gate, no test suite,
no network. Your whole output is the two sections below, in this order, starting with \`## PART A\`.

## PART A — reviewer replay (capability ceiling)

\`$item/reviewer-prompt-1.md\` is a fully rendered reviewer task from a completed loop run. Read it and follow it
**exactly**: the same eight numbered items, in order, the same per-scenario verdict table, the same
\`LOOP-BLOCKING:\` / \`VERDICT:\` / \`LOOP-CORRECTIONS-ONLY:\` / \`LOOP-DECISION:\` trailer.

Inputs it names, all in the tree: the diff \`$d1\` ($lines1 lines, $bytes1 B) read whole; \`$item/work-package.md\`;
\`$item/worker-report.md\`; the evidence under \`$item/\`; and the guideline and spec files the review's reading
set names.

**Do not open \`$item/reviewer-report-*.md\`.** They are the answer key; reading one invalidates the run.

## PART B — context ceiling (window size)

Continue in the same session, without dropping what you already read.

1. Read \`$second\` whole ($lines2 lines, $bytes2 B). The read tool caps one result at roughly 2 000 lines / 50 kB,
   so a file this size must be paged with \`offset\`/\`limit\` until its last line is taken.
2. Answer, quoting exact text, byte-exact and with the original indentation:
   - \`C1\` — line $a1_line of \`review-1.diff\`;
   - \`C2\` — line $a2_line of \`review-1.diff\`;
   - \`C3\` — the last line of \`review-1.diff\`;
   - \`C4\` — line $a3_line of the second diff;
   - \`C5\` — the last \`@@\` header of the second diff, and the first \`+\` line inside that hunk;
   - \`C6\` — the number of \`diff --git\` lines in each file.
3. Report: \`R1\` read calls per file; \`R2\` total characters read; \`R3\` whether any call was truncated, refused or
   errored, with its exact text.

If a call fails for any reason, print the exact failure text under \`R3\` and stop there.
SPEC

{
  echo "# Answer key — $item"
  echo
  echo "Generated $(date -u +%Y-%m-%dT%H:%M:%SZ) by \`tools/bench/probe-spec.sh\`; keep it away from the children."
  echo
  echo "## Part B anchors"
  echo
  echo "- \`C1\` — line $a1_line of \`review-1.diff\`:"
  emit_anchor "$d1" "$a1_line"
  echo "- \`C2\` — line $a2_line of \`review-1.diff\`:"
  emit_anchor "$d1" "$a2_line"
  echo "- \`C3\` — last line of \`review-1.diff\`:"
  emit_anchor "$d1" "$lines1"
  echo "- \`C4\` — line $a3_line of \`$(basename "$second")\`:"
  emit_anchor "$second" "$a3_line"
  echo "- \`C5\` — last \`@@\` header of \`$(basename "$second")\` (line $last_hunk):"
  emit_anchor "$second" "$last_hunk"
  echo "  …and the first \`+\` line in that hunk (line $first_plus):"
  emit_anchor "$second" "$first_plus"
  echo "- \`C6\` — \`diff --git\` lines: $files1 in \`review-1.diff\`, $files2 in \`$(basename "$second")\`."
  echo
  echo "## Part A"
  echo
  echo "The answer key is the item's own recorded round: \`$item/reviewer-report-1.md\`. Grading is \`SKILL.md\`'s"
  echo "rubric; read that report only after every child has finished."
  echo
  echo "Load this session asks for: $(( bytes1 + bytes2 )) B of diff — roughly $(( (bytes1 + bytes2) / 4 )) tokens,"
  echo "inside a reviewer round's own reading set."
} > "$key"

printf 'spec: %s\nkey:  %s\nload: %s B of diff (%s + %s)\n' \
  "$spec" "$key" "$(( bytes1 + bytes2 ))" "$bytes1" "$bytes2"
