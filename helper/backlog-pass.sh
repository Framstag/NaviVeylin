#!/usr/bin/env bash
# helper/backlog-pass.sh — one command that closes the tracker's metadata gap.
#
# Runs the two halves the tooling defines, on the items that are actually due:
#   1. classify.sh   — every item with no `class/*` label gets a kind and an area
#   2. prioritise.sh — every item whose recorded `basis=` no longer matches its labels (or that has no band)
#
# Selection, band arithmetic, the label and marker writes, the vocabulary gate and the status caps stay the
# tooling's: this script only supplies the components (`prioritise.sh` computes the band and refuses an
# out-of-range value), and it reads the queues from the tooling's own `--json`, never from a plan file. An
# item the tooling reports as `skipped` is not touched — no sampling, no re-scoring of an unchanged basis.
#
# The component tables are heuristic and deterministic, not a model reading. They live here, in `helper/`,
# on purpose: project keywords and area names are project values, and `tools/` may name none
# (`tools/check-portability.sh`, `lib#noProjectValueInTheEngine`). `agent/eligible` is never written —
# priority is not authorisation.
#
# Usage: helper/backlog-pass.sh [--max-rounds N]
#   env: MAX_ROUNDS (default 5)
# Exit: 0 done, 1 a tool invocation failed, 2 usage.

set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/.." && pwd)
cd "$root" || { echo "backlog-pass: cannot enter $root" >&2; exit 2; }

max_rounds=${MAX_ROUNDS:-5}
while [ $# -gt 0 ]; do
  case "$1" in
    --max-rounds) max_rounds=${2:-5}; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "backlog-pass: unknown argument $1" >&2; exit 2 ;;
  esac
done

tmp=$(mktemp -d) || exit 2
trap 'rm -rf "$tmp"' EXIT
log="$tmp/writes.log"

# ── the tables ───────────────────────────────────────────────────────────────────────────────────────
# One jq program, used for both halves: `kind`/`area` classify an item that carries no labels,
# `sev`/`reach`/`conf` supply the three components. The tooling caps confidence by status itself.

read -r -d '' JQ <<'JQEOF'
def kind:
  if   test("capability absent|no ui|nothing is wired|not wired|is absent"; "i") then "feature"
  elif test("crash|anr|fails|failure|broken|wrong|stale|contradict|regression|leak|does not|never |cannot|missing"; "i") then "bug"
  else "improvement" end;
def area:
  if   test("stylesheet|\\.oss"; "i") then "stylesheets"
  elif test("jni|native|submodule|c\\+\\+"; "i") then "native-jni"
  elif test("search|query|index"; "i") then "search"
  elif test("favorite|favourite|star"; "i") then "favorites"
  elif test("rout|navigation|instruction|turn|eta"; "i") then "route-and-navigation"
  elif test("car |aaos|android auto|template|host |rail|widget"; "i") then "car"
  elif test("render|zoom|tile|follow|bitmap|cairo|frame"; "i") then "map-rendering"
  elif test("gps|location|bearing"; "i") then "location"
  elif test("persist|settings|viewport|storage"; "i") then "persistence"
  elif test("database|download|basemap|osm"; "i") then "data-and-maps"
  elif test("compose|sheet|pill|font|palette|dark|screen"; "i") then "ui"
  elif test("gradle|build|ci |test|loop|tool|harness|script|selftest"; "i") then "build-and-harness"
  elif test("verif|device|capture|measure"; "i") then "verification"
  else "specs-and-process" end;
def sev($cls; $st; $t):
  if $st == "retired" then 1
  elif $cls == "bug" then
    (if ($t | test("crash|anr|process death|data loss|corrupt|outofmemory|stackoverflow"; "i")) then 5
     elif ($t | test("silent|wrong|no result|not drawn|unreachable|clipped|does not|never |cannot|empty|missing"; "i")) then 4
     elif ($t | test("cosmetic|latency|memory|noise|slow|perf"; "i")) then 2
     else 3 end)
  elif $cls == "improvement" then
    (if ($t | test("gate|red|silent|crash|fails"; "i")) then 3
     elif ($t | test("hygiene|docs|stale|tombstone|closed|merged|retired|decision"; "i")) then 1
     else 2 end)
  else 2 end;
def reach($cat; $st):
  if $st == "retired" then 1
  elif (["build-and-harness","specs-and-process","verification"] | index($cat)) then 1
  elif (["car","ui","search","favorites","stylesheets","native-jni"] | index($cat)) then 2
  else 3 end;
def conf($st):
  if ($st == "fixed-by" or $st == "in-flight" or $st == "retired") then 3
  elif $st == "unverified" then 1 else 2 end;
def lab($ns):
  ([.[] | select(startswith($ns + "/"))] | .[0] // "" | ltrimstr($ns + "/"));
JQEOF

# `list.sh --json` gives every issue; the classification queue is the label-less subset. Proposing for an
# item the pass did not select is harmless: classify.sh skips a proposal outside its own selected set.
classify_proposals() {
  bash tools/issues/list.sh --json 2>/dev/null |
    jq -c "$JQ"'
      [ .[] | select(([.labels[].name | select(startswith("class/"))] | length) == 0)
        | .title as $t | ($t | kind) as $k | ($t | area) as $a
        | {issue: .number, class: $k, category: $a, rationale: ("heuristic " + $k + "/" + $a + ": " + $t[0:140])} ]'
}

# `prioritise.sh --json` gives every record with `due`, the recorded labels and the title; the components are
# derived from those, and only the due records are emitted.
priority_scores() {
  jq -c "$JQ"'
    [ .records[] | select(.due)
      | .title as $t
      | (.labels | lab("class")) as $cls
      | (.labels | lab("category")) as $cat
      | (.labels | lab("status")) as $st
      | ($t | sev($cls; $st; $t)) as $sev
      | {issue: .issue, severity: $sev,
         reach: reach($cat; $st), confidence: conf($st),
         rationale: ("heuristic " + $cls + "/" + $cat + " (status/" + $st + "): " + $t[0:140])} ]'
}

count() { sed -n "s/.*[ ]$1=\([0-9]*\).*/\1/p" | head -1; }
report() { printf '%s\n' "$1" | grep -E '^metadata:' || printf '%s\n' "$1"; }

# ── the passes ───────────────────────────────────────────────────────────────────────────────────────
# Both queue bounds are lifted: the scorer is deterministic and instant, so a bounded batch would only cost
# extra rounds. A tool refusal still stops the run.

round=0
while :; do
  round=$((round + 1))
  [ "$round" -le "$max_rounds" ] || { echo "backlog-pass: round cap ($max_rounds) reached" >&2; break; }

  classify_proposals > "$tmp/proposals.json"
  if [ "$(jq 'length' < "$tmp/proposals.json")" -gt 0 ]; then
    out=$(bash tools/issues/classify.sh --proposals "$tmp/proposals.json" --write \
            --max-classify 1000000 --write-log "$log" 2>&1) || { printf '%s\n' "$out" >&2; exit 1; }
    report "$out"
  fi

  plan=$(bash tools/issues/prioritise.sh --json --max-rescore 1000000 2>/dev/null) || exit 1
  [ "$(jq '[.records[] | select(.due)] | length' <<<"$plan")" -gt 0 ] || break
  priority_scores <<<"$plan" > "$tmp/scores.json"
  out=$(bash tools/issues/prioritise.sh --scores "$tmp/scores.json" --write \
          --max-rescore 1000000 --write-log "$log" 2>&1) || { printf '%s\n' "$out" >&2; exit 1; }
  report "$out"
  [ "$(printf '%s\n' "$out" | count waiting)" -gt 0 ] 2>/dev/null || break
done

# ── the report ───────────────────────────────────────────────────────────────────────────────────────
echo "backlog-pass: rounds=$round writes=$(grep -c . "$log" 2>/dev/null || echo 0)"
bash tools/issues/check-metadata.sh --live 2>&1 | tail -2
for band in p0 p1 p2 p3; do
  n=$(bash tools/issues/list.sh --prio "$band" --json 2>/dev/null | jq 'length' 2>/dev/null)
  echo "  $band=${n:-?}"
done
exit 0
