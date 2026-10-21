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
    --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only="${2:-}"; shift 2 ;;
    --engine) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; engine="${2:-}"; shift 2 ;;
    --out) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; out="${2:-}"; shift 2 ;;
    --timeout) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; case_timeout="${2:-}"; shift 2 ;;
    --probe) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; probe="${2:-}"; shift 2 ;;
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
  '9.9@-@tools/loop/run.sh@state#agentCommandIsTheOnlyIndirection@the single agent-command indirection@s|^  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1 < /dev/null &$|  ( cd "$LOOP_ROOT" \&\& pi --print "$1" ) > "$2" 2>\&1 \&|'
  '9.10@-@tools/loop/run.sh@run#reviewerWriteInvalidatesTheRound@the reviewer read-only comparison@s|^      if \[ "$read_only" = "on" \] && \[ "$before" != "$after" \]; then|      if false; then|'
  '9.11@-@tools/loop/prompts/worker.md@state#projectKeysAreReadFromLoopConf@the project facts living in loop.conf@s|{{WORKER_SKILLS}}|openspec-apply-change build-app run-tests revert-check|'
  '12.1@-@tools/loop/worktree.sh@run#commitsWithoutAnAmbientIdentity@the commit is attributed to the run'"'"'s own identity@s|-c user.name="\$who_name" -c user.email="\$who_email" ||'
  '12.2@-@tools/loop/run.sh@run#childStartsInTheProjectRoot@the child started in the project, where the project'"'"'s own policy is resolved@s|^  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1 < /dev/null &$|  ( cd "$worktree" \&\& $LOOP_AGENT_CMD "$1" ) > "$2" 2>\&1 \&|'
  '12.3@-@tools/loop/run.sh@run#childWriteOutsideTheWorktreeIsBlocked@the run catching a child write left outside the worktree@s|^    printf '"'"'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran\\n'"'"' >> "$2"$|    : # the mutation carries the write instead|'
  '12.4@-@tools/loop/run.sh@run#reviewerWriteInTheProjectIsDiscarded@the reviewer comparison reading the project tree as well@s|git -C "$worktree" diff; project_state; }|git -C "$worktree" diff; }|'
  '12.5@-@tools/loop/worktree.sh@worktree#reusesABranchThatCarriesNoWorkOfItsOwn@the reuse of a branch that carries nothing of its own@s|^    if gitw merge-base --is-ancestor "$branch" "$base"; then$|    if false; then|'
  '12.6@-@tools/loop/worktree.sh@worktree#refusesABranchCarryingAPreviousAttempt@the refusal of a branch that carries an archived change of its own@s|^    if \[ "\$state" = "landed" \] \|\| gitw merge-base --is-ancestor "\$branch" "\$base"; then$|    if true; then|'
  '12.7@-@tools/loop/worktree.sh@worktree#setsAsideABlockedAttemptAndStartsFresh@the blocked attempt branch renamed aside so the item is worked@s|^    elif \[ "\$state" = "unfinished" \]; then$|    elif false; then|'
  '13.1@-@tools/loop/prescreen.sh@prescreen#aFinishedAttemptBranchIsADecision@the pre-screen asking git for a previous attempt on the item branch prefix@s|^    if ! git -C "$LOOP_ROOT" merge-base --is-ancestor "$branch" "$run_base" 2>/dev/null; then|    if false; then|'
  '13.2@-@tools/loop/run.sh@run#leftoverBranchIsRecordedNotDropped@the pre-screen verdicts that need a decision being escalated by the run@s|^  \[ -n "$decision_agent" \] \|\| return 0$|  return 0|'
  '13.3@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the start-time pre-flight on a kept worktree a previous run left dirty@s|^if \[ -d "$worktree" \] && \[ ! -e "$run_lock" \] && worktree_is_dirty; then$|if false; then|'
  '13.4@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the refusal being recorded under the item its branch marker names@s|^    if \[ "$dry_run" -eq 0 \] && \[ -n "$owner_item" \]; then$|    if false; then|'
  '13.5@-@tools/loop/run.sh@run#resetWorktreeDiscardsWhatItNames@the start-time repair discarding what it named@s|^    git -C "$worktree" reset -q --hard \|\| loop_die "run: could not reset the kept worktree"$|    : # the mutation discards no tracked change|;;s|^    git -C "$worktree" clean -qfd \|\| loop_die "run: could not clean the kept worktree"$|    : # the mutation discards no untracked file|'
  '13.6@-@tools/loop/run.sh@state#secondRunRefusesWithExit5@the lock telling a live run'"'"'s tree from a previous run'"'"'s leftovers@s|^if \[ -d "$worktree" \] && \[ ! -e "$run_lock" \] && worktree_is_dirty; then$|if [ -d "$worktree" ] \&\& worktree_is_dirty; then|'
  '13.7@-@tools/loop/run.sh@worktree#keepsByDefaultAndRemovesOnRequest@the end-of-run form honoured wherever the run ends@s|^  if \[ "$clean_worktree" -eq 1 \]; then$|  if false; then|'
  '12.8@-@tools/loop/run.sh@run#aSkipClearsTheInProgressMark@the skip exit path clearing the in-progress mark@s|^      loop_labels unpick "$issue" >&2 \|\| true   # the exit path clears the mark$|      : # the mutation keeps the mark|'
  '14.1@-@tools/loop/report.sh@report#escalatedSkipNamesTheLabelWritten@the decision list read from the label the escalation recorded, not the verdict@s|(\.escalatedLabel // "") != ""|(.verdict == $d)|'
  '14.2@-@tools/loop/run.sh@report#escalatedSkipNamesTheLabelWritten@the skip escalation recording the label it wrote@s|^          escalated_label="\$decision_label"   # the skip.s own escalation$|          : # the mutation records no escalation|'
  '14.3@-@tools/loop/run.sh@report#failedReviewEscalationIsNamed@the blocked exit path recording the label its escalation wrote@s|^        escalated_label="\$decision_label"   # the block.s own escalation$|        : # the mutation records no escalation|'
  '14.5@-@tools/loop/run.sh@run#blockedItemIsEscalatedWithItsReason@the blocked exit path escalating the reason the run holds@s|^      if loop_labels escalate "\$issue" --reason .*$|      if false; then|;;s|^        --evidence "\$item_dir, branch fix/issue-\$issue-\$slug" >&2; then$|        :|'
  '14.4@-@tools/loop/report.sh@report#aVerdictAloneIsNoEscalation@the list naming only the items this run escalated@s|\.escalatedLabel // ""|.issue|'
  '15.1@-@tools/loop/prescreen.sh@prescreen#refusalLeavesATraceInTheTracker@the pre-screen writing its refusal to the tracker instead of only to the run@s|^  if \[ "$write" -eq 1 \] && \[ -n "$refusal_labels" \]; then$|  if false; then|'
  '16.1@-@tools/loop/run.sh@run#stopReasonIsPrintedAndReported@the run recording the stop condition `next` named, on every path that leaves the loop@s|^  loop_state stop "$1" >/dev/null 2>&1 .. true$|  : # the reason is printed and not recorded|'
  '16.2@-@tools/loop/run.sh@run#stopReasonIsPrintedAndReported@the run printing the stop condition it was given@s|^  printf .stop: %s.n. "$1"$|  : # the mutation prints no stop line|'
  '16.3@-@tools/loop/report.sh@run#stopReasonIsPrintedAndReported@the report repeating the recorded stop reason@s|^stop_reason=.*$|stop_reason=""|'
  '17.1@-@tools/loop/prescreen.sh@prescreen#itemAwaitingADecisionIsNotQueued@the clause that leaves an item awaiting a decision to its owner@s|^  if \[ "$verdict" = "$queued_verdict" \] && \[ "$awaiting_decision" -eq 1 \]; then$|  if false; then|'
  '17.2@-@tools/loop/run.sh@run#awaitingDecisionIsNotEscalatedAgain@the run not re-escalating an item that already carries the escalation label@s|^    \| select(.*alreadyEscalated.*$|    \| select(true)|'
  '18.1@-@tools/loop/worktree.sh@worktree#refusesABranchWhoseReviewFailed@adoption reading the verdict the reviewer left on the branch@s|^  refused_round=.*$|  refused_round=""|'
  '18.2@-@tools/loop/run.sh@run#recordsAFailedReviewOnTheBranch@the run committing the reviewer verdict onto the item branch@s|^      bash "$LOOP_DIR/worktree.sh" record-review-failure .*$|      : # the verdict is recorded nowhere but this run|'
  '25.1@-@tools/loop/lib.sh@worktree#keepsSourcesNotBuildOutput@the symlink refusal that keeps the cleanup inside a kept worktree@s|^  if \[ -L "$target" \]; then$|  if false; then|'
  '25.2@-@tools/loop/loop-clean.sh@clean#reportsWithoutApplyAndRemovesWithIt@the age gate that keeps a scratch tree a live suite may be writing into@s|^  if \[ -z "$(find "$dir" -maxdepth 0 -mmin +"$stale_minutes" 2>/dev/null)" \]; then$|  if false; then|'
  '19.1@-@tools/loop/run.sh@run#reviewBudgetIsARecordedValue@the round budget coming from the recorded value instead of a literal@s|^    round_cap="$max_review_rounds"$|    round_cap=2|'
  '19.2@-@tools/loop/run.sh@run#aCorrectiveRefusalEarnsOneMoreRound@the extra round a corrective refusal earns@s|^        if \[ "$corrective_round_used" -eq 0 \] && \[ "$corrections_only" = "yes" \]; then$|        if false; then|'
  '19.3@-@tools/loop/run.sh@run#escalationCarriesTheDecision@the escalation carrying the reviewer decision@s|^      if \[ -n "$decision_field" \]; then$|      if false; then|'
  '20.1@-@tools/loop/prescreen.sh@prescreen#itemAwaitingADecisionIsNotQueued@`alreadyEscalated` following the label rather than the hold@s|^    if \[ "$awaiting_label" -eq 1 \]; then$|    if true; then|'
  '20.2@-@tools/loop/prescreen.sh@prescreen#escalatedItemKeepsItsBranchAsEvidence@the awaiting clause not overwriting the branch the adoption path reads@s|^  if \[ "$verdict" = "$queued_verdict" \] && \[ "$awaiting_decision" -eq 1 \]; then$|  if [ "$awaiting_decision" -eq 1 ]; then|'
  '21.1@-@tools/loop/prescreen.sh@prescreen#aHoldWithNoDecisionIsNotADecision@a hold with no recorded decision reported as a hold rather than as a decision@s|^      verdict="$held_verdict"$|      verdict="$decision_verdict"|'
  '21.2@-@tools/loop/labels.sh@labels#holdCarriesItsReason@the reason a hold writes travelling with the state as a comment@s|^    comment_only=1$|    comment_only=0|'
  '22.1@-@tools/loop/prescreen.sh@prescreen#harnessItemsLeadTheQueue@the category key ranking a leading category ahead of every other category@s|sort_by(\[._lead, ._rank, .issue\])|sort_by([._rank, .issue])|'
  '20.3@-@tools/loop/run.sh@run#stopReasonIsPrintedAndReported@the run recording the reason it stopped on every exit path@s|^  loop_state stop "$1" >/dev/null 2>&1 .. true$|  : # the reason is printed and not recorded|'
  '20.4@-@tools/loop/run.sh@run#aZeroRoundBudgetIsRefused@the refusal of a review budget that never runs@s|^case "$max_review_rounds" in .*$|: # the mutation accepts a budget that never runs|'
  '23.1@-@tools/loop/run.sh@run#childOutlastingTheWallClockIsBlocked@the wall-clock that ends a child instead of waiting for it for ever@s|^    if \[ \$(( \$(date +%s) - started )) -ge "\$cap" \]; then$|    if false; then|'
  '24.1@-@tools/loop/run.sh@run#hostBelowTheFloorBlocksTheItemAndStops@the host read that records a starved item instead of starting it (issue #209)@s|^  if \[ -n "\$free_mib" \] && \[ "\$free_mib" -lt "\$min_free_mib" \]; then$|  if false; then|'
  # The adapter sits beside the engine (`tools/pi-child-events.sh`, named by `LOOP_AGENT_CMD`), so its record
  # reaches it through `../` and resolves against the engine directory like every other file here. One string
  # in place of two argv words is the whole defect (measured 2026-10-20): the agent rejects the option, the
  # child dies in seconds, and the run reads that as an item that blocked.
  '26.1@-@../pi-child-events.sh@adapter#modeFlagIsItsOwnArgument@the mode flag handed to the child as two argv words, not one (measured 2026-10-20)@s|^mode=(--mode json)$|mode="--mode json"|'
  # 26.2 reaches the guard through the engine directory it lives in. Removing it hands every child that runs
  # the loop's tooling in another tree the running run's state path again (issue #214).
  '26.2@-@tools/loop/lib.sh@state#inheritedStatePathOutsideTheTreeIsIgnored@the guard that ignores a run identity named from outside this tree (issue #214)@s|^  if loop_path_outside_tree "\$LOOP_STATE_FILE" "\$LOOP_ROOT"; then LOOP_STATE_FILE=""; fi$|  : # the mutation keeps the inherited path|'
  # The classification that tells a child which never started from one that worked the item and failed: without
  # it every non-zero status is an item fault again, and one environment fault costs the run a decision slot
  # per item and stops it on "two consecutive items ended blocked" (measured 2026-10-20, issue #212).
  '27.1@-@tools/loop/run.sh@run#childThatNeverStartedIsTheRunsFault@the classification that keeps a child which never started off the item (issue #212)@s|^  if \[ "\$worker_exit" -ne 0 \] && \[ -z "\$verdict_claim" \] && \[ -z "\$timeout_claim" \] && \[ "\$head_now" = "\$base" \]; then$|  if false; then|'
  # 27.x — the process group a layer of the loop started, and the process the leftover report reads (issue #213).
  # The group kill is implemented once, in `lib.sh`, because the suite and the run must not drift on *how* they
  # end what they started (D16): 28.1 is that one implementation, 28.3 is the run's call site.
  '28.1@-@tools/loop/lib.sh@clean#interruptedSuiteEndsItsProcessGroup@the one implementation of ending a process group a layer of the loop started@s#^  kill -s TERM -- -"\$pgid" 2>/dev/null || return 0$#  return 0#'
  '28.2@-@tools/loop/loop-clean.sh@clean#reportsAndEndsAProcessLeftInAScratchTree@the report reading a process working directory inside a tree it removes@s|^  done <<< "\$(leftover_pids "\$dir")"$|  done <<< ""|'
  '28.3@-@tools/loop/run.sh@run#interruptedRunEndsTheChildItStarted@the run trap ending the child it started, not only the run@s|^  loop_end_group "\$active_child"$|  : # the mutation ends the run and leaves its child|'
  # 29.x — the decision queue holds only what a person can answer (issue #225): a block states its own question,
  # and a closure takes the label that asked for a decision off the item it ends.
  '29.1@-@tools/loop/run.sh@run#blockedItemAsksItsOwnQuestion@the question a block asks when the reviewer wrote none (issue #225)@s|^\*\*The decision:\*\* retry the item, or abandon it.*$|:|'
  '29.2@-@../issues/close.sh@labels#closeRemovesTheDecisionLabel@the closure taking the decision label off the item it ends (issue #225)@s|^    remove_args=(--remove-label "\$decision_label_closed")$|    remove_args=()|'
  # 30.x — a falsified skip is a finding, not a question (openspec change `falsified-items-close-in-the-loop`):
  # without the verdict the item goes back to the escalation it used to take.
  '30.1@-@tools/loop/run.sh@run#falsifiedSkipClosesWithItsMeasurement@the verdict that tells a measured falsification from an ordinary skip@s|^    outcome="falsified"$|    outcome="skipped"|'
  # The claim is read again after the review (openspec task 1.4): without the re-read, a falsification whose
  # tree moved during a corrective round is still closed as mis-diagnosed.
  '30.2@-@tools/loop/run.sh@run#falsificationWithAChangedTreeIsBlocked@the second read of the tree after the review of a falsification@s|^    if \[ "\$head_now" != "\$base" \]; then$|    if false; then|'
  # Without the retry, an answer cut on its way to the file is read as a refusal again: the round buys a second
  # worker and the item is blocked and escalated although every scenario in the report passed (issue #224).
  '31.1@-@tools/loop/run.sh@run#aCutReportIsRetriedInsteadOfRefused@the retry of a report that arrived cut on its way to the file (issue #224)@s|^      if \[ -z "\$(report_field "\$reviewer_report" LOOP-TIMEOUT)" \] &&$|      if false \&\&|'
  # 32.1 — a base that moved under the run stops it with a reason; the silent exit is what the case catches.
  '32.1@-@tools/loop/run.sh@run#staleBaseStopsTheRunWithItsReason@the stop that records why a stale base ended the run@s|^      \*"base moved"\*) stop_run "the base moved: main advanced|      *"base moved"*) : "the base moved: main advanced|'
  # 33.x — a branch is decided by what it *carries* (issue #232). 33.1 is the credit: without it an archived
  # change another item's commit added is the branch's own, which is how #230's refusal came to name an
  # unrelated change. 33.2 is the verdict: a blocked attempt is work, not a question nobody asked.
  '33.1@-@tools/loop/worktree.sh@worktree#doesNotAdoptAChangeCreditedToAnotherIssue@the commit subject that credits an archived change to this branch and its issue (issue #232)@s|^  gitr log -1 --format=%s "\$added" 2>/dev/null \| grep -qF "(#\$issue)" \|\| return 0$|  : # the mutation credits the branch with any change it holds|'
  '33.2@-@tools/loop/prescreen.sh@prescreen#aBlockedAttemptBranchIsWork@the verdict that reads a blocked attempt as work instead of as a decision (issue #232)@s|^        unfinished)$|        unfinished-set-aside)|'
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
