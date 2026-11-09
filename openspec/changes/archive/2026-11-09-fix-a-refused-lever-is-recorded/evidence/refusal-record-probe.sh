#!/usr/bin/env bash
# refusal-record-probe.sh — does the section that owns the item context bound record the lever that was refused?
#
# Item #833: raising `LOOP_ITEM_CONTEXT_MAX` above 200 000 was measured against the cuts it would remove and
# refused, and the refusal was recorded on the tracker only. This probe reads the body of `guidelines/Backlog.recipes.md`
# §7b — from its heading to the next `## ` heading — and requires the refusal, its issue and the three recorded
# contexts in it. It reads text only, writes one JUnit-shaped tally, runs no build and touches no document.
#
# Usage: refusal-record-probe.sh [--root <dir>] [--out <file>]
# Exit:  0 the section records the refusal; 1 it does not (the tally says which token is absent); 2 usage.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=""
out=""
while [ $# -gt 0 ]; do
  case "$1" in
    --root) [ $# -ge 2 ] || { echo "refusal-record-probe: --root needs a value" >&2; exit 2; }; root="$2"; shift 2 ;;
    --out)  [ $# -ge 2 ] || { echo "refusal-record-probe: --out needs a value" >&2; exit 2; }; out="$2"; shift 2 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "refusal-record-probe: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

# The default root is the tree that holds `guidelines/`, found by walking up, not a fixed number of levels: an
# archived change sits one directory deeper than an open one, so a fixed depth resolves to `openspec/` once the
# change is archived and the section then reads as missing. A `--root` still overrides it.
if [ -z "$root" ]; then
  root="$here"
  while [ "$root" != "/" ] && [ ! -d "$root/guidelines" ]; do root=$(dirname "$root"); done
  [ -d "$root/guidelines" ] || {
    echo "refusal-record-probe: no directory above $here holds guidelines/ — pass --root" >&2
    exit 2
  }
fi

section="guidelines/Backlog.recipes.md"
heading='## §7b'
case_name='guidelines/Backlog.recipes.md §7b / the refused lever'

block=$(awk -v heading="$heading" '
  index($0, heading) && !seen { seen = 1; next }
  seen { if ($0 ~ /^## /) exit; print }' "$root/$section")

tests=1
failures=0
missing=""
for token in '#833' 'refused' '200 427' '200 733' '201 981'; do
  # `case`, not `printf | grep -q`: under `pipefail` the early-exiting grep leaves printf writing to a closed
  # pipe, and the token reads as absent or present depending on how fast that happens (measured on this probe's
  # first run, which reported `refused` missing from a section that carries it four times).
  case "$block" in
    *"$token"*) ;;
    *) missing="$missing '$token'" ;;
  esac
done

if [ -z "$block" ]; then
  failures=1
  detail='the section names no line in the file'
elif [ -n "$missing" ]; then
  failures=1
  detail="the section that owns the bound records no refused lever: missing$missing"
else
  detail=""
fi

if [ "$failures" -eq 0 ]; then
  cases="<testcase classname=\"refusal-record\" name=\"$case_name\"/>"
else
  cases="<testcase classname=\"refusal-record\" name=\"$case_name\"><failure message=\"$(printf '%s' "$detail" | sed 's/[&<>\"]/./g')\"/></testcase>"
fi

xml=$(printf '<testsuite name="refusal-record" tests="%s" failures="%s" errors="0"><properties><property name="root" value="%s"/></properties>%s</testsuite>' \
  "$tests" "$failures" "$root" "$cases")

if [ -n "$out" ]; then printf '%s\n' "$xml" > "$out"; else printf '%s\n' "$xml"; fi
printf 'refusal-record-probe: tests=%s failures=%s%s\n' "$tests" "$failures" \
  "$( [ "$failures" -eq 0 ] || printf ' — %s' "$detail")"
[ "$failures" -eq 0 ]
