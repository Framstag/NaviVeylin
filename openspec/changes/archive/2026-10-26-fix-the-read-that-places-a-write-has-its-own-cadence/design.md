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
  is 70 % of a core for the whole life of every child, paid by a gate round of ≈695 s (`evidence/gate-round2.log`:
  its 20 step durations sum 695 s).
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

## Correction round 1 (the review's refusal)

Three findings, and what each correction is.

**1. The case's rationale claimed a synchronisation the stub does not have.** The paragraph in the writer case said
that the write waits for the process standing beside it and that both wait for the run's recorded reading
(`worker-report.md.outside-write`). The stub waits for neither: it appends the line, forks
`( cd "$LOOP_CHILD_ROOT" && exec sleep 10 ) &`, and then `sleep 8; rm -f …` — a fixed window, ended by the child
itself. The paragraph is deleted. In its place the rationale states the mechanism the case is red on HEAD for (the
engine before this change read the project's tree only on the reporting cadence, which the case does not lower) and
the fixed window that is real (a 10 s standing process beside an 8 s write). No assertion changed, and the case is
unchanged as a case.

**2. The GREEN run that retired `12.12` had no captured output.** It does now, `evidence/mutation-12.12.log` — the
record restored verbatim from the text the base carried, for the one run, and removed again
(`bash tools/loop-mutations.sh --only 12.12`):

```
GREEN  12.12  run#aWriterTheRunCanPlaceBlocksTheItem   <-- the case did not notice the mutation
red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0
```

That output is also the argument for retiring the record rather than keeping it: the sweep the mutation substitutes
is a `readlink` for every process on the host (~1 s, #288's own measurement of it), and the case's writer stands
10 s beside an 8 s write, so the sweep finds the same writer the child's own tree does. Keeping `12.12` — the
review's option (b) — would leave a record that is green *by measurement*, and a case that noticed it again would
need a short-lived writer, which is the phase dependence this change exists to remove; that is the alternative the
measurement eliminated. Abandoning the change (option (c)) leaves the 1-in-3 flake and the wrong cadence in the
guideline.

**3. A shipped scenario stated a clause no case pins.** With `12.12` retired, `run#aWriterTheRunCanPlaceBlocksTheItem`
pins the placement and not the ordering (`outside_write_seen` reads the child's own tree before the host-wide
sweep). The shipped scenario `A difference is attributed while the writer is alive` therefore no longer states the
ordering: its THEN carries the marks the case holds, the ordering keeps its code and the comment beside it
(`tools/loop/run.sh`), and its reason stays in `guidelines/Backlog.md` §7 — a guideline explains why code is written
that way, a scenario asserts what a case holds. Because that scenario lives in a requirement the change did not
otherwise touch, the delta carries the requirement as a `MODIFIED` block with every other scenario repeated
verbatim: a scenario edited in the shipped spec after archive is a spec change, and the change's own record has to
say which one.

**Notes the review recorded, and what became of them.**

- *The two summary XMLs stated a `time` no run took.* `evidence/red-on-head.xml` and
  `evidence/writer-case-fixed.xml` claimed 17 s and 51 s for three runs that each stand 8 s beside the write. Both
  state no `time` now and say in the file that the tally is the paired `.log`'s three lines, which is what the
  reviewer verified against.
- *The surface count contradicted itself.* `proposal.md` printed a grep's count and then enumerated twelve pointers
  beside it. The paragraph now prints both greps as they return today — 3 lines for `outside_write_seen`, 14 for
  `LOOP_PROGRESS_SECONDS` — and names each.
- *`proposal.md` cited "line 1159's exit-time comparison".* The comparison is `tools/loop/run.sh:1168`; the pointer
  says so now.
- *`run.sh`'s comment said the first reading is taken at the next whole second.* It is taken on the first look
  (`read_next=$SECONDS`), and the comment says that now.
- *The guideline attributed the 1-in-3 rate to a late reading.* `guidelines/Backlog.md` states the mechanism (a
  reading delayed past the writer's 2 s life records `done`) and attributes the rate to the issue's own report,
  which is where it comes from.
- *No standing record pins the reading's cadence half* (`12.10` deletes the call, which would be red even if the
  call sat under the report gate). Left as the review recorded it: a record for that half is new work on the
  mutation set, not part of this correction, and the one-shot red-on-HEAD run stays the falsification of it.

## Correction round 2 (the review's refusal, corrections only)

The round-2 review found the change green and refused it on one blocking finding plus three pointer notes. All four
corrections subtract; no case, assertion, mutation record or engine line is touched.

**1. The ADDED requirement stated a cadence the engine does not owe (blocking).** The reading is guarded by
`if [ ! -e "$2.outside-write" ] && [ "$SECONDS" -ge "$read_next" ]` (`tools/loop/run.sh:1047`), so it stops for the
rest of the child's life once a difference has been recorded — which is what the code's own comment says ("One
reading per child once a difference is found") and what `guidelines/Backlog.md` said. The requirement said "at least
once per whole second of that child's life" flatly, so it was false of the diff after the first difference. Both
now read "… of that child's life **until the first difference is recorded**", in the delta, in the shipped spec and
in the guideline paragraph; the requirement body is 477 characters, still under the validator's 500. The scenario
named by the requirement is unchanged: its case reads the tree inside the writer's life, which is before any
difference is recorded.

**2. Two pointers, and the gate figure.** `proposal.md`'s `12.12` bullet cited `--only 12.10 12.11` beside
`evidence/mutation-attribution.log`; the captured run is `bash tools/loop-mutations.sh --only 12.12`, and its
output is `evidence/mutation-12.12.log`. The gate figure the two documents carried — the older estimate — is
corrected to ≈695 s, which is round 2's gate measured rather than recalled: the 20 step durations on its own
`LOOP-GATE:` line (`evidence/gate-round2.log:1186`) sum to 695 s:

```
sed -n '1186p' gate-round2.log | grep -oE '[0-9]+s(,|\))' | tr -d 's,)' | awk '{s+=$1} END {print "steps="NR, "seconds="s}'
steps=20 seconds=695
```

Nothing in the argument depended on the old figure: the cost of one reading per second stands on the 0.035 s
`git status` measurement and 1 Hz alone.

**3. Two task labels.** `tasks.md` 4 now names the captured `--only 12.12` run; `tasks.md` 7 records that the
`--strict` output is absent from the record and states what stands instead (the hygiene check green in the gate's
record, and the `MODIFIED` block diffed byte-identical against the shipped requirement).
