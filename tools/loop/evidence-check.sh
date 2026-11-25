#!/usr/bin/env bash
# evidence-check.sh — mechanical check of an OpenSpec change's evidence pointers.
#
# A bug-fix change's own review failed twice in the loop's first run on claims that no artifact carried: a test
# tally attributed to a /tmp log, and a file:line whose premise had moved. Both are greppable, so they are checked
# mechanically here instead of by a reviewer's patience. Run it in phase C/D before the gate and again before
# archive (`tools/loop/prompts/worker.md`, phase F).
#
# It is the loop's tool, not a skill's: it lives in the tree the run clones, so a worker reaches it from the
# worktree without a seed step, and the paths it reads in *this* project are configuration, never literals here
# (`tools/loop/loop.conf` — the rule is `tools/check-portability.sh`'s, issue #255).
#
# Checks (FAIL => exit 1, WARN => exit 0 with a note):
#   C1 §references   every `§N` in the change's markdown resolves against the recorded `§ → issue` table
#                    (the migration's `import-evidence.md`), unless the same line names an archived change
#                    ("archived")
#   C2 test tallies  every `tests="A" … failures="B"` pair quoted in the markdown is carried by a JUnit XML
#                    under <root>/<LOOP_EVIDENCE_XML_DIR> or under <change>/evidence (a line saying "not
#                    retained" is exempt, by policy)
#   C3 /tmp pointers a cited `/tmp/...` path without "not retained" on its line is a dangling pointer
#   C4 mutations     `REVERT-CHECK MUTATION` must not survive anywhere under <root>/<LOOP_EVIDENCE_SCAN_PATHS>
#   C5 file:line     every cited `path:NN` resolves (by path or by basename) and NN <= the file's line count
#   C6 route         the report's `LOOP-ROUTE: <doc> §<id>` names a document under the project's guideline
#                    directory whose own `^## ` heading carries that id; a report with no route line is a WARN
#                    until the project's configuration makes it a FAIL (`LOOP_EVIDENCE_ROUTE`)
#
# Usage: evidence-check.sh <change-dir> [--table <file>] [--root .] [--conf <file>] [--scan <paths>]
#                           [--xml-dir <path>] [--guidelines <dir>] [--route <warn|fail>] [--selftest]
#   --scan / --xml-dir override the two values the project's configuration names, for a tree that is not this one.
set -euo pipefail

usage() { sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; }

# The recorded `§ → issue` table: the migration wrote it into its change directory, and archiving moved that
# directory under `openspec/changes/archive/`. Either location is the same record.
find_table() {
  local root="$1" candidate
  for candidate in "$root"/openspec/changes/*/import-evidence.md "$root"/openspec/changes/archive/*/import-evidence.md; do
    [ -f "$candidate" ] && { printf '%s\n' "$candidate"; return 0; }
  done
  return 1
}

# One key's value from a project configuration, read and never sourced — the same rule `loop_tmp_dir_for` follows
# (`tools/loop/lib.sh`): a check looks at a tree, it does not run that tree's configuration.
conf_value() { # <conf> <KEY>
  sed -n "s/^${2}=\"\\([^\"]*\\)\"[[:space:]]*$/\\1/p" "$1" | tail -1
}

SELFTEST_TMP=""
trap 'if [ -n "$SELFTEST_TMP" ]; then rm -rf "$SELFTEST_TMP"; fi' EXIT

run_checks() { # <change> <root> <table> <scan-paths> <xml-dir> <guideline-dir> <route-mode>
  local change="$1" root="$2" table="$3" scan_paths="$4" xml_dir="$5" guide_dir="$6" route_mode="$7"
  local fails=0 warns=0
  [ -d "$change" ] || { echo "FAIL change dir not found: $change"; return 1; }
  [ -f "$table" ] || { echo "FAIL reference table not found: $table"; return 1; }

  local mds
  mds=$(find "$change" -name '*.md' -type f | sort)
  [ -n "$mds" ] || { echo "FAIL no markdown artifacts under $change"; return 1; }

  local mode="live"
  case "$change" in */openspec/changes/archive/*) mode="archived";; esac
  [ "$mode" = "archived" ] && echo "note archived change: C1/C2 are report-only (archived artifacts keep their historical §-citations and a later run may have overwritten their XML); C3/C4/C5 stay strict"

  # ---- C1: §references ------------------------------------------------------------------
  local missing=0 exempt=0 total=0
  while IFS= read -r line; do
    # A route line's `§<id>` is the document's own section, not a backlog §: C6 resolves it, and C1 must not read
    # it against the migration's table — `§7e` carries `§7`, and the table has no row 7 (issue #974).
    case "$line" in *LOOP-ROUTE:*) continue;; esac
    local refs n
    refs=$(grep -o '§[0-9]\+' <<<"$line" | sort -u || true)
    for n in $refs; do
      n="${n#§}"; total=$((total+1))
      # the record is markdown: a row whose first column is exactly this id
      if grep -qE "^\|[[:space:]]*$n[[:space:]]*\|" "$table"; then continue; fi
      if grep -qi 'archiv' <<<"$line"; then exempt=$((exempt+1)); continue; fi
      if [ "$mode" = "archived" ]; then
        exempt=$((exempt+1))
        echo "DRIFT C1 archived artifact cites §$n, which the backlog no longer carries (history, not a defect)"
        continue
      fi
      echo "FAIL C1 dangling §$n in $change (no row for §$n in $table; the line names no archived change)"
      missing=$((missing+1))
    done
  done < <(grep -n '' $mds)
  [ "$missing" -eq 0 ] && echo "ok   C1 §references: $total checked, $exempt exempted as archived pointers" || fails=$((fails+missing))

  # ---- C2: test tallies -----------------------------------------------------------------
  # Two legitimate carriers: the build dir (live run) and the change's own evidence/ copy
  # (the form that travels into the archive). Both are named by the project's configuration, not here.
  local xmlroot="$root/$xml_dir"
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
  # The roots are the project's, read from its configuration: a marker is only ever planted in a source file the
  # falsification edited, and a path written here would be a project value in an engine (issue #255).
  local markers="" seen=0 rel hits
  for rel in $scan_paths; do
    [ -n "$rel" ] || continue
    if [ ! -d "$root/$rel" ]; then
      echo "note C4 scan path $rel does not exist under $root"
      continue
    fi
    seen=$((seen+1))
    hits=$(grep -rn 'REVERT-CHECK MUTATION' "$root/$rel" 2>/dev/null | head -5 || true)
    [ -n "$hits" ] && markers="$markers$hits"$'\n'
  done
  if [ "$seen" -eq 0 ]; then
    echo "FAIL C4 no scan path that exists — the mutation check scanned nothing"
    fails=$((fails+1))
  elif [ -n "$markers" ]; then
    echo "FAIL C4 left-over REVERT-CHECK MUTATION:"; printf '%s' "$markers" | sed '/^$/d; s/^/     /'; fails=$((fails+1))
  else
    echo "ok   C4 mutations: no REVERT-CHECK MUTATION marker under $seen configured path(s): $scan_paths"
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

  # ---- C6: the guideline route ------------------------------------------------------------
  # A report says which rule it was worked against in one line, `LOOP-ROUTE: <doc> §<id>`, and nothing resolved
  # that line until this check existed: a section number a document does not have was filed, reviewed and archived
  # as a route (issue #974). Resolution compares the cited id with the id the document's own `## ` headings carry
  # (`## 2a. Thing` is `§2a`), reading those headings and never the document. The directory is the project's
  # configuration and never a literal here, which is the rule `tools/check-portability.sh` enforces.
  local routes=0 resolved=0 r_unresolved=0 route_line doc id ids
  if [ "$mode" = "archived" ]; then
    echo "note C6 route: report-only on an archived change — the route is a rule of the live report"
  elif [ -z "$guide_dir" ] || [ ! -d "$root/$guide_dir" ]; then
    echo "note C6 route: no guideline directory at ${guide_dir:-<unnamed>} under $root — a route cannot resolve, so none is required"
  else
    while IFS= read -r route_line; do
      routes=$((routes+1))
      doc=$(sed -n 's/^.*LOOP-ROUTE:[[:space:]]*\([^[:space:]]*\).*$/\1/p' <<<"$route_line")
      id=$(sed -n 's/^.*§\([0-9][0-9a-z]*\).*$/\1/p' <<<"$route_line")
      if [ -z "$doc" ] || [ -z "$id" ]; then
        echo "FAIL C6 $(cut -c1-160 <<<"$route_line") — a route is 'LOOP-ROUTE: $guide_dir/<doc>.md §<id>'"
        fails=$((fails+1)); r_unresolved=$((r_unresolved+1)); continue
      fi
      case "$doc" in
        "$guide_dir"/*) ;;
        *) echo "FAIL C6 route names $doc, which is not under $guide_dir/"
           fails=$((fails+1)); r_unresolved=$((r_unresolved+1)); continue;;
      esac
      if [ ! -f "$root/$doc" ]; then
        echo "FAIL C6 route names a document that does not exist: $doc"
        fails=$((fails+1)); r_unresolved=$((r_unresolved+1)); continue
      fi
      # The two heading shapes this project writes: `## 2a. Agent iteration protocol` and `## §7a Running one
      # item` — the id is the leading number, and the shape's own separator follows it: a `.` and then whitespace,
      # or the heading's first space after the `§`. A heading that carries neither separator (`## 7a Running one
      # item`) and a dotted sub-number (`## 3.6 …`) carry no id (issue #974).
      ids=$(sed -n 's/^## §\([0-9][0-9a-z]*\)[[:space:]].*$/\1/p; s/^## \([0-9][0-9a-z]*\)\.[[:space:]].*$/\1/p' "$root/$doc")
      if grep -qxF "$id" <<<"$ids"; then
        resolved=$((resolved+1))
      else
        echo "FAIL C6 §$id does not resolve in $doc — its sections are: $(sed -n 's/^## §\([0-9][0-9a-z]*\)[[:space:]].*$/§\1/p; s/^## \([0-9][0-9a-z]*\)\.[[:space:]].*$/§\1/p' "$root/$doc" | tr '\n' ' ')"
        fails=$((fails+1)); r_unresolved=$((r_unresolved+1))
      fi
    # A **route line** is anchored: nothing but list/quote markers and space may precede it. A paragraph that
    # quotes the form — "the report block carries `LOOP-ROUTE: …`" — is prose about the line, not the line, and
    # reading it as one would refuse every change that documents its own convention (issue #974).
    done < <(grep -nE '^[[:space:]>*-]*LOOP-ROUTE:' $mds 2>/dev/null || true)
    if [ "$routes" -eq 0 ]; then
      if [ "$route_mode" = "fail" ]; then
        echo "FAIL C6 no LOOP-ROUTE: line — the change names no guideline section its work was routed by"
        fails=$((fails+1))
      else
        echo "WARN C6 no LOOP-ROUTE: line — the change names no guideline section its work was routed by (a WARN until this project sets LOOP_EVIDENCE_ROUTE to fail)"
        warns=$((warns+1))
      fi
    else
      echo "ok   C6 route: $resolved of $routes route line(s) resolved against their documents' own headings"
    fi
  fi

  echo "----"
  if [ "$fails" -gt 0 ]; then echo "evidence-check: FAIL ($fails)"; return 1; fi
  echo "evidence-check: PASS"; return 0
}

selftest() {
  local tmp; tmp="$(mktemp -d)"; SELFTEST_TMP="$tmp"   # no RETURN trap: it fires after the locals are gone (set -u)
  local fail=0
  ok() { if [ "$1" = "$2" ]; then echo "ok   $3"; else echo "FAIL $3: expected [$1] got [$2]"; fail=1; fi; }

  # The tree a check reads is the fixture's, and its two paths are this self-test's arguments — which is what a
  # project's configuration does in the real tree (`--scan`, `--xml-dir`), so no project value is written here.
  local scan="src" xmldir="build/test-results" guides="guidelines" route="warn"
  mkdir -p "$tmp/root/src/main/kotlin" "$tmp/root/$xmldir" "$tmp/change" "$tmp/root/$guides"
  # The route resolves against the document's own headings — `## 2a.` is `§2a`, and `§99` is not a section at all.
  printf '# Doc\n\n## 1. One\n\ntext\n\n## 2a. Two A\n\ntext\n\n## §2b Three\n\ntext\n\n## 3.6 Sub\n\ntext\n\n## 9 Nine\n\ntext\n' > "$tmp/root/$guides/Doc.md"
  printf 'line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\n' > "$tmp/root/src/main/kotlin/Foo.kt"
  printf '<?xml version="1.0"?>\n<testsuite name="x" tests="7" skipped="0" failures="0" errors="0" timestamp="2026-01-01T00:00:00Z"></testsuite>\n' \
    > "$tmp/root/$xmldir/TEST-x.xml"
  # the recorded table the migration wrote: §12 resolves, §999 does not
  printf '| § | issue |\n|---|---|\n| 12 | #7 |\n' > "$tmp/table.md"

  cat > "$tmp/change/proposal.md" <<'MD'
# Why
- Evidence: `TEST-x.xml` XML tests="7" failures="0" ts=2026-01-01T00:00:00Z
- Files: `src/main/kotlin/Foo.kt:9`
- Related: §12 was refuted by measurement
MD

  local out
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); local code=$?; set -e
  ok 0 "$code" "green fixture passes"
  case "$out" in *"evidence-check: PASS"*) echo "ok   green fixture verdict";; *) echo "FAIL green verdict: $out"; fail=1;; esac
  case "$out" in *"C2 test tallies: 1 pairs confirmed"*) echo "ok   tally confirmed against the XML";; *) echo "FAIL tally count: $out"; fail=1;; esac

  # C3 is report-only by design, so the strict half of this pair is C2: a number with no artifact fails there
  printf -- '- old evidence lived in /tmp/loop-1.log\n- Evidence: XML tests="999" failures="0"\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "C2 red on an unbacked tally"
  case "$out" in *"DRIFT C3 /tmp path cited"*) echo "ok   C3 reports the /tmp pointer without failing";; *) echo "FAIL C3 drift line missing"; fail=1;; esac

  cat > "$tmp/change/proposal.md" <<'MD'
- Related: §999 does not exist
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "C1 red on a dangling §"

  cat > "$tmp/change/proposal.md" <<'MD'
- Related: §999 but the change is archived as fix-thing
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "C1 exempts an archived pointer"

  cat > "$tmp/change/proposal.md" <<'MD'
- Files: `src/main/kotlin/Foo.kt:99`
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "C5 red on a line past EOF"

  cat > "$tmp/change/proposal.md" <<'MD'
- Files: `Foo.kt:9`
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "C5 resolves a basename-only citation"
  # the green half of C4's pair, before a marker is planted: it names the configured path it scanned
  case "$out" in *"no REVERT-CHECK MUTATION marker under 1 configured path(s): src"*) echo "ok   C4 names the configured path it scanned";; *) echo "FAIL C4 path line: $out"; fail=1;; esac

  printf '// REVERT-CHECK MUTATION (task 1)\n' >> "$tmp/root/src/main/kotlin/Foo.kt"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "C4 red on a leftover mutation"

  # a scan path that does not exist is a check that inspected nothing, and says so instead of passing
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "no-such-dir" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "C4 red when no configured scan path exists"

  printf 'line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\n' > "$tmp/root/src/main/kotlin/Foo.kt"
  cat > "$tmp/change/proposal.md" <<'MD'
- Evidence: XML tests="7" failures="0" — the red copy is not retained (overwritten by the gate run)
MD
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "a row labelled not retained is exempt by policy"

  # C6: a route resolves against the document's own headings, and one that does not is the false pointer the
  # check exists for (issue #974). The green arm first, then the § the document does not have, then the WARN arm a
  # report written before the rule landed falls into.
  printf '# Doc\n- LOOP-ROUTE: guidelines/Doc.md §2a\n- LOOP-ROUTE: guidelines/Doc.md §2b\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "a route resolves against the document's own heading"

  printf '# Doc\n- LOOP-ROUTE: guidelines/Doc.md §99\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "a § the document does not have is a false pointer"
  sections=$(sed -n 's/.*its sections are: \(.*\)$/\1/p' <<<"$out")
  ok "§1 §2a §2b " "$sections" "the refusal names the document's section list"

  # a dotted sub-number is neither of the two shapes this project writes, so it carries no id: a route to `§3`
  # against a document whose only `3`-bearing heading is `## 3.6 Sub` is the false pointer C6 exists to refuse.
  printf '# Doc\n- LOOP-ROUTE: guidelines/Doc.md §3\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "a dotted sub-number heading carries no id"
  sections=$(sed -n 's/.*its sections are: \(.*\)$/\1/p' <<<"$out")
  ok "§1 §2a §2b " "$sections" "the dotted sub-number is not in the document's section list"

  # a heading that carries neither separator is neither shape either, so it carries no id: `## 9 Nine` is not §9.
  printf '# Doc\n- LOOP-ROUTE: guidelines/Doc.md §9\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 1 "$code" "a heading with neither shape separator carries no id"

  printf '# Doc\n- no route line here\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "a report with no route line warns rather than failing"
  case "$out" in *"WARN C6"*) echo "ok   the missing route is a WARN";; *) echo "FAIL C6 warn arm: $out"; fail=1;; esac

  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "fail"); code=$?; set -e
  ok 1 "$code" "and the same report fails once the project reads the route as required"

  # A paragraph that quotes the form is prose about the line and not the line: reading it as one would refuse every
  # change that documents its own convention, which is the shape the first draft of this check had.
  printf '# Doc\nThe report block carries `LOOP-ROUTE: guidelines/Doc.md §99` and Phase A says where it goes\n' > "$tmp/change/proposal.md"
  set +e; out=$(run_checks "$tmp/change" "$tmp/root" "$tmp/table.md" "$scan" "$xmldir" "$guides" "$route"); code=$?; set -e
  ok 0 "$code" "a paragraph quoting the form is not a route line"

  [ "$fail" = 0 ] && echo "evidence-selftest: PASS" || { echo "evidence-selftest: FAIL"; return 1; }
}

main() {
  if [ "${1:-}" = "--selftest" ]; then selftest; return; fi
  local change="" root="." table="" conf="" scan="" xml_dir="" guide_dir="" route=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --root) root="${2:?}"; shift 2;;
      --table) table="${2:?}"; shift 2;;
      --conf) conf="${2:?}"; shift 2;;
      --scan) scan="${2:?}"; shift 2;;
      --xml-dir) xml_dir="${2:?}"; shift 2;;
      --guidelines) guide_dir="${2:?}"; shift 2;;
      --route) route="${2:?}"; shift 2;;
      -h|--help) usage; return 0;;
      --*) echo "evidence-check: unknown flag $1" >&2; exit 2;;
      *) change="$1"; shift;;
    esac
  done
  [ -n "$change" ] || { usage; exit 2; }
  [ -n "$table" ] || table=$(find_table "$root") || table="$root/openspec/changes/import-evidence.md"

  # The two paths this check reads in the project are the project's configuration's, never this file's: a flag
  # overrides them for a tree that is not the one this tool ships in.
  local conf_file="${conf:-$root/tools/loop/loop.conf}"
  if [ -z "$scan" ] || [ -z "$xml_dir" ]; then
    [ -f "$conf_file" ] || { echo "evidence-check: no configuration at $conf_file — pass --conf, or --scan and --xml-dir" >&2; exit 2; }
    [ -n "$scan" ] || scan=$(conf_value "$conf_file" LOOP_EVIDENCE_SCAN_PATHS)
    [ -n "$xml_dir" ] || xml_dir=$(conf_value "$conf_file" LOOP_EVIDENCE_XML_DIR)
  fi

  # The route's two values are the project's as well: the directory its guideline documents live in, and whether a
  # report that carries no route line is a WARN (the run after the rule landed, whose items were written before it)
  # or a FAIL (every run after). A project that names neither owes no route, and C6 says so instead of passing
  # silently — a path the configuration does not name is reported, never read as clean.
  [ -n "$guide_dir" ] || guide_dir=$(conf_value "$conf_file" LOOP_GUIDELINE_DIR 2>/dev/null || true)
  [ -n "$route" ] || route=$(conf_value "$conf_file" LOOP_EVIDENCE_ROUTE 2>/dev/null || true)
  [ -n "$route" ] || route="warn"
  case "$route" in
    warn|fail) ;;
    *) echo "evidence-check: LOOP_EVIDENCE_ROUTE is '$route' — it takes 'warn' or 'fail'" >&2; exit 2;;
  esac
  [ -n "$scan" ] || { echo "evidence-check: $conf_file names no LOOP_EVIDENCE_SCAN_PATHS — the mutation scan would inspect nothing" >&2; exit 2; }
  [ -n "$xml_dir" ] || { echo "evidence-check: $conf_file names no LOOP_EVIDENCE_XML_DIR — the tally check would find no XML" >&2; exit 2; }

  run_checks "$change" "$root" "$table" "$scan" "$xml_dir" "$guide_dir" "$route"
}

main "$@"
