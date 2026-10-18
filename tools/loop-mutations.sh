#!/usr/bin/env bash
# loop-mutations.sh — the mutation harness for a **shell gate**: one mutation per invariant, the named case
# must go red, restore, the case must go green again.
#
# `.pi/skills/revert-check` owns the rule (*one mutation per check*, a mutation that lands, a failure for the
# right reason, an exact restore, a green re-run); this file is the mechanical form of it for the loop, whose
# gate is `tools/loop-selftest.sh` rather than Gradle — so there is no cache to force and no XML to quote, and
# the two things that go wrong are the same two: **the mutation did not land** (a pattern that no longer
# matches the line) and **the case did not notice** (an assertion adjacent to the invariant). Both are
# reported as failures of this harness, never as a pass.
#
# It restores every file it touches, and restores the current one even when it is killed (EXIT trap). Run the
# full set before a change that owns `tools/loop/**` is archived; run one with `--only` while iterating.
#
# Exit: 0 every selected mutation went red and its restore went green; 1 a mutation did not apply, was not
#       noticed, or its restore stayed red; 2 usage or a missing prerequisite.
#
# Usage: tools/loop-mutations.sh [--list] [--only <labels>] [--engine <dir>] [--out <file>]
#                                [--timeout <seconds>]
#                                [--probe no-apply|stays-green]
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

list_only=0
only=""
engine="$root/tools/loop"
out=""
case_timeout=240
probe=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --only) only="${2:-}"; shift 2 ;;
    --engine) engine="${2:-}"; shift 2 ;;
    --out) out="${2:-}"; shift 2 ;;
    --timeout) case_timeout="${2:-}"; shift 2 ;;
    --probe) probe="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
    *) echo "loop-mutations: unknown argument $1" >&2; exit 2 ;;
  esac
done

for tool in bash jq git java javac sed; do
  command -v "$tool" >/dev/null 2>&1 || { echo "loop-mutations: $tool is required" >&2; exit 2; }
done
[ -d "$engine" ] || { echo "loop-mutations: no engine directory at $engine" >&2; exit 2; }
# The engine reads its dependency as a sibling (`$LOOP_DIR/../issues`), so a copy must keep that layout —
# `--engine <dir>` wants `<dir>` to be the copied `loop` directory, with `issues` beside it.
[ -f "$engine/../issues/lib.sh" ] || {
  echo "loop-mutations: --engine $engine has no sibling issues directory — copy the layout, not just tools/loop" >&2
  exit 2
}
[ -f "$root/tools/loop-selftest.sh" ] || { echo "loop-mutations: the gate tools/loop-selftest.sh is missing" >&2; exit 2; }

# The mutations. Fields are `@`-separated (no sed expression below contains one), and one record's sed
# expressions are separated by `;;`:
#
#   label @ fifo @ file @ case @ what it removes @ sed expression(s)
#
# `fifo` is `-` for a mutation that needs no blocked child; where it is a name, the harness creates a fifo and
# releases each child a moment after it starts, so the wrapper has a window in which to write (mutation 9.3).
MUTATIONS=(
  '9.1@-@tools/loop/state.sh@state#secondRunRefusesWithExit5@the lock that keeps one run per repository@s|^  if ! mkdir "$LOCK_FILE" 2>/dev/null; then|  if false; then|'
  '9.2@-@tools/loop/worktree.sh@worktree#refusesWhenMainAdvanced@the stale-base refusal@s|^  if \[ "$current" != "$base" \]; then|  if false; then|'
  '9.3@writes@tools/loop/run.sh@labels#writesOnlyBetweenChildren@one writer per tree: the child runs while the wrapper writes state@s|^  run_child "$item_dir/worker-prompt.md" "$item_dir/worker-report.md"$|  ( cd "$worktree" \&\& $LOOP_AGENT_CMD "$item_dir/worker-prompt.md" ) > "$item_dir/worker-report.md" 2>\&1 \&\n  worker_pid=$!\n  ( while kill -0 "$worker_pid" 2>/dev/null; do bash "$LOOP_DIR/state.sh" step A --name during-child --status running >/dev/null 2>\&1; sleep 0.02; done ) \&\n  wait "$worker_pid"|'
  '9.4@-@tools/loop/run.sh@labels#trapRemovesInProgressOnSignal@the signal trap that removes the in-progress label@s|^    loop_labels unpick "$in_progress" >&2 \|\| true$|    : # the mutation keeps the in-progress label|'
  '9.5@-@tools/loop/run.sh@run#childClaimWithoutCommitIsBlocked@the run verifying instead of trusting the child@s|^  if \[ "$worker_exit" -ne 0 \]; then|  if false; then|;;s|^  elif \[ "$verdict_claim" = "skipped" \]; then|  elif false; then|;;s|^  elif \[ "$head_now" = "$base" \]; then|  elif false; then|;;s|^  elif \[ -z "$change" \]; then|  elif false; then|;;s|^  elif \[ -n "$LOOP_GATE_CMD" \] && \[ "$gate_found" -eq 0 \]; then|  elif false; then|;;s|^  elif \[ "$cited_evidence" -eq 1 \] && \[ "$carried" -eq 0 \]; then|  elif false; then|'
  '9.6@-@tools/loop/run.sh@run#decisionCapRefusesToStart@the decision cap that stops a run@s|^if \[ "${open_decisions:-0}" -ge "$max_decisions" \]; then|if false; then|'
  '9.7@-@tools/loop/run.sh@run#mayCloseNoLeavesTheIssueOpen@the closure opt-out@s|^      if \[ "$may_close" = "no" \]; then close_args+=(--no-close); fi$|      : # the mutation closes anyway|'
  '9.8@-@tools/loop/worktree.sh@worktree#oneBranchPerItemOffTheBase@one branch per item@s|switch -c "$branch" "$base"|switch -c "fix/loop-branch" "$base"|'
  '9.9@-@tools/loop/run.sh@state#agentCommandIsTheOnlyIndirection@the single agent-command indirection@s|^  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1$|  ( cd "$LOOP_ROOT" \&\& pi --print "$1" ) > "$2" 2>\&1|'
  '9.10@-@tools/loop/run.sh@run#reviewerWriteInvalidatesTheRound@the reviewer read-only comparison@s|^      if \[ "$read_only" = "on" \] && \[ "$before" != "$after" \]; then|      if false; then|'
  '9.11@-@tools/loop/prompts/worker.md@state#projectKeysAreReadFromLoopConf@the project facts living in loop.conf@s|{{WORKER_SKILLS}}|openspec-apply-change build-app run-tests revert-check|'
  '12.1@-@tools/loop/worktree.sh@run#commitsWithoutAnAmbientIdentity@the commit is attributed to the run'"'"'s own identity@s|-c user.name="\$who_name" -c user.email="\$who_email" ||'
  '12.2@-@tools/loop/run.sh@run#childStartsInTheProjectRoot@the child started in the project, where the project'"'"'s own policy is resolved@s|^  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1$|  ( cd "$worktree" \&\& $LOOP_AGENT_CMD "$1" ) > "$2" 2>\&1|'
  '12.3@-@tools/loop/run.sh@run#childWriteOutsideTheWorktreeIsBlocked@the run catching a child write left outside the worktree@s|^    printf '"'"'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran\\n'"'"' >> "$2"$|    : # the mutation carries the write instead|'
  '12.4@-@tools/loop/run.sh@run#reviewerWriteInTheProjectIsDiscarded@the reviewer comparison reading the project tree as well@s|git -C "$worktree" diff; project_state; }|git -C "$worktree" diff; }|'
  '12.5@-@tools/loop/worktree.sh@worktree#reusesABranchThatCarriesNoWorkOfItsOwn@the reuse of a branch that carries nothing of its own@s|^    if gitw merge-base --is-ancestor "$branch" "$base"; then$|    if false; then|'
  '12.6@-@tools/loop/worktree.sh@worktree#refusesABranchCarryingAPreviousAttempt@the refusal of a branch that carries a previous attempt@s|^    if gitw merge-base --is-ancestor "$branch" "$base"; then$|    if true; then|'
  '12.7@-@tools/loop/run.sh@run#branchThatAppearsWhileTheRunIsOpenIsRecorded@the refusal on a leftover branch being recorded and escalated@s|loop_state done "$issue" --result blocked --verdict blocked --note "$start_out" >/dev/null|: # the mutation drops the recorded item|'
  '13.1@-@tools/loop/prescreen.sh@prescreen#previousAttemptBranchIsADecision@the pre-screen asking git for a previous attempt on the item branch prefix@s|^    if ! git -C "$LOOP_ROOT" merge-base --is-ancestor "$branch" "$run_base" 2>/dev/null; then|    if false; then|'
  '13.2@-@tools/loop/run.sh@run#leftoverBranchIsRecordedNotDropped@the pre-screen verdicts that need a decision being escalated by the run@s|^  \[ -n "$decision_agent" \] \|\| return 0$|  return 0|'
  '13.3@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the start-time pre-flight on a kept worktree a previous run left dirty@s|^if \[ -d "$worktree" \] && \[ ! -e "$run_lock" \] && worktree_is_dirty; then$|if false; then|'
  '13.4@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the refusal being recorded under the item its branch marker names@s|^    if \[ "$dry_run" -eq 0 \] && \[ -n "$owner_item" \]; then$|    if false; then|'
  '13.5@-@tools/loop/run.sh@run#resetWorktreeDiscardsWhatItNames@the start-time repair discarding what it named@s|^    git -C "$worktree" reset -q --hard \|\| loop_die "run: could not reset the kept worktree"$|    : # the mutation discards no tracked change|;;s|^    git -C "$worktree" clean -qfd \|\| loop_die "run: could not clean the kept worktree"$|    : # the mutation discards no untracked file|'
  '13.6@-@tools/loop/run.sh@state#secondRunRefusesWithExit5@the lock telling a live run'"'"'s tree from a previous run'"'"'s leftovers@s|^if \[ -d "$worktree" \] && \[ ! -e "$run_lock" \] && worktree_is_dirty; then$|if [ -d "$worktree" ] \&\& worktree_is_dirty; then|'
  '13.7@-@tools/loop/run.sh@worktree#keepsByDefaultAndRemovesOnRequest@the end-of-run form honoured wherever the run ends@s|^  if \[ "$clean_worktree" -eq 1 \]; then$|  if false; then|'
  '12.8@-@tools/loop/run.sh@run#aSkipClearsTheInProgressMark@the skip exit path clearing the in-progress mark@s|^      loop_labels unpick "$issue" >&2 \|\| true   # the exit path clears the mark$|      : # the mutation keeps the mark|'
)

# The harness's two probes: they prove the harness's own guards can fail, on a throwaway copy of the engine
# (`tools/loop-mutations-selftest.sh`). A probe is a mutation like any other, with a deliberately wrong or
# deliberately harmless shape.
PROBE_RECORDS=(
  'probe-no-apply@-@tools/loop/state.sh@state#initAndShowRoundTrips@a pattern that matches nothing@s|^# this line is not in the file$|# mutation|'
  'probe-stays-green@-@tools/loop/report.sh@report#headerAndTableShape@a real edit no case in the set asserts@s|^set -uo pipefail$|&\n# a probe as a second line|'
)

selected() { # selected <label>
  [ -z "$only" ] && return 0
  case " $only " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

if [ "$list_only" -eq 1 ]; then
  for record in "${MUTATIONS[@]}"; do
    IFS='@' read -r label _fifo _file case_name desc _exprs <<< "$record"
    printf '%s\t%s\t%s\n' "$label" "$case_name" "$desc"
  done
  exit 0
fi

scratch=$(mktemp -d "${TMPDIR:-/tmp}/loop-mutations.XXXXXX") || { echo "loop-mutations: cannot create a scratch directory" >&2; exit 2; }
# The transcript goes to stdout and, with `--out`, to a file as well: `exec > >(tee …)` keeps this shell
# the one that counts (a pipeline per report would increment the counters in a subshell and lose them).
if [ -n "$out" ]; then exec > >(tee -a "$out"); fi
current_backup=""
current_file=""
cleanup() {
  # A killed harness must not leave a mutation in the tree: the case that would catch it is the one this
  # file runs, and the next suite run would fail for a reason nobody is looking for.
  if [ -n "$current_backup" ] && [ -n "$current_file" ]; then
    cp "$current_backup" "$current_file"
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT

red=0
not_noticed=0
failed=0

# A fifo that releases each blocked child a moment after it starts (its `.ready` marker), so the wrapper has a
# window in which to write and the write is observable. The path is absolute: a child runs with its own cwd.
release_fifo() {
  local fifo="$1" last="" stamp marker="$1.ready"
  for _ in $(seq 1 600); do
    if [ -f "$marker" ]; then
      stamp=$(stat -c '%y' "$marker" 2>/dev/null)
      if [ -n "$stamp" ] && [ "$stamp" != "$last" ]; then
        last="$stamp"
        sleep 0.2
        timeout 3 sh -c "printf '\n' > '$fifo'" 2>/dev/null || true
      fi
    fi
    sleep 0.02
  done
}

run_case() { # run_case <case> <fifo|->  → 0 the case passed, non-zero it failed
  local case_name="$1" fifo="$2" status writer=""
  if [ "$fifo" != "-" ]; then
    release_fifo "$fifo" &
    writer=$!
    PI_STUB_WAIT="$fifo" LOOP_ENGINE="$engine" LOOP_CONF="$engine/loop.conf" \
      timeout "$case_timeout" bash "$root/tools/loop-selftest.sh" --case "$case_name" >/dev/null 2>&1
  else
    LOOP_ENGINE="$engine" LOOP_CONF="$engine/loop.conf" \
      timeout "$case_timeout" bash "$root/tools/loop-selftest.sh" --case "$case_name" >/dev/null 2>&1
  fi
  status=$?
  if [ -n "$writer" ]; then kill "$writer" 2>/dev/null; pkill -P "$writer" 2>/dev/null || true; fi
  return "$status"
}

apply_mutation() { # apply_mutation <file> <backup> <expressions separated by ;;>
  local file="$1" backup="$2" exprs="$3" expr
  cp "$file" "$backup"
  while IFS= read -r expr; do
    [ -n "$expr" ] || continue
    sed -i "$expr" "$file"
  # `printf '%s\n'`: a line without a trailing newline makes `read` return non-zero and the loop body would
  # never run — the mutation would then be reported as "did not apply" and the harness would be blamed.
  done < <(printf '%s\n' "$exprs" | sed 's/;;/\n/g')
}

one_mutation() { # one_mutation <record>
  local record="$1"
  local label fifo file case_name desc exprs backup
  IFS='@' read -r label fifo file case_name desc exprs <<< "$record"
  selected "$label" || return 0

  # The mutation is applied to the *engine* directory, which is this repository's `tools/loop` by default and
  # a throwaway copy when the harness's own self-test drives it (`--engine`). The case then runs against the
  # same directory, so what it notices is the mutation, not a stale copy.
  local rel="${file#tools/loop/}"
  local path="$engine/$rel"
  [ -f "$path" ] || { printf 'MISSING-FILE %s %s\n' "$label" "$file"; failed=$((failed + 1)); return 0; }

  local fifo_path="-"
  if [ "$fifo" != "-" ]; then
    fifo_path="$scratch/$fifo.fifo"
    rm -f "$fifo_path" "$fifo_path.ready"; mkfifo "$fifo_path"
  fi

  backup="$scratch/$label.$(basename "$file")"
  current_file="$path"; current_backup="$backup"
  apply_mutation "$path" "$backup" "$exprs"

  if cmp -s "$path" "$backup"; then
    printf 'MUTATION-DID-NOT-APPLY %s  %s (the pattern no longer matches — fix the mutation, not the case)\n' "$label" "$path"
    not_noticed=$((not_noticed + 1))
    cp "$backup" "$path"; current_file=""; current_backup=""
    return 0
  fi

  if run_case "$case_name" "$fifo_path"; then
    printf 'GREEN  %s  %s   <-- the case did not notice the mutation\n' "$label" "$case_name"
    not_noticed=$((not_noticed + 1))
  else
    printf 'RED    %s  %s   (%s)\n' "$label" "$case_name" "$desc"
    red=$((red + 1))
  fi

  cp "$backup" "$path"
  current_file=""; current_backup=""
  if run_case "$case_name" "-"; then
    printf 'restore %s %s green again\n' "$label" "$case_name"
  else
    printf 'RESTORE-FAILED %s  %s stayed red after the restore\n' "$label" "$case_name"
    failed=$((failed + 1))
  fi
  [ "$fifo_path" = "-" ] || rm -f "$fifo_path" "$fifo_path.ready"
  return 0
}

if [ -n "$probe" ]; then
  case "$probe" in
    no-apply) record="${PROBE_RECORDS[0]}" ;;
    stays-green) record="${PROBE_RECORDS[1]}" ;;
    *) echo "loop-mutations: unknown probe $probe" >&2; exit 2 ;;
  esac
  one_mutation "$record"
  printf '\nprobe %s: red %s · not-noticed %s · failed %s\n' "$probe" "$red" "$not_noticed" "$failed"
  [ "$not_noticed" -eq 0 ] || exit 1
  exit 0
fi

for record in "${MUTATIONS[@]}"; do
  one_mutation "$record"
done

printf '\nred-when-mutated: %s · not-noticed: %s · restore-failures: %s\n' "$red" "$not_noticed" "$failed"
[ "$failed" -eq 0 ] || exit 1
[ "$not_noticed" -eq 0 ] || exit 1
exit 0
