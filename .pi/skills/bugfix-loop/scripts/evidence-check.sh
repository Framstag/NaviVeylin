#!/usr/bin/env bash
# evidence-check.sh — mechanical check of an OpenSpec change's evidence pointers.
#
# A bug-fix change's own review failed twice in the loop's first run on claims that no artifact carried: a test
# tally attributed to a /tmp log, and a file:line whose premise had moved. Both are greppable, so they are checked
# mechanically here instead of by a reviewer's patience. Run it in phase C/D before the gate and again before
# archive (`.pi/skills/bugfix-loop/SKILL.md`, "Evidence pointers").
#
# Checks (FAIL => exit 1, WARN => exit 0 with a note):
#   C1 §references   every `§N` in the change's markdown exists as a `## N.` section in TODO.md,
#                    unless the same line names an archived change ("archived")
#   C2 test tallies  every `tests="A" … failures="B"` pair quoted in the markdown is carried by a JUnit XML
#                    under <root>/app/build/test-results (a line saying "not retained" is exempt, by policy)
#   C3 /tmp pointers a cited `/tmp/...` path without "not retained" on its line is a dangling pointer
#   C4 mutations     `REVERT-CHECK MUTATION` must not survive anywhere under <root>/app/src
#   C5 file:line     every cited `path:NN` resolves (by path or by basename) and NN <= the file's line count
#
# Usage: evidence-check.sh <change-dir> [--todo TODO.md] [--root .] [--selftest]
set -euo pipefail

usage() { sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; }

SELFTEST_TMP=""
trap 'if [ -n "$SELFTEST_TMP" ]; then rm -rf "$SELFTEST_TMP"; fi' EXIT

run_checks() {
  local change="$1" root="$2" todo="$3"
  local fails=0 warns=0
  [ -d "$change" ] || { echo "FAIL change dir not found: $change"; return 1; }
  [ -f "$todo" ] || { echo "FAIL TODO file not found: $todo"; return 1; }

  local mds
  mds=$(find "$change" -name '*.md' -type f | sort)
  [ -n "$mds" ] || { echo "FAIL no markdown artifacts under $change"; return 1; }

  local mode="live"
  case "$change" in */openspec/changes/archive/*) mode="archived";; esac
  [ "$mode" = "archived" ] && echo "note archived change: C1/C2 are report-only (archived artifacts keep their historical §-citations and a later run may have overwritten their XML); C3/C4/C5 stay strict"

  # ---- C1: §references ------------------------------------------------------------------
  local missing=0 exempt=0 total=0
  while IFS= read -r line; do
    local refs n
    refs=$(grep -o '§[0-9]\+' <<<"$line" | sort -u || true)
    for n in $refs; do
      n="${n#§}"; total=$((total+1))
      if grep -qE "^## $n\." "$todo"; then continue; fi
      if grep -qi 'archiv' <<<"$line"; then exempt=$((exempt+1)); continue; fi
      if [ "$mode" = "archived" ]; then
        exempt=$((exempt+1))
        echo "DRIFT C1 archived artifact cites §$n, which the backlog no longer carries (history, not a defect)"
        continue
      fi
      echo "FAIL C1 dangling §$n in $change (no '## $n.' in $todo; the line names no archived change)"
      missing=$((missing+1))
    done
  done < <(grep -n '' $mds)
  [ "$missing" -eq 0 ] && echo "ok   C1 §references: $total checked, $exempt exempted as archived pointers" || fails=$((fails+missing))

  # ---- C2: test tallies -----------------------------------------------------------------
  # Two legitimate carriers: the build dir (live run) and the change's own evidence/ copy
  # (the form that travels into the archive).
  local xmlroot="$root/app/build/test-results"
  local evidence="$change/evidence"
  local checked=0 unparsed=0 exempt2=0 drift=0
  while IFS= read -r line; do
    grep -q 'tests[= ]' <<<"$line" || continue
    grep -q 'failures[= ]' <<<"$line" || continue
    if grep -qi 'not retained' <<<"$line"; then exempt2=$((exempt2+1)); continue; fi
    local t f
    t=$(grep -oE 'tests[= ]*"?[0-9]+' <<<"$line" | head -1 | grep -oE '[0-9]+$' || true)
    f=$(grep -oE 'failures[= ]*"?[0-9]+' <<<"$line" | head -1 | grep -oE '[0-9]+$' || true)
    if [ -z "$t" ] || [ -z "$f" ]; then unparsed=$((unparsed+1)); continue; fi
    checked=$((checked+1))
    if [ ! -d "$xmlroot" ] && [ ! -d "$evidence" ]; then
      if [ "$mode" = "archived" ]; then drift=$((drift+1)); continue; fi
      echo "FAIL C2 no XML under $xmlroot or $evidence — cannot confirm tests=$t failures=$f"; fails=$((fails+1)); continue
    fi
    if ! grep -rqE "tests=\"$t\"[^>]*failures=\"$f\"" "$xmlroot" "$evidence" 2>/dev/null; then
      if [ "$mode" = "archived" ]; then
        drift=$((drift+1))
        echo "DRIFT C2 tests=$t failures=$f is carried by no XML on disk today — a later run overwrote it (why the live rule is: copy the cited XML into <change>/evidence/ before archive)"
        continue
      fi
      echo "FAIL C2 quoted tally tests=$t failures=$f is carried by no XML under $xmlroot or $evidence"
      fails=$((fails+1))
    fi
  done < <(grep -n '' $mds)
  echo "ok   C2 test tallies: $checked pairs confirmed, $exempt2 exempted (not retained), $unparsed unparsable, $drift drift"

  # ---- C3: /tmp pointers (report-only: a /tmp path beside a quoted number is tolerable; a number with NO
  #      artifact is what C2 fails on) -------------------------------------------------------------
  local tmpfails=0
  while IFS= read -r line; do
    grep -q '/tmp/' <<<"$line" || continue
    grep -qi 'not retained' <<<"$line" && { exempt2=$((exempt2+1)); continue; }
    tmpfails=$((tmpfails+1))
    echo "DRIFT C3 /tmp path cited: $(cut -c1-130 <<<"$line")"
  done < <(grep -n '' $mds)
  if [ "$tmpfails" -eq 0 ]; then echo "ok   C3 /tmp pointers: none cited"; else echo "note C3: $tmpfails line(s) cite a /tmp path — re-runnable only until the next run; the number they carry must also be in this artifact or in a retained XML (C2)"; fi

  # ---- C4: leftover mutations -----------------------------------------------------------
  local markers
  markers=$(grep -rn 'REVERT-CHECK MUTATION' "$root/app/src" 2>/dev/null | head -5 || true)
  if [ -n "$markers" ]; then
    echo "FAIL C4 left-over REVERT-CHECK MUTATION:"; echo "$markers" | sed 's/^/     /'; fails=$((fails+1))
  else
    echo "ok   C4 mutations: no REVERT-CHECK MUTATION marker in $root/app/src"
  fi

  # ---- C5: file:line --------------------------------------------------------------------
  # Resolution order matters: a bare `tasks.md:99` inside a change means THAT change's file, so try the
  # change dir before a tree-wide basename search (which would find a shorter, unrelated `tasks.md`).
  resolve_ref() {
    local p="$1" base cand hits n
    base=$(basename "$p")
    for cand in "$root/$p" "$change/$p" "$change/$base"; do
      [ -f "$cand" ] && { printf '%s' "$cand"; return; }
    done
    hits=$(find "$root" -name "$base" -type f -not -path '*/build/*' -not -path '*/.git/*' 2>/dev/null || true)
    n=$(grep -c . <<<"$hits" || true)
    if [ "$n" -eq 1 ]; then printf '%s' "$hits"; return; fi
    printf ''   # unresolved or ambiguous -> report-only
  }
  local fl_checked=0 fl_unresolved=0
  while IFS= read -r line; do
    local ref p fname file n
    for ref in $(grep -oE '[A-Za-z0-9_./-]+\.(kt|java|kts|py|md|cpp|h|xml|gradle|sh|json):[0-9]+' <<<"$line" | sort -u || true); do
      p="${ref%:*}"; n="${ref##*:}"
      file=$(resolve_ref "$p")
      if [ -z "$file" ]; then fl_unresolved=$((fl_unresolved+1)); continue; fi
      fl_checked=$((fl_checked+1))
      if [ "$n" -gt "$(wc -l < "$file")" ]; then
        echo "FAIL C5 $ref points past the end of $file ($(wc -l < "$file") lines)"; fails=$((fails+1))
      fi
    done
  done < <(grep -n '' $mds)
  echo "ok   C5 file:line: $fl_checked resolved and in range, $fl_unresolved unresolved or ambiguous"

  echo "----"
  if [ "$fails" -gt 0 ]; then echo "evidence-check: FAIL ($fails)"; return 1; fi
  echo "evidence-check: PASS"; return 0
}

selftest() {
  local tmp; tmp="$(mktemp -d)"; SELFTEST_TMP="$tmp"   # no RETURN trap: it fires after the locals are gone (set -u)
  local fail=0
  ok() { if [ "$1" = "$2" ]; then echo "ok   $3"; else echo "FAIL $3: expected [$1] got [$2]"; fail=1; fi; }

  mkdir -p "$tmp/root/app/src/main/kotlin" "$tmp/root/app/build/test-results" "$tmp/change"
  printf 'line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\n' > "$tmp/root/app/src/main/kotlin/Foo.kt"
  printf '<?xml version="1.0"?>\n<testsuite name="x" tests="7" skipped="0" failures="0" errors="0" timestamp="2026-01-01T00:00:00Z"></testsuite>\n' \
    > "$tmp/root/app/build/test-results/TEST-x.xml"
  printf '# t\n\n## 12. a section\n**id:** 12 · **category:** ui · **class:** bug · **status:** open\n' > "$tmp/root/TODO.md"

  cat > "$tmp/change/proposal.md" <<'MD'
# Why
- Evidence: `TEST-x.xml` XML tests="7" failures="0" ts=2026-01-01T00:00:00Z
- Files: `app/src/main/kotlin/Foo.kt:9`
- Related: §12 was refuted by measurement
MD

  local out
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); local code=$?; set -e
  ok 0 "$code" "green fixture passes"
  case "$out" in *"evidence-check: PASS"*) echo "ok   green fixture verdict";; *) echo "FAIL green verdict: $out"; fail=1;; esac
  case "$out" in *"C2 test tallies: 1 pairs confirmed"*) echo "ok   tally confirmed against the XML";; *) echo "FAIL tally count: $out"; fail=1;; esac

  # C3 is report-only by design, so the strict half of this pair is C2: a number with no artifact fails there
  printf -- '- old evidence lived in /tmp/loop-1.log\n- Evidence: XML tests="999" failures="0"\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 1 "$code" "C2 red on an unbacked tally"
  case "$out" in *"DRIFT C3 /tmp path cited"*) echo "ok   C3 reports the /tmp pointer without failing";; *) echo "FAIL C3 drift line missing"; fail=1;; esac

  cat > "$tmp/change/proposal.md" <<'MD'
- Related: §999 does not exist
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 1 "$code" "C1 red on a dangling §"

  cat > "$tmp/change/proposal.md" <<'MD'
- Related: §999 but the change is archived as fix-thing
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 0 "$code" "C1 exempts an archived pointer"

  cat > "$tmp/change/proposal.md" <<'MD'
- Files: `app/src/main/kotlin/Foo.kt:99`
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 1 "$code" "C5 red on a line past EOF"

  cat > "$tmp/change/proposal.md" <<'MD'
- Files: `Foo.kt:9`
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 0 "$code" "C5 resolves a basename-only citation"

  printf '// REVERT-CHECK MUTATION (task 1)\n' >> "$tmp/root/app/src/main/kotlin/Foo.kt"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 1 "$code" "C4 red on a leftover mutation"

  printf 'line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\n' > "$tmp/root/app/src/main/kotlin/Foo.kt"
  cat > "$tmp/change/proposal.md" <<'MD'
- Evidence: XML tests="7" failures="0" — the red copy is not retained (overwritten by the gate run)
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/root/TODO.md"); code=$?; set -e
  ok 0 "$code" "a row labelled not retained is exempt by policy"

  [ "$fail" = 0 ] && echo "evidence-selftest: PASS" || { echo "evidence-selftest: FAIL"; return 1; }
}

main() {
  if [ "${1:-}" = "--selftest" ]; then selftest; return; fi
  local change="" root="." todo=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --root) root="${2:?}"; shift 2;;
      --todo) todo="${2:?}"; shift 2;;
      -h|--help) usage; return 0;;
      --*) echo "evidence-check: unknown flag $1" >&2; exit 2;;
      *) change="$1"; shift;;
    esac
  done
  [ -n "$change" ] || { usage; exit 2; }
  [ -n "$todo" ] || todo="$root/TODO.md"
  run_checks "$change" "$root" "$todo"
}

main "$@"
