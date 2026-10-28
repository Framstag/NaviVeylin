#!/usr/bin/env bash
# gate-attribute.sh — the mechanical facts of a red gate log, in one bounded block (issue #689).
#
# Why this file exists: past an item's first gate call, reading the log is what the item keeps paying for. Over the
# loop's archive (`.pi/issue-loop/items`, read by `python3 tools/loop-spend.py`, 2026-10-28) 81 % of a gate-running
# session's tokens are spent after its first gate call — 10 793 turns at 106 k/turn against 5 991 at 56 k — and per
# heavy item the gate and suite runs are ~20 % of its shell calls beside ~18 % spent reading the log they produced,
# followed by a `grep`/`sed` chain to find the failing case (#682 47+30 of 186, #50 24+25 of 234, #319 33+39 of 195).
#
# What this reads, and what it must not decide. The lines below are what the log records: the failing case, the
# `file:line` it printed, its first error line, the step that failed, and the exact command that reruns only that
# case. Attribution is `guidelines/Build.md` §4d's and a red test's diagnosis is §6f's: whether a red case is a flake,
# a real failure or a wrong expectation is a judgement, and a script that guesses converts one bad turn into a wrong
# fix. The flake line therefore states the recorded fact and nothing else — this tree keeps no flake list, so it says
# so rather than answering "no" about a case nobody has classified.
#
# The log is what `tools/loop/gate.sh` writes, with no second format of this script's own: `gate: <cmd>` starts a
# step, the same line with ` — <n>s` ends it, and the suite's own output stands between them — a shell case prints
# `<group>#<name>| <text>` while it runs and `FAIL <group>#<name>` at its end, while Gradle names a failing case
# `<class> > <method> FAILED` with its message or location on the following lines and its task as
# `> Task :<module>:<task> FAILED`. `LOOP-GATE: tests=… failures=… errors=…` is the verdict.
#
# The rerun is derived from the failing step's own command line and never guessed: the launcher and the flags are
# that line's words, the task is the one the log named as failed and the case is the one it named as failing. A step
# that names no launcher, no failed test task or no case is answered with `rerun: not derivable from this log` instead
# of an invented module — a rerun that names the wrong module costs a round and reads like a result.
#
# Every line is cut to a share of `LOOP_TOOL_RESULT_MAX_CHARS` — the profile's own bound, read and never sourced, by
# the `sed` the child adapter applies — so the whole block stays inside the bound a child's tool result is held to
# (issue #660). `0`, or a profile without the key, means no cut.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

usage() {
  cat <<'USAGE'
Usage: gate-attribute.sh <gate.log> [--conf <loop.conf>] [--max-chars <n>]
  --conf       the loop profile the bound is read from (default: the profile beside this engine, tools/loop/loop.conf)
  --max-chars  the bound, overriding the profile's LOOP_TOOL_RESULT_MAX_CHARS; "0" means no bound
Exit: 0 the log was read and its block is printed — a red log is this script's input, not its failure;
      2 usage, a log it cannot read, or a log that carries no gate line of any kind.
USAGE
}

log=""
conf=""
max_chars=""
while [ $# -gt 0 ]; do
  case "$1" in
    --conf) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { usage >&2; exit 2; }; conf="${2:-}"; shift 2 ;;
    --max-chars) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { usage >&2; exit 2; }; max_chars="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) printf 'gate-attribute: unknown argument %s\n' "$1" >&2; usage >&2; exit 2 ;;
    *) [ -z "$log" ] || { printf 'gate-attribute: one log at a time (already given %s)\n' "$log" >&2; exit 2; }; log="$1"; shift ;;
  esac
done
[ -n "$log" ] || { usage >&2; exit 2; }
[ -f "$log" ] && [ -r "$log" ] || { printf 'gate-attribute: cannot read %s\n' "$log" >&2; exit 2; }
[ -n "$conf" ] || conf="$here/loop/loop.conf"

# The bound, read the way the child adapter reads it (never sourced: a check looks at a tree, it does not run that
# tree's configuration). A value that is not a whole number is no bound, exactly as the adapter treats it.
if [ -z "$max_chars" ]; then
  max_chars=0
  if [ -f "$conf" ]; then
    max_chars=$(sed -n 's/^LOOP_TOOL_RESULT_MAX_CHARS=["]\{0,1\}\([0-9][0-9]*\)["]\{0,1\}[[:space:]]*$/\1/p' "$conf" | tail -1)
  fi
fi
case "$max_chars" in ''|*[!0-9]*) max_chars=0 ;; esac
# The block is at most nine lines, so a line's share of the bound is a ninth of it, less the marker and the newline:
# the guarantee is arithmetic, and nothing is dropped — which is the difference between a block a child can afford and
# one that arrives cut in half, its last line the rerun command.
per_line=0
[ "$max_chars" -gt 0 ] && per_line=$(( max_chars / 9 - 4 ))

# ── the log's own structure: its steps, its verdict and its failing cases ───────────────────────────────────────
# One pass, because a gate log of a `tools/loop/**` delta carries every case's relayed output and is read once here
# rather than once per fact. A relayed case line is the case's own text and never a failure marker: a case that prints
# a Gradle-shaped line as its fixture would otherwise be read as the failing case.
structure=$(awk '
  /^gate: / {
    if ($0 ~ / — [0-9]+s$/ || $0 ~ / — the step overran/) { instep = 0; next }
    cmd = $0; sub(/^gate: /, "", cmd)
    print "STEP\t" cmd
    step = cmd; instep = 1; split("", first)
    next
  }
  /^LOOP-GATE: / { v = $0; sub(/^LOOP-GATE: /, "", v); print "VERDICT\t" v; next }
  {
    if (!instep) next
    if (match($0, /^[^|]+\| /)) {
      i = index($0, "| "); nm = substr($0, 1, i - 1)
      if (!(nm in first)) first[nm] = substr($0, i + 2)
      next
    }
    if ($0 ~ /^[^ \t].* > .* FAILED$/ && $0 !~ /^> Task /) {
      cl = $0; sub(/ FAILED$/, "", cl)
      nf++
      if (!(step in failed)) { failed[step] = 1; nfs++ }
      printf "FAILREC\tgradle\t%d\t%s\t%s\n", NR, cl, step
      next
    }
    if ($0 ~ /^FAIL /) {
      name = $0; sub(/^FAIL /, "", name); sub(/ \(exceeded [0-9]+ s\)$/, "", name)
      nf++
      if (!(step in failed)) { failed[step] = 1; nfs++ }
      printf "FAILREC\tshell\t%d\t%s\t%s\n", NR, name, step
      next
    }
  }
  END { print "FAILCOUNT\t" nf + 0; print "FAILSTEPS\t" nfs + 0 }
' "$log")

verdict=""
fail_count=0
fail_steps=0
first_kind=""
first_line=0
first_case=""
first_step=""
others=""
steps=""
while IFS=$'\t' read -r tag a b c d; do
  case "$tag" in
    STEP) steps="$steps$a"$'\n' ;;
    VERDICT) verdict="$a" ;;
    FAILCOUNT) fail_count="$a" ;;
    FAILSTEPS) fail_steps="$a" ;;
    FAILREC)
      if [ -z "$first_case" ]; then
        first_kind="$a"; first_line="$b"; first_case="$c"; first_step="$d"
      else
        # The block names what else failed, so a two-failure log is not read as a one-failure log. Three names show the
        # shape; past them the block keeps a count, because the block is bounded and the log is not.
        if [ "$(printf '%s' "$others" | wc -w)" -lt 3 ]; then
          others="$others${others:+, }$c"
        else
          others="$others …"
        fi
      fi
      ;;
  esac
done <<< "$structure"

# ── the attributed case's own lines ─────────────────────────────────────────────────────────────────────────────
# `mode=case` reads the lines around and after the failure marker the structure pass found; `mode=step` reads a step
# that failed before any case reported (`LOOP-GATE: … errors=1` — a configuration failure, #268), whose first error
# line is the build's own.
detail_step="$first_step"
detail_mode="case"
# A gate whose steps failed without naming a case is attributed by its step: the verdict's own numbers say so — an
# `errors=` above zero is a step that failed before a case ran, and a `— failing:` list is the steps the gate reported
# red even when no case line reached the log. Measured shape (#268): a compiled step died at configuration and was
# tallied `tests=0 failures=0 errors=1`.
if [ "$fail_count" -eq 0 ]; then
  case "$verdict" in
    *'— failing:'*|*'errors='[1-9]*)
      detail_mode="step"
      detail_step=""
      [ "$fail_steps" -gt 0 ] || fail_steps=1
      ;;
  esac
fi
if [ "$detail_mode" = "step" ]; then
  # The failed step's command as the verdict's own `— failing:` list names it: that list is the commands, space
  # separated, so the step whose line is a prefix of it is the one the gate reported first.
  tail_text="${verdict#*— failing:}"
  tail_text="${tail_text# }"
  while IFS= read -r candidate; do
    [ -n "$candidate" ] || continue
    case "$tail_text" in "$candidate"*) detail_step="$candidate"; break ;; esac
  done <<< "$steps"
fi
detail=""
if [ -n "$detail_step" ]; then
  detail=$(awk -v step="$detail_step" -v mode="$detail_mode" -v kind="$first_kind" -v cname="$first_case" -v target="$first_line" '
    function trim(s) { gsub(/^[ \t]+/, "", s); gsub(/[ \t]+$/, "", s); return s }
    function ref(s) {
      if (match(s, /[A-Za-z0-9_.\/-]+\.(kt|kts|java|cpp|hpp|h|sh|py|xml|json|md):[0-9]+/)) return substr(s, RSTART, RLENGTH)
      return ""
    }
    BEGIN { instep = 0; grading = 0; err = ""; frame = ""; fileref = ""; done = 0; taskfail = "" }
    # A step that named no case is attributed by the first error line of the build itself — `e: …` or `error: …` (§4a)
    # — and a `> Task … FAILED` line only when the step printed no such line: the task name says which task broke, not
    # what it said.
    function flushstep() {
      if (done) return
      if (err == "" && taskfail != "") { err = taskfail; fileref = ref(taskfail) }
      print "ERROR\t" err
      if (fileref != "") print "FILEREF\t" fileref
      done = 1
    }
    {
      if (done) next
      if ($0 ~ /^gate: /) {
        if (mode == "step" && grading) flushstep()
        if ($0 ~ / — [0-9]+s$/ || $0 ~ / — the step overran/) { instep = 0; grading = 0; next }
        cmd = $0; sub(/^gate: /, "", cmd)
        instep = (cmd == step)
        split("", first)
        grading = (instep && mode == "step")
        next
      }
      if (!instep) next
      if (NR == target) {
        if (kind == "shell") {
          err = trim(first[cname]); fileref = ref(err)
          print "ERROR\t" err
          if (fileref != "") print "FILEREF\t" fileref
          done = 1; next
        }
        grading = 1
        next
      }
      if (NR < target) {
        if (match($0, /^[^|]+\| /)) {
          i = index($0, "| "); nm = substr($0, 1, i - 1)
          if (!(nm in first)) first[nm] = substr($0, i + 2)
        }
        next
      }
      if (!grading) next
      if (mode == "step") {
        if ($0 ~ /^(e: |error:)/) {
          err = trim($0); fileref = ref($0)
          print "ERROR\t" err
          if (fileref != "") print "FILEREF\t" fileref
          done = 1
        } else if ($0 ~ /^> Task .* FAILED$/ && taskfail == "") {
          taskfail = trim($0)
        }
        next
      }
      # The next failure, the next step or the verdict is where the attributed case and its lines end.
      if ($0 ~ /^FAIL / || $0 ~ /^LOOP-GATE/) { grading = 0; done = 1; next }
      if ($0 ~ /^[^ \t].* > .* FAILED$/ && $0 !~ /^> Task /) { grading = 0; done = 1; next }
      t = trim($0)
      if (t == "" || t ~ /^> Task /) next
      if (err == "") {
        if (t ~ /^at /) { if (frame == "") frame = t; next }
        err = t
        if (fileref == "") fileref = ref(t)
        next
      }
      if (fileref == "") fileref = ref(t)
    }
    END {
      if (mode == "step") { flushstep(); exit }
      if (!done) {
        if (err == "") err = frame
        print "ERROR\t" err
        if (fileref != "") print "FILEREF\t" fileref
      }
    }
  ' "$log")
fi

case_error=""
case_fileref=""
while IFS=$'\t' read -r tag a; do
  case "$tag" in
    ERROR) case_error="$a" ;;
    FILEREF) case_fileref="$a" ;;
  esac
done <<< "$detail"
# A build reports a source location as a URL (`e: file:///…/Map.kt:42:13`): the three slashes are the scheme's, and the
# path a reader can open starts at the third.
case "$case_fileref" in ///*) case_fileref="/${case_fileref#///}" ;; esac

# ── the rerun command, from the failing step's own words ────────────────────────────────────────────────────────
# Nothing here is a value this project records: the launcher, the flags and the task are the failing step's own line,
# and the case is the failure's own line. The two shapes a gate step has are a build — where one case is selected with
# `--tests` — and a shell suite, where one case is selected with `--case`.
rerun_step="$first_step"
[ -n "$rerun_step" ] || rerun_step="$detail_step"
rerun=""
if [ -n "$rerun_step" ]; then
  # shellcheck disable=SC2086 # the step's words are a command line, and each one is examined as it stands.
  set -f
  set -- $rerun_step
  set +f
  # A step that starts its command through `bash`/`sh` names the script one word later.
  case "${1:-}" in bash|sh|/bin/bash|/bin/sh|/usr/bin/bash|/usr/bin/sh) shift ;; esac
  launcher="${1:-}"
  case "$(basename -- "${launcher:-x}")" in
    gradlew|gradle)
      fqcn="${first_case%% > *}"
      # The failed test task, as the log's own `> Task :<module>:<task> FAILED` line names it: only a test task can be
      # rerun with `--tests`, so a red compile — which names `compileXDebugKotlin` — is not answered with a command
      # that cannot run.
      task=$(awk -v step="$rerun_step" '
        $0 == "gate: " step { instep = 1; next }
        instep && ($0 ~ / — [0-9]+s$/ || $0 ~ / — the step overran/) { exit }
        instep && /^> Task .* FAILED$/ {
          line = $0; sub(/^> Task /, "", line); sub(/ FAILED$/, "", line)
          if (line ~ /[Tt]est/) { print line; exit }
        }
      ' "$log")
      if [ -n "$task" ] && [ -n "$fqcn" ] && [ "$first_kind" = "gradle" ]; then
        rerun="$launcher $task --tests \"$fqcn\""
        # The step's own flags, kept verbatim: a rerun without `-PforceTests` is an up-to-date task or a cache hit, and
        # one without the gate's other flags is not the configuration the failure was seen in (§4e).
        shift
        for w in "$@"; do
          case "$w" in -*) rerun="$rerun $w" ;; esac
        done
      fi
      ;;
    *-selftest.sh)
      case "$first_case" in
        *'#'*) rerun="$rerun_step --case $first_case" ;;
      esac
      ;;
  esac
fi
[ -n "$rerun" ] || rerun="not derivable from this log"

# ── the block ─────────────────────────────────────────────────────────────────────────────────────────────────
# One fact per line, in the order a worker reads them. A verdict a log does not carry is stated as absent: a red gate
# whose log ends early must not read as a green one.
cut_line() { # <text> → the text, cut to the per-line share of the bound and marked when it was
  local s=$1
  if [ "$per_line" -gt 3 ] && [ "${#s}" -gt "$per_line" ]; then
    printf '%s…' "${s:0:$((per_line - 3))}"
  else
    printf '%s' "$s"
  fi
}

# The verdict's numbers without the per-step wall clocks the gate appends: those are a cost, not an attribution.
verdict_line() { # <verdict tail>
  local v=$1
  case "$v" in
    '') printf 'none — this log carries no LOOP-GATE verdict line' ;;
    tests=*)
      local counts=""
      counts=$(printf '%s' "$v" | sed -n 's/^\(tests=[0-9][0-9]* failures=[0-9][0-9]* errors=[0-9][0-9]*\).*$/\1/p')
      if [ -n "$counts" ]; then
        if [ "$fail_steps" -gt 0 ]; then
          printf '%s — %s failing step%s' "$counts" "$fail_steps" "$([ "$fail_steps" -eq 1 ] || printf 's')"
        else
          printf '%s' "$counts"
        fi
      else
        printf '%s' "$v"
      fi
      ;;
    *) printf '%s' "$v" ;;
  esac
}

printf '%s\n' "$(cut_line "verdict: $(verdict_line "$verdict")")"
if [ "$fail_count" -eq 0 ] && [ "$fail_steps" -eq 0 ]; then
  printf '%s\n' "$(cut_line 'case: none — every case this log reports passed')"
else
  if [ -n "$first_step" ]; then
    printf '%s\n' "$(cut_line "step: $first_step")"
  elif [ -n "$detail_step" ]; then
    printf '%s\n' "$(cut_line "step: $detail_step")"
  else
    printf '%s\n' "$(cut_line 'step: none — the log names no failing step')"
  fi
  if [ -n "$first_case" ]; then
    printf '%s\n' "$(cut_line "case: $first_case")"
    [ -z "$others" ] || printf '%s\n' "$(cut_line "others: $others")"
  else
    printf '%s\n' "$(cut_line 'case: none — a step failed before a case reported')"
  fi
  printf '%s\n' "$(cut_line "file:line: ${case_fileref:-not named by this log}")"
  printf '%s\n' "$(cut_line "error: ${case_error:-not named by this log}")"
  printf '%s\n' "$(cut_line 'flake: not recorded — this tree keeps no flake list')"
  printf '%s\n' "$(cut_line "rerun: $rerun")"
fi
exit 0
