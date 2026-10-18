# Falsification — `fix-status-label-replacement`

> **After the archive (2026-10-18).** The change was archived as
> `openspec/changes/archive/2026-10-18-fix-status-label-replacement` (2 requirements added to
> `issue-backlog`, 0 modified, 0 removed — no existing scenario was at risk, which was the point of the
> ADDED-only delta). #178 was then moved through the fixed path, and its closure attempted twice — the first
> attempt is kept here because it shows the gate refusing the tooling's own work:
>
> ```
> $ bash tools/issues/update.sh 178 --status fixed-by --json
> {"issue":178,"labels":["status/fixed-by"],"removedLabels":["status/open"]}
>
> $ bash tools/issues/close.sh 178 --reason "update.sh --status replaces …" --evidence "…" --json
> {"closed": false, "outcome": "refused", "issue": 178, "reason": "missing-eligibility-label",
>  "detail": "agent/eligible absent"}
> ```
>
> That refusal is the gate working, not a failure of the change: #178 was not authorised for automated work, the
> `agent/eligible` label is reserved and no write path may set it (`label-reserved`, every path), so its closure
> was the owner's act and not one the session could take. Nothing was posted and nothing was closed by the
> refused attempt. The owner then applied the opt-in, and the second attempt went through the tooling's own
> path:
>
> ```
> $ bash tools/issues/close.sh 178 --reason "update.sh --status replaces the item's state instead of accumulating it" \
>     --evidence "archived change 2026-10-18-fix-status-label-replacement: …" --json
> {"closed": true, "outcome": "closed", "issue": 178, "stateReason": "completed", …}
> $ gh issue view 178 --json state,labels | jq -c '{state, labels: [.labels[].name] | sort}'
> {"state":"CLOSED","labels":["agent/eligible","category/build-and-harness","class/bug","prio/p3",
>   "source/agent","status/fixed-by"]}
> ```
>
> The review's P2-1 was filed through the tooling as **#179** (with the offline reproduction, no opt-in label: a
> filing is not an authorisation).


One mutation per invariant, the named case must go red, the file restored, the case green again. The rule is
`.pi/skills/revert-check`; the mechanism is `tools/mutate.sh` (`.pi/skills/mutation-runner`). The set is
versioned in the repository at `tools/status-mutations.json`, beside the runner it is meant for, and the CI
step that owns the issue tooling prints its `--list` — the shape `tools/resolve-stale-mutations.json` has.

```
$ bash tools/mutate.sh --mutations tools/status-mutations.json
5.1	update#statusReplacesTheOldStatus	the displaced state labels being read from the item, so the add and the removals ride one edit
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.2	update#statusReplacementIsIdempotent	the difference-only rule — a state write does not add the label the item already carries
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.3	update#statusRejectsTwoStatesInOneCall	a call naming two states being refused instead of silently resolved
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.4	update#statusReportNamesImpliedRemoval	the report carrying the displaced labels the caller never named
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 4 mutation(s) run, 4 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

Every search is a single line and unique in `tools/issues/update.sh` (checked with `grep -F -c` before the
set was written), so `MUTATION-DID-NOT-APPLY` is what a stale pattern would report rather than a silent pass.

## Red on HEAD, per case

Two of the four cases were also run against the tree's own `update.sh` — the pre-change file taken with
`git show HEAD:tools/issues/update.sh` — because a case that was written *after* the fix has to be shown to
have been red **before** it:

```
$ git show HEAD:tools/issues/update.sh > tools/issues/update.sh
$ bash tools/issue-tooling-selftest.sh --case update#statusReplacementIsIdempotent
  no label is added again: expected '0', got '1'
FAIL update#statusReplacementIsIdempotent
$ bash tools/issue-tooling-selftest.sh --case update#statusRejectsTwoStatesInOneCall
  a status-namespace label beside --status is a usage error: expected '2', got '0'
FAIL update#statusRejectsTwoStatesInOneCall
$ bash tools/issue-tooling-selftest.sh --case update#statusReportNamesImpliedRemoval
  and the state it displaced, which the caller never named: 'status/open' not found in: []
FAIL update#statusReportNamesImpliedRemoval
```

`update#statusReplacesTheOldStatus` carries the same proof in its first run, which is where the change began:

```
$ bash tools/issue-tooling-selftest.sh --case update#statusReplacesTheOldStatus
  the displaced state is removed in the same write: '--remove-label status/open' not found in:
    gh issue edit 11 --add-label status/fixed-by
FAIL update#statusReplacesTheOldStatus
```

## The suites the changed engines are covered by

Run after the last code edit, all offline, no token, no Gradle, JDK 27:

```
issue-tooling      issue-tooling-selftest: all 76 cases passed (7 of them new)
resolve-stale      resolve-stale-selftest: all 20 cases passed
sensor             sensor-selftest: all 36 cases passed      (the reopen lifecycle uses the shared scan)
loop               loop-selftest: all 53 cases passed        (one expectation named the displaced label)
check-portability  check-portability-selftest: all 21 cases passed
```

The tallies are after the independent review of 2026-10-18 (`evidence/review.md`), its four in-scope fixes and
the owner-approved P1 fix (task group 7): `lib#statusPrefixNeedsTheProfile`,
`update#statusWriteReportsAnEmptyTransition` and `update#stateNamedAsALabelReplacesTheOldState` are the three
cases beyond the delta's first four, and the mutation set was re-run afterwards — 5 of 5 red, every restore
green. Two of the runner's guards fired during the work and neither passed silently: its 5.1 record reported
`MUTATION-DID-NOT-APPLY` once the prefix became a parameter, and records 5.3/7.1 were re-pointed at lines that
are unique in `tools/issues/update.sh` (the state-namespace pattern line appears twice now, so a mutation there
would have been two mutations at once).

## The sensor escalation (P1), proven red before it was green

The case that pinned the add-only write now gives its fixture item the state a filed item carries and asserts the
escalation's whole label write. Against the tree's own `update.sh` (`git show HEAD:tools/issues/update.sh`):

```
$ git show HEAD:tools/issues/update.sh > tools/issues/update.sh
$ bash tools/sensor-selftest.sh --case run#escalationNeverSetsEligibility
  … expected '--add-label agent/needs-decision --add-label status/on-hold --remove-label status/open',
    got '--add-label agent/needs-decision
--add-label status/on-hold'
FAIL run#escalationNeverSetsEligibility
```

That is the whole of P1: the escalation set the new state and left the item's own `status/open` in place, so an
escalated item carried two states. With the fix the case is green and the mutation record `7.1` (which makes
`update.sh` recognise the label and then drop it) turns it red again.

`check-portability` matters twice here: the shared scan lives in `tools/issues/lib.sh`, which is an engine, so
the new code may name the namespace but no project value — and the matrix case in `tools/loop-cases.sh` had to
name the displaced label, which is the loop's own integration of the replacement.

## The damage that was already in the tracker

Task 6.1, done with the fixed write path rather than by hand. The detection is the tracker's own query, and it
found exactly the two items #178's body named:

```
$ gh issue list --state all --limit 400 --json number,labels | jq -r '[.[] | {number, status:
    ([.labels[]? | .name | select(startswith("status/"))])} | select((.status | length) > 1)] | .[]'
#110 [status/on-hold status/open] §176 The first favourites template can take seconds on a cold session …
#109 [status/on-hold status/open] §175 A car screen's observation body is still a main-thread provider …
```

Both are open and parked, so `on-hold` is the surviving state; the repair is one call each, and neither call
adds the label the item already carries — the report says what it removed:

```
$ bash tools/issues/update.sh 109 --status on-hold --json
{"issue":109,"labels":[],"removedLabels":["status/open"]}
$ bash tools/issues/update.sh 110 --status on-hold --json
{"issue":110,"labels":[],"removedLabels":["status/open"]}
```

## A live write that should not have happened, and its repair

Recorded because a reader of this change has to know every write that touched the tracker, including a
mistake. While reproducing the review's P1 by hand, a scratch run of `update.sh` was started without the stub
`gh` first on `PATH` (the harness's own cases set it; a bare command in the session did not). `GH_STUB_DIR`
was therefore inert and the **real** `gh` ran:

```
16:54:09Z  labeled   agent/needs-decision   on #11
16:54:09Z  labeled   status/on-hold         on #11
```

#11 is a live, `agent/eligible`, `status/open` item (§171, the archived capabilities' placeholder purpose), so
that run added an unearned `agent/needs-decision` and — through the very defect this change removes — a second
state. The inverse was applied through the tooling at 16:55:43, and #11's label set is byte-for-byte what it
was before:

```
16:55:43Z  unlabeled agent/needs-decision   on #11
16:55:43Z  unlabeled status/on-hold         on #11
$ gh issue view 11 --json labels | jq -r '[.labels[].name] | sort | join(", ")'
agent/eligible, category/specs-and-process, class/improvement, prio/p3, source/todo-import, status/open
```

The two label events stay in #11's timeline, which is the tooling's own rule (nothing is deleted; history is
what makes a closure auditable). No other issue was touched: the tracker-wide query below is the check, and it
was re-run after the repair. The lesson for the next session is the harness's, not the tooling's: a write-path
probe is run through `tools/issue-tooling-selftest.sh` or with the fixture stub explicitly first on `PATH` —
never as a bare `update.sh` call.

And the same query that found them now finds nothing, so the invariant holds over the whole tracker and not
only over the writes that followed the fix:

```
$ … the same jq filter, over every item, any state
OK: no item carries two status labels
$ gh issue view 109 --json labels | jq -c '[.labels[].name | select(startswith("status/"))]'
["status/on-hold"]
```
