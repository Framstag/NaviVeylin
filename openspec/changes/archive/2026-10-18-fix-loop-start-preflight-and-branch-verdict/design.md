# fix-loop-start-preflight-and-branch-verdict — design

The root cause is measured, so each decision below names the alternatives the evidence eliminated rather than
a menu of options (`openspec/config.yaml`, design: the bug-fix exception).

## D1 — The check is right; its position was the defect (`run.sh`)

`worktree.sh create` refuses a dirty kept worktree at `tools/loop/worktree.sh:58`, and `run.sh` reaches that
call only after `loop_state init`, the decision-cap preflight, `prescreen.sh` and `loop_state lock` — measured
on HEAD: exit 5, the pre-screen already spent, `items recorded: 0`, `the run printed a report: 0`
(`evidence/red-on-head.log`). The check moves to a pre-flight that runs before `prescreen.sh`, so the run
still refuses, but the refusal now names the item, records it, escalates it and prints the report.

| alternative | why the evidence eliminates it |
|---|---|
| leave the check where it is and make the caller record the refusal | impossible: the call fails *before* an item exists, and after the lock, so there is no item to attribute and no path back to the pre-screen's output — the measured run wrote nothing at all. |
| keep the check in `create` only, with the run tolerating the failure | impossible: the refusal is the correct rule (the previous run's work must not be committed onto this run's branch); tolerating it would carry the dirt into the run's own branch. |
| run the pre-flight without consulting the lock | eliminated by measurement: with the pre-flight before the lock, `state#secondRunRefusesWithExit5` went red — a *live* run's in-flight worktree (the blocked child had written its files, the run had not committed yet) was attributed as leftovers, escalated and recorded as blocked, while the first run was still working that item. A worktree a live run is working in is not leftovers, and the lock in the run's own state directory is the one cheap signal that tells the two apart. The pre-flight reads it and never takes it. |

## D2 — The item comes from the worktree's branch marker, made to name it (`worktree.sh`)

Attribution is what the defect is missing, so the pre-flight needs a source for the item. The issue names
`.pi/loop-branch`, and the measurement showed the marker held the *run's* branch (`loop-20261018-190238`,
`evidence/red-on-head.log`) because only `cmd_create` wrote it. `cmd_start_item` now writes the item's branch
there, one line, so the marker says what the tree is working on — which is also what `cmd_remove`'s message
always meant ("branch kept").

| alternative | why the evidence eliminates it |
|---|---|
| read the worktree's checked-out branch (`git rev-parse --abbrev-ref HEAD`) | rejected: it is a second source of truth for the same fact, and it is not the file the issue's measurement named; the marker is written at both points where the branch changes, so one read is enough. |
| infer the item from the branch names of the dirty files, or from the tracker | impossible: a file list names no item, and the tracker is exactly what cannot see this leftover (#175's root cause). |

## D3 — The pre-screen decides, and the run escalates (the writer split)

`prescreen.sh` writes the run's state (`state.sh queue`) and never the tracker; `run.sh` is the run's label
writer. So the pre-screen records the branch verdict in `excluded[]` — with the branch as its evidence, which
`state.sh cmd_queue` now keeps — and `run.sh` escalates every exclusion whose verdict is the decision value,
before the first item is picked, including when the run then finds nothing else to work.

| alternative | why the evidence eliminates it |
|---|---|
| the pre-screen writes the labels itself | impossible: the pre-screen runs during `--dry-run`, and `run#dryRunTouchesNothing` asserts a dry run writes no label. |
| leave the start's check as the only one | eliminated by #175's measurement: the item is queued, picked, labelled in progress and given a budget slot before `start-item` can refuse it — the pre-screen is the cheap half of the gate, and this is a rejection it could not make. |
| name the verdict anything other than the profile's decision value | rejected: the report's "decisions the owner owes" and the escalation label are one fact; naming the verdict the profile's `decisionAgent` value keeps them one query. The pre-screen reads it from the profile's `roles`, so the engine still names no vocabulary value (`tools/check-portability.sh`). |

## D4 — Never commit and never discard without a flag

`--reset-worktree` is the start-time repair: it prints the files it is about to discard (the same list the
refusal printed), then `git reset --hard` + `git clean -fd` in the kept worktree. The commits on the previous
branches are not touched — they are the record a person's decision points at — and the tracker is not touched
either. Auto-clean was eliminated by the rule the refusal states: the leftovers are the only record of the
previous attempt until a person decides, so nothing discards them implicitly.

## D5 — `--clean-worktree` holds wherever the run ends

The pre-screen's new verdict can end a run at the "nothing eligible" exit, which never reached the end of the
item loop where `--clean-worktree` was honoured — the existing case `worktree#keepsByDefaultAndRemovesOnRequest`
went red on exactly that (`the worktree survived --clean-worktree`). The flag is the run's, so the report and
the removal are one `end_run()` used by the item loop's end and by the two "nothing eligible" exits. The
refusal paths (identity, decision cap, the pre-flight, the lock, a stale base) still leave the tree alone: a
refusal is not an end of the work the flag was passed for, and the pre-flight's own message promises that
nothing is discarded without `--reset-worktree`.

## D6 — The authoritative check keeps its guard, and each new predicate has one mutation

`start-item`'s check stays the authoritative one (the pre-screen cannot see a branch that did not exist when
it ran), so `run#branchThatAppearsWhileTheRunIsOpenIsRecorded` makes the branch appear while the run is open —
through the first item's worker — and is green on HEAD: it guards the recording and the escalation path #170
added, and it exercises the "escalated once, not again when the pre-screen runs a second time" guard
(`two/issues.json` exists so the case can exhaust its queue). Eight mutations (`13.1`–`13.7` and the
re-pointed `12.7`) cover the new predicates: the branch query, the escalation of a decision verdict, the
pre-flight firing, the refusal's record, the discard, the lock signal, and the end-of-run form.

## Verification

`tools/loop-selftest.sh` (59 cases) and `tools/loop-mutations.sh` (26 mutations: red when mutated, green after
the restore) are the gate, with `tools/loop-mutations-selftest.sh` and `tools/check-portability.sh` beside
them. No compiled file is touched, so no Gradle task runs (`guidelines/Build.md` §2, the scope block).
Evidence: `evidence/red-on-head.log`, `evidence/loop-selftest-final.log`, `evidence/loop-mutations-final.log`.

## Not measured, and named

* **A stale lock.** A run killed with SIGKILL leaves the lock *and* the dirty tree; the pre-flight then stands
  aside and the run refuses at the lock, naming the owner, as it did before. Telling a stale lock from a live
  one needs a liveness test the loop has no cheap answer for.
* **A leftover branch whose commits are already in the run's base.** Both the pre-screen and the start answer
  "carries no work of its own", so the item is worked from the base — `worktree#reusesABranchThatCarriesNoWorkOfItsOwn`
  covers the start's half; the pre-screen's half is the same comparison, not separately measured.
* **Two runs racing between the pre-flight's lock read and the lock itself.** The pre-flight's read is
  advisory; the run still takes the lock further down, where the second run is refused with the lock's own
  message.
