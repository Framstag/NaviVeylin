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
# It restores every file it touches, restores the current one even when it is killed (EXIT trap), and refuses to
# restore from a backup that did not land: the backups live under the project's own `.pi/tmp` unless `TMPDIR`
# says otherwise, because a full `/tmp` (a tmpfs on the measured host) once emptied six engine files — measured
# 2026-10-21, issue #234.
#
# **The bound.** Each record names the file it mutates (`label@fifo@file@case@desc@exprs`), so a change can run
# the records whose file it touched instead of paying the whole set (issue #235: 81 records × two case runs is a
# 21-minute pass): `--files <path> ...` names the files, and `--from-git <base>` derives them from the diff
# against that base plus the working tree's own state, the two sources `tools/loop/gate.sh` reads for "the paths
# the change touches". A filtered run states its selection against the declared set (`selected 7 of 81
# records`), `--list` applies the same filter, and a filter that names no record exits 2 — the harness must never
# pass by doing nothing. Run the file set of a change while iterating, and the full set before a change that owns
# `tools/loop/**` is archived.
#
# **The check.** A record quotes one line, and a line changes. `--check` applies every selected record's
# expressions to a copy of the file the record names and reports each record that no longer changes it — no case
# runs and nothing in the tree is mutated, so it is cheap enough for the project's gate, which owes it for a path
# under `tools/loop/**` (issue #264: `9.9` and `12.2` had quoted `run.sh`'s child-start line as it was before it
# carried the child's own completion record, and both reported `MUTATION-DID-NOT-APPLY` until the next hand-run
# set — two invariants asserted by nothing in the meantime).
#
# Exit: 0 every selected mutation went red and its restore went green, or every selected record applies to the
#       file it names (`--check`); 1 a mutation did not apply, was not noticed, its restore stayed red, or a record
#       is stale (`--check`); 2 usage, a missing prerequisite, a filter that names no record, or a backup/restore
#       that could not be taken safely (nothing is mutated in that case).
#
# Usage: tools/loop-mutations.sh [--list] [--check] [--only <labels>] [--files <path> ...] [--from-git <base>]
#                                [--engine <dir>] [--out <file>] [--timeout <seconds>]
#                                [--probe no-apply|stays-green|empty-backup]
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

list_only=0
check=0
only=""
# The file set, kept space-padded (` path path `) so a membership test is one `case` and no array expansion runs
# under `set -u`. A record's file is a repository-relative path, so the filter compares paths, never basenames.
files=" "
from_git=""
engine="$root/tools/loop"
out=""
case_timeout=240
probe=""
while [ $# -gt 0 ]; do
  case "$1" in
    --list) list_only=1; shift ;;
    --check) check=1; shift ;;
    --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only="${2:-}"; shift 2 ;;
    # Repeatable, and space-separated inside one value, so a caller can pass either shape without a rule.
    --files) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }
      for _wanted in ${2:-}; do files="$files$_wanted "; done; shift 2 ;;
    --from-git) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; from_git="${2:-}"; shift 2 ;;
    --engine) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; engine="${2:-}"; shift 2 ;;
    --out) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; out="${2:-}"; shift 2 ;;
    --timeout) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; case_timeout="${2:-}"; shift 2 ;;
    --probe) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; probe="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) echo "loop-mutations: unknown argument $1" >&2; exit 2 ;;
  esac
done

for tool in bash jq git java javac sed; do
  command -v "$tool" >/dev/null 2>&1 || { echo "loop-mutations: $tool is required" >&2; exit 2; }
done
[ -d "$engine" ] || { echo "loop-mutations: no engine directory at $engine" >&2; exit 2; }
# The two prerequisites below are what *running a case* needs, and `--check` runs none: it applies patterns to
# copies of the files the records name, so it needs the engine and nothing else — which is what lets a caller
# point it at a bare copy of the set.
if [ "$check" -eq 0 ]; then
# The engine reads its dependency as a sibling (`$LOOP_DIR/../issues`), so a copy must keep that layout —
# `--engine <dir>` wants `<dir>` to be the copied `loop` directory, with `issues` beside it.
[ -f "$engine/../issues/lib.sh" ] || {
  echo "loop-mutations: --engine $engine has no sibling issues directory — copy the layout, not just tools/loop" >&2
  exit 2
}
[ -f "$root/tools/loop-selftest.sh" ] || { echo "loop-mutations: the gate tools/loop-selftest.sh is missing" >&2; exit 2; }
fi

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
  # Records 9.9 and 12.2 (the second one below, in the 12.x block) anchor on the *head* of `run_child`'s
  # child-start line — the command and its directory — and not on its tail: the tail gained the child's own
  # completion record (`.status`, issue #227), and a record that quotes the whole line is orphaned by the next
  # edit to any part of it (issue #264).
  '9.9@-@tools/loop/run.sh@state#agentCommandIsTheOnlyIndirection@the single agent-command indirection@s|^  ( cd "$LOOP_ROOT" \&\& $LOOP_AGENT_CMD "$1"; printf|  ( cd "$LOOP_ROOT" \&\& pi --print "$1"; printf|'
  '9.10@-@tools/loop/run.sh@run#reviewerWriteInvalidatesTheRound@the reviewer read-only comparison@s|^      if \[ "$read_only" = "on" \] && \[ "$before" != "$after" \]; then|      if false; then|'
  '9.11@-@tools/loop/prompts/worker.md@state#projectKeysAreReadFromLoopConf@the project facts living in loop.conf@s|{{WORKER_SKILLS}}|openspec-propose openspec-apply-change build-app run-tests revert-check|'
  '12.1@-@tools/loop/worktree.sh@run#commitsWithoutAnAmbientIdentity@the commit is attributed to the run'"'"'s own identity@s|-c user.name="\$who_name" -c user.email="\$who_email" ||'
  '12.2@-@tools/loop/run.sh@run#childStartsInTheProjectRoot@the child started in the project, where the project'"'"'s own policy is resolved@s|^  ( cd "$LOOP_ROOT" \&\& $LOOP_AGENT_CMD "$1"; printf|  ( cd "$worktree" \&\& $LOOP_AGENT_CMD "$1"; printf|'
  '12.3@-@tools/loop/run.sh@run#childWriteOutsideTheWorktreeIsBlocked@the run catching a child write left outside the worktree@s|^    printf '"'"'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran\\n'"'"' >> "$2"$|    : # the mutation carries the write instead|'
  '12.4@-@tools/loop/run.sh@run#reviewerWriteInTheProjectIsDiscarded@the reviewer comparison reading the project tree as well@s|git -C "$worktree" diff; project_state; }|git -C "$worktree" diff; }|'
  '12.5@-@tools/loop/worktree.sh@worktree#reusesABranchThatCarriesNoWorkOfItsOwn@the reuse of a branch that carries nothing of its own@s|^    if \[ -z "\$carried" \] \|\| \[ "\$state" = "landed" \]; then$|    if false; then|'
  '12.6@-@tools/loop/worktree.sh@worktree#refusesABranchCarryingAPreviousAttempt@the refusal of a branch that carries an archived change of its own@s|^    if \[ -z "\$carried" \] \|\| \[ "\$state" = "landed" \]; then$|    if true; then|'
  '12.7@-@tools/loop/worktree.sh@worktree#setsAsideABlockedAttemptAndStartsFresh@the blocked attempt branch renamed aside so the item is worked@s|^    elif \[ "\$state" = "unfinished" \]; then$|    elif false; then|'
  '13.1@-@tools/loop/prescreen.sh@prescreen#aFinishedAttemptBranchIsADecision@the pre-screen asking git for a previous attempt on the item branch prefix@s|^  while IFS= read -r branch; do$|  while false; do|'
  '13.2@-@tools/loop/run.sh@run#leftoverBranchIsRecordedNotDropped@the pre-screen verdicts that need a decision being escalated by the run@s|^  \[ -n "$decision_agent" \] \|\| return 0$|  return 0|'
  '13.3@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the start-time pre-flight on a kept worktree a previous run left dirty@s|^if \[ -d "$worktree" \] && worktree_is_dirty; then$|if false; then|'
  '13.4@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the refusal being recorded under the item its branch marker names@s|^    if \[ "$dry_run" -eq 0 \] && \[ -n "$owner_item" \]; then$|    if false; then|'
  '13.5@-@tools/loop/run.sh@run#resetWorktreeDiscardsWhatItNames@the start-time repair discarding what it named@s|^    git -C "$worktree" reset -q --hard \|\| loop_die "run: could not reset the kept worktree"$|    : # the mutation discards no tracked change|;;s|^    git -C "$worktree" clean -qfd \|\| loop_die "run: could not clean the kept worktree"$|    : # the mutation discards no untracked file|'
    # 13.6 was the lock telling a live run'"'"'s tree from a previous run'"'"'s leftovers. The lock is taken before the
  # kept worktree is read now, so a live run is refused at the lock and never reaches that check; the invariant
  # the record still owns is the refusal itself: a live owner'"'"'s lock is a refusal, not a takeover.
  '13.6@-@tools/loop/state.sh@state#secondRunRefusesWithExit5@the refusal of a live owner'"'"'s lock@s|^      exit "\$LOOP_REFUSE"$|      exit 0 # the mutation accepts a live owner'"'"'s lock|'
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
  '18.1@-@tools/loop/worktree.sh@worktree#refusesABranchWhoseReviewFailed@adoption deciding on the verdict the reviewer left on the branch@s|^  case "\$review_state" in$|  case "" in|'
  '18.2@-@tools/loop/run.sh@run#recordsAFailedReviewOnTheBranch@the run committing the reviewer verdict onto the item branch@s|^        bash "\$LOOP_DIR/worktree.sh" record-review-failure .*$|        : # the verdict is recorded nowhere but this run|'
  # 18.3/18.4 — a round whose reviewer never answered must not read like a pass (issue #262): the run announces
  # the round on the branch before the reviewer starts, and adoption refuses an announcement that holds no
  # answer. The two halves are separate invariants: without 18.4 nothing is announced at all, and the branch
  # carries neither a request nor an answer of the round that is running.
  '18.3@-@tools/loop/worktree.sh@worktree#refusesABranchWhoseReviewRoundNeverAnswered@adoption refusing a round that was announced and never answered@s|review-unanswered|adoptable|'
  '18.4@-@tools/loop/run.sh@run#announcesAndRecordsTheReviewRound@the run announcing the review round on the item branch before its reviewer starts@s|^        bash "\$LOOP_DIR/worktree.sh" record-review-round .*requested .*$|        : # the round is announced nowhere but this run|'
  '25.1@-@tools/loop/lib.sh@worktree#keepsSourcesNotBuildOutput@the symlink refusal that keeps the cleanup inside a kept worktree@s|^  if \[ -L "$target" \]; then$|  if false; then|'
  '25.2@-@tools/loop/loop-clean.sh@clean#reportsWithoutApplyAndRemovesWithIt@the age gate that keeps a scratch tree a live suite may be writing into@s|^  if \[ -z "$(find "$dir" -maxdepth 0 -mmin +"$stale_minutes" 2>/dev/null)" \]; then$|  if false; then|'
  '19.1@-@tools/loop/run.sh@run#reviewBudgetIsARecordedValue@the round budget coming from the recorded value instead of a literal@s|^    round_cap="$max_review_rounds"$|    round_cap=2|'
  '19.2@-@tools/loop/run.sh@run#aCorrectiveRefusalEarnsOneMoreRound@the extra round a corrective refusal earns@s|^        if \[ "$corrective_round_used" -eq 0 \] && \[ "$corrections_only" = "yes" \]; then$|        if false; then|'
  '19.3@-@tools/loop/run.sh@run#escalationCarriesTheDecision@the escalation carrying the reviewer decision@s|^      if \[ -n "$decision_field" \]; then$|      if false; then|'
  # The round a corrective refusal buys is handed the corrections, not the change again (issue #245): the diff's
  # base is that round's own, and the refusing report travels in the prompt. One mutation per half — the case
  # asserts the narrowed range first and the quoted findings second.
  '40.1@-@tools/loop/run.sh@run#aCorrectiveRoundIsHandedTheCorrections@the base the extra round_diff is built from@s|^      write_review_diff "\$diff_base" "\$diff_file"$|      write_review_diff "\$base" "\$diff_file"|'
  '40.2@-@tools/loop/run.sh@run#aCorrectiveRoundIsHandedTheCorrections@the refusing report the extra round reads@s|^          correction_scope=\$(review_correction_scope "\$diff_base" "\$reviewer_report")$|          correction_scope=""|'
  # The section names `{{DIFF_PATH}}`/`{{CHANGE_ROOT}}` itself, so the pair it is substituted from has to be the
  # *first* one: rendered last, the paths the section names survive as literal placeholders and the round that
  # exists to verify corrections points its reviewer at a file called `{{DIFF_PATH}}` (item #245's round-1 review).
  # Two expressions, because the defect is an order: the pair is deleted from the head and appended after the last.
  '40.3@-@tools/loop/run.sh@run#aCorrectiveRoundIsHandedTheCorrections@the correction section substituted before the paths it names@s|^    "CORRECTION_SCOPE=\${2:-}" \\$|    "LOOP_REORDERED=1" \\|;;s|^    "REVIEW_EXCLUDE=\${review_exclude_spec:-none}" "REVIEW_WALL_CLOCK=\${child_max:-2700}"$|& \\\n    "CORRECTION_SCOPE=${2:-}"|'
  '20.1@-@tools/loop/prescreen.sh@prescreen#itemAwaitingADecisionIsNotQueued@`alreadyEscalated` following the label rather than the hold@s|^    if \[ "$awaiting_label" -eq 1 \]; then$|    if true; then|'
  '20.2@-@tools/loop/prescreen.sh@prescreen#escalatedItemKeepsItsBranchAsEvidence@the awaiting clause not overwriting the branch the adoption path reads@s|^  if \[ "$verdict" = "$queued_verdict" \] && \[ "$awaiting_decision" -eq 1 \]; then$|  if [ "$awaiting_decision" -eq 1 ]; then|'
  '21.1@-@tools/loop/prescreen.sh@prescreen#aHoldWithNoDecisionIsNotADecision@a hold with no recorded decision reported as a hold rather than as a decision@s|^      verdict="$held_verdict"$|      verdict="$decision_verdict"|'
  '21.2@-@tools/loop/labels.sh@labels#holdCarriesItsReason@the reason a hold writes travelling with the state as a comment@s|^    comment_only=1$|    comment_only=0|'
  '22.1@-@tools/loop/prescreen.sh@prescreen#harnessItemsLeadTheQueue@the category lead ranking a leading defect ahead of every other item@s#^  | ($lead + $tail) as $order$#  | (($lead + $tail) | sort_by([class_rank($worked), .issue])) as $order#'
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
  # 34.x — the four properties of issue #191's phase lines, one mutation each: the text is streamed while the
  # pre-screen runs, the same bytes are kept, a line has an elapsed field, and no line is ever read back into
  # the state file. Each is exercised by the case that owns its scenario, so a property that stops being true
  # turns exactly one case red.
  '34.1@-@tools/loop/run.sh@run#prescreenAnnouncesItselfBeforeItReturns@the stream that reaches the terminal while the pre-screen runs (issue #191)@s#\| tee "\$prescreen_log"#\| cat > "\$prescreen_log"#'
  '34.2@-@tools/loop/run.sh@run#prescreenAnnouncesItselfBeforeItReturns@the kept copy of the pre-screen text in the state directory (issue #191)@s#\| tee "\$prescreen_log"#\| tee /dev/null#'
  '34.3@-@tools/loop/lib.sh@run#everyPhaseBoundaryIsPrinted@the elapsed field of a phase line (issue #191)@s#"\$(fmt_elapsed \$(( \$(date +%s) - LOOP_PROGRESS_STARTED )))"#""#'
  '34.4@-@tools/loop/lib.sh@run#everyPhaseBoundaryIsPrinted@the rule that a phase line is terminal output and never a state field (issue #191)@s#^  local phase="\$1"; shift$#  local phase="\$1"; shift; jq --arg l "loop: \$phase" '"'"'.run.progressNote = \$l'"'"' "\$LOOP_STATE_FILE" > "\$LOOP_STATE_FILE.n" 2>/dev/null \&\& mv "\$LOOP_STATE_FILE.n" "\$LOOP_STATE_FILE"#'
  '34.5@-@tools/loop/run.sh@run#everyPhaseBoundaryIsPrinted@the report path on the item line, the view a person can read while the item is worked (issue #191)@s# — its report is written to \$item_dir/worker-report\.md##'
  # 35.1–35.3 — a branch is read by patch identity, not by ancestry (issue #228). 35.1 restores the ancestry
  # question in the one helper all three sites ask, which is the defect itself: a branch landed as a commit of
  # the loop's own becomes "1 commit the base does not have" again and the item is refused for ever.
  '35.1@-@tools/loop/lib.sh@worktree#reusesABranchWhoseWorkAlreadyLanded@the patch identity a branch is read by, instead of its commit objects@s|git -C "\$repo" cherry "\$base" "\$branch" 2>/dev/null|git -C "$repo" log --format="+ %h %s" "$base..$branch" 2>/dev/null|'
  # 35.2 — removed with #232's verdict split: the case it named (`prescreen#landedBranchIsNotADecision`) can
  # no longer notice it, because a branch that carries commits and no archived change of its own is *work* now,
  # whatever its patches say. Patch identity still has an owning case and mutation in `lib.sh` (35.1) and in the
  # report (35.3).
  '35.3@-@tools/loop/report.sh@report#landedBranchIsNoAction@the report counting what a branch carries by patch@s|loop_unlanded_commits "\$LOOP_ROOT" "\$base" "\$branch"|git -C "$LOOP_ROOT" log --format="+ %h %s" "$base..$branch" 2>/dev/null|'
  # 36.1 — the item's comments are asked for, so the recorded decision can reach the worker (issue #196). The
  # mutation asks for the six fields the runner asked for before, and the package then states `recorded: none`
  # for an item nobody left undecided. The fixture `gh` filters by `--json` exactly as the real CLI does, which is
  # what makes the omission observable at all.
  '36.1@-@tools/loop/run.sh@run#workPackageCarriesTheRecordedDecision@the comments the run asks the tracker for, so the recorded decision reaches the package@s|loop_issue_json "\$issue" --comments|loop_issue_json "$issue"|'
  # 37.1 — the reviewer's task states what the round may run (issue #236): without the gate in the read-only
  # list, item 3 is answered by running the suite again, which is the 4 min 1 s the item measured.
  '37.1@-@tools/loop/prompts/reviewer.md@prompt#reviewerIsBoundedToReading@the gate named in the read-only list the reviewer is given (issue #236)@s|do not run the gate|do not run that|'
  # 37.2 — item 3 is the line that invited the suite run (issue #236): restored to "is the count a real
  # execution", the task leaves the runner as the way to answer it and the case must notice.
  '37.2@-@tools/loop/prompts/reviewer.md@prompt#reviewerIsBoundedToReading@item 3 asking for the tally the evidence carries instead of a second execution (issue #236)@s|Read the tally it carries; never reproduce it by running the gate\.|Is the count a real execution?|'
  # 38.1 — the worker names the change's surface before it fixes the plan (issue #238): without the step, the plan
  # is estimated against its first file, which is how a "two functions" plan landed as 292 lines over six.
  '38.1@-@tools/loop/prompts/worker.md@prompt#workerNamesTheChangeSurfaceBeforeThePlan@the surface pass that sets the plan estimate (issue #238)@s|Count the surface before the plan is fixed\.|Plan the change first.|'
  # 39.1 — the block names the checks the pathspec owes, read from `gate.sh --plan`, the one place that decides
  # them (issue #246). Without it the worker is back to walking the tree for each check it might owe.
  '39.1@-@tools/loop/measure.sh@measure#namesTheOwedChecksInOneBlock@the owed checks the block names (issue #246)@s|^  while IFS= read -r cmd; do$|  while false; do|'
  # 39.2 — two open changes are named as ambiguous instead of guessed between (issue #246, review round 1): the
  # count that makes them ambiguous is pinned to one, so the block is back to measuring whichever change sorts
  # last while the worker reads a stranger's scenario count.
  '39.2@-@tools/loop/measure.sh@measure#refusesToGuessWhichChangeItMeasures@the count that makes two open changes ambiguous (issue #246)@s|^  open_count=.*$|  open_count=1|'
  # 39.3 — a directory of more tallies than the block prints is not a directory of twenty (issue #246, review
  # round 1): without the line, the cap reads as the whole tree.
  '39.3@-@tools/loop/measure.sh@measure#saysHowManyTalliesItWithheld@the line that says how many tallies the cap withheld (issue #246)@s|^    item TALLY-MORE|    : # the mutation withholds the count|'
  # 41.x — the lock that keeps one run per repository, taken before the run writes anything (measured
  # 2026-10-22: the run that started at 19:34 replaced the state of the run that started at 18:46, merged
  # that run's unfinished item into main and closed it, and was refused at the lock two minutes into a
  # 240-minute budget). One mutation per invariant: the order, the liveness, the report's wording.
  '41.1@-@tools/loop/run.sh@state#aRefusedRunLeavesTheLiveStateAlone@the lock taken before the run writes anything@s|^loop_state lock --owner "\$\$" \|\| exit \$?$|: # the mutation takes no lock at all|'
  '41.2@-@tools/loop/state.sh@state#aDeadOwnersLockIsTakenOver@the liveness of the lock'"'"'s owner@s|^    if \[ -n "\$holder_pid" \] && kill -0 "\$holder_pid" 2>/dev/null; then$|    if true; then|'
  '41.3@-@tools/loop/report.sh@report#aKilledRunIsNotALiveRun@the report telling a run whose process is gone from one that is going@s|^if \[ "\$owner_pid" -gt 0 \] && \[ -z "\$stop_reason" \] && ! kill -0 "\$owner_pid" 2>/dev/null; then$|if false; then|'
  # 42.x — the branches the loop made (measured 2026-10-22: 43 of 53 local branches were already in the
  # base and nothing removed them). One mutation per invariant: the worktree's release, the deletion, the
  # sweep's own question, and the report's second line.
  '42.1@-@tools/loop/worktree.sh@run#aMergedBranchIsRemoved@the worktree released from the branch it holds@s|^    gitw switch --detach >&2 \|\| loop_die "release-branch: could not release the worktree from \$branch"$|    : # the mutation leaves the worktree on the branch|'
  '42.2@-@tools/loop/worktree.sh@run#aMergedBranchIsRemoved@the deletion of the merged branch@s|^  gitr branch -d "\$branch" >&2 \|\| {$|  : # the mutation removes no branch\n  false \|\| {|'
  '42.3@-@tools/loop/loop-clean.sh@clean#sweepsOnlyWhatIsInTheBase@the sweep asking what a branch carries@s|^    if unlanded=\$(loop_unlanded_commits "\$LOOP_ROOT" "\$base_commit" "\$branch"); then$|    if true; then|'
  '42.4@-@tools/loop/report.sh@report#landedBranchesAreNamedAsRemovable@the report naming the branches that can be removed@s|^printf .landed, removable: %s\\n. |printf "nothing removable: %s\\n" |'
  # 43.x — the record check (issue #264): a record quotes one line of one engine file, and nothing compared the two
  # until the gate owed the comparison: #227 gave `run.sh`'s child-start line the child's own completion record,
  # and `9.9`/`12.2` quoted the whole line, so both reported `MUTATION-DID-NOT-APPLY` and the two invariants they
  # own were falsified by nothing. 43.1 is the check's own refusal — without it a stale record is printed and the
  # check exits 0; 43.2 is the profile pair that makes the gate owe it; 43.3 is the shape of the defect: an edit
  # to the engine line the records quote, leaving them pointed at a line that is no longer there.
  # The record names a file *beside* the engine (`../loop-mutations.sh`), the shape records 26.1 and 29.2 already
  # use for a file the engine's own directory does not hold: `tools/loop-mutations.sh` under the `tools/loop/`
  # prefix it is not a member of resolves to `$engine/-mutations.sh`. The labels are 43.x and not 41.x because the
  # lock change took 41.x first (`#265`), and a label names one invariant for good.
  '43.1@-@../loop-mutations.sh@mutation#aStaleRecordIsNamed@the exit that fails a set whose record no longer applies (issue #264)@s|^  if \[ "$stale" -ne 0 \]; then$|  if false; then|'
  '43.2@-@tools/loop/loop.conf@gate#anEnginePathOwesTheRecordCheck@the profile pair that owes the record check for a path under the engine (issue #264)@s#tools/loop/\*|bash tools/loop-mutations\.sh --check;##'
  '43.3@-@tools/loop/run.sh@mutation#everyDeclaredRecordApplies@the anchor two records keep on the child-start line (issue #264)@s|^  ( cd "$LOOP_ROOT" \&\& $LOOP_AGENT_CMD "$1"; printf|  ( cd "$LOOP_ROOT" \&\& $LOOP_AGENT_CMD "$1" \&\& printf|'
  # 44.1 is the cap reaching further than the child's process group. The group is what `set -m` gives the child,
  # and a descendant that called `setsid` is in no group the two signals name: measured 2026-10-22 (issue #272),
  # the mutation harness a child had started kept mutating and restoring the worktree until seven minutes after
  # the run had recorded the child as ended, its last restore leaving the tree dirty for the next run. The sweep
  # is the same `loop_pids_under` test the self-test's teardown and `loop-clean.sh` report with, run over the
  # tree the survivor still names as its working directory.
  '44.1@-@tools/loop/run.sh@run#detachedProcessInTheWorktreeIsEndedByTheCap@the cap ending the processes the child left working in the tree, and naming each one (issue #272)@s|^    sweep=\$(loop_end_pids_under "$LOOP_WORKTREE")$|    sweep=""|'
  # 46.1 is the stream that carries a suite output to the console while the suite runs. Measured 2026-10-23
  # (issue #269): the gate held a suite's whole output in a variable, so a gate that ran for eleven minutes
  # carried two lines and neither an operator nor a worker could tell a long suite from a stopped one. The
  # mutation restores the command substitution and leaves the tally reading the same bytes — only the console
  # goes silent, which is what the case that names it asserts.
  '46.1@-@tools/loop/gate.sh@gate#theSuitesOutputReachesTheConsoleWhileItRuns@the stream that carries a suite output to the console while the suite runs (issue #269)@s|^    \$cmd 2>&1 \| tee "\$stream"$|    out=$($cmd 2>&1); printf %s "$out" > "$stream"|'
  # 47.1 is the two fallbacks that turned a failed suite with no summary into one failing case (issue #268).
  # Measured 2026-10-23: a compiled step that died at configuration (`BUILD FAILED in 21s`, no test task reached its
  # action) was tallied `tests="1" failures="1"`, and the tally the run's own check reads said a test exists where
  # none executed. The mutation puts the fabrication back beside the error count, so the case that names it sees the
  # phantom case and the phantom failure both.
  '47.1@-@tools/loop/gate.sh@gate#aFailingSuiteWithNoSummaryInventsNoTest@the error count that keeps a suite which reported no summary from being tallied as one failing test (issue #268)@s|^  err=0$|  err=0; [ "$rc" -eq 0 ] \|\| { n=1; f=1; }|'
  # 48.x — the driver's compile, on the selection that needs it (issue #261). The labels are 48.x because
  # #273's merge took 49.x and 50.x while this change waited on a decision, and a label names one invariant for
  # good: the change's own dated evidence still prints `RED 49.1` from the run it measured before that merge.
  # 48.1 puts the compile back above the case list, which is where it was before a `--case` run of a shell-only case
  # paid for it (issue #261). It is the one call site a selection cannot skip: the driver reaches it before it knows
  # which cases it runs, so the poisoned `javac` the case installs is invoked whatever the selection is.
  '48.1@-@../loop-selftest.sh@driver#aCaseThatAsksForNoCoreCompilesNothing@the compile at the first case that asks for it, not above the case list (issue #261)@s|^source "$root/tools/loop-cases.sh"$|core_ensure\nsource "$root/tools/loop-cases.sh"|'
  # 48.2 is the other half of 48.1: the marker is what makes the compile *once per suite* rather than once per
  # asking case. Without it a suite of two asking cases is still green — each case compiles again and is handed a
  # class path — so the invariant is invisible to an assertion and only a count catches it: the case installs a
  # `javac` that records every invocation, and the mutation makes it record two.
  '48.2@-@../loop-selftest.sh@driver#theCoreIsCompiledOncePerSuite@the marker that keeps the compile to one per suite, not one per asking case (issue #261)@s|^  if \[ ! -f "$selftest_core_marker" \]; then$|  if true; then|'
  # 49.x — the suite's own selection and its record of what it cost. Measured 2026-10-23: one case under
  # `tools/loop/**` owes the whole self-test (723 s for 163 cases, serial) and the gate said nothing about it, so an
  # item that touched two files paid 723 s on every iteration (#261 paid eight) and the twelve minutes could only
  # be established with a stopwatch and a `ps`. One mutation per invariant: the group filter, the refusal of a
  # filter that names nothing, the duration in the gate's record.
  '49.1@-@../loop-selftest.sh@driver#aGroupFilterRunsOnlyItsOwnCases@the group filter that keeps a suite from running the cases of another group (2026-10-23)@s|^  if \[ -n "\$only_group" \] && ! matches "\$case_name" "\$only_group#\*"; then continue; fi$|  : # the mutation runs every case the suite declares|'
  '49.2@-@../loop-selftest.sh@driver#aFilterThatNamesNoCaseRefuses@the count the refusal of a filter that names no case depends on (2026-10-23)@s|^  printf .%s. "\$n"$|  printf .%s. 1|'
  '49.3@-@tools/loop/gate.sh@gate#theSummaryNamesWhatEachSuiteCost@the wall clock the gate records beside the suite that spent it (2026-10-23)@s|^  took=\$((SECONDS - t0))$|  took=0|'
  '49.4@-@../loop-selftest.sh@driver#aGroupFilterRunsOnlyItsOwnCases@the summary that names the selection a filtered run made (2026-10-23, review round 1 P1)@s|^\[ -n "\$only_group" \] && selection=" --group \$only_group"$|: # the mutation names no selection|'
  # 50.x — a compiled path is read by the tree it sits in, and not by what its name ends in (issue #273). The rule
  # has two halves and either alone leaves the case it names green: 50.1 puts the basename pattern back, which makes
  # an archived evidence tally a compiled file again, and 50.2 removes the matcher's `set -f`, so the pattern
  # anchored to a module's tree is replaced by the names it matched in the working directory and the module's own
  # resource stops owing the build. The labels are 50.x and not 44.x because #272's sweep landed first with 44.1,
  # and a label names one invariant for good; the change's own archived evidence still prints `RED 44.1`/`RED 44.2`
  # from the runs it measured before this merge, which is what a dated log is.
  '50.1@-@tools/loop/loop.conf@gate#anArchivedArtefactOwesNoBuild@the profile reading a compiled path by its tree rather than by its name@s|^LOOP_GATE_COMPILED_PATTERNS="\*/src/\*|LOOP_GATE_COMPILED_PATTERNS="*.xml */src/*|'
  '50.2@-@tools/loop/gate.sh@gate#aModuleResourceOwesTheBuild@the matcher reading an anchored pattern as a pattern@s|\*) set -f|\*) true|'
  # 51.1 — a tally's name is never a dotfile (issue #278). The profile's own command starts `./`, and a name taken
  # straight from the command keeps that leading `.`, so the one tally that records the build is hidden from a plain
  # `ls` and from a `*.xml` glob — the round-3 reviewer of #273 counted five files where the directory held six.
  '51.1@-@tools/loop/gate.sh@gate#aCompiledStepsTallyIsNotHidden@the ./ prefix stripped before the tally is named (issue #278)@s|\${cmd#\./}|\$cmd|'
  # 52.x — the gate reads the profile beside its own engine, and says which profile that was (issue #277). Measured
  # 2026-10-23 finishing #273: the run exported the profile of the project it was started in to every child, so a
  # change whose delta rewrote `LOOP_GATE_COMPILED_PATTERNS` was gated by the rule it was replacing, and the log named
  # no profile at all — the round-3 reviewer refused it, and the repair was a hand-pinned re-run. One mutation per
  # half: 52.1 hands the run's own profile to the child again, 52.2 takes the pin out of the log, and 52.3 makes a
  # pinned profile claim to be the one the engine ships.
  '52.1@-@tools/loop/run.sh@gate#theChildReadsTheProfileOfTheTreeItWorksIn@the run keeping its own profile out of a child that works in another tree (issue #277)@s|^export -n LOOP_CONF$|export LOOP_CONF="${LOOP_CONF:-$LOOP_DIR/loop.conf}"|'
  '52.2@-@tools/loop/gate.sh@gate#theGateNamesTheProfileItRead@the profile the gate read, named in the log it wrote (issue #277)@s|^    say "LOOP-GATE-PROFILE: \$LOOP_CONF_PATH"$|    : # the mutation names no profile|'
  '52.3@-@tools/loop/gate.sh@gate#theGateNamesTheProfileItRead@a pinned profile named as pinned, beside the one the engine ships (issue #277)@s|^  \[ "\$resolved/\$(basename "\$LOOP_CONF_PATH")" = "\$engine/loop.conf" \]$|  true|'
)

# The harness's two probes: they prove the harness's own guards can fail, on a throwaway copy of the engine
# (`tools/loop-mutations-selftest.sh`). A probe is a mutation like any other, with a deliberately wrong or
# deliberately harmless shape.
PROBE_RECORDS=(
  'probe-no-apply@-@tools/loop/state.sh@state#initAndShowRoundTrips@a pattern that matches nothing@s|^# this line is not in the file$|# mutation|'
  'probe-stays-green@-@tools/loop/report.sh@report#headerAndTableShape@a real edit no case in the set asserts@s|^set -uo pipefail$|&\n# a probe as a second line|'
)

# `--from-git <base>` adds the paths a change touched to the file set, from the same two sources the loop's gate
# reads: the diff against the base, and the working tree's own state (a change that is not committed yet).
if [ -n "$from_git" ]; then
  git_paths=$(git diff --name-only "$from_git" 2>/dev/null) || {
    printf 'loop-mutations: --from-git %s is not a base git can diff this working tree against\n' "$from_git" >&2
    exit 2
  }
  status_paths=$(git status --porcelain 2>/dev/null | sed 's/^...//')
  while IFS= read -r _wanted; do
    [ -n "$_wanted" ] || continue
    case "$files" in *" $_wanted "*) continue ;; esac
    files="$files$_wanted "
  done < <(printf '%s\n' "$git_paths" "$status_paths")
fi

# The selection is the record's label against `--only` *and* its file against the file set: the two filters are
# independent, and a record has to pass both to run.
selected() { # selected <label> <file>
  if [ -n "$only" ]; then
    case " $only " in *" $1 "*) ;; *) return 1 ;; esac
  fi
  if [ "$files" != " " ]; then
    case "$files" in *" $2 "*) ;; *) return 1 ;; esac
  fi
  return 0
}

# What a filter selected, against what the harness declares — counted before anything is mutated, because a
# selection of zero records is the one answer that must not look like a pass: a caller who mistyped a path would
# otherwise read exit 0 as "the change's records all held".
declared=${#MUTATIONS[@]}
picked=0
for record in "${MUTATIONS[@]}"; do
  IFS='@' read -r _label _fifo _file _case _desc _exprs <<< "$record"
  selected "$_label" "$_file" && picked=$((picked + 1))
done
if [ "$picked" -eq 0 ] && { [ -n "$only" ] || [ "$files" != " " ]; }; then
  printf 'loop-mutations: the selection names no record of the %s declared — a run that mutates nothing would report success, so this is a refusal (--list shows what each record owns)\n' "$declared" >&2
  exit 2
fi
if [ -n "$only" ] || [ "$files" != " " ]; then
  printf 'loop-mutations: selected %s of %s records' "$picked" "$declared" >&2
  [ -n "$only" ] && printf ' (labels: %s)' "$only" >&2
  [ "$files" != " " ] && printf ' (files:%s)' "${files% }" >&2
  printf '\n' >&2
fi

if [ "$list_only" -eq 1 ]; then
  for record in "${MUTATIONS[@]}"; do
    IFS='@' read -r label _fifo file case_name desc _exprs <<< "$record"
    selected "$label" "$file" || continue
    printf '%s\t%s\t%s\n' "$label" "$case_name" "$desc"
  done
  exit 0
fi

scratch_root="${TMPDIR:-$root/.pi/tmp}"
mkdir -p -- "$scratch_root" 2>/dev/null || { echo "loop-mutations: cannot create the scratch root $scratch_root" >&2; exit 2; }
scratch=$(mktemp -d "$scratch_root/loop-mutations.XXXXXX") || { echo "loop-mutations: cannot create a scratch directory under $scratch_root" >&2; exit 2; }
# Named once, so a caller can see where the backups of the files it is about to mutate went (issue #234).
printf 'loop-mutations: scratch %s\n' "$scratch" >&2
# The transcript goes to stdout and, with `--out`, to a file as well: `exec > >(tee …)` keeps this shell
# the one that counts (a pipeline per report would increment the counters in a subshell and lose them).
if [ -n "$out" ]; then exec > >(tee -a "$out"); fi
current_backup=""
current_file=""

# The two directions of the one thing this harness must not get wrong: the tree it mutates is the tree that
# proves a change, so a backup that did not land (a full filesystem is the measured cause, 2026-10-21) stops the
# harness *before* anything is mutated, and a restore refuses a backup that is empty or does not match what it
# wrote (issue #234).
backup_file() { # backup_file <file> <backup>
  cp -- "$1" "$2" 2>/dev/null || true
  if [ ! -s "$2" ]; then
    printf 'loop-mutations: could not back up %s into %s (a full filesystem is the measured cause) — nothing was mutated\n' \
      "$1" "$2" >&2
    exit 2
  fi
}
restore_file() { # restore_file <backup> <file>
  if [ ! -s "$1" ]; then
    printf 'loop-mutations: refusing to restore %s from a backup that is empty or missing (%s) — the backup did not land, so the file it protects is left alone\n' \
      "$2" "$1" >&2
    exit 2
  fi
  cp -- "$1" "$2" 2>/dev/null || { printf 'loop-mutations: could not restore %s from %s\n' "$2" "$1" >&2; exit 2; }
  cmp -s "$1" "$2" || { printf 'loop-mutations: %s does not match %s after the restore\n' "$2" "$1" >&2; exit 2; }
}

cleanup() {
  # A killed harness must not leave a mutation in the tree: the case that would catch it is the one this
  # file runs, and the next suite run would fail for a reason nobody is looking for. A backup that did not
  # land cannot restore anything, and saying so is all this path may do (issue #234).
  if [ -n "$current_backup" ] && [ -n "$current_file" ]; then
    if [ -s "$current_backup" ]; then
      cp -- "$current_backup" "$current_file" 2>/dev/null ||
        printf 'loop-mutations: the interrupted mutation could not be restored into %s — its backup is %s\n' \
          "$current_file" "$current_backup" >&2
    else
      printf 'loop-mutations: the interrupted mutation has no backup for %s — check it by hand against git\n' \
        "$current_file" >&2
    fi
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT

# `--check`: each selected record's expressions applied to a copy of the file the record names, and reported when
# one of them changes nothing. Reported per record and not per expression — one stale expression is enough to make
# the record mutate less than it declares, and `RED` for a partly applied mutation is the silent half of the same
# lie. `$checked` counts the records whose file exists, so the summary is the set that was actually looked at and
# not the set that was declared.
if [ "$check" -eq 1 ]; then
  checked=0
  stale=0
  for record in "${MUTATIONS[@]}"; do
    IFS='@' read -r label _fifo file _case _desc exprs <<< "$record"
    selected "$label" "$file" || continue
    path="$engine/${file#tools/loop/}"
    if [ ! -f "$path" ]; then
      printf 'MUTATION-PATTERN-NOFILE %s  %s (the record names a file the engine does not have)\n' "$label" "$file" >&2
      stale=$((stale + 1))
      continue
    fi
    probe="$scratch/check.$(printf '%s' "$label" | tr -c 'A-Za-z0-9._-' '_')"
    cp -- "$path" "$probe" 2>/dev/null || {
      printf 'loop-mutations: could not copy %s to check its records — nothing was mutated\n' "$path" >&2
      exit 2
    }
    record_stale=0
    while IFS= read -r expr; do
      [ -n "$expr" ] || continue
      cp -- "$probe" "$probe.prev" || { printf 'loop-mutations: could not copy %s for the check\n' "$probe" >&2; exit 2; }
      sed -i "$expr" "$probe"
      if cmp -s "$probe.prev" "$probe"; then
        record_stale=1
        break
      fi
    done < <(printf '%s\n' "$exprs" | sed 's/;;/\n/g')
    checked=$((checked + 1))
    if [ "$record_stale" -eq 1 ]; then
      printf 'MUTATION-PATTERN-STALE %s  %s (an expression of this record names no line of this file — fix the record, not the case)\n' "$label" "$file" >&2
      stale=$((stale + 1))
    fi
  done
  if [ "$stale" -ne 0 ]; then
    printf 'loop-mutations: %s of %s checked records no longer apply — the set is stale\n' "$stale" "$checked"
    exit 1
  fi
  printf 'loop-mutations: all %s cases passed — every selected record applies to the file it names\n' "$checked"
  exit 0
fi

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
  backup_file "$file" "$backup"
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
  selected "$label" "$file" || return 0

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
    restore_file "$backup" "$path"; current_file=""; current_backup=""
    return 0
  fi

  if run_case "$case_name" "$fifo_path"; then
    printf 'GREEN  %s  %s   <-- the case did not notice the mutation\n' "$label" "$case_name"
    not_noticed=$((not_noticed + 1))
  else
    printf 'RED    %s  %s   (%s)\n' "$label" "$case_name" "$desc"
    red=$((red + 1))
  fi

  restore_file "$backup" "$path"
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
    empty-backup)
      # A backup that did not land must not be copied over the file it protects: this probe has the shape the
      # full /tmp gave the harness on 2026-10-21 (issue #234), and must end the harness with exit 2. The file it
      # protects is the caller's when `LOOP_MUTATIONS_PROBE_VICTIM` names one, so a test can look at it after
      # the harness has exited.
      victim="${LOOP_MUTATIONS_PROBE_VICTIM:-$scratch/empty-backup.victim}"
      printf 'the tree\n' > "$victim" || { echo "loop-mutations: the probe cannot write $victim" >&2; exit 2; }
      : > "$scratch/empty-backup.backup"
      restore_file "$scratch/empty-backup.backup" "$victim"
      printf 'probe empty-backup: the restore was NOT refused (the victim now holds %s bytes)\n' \
        "$(wc -c < "$victim" | tr -d ' ')"
      exit 0
      ;;
    *) echo "loop-mutations: unknown probe $probe" >&2; exit 2 ;;
  esac
  # A probe is the harness's own guard, not a record of the set: a file set must not silently switch it off.
  files=" "
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
