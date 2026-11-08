#!/usr/bin/env bash
# loop-mutations.sh — the mutation harness for a **shell gate**: one mutation per invariant, the named case
# must go red, restore, the case must go green again.
#
# `.pi/skills/revert-check` owns the rule (*one mutation per check*, a mutation that lands, a failure for the
# right reason, an exact restore, a green re-run); this file is the mechanical form of it for the loop, whose
# gate is `tools/loop/tests/loop-selftest.sh` rather than Gradle — so there is no cache to force and no XML to quote, and
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
# Usage: tools/loop/tests/loop-mutations.sh [--list] [--check] [--only <labels>] [--files <path> ...] [--from-git <base>]
#                                [--engine <dir>] [--out <file>] [--timeout <seconds>]
#                                [--probe no-apply|stays-green|empty-backup]
set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)

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
[ -f "$root/tools/loop/tests/loop-selftest.sh" ] || { echo "loop-mutations: the gate tools/loop/tests/loop-selftest.sh is missing" >&2; exit 2; }
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
  '9.4@-@tools/loop/run.sh@labels#trapRemovesInProgressOnSignal@the signal trap that removes the in-progress label@s|^      loop_labels unpick "$in_progress" >&2 \|\| true$|      : # the mutation keeps the in-progress label|'
  '9.5@-@tools/loop/run.sh@run#childClaimWithoutCommitIsBlocked@the run verifying instead of trusting the child@s|^  elif \[ "\$last_exit" -ne 0 \]; then|  elif false; then|;;s|^  elif \[ "\$verdict_claim" = "skipped" \]; then|  elif false; then|;;s|^  elif \[ "\$head_now" = "\$base" \]; then|  elif false; then|;;s|^  elif \[ -z "\$change" \]; then|  elif false; then|;;s|^  elif \[ -n "\$LOOP_GATE_CMD" \] && \[ "\$gate_found" -eq 0 \]; then|  elif false; then|;;s|^  elif \[ "\$cited_evidence" -eq 1 \] && \[ "\$carried" -eq 0 \]; then|  elif false; then|'
  '9.6@-@tools/loop/run.sh@run#decisionCapRefusesToStart@the decision cap that stops a run@s|^if \[ "${open_decisions:-0}" -ge "$max_decisions" \]; then|if false; then|'
  '9.7@-@tools/loop/run.sh@run#mayCloseNoLeavesTheIssueOpen@the closure opt-out@s|^      if \[ "$may_close" = "no" \]; then close_args+=(--no-close); fi$|      : # the mutation closes anyway|'
  '9.8@-@tools/loop/worktree.sh@worktree#oneBranchPerItemOffTheBase@one branch per item@s|switch -c "$branch" "$base"|switch -c "fix/loop-branch" "$base"|'
  # Records 9.9 and 12.2 (the second one below, in the 12.x block) anchor on the *head* of `run_child`'s
  # child-start line — the command and its directory — and not on its tail: the tail gained the child's own
  # completion record (`.status`, issue #227), and a record that quotes the whole line is orphaned by the next
  # edit to any part of it (issue #264).
  '9.9@-@tools/loop/run.sh@state#agentCommandIsTheOnlyIndirection@the single agent-command indirection@s|^  ( cd "$LOOP_ROOT" \&\& $child_cmd "$1"; printf|  ( cd "$LOOP_ROOT" \&\& pi --print "$1"; printf|'
  '9.10@-@tools/loop/run.sh@run#reviewerWriteInvalidatesTheRound@the reviewer read-only comparison@s|^      if \[ "$read_only" = "on" \] && \[ "$before" != "$after" \]; then|      if false; then|'
  '9.11@-@tools/loop/prompts/worker.md@state#projectKeysAreReadFromLoopConf@the project facts living in loop.conf@s|{{WORKER_SKILLS}}|openspec-propose openspec-apply-change build-app run-tests revert-check|'
  # 9.12 is issue #787's invariant: the cap's refusal written as the run's own stop reason. The record removes the
  # `stop_run` call and leaves the arm's console text in place, so the case fails on the reason it reads out of
  # `run.stop` — not on the refusal firing, which 9.6 owns.
  '9.12@-@tools/loop/run.sh@run#theDecisionCapRecordsItsStopReason@the cap'"'"'s refusal written as the run'"'"'s stop reason@s|^  stop_run "the decision cap refuses the start: |  : "the decision cap refuses the start: |'
  # 9.13 is issue #755's invariant: the signal the trap took written as the run's own stop. The record removes only the
  # `loop_state stop` write, so the HUP case fails on the reason it reads out of `run.stop` and not on the trap, the
  # label or the lock, which the case asserts around it.
  '9.13@-@tools/loop/run.sh@run#aHupEndedRunRecordsItsStopAndUnpicksItsItem@the run recording the signal it took as its own stop (issue #755)@s|^    loop_state stop "interrupted by SIG\${signal_taken}" >&2 \|\| true$|    : # the mutation records no signal|'
  '12.1@-@tools/loop/worktree.sh@run#commitsWithoutAnAmbientIdentity@the commit is attributed to the run'"'"'s own identity@s|-c user.name="\$who_name" -c user.email="\$who_email" ||'
  '12.2@-@tools/loop/run.sh@run#childStartsInTheProjectRoot@the child started in the project, where the project'"'"'s own policy is resolved@s|^  ( cd "$LOOP_ROOT" \&\& $child_cmd "$1"; printf|  ( cd "$worktree" \&\& $child_cmd "$1"; printf|'
  '12.3@-@tools/loop/run.sh@run#childWriteOutsideTheWorktreeIsNoted@the run recording a child write left outside the worktree@s|^    outside_write_line "$before" "$after" >> "$2"$|    : # the mutation carries the write instead|'
  '12.4@-@tools/loop/run.sh@run#strictOutsideWriteDiscardsTheReviewRound@the round discarded on a difference the run can place or was told to judge@s|^      if outside_write_decides; then$|      if false; then|'
  '12.9@-@tools/loop/run.sh@run#strictOutsideWriteBlocksTheItem@the strict run turning the difference into a verdict about the item@s|^  elif outside_write_decides; then$|  elif false; then|'
  '12.10@-@tools/loop/run.sh@run#aWriterTheRunCanPlaceBlocksTheItem@the reading taken while the child runs, which is what ties a write to it@s|^          outside_write_seen "$before" "$now" "$child" "$hb_pid" > "$2.outside-write" 2>/dev/null \|\| true$|          : # the mutation takes no reading while the child runs|'
  # 12.11 is issue #288's second half: without it, the run's own child chain — started in the project by
  # `run_child`, so it stands there for the whole item — marks every difference in that tree, and a peer's write
  # decides an item whose work was never touched. Its pair 12.12 (`for pid in $(loop_tree_pids …)` replaced by the
  # host-wide sweep) was **removed** by issue #673: `run#aWriterTheRunCanPlaceBlocksTheItem` is the only case that
  # could notice it, and it no longer can — that sweep is a `readlink` for every process on the host (~1 s where #288
  # measured it) and the case's writer stands 10 s beside an 8 s write, so the sweep finds the same writer the child's
  # own tree does. Measured 2026-10-26 on the repaired tree, with the record restored verbatim for the one run:
  # `GREEN 12.12 … the case did not notice the mutation` (`evidence/mutation-12.12.log` of the archived change). A
  # record a case cannot notice is not evidence (the rule this file's `13.x` records follow). The ordering stays in
  # `run.sh` and its reason stays in `guidelines/Backlog.md` §7; what it lost is a case, not the behaviour.
  '12.11@-@tools/loop/run.sh@run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem@the child chain the run itself started in the project not being a writer@s|^    pid_left_the_worktree "$pid" "$LOOP_WORKTREE" \|\| continue$|    : # the mutation marks every process of the tree that stands in the project|'
  # 12.13 is the run's own prerequisite (issue #703): without it a host whose `pgrep` is absent records every
  # difference in the project's own tree and places none, and the item keeps the outcome its work earned while the
  # deciding half of the rule was never enforced — the suite refuses such a host, a hand-started run does not.
  '12.13@-@tools/loop/run.sh@run#aHostWithoutThePlacementReadingIsRefused@the run refusing a host whose placement reading cannot answer@s|^if ! command -v pgrep >/dev/null 2>&1; then$|if false; then|'
  '12.5@-@tools/loop/worktree.sh@worktree#reusesABranchThatCarriesNoWorkOfItsOwn@the reuse of a branch that carries nothing of its own@s|^    if \[ -z "\$carried" \] \|\| \[ "\$state" = "landed" \]; then$|    if false; then|'
  '12.6@-@tools/loop/worktree.sh@worktree#refusesABranchCarryingAPreviousAttempt@the refusal of a branch that carries an archived change of its own@s|^    if \[ -z "\$carried" \] \|\| \[ "\$state" = "landed" \]; then$|    if true; then|'
  '12.7@-@tools/loop/worktree.sh@worktree#setsAsideABlockedAttemptAndStartsFresh@the blocked attempt branch renamed aside so the item is worked@s|^    elif \[ "\$state" = "unfinished" \] \|\| \[ "\$state" = "rework-cut" \]; then$|    elif false; then|'
  '13.1@-@tools/loop/prescreen.sh@prescreen#aFinishedAttemptBranchIsADecision@the pre-screen asking git for a previous attempt on the item branch prefix@s|^  while IFS= read -r branch; do$|  while false; do|'
  '13.2@-@tools/loop/run.sh@run#leftoverBranchIsRecordedNotDropped@the pre-screen verdicts that need a decision being escalated by the run@s|^  \[ -n "$decision_agent" \] \|\| return 0$|  return 0|'
  '13.3@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the start-time pre-flight on a kept worktree a previous run left dirty@s|^if \[ -d "$worktree" \] && worktree_is_dirty; then$|if false; then|'
  '13.4@-@tools/loop/run.sh@run#dirtyKeptWorktreeIsARecordedRefusal@the refusal being recorded under the item its branch marker names@s|^    if \[ "$dry_run" -eq 0 \] && \[ -n "$owner_item" \]; then$|    if false; then|'
  '13.5@-@tools/loop/run.sh@run#resetWorktreeDiscardsWhatItNames@the start-time repair discarding what it named@s|^  git -C "$worktree" reset -q --hard \|\| loop_die "run: could not reset the kept worktree"$|  : # the mutation discards no tracked change|;;s|^  git -C "$worktree" clean -qfd \|\| loop_die "run: could not clean the kept worktree"$|  : # the mutation discards no untracked file|'
  '13.8@-@tools/loop/run.sh@run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard@the repair syncing the submodule its own verbs cannot reach@s|^  if \[ -n "\$(loop_conf_value LOOP_SUBMODULE_PATH)" \] && \[ -f "\$worktree/.gitmodules" \]; then$|  if false; then|'
  '13.9@-@tools/loop/run.sh@run#resetWorktreeNamesTheDirtItCannotDiscard@the repair naming what it could not discard instead of announcing a clean tree@s|^  if \[ -n "\$reset_after" \]; then$|  if false; then|'
  # 13.10 is the one distinction the mid-run refusal cannot read from the tree (issue #664): dirt this run ended
  # itself is already written to the item's patch, so that tree may be discarded and the start retried — the dirt
  # of another item is not, and still stops the run (`run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`).
  '13.10@-@tools/loop/run.sh@run#theStartAfterTheRunsOwnEndedChildIsNotRefused@the run discarding the dirt its own ended child left, the record being in place@s|^        if own_leftovers_of "\$owner_item" .*$|        if false; then|'
  # 13.11 is the reason a retry answered with, which is not the dirt it was retried over (issue #664, round 2): the
  # retry's line was printed and dropped, so a start refused for its branch was recorded as "the worktree is not
  # clean" with an empty list of files, on a tree the run had just cleaned itself.
  '13.11@-@tools/loop/run.sh@run#aRetriedStartStopsWithItsOwnReason@the retry'"'"'s own refusal written down instead of the dirt it was retried over@s|^          case "\$start_out" in$|          case "not clean" in|'
  # 13.12 is the reused worktree's own module checkout: the switch moves the gitlink, so without it the tree
  # `create` just branched is dirty before any item starts and `start-item` refuses it under no item (issue #309).
  '13.12@-@tools/loop/worktree.sh@worktree#checksOutTheNewPinWhenItReusesTheWorktree@the reused worktree placed on the pin the new base records@s|^    sync_submodule worktree$|    : # the mutation leaves the module on the previous pin|'
  # 13.13 is the sweep's destructive half (issue #239): `prune-landed --apply` runs unattended at the run's end and
  # deletes a ref, so the reading that keeps a branch the base does not carry is what stands between the sweep and
  # a branch that is somebody's only copy of its work. The case's first assertion after the run is the one that
  # fails: the branch that carries work is gone.
  '13.13@-@tools/loop/worktree.sh@run#theEndSweepsTheLandedBranchesItMade@the refusal that keeps a branch the base does not carry@s|^    if ! branch_is_landed "\$branch" "\$base"; then$|    if false; then|'
  # 13.14 is the pin a loop worktree's module cannot fetch (measured 2026-10-28): the module inside a worktree is a clone of
  # its own with the project's URL, so a pin only the root tree's module holds — a bump landed before its submodule
  # branch was pushed — leaves the module on its old pin and the superproject dirty under no item.
  '13.14@-@tools/loop/worktree.sh@worktree#placesAPinOnlyTheRootCheckoutHolds@the pin the module cannot fetch placed from the root checkout@s|^  if fetch_pin_from_root "\$path" "\$(submodule_pin "\$path")" &&$|  if false \&\&|'
  # 13.15 is the same dirt met mid-run (measured 2026-10-28): the module on another pin is not work, so the run places the pin
  # the base records and starts the item, instead of ending under dirt no item owns.
  '13.15@-@tools/loop/run.sh@run#placesTheModuleAndGoesOnWhenTheOnlyDirtIsThePin@the module-only dirt placed and the start retried instead of the run stopped@s|^  elif \[ "\$dry_run" -eq 0 \] && submodule_only_dirt "\$leftovers" && place_submodule_pin; then$|  elif false; then|'
  # 13.16 is the pre-flight that catches the same failure at a run's start (measured 2026-10-28): a base pinning a commit no
  # clone this host reads is refused before the pre-screen is paid for, with the push that publishes it named.
  '13.16@-@tools/loop/worktree.sh@run#aPinNoCloneHoldsIsRefusedBeforeThePrescreen@the pre-flight refusing a base pin no clone this host holds@s|^  \[ -n "\$path" \] && \[ -f "\$LOOP_ROOT/.gitmodules" \] \|\| return 0$|  return 0|'
  # 13.17 is the remote arm's destructive half (issue #763): the sweep now deletes a ref on a shared remote, and the
  # one reading nobody else can make is which head is an open pull request — deleting it closes a review. The case's
  # first assertion after the read-only sweep is the one that fails: the ref the pull request is disappeared.
  '13.17@-@tools/loop/worktree.sh@run#theRemoteSweepKeepsAnOpenPullRequest@the refusal that keeps the head of an open pull request@s|^    case " \$pr_heads " in$|    case "" in|'
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
  '14.6@-@tools/loop/run.sh@run#aFailedEscalationWriteIsNamed@the blocked exit path recording the write the tracker refused@s|^        label_failures=.*$|        : # the mutation swallows the refused escalation|'
  '14.7@-@tools/loop/run.sh@run#aFailedUnpickIsNamed@the blocked exit path recording the in-progress mark it could not clear@s|^      loop_labels unpick "\$issue" >&2 \|\| label_failures="unpick"$|      loop_labels unpick "\$issue" >&2 \|\| true|'
  '14.8@-@tools/loop/report.sh@run#aFailedEscalationWriteIsNamed@the report naming the item whose label write the tracker refused@s|^  actions\[.*="set #\$issue.*$|  : # the mutation names no unwritten label|'
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
  '40.3@-@tools/loop/run.sh@run#aCorrectiveRoundIsHandedTheCorrections@the correction section substituted before the paths it names@s|^    "CORRECTION_SCOPE=\${2:-}" "PRE_GATE=\${3:-}" \\$|    "LOOP_REORDERED=1" \\|;;s|^    "REVIEW_EXCLUDE=\${review_exclude_spec:-none}" "REVIEW_WALL_CLOCK=\${child_max:-2700}"$|& \\\n    "CORRECTION_SCOPE=${2:-}" "PRE_GATE=${3:-}"|'
  '20.1@-@tools/loop/prescreen.sh@prescreen#itemAwaitingADecisionIsNotQueued@`alreadyEscalated` following the label rather than the hold@s|^    if \[ "$awaiting_label" -eq 1 \]; then$|    if true; then|'
  '20.2@-@tools/loop/prescreen.sh@prescreen#escalatedItemKeepsItsBranchAsEvidence@the awaiting clause not overwriting the branch the adoption path reads@s|^  if \[ "$verdict" = "$queued_verdict" \] && \[ "$awaiting_decision" -eq 1 \]; then$|  if [ "$awaiting_decision" -eq 1 ]; then|'
  '21.1@-@tools/loop/prescreen.sh@prescreen#aHoldWithNoDecisionIsNotADecision@a hold with no recorded decision reported as a hold rather than as a decision@s|^      verdict="$held_verdict"$|      verdict="$decision_verdict"|'
  '21.2@-@tools/loop/labels.sh@labels#holdCarriesItsReason@the reason a hold writes travelling with the state as a comment@s|^    comment_only=1$|    comment_only=0|'
  '21.3@-@tools/loop/run.sh@run#staleSkipAsksItsQuestion@the escalation a stale skip owes instead of a bare hold nobody can answer@s|^        \|\| \[ "\$skip_verdict" = "falsified" \] \|\| \[ "\$skip_verdict" = "stale" \]; then$|        \|\| [ "$skip_verdict" = "falsified" ]; then|'
  '22.1@-@tools/loop/prescreen.sh@prescreen#harnessItemsLeadTheQueue@the category lead ranking a leading defect ahead of every other item@s#^  | ($lead + $tail) as $ranked$#  | (($lead + $tail) | sort_by([class_rank($worked), .issue])) as $ranked#'
  # The pin is the queue's one per-item override (capability `loop-queue-pin`): without the lift a pinned item keeps
  # the rank the class and category keys give it, so a pinned harness improvement sits at the tail the interval puts
  # it in and the case sees no rank 1. One expression, because the whole of the pin is that one select.
  '259.1@-@tools/loop/prescreen.sh@prescreen#aPinnedItemLeadsTheQueue@the pin lifting a pinned item ahead of the category lead@s#| map(select(.pinned)) |#| map(select(.pinned and false)) |#'
  # The rank is the order the run works (issue #824's first live run started #823, rank 29, while a next-up item stood
  # at rank 1): the records leave the pre-screen sorted by the rank the previous stage computed.
  '260.1@-@tools/loop/prescreen.sh@prescreen#theQueueIsWrittenInRankOrder@the sort that makes the rank the order the run works@s#^records=$(printf .%s. "$ranked" | jq -c .sort_by.*$#records="$ranked"#'
  '20.3@-@tools/loop/run.sh@run#stopReasonIsPrintedAndReported@the run recording the reason it stopped on every exit path@s|^  loop_state stop "$1" >/dev/null 2>&1 .. true$|  : # the reason is printed and not recorded|'
  '20.4@-@tools/loop/run.sh@run#aZeroRoundBudgetIsRefused@the refusal of a review budget that never runs@s|^case "$max_review_rounds" in .*$|: # the mutation accepts a budget that never runs|'
  '23.1@-@tools/loop/run.sh@run#childOutlastingTheWallClockIsCut@the wall-clock that ends a child instead of waiting for it for ever@s|^    if \[ \$(( now - started )) -ge "\$cap" \]; then$|    if false; then|'
  # 23.2 is issue #227's bound: the run's own deadline ends a child instead of the cap alone. The `stop_run` the
  # same arm writes has no record of its own: on the ordinary path `next` overwrites it with the deadline it names
  # anyway, so a mutation of that line goes unnoticed — its value is the run killed between the child and `next`,
  # which no case reaches deterministically.
  '23.2@-@tools/loop/run.sh@run#childIsEndedAtTheRunsDeadline@the deadline that ends a child outliving the run@s|^    if \[ "\$deadline_epoch" -gt 0 \] \&\& \[ "\$now" -ge "\$deadline_epoch" \]; then$|    if false; then|'
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
  # it every unread item is an item fault again, and one environment fault costs the run a decision slot per item
  # and stops it on "two consecutive items ended blocked" (measured 2026-10-20, issue #212). Two shapes assert it,
  # because the status is not what the classification may read: an adapter that refused the child's argv leaves
  # exit 1 and a complaint (#212), a provider that refused every call leaves exit 0 and a 0-byte answer (#742).
  '27.1@-@tools/loop/run.sh@run#childThatNeverStartedIsTheRunsFault@the classification that keeps a child which never started off the item (issue #212)@s|^  if \[ -z "\$verdict_claim" \] && \[ -z "\$timeout_claim" \] && \[ -z "\$empty_tree_reading" \] && \[ "\$head_now" = "\$base" \]; then$|  if false; then|'
  '27.2@-@tools/loop/run.sh@run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault@the same classification read from the answer rather than the status (issue #742)@s|^  if \[ -z "\$verdict_claim" \] && \[ -z "\$timeout_claim" \] && \[ -z "\$empty_tree_reading" \] && \[ "\$head_now" = "\$base" \]; then$|  if false; then|'
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
  '31.1@-@tools/loop/run.sh@run#aCutReportIsRetriedInsteadOfRefused@the retry of a report that arrived cut on its way to the file (issue #224)@s|^      if \[ -z "$reviewer_deadline" \] && \[ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" \] &&$|      if false \&\&|'
  # 32.1 — a base that moved under the run stops it with a reason; the silent exit is what the case catches.
  '32.1@-@tools/loop/run.sh@run#staleBaseStopsTheRunWithItsReason@the stop that records why a stale base ended the run@s|^      \*"base moved"\*) stop_run "|      *"base moved"*) : "|'
  # 33.x — a branch is decided by what it *carries* (issue #232). 33.1 is the credit: without it an archived
  # change another item's commit added is the branch's own, which is how #230's refusal came to name an
  # unrelated change. 33.2 is the verdict: a blocked attempt is work, not a question nobody asked.
  '33.1@-@tools/loop/worktree.sh@worktree#doesNotAdoptAChangeCreditedToAnotherIssue@the commit subject that credits an archived change to this branch and its issue (issue #232)@s|^  gitr log -1 --format=%s "\$added" 2>/dev/null \| grep -qF "(#\$issue)" \|\| return 0$|  : # the mutation credits the branch with any change it holds|'
  '33.2@-@tools/loop/prescreen.sh@prescreen#aBlockedAttemptBranchIsWork@the verdict that reads a blocked attempt as work instead of as a decision (issue #232)@s|^        unfinished\|rework-cut)$|        unfinished-set-aside)|'
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
  # 54.x — the map covers every command the pipeline's check steps run (issue #242): without a pair, a landing that
  # breaks that step is green in the run and red in the pipeline, which is the shape that reddened `main` on
  # 2026-10-21. A profile whose map is one long quoted line can only be mutated in place, so the expressions delete
  # a pair from the copy the case reads. The labels are 54.x because 40.x is another change's (`run#aCorrectiveRoundIsHandedTheCorrections`).
  '54.1@-@tools/loop/loop.conf@gate#theMapCoversEveryPipelineStep@the pair that carries the build-inputs step in the map (issue #242; the second expression added 2026-11-05, issue #781, because the reader edge of #289 carries the same command)@s#tools/build-inputs\*|tools/tests/build-inputs-selftest.sh;##;;s#tools/sensors/sensors.json|tools/tests/build-inputs-selftest.sh;##'
  # 54.2 — the gate owes that step's check for a path the step reads (issue #242): without the pair the plan answers
  # `nothing owed` and exits 0 for a delta the pipeline refuses.
  '54.2@-@tools/loop/loop.conf@gate#aPipelineCheckIsOwedByItsPaths@the pair that owes the check over a path the step reads (issue #242)@s#tools/build-inputs\*|tools/build-inputs.sh --check;##'
  # 54.3 — the plan names every command the step runs (issue #242, review round 1): without the pair the case's two
  # older assertions still pass, and the mutation set the same step runs is owed by nobody.
  '54.3@-@tools/loop/loop.conf@gate#aPipelineCheckIsOwedByItsPaths@the pair that owes the build-inputs mutation list (issue #242)@s#tools/build-inputs\*|tools/tests/build-inputs-mutations.sh --list;##'
  # 54.4 — the plan names no command of another step (issue #242, review round 1): a pair that reaches the
  # portability check through the same glob makes the map claim the rejected option 2 while every positive
  # assertion still passes.
  '54.4@-@tools/loop/loop.conf@gate#aPipelineCheckIsOwedByItsPaths@a pair that reaches another step through a coarse glob (issue #242)@s#tools/build-inputs\*|tools/build-inputs.sh --check;#tools/build-inputs*|tools/build-inputs.sh --check;tools/build-inputs*|tools/check-portability.sh;#'
  # 54.5/54.6 — the map's path side is a hand-kept reader copy (issue #289): a path a check reads but that no
  # glob of that step names owes none of its commands, so a project value planted in the loop's configuration —
  # or in an engine `Check portability` scans — is green in the run while the step reddens.
  '54.5@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes the portability check for a path it reads, the loop configuration (issue #289)@s#tools/loop/\*|tools/check-portability.sh;##'
  '54.6@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes the portability check for an engine it scans (issue #289)@s#tools/issues/\*|tools/check-portability.sh;##'
  # 54.7-54.10 — the reader edges measured for the second step (issue #289's review, P1): `Check issue tooling`
  # greps `tools/loop/*.sh` and `tools/sensors/*.sh` and reads `tools/sensors/sensors.json`, and the configs
  # `tools/tooling.json` and `tools/sensors/sensors.json` are read by the steps above as well.
  '54.7@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes the issue tooling step for an engine its case greps (issue #289)@s#tools/loop/\*|tools/issues/tests/issue-tooling-selftest.sh;##'
  '54.8@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes one command of that step besides its self-test (issue #289)@s#tools/loop/\*|tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list;##'
  '54.9@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes the portability check for the profile it reads (issue #289)@s#tools/tooling.json|tools/check-portability.sh;##'
  '54.10@-@tools/loop/loop.conf@gate#aPathACheckReadsOwesThatStepsCommandSet@the pair that owes the build-inputs step for the registry it reads (issue #289)@s#tools/sensors/sensors.json|tools/build-inputs.sh --check;##'
  # 54.11 — a leg's group selection is the map's own driver entry with a narrowing appended (issue #781): without
  # the strip, the suite the pipeline runs as three legs names the driver as a command the map does not carry, so
  # the loop's suite is red for a landing the pipeline would accept.
  '54.11@-@../loop-cases.sh@gate#theMapCoversEveryPipelineStep@the strip that reads a leg'"'"'s group selection as the driver'"'"'s own entry (issue #781)@s|^  strip() .*$|  strip() { sed -e '"'"'s/ --from-git [^ ]*$//'"'"'; }|'
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
  # use for a file the engine's own directory does not hold: `tools/loop/tests/loop-mutations.sh` under the `tools/loop/`
  # prefix it is not a member of resolves to `$engine/-mutations.sh`. The labels are 43.x and not 41.x because the
  # lock change took 41.x first (`#265`), and a label names one invariant for good.
  '43.1@-@../loop-mutations.sh@mutation#aStaleRecordIsNamed@the exit that fails a set whose record no longer applies (issue #264)@s|^  if \[ "$stale" -ne 0 \]; then$|  if false; then|'
  '43.2@-@tools/loop/loop.conf@gate#anEnginePathOwesTheRecordCheck@the profile pair that owes the record check for a path under the engine (issue #264)@s#tools/loop/\*|bash tools/loop-mutations\.sh --check;##'
  '43.3@-@tools/loop/run.sh@mutation#everyDeclaredRecordApplies@the anchor two records keep on the child-start line (issue #264)@s|^  ( cd "$LOOP_ROOT" \&\& $child_cmd "$1"; printf|  ( cd "$LOOP_ROOT" \&\& $child_cmd "$1" \&\& printf|'
  # 44.1 is the cap reaching further than the child's process group. The group is what `set -m` gives the child,
  # and a descendant that called `setsid` is in no group the two signals name: measured 2026-10-22 (issue #272),
  # the mutation harness a child had started kept mutating and restoring the worktree until seven minutes after
  # the run had recorded the child as ended, its last restore leaving the tree dirty for the next run. The sweep
  # is the same `loop_pids_under` test the self-test's teardown and `loop-clean.sh` report with, run over the
  # tree the survivor still names as its working directory.
  '44.1@-@tools/loop/run.sh@run#detachedProcessInTheWorktreeIsEndedByTheCap@the cap ending the processes the child left working in the tree, and naming each one (issue #272)@s|^    sweep=\$(loop_end_pids_under "$LOOP_WORKTREE")$|    sweep=""|'
  # 45.1 — the commit that outlived the child it belongs to. Measured 2026-10-22 (issue #271): the worker of
  # #262 was ended by the cap at 23:48 with mutation 19.2 applied, `end-item` committed that tree 18 s later, and
  # the dead arm stayed on `origin/fix/issue-262-…` until a person amended it by hand at 05:05. The mutation
  # restores the unconditional commit, so the case reads a branch the run must have left at the base.
  '45.1@-@tools/loop/run.sh@run#treeOfAChildTheCapEndedIsNotCommitted@the guard that leaves the tree of a child the run ended uncommitted (issue #271)@s|^  if \[ "\$last_exit" -eq "\$LOOP_CHILD_TIMEOUT" \] \|\| \[ -n "\$timeout_claim" \]; then$|  if false; then|'
  # 45.2 is the same rule at the second commit site, and it is a separate record because it is a separate guard:
  # the first one leaving the item's own worker uncommitted says nothing about the worker a refusing review round
  # sends the change back with (issue #271, found by that change's round-1 review — the requirement was universal
  # and this call site was left as it stood). The mutation restores the unconditional `end-item` there, so the
  # rework worker's half-applied tree lands in a commit on top of the item's own fix.
  '45.2@-@tools/loop/run.sh@run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted@the guard that leaves the tree of a rework worker the cap ended uncommitted (issue #271)@s|^      if \[ "\$round2_exit" -eq "\$LOOP_CHILD_TIMEOUT" \] \|\| \[ -n "\$round2_timeout" \]; then$|      if false; then|'
  # 46.1 is the stream that carries a suite output to the console while the suite runs. Measured 2026-10-23
  # (issue #269): the gate held a suite's whole output in a variable, so a gate that ran for eleven minutes
  # carried two lines and neither an operator nor a worker could tell a long suite from a stopped one. The
  # mutation ends the follower before it starts, so nothing is streamed and the console is silent until the step
  # ends — which is what the case that names it asserts. Since issue #315 the stream is a file the step's command
  # writes and the console follows; the follower is the whole of the streaming.
  '46.1@-@tools/loop/gate.sh@gate#theSuitesOutputReachesTheConsoleWhileItRuns@the stream that carries a suite output to the console while the suite runs (issue #269)@s|^    follow_stream "\$stream" "\$follow_stop" &$|    : # the mutation replays the output only when the step ends|'
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
  '48.1@-@../loop-selftest.sh@driver#aCaseThatAsksForNoCoreCompilesNothing@the compile at the first case that asks for it, not above the case list (issue #261)@s|^source "$root/tools/loop/tests/loop-cases.sh"$|core_ensure\nsource "$root/tools/loop/tests/loop-cases.sh"|'
  # 48.2 is the other half of 48.1: the marker is what makes the compile *once per suite* rather than once per
  # asking case. Without it a suite of two asking cases is still green — each case compiles again and is handed a
  # class path — so the invariant is invisible to an assertion and only a count catches it: the case installs a
  # `javac` that records every invocation, and the mutation makes it record two.
  '48.2@-@../loop-selftest.sh@driver#theCoreIsCompiledOncePerSuite@the marker that keeps the compile to one per suite, not one per asking case (issue #261)@s|^    if \[ ! -f "$selftest_core_marker" \]; then$|    if true; then|'
  # 49.x — the suite's own selection and its record of what it cost. Measured 2026-10-23: one case under
  # `tools/loop/**` owes the whole self-test (723 s for 163 cases, serial) and the gate said nothing about it, so an
  # item that touched two files paid 723 s on every iteration (#261 paid eight) and the twelve minutes could only
  # be established with a stopwatch and a `ps`. One mutation per invariant: the group filter, the refusal of a
  # filter that names nothing, the duration in the gate's record.
  '49.1@-@../loop-selftest.sh@driver#aGroupFilterRunsOnlyItsOwnCases@the group filter that keeps a suite from running the cases of another group (2026-10-23)@s|^  if \[ -n "\$only_group" \] && ! matches "\$case_name" "\$only_group#\*"; then continue; fi$|  : # the mutation runs every case the suite declares|'
  '49.2@-@../loop-selftest.sh@driver#aFilterThatNamesNoCaseRefuses@the count the refusal of a filter that names no case depends on (2026-10-23; anchored 2026-11-05, issue #781: an unanchored expression took the nproc fallback instead of the counter)@/^count_matching() {/,/^}/s|^  printf .%s. "\$n"$|  printf .%s. 1|'
  '49.3@-@tools/loop/gate.sh@gate#theSummaryNamesWhatEachSuiteCost@the wall clock the gate records beside the suite that spent it (2026-10-23)@s|^  took=\$((st_ended\[slot\] - t0))$|  took=0|'
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
  # 53.x — a start the worktree refuses mid-run, and the three things the run owes when it ends on one (issue #284).
  # Measured 2026-10-23: the run stopped 166 minutes into an 8 h budget with `the item could not be started: loop:
  # the worktree is not clean — refusing to start #270:`, 7 eligible items unworked, the item whose work the tree
  # held named nowhere, and #270 carrying no state entry at all. One record per invariant, and each makes the same
  # case red on its own: 53.1 leaves the leftovers unattributed (no escalation, no account under the item that left
  # them), 53.2 records the item a second time instead of joining its one record — which is what counted one item
  # twice in every figure the report derives from `.items` and let `next` read one block as two consecutive ones —
  # and 53.3 puts the refusal's first line back as the stop reason, which names neither the item nor the files.
  '53.1@-@tools/loop/run.sh@run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds@the leftovers recorded under the item whose work the tree holds, and escalated (issue #284)@s|^          if \[ "\$dry_run" -eq 0 \] && \[ -n "\$owner_item" \]; then$|          if false; then|'
  '53.2@-@tools/loop/state.sh@run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds@one record per item, a second account joining the note the item already has (issue #284)@s|^      if (\[\.items\[\] \| select(\.issue == \$i)\] \| length) == 0 then$|      if true then|'
  '53.3@-@tools/loop/run.sh@run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds@the stop reason naming the item the leftovers belong to and the files it found (issue #284)@s|^          stop_run "the item could not be started: the worktree is not clean .*$|          stop_run "the item could not be started: $(printf %s "$start_out" \| head -1)"|'
  # 53.1 — the report scans the kept worktree's own scratch root beside its own (issue #274). Measured 2026-10-23:
  # four `loop-selftest.*` trees and four poll loops under `.pi/worktrees/loop/.pi/tmp`, three of the trees past the
  # 120-minute threshold, and `loop-clean` printed its heading with no entry — the root a suite writes into is the
  # root of the tree it runs in, and the gate runs the suite inside the worktree.
  '53.4@-@tools/loop/loop-clean.sh@clean#reportsTheWorktreesScratchTreesToo@the report resolving the kept worktree own scratch root (issue #274)@s|^  worktree_tmp=\$(loop_tmp_dir_for "\$tree")$|  worktree_tmp=""|'
  # 53.2 — the two roots are compared as directories, so `--worktree .` (the same root written `$LOOP_ROOT/.`) is scanned
  # once instead of twice. Measured 2026-10-23 (issue #274's round-1 review): the strings never matched, so that
  # spelling printed two headings for one root and counted one tree's bytes twice.
  '53.5@-@tools/loop/loop-clean.sh@clean#worktreeDotSweepsTheOneRootOnce@the two roots compared as directories rather than as written (issue #274 review)@s|^  loop_same_dir "\$worktree_tmp" "\$LOOP_TMP_DIR" && worktree_tmp=""$|  : # the dedup removed|'
  # 53.x — the case set is selected by the files a delta touches (issue #276). 190 cases and 723 s of self-test were
  # owed for every `tools/loop/**` gate run because a case had no record of which file it exercises, while the
  # mutation harness had carried exactly that field since #235. One mutation per half: 53.6 leaves the profile's
  # `{base}` token unsubstituted, 53.7 makes the file set select every case, 53.8 removes the refusal a file set that
  # names nothing gets, 53.9 drops the rule that a core file owes the whole set, and 53.10 removes one token from a
  # case's declaration so the declaration is narrower than the case that carries it.
  '53.6@-@tools/loop/gate.sh@gate#theDeltaHandsASuiteTheBaseItMeasured@the base substituted into the suite the delta owes (issue #276)@s|^    cmd=\${cmd//\\{base\\}/\$base}$|    : # the mutation substitutes no base|'
  '53.7@-@../loop-selftest.sh@driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns@the file set narrowing the selection instead of selecting everything (issue #276)@s|^  if ! files_select "\$case_name"; then continue; fi$|  : # the mutation selects every case|'
  '53.8@-@../loop-selftest.sh@driver#aFileSetThatNamesNoCaseIsRefused@a file set that names no case refused rather than read as an empty suite (issue #276)@s|^if \[ "\$only_files" != " " \] && \[ "\$(count_files_matching)" -eq 0 \]; then$|if false; then|'
  '53.9@-@../loop-selftest.sh@driver#aCoreFileOwesEveryCase@a core file owing the whole set whatever the cases declare (issue #276)@s|^  core_files=\$(loop_case_core_files)$|  core_files=""|'
  '53.10@-@../loop-cases.sh@driver#everyCaseDeclaresTheFilesItCanReach@a declaration narrower than the case that carries it (issue #276)@s|^  # files: tools/loop/gate.sh tools/pi-child-events.sh tools/tooling.json$|  # files: tools/pi-child-events.sh tools/tooling.json|'
  '53.11@-@../loop-selftest.sh@clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft@the sweep of the suite own scratch root on the way out, so a process a case left behind does not outlive it (issue #233)@s|^  loop_end_pids_under "\$tmp"$|  : # the mutation leaves the process behind|'
  # A delta no case declares (issue #779). The driver answered that file set with one outcome only — the refusal — and a
  # CI step handed a real delta needs the second: with `--allow-empty` the empty intersection is a statement naming the
  # delta, and without the flag the refusal stands byte for byte. One mutation, the flag's branch removed, so the
  # statement goes back to being a refusal; the case that reads the pair is the one named here.
  '80.1@-@../loop-selftest.sh@driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt@the empty intersection stated rather than refused when the caller has declared it expects one (issue #779)@s|^  if \[ "\$allow_empty" -eq 1 \]; then$|  if false; then|'
  # The pin compares the pipeline's command against the map's, and the delta the step is now handed is written as a
  # run-time substitution (`${{ steps.delta.outputs.args }}`). Removing that half of the strip line puts the
  # expression back into the comparison, so the pin reports the step as missing from the map — which is the state
  # this tree was in from the leg split, before the normalisation existed.
  '80.2@-@../loop-cases.sh@gate#theMapCoversEveryPipelineStep@the run-time substitution the pipeline makes stripped before the map is compared (issue #779)@s|^  strip() { sed -e .*--from-git \[\^ \]\*\$//.; }$|  strip() { sed -e '\''s/ --group [^ ]*$//'\'' -e '\''s/ --from-git [^ ]*$//'\''; }|'
  # One suite per repository (issue #295): the lock is taken at all, the pattern that is a suite is told from the one
  # named case that is not, the lease lets a suite whose owner is gone take it over, and the release leaves the
  # repository free for the suite after it.
  '55.1@-@../loop-selftest.sh@driver#aSecondSuiteInOneRepositoryIsRefused@the lock that refuses a second suite in one repository (issue #295)@s|^  if ! mkdir "\$lock" 2>/dev/null; then$|  if false; then|'
  '55.2@-@../loop-selftest.sh@driver#aSecondSuiteInOneRepositoryIsRefused@the pattern a suite is, told from the one named case that is not (issue #295)@s|^  \[ -n "\$only_case" \] && ! is_glob "\$only_case" \|\| return 1$|  [ -n "$only_case" ] \|\| return 1|'
  '55.3@-@../loop-selftest.sh@driver#aSecondSuiteInOneRepositoryIsRefused@the release that leaves the repository free for the suite after it (issue #295)@s|^  \[ -z "\$selftest_lock" \] \|\| rm -rf "\$selftest_lock"$|  : # the mutation never releases the lock|'
  '55.4@-@../loop-selftest.sh@driver#aSecondSuiteInOneRepositoryIsRefused@the lease that lets a suite whose owner is gone take the lock over (issue #295)@s|^    if \[ "\$holder_pid" -gt 0 \] && kill -0 "\$holder_pid" 2>/dev/null; then$|    if true; then|'
  # 56.1 — the base a run records is the base *branch*'s commit, never the commit the project tree happens to have
  # checked out (issue #301). Measured 2026-10-24: the run was opened on `fix/issue-44-phone-diagnostic-locale`, so
  # `init` recorded that branch's `a0ca0f35` while `main` was `a0a15fe0`, 12 commits ahead since before the run
  # opened; the first `start-item` refused it as a stale base and the run ended with 0 items and 0 steps, the whole
  # pre-screen (3 min) being its only work. The mutation puts the `HEAD` read back in the place of the base branch.
  '56.1@-@tools/loop/state.sh@state#recordsTheBaseBranchNotTheCheckout@the base recorded from the base branch instead of the commit the checkout has@s|--verify --quiet "\$(loop_base_ref)"|HEAD|'
  # 56.2 — the run refuses a project tree that is not checked out on the base branch, because the landing step
  # merges into that tree (issue #301's review). Without the guard the measured state reaches the merge: the item
  # is blocked after its budget, or — on a fast-forwardable checkout — closed with its work never on the base.
  '56.2@-@tools/loop/run.sh@run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen@the refusal of a run whose project tree is not on the base branch@s|^if \[ "\$checkout_ref" != "\$(loop_base_ref)" \]; then$|if false; then|'
  # 56.3 — the base branch is a key of the project's own configuration, and the environment may override it (issue
  # #303: the branch was reachable through the environment alone, so a ported project could name it nowhere it
  # commits). The mutation records the branch with an outright assignment, which the sourced configuration applies
  # *after* the environment — so the caller's `LOOP_BASE_REF` silently loses and every later reader descends from
  # the recorded branch instead.
  '56.3@-@tools/loop/loop.conf@state#baseBranchIsAConfigurationKey@the base branch recorded as a value an inherited environment may override (issue #303)@s|^LOOP_BASE_REF=.*$|LOOP_BASE_REF="main"|'
  # 57.1 — the landing step verifies that the project's own tree has the base branch checked out before it merges
  # into it (issue #308). Without the guard the merge acts on whatever branch that tree holds: a checkout that
  # merely fast-forwards absorbs the item's commits, the base stays where it was, and the item closes with its fix
  # on neither — the mechanism measured 2026-10-24 in `worktree#refusesToMergeIntoATreeOffTheBaseBranch`, where
  # HEAD answered 0 where 5 is owed.
  '57.1@-@tools/loop/worktree.sh@worktree#refusesToMergeIntoATreeOffTheBaseBranch@the checkout the landing step merges into being the base branch@s|^  if \[ "\$branch" != "\$(base_ref)" \]; then$|  if false; then|'
  # 57.2 — the base a run records is a commit the base *branch* carries, never just a commit `rev-parse` resolves
  # (issue #308). Without the ancestry test the record takes whatever the run computed, which is how an absorbed
  # merge read as an advance and the closure named a base that never carried the fix.
  '57.2@-@tools/loop/state.sh@state#setBaseRefusesACommitTheBaseBranchDoesNotCarry@the ancestry of a recorded base against the base branch@s|^  git -C "\$LOOP_ROOT" merge-base --is-ancestor "\$sha" "\$branch" 2>/dev/null \|\|$|  true \|\||'
  # 58.1–58.5 — an item blocked by a mechanism records which one stopped it (issue #280, the decision recorded on
  # it: keep both stop rules and make their spend attributable). Measured 2026-10-23 over the archive: 10 refusing
  # rounds cost 198.8 M tokens and 1 unmergeable attempt 22.9 M, both recorded as the one word `blocked`, so the
  # two could be told apart only by reading the note's prose. The five clauses are the two engine sites, the state
  # write, the mechanism printed in the report's own column, and the `-` that column prints for a row no mechanism
  # stopped; each is noticed by the case that names its mechanism. The last two were rewritten in review round 1:
  # `58.4` mutated the whole interpolation, which produced an invalid jq escape so the report died instead of the
  # column going missing, and nothing mutated the `-` a row with no mechanism prints (issue #280, round 1).
  '58.1@-@tools/loop/run.sh@run#reviewRoundsCapAtTwo@the refusing last round named as the mechanism that stopped the item (issue #280)@s|^      stop_reason="refused"$|      stop_reason=""|'
  '58.2@-@tools/loop/run.sh@run#doesNotCloseOnAMergeTheBaseCannotTake@a base that would not take the branch named as the other mechanism (issue #280)@s|^      stop_reason="unmergeable"$|      stop_reason=""|'
  '58.3@-@tools/loop/state.sh@state#aBlockedItemNamesTheMechanismThatStoppedIt@the mechanism written into the item record (issue #280)@s|^                  stopReason:(if \$sr == "" then null else \$sr end),$|                  stopReason:null,|'
  '58.4@-@tools/loop/state.sh@report#wallTimeAndReasonInTheTable@the mechanism printed in a column of its own, beside the spend (issue #280)@s|if \. == "" then "-" else \. end|if true then "-" else . end|'
  '58.5@-@tools/loop/state.sh@report#wallTimeAndReasonInTheTable@`-` in the stop column of a row no mechanism stopped (issue #280)@s|if \. == "" then "-" else \. end|if false then "-" else . end|'
  # 59.x — the self-test's cases run in a bounded pool, and what a case leaves behind ends with it (issue #260).
  # Measured 2026-10-22: 163 cases strictly one after another, 723 s, though every case already owned a throwaway
  # repository, a scratch tree and a process group of its own. One record per invariant: 59.1 bounds the pool to one
  # case at a time — the shape the suite had before the pool, with every case still green — and 59.2 leaves the sweep
  # over a case's own scratch root out, so what the case left behind is still standing when its slot is handed on.
  '59.1@-@../loop-selftest.sh@driver#theSuiteRunsItsCasesInAPool@the pool that starts the next case as one finishes, rather than one case at a time (issue #260)@s|^  while \[ "$running" -lt "$workers" \] && \[ "$started" -lt "$queued" \]; do$|  while [ "$running" -lt 1 ] \&\& [ "$started" -lt "$queued" ]; do|'
  '59.2@-@../loop-selftest.sh@driver#aCaseThatLeavesAProcessBehindIsSweptWithIt@the sweep of a case own scratch root before its slot is handed on (issue #260)@s|^  loop_end_pids_under "$dir/tree" --quiet$|  : # the mutation leaves the process behind|'
  # 60.x — the wait for a child looks at a fraction of a second, and the heartbeat reports elapsed seconds (issue
  # #259). Measured 2026-10-22: the poll and the join of the heartbeat beside it charged a child that answered at
  # once 1–2 s of pure sleep, and 40 % of `run#recordsAFailedReviewOnTheBranch` was sleep. One record per invariant:
  # 60.1 puts the whole second back in the poll — the run's wait outlasts a child that has already answered — and
  # 60.2 drops the elapsed-second test, so the heartbeat reports on every look and the configured interval stops
  # meaning seconds.
  '60.1@-@tools/loop/run.sh@run#theWaitEndsWhenTheChildDoes@the wait looking for the completion record below a whole second (issue #259)@s|^    sleep "\$poll"$|    sleep 1|'
  '60.2@-@tools/loop/run.sh@run#theProgressLineKeepsItsInterval@the heartbeat'"'"'s test on elapsed seconds rather than on the look it was woken by (issue #259)@s|^      \[ "\$elapsed" -ge "\$next" \] \|\| continue$|      : # the mutation reports on every look|'
  # 61.x — the measuring block carries the suite's declared case count and the gate's own timing, and both tasks
  # state the rule a quoted number follows (issue #244). Measured 2026-10-21 over `.pi/issue-loop/items/*/
  # reviewer-report-1.md`: 11 PASS against 29 FAIL, 147 `UNPROVEN` claims in the round-1 reports, most of them
  # numbers the change asserted and nobody measured. One record per invariant: 61.1 drops the declared count, 61.2
  # drops the gate's timing, and 61.3/61.4 remove the rule each task states — the worker's that a number is a
  # command's output, the reviewer's that a number with no such command is a correction rather than a refusal.
  '61.1@-@tools/loop/measure.sh@measure#printsTheSuiteCountAndTheGateTiming@the declared case count the block carries, so a suite count is a command rather than a tally by hand (issue #244)@s|^if \[ -n "\$suite_cmd" \]; then$|if false; then|'
  '61.2@-@tools/loop/measure.sh@measure#printsTheSuiteCountAndTheGateTiming@the gate timing summed from the tallies it wrote (issue #244)@s|^if \[ "\$gate_suites" -gt 0 \]; then$|if false; then|'
  '61.3@-@tools/loop/prompts/worker.md@prompt#workerIsHandedTheMeasureBlock@the worker told that a quoted number is a command'"'"'s output in its record (issue #244)@s|^\*\*A number the change quotes is the output of a command in its record\.\*\*|A number is a guess.|'
  '61.4@-@tools/loop/prompts/reviewer.md@prompt#reviewerIsBoundedToReading@the reviewer told that a number no command produced is a correction, not a refusal (issue #244)@s|A number no command in the record produced is not a claim|A number may be taken on trust|'

  # 62.x — the loop's own bounds and its record of what a stopped run found (issue #308). Six invariants, six
  # records: the case bound itself, the guard that a bound answers only a case that outran it, the relay that carries
  # a running case's line into the suite's log, the call that ends the heartbeat with a signalled run, and the two
  # dirty-tree paths that keep the work they found. Measured before the change: 56 minutes of a 480-minute budget on
  # one case that nothing could end, and a killed worker's work reconstructed by hand. The ids were `61.1`–`61.6`
  # until this change met the branch where 61.x already belonged to another change (#244): a record's id is not
  # owned, so the second one to land takes the next free block.
  '62.1@-@../loop-selftest.sh@driver#aCaseThatBlocksIsEndedByItsOwnBound@the case bound the suite ends a case with (issue #308)@s|^  ( sleep "\$case_max"$|  ( sleep 99999|'
  '62.2@-@../loop-selftest.sh@driver#aCaseInsideItsBoundKeepsItsOwnVerdict@the pool reading a case'"'"'s own verdict instead of answering every case with the bound (issue #308)@s|^    if finish_case "\$slot"; then$|    if false; then|'
  '62.3@-@../loop-selftest.sh@driver#aRunningCasePrintsItsOwnLines@the relay that carries a running case'"'"'s own line to the suite (issue #308)@s#^    ( while IFS= read -r line; do$#    ( while false; do#'
  '62.4@-@tools/loop/run.sh@run#noProcessOfASignalledRunOutlivesIt@the call that ends the heartbeat with a signalled run (issue #308)@s|^  loop_end_pid "\$active_hb"$|  : # the mutation leaves the heartbeat|'
  '62.5@-@tools/loop/run.sh@run#treeOfACapEndedChildIsKeptAsAPatch@the patch a cap-ended child'"'"'s tree is kept in (issue #308)@s|^    keep_leftovers "\$issue"$|    : # the mutation keeps no patch|'
  '62.6@-@tools/loop/run.sh@run#aRefusedStartKeepsTheLeftoversItFound@the patch a refused start keeps (issue #308)@s|^          own_leftovers_of "\$owner_item" \|\| keep_leftovers "\$owner_item"$|          : # the mutation keeps no patch|'

  # 255.x — the evidence check reads the roots it scans from the project's configuration, and an engine may hold no
  # project path (issue #255). The check came out of the `fix-loop` skill, whose C4 looked for a leftover
  # `REVERT-CHECK MUTATION` marker under the literal `app/src` and so reported on this project's source from any
  # tree; it now reads `LOOP_EVIDENCE_SCAN_PATHS`/`LOOP_EVIDENCE_XML_DIR` from `tools/loop/loop.conf`. One record,
  # and the case it must be noticed by is `evidence#scansWhatTheProfileNames`: the mutation puts the retired
  # literal back where the configuration was read, so the marker the case plants under the root its own
  # configuration names is not reported — and a configuration that names no scan root is answered with a clean
  # report instead of the refusal the same case asserts.
  '255.1@-@tools/loop/evidence-check.sh@evidence#scansWhatTheProfileNames@the scan root read from the project configuration — the engine looks at a literal of its own instead (issue #255)@s#^    \[ -n "\$scan" \] || scan=\$(conf_value "\$conf_file" LOOP_EVIDENCE_SCAN_PATHS)$#    [ -n "$scan" ] || scan="app/src"#'

  # 63.1 — the step ends with the command it started, not with the pipe that command's output went into (issue
  # #315). Measured 2026-10-26: a `tools/loop/tests/loop-selftest.sh` step printed its verdict 565 s in and its own line read
  # `— 1237s`, because the pipeline stayed open while a writer the suite left behind held the write end — ~670 s of
  # gate after the verdict, on every run whose suite ended before its watchers expired. The mutation puts `tee` on
  # a pipe back in the command's place, so the step waits for the pipe's EOF again and the case that names it reads
  # a step that is still inside the gate long after its command exited.
  # Its three ampersands are escaped (`2>\&1`, the trailing `\&`): `sed`'s replacement expands an unescaped `&` to the
  # whole match, so the round-1 record rewrote the line into a corrupted one instead of restoring the pipeline, and
  # the case it reddened did so for a reason that was not the invariant's (issue #315, its round-1 review).
  # The pattern reads the step's two halves as `.*` (issue #311): the launch line now writes the step's completion
  # record beside the stream (`( $cmd; printf '%s\n' "$?" > "$status_file" ) >"$stream" 2>&1 &`), and a record that
  # quoted the `printf` would have to carry a `\n` through `sed`'s own escaping. What the mutation restores is the
  # shape, not the line.
  '63.1@-@tools/loop/gate.sh@gate#aStepEndsWithItsCommandNotWithItsWatchers@the step ending with the command it started, not with the pipe that command wrote into (issue #315)@s|^  ( \$cmd;.*>"\$stream" 2>&1 &$|  \$cmd 2>\&1 \| tee "\$stream" \&|'

  # 63.2 — the step's own bound (issue #311). The gate bounded a child through the run's `LOOP_CHILD_MAX_SECONDS` and
  # a case through the suite's `LOOP_CASE_MAX_SECONDS`, and a step through nothing: measured 2026-10-25, one
  # `tools/loop/tests/loop-selftest.sh` step consumed a worker's whole wall-clock and the run ended that child with its gate
  # already green and its work uncommitted. The mutation makes the guard never fire, so the case that names the
  # bound reads a gate that is still inside a suite which never returns — the state the item was filed about.
  '63.2@-@tools/loop/gate.sh@gate#aStepIsBoundedAndTheOverrunNamesIt@the bound that answers a gate step which has not returned (issue #311)@s|^      if \[ .* -ge "\$step_max" \]; then$|      if false; then|'

  # 64.1 — the child's bound following what one item's gate owes (issue #663). Measured 2026-10-26 on #310, a
  # `tools/loop/**` item: 957 s of red gate round, 233 s of one focused falsification suite, 972 s of green gate
  # round and 542 s of phases = 2 704 s against the 2 700 s constant, and the child was ended 8 s after its green
  # `gate-summary.xml` was written; #664 and #666 ended the same way. The mutation keeps the floor and drops the
  # derivation, so the case reads a child given less than its own item's gate costs.
  '64.1@-@tools/loop/run.sh@run#theChildCapIsNeverBelowWhatOneItemsGateOwes@the bound that follows what one item'"'"'s gate owes (issue #663)@s|^  if \[ "\$owes" -gt "\$floor" \]; then .*$|  : # the mutation keeps the floor|'
  # 64.2 — the other clock the run answers "may this item start?" with (issue #663). `next` refuses an item the
  # remaining budget cannot pay for, and the floor below what one item's gate owes lets an item start that pays for
  # its gate and loses the phases after a green round: the bound's own loss through the other door.
  '64.2@-@tools/loop/loop.conf@state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes@the item floor never recorded below what one item'"'"'s gate owes (issue #663)@s|^LOOP_ITEM_FLOOR_MINUTES=.*$|LOOP_ITEM_FLOOR_MINUTES=45|'

  # 65.x — a run's report reads each item's spend against a recorded band (issue #662). Measured over the archive's
  # states: the per-item median rose from 9.51 M to 18.30 M tokens in three days and no report said a word, because
  # the tokens, the largest context and the run total were printed beside nothing. One record per site — the
  # run-level line and the table's column — and the case that names both is `report#anItemOverTheTokenBoundIsFlagged`.
  # A warning, never a stop: the mutation that matters is the one that makes the flag never fire, not one that
  # changes an outcome, because no outcome is derived from it.
  '65.1@-@tools/loop/report.sh@report#anItemOverTheTokenBoundIsFlagged@the run-level line that names the items above the recorded band (issue #662)@s|^if \[ "\$item_tokens_warn" -gt 0 \]; then$|if false; then|'
  '65.2@-@tools/loop/state.sh@report#anItemOverTheTokenBoundIsFlagged@the table column that says how far above the band an item is (issue #662)@s|if \$over > 0 and \$tot != null and \$tot > \$over|if false|'

  # 66.x — the child is started without the project's context files, and its own phase prompt carries the routing
  # that was in them (issue #661). Measured over the archive (2026-10-27): the system prompt of every child opened
  # with a 25 624-character `project_context` section — 77 % of the 33 088-character system prompt — and that
  # section is charged on every turn, 9 151 of the 17 235 tokens a turn cost (`python3 tools/loop-spend.py`, probe
  # `evidence/sections-probe.py`). One record per invariant: 66.1 removes the context switch from the adapter, so
  # the child is handed the project's `AGENTS.md` again; 66.2 removes the section-list recipe from the worker's
  # route, so a child with no context files is sent to a guideline it cannot read by section.
  '66.1@-@../pi-child-events.sh@adapter#theChildIsStartedWithoutContextFiles@the context switch that keeps the project'"'"'s own context files out of a child'"'"'s prompt (issue #661)@s|^context=(--no-context-files)$|context=()|'
  '66.2@-@tools/loop/prompts/worker.md@prompt#thePhasePromptsCarryTheirOwnRoute@where the capabilities live, in the route a child with no context files is given (issue #661)@s|openspec/specs/|openspec/spec/|'

  # 67.x — a tool result over the recorded bound is cut and named, and the trim is recorded (issue #660). Measured
  # over the archive (2026-10-27): 59.2 M characters of tool output were injected once and 876.4 M tokens of it
  # re-read — 53 % of the 1.66 B the archive spent — while the median result is 825 characters and 9.0 % of the
  # 22 901 results are above the 8 000 the bound records (`python3 tools/loop-spend.py`, probe
  # `evidence/result-sizes.py`). One record per invariant: 67.1 stops the cut, 67.2 stops the line from naming the
  # size it cut, 67.3 stops the record, 67.4 stops the adapter from loading the rule with every child, 67.5 stops
  # the adapter from naming that record beside the child's event stream (the clause the spec names and round 1
  # left undefended), and 67.6 moves the pass-through's `at or under` boundary to `under`.
  '67.1@-@../pi-trim-result.mjs@trim#aResultAboveTheBoundIsTrimmedAndNamed@the cut itself, so an oversized result is handed over whole again (issue #660)@s|  return cut + trimNotice(tool, total, bound);|  return text;|'
  '67.2@-@../pi-trim-result.mjs@trim#aResultAboveTheBoundIsTrimmedAndNamed@the line that names the size the result had, so a cut is judged as the whole answer (issue #660)@s|the first \${bound} are shown|the first \${total} are shown|'
  '67.3@-@../pi-trim-result.mjs@trim#theTrimIsRecorded@the record of what was cut, so the item cannot say which result a claim was read from (issue #660)@s|      appendFileSync(record, trimRecordLine(tool, total, bound));|      void record;|'
  '67.4@-@../pi-child-events.sh@adapter#theChildIsStartedWithTheResultBound@the word that loads the bound with every child (issue #660)@s|^extension=(-e "$here/pi-trim-result.mjs")$|extension=()|'
  '67.5@-@../pi-child-events.sh@adapter#theChildIsStartedWithTheResultBound@the line that names the trim record beside the child'"'"'s own event stream, so every trim record silently vanishes (issue #660)@s|^  export LOOP_TOOL_RESULT_TRIM_RECORD=.*$|  :|'
  '67.6@-@../pi-trim-result.mjs@trim#aResultUnderTheBoundIsHandedOverWhole@the boundary that hands a result of exactly the bound over whole, so a result that fitted is cut (issue #660)@s|text.length <= bound|text.length < bound|'

  # 68.x — the tail of a run is admitted on the cheapest item it has measured, and the item the run's own deadline
  # cuts is left for the next run instead of being escalated, with the work it left named in its record (issue
  # #674). Measured 2026-10-27: the floor was the median wall time × 1.5 held above
  # `LOOP_ITEM_FLOOR_MINUTES=65`, so a run of 10-15 min skips stopped up to ~50 min before its deadline. One record
  # per invariant: 68.1 makes the floor follow the most expensive measured item again (the old lower bound's
  # effect), 68.2 escalates the item the deadline cut (the guard never holds), 68.3 drops the patch path
  # from the item's record so the report cannot name the work an ended child left, 68.4 stops reading the
  # reviewer's own `LOOP-DEADLINE` (the clock is then read as the turn bound written beside it), 68.5 lets the second
  # pre-screen pass run after a deadline cut, so the report names a decision for the item the clock ended. 68.2 and
  # 68.6 were retired with issue #790: both widened the guard that escalates a block, and the guard is gone — the
  # two causes it excused (the run's own deadline, and a bound) leave a `cut` above the block arm, so the item they
  # excused is recorded by the result the record 82.1 mutates.
  '68.1@-@tools/loop/state.sh@state#floorIsDerivedFromTheFinishedItemsWallTimes@the floor following the cheapest measured item rather than the most expensive one (issue #674)@s|^      \| (\$w \| min) as \$cheapest$|      \| ($w \| max) as $cheapest|'
  '68.3@-@tools/loop/state.sh@run#treeOfACapEndedChildIsKeptAsAPatch@the patch path the item'"'"'s record carries, so the report can name the work an ended child left (issue #674)@s|leftover:(if \$lo == "" then null else \$lo end)|leftover:null|'
  '68.4@-@tools/loop/run.sh@run#reviewerEndedAtTheRunsDeadlineIsNotADecision@the reviewer'"'"'s own `LOOP-DEADLINE` read, so the clock the run ended the round with is read as the turn bound run_child wrote beside it, and the reason names the wrong bound (issue #674)@s|^      reviewer_deadline=.*LOOP-DEADLINE.*$|      reviewer_deadline=""|'
  '68.5@-@tools/loop/run.sh@run#reviewerEndedAtTheRunsDeadlineIsNotADecision@the guard that leaves the queue alone after a deadline cut, so the pass re-excludes the cut item and the report names a decision for it (issue #674)@s|^    \[ "\$deadline_cut" -eq 0 \] && \[ "\$bound_cut" -eq 0 \]; then$|    [ 0 -eq 0 ]; then|'

  # 69.x — the pre-screen asks the item's text where its fix lands, and the answer is the author's own claim: a
  # line beginning `The fix lands in the submodule` (#314). Measured 2026-10-26 on the adoption run: the path in a
  # body is evidence — the file a dirty tree was seen on — and reading it as the claim parked #309, #310 and #305
  # as submodule patches. The mutation drops the line anchor and matches the phrase anywhere, which is precisely
  # what makes a body that quotes the marker indistinguishable from one that states it.
  '69.1@-@tools/loop/prescreen.sh@prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim@the line anchor that separates the author'"'"'s stated claim from a quoted mention of it (issue #314)@s|grep -q "^\$marker"|grep -qF "$marker"|'

  # 70.x — the loop's `close` verdict records a settled state, and the item it closes may carry the *other*
  # settled value of the profile (a retired entry a fix closes, a fixed item a person retires). Recording a
  # settled state is that verdict's own purpose, exactly as it is `close.sh`'s, so the write names the
  # transition (#684) instead of being refused with exit 3 `status-settled` and leaving the closure half
  # attempted between its two writes (#699). The mutation takes the flag off the closure's own write and leaves
  # the routine writers as they were, so the case's refusal is the guard's and not something else.
  '70.1@-@tools/loop/labels.sh@labels#closeNamesTheSettledTransition@the closure naming the transition its own write makes, so the other settled value is displaced instead of refusing the write (issue #699)@s|^  \[ "\$close_issue" -eq 0 \] \|\| args+=(--displace-settled)$|  : # the closure names no transition|'

  # 71.x — a bound is not larger than the ceiling of the process that started the call (issue #692). Measured twice on
  # 2026-10-28 at `Error: Tool 'ctx_shell' exceeded its timeout of 900000ms.`: the project's own bounds were every one
  # of them above the ceiling, nothing read one, and the children died mid-suite with their work uncommitted. The first
  # mutation removes the suite's clock; the second makes it fire at once, so a ceiling the selection fits inside ends
  # the suite as well; the third removes the gate's clamp, which leaves the step's bound above the caller's ceiling.
  # The last three are the review's own correction (round 1): ending a watcher's *subshell* left the `sleep` it forked
  # standing — one per case and one for the ceiling — so each line that now ends a whole group has a mutation putting it
  # back to the single pid, and the two `driver#` cases count what is left behind (2026-10-29: 1 and 2 processes).
  '71.1@-@../loop-selftest.sh@driver#aSuiteEndsInsideTheCeilingItsCallerNamed@the watcher that ends the suite at the ceiling its caller named (issue #692)@s#^if \[ -n "\$ceiling" \]; then$#if false; then#'
  '71.2@-@../loop-selftest.sh@driver#aCeilingTheSuiteFitsInsideChangesNothing@the ceiling being a clock rather than an end for every suite that names one (issue #692)@s#^  ( sleep "\$ceiling";#  ( sleep 0;#'
  '71.3@-@gate.sh@gate#theStepBoundIsClampedByTheCeilingTheCallerNamed@the clamp that holds a gate step to the ceiling the caller named (issue #692)@s#^step_max=\$(loop_bound_under_ceiling "\$step_max")$#step_max=$step_max#'
  '71.4@-@../loop-selftest.sh@driver#aCeilingTheSuiteFitsInsideChangesNothing@the exit trap ending the ceiling watcher by its group, so a suite that ends inside its ceiling leaves that watcher sleep standing (issue #692)@s#^  loop_end_group "\${ceiling_watch:-}"$#  :#'
  '71.5@-@../loop-selftest.sh@driver#aSuiteEndsInsideTheCeilingItsCallerNamed@the exit trap ending a still-running case bound watcher by its group, so a suite the ceiling cut leaves that watcher sleep standing (issue #692)@s#^      loop_end_group "\${case_slot_watch\[\$slot\]:-}"$#      loop_end_pid "${case_slot_watch[$slot]:-}"#'
  '71.6@-@../loop-selftest.sh@driver#aCeilingTheSuiteFitsInsideChangesNothing@the case that ended normally swept with the watcher group, so every case of a green suite leaves one sleep standing (issue #692)@s#^  loop_end_group "\${case_slot_watch\[\$slot\]:-}"$#  loop_end_pid "${case_slot_watch[$slot]:-}"#'

  # 72.x — a run's report also says whether the loop is getting better (issue #247): the review round-1 pass rate
  # and the item wall median, minimum and maximum, totalled from the state the report already reads, with the third
  # figure — the landings that reddened `main` — named where it lives rather than read from the tracker. Measured
  # 2026-10-21: a person computed all three by hand from `run.json` and `items/*/reviewer-report-1.md` because no
  # report printed one.
  #
  # Review round 1 refused the first cut of this block, and the finding is why the rate is read from a round's
  # **verdict** and not from its count: `run.sh:1680` sets `review_rounds=$round` before the reviewer runs, so
  # `reviewRounds == 1` covers a passed round 1 and a refused one that then blocked alike — measured on the tree's
  # own state, #16 and #197 are blocked with `reviewRounds: 1` against `VERDICT: FAIL` reports, and #51 carries the
  # same count for a round 1 that was cut at its turn bound. `72.1` is that
  # defect stated as a mutation, so the case that reads the rate notices a numerator taken from the count.
  #
  # One record per invariant: the verdict the numerator is read from (`72.1`, the review's own finding), the middle
  # index a median sits at (`72.2`), the denominator (`72.3`), the two dashes a figure nobody measured prints
  # (`72.4`, `72.5`), the report's own offline-ness (`72.6`), the round that ended without a verdict being named
  # apart (`72.7`), and a state written before the verdict was recorded (`72.8`).
  '72.1@-@tools/loop/report.sh@report#trendFiguresFromState@the numerator read from the round count instead of the round verdict, so a round 1 that refused and then blocked is totalled as a pass — the review round 1 finding of this change (issue #247)@s#select((.v == "PASS" or .v == "NOTES") and .r == 1)#select(.r == 1)#'
  '72.2@-@tools/loop/report.sh@report#trendFiguresFromState@the middle index a median is taken at, so the smallest wall of the run is printed as its median (issue #247)@s#then .\[(length / 2) | floor\]#then .[0]#'
  '72.3@-@tools/loop/report.sh@report#trendFiguresFromState@the denominator of the pass rate, so a round that ended without a verdict is counted as a decided round (issue #247)@s#\[\$it\[\] | select(\(\.v | decided\))\]#[$it[] | select((.v | decided) or (.r >= 1))]#'
  '72.4@-@tools/loop/report.sh@report#noRoundOrWallPrintsDashNotZero@the dash a wall nobody measured prints, replaced by the zero that reads as a measurement (issue #247)@s#if (\$walls | length) == 0 then "- · no item recorded a wall time"#if (\$walls | length) == 0 then "median 0 s (min 0, max 0) · 0 items"#'
  '72.5@-@tools/loop/report.sh@report#noRoundOrWallPrintsDashNotZero@the dash a review rate nobody measured prints, replaced by a `0/0` (issue #247)@s#- · no round of this run ended in a verdict#0/0 first rounds passed#'
  '72.6@-@tools/loop/report.sh@report#landingsAreTheStoredNumber@the report making no tracker read of its own, so the number it says the state stores is fetched (issues #247, #709)@s#^loop_resolve$#loop_resolve\ngh issue list --label broken-main >/dev/null 2>\&1 || true#'
  '72.7@-@tools/loop/report.sh@report#trendFiguresFromState@the bucket a round that ended without a verdict is counted in, so it is silently absent from every figure the line prints (issue #247)@s#\[\$it\[\] | select((.r >= 1) and ((.v | decided) | not))\]#[$it[] | select(false)]#'
  '72.8@-@tools/loop/report.sh@report#noRoundOrWallPrintsDashNotZero@the rule that a state written before the verdict was recorded prints a dash instead of a rate over a count that cannot tell a pass from a refusal (issue #247)@s#\$withVerdict == 0 and#false and#'

  # 73.1 — a round the item turn bound ended has no answer to read (issue #247, review round 2). Measured on item
  # #51: its round 1 ended at the 250-turn bound, its transcript carries `LOOP-TIMEOUT` and no `VERDICT:`, and the
  # run read that silence as a refusal, so the record was `blocked` with `reviewRounds 1` and a rate counted it as
  # a refused round. The branch below records the cut instead, and the case that notices its absence asks what the
  # run own rate says about the item it just cut.
  '73.1@-@tools/loop/run.sh@run#aReviewerEndedAtItsTurnBoundIsNotARefusal@the branch recording a round the item turn bound ended as cut, so the silence of a reviewer that never answered is read as a refusal and buys a rework worker (issue #247)@s#^      if \[ -n "\$(report_field "\$reviewer_report" LOOP-TIMEOUT)" \]; then$#      if false; then#'
  # 74.1/74.2 — the change an item names (`agent-issue-loop`, "A change the item names is handed to its worktree" and
  # "A tier-B item is verified, never planned again"): the copy refuses what the tree does not hold, and the pre-screen
  # refuses a change the readiness checker no longer passes. Each is the one line that decides its refusal.
  '74.1@-@tools/loop/worktree.sh@worktree#aMissingChangeRefusesTheItem@the refusal of a change the tree does not hold, replaced by an empty change directory@s|^  if \[ ! -d "\$src" \]; then$|  if false; then|'
  '74.2@-@tools/loop/prescreen.sh@prescreen#aChangeThatIsNoLongerReadyIsRefused@the tier-B refusal decided on the readiness checker verdict@s|^          if \[ "\$readiness_status" -ne 0 \] .*$|          if false; then|'
  # 75.x — the shaping inbox (capability `shaping-inbox`): the label a run writes beside its hold when its verdict
  # was that a person must reshape the item, the list that decides which verdicts do, and the state write that takes
  # the label off. Each is one line, and the case that owns it asks the tracker what the item carries afterwards.
  '75.1@-@tools/loop/labels.sh@run#aShapingVerdictWritesTheLabel@the shaping label written in the same call as the hold, so the item is never held and nameless to the queue that exists for it (capability `shaping-inbox`)@s%^  add_labels+=("\$shaping_label")$%  : # the mutation leaves the item held and nameless%'
  '75.2@-@tools/loop/lib.sh@run#aVerdictOutsideTheListWritesNoLabel@the question `roles.shapingVerdicts` answers — whether a verdict is a shaping verdict — so every hold would carry the shaping label (capability `shaping-inbox`, D2)@s%^    \[ "\$named" = "\$verdict" \] && return 0$%    return 0%'
  '75.3@-@../issues/update.sh@labels#closeRemovesTheShapingLabel@the state write that takes the shaping label off the item whose outcome it records, so nothing ever leaves the inbox (capability `shaping-inbox`, D4)@s%^  if \[ -n "\$shaping_label" \] && grep -Fxq -- "\$shaping_label" <<< "\$labels_now"; then$%  if false; then%'

  # 76.1 — the gate ends the child that ran it, and a fresh child writes the archive (issue #744). Measured on the
  # archive 2026-11-02 (`python3 tools/loop-spend.py`): 1.43 B of the 1.83 B tokens of the 274 sessions that ran a gate
  # were charged after the first `LOOP-GATE` line. The condition below is the one line that decides whether the tail
  # child exists at all, so a tail made unreachable puts the item's whole derivation back into the archive phase — and
  # the case that owns it counts the children (`run#childUsageIsRecordedPerItem`), which one child cannot satisfy.
  '76.1@-@tools/loop/run.sh@run#childUsageIsRecordedPerItem@the tail child the gate line starts, and the item spend as the sum of two children (issue #744)@s|^  if \[ -z "\$pre_gate_refusal" \] \&\& \[ -z "\$empty_tree_reading" \] \&\& \[ -z "\$phase_failure" \] \&\& \[ "\$gate_exit" -eq 0 \] \&\& \[ "\$gate_write_decides" -eq 0 \] \&\&$|  if [ -z "$pre_gate_refusal" ] \&\& [ "$gate_exit" -eq 0 ] \&\& [ "$gate_write_decides" -eq 99 ] \&\&|'
  # 76.2 — the arm that records a red gate as the tail reported it (issue #744, review round 1). Without it the item
  # falls to "no archived change appears in the run's diff", which is the opposite of what the tail said and drops the
  # gate's own numbers; the case asks the reason for the gate's line and for the absence of that sentence.
  '76.2@-@tools/loop/run.sh@run#aRedGateIsBlockedByTheTail@the outcome arm that takes a blocked verdict as the tail reported it, with the gate line (issue #744)@s|^  elif \[ "\$verdict_claim" = "blocked" \]; then$|  elif false; then|'
  # 77.1 is the role-scoped command a project records instead of one command for every child. The head of the line
  # `run_child` resolves is the resolution itself: with it gone every child is back on `LOOP_AGENT_CMD`, which is the
  # shape before the key existed — so the case that names the invariant must see the reviewer's child move.
  '77.1@-@tools/loop/run.sh@state#aRolesOwnCommandStartsOnlyThatRolesChild@the role-scoped agent command that lets one role run on a command of its own@s#^  if \[ -n "\$.*then child_cmd="\$role_cmd"; fi$#  : the mutation keeps every child on the project command#'
  # 77.2 is the chain that resolution is read through (issue #821): a role's own key, the key of the role it belongs to,
  # then the project's one — with the item's class refining the role it is spelled after. The candidate walk that
  # returns the first recorded key is the chain; with it gone every child is back on `LOOP_AGENT_CMD`, which is what the
  # case's first assertion measures (the round before the gate is started on the reviewer's key it inherits).
  '77.2@-@tools/loop/run.sh@state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo@the chain that gives a role the key of its head role rather than the project one (issue #821)@s#^    if \[ -n "\$(loop_conf_value "\$candidate" | tr -d .\[:space:\].)" \]; then printf .%s. "\$candidate"; return 0; fi$#    if false; then :; fi#'

  # 78.x — a run reports the shape it spent its budget in, and names an item below the recorded floor (issue #757).
  # Measured over the archive: 36 825 tool calls in 33 183 turns, 1.11 per turn, at 78 k tokens a turn and 97 % cache
  # read — one call per turn is a chain in which every turn re-reads the whole conversation. Two sites in the report
  # (the trend floor and the table column) and one in the state (the counts the ratio is derived from), each with
  # the case `report#anItemBelowTheCallsPerTurnFloorIsNamed`.
  '78.1@-@tools/loop/report.sh@report#anItemBelowTheCallsPerTurnFloorIsNamed@the trend naming the items below the recorded calls-per-turn floor, so a chain of single-call turns is never flagged (issue #757)@s|select(\.r < \$floor)|select(false)|'
  '78.2@-@tools/loop/state.sh@report#anItemBelowTheCallsPerTurnFloorIsNamed@the table column that prints the per-item calls per turn (issue #757)@s|if \$calls == null or \$turns == null or \$turns == 0 then "-"|if true then "-"|'
  '78.3@-@tools/loop/state.sh@report#anItemBelowTheCallsPerTurnFloorIsNamed@the calls an item event stream records, so the ratio has nothing to be derived from (issue #757)@s|^         calls: (if \$turns > 0 then \$calls else null end),$|         calls: null,|'
  '78.4@-@tools/loop/report.sh@report#anItemBelowTheCallsPerTurnFloorIsNamed@the dash a state written before the two counts existed prints, replaced by a reason that names a run of items answering without a tool (issue #757)@s|select(.metrics.tokens != null)|select(false)|'

  # 79.x — the diff is reviewed before the item's gate, and a fresh child runs the one gate (issue #758). Measured on
  # the loop's archive (2026-11-03, 227 items): 1 581 of 2 591 Mtok — 61 % — is spent after the item's first gate, and
  # 117 child sessions ran the gate three times or more, because a finding a reader could have made from the diff alone
  # was paid for by a gate it did not need and then by a full rework pass. Four sites, each with the case that owns the
  # shape: which section the change child's prompt is handed, the block that runs the round and the gate child at all,
  # the hand-off of the findings to that child, and the outcome arm a round the run cut records.
  '79.1@-@tools/loop/run.sh@run#thePreGateReviewRunsBeforeTheGate@the section `worker.md` is handed, so the change child is told which end its session has (issue #758)@s|^  if \[ "\${1:-off}" = on \]; then cat "\$LOOP_DIR/prompts/gate-deferred.md"; else cat "\$LOOP_DIR/prompts/gate-own.md"; fi$|  if false; then cat "$LOOP_DIR/prompts/gate-deferred.md"; else cat "$LOOP_DIR/prompts/gate-own.md"; fi|'
  '79.2@-@tools/loop/run.sh@run#thePreGateReviewRunsBeforeTheGate@the block that reads the diff before the gate and starts the child that runs it (issue #758)@s|^  if \[ "\$pre_gate" = on \] \&\& \[ "\$worker_exit" -eq 0 \] \&\& \[ "\$worker_write_decides" -eq 0 \]; then$|  if false; then|'
  '79.3@-@tools/loop/run.sh@run#thePreGateReviewRunsBeforeTheGate@the findings the refuting round wrote, handed to the child that runs the gate (issue #758; the reading moved to the verdict reader with issue #793)@s|^        if \[ "\$pre_gate_verdict" = FAIL \] && \[ -n "\$blocking" \] && \[ "\$blocking" -gt 0 \]; then$|        if false; then|'
  '79.4@-@tools/loop/run.sh@run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun@the outcome arm a round the run cut records, so a change nobody read is left for the next run instead of closing (issue #758)@s|^  if \[ -n "\$pre_gate_refusal" \]; then$|  if false; then|'

  # 80.x — a round the run's own bound ended is recorded `cut`, not `blocked` (issue #785). Measured 2026-11-05: #780
  # had a tail that wrote `LOOP-VERDICT: closed`, a gate of `tests=460 failures=0`, an archived change and three
  # commits on its branch, and its review round 1 was ended at the turn bound after 53 turns — the item was recorded
  # `blocked`, escalated to a person and counted towards the run's two-blocked stop, as #783, #767 and #763 were the
  # same day. Three sites, each with the case that owns the shape: the result the cut arm writes, the cut that is
  # written on the branch so the round is not read as one nobody answered, and the queue's second pass the run skips
  # after a cut, which would re-exclude the item whose branch the cut child had just made.
  '80.1@-@tools/loop/run.sh@run#aReviewerEndedAtItsTurnBoundIsNotARefusal@the result a round the run ended at a bound records, so it is a block its owner owes an answer to (issue #785)@s|--result cut --verdict cut|--result blocked --verdict blocked|'
  '80.2@-@tools/loop/run.sh@run#aReviewerEndedAtItsTurnBoundIsNotARefusal@the cut written on the branch, so the round the run ended is read as one nobody answered and the branch as a question only a person can answer (issues #785, #262)@s|^  bash .*record-review-round .*"\$1" cut >&2.*$|  : # the mutation leaves the round announced and unanswered|'
  '80.3@-@tools/loop/run.sh@run#aReviewerEndedAtItsTurnBoundIsNotARefusal@the guard that skips the queue second pass after a bound cut an item the run just worked (issue #785)@s|^    \[ "\$deadline_cut" -eq 0 \] && \[ "\$bound_cut" -eq 0 \]; then$|    [ 0 -eq 0 ]; then|'
  # 81.x — a block the run's own bound caused is no refusal (issue #786): the mechanism itself (81.1) and the streak
  # `next` stops a run on (81.2). The third site — the escalation such a block no longer writes — is gone with the
  # guard 68.6 used to own: a bound-ended item leaves a `cut` and never reaches the block arm (issue #790), and
  # `run#childPastItsTurnBoundIsCut` asserts the same absence through the label stub's record. Measured 2026-11-05:
  # five of six runs stopped on the streak with 758 of 3 211 budgeted minutes used, and the first run carrying the
  # per-role turn bounds ended after 156 of its 480 minutes with three of six items ended at exactly 110 turns.
  '81.1@-@tools/loop/run.sh@run#childPastItsTurnBoundIsCut@the mechanism a bound-ended item records, so a bound reads as a refusal (issue #786)@s|^    if \[ "\$last_exit" -eq "\$LOOP_CHILD_TIMEOUT" \] \|\| \[ -n "\$timeout_claim" \]; then stop_reason="bound"; fi$|    : # the mutation records no mechanism|'
  '81.2@-@tools/loop/state.sh@state#aBlockAtTheRunsBoundIsNoRefusal@the streak read from refusals only, so two bound blocks stop a run whose queue and clock are untouched (issue #786)@s|select(.result == "blocked" and (.stopReason // "") != "bound")|select(.result == "blocked")|'

  # 82.x — a wait whose writer vanished ends on that fact, not on a clock (issue #795). Measured 2026-11-06: a run
  # started at 06:27:14Z was reparented to `systemd --user` when the session that owned it died, and it sat in the
  # poll for its child's completion record with a single `sleep 0.05`, its lock still held and item #793 still
  # `agent/in-progress`; found 17 minutes in, and the child cap would have ended the wait at 45 minutes with an item
  # blocked for a dead child. The mutation drops the liveness reading, so the case sees the wait the tree's poll was
  # — ended by a clock 3 600 s away — and nothing else changes: the record and every clock are untouched.
  '82.1@-@tools/loop/run.sh@run#aVanishedWriterEndsTheWait@the liveness of the subshell that writes the child record, so a writer that vanished is waited for until a clock (issue #795)@s|^    if \[ ! -e "\$status_file" \] && ! kill -0 "\$child" 2>/dev/null; then$|    if false; then|'
  # 83.x — a closing block a reviewer fenced as inline code is a complete answer (issue #793). Measured 2026-11-06 on
  # item #788: both of its pre-gate reports ended `LOOP-BLOCKING: 3` then `VERDICT: FAIL`, each line wrapped in
  # backticks and the file ending on the adapter's newline, and the run read two complete refusals as answers the
  # transport had cut. Without the un-fencing, the same report is a cut answer again.
  '83.1@-@tools/loop/run.sh@run#aFencedClosingBlockIsACompleteAnswer@the fence taken off a closing line before the report is judged complete (issue #793)@s#^  report_lines "\$1" | grep -qE "\$2" 2>/dev/null$#  grep -qE "\$2" "\$1" 2>/dev/null#'
  '84.1@-@tools/loop/gate.sh@gate#theDeclarationCaseIsOwedFirst@the cheap step the gate owes before its suites and its build, so a declaration one path token short is found at the end of the gate again (issue #792)@s|^owe_pairs first "\$first_steps"$|: # the mutation leaves the cheap step to the end|'

  # 85.x — the suite states what it cost (issue #782). Measured 2026-11-06 in the change root: `tools/loop/tests/loop-selftest.sh
  # --list` declares 326 cases, and each addition to that set was reviewed for what it asserts and by nobody for what
  # it costs, because the summary named the count, the JDK and the selection and nothing about time. Two sites: the
  # per-group time a case's end is charged to, and the `--times` gate that keeps the figure off a `PASS` line two
  # callers parse.
  '85.1@-@../loop-selftest.sh@driver#theSummaryNamesWhatEachGroupCost@the group figure a finished case is charged to, so the summary states a measured time and not a constant (issue #782)@s|^  group_ms\[\$case_group\]=\$(( \${group_ms\[\$case_group\]:-0} + elapsed ))$|  group_ms[$case_group]=0|'
  '85.2@-@../loop-selftest.sh@driver#theTimesFigureIsAskedForNotAssumed@the gate that keeps the per-case figure off a `PASS` line nobody asked for it on (issue #782)@s|^      if \[ "\$times" -eq 1 \]; then$|      if [ 1 -eq 1 ]; then|'

  # 86.x — an adoption the tree refuses is work, not the owner's decision (issue #794). Measured 2026-11-06 on #760:
  # the pre-screen excluded an item whose branch `branch-state` read `adoptable` the whole time, the adoption had
  # failed because the project tree was not clean, and the run escalated the item with the pre-screen's reason —
  # 12 h after its owner had answered the only question it had. The mutation drops the record the exclusion loop
  # writes, so the refusal is back to a line on the terminal and the item is back to a spent decision slot.
  '86.1@-@tools/loop/run.sh@run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable@the adoption of an admissible branch recorded beside the exclusion, so a merge the tree refused is work and not the owner.s decision (issue #794)@s|^    loop_state adoption-refused "\$issue" --reason "\$adoption_refusal" >/dev/null \|\|$|    true \|\||'
  # 87.x — a child has a search tool that runs (issue #796). Measured 2026-11-06 by `evidence/ctx-grep-census.py` over
  # this run archive: 282 of 482 `ctx_grep` calls answered `zsh:1: command not found: rg` (125 of 252 item directories),
  # while the binary sits in the agent's own bin directory — and the recipe every phase prompt and every guideline
  # header named for a document's section list, `grep -n '^## '`, is refused by the policy the same adapter loads, so
  # the child was given one call it could not make and one tool that does not run. One record per invariant: the
  # directory on the child's PATH, the scope that makes the wrapper answer one document, the first line that names what
  # was walked, the refusal that keeps a scope inside the tree, and the two documents that name the recipe.
  '87.1@-@../pi-child-events.sh@adapter#theChildIsStartedWithItsToolDirectoryOnPath@the agent'"'"'s own tool directory prepended to the child'"'"'s PATH, so a run started without it hands every worker no `rg` — the binary `ctx_grep` and the agent'"'"'s own subprocesses look for (issue #796)@s|^    \*) export PATH="\$agent_bin:.*$|    *) :|'
  '87.2@-@../look.sh@look#aPathScopesTheWalkToIt@the scope that answers one document'"'"'s section list instead of every document'"'"'s headings (issue #796)@s|^  target=\${scope%/}$|  target=.|'
  '87.3@-@tools/loop/prompts/worker.md@prompt#thePhasePromptsCarryTheirOwnRoute@the call every phase prompt names for a document'"'"'s section list, so the recipe a child is handed is the shell search its own policy refuses (issue #796)@s|tools/look.sh .\^## . --path|grep -n '"'"'^## '"'"'|'
  '87.4@-@../../AGENTS.md@prompt#thePhasePromptsCarryTheirOwnRoute@the call the document map names for a section list, so the prompts route a child into the same refused recipe one document further on (issue #796)@s|the complete section list of any file is `tools/look.sh .\^## . --path guidelines/<file>.md`|the complete section list of any file is `grep -n '"'"'^## '"'"' guidelines/<file>.md`|'
  '87.5@-@../look.sh@look#aPathScopesTheWalkToIt@the first line naming what the walk covered, so a scoped answer reads as a tree-wide one (issue #796)@s|^  where="\$root/\$target"$|  where="$root"|'
  '87.6@-@../look.sh@look#aPathScopesTheWalkToIt@the refusal that keeps a scope inside the tree, so `--path ../outside.md` reads the tree'"'"'s parent (issue #796)@s|^    \*/\.\./\*) printf |    */../*) : # |'
  '88.1@-@tools/loop/lib.sh@worktree#aSetAsideBranchWhosePatchesLandedIsRemoved@the set-aside namespace among the branches a sweep enumerates, so a branch the run set aside is invisible to both removal paths again (issue #803)@s|loop/leftover/\*|loop/leftover-never/\*|'

  # 256.x — the report splits a run's spend where it was spent (issue #759). Measured over 227 items (2026-11-03):
  # 89.6 h spent, model 30.1 h, the loop's gate 35.1 h and the loop's own self-tests 11.6 h, and 1 581 of 2 591 Mtok
  # (61 %) spent after the item's first gate — three splits no report carried, readable only by summing the raw
  # event streams by hand. Three sites, one recording and two lines: the phase figures the state records, the gate
  # bucket the trend totals, and the numerator of the share after the first gate.
  '256.1@-@tools/loop/state.sh@report#theSpendIsSplitByPhaseRoleAndGate@the phase split the stream records, so no phase the trend totals has a duration (issue #759)@s|else {modelMs: $modelMs, gateMs: $gateMs, otherMs: $otherMs} end)|else {modelMs: 0, gateMs: 0, otherMs: 0} end)|'
  '256.2@-@tools/loop/report.sh@report#theSpendIsSplitByPhaseRoleAndGate@the gate bucket the trend totals, so the phase that costs the most is printed as one that cost nothing (issue #759)@s#{n: "gate", ms: (\[$phs\[\].gateMs\] | add)}#{n: "gate", ms: 0}#'
  '256.3@-@tools/loop/report.sh@report#theSpendIsSplitByPhaseRoleAndGate@the numerator of the share spent after an item first gate, pinned to a constant so every run reads as fully after its gate (issue #759)@s#(((\$a / \$tot) \* 100)#((1 \* 100)#'

  # 258.x — every phase prompt names the cut a read result can arrive with (issue #818). Measured 2026-11-07 by the
  # reviewer-model probe over one 441 334 B / 6 639-line diff: the read tool caps one result at ~2 000 lines / 50 kB
  # (`docs/2026-11-07-reviewer-model-analysis.md:47`), so the diff must be paged, and of seven children one answered
  # the diff's last line byte-exact. The bound is applied by `tools/pi-trim-result.mjs` and was named nowhere a child
  # reads it. One record for the prompt the item names — the reviewer's, whose report is a claim about the text it read.
  '258.1@-@tools/loop/prompts/reviewer.md@prompt#thePhasePromptsCarryTheirOwnRoute@the sentence that names the marker a cut read carries, so a reviewer that holds a window grades it as the file (issue #818)@s#LOOP-TRIMMED#LOOP-WINDOW#'

  # 89.x — an item's kept stream is bounded and reported (issue #802). Measured 2026-11-06 over this archive: 254 event
  # streams held 3 483 MiB (p50 11.84, p90 29.88, max 63.47 MiB), and no cleanup arm named the 3 563 MiB of item
  # directories. Four invariants: the writer rotates at its bound, the turn count survives a rotation, the report's age
  # gate, and the report's reading of whether an issue still cites the directory.
  '89.1@-@../pi-child-events.sh@adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded@the bound the writer rotates at, so the stream of every item is appended for its whole life again (issue #802)@s#^          \[ "$kept_bytes" -le "$events_bound" \] || rotate_events "$events"$#          :#'
  '89.2@-@tools/loop/run.sh@run#aRotatedStreamStillCountsTheTurnsItDropped@the turns a rotated stream records added to the ones it still holds, so a child bound falls back to zero whenever the writer rotates (issue #802)@s#"$(( n + r ))"#"$n"#'
  '89.3@-@tools/loop/loop-clean.sh@clean#reportsAnAgedItemDirectory@the age gate that keeps an item directory an open issue may still cite, so a directory written a minute ago is released (issue #802)@s#-mmin -$(( archive_days \* 1440 ))#-mmin -0#'
  '89.4@-@tools/loop/loop-clean.sh@clean#reportsAnAgedItemDirectory@the run state reading of a landed and closed item, so a landed item is released whatever its issue still cites (issue #802)@s#(.metrics.closed // "no") == "yes"#(true)#'
  # 90.1 — the start-time pre-flight refuses a project tree that carries uncommitted work, because the landing step
  # merges into that tree and both of its merges refuse dirt there (issue #798). Without the guard the refusal arrives
  # after the item's whole budget and its gate: measured over this loop's archive, four items were recorded
  # `unmergeable` for it (#668, #675, #769, #782), and #782's two files cost one run and two items. The sed removes the
  # guard alone, so the case this record names fails on the exit it expects and on the paths nothing printed.
  '90.1@-@tools/loop/run.sh@run#aDirtyProjectTreeIsRefusedBeforeThePreScreen@the refusal of a run whose project tree carries uncommitted work, so the landing step refuses it after the item and its gate (issue #798)@s|^if \[ -n "\$project_dirt" \]; then$|if false; then|'
  # 91.1 is issue #797's invariant: the empty tree read at the diff, before the child that would run the gate. Without
  # it the run commits the change child's nothing, starts the tail anyway, and reads its own "could not start" stop on
  # the tail's empty answer — the item is not even recorded, and the gate phase the reading would have refused is what
  # eight records over the archive paid for (2026-10-23 #260 at 1 915 s, 2026-10-27 #319 at 1 422 s, 2026-11-03 #721 at
  # 916 s). The sed removes the reading alone: the outcome block's arm 9.5 owns still blocks the item, so the case this
  # record names fails on the children it asserts nobody started.
  '91.1@-@tools/loop/run.sh@run#anEmptyTreeIsRefusedBeforeTheGateChild@the reading that refuses an empty tree before the child that would run the gate, so nothing follows a child that committed nothing (issue #797)@s|^      if \[ "$head_now" = "$base" \]; then|      if false; then|'

  # 92.x — a phase the run's own bound ended is recorded `cut`, not `blocked` (issue #790): #786 recorded the
  # mechanism and left the result a block, so the item's own child is the arm the rule did not reach while the
  # review round was already cut (#785). The result (92.1), the mechanism kept beside it (92.2) and the same rule at
  # the second commit site — the rework worker of a refusing round (92.3). Measured
  # 2026-11-05: #788, #778 and #759 each carried `result: blocked` with `stopReason: bound`, a 4–6 k-line
  # `leftover-from-killed-run.patch`, and a note naming the turn bound the run itself had enforced. The ids
  # 85.x–91.x are taken on the base by #782, #794, #796, #803, #802 and #797.
  '92.1@-@tools/loop/run.sh@run#childPastItsTurnBoundIsCut@the result a bound-ended child records, so the item is read as a refusal the run'"'"'s own bound never measured (issues #786, #790)@s|^  if \[ "\$pre_gate_cut" -eq 1 \] \|\| \[ "\$stop_reason" = bound \]; then outcome="cut"; fi$|  if [ "$pre_gate_cut" -eq 1 ]; then outcome="cut"; fi|'
  '92.2@-@tools/loop/run.sh@run#childPastItsTurnBoundIsCut@the mechanism a cut keeps beside its result, so the spend of the bound that ended the phase is totalled under no name (issue #790)@s|^        --stop-reason "\$stop_reason" \\$|        --stop-reason "" \\|'
  '92.3@-@tools/loop/run.sh@run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted@the result a rework worker the bound ends records, so the item is read as a refusal the run'"'"'s own bound never measured (issue #790)@s|^        stop_reason="bound"$|        stop_reason=""|'

  # 93.x — a child pays for the full gate only the number of times the profile records (issue #756). Measured
  # 2026-11-03 over the archive's 227 items and 558 child sessions: the gate was started 910 times and spent 35.1 h,
  # 60 % of all tool wall clock, and 117 of the 245 sessions that ran it at all (48 %) ran it three or more times. The
  # mutation drops the comparison the refusal hangs on, so a third full run in one child is measured again.
  '93.1@-@tools/loop/gate.sh@gate#aThirdFullRunInOneSessionIsRefused@the refusal of a full gate run past the number the profile records, so a child cannot pay for the same suites three times (issue #756)@s|^    if \[ "\$count" -ge "\$runs_max" \]; then$|    if false; then|'

  # 94.x — a rework worker the run's own bound ended leaves a record on its branch, not a question for a person
  # (issue #817). #788 wrote `cut`/`bound` on the item and nothing on the branch, so `carried_review_state` read the
  # reviewer's own refusal as the branch's highest round and the next pre-screen named a phase the run had ended
  # itself `agent/needs-decision`. The sibling record 80.2 quotes the reviewer's cut (`"\$1" cut >&2`) and cannot
  # reach a line that ends `"\$1" rework-cut`, so this one site is the record the guard that writes it owes.
  '94.1@-@tools/loop/run.sh@run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted@the rework record written on the branch, so a later pre-screen reads the phase the run ended as work instead of a question its owner owes an answer to (issue #817)@s|^        record_rework_cut "\$round"$|        : # the mutation leaves the rework cut recorded nowhere|'
  # 95.x — the bounds an item's numbers are read against travel with the numbers (issue #815). The state named the
  # budget and the review rounds and nothing of the bound its children's tool results were cut at, so a run made at
  # another `LOOP_TOOL_RESULT_MAX_CHARS` — the one experiment #791's 200 104-token cut asks for — left a record whose
  # figures nobody could read back as that bound's. The record writes the value it read from the configuration; the
  # mutation writes a number no configuration holds.
  '95.1@-@tools/loop/state.sh@state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst@the character bound the run recorded for every tool result it cut, so a run at another bound is a record a reader can tell apart (issue #815)@s|--argjson toolResultMaxChars "\$tool_result_max"|--argjson toolResultMaxChars 0|'

  # 96.x — a run reports the reads its sessions repeated (issue #814). The worker prompt has stated the rule since
  # #246 and nothing counted it: #798 repeated 49 of 79 file reads and #791 30 of 55, and no report line could be
  # quoted either way, so the rule and the seam that caps it (#685) read the same as a child that ignores both. The
  # state records the count, the characters and the base it is a share of; the report prints them and names the worst
  # item. Both halves carry the case `report#anItemRecordsItsRepeatReads`.
  '96.1@-@tools/loop/state.sh@report#anItemRecordsItsRepeatReads@the count of reads a session repeated, so an item that reopens a path it already read is a record a report can show (issue #814)@s|^         dupReads: (if \$reads > 0 then \$dup.dup else null end),$|         dupReads: null,|'
  '96.2@-@tools/loop/report.sh@report#anItemRecordsItsRepeatReads@the worst item the repeat-reads line names, so the rate is printed beside nothing it can be acted on (issue #814)@s| chars re-injected| characters re-injected|'

  # 97.x — the gate an item ran and the round that read its diff reach the item's record whichever arm it ended on
  # (issue #813). `gate_field` was computed 448 lines before the `falsified` arm and passed by the `closed` arm alone, so
  # a blocked item — the arm most gates end in — recorded `metrics.gate: ""` and `report.sh` ordered a re-run of a gate
  # whose tally was on disk (the item's own record: #798 ran its gate for 856 s and recorded an empty field); the round
  # before the gate was read for its blocking count and its verdict was dropped, so the same item said no round was
  # read beside that round's own spend. Both halves carry one case each.
  '97.1@-@tools/loop/run.sh@run#aRedGateIsBlockedByTheTail@the gate tally the four arms that do not close pass, so an item that ran the gate and then blocked records an empty field and is ordered a re-run it already paid for (issue #813)@s|^        --gate "\$gate_field" \\$|        --gate "" \\|'
  '97.2@-@tools/loop/run.sh@run#aBlockedItemRecordsTheRoundThatReadIt@the verdict and the count of the round that read the diff, so an item that read one records `off`/`0` beside the spend that round made (issue #813)@s|^  if \[ -z "\$pre_gate_refusal_verdict" \] \&& \[ -n "\$pre_gate_verdict" \]; then$|  if false; then|'
  # 98.x — what a close over a red gate records (issue #811). Measured 2026-11-06: 3 of the 7 closed items of one run
  # carry `errors=1` / `failures=1` beside `verdict: closed`, each waived in prose inside its closing child transcript,
  # so the same flake was diagnosed twice in one run and counted zero times. The refusal is what makes the clause a
  # field rather than a sentence, the write is what the report counts, and the run is what carries the child's clause
  # into the record; one case each.
  '98.1@-@tools/loop/state.sh@state#aCloseOverARedGateWithoutAWaiverIsRefused@the refusal of a close over a red tally that records no waiver, so the judgement lives in a transcript and the item reads `closed` with `errors=1` (issue #811)@s|^  elif \[ "\$result" = done \] && .*$|  elif false; then|'
  '98.2@-@tools/loop/state.sh@state#aCloseOverARedGateRecordsWhyItWasWaived@the clause a close over a red gate was waived with, so a report can count the waiver instead of trusting prose (issue #811)@s|^                           gateWaived:(if \$gw == "" then null else \$gw end),$|                           gateWaived:null,|'
  '98.3@-@tools/loop/run.sh@run#aCloseOverARedGateIsWaivedOrBlocked@the clause the closing child recorded, so the run blocks an item it was handed a waiver for and the record never says why the red was taken (issue #811)@s|^  gate_waived=\$(report_field "\$final_report" LOOP-GATE-WAIVED)$|  gate_waived=""|'
  # 99.x — the closure's claim read against the base branch (issue #715). Measured 2026-10-30: six branches carried
  # work the base did not have and five of them belonged to items the tracker showed `CLOSED status/fixed-by`, because
  # a closure's evidence is a branch and nothing compared it with the branch it has to be an ancestor of. Two halves,
  # one case each: the tooling's own refusal (`tools/issues/tests/close-mutations.json` A1.6) and the loop's ask, which happens
  # *before* the state write so a refused closure leaves the item unsettled.
  '99.1@-@tools/loop/lib.sh@labels#closeRefusesACommitTheBaseDoesNotCarry@the loop asking the base branch before it settles the item, so the state write lands and the closure is refused after it — the item reads settled on a fix the tree does not have, which no later run revisits (issue #715)@s|^  git -C "\$LOOP_ROOT" merge-base --is-ancestor "\$commit" "\$base" >/dev/null 2>&1 \&\& return 0$|  return 0|'
  # 261.1 — the profile's shaping list and the run's write are one answer (issue #738). The list named
  # `needs-diagnosis` while `run.sh`'s skipped arm escalated that verdict to the decision label and never wrote the
  # shaping one, so the declaration and the write disagreed and no case compared them: the matrix case called
  # `labels.sh` directly, which is not a path a run takes. Putting the verdict back in the list is what the new case
  # reads.
  '261.1@-@../tooling.json@run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames@the verdict the profile declares a shaping verdict while the run escalates it, so the list and the labels a skip leaves disagree (issue #738)@s%^    "shapingVerdicts": \["unspecifiable", "too-wide", "not-spec-shaped"\],$%    "shapingVerdicts": ["needs-diagnosis", "unspecifiable", "too-wide", "not-spec-shaped"],%'

  # 262.x — a gate record names the tree it measured (issue #819). Measured 2026-10-22: a change was archived with a
  # gate its own round-2 corrections never ran — every XML in that archive's `evidence/gate/` is stamped 12:23–12:26
  # against the round-2 runs at 12:42–12:43 — and nothing in the tree could say so. The record loses the identity
  # (262.1), the run stops writing the same identity beside the diff a round is handed (262.2), the digest stops
  # depending on the change at all (262.3), and the one derivation stops leaving out the generated trees the round's
  # diff leaves out (262.4), which is the state where the gate and the round name two trees on a tree nobody corrected.
  '262.1@-@tools/loop/gate.sh@gate#aThirdFullRunInOneSessionIsRefused@the tree a full run records, so its record cannot be attributed to the content it measured (issue #819)@s|^    printf '\''%s\\t%s\\t%s\\n'\'' "\$session_key" "\$gate_argv" "\${tree_identity:-head=unknown diff=unknown}" >> "\$runs_record" 2>/dev/null \|\| true$|    printf '\''%s\\t%s\\n'\'' "$session_key" "$gate_argv" >> "$runs_record" 2>/dev/null \|\| true|'
  '262.2@-@tools/loop/run.sh@run#aReviewDiffNamesTheTreeItWasWrittenFrom@the identity a round is handed beside its diff, so the reviewer compares a gate against a tree it never saw (issue #819)@s|^  loop_change_identity "\$worktree" "\${base:-\$1}" > "\$2\.identity"$|  true|'
  '262.3@-@tools/loop/lib.sh@gate#aThirdFullRunInOneSessionIsRefused@the digest of the change itself, so a tree corrected after the gate reads as the tree the gate measured (issue #819)@s|^  printf '\''head=%s diff=%s'\'' "\$head" "\$digest"$|  printf '\''head=%s diff=%s'\'' "$head" "0000000000000000"|'
  '262.4@-@tools/loop/lib.sh@gate#theGateAndTheRoundNameOneTree@the path set the one derivation leaves the generated trees out of, so a gate reads its own evidence as a correction of the tree it measured (issue #819)@s|^  loop_tree_identity "\${1:-}" "\${2:-}" "\$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)"$|  loop_tree_identity "${1:-}" "${2:-}"|'
  # 100.1 — the record an unreadable item gets instead of the stop, and the `break` that follows it (issue #808).
  # Restoring the stop is what the base did: the item is left unrecorded and the run ends on one item's socket. The
  # `--note` line that followed the record is left as a second command and fails harmlessly (`|| true`), so the
  # mutated file stays valid. The `break` is restored with it, because a `stop_run` that only records leaves the
  # candidate in the queue and the run takes it again — the case would be killed by its own timeout instead of
  # failing at its first assertion, which is the shape `tools/loop/tests/loop-mutations.sh:1175–1182` names as unreadable.
  '100.1@-@tools/loop/run.sh@run#anUnreadableItemIsNotTheEndOfTheRun@the record an item the tracker did not answer for gets and the break that follows it, so a read that fails is the whole stop again and the item is left unrecorded (issue #808)@s|^    loop_state done "\$issue" --result blocked --verdict unreadable \\$|    stop_run "gh could not read #\$issue — the tracker did not answer" \\|;;s|^    continue$|    break|'
  # 101.1 — the reading that asks a phase whose answer is not an answer once more (issue #810). Without it the gate
  # child's 0-byte report starts no tail and the item is decided from the empty file, so its reason is the arm that
  # names the item's own tree: "reported success but no archived change appears in the run's diff" over a branch that
  # carries a commit, which is the note item #755 was recorded with after 11 m 49 s.
  '101.1@-@tools/loop/run.sh@run#aPhaseThatAnswersNothingIsThatPhasesFailure@the reading that asks a phase whose report carries no closing line once more, so a child that exits 0 and answers nothing is read as the item'"'"'s own tree (issue #810)@s|^        if \[ "\$gate_exit" -eq 0 \] && ! gate_answered "\$gate_report"; then$|        if false; then|'
  # 102.1 — the refusal read out of the child's own stream and named in the stop (issue #828). Without it the stop is
  # the shape the run always wrote — the status and the answer's size — which could not tell a provider that refused
  # every call from a child that broke, so a run ended on the first refused child and its record named neither the
  # provider nor the refusal. The engine is otherwise unchanged: only the value named in the reason is emptied.
  '102.1@-@tools/loop/run.sh@run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault@the refusal read from the child'"'"'s own event stream and named in the stop reason, so the empty answer a refused provider leaves reads once more as a broken child (issue #828)@s|^    refused_by=\$(child_refusal "\${events_file:-}")$|    refused_by=""|'
  # 263.x — which child owns a selection (issue #816). The change child was told "It runs none of them" and the round
  # before the gate refused the checks it had not run for want of a log, so item #798's child ran the gate's own
  # selection inside its own session — 129 cases in 219.93 s and 349 cases in 446.01 s — and the gate child then ran
  # both again. One record per prompt that states the split, each mutating the sentence its case asserts. Renumbered
  # from the branch's own 95.x when this landed: the tree had already spent 95.1 on the bounds issue #815 records.
  '263.1@-@tools/loop/prompts/worker.md@prompt#theChangeChildCitesTheMeasureBlockAndRunsNoSuite@the gate named as the owner of the full selection, so the change child runs that check itself (issue #816)@s|gate owns the full selection|the gate is run by the child that runs it|'
  '263.2@-@tools/loop/prompts/pre-gate-review.md@prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle@the claim the round before the gate may refuse, so a check the gate will run is refused for want of a log the change child never produced (issue #816)@s|a claim the gate cannot settle|a claim nobody has logged|'

  # quota-hold.x — the quota a run waits on instead of ending on. The records for the change that added the hold
  # (2026-11-08), labelled by the change rather than by a tracker item: the wait is turned into a status of the
  # run (6), the check that could not be made stays no refusal, an empty key stays no question, the wait moves the
  # deadline with it, and the run pauses on the status instead of reading it as a stop.
  'quota-hold.1@-@tools/loop/state.sh@state#aQuotaWindowIsAHoldNotAStop@the hold a reader returns becoming the run paused status, not one of its stops@s|^          exit "\$LOOP_HOLD"$|          exit "$LOOP_STOP"|'
  'quota-hold.2@-@tools/loop/state.sh@state#aQuotaCheckThatCannotBeMadeIsNotARefusal@the reading that could not be made staying no refusal@s|^          loop_note "state: the quota check|          exit "$LOOP_HOLD" # the mutation holds on a reader that failed|'
  'quota-hold.3@-@tools/loop/state.sh@state#noQuotaCommandAsksNothing@an empty key meaning no question at all@s|^    quota_cmd=\$(loop_conf_value LOOP_QUOTA_CMD)$|    quota_cmd="\${quota_cmd:-bash .pi/quota-stub.sh}"|'
  'quota-hold.4@-@tools/loop/state.sh@state#aHoldMovesTheDeadlineWithIt@the wait moving the deadline with it, so a pause is not spent budget@s|.run.deadlineEpoch += \$seconds|.run.deadlineEpoch += 0|'
  'quota-hold.5@-@tools/loop/run.sh@run#aQuotaHoldPausesTheRunAndResumesIt@the run pausing on a hold instead of ending on it@s|^  if \[ "\$next_status" -eq "\$LOOP_HOLD" \]; then$|  if false; then|'
  'quota-hold.6@-@tools/loop/report.sh@report#theSpendIsWorkNotWaiting@the spend figure excluding the wait, so a paused run reports work rather than clock@s|\$(date +%s) - run_epoch ) / 60 - paused_min|$(date +%s) - run_epoch ) / 60|'
  'quota-hold.7@-@tools/loop/run.sh@run#theQuotaSwitchIsExplicit@the flag emptying the reader key for one run, so a person can start a run whose provider the reader does not answer for@s|^  export LOOP_QUOTA_CMD=""$|  : # the mutation keeps the reader armed|'
  'quota-hold.8@-@tools/loop/loop.conf@run#theQuotaSwitchIsExplicit@a set-but-empty environment value winning over the file, so the key can be switched without editing it@s|{LOOP_QUOTA_CMD-bash|{LOOP_QUOTA_CMD:-bash|'
)

# The harness's two probes: they prove the harness's own guards can fail, on a throwaway copy of the engine
# (`tools/loop/tests/loop-mutations-selftest.sh`). A probe is a mutation like any other, with a deliberately wrong or
# deliberately harmless shape.
PROBE_RECORDS=(
  'probe-no-apply@-@tools/loop/state.sh@state#initAndShowRoundTrips@a pattern that matches nothing@s|^# this line is not in the file$|# mutation|'
  'probe-stays-green@-@tools/loop/report.sh@report#headerAndTableShape@a real edit no case in the set asserts@s|^set -uo pipefail$|&\n# a probe as a second line|'
  # 43.x — the environment a kept worktree records, which the gate itself must run under (measured 2026-10-23,
  # issue #267: a hand-run gate in the worktree died at configuration in 21 s, having executed no test, because
  # only `run.sh` sourced the file).
  '257.1@-@tools/loop/gate.sh@gate#runsUnderTheWorktreeEnvironment@the worktree environment the gate itself runs under@s|^if \[ -f "\$LOOP_ROOT/.pi/loop-env.sh" \]; then$|if false; then|'
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
  case_log="$scratch/case.out"; : > "$case_log"
  if [ "$fifo" != "-" ]; then
    release_fifo "$fifo" &
    writer=$!
    PI_STUB_WAIT="$fifo" LOOP_ENGINE="$engine" LOOP_CONF="$engine/loop.conf" \
      timeout "$case_timeout" bash "$root/tools/loop/tests/loop-selftest.sh" --case "$case_name" >"$case_log" 2>&1
  else
    LOOP_ENGINE="$engine" LOOP_CONF="$engine/loop.conf" \
      timeout "$case_timeout" bash "$root/tools/loop/tests/loop-selftest.sh" --case "$case_name" >"$case_log" 2>&1
  fi
  status=$?
  if [ -n "$writer" ]; then kill "$writer" 2>/dev/null; pkill -P "$writer" 2>/dev/null || true; fi
  return "$status"
}

# The assertion a red case failed on, and when: a `RED` line that names only the case is a label a reader has to
# re-run to check, and the round-1 review of #674 read the evidence as one that carried no failing message and no
# timestamp. The suite prints the case's own failing assertion before its `FAIL <case>` line, so the first line that
# carries the suite's `<case>|   <message>` shape is the one that names the failure.
case_reason() {
  [ -s "${case_log:-}" ] || return 0
  sed -n 's/^[^|]*|  *//p' "$case_log" | head -1 | cut -c1-240
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
    printf '       %s — %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(case_reason)"
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
