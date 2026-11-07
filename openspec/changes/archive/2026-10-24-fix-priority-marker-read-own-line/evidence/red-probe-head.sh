#!/usr/bin/env bash
# red-probe-head.sh — the marker parser reads the grammar where prose quotes it (issue #293).
# Run from the change root: bash <this> tools/issues/lib.sh
set -uo pipefail
lib="${1:-tools/issues/lib.sh}"
# shellcheck source=/dev/null
source "$lib"

quoted='<!-- prio: band=p3 score=5 sev=3 reach=1 conf=2 occ=1 basis=abc at=2026-10-24 -->'
inline="the grammar is \`$quoted\` in prose."
fenced="the pass appends, on its own line:
\`\`\`
$quoted
\`\`\`
and that span is a quotation of the grammar, not a record."
real="a real record follows a quotation of the grammar:

\`$quoted\`

<!-- prio: band=p2 score=9 sev=4 reach=2 conf=1 occ=1 basis=0123456789abcdef at=2026-10-17 -->"
newmarker='<!-- prio: band=p2 score=9 sev=4 reach=2 conf=1 occ=1 basis=0123456789abcdef at=2026-10-24 -->'

bad=0
check() { # <what> <expected> <got>
  if [ "$2" = "$3" ]; then printf 'ok   %s\n' "$1"
  else printf 'RED  %s: expected [%s] got [%s]\n' "$1" "$2" "$3"; bad=$((bad+1)); fi
}

check "an inline quotation carries no marker" "" "$(issues_prio_marker "$inline")"
check "a fenced quotation carries no marker" "" "$(issues_prio_marker "$fenced")"
check "a real marker beside a quotation is the record" "p2" \
  "$(issues_prio_field "$(issues_prio_marker "$real")" band)"

# The shape's two anchors, one body line each: a prose line that *ends* at the close is excluded by the column-0
# clause, and a line that *starts* at the open but continues past the close is excluded by the end anchor. The
# fenced body above covers neither of them, which is how the first round shipped a clause nothing held (#293).
endsatclose="the record stands on the body's own last line, the pass writes: $quoted"
startsonly="$quoted is the grammar the pass writes, quoted here at column 0 and continued after the close."
check "a prose line that ends at the close carries no marker" "" "$(issues_prio_marker "$endsatclose")"
check "a line that starts at the open but does not close carries no marker" "" "$(issues_prio_marker "$startsonly")"

composed=$(issues_body_with_marker "$inline" "$newmarker")
check "the writer keeps the quoted prose" "1" "$(printf '%s\n' "$composed" | grep -cF "\`$quoted\`" || true)"
check "and the writer adds exactly one marker line" "1" \
  "$(printf '%s\n' "$composed" | grep -c '^<!-- prio:' || true)"

# The digest decides whether an item is re-interpreted, so the quotation is part of the claim: it stays in the
# digest, and editing it moves the digest.
edited=${inline/p3/p1}
check "the digest keeps the quotation" "different" \
  "$( [ "$(issues_body_hash "$inline")" != "$(issues_body_hash "$edited")" ] && echo different || echo same)"

printf '%s\n' "--- the parser's answer for the fenced body: '$(issues_prio_marker "$fenced")'"
[ "$bad" -eq 0 ] || { printf '%s\n' "RED: $bad of 8 checks failed"; exit 1; }
printf 'green: all 8 checks passed\n'
