# Evidence — fix-worker-own-shell-write-is-staged-and-guarded

## The measurement the change was planned on

From the change root, before planning, and again once the change existed (`./tools/loop/measure.sh`):

```
LOOP-MEASURE-SUITE cases=288 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-TALLY …/tools_loop-selftest.sh_--from-git_2f954a04b3f75e138c529fffe7985ca011375866.xml tests=288 failures=0 time=403s
LOOP-MEASURE-GATE seconds=798 suites=20
LOOP-MEASURE-CHANGE fix-worker-own-shell-write-is-staged-and-guarded scenarios=13 cases=13 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added (`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`), 1 fixture hook added
```

The suite's declared count went from 287 to 288 with the case this change adds, which is why the tally and the
`--list` count agree.

## Red on HEAD

`evidence/red-on-head.sh` runs the case the delta names; on HEAD's content of `tools/loop/fixtures/pi` — restored
for the run and put back afterwards — it printed, at 2026-10-30T08:51:41Z:

```
FAIL run#theWorkersOwnShellWriteIsRecordedAndNotPlaced — the difference is recorded, with the path that changed: 'demo-owned.txt' not found in:
tests=1 failures=1
```

- XML: `evidence/red-on-head.xml` — `tests="1" failures="1" errors="0"`, timestamp `2026-10-30T08:51:41Z`
- log: `evidence/red-on-head.log`

The case is red for the reason the item names: `tools/loop/fixtures/pi` starts its action *after*
`cd "$LOOP_WORKTREE"`, and no hook forks a shell while the child still stands where `run_child` starts it
(`tools/loop/run.sh:1140`), so the shape cannot be staged and the difference it must record is never made.

## Green on the change

The same script on the change's own tree, 2026-10-30T08:51:25Z: `tests=1 failures=0`, `PASS
run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` — `evidence/green-case.xml`, `evidence/green-case.log`.

The case asserts four things, and the last is the one that separates this shape from a difference the run never
saw: the difference is recorded with its path (`demo-owned.txt`), the item is `done`, it is `closed`, and the
transcript's own reading carries `attributed=none`.

## The cases the delta touches, run focused

```
PASS run#aWriterTheRunCanPlaceBlocksTheItem       (a placed writer still blocks — the verdict this change must not move)
PASS run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem
PASS run#childWriteOutsideTheWorktreeIsNoted
PASS run#strictOutsideWriteBlocksTheItem
PASS run#strictOutsideWriteDiscardsTheReviewRound
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced
PASS run#aFailedEscalationWriteIsNamed
PASS run#reviewerWriteInvalidatesTheRound
loop-selftest: all 8 cases passed — selection: --case run#*rite*
PASS run#dirtyBaseRefusesTheMergeAndNamesThePath
```

## The gate, once

`./tools/loop/gate.sh` from the change root, 13m20s wall (the gate measured 798 s of suite time over 20 steps):

```
LOOP-GATE: tests=725 failures=0 errors=0 (tools/loop-selftest.sh --from-git 2f954a04b3f75e138c529fffe7985ca011375866 403s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 283s, …)
```

- `evidence/gate.log` — the gate's whole output
- `evidence/gate-line.txt` — the `LOOP-GATE:` line, quoted above in full
- `evidence/gate-tallies/` — the 20 tally files the gate wrote, as produced

**One edit after the gate, and why it is safe.** The gate ran with the spec delta as it then stood; afterwards the
delta gained the `Case` lines of four scenarios it restates verbatim from the shipped requirement, so the change's
own scenario count reads `scenarios=13 cases=13 unnamed=0` instead of `cases=9 unnamed=4`. No behaviour, no case
and no fixture changed with it, and the three checks that read `openspec/**` were re-run directly rather than
through the gate:

```
$ bash tools/gen-feature-list.sh --check-classification      → exit 0
$ bash tools/check-openspec-hygiene.sh                      → exit 0
$ openspec validate fix-worker-own-shell-write-is-staged-and-guarded --strict → Change … is valid
```

## What the fixture change cannot touch

`tools/loop/fixtures/pi` gained one block guarded by `PI_STUB_PROJECT_SHELL`, run before the `cd` into the
worktree. No case sets the variable except the one this change adds, and the portability suite copies the fixture
with the block inert; `bash -n tools/loop/fixtures/pi` parses it. Nothing in `tools/loop/run.sh` changed, so the
mutation record 12.11 (`pid_left_the_worktree … || continue`) still has its case, and the whole suite ran against
the unchanged engine.
