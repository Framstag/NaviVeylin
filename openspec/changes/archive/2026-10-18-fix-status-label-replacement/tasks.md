# Tasks

## 1. The case that is red on HEAD

- [x] 1.1 Add `update#statusReplacesTheOldStatus` to `tools/issue-tooling-cases.sh`: a fixture issue carrying
      `status/open`, one recorded `update.sh <n> --status fixed-by` (`GH_STUB_RECORD`), asserting that exactly one
      `gh issue edit` line carries both `--add-label status/fixed-by` and `--remove-label status/open`. Verify it
      fails on HEAD: `bash tools/issue-tooling-selftest.sh --case update#statusReplacesTheOldStatus` exits non-zero
      naming the missing `--remove-label`.

## 2. The displacement filter, shared

- [x] 2.1 Add a pure filter to `tools/issues/lib.sh` — given the issue JSON and the target label, it prints every
      label of the status namespace except the target, one per line, and makes no `gh` call. Verify with a case
      (`bash tools/issue-tooling-selftest.sh --case update#statusReplacesTheOldStatus` still drives it, and an
      issue carrying no status label yields no output).
- [x] 2.2 Point `tools/issues/reopen.sh:99-117` at that filter instead of its inline loop. Verify the reopen paths
      stay green: `bash tools/sensor-selftest.sh` (the reopen lifecycle) and
      `bash tools/check-portability-selftest.sh` (the reopen case in `tools/portability-cases.sh:415`; the file
      name in this task was wrong when it was written — measured 2026-10-18).

## 3. The write path

- [x] 3.1 In `tools/issues/update.sh`, when `--status <v>` is given: read the issue with one
      `gh issue view <n> --json labels`, remove every displaced status label from that read, and emit
      `--add-label <target>` plus one `--remove-label` per displaced label in the single `gh issue edit` the script
      already issues. Verify: `update#statusReplacesTheOldStatus` is green.
- [x] 3.2 Write only the difference: no `--add-label` for a label the issue already carries, and never both flags
      for one label. Add `update#statusReplacementIsIdempotent` (an issue carrying only `status/open`, written with
      `--status open`, records no `--add-label` and still carries exactly one status). Verify the case is red on
      HEAD and green after 3.1.
- [x] 3.3 Refuse a request that names two states: with `--status <v>` present, a `--label status/<other>` or a
      `--remove-label status/<v>` is refused with exit 2 and a reason naming both values, before the read and
      before any write. Add `update#statusRejectsTwoStatesInOneCall` (no `gh issue edit` recorded). Verify: the case
      is red on HEAD and green after this task.
- [x] 3.4 Report the whole transition: `--json`'s `removedLabels` carries the displaced labels too. Add
      `update#statusReportNamesImpliedRemoval` and verify the recorded JSON names them; red on HEAD, green after
      3.1.

## 4. The invariant, where the other tracker invariants live

- [x] 4.1 Add the row to `guidelines/Backlog.md` §4: a state transition replaces the namespace's value, measured by
      `update#statusReplacesTheOldStatus`. Verify by reading the row beside I1-I7 and by
      `grep -n 'status' guidelines/Backlog.md` showing the row and its measurement; no other section restates the
      rule (AGENTS.md already states the exactly-one fact and needs no edit).

## 5. Integration and falsification

- [x] 5.1 Run the suites the changed engines are covered by and record their tallies: `bash
      tools/issue-tooling-selftest.sh`, `bash tools/resolve-stale-selftest.sh`, `bash tools/sensor-selftest.sh`,
      `bash tools/check-portability-selftest.sh`, `bash tools/loop-selftest.sh`. Verify every suite is green and
      that no case asserts the absence of the new `--remove-label`. All five green — 73 (4 new), 20, 36, 21, 53
      cases — after one case *did* assert the absence: `labels#verdictToLabelMatrix` in `tools/loop-cases.sh`
      compared the removed set exactly, so its six state-setting verdicts now name the displaced `status/open`.
      That is this change's behaviour showing up at the loop level, not a narrowing: the case would fail again if
      a verdict only added a state. Tallies are in `evidence/revert-check.md`.
- [x] 5.2 Falsify the fix (task said: delete the removal loop, confirm
      `update#statusReplacesTheOldStatus` goes red and names it, restore, green again). Done as the project's
      versioned form: four records (one per ADDED scenario) in `tools/status-mutations.json`, run with
      `tools/mutate.sh` — 4 red, 4 restored green, zero problems. The set was first written beside this
      change's evidence and promoted to `tools/**` with a CI `--list` line beside the issue-tooling step on
      the owner's decision, so the falsification outlives the archive; `evidence/revert-check.md` carries the
      transcript, the uniqueness check on every search, and the three cases shown red against `git show
      HEAD:tools/issues/update.sh`.

## 6. The damage already in the tracker

- [x] 6.1 Find the items that carry two `status/*` labels — `tools/issues/list.sh --json` over the tracker — and
      repair each with one `update.sh` call that keeps the state the item's own record implies (a closed item keeps
      `status/fixed-by`, an open parked item keeps `status/on-hold`). Verify the same query reports no item with
      two states afterwards, and each repaired item is named in the change's evidence. Done: exactly #109 and
      #110 carried two states, each was repaired with `update.sh <n> --status on-hold` (which removed
      `status/open` and added nothing), and the tracker-wide query now reports none. Transcript in
      `evidence/revert-check.md`, "The damage that was already in the tracker".

## 7. The state named as a label (the independent review's P1, owner-approved into this change)

- [x] 7.1 In `tools/issues/update.sh`, fold a state named as the namespaced label into the value spelling: the
      label is pulled out of the plain labels and treated as the target before the contradiction checks, the
      vocabulary gate and the read, so a check's escalation (`--label status/on-hold` from the registry) replaces
      the item's state exactly as `--status on-hold` does. Verified by
      `update#stateNamedAsALabelReplacesTheOldState` (adds the other label and the state, removes the displaced
      one, and refuses two states on this spelling with exit 2).
- [x] 7.2 Strengthen the sensor case that pinned the add-only write: `tools/sensor-cases.sh`'s
      `run#escalationNeverSetsEligibility` now gives the fixture item the state a filed item carries
      (`status/open`, from the registry's `.labels`) and asserts the escalation's write is exactly the two adds
      *plus* `--remove-label status/open`. Shown red against `git show HEAD:tools/issues/update.sh` (the removed
      label absent, nothing else) and green with the fix.
- [x] 7.3 The scenario `update#stateNamedAsALabelReplacesTheOldState` in the delta spec (the requirement text was
      already phrased over "a call that sets an item's lifecycle state"), decision D8 in `design.md` with the two
      alternatives eliminated, and mutation record `7.1` in `tools/status-mutations.json` — 5 of 5 red, every
      restore green.

## Workflow follow-up

- Independent read-only review of the change before archive.
- Archive the change with `openspec archive fix-status-label-replacement`, then verify the archived delta landed in
  `openspec/specs/issue-backlog/spec.md` with every existing scenario intact.
- Close #178 through the fixed path (`update.sh --status fixed-by`, then `close.sh` with its reason and evidence),
  which is the first transition the fix governs.
