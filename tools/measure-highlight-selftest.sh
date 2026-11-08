#!/usr/bin/env bash
# Device-free self-test of tools/measure-highlight.py — the detector that turns a claimed
# screenshot finding into a bbox / band / verdict / exit code.
#
# Runs on synthetic images it builds itself (python3 stdlib `zlib` + `struct`, no ImageMagick,
# no device, no emulator, no git) — the `tools/declared-cases-selftest.sh` precedent. The
# fixtures live in a temp dir, so the repository carries no binary fixture.
#
#   bash tools/measure-highlight-selftest.sh
#
# Every case also states the same-moment diagnostics the detector requires (`--state`): one
# `MapCanvasVM: segment focus:` line per case, and the four refusal cases that break that
# requirement in one way each.
#
# Exit code 0 = every case passed; 1 = at least one case failed (each failure prints what the
# tool measured instead).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL="$HERE/measure-highlight.py"
[ -f "$TOOL" ] || { echo "selftest: no tools/measure-highlight.py next to $0" >&2; exit 1; }

WORK="$(mktemp -d)" || exit 1
trap 'rm -rf "$WORK"' EXIT

python3 - "$WORK" <<'PY'
import struct, sys, zlib
from pathlib import Path

out = Path(sys.argv[1])
WHITE = (255, 255, 255)
CASING = (224, 247, 250)  # the dark-mode casing colour `#E0F7FA` (RouteSegmentHighlightOverlay)

def write_png(path, width, height, rows):
    """rows: {y: [(x_from, x_to), ...]} — inclusive dark-casing spans, rest white."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        spans = rows.get(y, [])
        line = bytearray(WHITE * width)
        for x in range(width):
            if any(a <= x <= b for a, b in spans):
                line[x * 3:x * 3 + 3] = bytes(CASING)
        raw += line
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + chunk(b"IEND", b""))

# 41 px run (x 10..50) followed by a 5 px one (x 100..104): the longest run is NOT the last.
write_png(out / "longest-run-not-last.png", 200, 20, {5: [(10, 50), (100, 104)]})
# The same two runs in the opposite order — the longest run IS the last one.
write_png(out / "longest-run-is-last.png", 200, 20, {5: [(10, 14), (100, 140)]})
# Two rows, each with its long run on a different side; the bbox must span both long runs
# (x 10..50 on y=5, x 60..100 on y=6).
write_png(out / "longest-run-per-row.png", 200, 20, {5: [(10, 50), (100, 104)], 6: [(10, 14), (60, 100)]})
# One row, one run — the ordinary case.
write_png(out / "single-run.png", 200, 20, {5: [(10, 50)]})
# A single 5 px dot: below MIN_RUN_PX, so it must never qualify a row.
write_png(out / "short-only.png", 200, 20, {5: [(10, 14)]})
# Two 5 px runs far apart in one row: still below the floor — runs are not summed, and the
# row's overall extent is not the rule either.
write_png(out / "two-short-runs.png", 200, 20, {5: [(10, 14), (100, 104)]})
# A long run near the bottom, behind a card whose top is y=15.
write_png(out / "behind-card.png", 200, 20, {18: [(10, 50)]})
# Issue #90's frame: two casing-coloured glyph rows 10 rows apart, 9 px each — 18 px in total,
# which is the false positive the same-moment state now refuses.
write_png(out / "glyph-pair.png", 200, 20, {5: [(10, 18)], 15: [(10, 18)]})
PY

# The same-moment diagnostics: one `segment focus:` line as the app logs it
# (`MapCanvasViewModelRoute.kt:463-468`), coordinate-free, for a card top of $1 px.
focus_line() {
  printf '%s\n' "11-08 11:34:11.402  1999  1999 D MapCanvasVM: segment focus: range=0..2 mag=15.0 covered=$((20 - $1)) band=[0,$1] margin=2 bboxPx=[10,50,5,5] inside=true"
}
focus_line 15 > "$WORK/state.log"
focus_line 16 > "$WORK/state-other-band.log"
focus_line 20 > "$WORK/state-no-card.log"
printf '%s\n' \
  "11-08 11:34:11.100  1999  1999 D MapCanvasVM: route published" \
  "11-08 11:34:11.200  1999  1999 D MapCanvasVM: render frame 41" > "$WORK/state-no-focus.log"

PASS=0
FAIL=0

# check <case> <image> <expected exit> <expected-fragments…> [-- <extra tool args…>]
check() {
  local case="$1" image="$2" expected_code="$3"; shift 3
  local -a expected=() extra=()
  local separator=0 argument
  for argument in "$@"; do
    if [ "$argument" = "--" ]; then separator=1; continue; fi
    if [ "$separator" = 1 ]; then extra+=("$argument"); else expected+=("$argument"); fi
  done
  local out code fragment
  out="$(python3 "$TOOL" "$WORK/$image" --json ${extra[@]+"${extra[@]}"} 2>&1)"
  code=$?
  printf 'case %s: %s\n     exit=%d\n' "$case" "$out" "$code"
  for fragment in ${expected[@]+"${expected[@]}"}; do
    case "$out" in
      *"$fragment"*) ;;
      *) printf 'FAIL %s — measured output does not carry %s: %s\n' "$case" "$fragment" "$out"
         FAIL=$((FAIL + 1)); return ;;
    esac
  done
  if [ "$code" != "$expected_code" ]; then
    printf 'FAIL %s — exit %s, expected %s\n' "$case" "$code" "$expected_code"
    FAIL=$((FAIL + 1)); return
  fi
  PASS=$((PASS + 1))
  printf 'PASS %s\n' "$case"
}

# Every case below states the same moment it measures: the diagnostics carry an analysed-segment
# line whose band is the band the shot is measured against (`--state`, band top 15).
# A row whose longest run is not its rightmost one is still a highlight row, and the bbox is
# the longest run's extent.
check longest-run-not-last longest-run-not-last.png 0 '"bbox": [10, 50, 5, 5]' '"highlight_px": 41' '"inside": true' -- --state "$WORK/state.log" --band-bottom 15
# The mirror order keeps working (green on HEAD too — the fix must not trade one order for the other).
check longest-run-is-last longest-run-is-last.png 0 '"bbox": [100, 140, 5, 5]' '"highlight_px": 41' '"inside": true' -- --state "$WORK/state.log" --band-bottom 15
# Each qualifying row contributes its own longest run to the bbox: 41 + 41 px over y 5..6.
check longest-run-per-row longest-run-per-row.png 0 '"bbox": [10, 100, 5, 6]' '"highlight_px": 82' '"inside": true' -- --state "$WORK/state.log" --band-bottom 15
# Unchanged behaviour: one run per row.
check single-run single-run.png 0 '"bbox": [10, 50, 5, 5]' '"highlight_px": 41' '"inside": true' -- --state "$WORK/state.log" --band-bottom 15
# A run below MIN_RUN_PX never qualifies a row, whichever position it holds.
check short-only short-only.png 2 '"highlight": null' -- --state "$WORK/state.log" --band-bottom 15
# …and separate short runs neither sum to the floor nor widen the row's span.
check two-short-runs two-short-runs.png 2 '"highlight": null' -- --state "$WORK/state.log" --band-bottom 15
# The band verdict is unchanged: a long run behind the card is clipped.
check behind-card behind-card.png 1 '"clipped": ["bottom"]' '"inside": false' -- --state "$WORK/state.log" --band-bottom 15 --margin 2

# The same-moment state: a frame whose diagnostics do not establish an analysed segment gets a
# refusal, never a verdict (issue #90 — 18 px of parking-glyph colour reported `inside`).
check no-state glyph-pair.png 2 '"highlight": null' '"refused": "no-state"'
check no-focus-line glyph-pair.png 2 '"highlight": null' '"refused": "no-focus-line"' -- --state "$WORK/state-no-focus.log"
# A band that ends at the canvas bottom measured no card, so the frame is not the assumed state.
check no-card glyph-pair.png 2 '"highlight": null' '"refused": "no-card"' -- --state "$WORK/state-no-card.log"
# The shot and the diagnostics are not one moment: their bands disagree.
check band-disagrees glyph-pair.png 2 '"highlight": null' '"refused": "band-disagrees"' -- --state "$WORK/state-other-band.log" --band-bottom 15

printf 'measure-highlight selftest: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
