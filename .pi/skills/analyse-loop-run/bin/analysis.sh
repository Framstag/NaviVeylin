#!/usr/bin/env bash
# analysis.sh — the mechanical half of the `analyse-loop-run` pass.
#
# Why this exists: one pass re-typed the same five blocks of `jq` every time — which run is the subject, its
# per-item numbers, its spend, one item's evidence, the archive's rates — and the skill's own copy of the first
# and the second did not run in this harness (a `$((b-a))` over two `$(date …)` values is refused by the shell
# allowlist) and counted the wrong children (`test("^worker")` also matches `worker-gate` and `worker-tail`, so
# one item was printed as three worker children). The judgement the pass owes — what was good, what was bad, and
# the suggestion table — is not in here and cannot be.
#
# Usage:
#   analysis.sh select   [--root DIR]
#   analysis.sh numbers  --state FILE
#   analysis.sh spend    --state FILE [--since YYYY-MM-DD]
#   analysis.sh item     N [--root DIR]
#   analysis.sh rates    [--n N] [--root DIR]
#
# Reads only: no build, no gate, no network, no tracker write. `python3` is used for `tools/loop-spend.py` alone,
# which is the one interpreter the pass permits.
#
# Exit: 0 measured · 2 usage or unusable input (no run with an item, a state that does not parse).
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project=$(cd "$here/../../../.." && pwd)
root="$project/.pi/issue-loop"

die() { printf 'analysis: %s\n' "$1" >&2; exit 2; }
need() { [ $# -ge 2 ] && [ -n "${2:-}" ] || die "$1 needs a value"; }

# ── select: which run is the subject (skill §0) ───────────────────────────────────────────────────────────────
#
# A finished run that worked no item is not a subject: the newest archives here are often one-second pre-flight
# refusals, and analysing one produces five blocks of dashes. So the walk goes back to the newest state that
# carries an item and names every run it skipped, with the reason the state already recorded. The run in place
# is excluded while its process is alive — its streams are still growing and its numbers would change under the
# reader.
cmd_select() {
  local pid live=no list path n start stop reason last=""
  pid=""
  # the lock directory is removed when a run ends, so its absence is the ordinary case, not an error
  if [ -r "$root/lock/owner" ]; then pid=$(tr -cd '0-9' < "$root/lock/owner" 2>/dev/null) || pid=""; fi
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null \
     && [ -z "$(jq -r '.run.stop.reason // ""' "$root/run.json" 2>/dev/null)" ]; then live=yes; fi
  printf 'live=%s  root=%s\n' "$live" "$root"
  if [ "$live" = yes ]; then
    printf 'excluded: %s/run.json — the run in place, started %s, still open (its streams are still growing)\n' \
      "$root" "$(jq -r '.run.startedAt // "-"' "$root/run.json" 2>/dev/null)"
  fi
  list=$(
    if [ "$live" = no ] && [ -f "$root/run.json" ]; then printf '%s\n' "$root/run.json"; fi
    ls -1t "$root"/runs/*.json 2>/dev/null
  )
  [ -n "$list" ] || die "no state under $root — there is no run to analyse"
  while IFS= read -r path; do
    [ -f "$path" ] || continue
    n=$(jq -r '(.items | length) // 0' "$path" 2>/dev/null) || n=0
    case "$n" in ''|*[!0-9]*) n=0 ;; esac
    start=$(jq -r '.run.startedAt // "-"' "$path" 2>/dev/null) || start="-"
    stop=$(jq -r '.run.stop.at // "open"' "$path" 2>/dev/null) || stop="open"
    reason=$(jq -r '(.run.stop.reason // "(no stop recorded — the run ended without a reason)")[0:78]' "$path" 2>/dev/null) || reason="-"
    if [ -z "$last" ] && [ "$n" -gt 0 ]; then
      last=$path
      printf 'LAST=%s\n  %s item(s) · %s → %s\n  stop: %s\n' "$path" "$n" "$start" "$stop" "$reason"
      break
    else
      printf 'skipped: %s — %s item(s) · %s → %s · %s\n' "$path" "$n" "$start" "$stop" "$reason"
    fi
  done <<< "$list"
  [ -n "$last" ] || die "no state carries an item — every archived run worked none"
  printf '\nnext: analysis.sh numbers --state %s\n' "$last"
}

# ── numbers: the figures a finished run owes (skill §1) ───────────────────────────────────────────────────────
#
# Child names are counted apart: `worker` is a work package, `worker-gate` and `worker-tail` are the gate and the
# tail, and a "how many tries" claim is made of the first alone. Roles are printed as a share, because a single
# figure ("the reviewer cost 5.5 Mtok") says nothing until it is read against the run's total.
cmd_numbers() {
  local f=$1 start_epoch stop_epoch stop_raw walls
  [ -f "$f" ] || die "no such state: $f"
  jq -e '.run' "$f" >/dev/null 2>&1 || die "$f is not a loop state (no .run)"

  jq -r '
    def n(f): [.items[] | select(f)] | length;
    "window        \(.run.startedAt) → \(.run.stop.at // "open")",
    "budget        \(.items | length) worked of \(.run.items) budgeted · \(.run.minutes) min · review \(.run.review) · rounds \(.run.maxReviewRounds)",
    "outcomes      done \(n(.result=="done")) · blocked \(n(.result=="blocked")) · cut \(n(.result=="cut")) · skipped \(n(.result=="skipped")) · falsified \(n(.result=="falsified"))",
    "never started \([.excluded[]?] | length) excluded · \([.excluded[]?.verdict] | group_by(.) | map("\(.[0]) \(length)") | join(" · "))",
    "green gates   \([.items[] | select((.metrics.gate // "") | test("failures=0 errors=0"))] | length) of \([.items[] | select((.metrics.gate // "") != "")] | length) that ran one",
    "stop          \(.run.stop.reason // "(no stop recorded)")"
  ' "$f"

  printf '\nphases (by the child that performed them)\n'
  jq -r '.steps | group_by(.name) | map({n: length, name: .[0].name}) | sort_by(-.n) | .[] | "  \(.n)  \(.name)"' "$f"

  printf '\nchildren per item (worker is a work package; gate and tail are their own)\n'
  jq -r '
    [.steps[] | select(.name | test("^(worker|worker-gate|worker-tail|worker-rework|pre-gate-review)$"))]
    | group_by(.issue) | .[]
    | "  #\(.[0].issue)\t" + ([.[].name] | group_by(.) | map("\(.[0])=\(length)") | join(" "))
  ' "$f"

  printf '\nper item\n'
  jq -r '
    .items[]
    | (.metrics // {}) as $m
    | "  #\(.issue)\t\(.result // "-")\tstop \(.stopReason // "-")\tturns \($m.turns // "-")\tcalls \($m.calls // "-")\trounds \($m.reviewRounds // 0) \($m.reviewVerdict // "-")\twall \(($m.wallSeconds // 0 | . * 10 | round) / 10) s\tctx \($m.context // "-")\treads \($m.reads // "-") dup \($m.dupReads // "-")\ttokens \((($m.tokens.cacheRead // 0) + ($m.tokens.input // 0) + ($m.tokens.output // 0)))"
  ' "$f"

  printf '\ntokens by role (frozen in the state)\n'
  jq -r '
    .items[] | select(.metrics.roles != null)
    | .issue as $i | .metrics.roles as $r
    | ([$r | to_entries[] | select(.value != null) | .value] | add) as $t
    | if $t == null or $t == 0 then "  #\($i) no role split"
      else "  #\($i) total \($t) — " + ($r | to_entries | sort_by(-(.value // 0))
        | map("\(.key) \(.value // 0) (\((((.value // 0) * 1000 / $t) | round) / 10) %)") | join(" · ")) end
  ' "$f"

  printf '\nclock (model / gate / other) and the tail after the first gate\n'
  jq -r '
    .items[] | (.metrics // {}) as $m
    | ($m.phases // {}) as $p
    | (($p.modelMs // 0) + ($p.gateMs // 0) + ($p.otherMs // 0)) as $t
    | def sec(x): (((x / 1000) | round) | tostring) + " s";
      def pc(x): if $t == 0 then "-" else ((((x * 1000 / $t) | round) / 10) | tostring) + " %" end;
      if $t == 0 then "  #\(.issue) no phase figure"
      else "  #\(.issue) model \(sec($p.modelMs // 0)) (\(pc($p.modelMs // 0))) · gate \(sec($p.gateMs // 0)) (\(pc($p.gateMs // 0))) · other \(sec($p.otherMs // 0)) (\(pc($p.otherMs // 0))) · of \(sec($t))" end,
      (if $m.afterGate == null then ("  #\(.issue) after the first gate: not measured")
       else ("  #\(.issue) after the first gate \(((($m.afterGate.tokens // 0) * 1000 / (if ($m.afterGate.total // 0) == 0 then 1 else $m.afterGate.total end)) | round / 10)) % — \($m.afterGate.tokens // 0) of \($m.afterGate.total // 0) over \($m.afterGate.sessions // 0) session(s)") end)
  ' "$f"

  start_epoch=$(date -u -d "$(jq -r '.run.startedAt' "$f")" +%s 2>/dev/null) || die "cannot read .run.startedAt"
  stop_raw=$(jq -r '.run.stop.at // ""' "$f")
  [ -n "$stop_raw" ] || stop_raw=now
  stop_epoch=$(date -u -d "$stop_raw" +%s 2>/dev/null) || stop_epoch=0
  walls=$(jq -r '[.items[].metrics.wallSeconds // 0] | add // 0' "$f")
  awk -v a="$start_epoch" -v b="$stop_epoch" -v w="$walls" 'BEGIN {
    printf "  window %d s · item walls %d s · outside the items %d s", b - a, w, b - a - w
    if (b - a > 0) printf " (%.1f %%)\n", 100 * (b - a - w) / (b - a); else printf "\n"
  }'
}

# ── spend: why the tokens went where they did (skill §2) ──────────────────────────────────────────────────────
#
# `loop-spend.py` reads `.pi/issue-loop/items/<issue>/events.jsonl`, which the last run that worked the item
# overwrote — so the filter is by the analysed run's own item list *and* by day, and an item whose stream no
# longer matches the state's recorded total is named instead of quoted. The recorded total is cacheRead + input +
# output; `loop-spend`'s `tok` leaves output out, which is why the comparison adds `.out` back.
cmd_spend() {
  local f=$1 since="" tmp end_day
  [ -f "$f" ] || die "no such state: $f"
  shift
  while [ $# -gt 0 ]; do
    case "$1" in
      --since) need "$@"; since=${2:-}; shift 2 ;;
      *) die "spend: unknown argument $1" ;;
    esac
  done
  [ -n "$since" ] || since=$(jq -r '.run.startedAt' "$f" | cut -c1-10)
  end_day=$(date -u -d "$(jq -r '.run.stop.at // "now"' "$f")" +%Y-%m-%d 2>/dev/null) || end_day=$(date -u +%Y-%m-%d)
  tmp=$(mktemp) || die "no temp file"
  trap 'rm -f "${tmp:-}" "${tmp:-}.err"' EXIT
  timeout 200 python3 "$project/tools/loop-spend.py" --json --top 0 --since "$since" > "$tmp" 2>"$tmp.err" ||
    die "loop-spend failed: $(head -1 "$tmp.err")"
  printf 'window since %s, items of this run only, day ≤ %s\n' "$since" "$end_day"
  printf 'byRole, afterFirstGate and capSaving below are WINDOW-wide, not run-wide — the state\047s own .metrics.roles and .metrics.afterGate are the run figures.\n'

  printf '\nthis run'"'"'s items, as the streams hold them\n'
  jq -r --arg day "$end_day" --argjson want "$(jq -c '[.items[].issue | tostring]' "$f")" '
    [.perItem[] | select(.day <= $day) | select(.issue as $i | $want | index($i))]
    | if length == 0 then "  none — no stream of this run survives a later run'"'"'s overwrite"
      else (.[] | "  #\(.issue)\tday \(.day)\ttok \(.tok) + out \(.out) = \(.tok + .out)\tturns \(.turns)\treads \(.reads) dup \(.dup)\tinjected \(.injected)\tmax_ctx \(.max_ctx)") end
  ' "$tmp"

  printf '\nstream against the state (a mismatch means a later run overwrote the stream)\n'
  jq -r --arg day "$end_day" --argjson want "$(jq -c '[.items[].issue | tostring]' "$f")" '
    [.perItem[] | select(.day <= $day) | select(.issue as $i | $want | index($i))]
    | .[] | "\(.issue)\t\(.tok + .out)"
  ' "$tmp" | while IFS=$'\t' read -r issue total; do
    [ -n "${issue:-}" ] || continue
    recorded=$(jq -r --arg i "$issue" '
      [.items[] | select((.issue | tostring) == $i)
        | ((.metrics.tokens.cacheRead // 0) + (.metrics.tokens.input // 0) + (.metrics.tokens.output // 0))]
      | first // "-"' "$f")
    if [ "$total" = "$recorded" ]; then
      printf '  #%s ok — %s\n' "$issue" "$total"
    else
      printf '  #%s DIFF — stream %s, state %s: the stream belongs to a later run and its shape is not evidence\n' \
        "$issue" "$total" "$recorded"
    fi
  done

  printf '\nlevers (archive measurement, model not a promise)\n'
  jq -r '
    (.tokens.total // 0) as $t
    | if $t == 0 then "  no total"
      else (.capSaving // {} | to_entries | sort_by(.value) | .[]
        | "  cap \(.key) chars saves \(.value) (\(((.value * 1000 / $t) | round) / 10) % of \($t))") end
  ' "$tmp"
}

# ── item: one item's evidence, from the stream's own field names (skill §3) ───────────────────────────────────
cmd_item() {
  local n=$1 d ev
  d="$root/items/$n"
  ev="$d/events.jsonl"
  [ -d "$d" ] || die "no item directory: $d"
  printf 'evidence under %s\n' "$d"
  ls -1 "$d" | grep -v '^events\.jsonl$' | sed 's/^/  /' | head -40
  if [ ! -s "$ev" ]; then
    printf '\nno event stream\n'
    return 0
  fi
  printf '\nstream\n'
  printf '  lines %s · turns %s · tool calls %s · sessions %s\n' \
    "$(wc -l < "$ev")" \
    "$(jq -r 'select(.type=="turn_end") | 1' "$ev" | wc -l)" \
    "$(jq -r 'select(.type=="tool_execution_start") | 1' "$ev" | wc -l)" \
    "$(jq -r 'select(.type=="agent_start") | 1' "$ev" | wc -l)"
  jq -r 'select(.type=="stream_rotation")
    | "  rotation dropped \(.dropped_lines) lines / \(.dropped_bytes) bytes, \(.dropped_turn_ends) turn end(s)"' "$ev"
  printf '\ntools by name\n'
  jq -r 'select(.type=="tool_execution_start") | .toolName // "?"' "$ev" | sort | uniq -c | sort -rn |
    awk '{printf "  %-14s %s\n", $2, $1}'
  printf '\nreads per path (top 12)\n'
  jq -r 'select(.type=="tool_execution_start" and (.toolName=="ctx_read" or .toolName=="read"))
    | (.args.path // .args.file_path // "") | tostring' "$ev" |
    sed "s#$project/.pi/worktrees/loop/##; s#$project/##" |
    sort | uniq -c | sort -rn | head -12 | awk '{n=$1; $1=""; sub(/^ /,""); printf "  %-58s %s\n", $0, n}'
  printf '\ntrims and the repeat rule\n'
  if [ -s "$d/tool-result-trims.log" ]; then
    awk -F'chars=| kept=' '
      /LOOP-TOOL-RESULT-TRIM/   { t++; drop += $2 - $3 }
      /LOOP-TOOL-RESULT-REPEAT/ { r++; with += $2 - $3 }
      END { printf "  capped %d result(s), %d chars dropped · repeat rule replaced %d read(s), %d chars withheld\n", t, drop, r, with }
    ' "$d/tool-result-trims.log"
  else
    printf '  no trim record\n'
  fi
  printf '\nverdicts on record\n'
  if [ -s "$d/red-on-HEAD.log" ]; then
    printf '  red on HEAD: %s\n' "$(grep -cE '^(FAIL|PASS)' "$d/red-on-HEAD.log") case line(s), $(grep -c '^FAIL' "$d/red-on-HEAD.log") FAIL"
  fi
  [ -s "$d/gate.log" ] && printf '  gate log %s lines\n' "$(wc -l < "$d/gate.log")"
  for f in worker-report.md worker-gate-report.md worker-tail-report.md pre-gate-review.md reviewer-report-1.md; do
    [ -s "$d/$f" ] && printf '  %s %s bytes\n' "$f" "$(wc -c < "$d/$f")"
  done
  return 0
}

# ── rates: one run is not a rate (skill §3) ───────────────────────────────────────────────────────────────────
cmd_rates() {
  local n=12
  while [ $# -gt 0 ]; do
    case "$1" in
      --n) need "$@"; n=${2:-}; shift 2 ;;
      *) die "rates: unknown argument $1" ;;
    esac
  done
  printf 'archive: %s state(s)\n' "$(ls -1 "$root"/runs/*.json 2>/dev/null | wc -l)"
  jq -s -r '
    [.[] | select(.run != null)]
    | "runs with a state record: \(length) · no stop recorded: \([.[] | select(.run.stop == null)] | length)",
      "items worked \( [.[] | .items[]?] | length) · done \( [.[] | .items[]? | select(.result=="done")] | length) · blocked \( [.[] | .items[]? | select(.result=="blocked")] | length)",
      "blocked by stop class: \([.[] | .items[]? | select(.result=="blocked") | (.stopReason // "unclassified")] | group_by(.) | map("\(.[0]) \(length)") | join(" · "))",
      "outcomes: \([.[] | .items[]? | (.result // "no-result")] | group_by(.) | map("\(.[0]) \(length)") | join(" · "))"
  ' "$root"/runs/*.json
  printf '\nlast %s runs that worked an item (oldest first)\n' "$n"
  jq -s -r --argjson n "$n" '
    [.[] | select(.items != null and (.items | length) > 0)] | sort_by(.run.startedAt) | .[-$n:]
    | .[] | "  \(.run.startedAt[0:16])  \(.items | length) item(s)  done \([.items[] | select(.result=="done")] | length)  blocked \([.items[] | select(.result=="blocked")] | length)  \([.items[] | select(.result != "done") | (.stopReason // "unclassified")] | group_by(.) | map("\(.[0])×\(length)") | join(" "))"
  ' "$root"/runs/*.json
}

main() {
  [ $# -ge 1 ] || die "usage: analysis.sh select|numbers|spend|item|rates [args]"
  local cmd=$1
  shift
  case "$cmd" in
    select)
      while [ $# -gt 0 ]; do
        case "$1" in
          --root) need "$@"; root=${2:-}; shift 2 ;;
          *) die "select: unknown argument $1" ;;
        esac
      done
      cmd_select ;;
    numbers)
      local state=""
      while [ $# -gt 0 ]; do
        case "$1" in
          --state) need "$@"; state=${2:-}; shift 2 ;;
          *) die "numbers: unknown argument $1" ;;
        esac
      done
      [ -n "$state" ] || die "numbers needs --state FILE"
      cmd_numbers "$state" ;;
    spend)
      local sstate="" ssince=""
      while [ $# -gt 0 ]; do
        case "$1" in
          --state) need "$@"; sstate=${2:-}; shift 2 ;;
          --since) need "$@"; ssince=${2:-}; shift 2 ;;
          *) die "spend: unknown argument $1" ;;
        esac
      done
      [ -n "$sstate" ] || die "spend needs --state FILE"
      if [ -n "$ssince" ]; then cmd_spend "$sstate" --since "$ssince"; else cmd_spend "$sstate"; fi ;;
    item)
      local num=$1
      shift
      while [ $# -gt 0 ]; do
        case "$1" in
          --root) need "$@"; root=${2:-}; shift 2 ;;
          *) die "item: unknown argument $1" ;;
        esac
      done
      cmd_item "$num" ;;
    rates) cmd_rates "$@" ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) die "unknown subcommand: $cmd" ;;
  esac
}

main "$@"
