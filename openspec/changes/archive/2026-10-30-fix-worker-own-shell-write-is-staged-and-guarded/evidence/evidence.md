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

## Red on the base

`evidence/red-on-head.sh --base` installs the base commit's `tools/loop/fixtures/pi` (`git show
27cf61c:tools/loop/fixtures/pi`), runs the case the delta names, and restores the tree's own copy on exit — the
round-1 evidence took that restore by hand, and this script now performs it, so the red half is repeatable from the
archive alone. It printed, at 2026-10-30T09:04:21Z:

```
FAIL run#theWorkersOwnShellWriteIsRecordedAndNotPlaced — the difference is recorded, with the path that changed: 'demo-owned.txt' not found in:
tests=1 failures=1
```

- XML: `evidence/red-on-head-round2.xml` — `tests="1" failures="1" errors="0"`, timestamp `2026-10-30T09:04:21Z`
- log: `evidence/red-on-head-round2.log`
- round 1's pair, the same case before this round added its last assertion: `evidence/red-on-head.xml`
  (`tests="1" failures="1"`, `2026-10-30T08:51:41Z`) and `evidence/red-on-head.log`

The case is red for the reason the item names: `tools/loop/fixtures/pi` starts its action *after*
`cd "$LOOP_WORKTREE"`, and no hook forks a shell while the child still stands where `run_child` starts it
(`tools/loop/run.sh:1140`), so the shape cannot be staged and the difference it must record is never made.

## Green on the change

The same script with `--tree`, 2026-10-30T09:04:38Z: `tests=1 failures=0`, `PASS
run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` — `evidence/green-case-round2.xml`,
`evidence/green-case-round2.log` (round 1's `evidence/green-case.xml`, `2026-10-30T08:51:25Z`, is the same run
before this round's assertion).

The case asserts five things: the difference is recorded with its path (`demo-owned.txt`), the item is `done`, it
is `closed`, the reading names the shell that wrote among the processes standing in the project, and the reading's
`attributed` field is `none`. The fourth is the one round 1 found missing: `attributed=none` is printed identically
by a run whose walk of the child's tree returned nothing, so it cannot by itself separate "found and placed by
nothing" from "never found at all". The staged shell therefore records its own pid
(`${0%/*}/project-shell.pid`) and the case reads the `LOOP-OUTSIDE-WRITE-ALIVE:` field — the processes standing in
the project when the difference was read — for that pid, and only then reads `attributed=none` (issue #704,
review round 1, BLOCKING).

## The cases the delta touches, run focused

Selection `--case run#*rite*`, the group round 1 ran, after this round's assertion was added —
`evidence/green-cases-round2.log`:

```
PASS run#aFailedEscalationWriteIsNamed
PASS run#reviewerWriteInvalidatesTheRound
PASS run#strictOutsideWriteBlocksTheItem
PASS run#strictOutsideWriteDiscardsTheReviewRound
PASS run#childWriteOutsideTheWorktreeIsNoted
PASS run#aWriterTheRunCanPlaceBlocksTheItem
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced
PASS run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem
loop-selftest: all 8 cases passed (JDK 27, offline, no token, no sandbox) — selection: --case run#*rite*
```

Round 1 ran the same group and passed the same 8 cases before the case gained its fourth assertion; this round's
run is the one archived, because it is the one taken against the case as it now stands.

## The gate, once

`./tools/loop/gate.sh` from the change root, 13m20s wall (`1327,10s user 1048,39s system 296% cpu 13:20,06 total`,
the gate measured 798 s of suite time over 20 steps):

```
LOOP-GATE: tests=725 failures=0 errors=0 (tools/loop-selftest.sh --from-git 2f954a04b3f75e138c529fffe7985ca011375866 403s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 283s, …)
```

- `evidence/gate.log` — the gate's whole output
- `evidence/gate-line.txt` — the `LOOP-GATE:` line, quoted above in full
- `evidence/gate-tallies/` — the 20 tally files the gate wrote, as produced

**The gate is dated by its own content**, which is what round 1's review found missing: `gate.log`'s per-case and
per-suite stamps run `2026-10-30T08:37:09Z` to `2026-10-30T08:50:15Z`, and its last suite line records
`HEAD 27cf61cb`, the run's base. The measure block reads the same records (`measure-round2.log`):

```
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-30-fix-worker-own-shell-write-is-staged-and-guarded/evidence/gate-summary.xml tests=725 failures=0
LOOP-MEASURE-TALLY …/tools_loop-selftest.sh_--from-git_2f954a04b3f75e138c529fffe7985ca011375866.xml tests=288 failures=0 time=403s
```

The gate's own directory is empty because the run's `clean` emptied it; the tallies are the archived copies.

**One edit after the gate, and why it is safe.** The gate ran with the spec delta as it then stood; afterwards the
change's own scenario count reads `scenarios=13 cases=13 unnamed=0` instead of `cases=9 unnamed=4`, because the
delta's four restated scenarios gained their `Case` lines. No behaviour, no case and no fixture changed with it,
and the checks that read `openspec/**` were re-run directly rather than through the gate:

```
$ bash tools/gen-feature-list.sh --check-classification      → exit 0
$ bash tools/check-openspec-hygiene.sh                      → exit 0
$ openspec validate fix-worker-own-shell-write-is-staged-and-guarded --strict → Change … is valid
```

**The correction round ran no gate, and the scoping says why.** This round edits one test source
(`tools/loop-cases.sh`, one assertion and the pid line the case's own shell writes), three documents of the change,
and the spec's scenario wording — no engine script, no fixture, no compiled file. A change scoped to test sources
owes its red case and the affected cases, which are the two runs above; re-running the gate here would re-take
evidence for code this round did not touch.

## What the fixture change cannot touch

`tools/loop/fixtures/pi` gained one block guarded by `PI_STUB_PROJECT_SHELL`, run before the `cd` into the
worktree. No case sets the variable except the one this change adds, and the portability suite copies the fixture
with the block inert; `bash -n tools/loop/fixtures/pi` parses it. Nothing in `tools/loop/run.sh` changed, so the
mutation record 12.11 (`pid_left_the_worktree … || continue`) still has its case, and the whole suite ran against
the unchanged engine.

## Correction round 1 (the review of round 1 refused the change)

One BLOCKING finding, on the guard this change exists for. The scenario's THEN said the reading "names the shape as
one nothing could place rather than one it never found", and the case asserted only `attributed=none` — a string
`tools/loop/run.sh:996` prints identically when the walk of the child's tree yields no candidate at all (`:987`–`:992`),
so the found half was pinned by a different claim than the one the THEN states; the only field that carries it is
the unchecked `alive` half of the same line.

The case now stages the pid: the shell the hook forks writes its own pid (`${0%/*}/project-shell.pid`) before it
writes the project's tree (`tools/loop-cases.sh:5332`), and the case reads the `LOOP-OUTSIDE-WRITE-ALIVE:` field out
of the transcript and asserts it names that pid, then reads `attributed=none` (`tools/loop-cases.sh:5347`–`:5352`).
The pid is also asserted non-empty, so a missing marker cannot make the pattern vacuous (`:5349`).
The scenario's THEN now states exactly those two halves — "names that shell among the processes standing in the
project and places no process of that tree" — in the delta and in the shipped spec
(`openspec/specs/agent-issue-loop/spec.md:460`–`:461`).

The round's five notes were corrected with it:

- `proposal.md` quoted a red timestamp no command produced (`2026-10-30T08:34:26Z`); it now quotes the archived
  XML's own stamp.
- `evidence/red-on-head.sh` restores the base fixture itself (`--base`, the default; `--tree` is the green half of
  the same case), so the red half is repeatable from the archive without the hand restore round 1 recorded.
- The gate record is dated by its own content (`gate.log`'s stamps, `HEAD 27cf61cb`), above.
- `proposal.md`'s What Changes named the scenario "recorded and not placed" while the spec says "found and not
  placed"; the proposal matches the spec now, and it lists the four `Case` lines the restatements added — including
  the `Case: none` one, which is implementation rationale in the contract but truthful and required by
  `openspec/config.yaml`.
- `tasks.md` 1.1's verify count and 2.2's "verbatim" claim are corrected in the section this round adds.

| step | result | artifact |
|---|---|---|
| the corrected case on the base fixture, installed and restored by the script itself | `tests=1 failures=1`, `FAIL … 'demo-owned.txt' not found in:`, exit 1, 2026-10-30T09:04:21Z | `red-on-head-round2.xml`, `red-on-head-round2.log` |
| the corrected case on the tree | `tests=1 failures=0`, `PASS`, exit 0, 2026-10-30T09:04:38Z | `green-case-round2.xml`, `green-case-round2.log` |
| the affected group (`--case run#*rite*`, the 8 cases round 1 selected) | 8 × `PASS` | `green-cases-round2.log` |
| the corrected case repeated, for the reading's one-second window against the shell's 8 s life | 5 × `PASS` | `repeat-round2.log` |
| `tools/loop/evidence-check.sh` on the archived change | `evidence-check: PASS` (C1–C5, 540 `file:line` resolved) | `evidence-check-round2.log` |
| structural checks this round owes (it touches spec text and one test source) | `openspec validate --archived` → `✓ change/2026-10-30-fix-worker-own-shell-write-is-staged-and-guarded`; `check-openspec-hygiene.sh` exit 0; `check-doc-routes.sh` exit 0 (169 section references resolve) | quoted here; `measure-round2.log` carries the measure block |
| the change's own scenario count | `scenarios=13 cases=13 unnamed=0` | `measure-round2.log` (`LOOP-MEASURE-CHANGE`) |

## Landed by hand, because the round-2 reviewer was cut

The run announced review round 2 and ended its reviewer at the item's turn bound, so no reviewer graded this change
and there is no `review-round-2-passed.md` — only the announcement, on purpose. The change was reviewed and merged by
hand, which is the path `tools/loop/worktree.sh:444` names for an unanswered announcement, and
`evidence/review-round-2-verified-by-operator.md` is that pass: round 1's findings read against this tree, the red
and green halves re-run, the affected group and the whole suite re-run on the tree with `main` merged in (289 of
289), the structural checks, and the one conflict the merge carried.
