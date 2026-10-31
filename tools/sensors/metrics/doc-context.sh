#!/usr/bin/env bash
# doc-context.sh — the documentation-context rows' measurement: what a session must read before it works.
#
# The start-of-session cost is the entry document a session is handed plus the guideline sections it is then
# sent to. Nothing measured either, so a section that doubled broke nothing and announced nothing (issue #197).
# Four rows share this measurement, and the registry's `scope` field says which half each one bounds:
#
#   routed-section     every section the routing table names, one condition per (document, section)
#   entry-document     the always-read entry document the table lives in, one condition for it
#   normative-document every normative `guidelines/<Concern>.md` (companions excluded), one condition per document
#   normative-purity   a measured figure, a tally, a dated provenance or a `### Why …` heading inside a
#                      normative document, one condition per (document, token), reported with its line
#
# The routing table is the *entry document's own* `## Documentation Map` region, so the pairs are derived where a
# reader reads them, never from a list here: a section the table stops naming stops being a condition without an
# edit to this script, and a document no row of the table names is not part of the start cost at all — it is read
# only by someone already working in that area. The documents are taken from `guidelines/*.md` at run time, the
# same way `tools/check-doc-routes.sh` takes them, so a document added later is covered without editing this file.
#
# The unit is bytes, and a section's cost is the byte count of its lines from the `## ` heading through the line
# before the next `## ` heading — the ruler `documentation-ownership`, "The routing cost is measured", already
# states. The line count is carried beside it and the bound is read in lines as well, because a document's size
# is read in lines: at 80 bytes/line — the mean line width of this project's entry document and its guidelines,
# measured 2026-10-29 — the section row's 12 000 bytes are 150 lines.
#
# The bound and the consecutive count are the registry row's and arrive in `SENSORS_THRESHOLD` and
# `SENSORS_CONSECUTIVE`: this script holds no bound constant, so retuning a check is one edit in `sensors.json`
# (design D9, D10). One routed section is one condition — the fingerprint is the document and the section, so an
# item closes when that section shrinks.
#
# Inputs (set by `run.sh`): SENSORS_THRESHOLD, SENSORS_ROW, SENSORS_CONDITIONS, SENSORS_ROOT.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/sensors/lib.sh
source "$here/../lib.sh"

row="${SENSORS_ROW:-{\}}"
threshold="${SENSORS_THRESHOLD:-0}"
scope=$(jq -r '.scope // "routed-section"' <<< "$row")
entry=$(jq -r '.entry // empty' <<< "$row")
root="${SENSORS_ROOT:-.}"
tab=$(printf '\t')
# The mean line width of this project's documents, for the line count a reader compares a bound against. It is a
# reading aid, never a conversion the verdict goes through: the byte count is the measurement and the bound's unit.
line_bytes=80

tmp=$(mktemp -d "${TMPDIR:-/tmp}/doc-context.XXXXXX") || {
  case "$scope" in
    entry-document) sensors_emit_error '{"path":"unknown"}' "no-scratch-directory" ;;
    normative-document) sensors_emit_error '{"document":"unknown"}' "no-scratch-directory" ;;
    normative-purity) sensors_emit_error '{"document":"unknown","token":"unknown"}' "no-scratch-directory" ;;
    *) sensors_emit_error '{"document":"unknown","section":"unknown"}' "no-scratch-directory" ;;
  esac
  exit 0
}
trap 'rm -rf "$tmp"' EXIT

# A tree the measurement cannot read is the third outcome, never a clean one: an unreadable entry document or
# routing table would otherwise reach the tracker as "no section is past the bound". The fields are the row's
# own, so the finding is the contract's shape and the runner reads it as the failed check it is (design D5).
fail_check() { # <reason-token>
  case "$scope" in
    entry-document) sensors_emit_error '{"path":"unknown"}' "$1" ;;
    normative-document) sensors_emit_error '{"document":"unknown"}' "$1" ;;
    normative-purity) sensors_emit_error '{"document":"unknown","token":"unknown"}' "$1" ;;
    *) sensors_emit_error '{"document":"unknown","section":"unknown"}' "$1" ;;
  esac
}

# The entry document is required by the two scopes that read it (the entry document itself, and the routing
# table its pairs are derived from); the normative-document and normative-purity scopes read `guidelines/`
# alone and declare no entry.
case "$scope" in
  entry-document|routed-section)
    [ -n "$entry" ] || { fail_check "no-entry-document-declared"; exit 0; }
    entry_path="$root/$entry"
    [ -f "$entry_path" ] || { fail_check "no-entry-document"; exit 0; }
    ;;
esac

bytes_of() { wc -c < "$1" | tr -d ' '; }
lines_of() { wc -l < "$1" | tr -d ' '; }

# A normative document is a `guidelines/<Concern>.md` that is not a companion: a `.why.md` (rationale) or a
# `.recipes.md` (procedure) is the same concern's other layer, read against its base document and bounded by
# none of these rows.
normative_documents() {
  for path in "$root"/guidelines/*.md; do
    [ -f "$path" ] || continue
    case "$(basename "$path" .md)" in *.why|*.recipes) continue ;; esac
    printf '%s\n' "$path"
  done
}

# One line per occurrence of text the normative layer must not hold: a `(measured YYYY-…)` figure, a `### Why …`
# heading, an `issue #N` reference, or a fenced block that carries a command's output — a `tests=`/`failures=`
# tally or a dated line. A fenced block that is just a command (no output) is the rule's own statement and is
# not reported (`documentation-ownership`, "A normative document carries no rationale and no procedure").
purity_scan() { # $1 path → lines "<line><TAB><token>"
  LC_ALL=C awk '
    /^```/ {
      if (fence) { fence = 0; if (ftally) print fstart "\toutput" } else { fence = 1; fstart = NR; ftally = 0 }
      next
    }
    fence {
      if ($0 ~ /tests=|failures=/ || $0 ~ /[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/) ftally = 1
      next
    }
    {
      if ($0 ~ /\(measured [0-9][0-9][0-9][0-9]-/) print NR "\tmeasured"
      if ($0 ~ /^### Why /) print NR "\twhy"
      if ($0 ~ /issue #[0-9]/) print NR "\tissue"
    }' "$1"
}

# -------------------------------------------------------------------------------------------- the entry document

if [ "$scope" = "entry-document" ]; then
  size=$(bytes_of "$entry_path")
  entry_lines=$(lines_of "$entry_path")
  fields=$(jq -nc --arg path "$entry" '{path: $path}')
  fingerprint=$(sensors_fingerprint "$fields" "path")
  printf 'doc-context: %s bytes=%s lines=%s (bound %s bytes, ~%s lines at %s bytes/line)\n' \
    "$entry" "$size" "$entry_lines" "$threshold" "$((threshold / line_bytes))" "$line_bytes" >&2
  findings=0
  if sensors_compare "$size" "$threshold" greater-than; then
    sensors_emit "$fingerprint" "$size" violation "$fields" "" \
      "the entry document is past its size bound" \
      "$entry is ${size} bytes in ${entry_lines} lines, past the row's bound of ${threshold} bytes (~$((threshold / line_bytes)) lines at ${line_bytes} bytes/line)"
    findings=$((findings + 1))
  fi
  # A recorded condition on the entry document clears under its own path, with the number this run measured.
  while IFS= read -r condition; do
    [ -n "$condition" ] || continue
    path=$(jq -r '.condition.path // empty' <<< "$condition")
    [ "$path" = "$entry" ] || continue
    sensors_compare "$size" "$threshold" greater-than && continue
    sensors_emit "$fingerprint" "$size" clear "$fields" "" \
      "the entry document is inside its size bound" \
      "$entry is ${size} bytes in ${entry_lines} lines, inside the row's bound of ${threshold} bytes"
    findings=$((findings + 1))
  done < <(jq -c '.[]? | select(.state == "OPEN")' <<< "${SENSORS_CONDITIONS:-[]}")
  if [ "$findings" -eq 0 ]; then
    fields='{"path":"none"}'
    fingerprint=$(sensors_fingerprint "$fields" "path")
    sensors_emit "$fingerprint" "none" clear "$fields" "" \
      "the entry document is inside its size bound" \
      "$entry is ${size} bytes in ${entry_lines} lines, inside the row's bound of ${threshold} bytes, and no size condition is recorded"
  fi
  exit 0
fi

# --------------------------------------------------------------------------------- the normative documents

# `document-size`: every normative document (`guidelines/<Concern>.md`, companions excluded) is bounded by the
# row's own threshold, one condition per document, identified by its path. The row is what retires the status
# quo where a 166 383-byte file was legal merely because no section of it was routed past its own bound.
if [ "$scope" = "normative-document" ]; then
  findings=0
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    doc=$(basename "$path" .md)
    size=$(bytes_of "$path"); doc_lines=$(lines_of "$path")
    fields=$(jq -nc --arg d "$doc" '{document: $d}')
    fingerprint=$(sensors_fingerprint "$fields" "document")
    printf 'doc-context: %s.md bytes=%s lines=%s (bound %s bytes)\n' "$doc" "$size" "$doc_lines" "$threshold" >&2
    sensors_compare "$size" "$threshold" greater-than || continue
    sensors_emit "$fingerprint" "$size" violation "$fields" "" \
      "the normative document is past its size bound" \
      "guidelines/${doc}.md is ${size} bytes in ${doc_lines} lines, past the row's bound of ${threshold} bytes (~$((threshold / line_bytes)) lines at ${line_bytes} bytes/line)"
    findings=$((findings + 1))
  done < <(normative_documents)
  while IFS= read -r condition; do
    [ -n "$condition" ] || continue
    doc=$(jq -r '.condition.document // empty' <<< "$condition")
    [ -n "$doc" ] || continue
    path="$root/guidelines/$doc.md"
    [ -f "$path" ] || continue
    size=$(bytes_of "$path")
    sensors_compare "$size" "$threshold" greater-than && continue
    fields=$(jq -nc --arg d "$doc" '{document: $d}')
    fingerprint=$(sensors_fingerprint "$fields" "document")
    sensors_emit "$fingerprint" "$size" clear "$fields" "" \
      "the normative document is inside its size bound" \
      "guidelines/${doc}.md is ${size} bytes, inside the row's bound of ${threshold} bytes"
    findings=$((findings + 1))
  done < <(jq -c '.[]? | select(.state == "OPEN")' <<< "${SENSORS_CONDITIONS:-[]}")
  if [ "$findings" -eq 0 ]; then
    fields='{"document":"none"}'
    fingerprint=$(sensors_fingerprint "$fields" "document")
    sensors_emit "$fingerprint" "none" clear "$fields" "" \
      "no normative document is past its size bound" \
      "every normative document under guidelines/ is inside the row's bound of ${threshold} bytes, and no size condition is recorded"
  fi
  exit 0
fi

# `normative-purity`: a normative document states rules and their refusal paths, and holds no measurement, no
# command's output, no dated provenance and no `### Why …` heading — each occurrence is a condition with the
# file and the line that carries it, so a rationale cannot re-enter the layer it left.
if [ "$scope" = "normative-purity" ]; then
  findings=0
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    doc=$(basename "$path" .md)
    while IFS="$tab" read -r line token; do
      [ -n "$token" ] || continue
      fields=$(jq -nc --arg d "$doc" --arg t "$token" '{document: $d, token: $t}')
      fingerprint=$(sensors_fingerprint "$fields" "document token")
      sensors_emit "$fingerprint" "$line" violation "$fields" "" \
        "the normative document carries rationale or procedure" \
        "guidelines/${doc}.md line ${line} carries a ${token} the normative layer must not hold — move it into guidelines/${doc}.why.md or guidelines/${doc}.recipes.md"
      findings=$((findings + 1))
    done < <(purity_scan "$path")
  done < <(normative_documents)
  while IFS= read -r condition; do
    [ -n "$condition" ] || continue
    doc=$(jq -r '.condition.document // empty' <<< "$condition")
    token=$(jq -r '.condition.token // empty' <<< "$condition")
    [ -n "$doc" ] && [ -n "$token" ] || continue
    path="$root/guidelines/$doc.md"
    [ -f "$path" ] || continue
    purity_scan "$path" | awk -F"$tab" -v t="$token" '$2 == t { found = 1 } END { exit found ? 0 : 1 }' && continue
    fields=$(jq -nc --arg d "$doc" --arg tok "$token" '{document: $d, token: $tok}')
    fingerprint=$(sensors_fingerprint "$fields" "document token")
    sensors_emit "$fingerprint" "0" clear "$fields" "" \
      "the normative document holds no such text" \
      "guidelines/${doc}.md carries no ${token} any more"
    findings=$((findings + 1))
  done < <(jq -c '.[]? | select(.state == "OPEN")' <<< "${SENSORS_CONDITIONS:-[]}")
  if [ "$findings" -eq 0 ]; then
    fields='{"document":"none","token":"none"}'
    fingerprint=$(sensors_fingerprint "$fields" "document token")
    sensors_emit "$fingerprint" "none" clear "$fields" "" \
      "no normative document carries rationale or procedure" \
      "every normative document under guidelines/ is free of a measured figure, a tally, a dated provenance and a ### Why heading, and no condition is recorded"
  fi
  exit 0
fi

# -------------------------------------------------------------------------------------------- the routed sections

# 1. the routing table: from `## Documentation Map` to the next `## ` heading
awk '/^## Documentation Map$/{f=1;next} f && /^## /{exit} f{print}' "$entry_path" > "$tmp/region"
[ -s "$tmp/region" ] || { fail_check "no-routing-table"; exit 0; }

# 2. the documents: derived from the guideline directory, never a list inside this script
named=""
for path in "$root"/guidelines/*.md; do
  [ -f "$path" ] || continue
  named="$named $(basename "$path" .md)"
done
named=$(printf '%s\n' $named | sort)
[ -n "$named" ] || { fail_check "no-guideline-document"; exit 0; }
alternation=$(printf '%s\n' $named | paste -sd '|' -)

# 3. the pairs the table makes: document, section, and the quoted heading text where the table gives one — the
#    text is what tells two alike-numbered headings apart, exactly as the route check reads it.
sed -E "s/($alternation)\\.md/@@&@@/g" "$tmp/region" | awk -F'@@' -v pat="$alternation" '
  BEGIN { filere = "^(" pat ")\\.md$" }
  {
    file = ""
    for (i = 1; i <= NF; i++) {
      if ($i ~ filere) { file = substr($i, 1, length($i) - 3); continue }
      if (file == "") continue
      chunk = $i
      while ((p = index(chunk, "§")) > 0) {
        chunk = substr(chunk, p + 1)
        if (match(chunk, /^[0-9]+[a-z]?/) == 0) continue
        num = substr(chunk, 1, RLENGTH); chunk = substr(chunk, RLENGTH + 1)
        txt = ""
        sub(/^[ \t]+/, "", chunk)
        if (substr(chunk, 1, 1) == "\"") {
          rest = substr(chunk, 2); q = index(rest, "\"")
          if (q > 0) { txt = substr(rest, 1, q - 1); chunk = substr(rest, q + 1) }
        }
        print file "\t" num "\t" txt
      }
    }
  }' | sort -u > "$tmp/pairs"

# 4. every section of every guideline document: document, number, bytes, lines, heading text. The heading is
#    recognised outside a fenced code block only — a block that quotes a heading shows an example — while the
#    block's own lines still count into the section that holds it.
: > "$tmp/sections"
for path in "$root"/guidelines/*.md; do
  [ -f "$path" ] || continue
  LC_ALL=C awk -v file="$(basename "$path" .md)" '
    /^```/ { fence = !fence }
    !fence && /^## / {
      if (have) printf "%s\t%s\t%d\t%d\t%s\n", file, num, bytes, lines, text
      h = substr($0, 4); num = ""
      if (h ~ /^[0-9]+[a-z]?\./) { p = index(h, "."); num = substr(h, 1, p - 1); h = substr(h, p + 1); sub(/^ /, "", h) }
      text = h; bytes = length($0) + 1; lines = 1; have = 1; next
    }
    have { bytes += length($0) + 1; lines++ }
    END { if (have) printf "%s\t%s\t%d\t%d\t%s\n", file, num, bytes, lines, text }
  ' "$path" >> "$tmp/sections"
done

# 5. each routed pair with the cost of the heading it names. A pair whose number the document does not carry
#    gets no measurement rather than a guessed one — reporting a dangling route is the route check's job.
LC_ALL=C awk -F'\t' -v OFS="$tab" '
  FNR == NR { key = $1 SUBSEP $2; want[key] = 1; want_text[key] = $3; next }
  {
    key = $1 SUBSEP $2
    if (!(key in want)) next
    if (want_text[key] != "" && index($5, want_text[key])) { hit[key] = $3 "\t" $4 }
    else if (!(key in fallback)) fallback[key] = $3 "\t" $4
  }
  END {
    for (k in want) {
      split(k, parts, SUBSEP)
      v = (k in hit) ? hit[k] : (k in fallback ? fallback[k] : "")
      print parts[1], parts[2], v
    }
  }' "$tmp/pairs" "$tmp/sections" | sort > "$tmp/routed"

# The ranking is what makes "which routed section is largest" answerable without the row filing anything; it is
# the stderr half, and the findings below are the contract.
awk -F'\t' '$3 != ""' "$tmp/routed" | sort -t "$tab" -k3,3nr | head -n 5 |
  while IFS="$tab" read -r document section bytes lines text; do
    printf 'doc-context: %s.md §%s bytes=%s lines=%s\n' "$document" "$section" "$bytes" "$lines" >&2
  done

findings=0
while IFS="$tab" read -r document section bytes lines text; do
  [ -n "$document" ] || continue
  if [ -z "$bytes" ]; then
    printf 'doc-context: %s.md §%s — the document carries no such section, so it costs nothing to measure\n' \
      "$document" "$section" >&2
    continue
  fi
  fields=$(jq -nc --arg d "$document" --arg s "$section" '{document: $d, section: $s}')
  fingerprint=$(sensors_fingerprint "$fields" "document section")
  sensors_compare "$bytes" "$threshold" greater-than || continue
  sensors_emit "$fingerprint" "$bytes" violation "$fields" "" \
    "the routed section is past the size bound" \
    "guidelines/${document}.md §${section} is ${bytes} bytes in ${lines} lines, past the row's bound of ${threshold} bytes (~$((threshold / line_bytes)) lines at ${line_bytes} bytes/line)"
  findings=$((findings + 1))
done < "$tmp/routed"

# Every condition this row owns and does not report as present has cleared — the fingerprint comes from the
# issue's own body record, so the clearing finding names the identity the issue carries (design D4).
while IFS= read -r condition; do
  [ -n "$condition" ] || continue
  document=$(jq -r '.condition.document // empty' <<< "$condition")
  section=$(jq -r '.condition.section // empty' <<< "$condition")
  [ -n "$document" ] && [ -n "$section" ] || continue
  fields=$(jq -nc --arg d "$document" --arg s "$section" '{document: $d, section: $s}')
  fingerprint=$(sensors_fingerprint "$fields" "document section")
  current=$(awk -F'\t' -v d="$document" -v s="$section" '$1 == d && $2 == s { print $3 "\t" $4; exit }' "$tmp/routed")
  if [ -z "$current" ]; then
    # The table no longer routes that pair — a section a split replaced, a document no row names, or one the
    # tree dropped. It carries no byte count to report, and a number nobody measured is worse than none: the
    # condition clears on its own identity and says what happened instead.
    sensors_emit "$fingerprint" "outside-scope" clear "$fields" "" \
      "the routed section is inside the size bound" \
      "guidelines/${document}.md §${section} is not a section the routing table names, so it carries no cost and no condition"
    findings=$((findings + 1))
    continue
  fi
  current_bytes=${current%%$tab*}
  current_lines=${current#*$tab}
  sensors_compare "$current_bytes" "$threshold" greater-than && continue
  sensors_emit "$fingerprint" "$current_bytes" clear "$fields" "" \
    "the routed section is inside the size bound" \
    "guidelines/${document}.md §${section} is ${current_bytes} bytes in ${current_lines} lines, inside the row's bound of ${threshold} bytes"
  findings=$((findings + 1))
done < <(jq -c '.[]? | select(.state == "OPEN")' <<< "${SENSORS_CONDITIONS:-[]}")

# A table whose every routed section is inside the bound must say so: the runner reads a row that printed nothing
# as a failed check, and "no routed section is past the bound" is a verdict, not silence. The identity is the
# row's own, so the finding lands on no issue and closes nothing.
if [ "$findings" -eq 0 ]; then
  fields='{"document":"none","section":"none"}'
  fingerprint=$(sensors_fingerprint "$fields" "document section")
  sensors_emit "$fingerprint" "none" "clear" "$fields" "" \
    "no routed section is past the size bound" \
    "every section the routing table names is inside the row's bound of ${threshold} bytes, and no size condition is recorded"
fi
