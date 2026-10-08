#!/bin/bash
# Self-test of tools/measure-highlight.py: draws the highlight's casing colour with
# ImageMagick into synthetic screenshots — one fully inside the free band, one behind
# the card — and checks that the script's verdict matches. Runs without a device, so
# the detector's behaviour is verifiable before it is used as evidence.
set -u
cd "$(dirname "$0")/../../.."
WORK=.tmp-measure-selftest
mkdir -p "$WORK"
FAIL=0

# Canvas 1080x2400, card occupying the bottom 236 px (band [0,2164]).
magick -size 1080x2400 xc:white \
  -fill '#00454F' -draw 'rectangle 300,900 360,1100' "$WORK/inside.png"
magick -size 1080x2400 xc:white \
  -fill '#00454F' -draw 'rectangle 300,2100 360,2300' "$WORK/behind-card.png"
# A dense run must be required: a single label-sized dot is not a highlight.
magick -size 1080x2400 xc:white \
  -fill '#00454F' -draw 'rectangle 300,900 304,904' "$WORK/dot.png"

check() {
  local name="$1" expect="$2" expected_code="$3" out code
  out=$(python3 tools/measure-highlight.py "$WORK/$name.png" --band-bottom 2164 --json)
  code=$?
  case "$out" in *"\"inside\": $expect"*) echo "ok   $name -> inside=$expect";;
    *) echo "FAIL $name -> expected inside=$expect, got: $out"; FAIL=1;; esac
  [ "$code" = "$expected_code" ] || { echo "FAIL $name -> exit $code, expected $expected_code"; FAIL=1; }
}

check inside true 0
check behind-card false 1
# No dense run anywhere: no highlight found, exit 2.
out=$(python3 tools/measure-highlight.py "$WORK/dot.png" --band-bottom 2164 --json); code=$?
case "$out" in *'"highlight": null'*) echo "ok   dot -> no highlight";;
  *) echo "FAIL dot -> expected no highlight, got: $out"; FAIL=1;; esac
[ "$code" = 2 ] || { echo "FAIL dot -> exit $code, expected 2"; FAIL=1; }

rm -rf "$WORK"
echo "selftest FAIL=$FAIL"
exit $FAIL
