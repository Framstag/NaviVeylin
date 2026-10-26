# Design — the reading that places a write has its own cadence

## Root cause

`run_child`'s heartbeat is one loop with two jobs, and until this change it ran the second inside the first's
clock: the report gate

```
      elapsed=$(( SECONDS - hb_base ))
      [ "$elapsed" -ge "$next" ] || continue
      next=$(( elapsed + interval ))
```

`continue`s on every look that is not a report, so the reading of the project's own tree — the only one taken
while the child runs, and therefore the only evidence that can place a transient write — happened once per
`LOOP_PROGRESS_SECONDS` (default 30 s). The case that pins the placement lowered that interval to 1 s to be
answered, which put the reading on a clock comparable to the writer's own life: measured at HEAD
(`evidence/phase-margin-probe.md`), the reading landed 0.45–0.76 s after the write against a writer that stood for
2 s, and a reading delayed past that life records `done` because the write is gone before the exit-time compare.

## The single fix

Two halves of one mechanism, not two options:

1. **The reading gets a cadence of its own**, in whole seconds of the heartbeat's clock,
   `if [ ! -e "$2.outside-write" ] && [ "$SECONDS" -ge "$read_next" ]; then read_next=$(( SECONDS + 1 ))`, placed
   *before* the report gate so a look that will not report can still read. `LOOP_PROGRESS_SECONDS` keeps its
   meaning; no new project key exists, because "how often evidence is read" is an engine property.
2. **The case stops racing the reading.** It no longer lowers the reporting interval (that absence is what makes
   it red on HEAD), and its writer stands 10 s beside a write that lives 8 s: the margin is ~7 s against a 1 s
   cadence, where it was ~1.3 s.

The one wording change (`outside_write_seen`'s first line) exists so both readings say one sentence: the
in-time line used to open "the project tree changed at <ts> — …", which is *not* the sentence the item's note
carries when the difference is only seen at the child's exit, and `run#childWriteOutsideTheWorktreeIsNoted`
asserts that sentence. With one reading per second the in-time path is the common one, so the two now agree.

## Why these alternatives were eliminated

- **Widen the case's window only.** It removes the flake (a wide window cannot be starved away) but leaves the
  engine reading at the reporting cadence, and then no case can be red on HEAD deterministically: the case would
  have to lower the interval again, which is the race. Measured: with the window widened and
  `LOOP_PROGRESS_SECONDS=1` still exported, HEAD passes — no red evidence, and the item's own claim ("1 run in
  3 records the item `done`") stays unfixed for the next writer case.
- **Keep one reading per child, but take it twice (first difference and again before exit).** The writer is gone
  before the exit reading by construction — that is the case's whole shape — so the second reading places
  nothing.
- **Read on every look (20 per second).** One `git status` in the project's tree is 0.035 s here, so 20 per second
  is 70 % of a core for the whole life of every child, paid by a gate of ~970 s.
- **Add a project key for the cadence.** It is not a project fact: nothing about this project decides how often a
  tree has to be read to be evidence. `loop.conf` records what a project *chooses*; adding a key here would let a
  project configure its way back into the flake.
- **Retire `run#aWriterTheRunCanPlaceBlocksTheItem` and let `run#strictOutsideWriteBlocksTheItem` cover the
  placement.** The strict case decides on the mode, not on the placement (`outside_write_decides` is true there
  with no attribution at all), so it pins nothing about the reading. Kept the case and repaired it.

## Blast radius

- `tools/loop/run.sh` — the heartbeat's reading block and one `printf` in `outside_write_seen`. No other caller of
  either reads the changed format: `outside_write_note` is derived with a `sed` on the line's prefix, and the
  transcript's consumers (`report.sh`, the reviewer's prompt) take the whole transcript.
- `tools/loop-cases.sh` — one case. Its `# files:` declaration is unchanged and still names `tools/loop/run.sh`,
  the fixture scenarios, `tools/pi-child-events.sh` and `tools/tooling.json`.
- `tools/loop-mutations.sh` — record `12.12` removed, `12.10` and `12.11` re-measured red. `--check` passes over
  all 178 remaining records.
- The cases whose notes or verdicts come from a difference: `run#childWriteOutsideTheWorktreeIsNoted`,
  `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`,
  `run#strictOutsideWriteBlocksTheItem`, `run#strictOutsideWriteDiscardsTheReviewRound`,
  `run#reviewerWriteInvalidatesTheRound` — all ten ran green by hand (`evidence/affected-cases.log`), and the gate
  runs the whole suite because `tools/loop-cases.sh` is a core file of the selection.

## Rollback

Revert the three script edits, the guideline paragraph and the spec delta. The new line format is inert text for
an older engine (nothing parses it back), the mutation set stays valid without `12.12`, and the case's older
shape still passes on an engine that reads on the reporting cadence when the case exports `LOOP_PROGRESS_SECONDS=1`
— which is exactly what this change deletes.
