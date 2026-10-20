# Proposal — fix-loop-phase-lines

## Why

A run is silent between its header and an item's result line, and the silence is the run's own doing rather
than the child's: measured 2026-10-20 on the real backlog, the pre-screen is a **53 s** call
(`tools/loop/prescreen.sh --dry-run --json`, 84 records over 188 tracker items) whose whole output was taken by
a command substitution and printed only after it returned — `prescreen_out=$(bash "$LOOP_DIR/prescreen.sh" …)`
followed by `printf '%s\n' "$prescreen_out"` (`tools/loop/run.sh`, pre-fix lines 332–334). Nothing of it reached
the terminal while it ran. No line marked any other phase boundary either: the run's last words before an item's
result were the `worktree:` line, so one item's whole life — 40–65 min for a change, 10–15 min for a skip
(`.pi/skills/fix-loop/REFERENCE.md`, "Measured costs") — looked the same as a hung run. On 2026-10-19 and 2026-10-20
two runs were interrupted by hand inside that pre-screen window (the issue's operator account, not a transcript
this change holds), and a third invocation that hung on a missing
flag value (#190) was indistinguishable from both.

What the header named was its arguments and nothing else, so nothing on the console said that the pre-screen
came next, that it is not instant, where the state lives, or that `tools/loop/state.sh show` exists as a live
view at all.

Root cause in one sentence: the run announces nothing at its own phase boundaries — its longest single call is
captured whole and replayed after it returns, and the boundaries it crosses in between are not printed — so the
terminal carries no evidence that a working run and a stopped one differ.

Failing cases and their red run on the unpatched scripts (evidence, not assertion), base `3211d4d8b395`,
`tools/loop-selftest.sh --case <case>`, recorded in `evidence/red-phase-lines.log`:

- `run#headerNamesTheLiveView` — `the header names the command that shows the run's progress: 'state.sh show'
  not found in: agent-issue-loop: items=1 …` (exit 1).
- `run#prescreenAnnouncesItselfBeforeItReturns` — the case holds the pre-screen inside a `list.sh` that sleeps
  3 s and reads the console 1.5 s in: `the phase is announced on the console: 'loop: pre-screen' not found in:
  agent-issue-loop: items=1 …`, i.e. at 1.5 s the console carried the header line and nothing else (exit 1).
- `run#everyPhaseBoundaryIsPrinted` — `the pre-screen's boundary: 'loop: pre-screen · ' not found in:
  agent-issue-loop: items=1 …` (exit 1).

What the tree already has, and what this change therefore does not repeat: the wait around a **child** process
already reports itself (`run_child`: `started`, a `running` line every `LOOP_PROGRESS_SECONDS`, `finished` with
the exit status), added by the archived change `2026-10-20-fix-loop-progress-reporting` under the capability's
requirement "A run reports the wait it cannot get from the child". The silence this change closes is the one
*outside* a child, and the console's account of what the run is about to do.

Owning capability and requirement: `agent-issue-loop` — this change ADDs the requirement "The run names its
phases and the live view into it". No existing requirement's text moves; the child-reporting requirement above
is the neighbour, not the subject.

Owning guideline section: `guidelines/Backlog.md` §1 "Status, and what this document owns" names the loop and
its entry `tools/loop/run.sh`; the loop's operating document, `.pi/skills/fix-loop/SKILL.md` section "What a run
prints while it works", is where the three kinds of line are described, and `guidelines/Build.md` §2 (the
iteration protocol, its scope block) is the rule that a delta touching no compiled file is verified by its own
structural checks instead of a build. No rendering, UI, logging or regulatory section applies: nothing here is
an app line, and no progress line carries a coordinate (there is no coordinate in the loop's output at all).

## What Changes

- `tools/loop/lib.sh` gains `progress <phase> <what>`, one format — `loop: <phase> · <elapsed> · <what>` — with
  the elapsed time counted from the run's own start, and `fmt_elapsed` moves there from `run.sh` so a phase
  line and a child's line agree on what an elapsed time looks like.
- `tools/loop/run.sh` prints a line wherever it crosses a boundary it can observe itself: the pre-screen's
  start and end, the worktree coming ready, the item being picked (naming the report file to watch), the gate's
  evidence being read, and `end-item` having returned with the tree's new HEAD.
- The pre-screen **streams** through `tee` into `<state dir>/prescreen.log` instead of being captured and
  replayed: the text reaches the terminal while the call runs, the same bytes stay kept for a path that reads
  them afterwards, and `pipefail` inside the subshell keeps the pipeline's status the pre-screen's own, so the
  run's decision reads exactly what it read before.
- The header is followed by one line naming the next step (the pre-screen, and that it is not instant), the
  state directory and `bash tools/loop/state.sh show`.
- `.pi/skills/fix-loop/SKILL.md`, section "What a run prints while it works", names the phase lines and the
  header hint beside the three child lines.

No decision, state field, exit status, verdict or report cell is touched. A progress line is terminal output
only and is never parsed back: the state file stays the run's record and the report stays derived from it, which
is what makes a run that printed them and a run that did not the same run.

Additive: no flag, path, exit status, state field or verdict changes, and no phase's input changes. Rollback:
revert `tools/loop/run.sh`, `tools/loop/lib.sh`, `tools/loop-cases.sh` and `.pi/skills/fix-loop/SKILL.md`; a run
then prints what it printed before, and no consumer reads the new lines.

## Capabilities

### New Capabilities

None.

### Added Requirements

- `agent-issue-loop`: one new requirement, "The run names its phases and the live view into it", with three
  scenarios — the header names the next step and the live view, the pre-screen is announced and reported before
  it returns, and every boundary outside a child prints one line in one format. Each scenario is exercised by
  exactly one case: `run#headerNamesTheLiveView`, `run#prescreenAnnouncesItselfBeforeItReturns` and
  `run#everyPhaseBoundaryIsPrinted`.

### Modified Capabilities

None.

## Impact

- `tools/loop/lib.sh` — `progress` and `fmt_elapsed` (moved in from `run.sh`), plus the run's start time.
- `tools/loop/run.sh` — the header hint, the streamed pre-screen, and five `progress` call sites; `fmt_elapsed`
  leaves this file for `lib.sh`; `stage_dir` is read once, before the path that needs it.
- `tools/loop-cases.sh` — three new shell cases, registered in `LOOP_SHELL_CASES` and in the dispatcher of
  `loop_run_shell_case`, each asserting its scenario's clauses: the pre-screen case holds a stand-in pre-screen
  (one line, a 3 s hold, a second line) and reads the console 1.5 s in, so the text is proved streamed rather
  than replayed, and reads the kept `<state dir>/prescreen.log` afterwards; the boundary case asserts the
  elapsed format and one line per crossing and that no phase line reaches the state file or the report.
- `tools/loop-mutations.sh` — mutations 33.1–33.4, one per property the phase lines own (streamed, kept,
  elapsed field, never read back), so the falsification of each is a versioned record rather than a one-off.
- `.pi/skills/fix-loop/SKILL.md` — the section "What a run prints while it works" gains the phase lines and the
  header hint.
- `openspec/specs/agent-issue-loop/spec.md` — gains the ADDED requirement at archive time; this change's delta
  is the only place it is written now.
- No app module, manifest, resource, stylesheet or native file is touched, and no phone/car variant is
  affected, so `guidelines/UI.md` §1's parity requirement does not apply.
- Guidelines affected: none beyond the ones that route this change. `guidelines/Logging.md` §1–§3 is about the
  app's own lines and its coordinate rule; the loop prints paths, counts and elapsed times, so the rule is
  satisfied by construction and nothing in the guideline changes.

## Scope

Tooling only — the backlog loop that closes defect-shaped items. It is not a feature of the app, of the Android
Auto integration or of the phone UI.
