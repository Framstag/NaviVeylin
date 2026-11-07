# fix-the-read-that-places-a-write-has-its-own-cadence

## Why

The case that pins a placed write is flaky. `run#aWriterTheRunCanPlaceBlocksTheItem`
(`tools/loop-cases.sh`, the case the requirement "A difference is attributed while the writer is alive" names)
fails in roughly one run of three with `a write the run can place in the child's own tree decides the item:
expected 'blocked', got 'done'` — the same sentence the loop's guideline ("The loop" in `guidelines/Backlog.md`) already quotes from 2026-10-23.
A flaky gate case is evidence of nothing: it reds a change it does not belong to and it hides the regression it
was written for.

**Root cause, one sentence:** the run's only in-time reading of the project's tree — the evidence that lets it
place a transient write — is taken on the *reporting* cadence (`LOOP_PROGRESS_SECONDS`, `tools/loop/run.sh`'s
heartbeat, the reading guarded by `[ "$elapsed" -ge "$next" ] || continue`), so its resolution is an interval a
person configured to read progress, and the case had to lower that interval to 1 s to be answered at all — which
left it one second of phase margin against a writer that stands for two.

**Measured on HEAD** (`evidence/phase-margin-probe.md`, a probe planted in the heartbeat for one measurement and
removed): with `LOOP_PROGRESS_SECONDS=1` the single reading that saw the difference landed 0.45 s, 0.66 s and
0.76 s after the write against a writer that exists for 2 s, and that write is taken back 3 s after it appears, so
the reading at the child's exit sees nothing. Any delay of about a second between two readings — a busy host, a
pooled suite, the `git status` of a landing — moves the first reading past the writer's life, nothing is placed,
and line 1168's exit-time comparison (`[ "$before" != "$after" ]`) finds nothing either because the file is gone:
the item is recorded `done`.

**Red test on HEAD, before the fix.** The case, repaired to hold its window open and no longer lowering the
reporting interval, run against the unchanged engine at `4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46`:

```
$ bash tools/loop-selftest.sh --case 'run#aWriterTheRunCanPlaceBlocksTheItem'
run#aWriterTheRunCanPlaceBlocksTheItem|   a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'
FAIL run#aWriterTheRunCanPlaceBlocksTheItem
loop-selftest: 1 of 1 cases failed
```

Three consecutive runs, red in all three (`evidence/red-on-head.log`, `evidence/red-on-head.xml`,
`tests="1" failures="1"`). The same command on the repaired tree: `PASS` three times
(`evidence/writer-case-fixed.log`, `evidence/writer-case-fixed.xml`, `tests="3" failures="0"`).

**Owning capability and guideline.** `openspec/specs/agent-issue-loop/spec.md` — the scenarios "A process that
writes outside its tree is caught" and "A difference is attributed while the writer is alive" (issue #286, #288),
which name this case. The guideline section is "The loop" in `guidelines/Backlog.md`, the paragraph that states
what placing a difference means; it carried the wrong cadence ("reads the project's tree once per
`LOOP_PROGRESS_SECONDS`").

**Surface, counted before the plan was fixed.** The rule has three places, and the count is the plan's basis:

```
surface: 0 cases added, 1 rewritten, 1 mutation record retired, 1 guideline paragraph, 1 spec requirement (ADDED)
         + the correction round's 1 narrowed shipped scenario, carried in the delta as a MODIFIED requirement
         — grep -rn "outside_write_seen" tools/ (3) and grep -rn "LOOP_PROGRESS_SECONDS" tools/ guidelines/ openspec/specs/ (14)
```

```
$ grep -rn "outside_write_seen" tools/   → 3 lines: tools/loop/run.sh:889 (the definition),
                                            tools/loop/run.sh:1051 (its one call),
                                            tools/loop-mutations.sh:119 (record 12.10, which quotes that call)
$ grep -rn "LOOP_PROGRESS_SECONDS" tools/ guidelines/ openspec/specs/   → 14 lines:
  tools/loop/run.sh:974 (the heartbeat's doc line), :995 (the interval), :1043 (the reading's comment)  ·
  tools/loop-cases.sh:3837, :3885, :3893 (the reporting cases), :4656 (this case's comment), :4709 (the peer
  case's interval)  ·  guidelines/Backlog.md:275 (the guideline paragraph), :395 (its second mention)  ·
  openspec/specs/agent-issue-loop/spec.md:772, :2253, :2264 (the reporting requirements), :2431 (this change's added
  requirement)
```

So: **1 case rewritten** (`run#aWriterTheRunCanPlaceBlocksTheItem`), **0 cases added**, **1 engine site** (the
heartbeat's reading), **1 mutation record retired** (`12.12`, see below), **1 guideline paragraph**, **1 spec
delta**. The peer case (`run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`) keeps its own 1 s interval on
purpose: it waits for the run's record too, and it is the *unattributable* half of the pair.

## What Changes

- `tools/loop/run.sh` — the in-time reading gets a cadence of its own: **one reading per whole second of the
  child's life**, advanced by `read_next=$(( SECONDS + 1 ))` beside the reporting clock, and the reporting branch
  (`[ "$elapsed" -ge "$next" ] || continue`) no longer gates it. `LOOP_PROGRESS_SECONDS` keeps its meaning — the
  interval at which a person is told — and no project key is added, because the cadence is an engine property and
  not a project fact.
- `tools/loop/run.sh` — the in-time line reads `the project tree changed while this process ran: <paths> — read
  while the child was alive at <ts>`, the same opening sentence the exit-time line carries, so the item's note
  says one thing whichever reading caught the difference. Only the clause order changed: the path list stays
  first, and the moment is still stated.
- `tools/loop-cases.sh` — `run#aWriterTheRunCanPlaceBlocksTheItem` no longer exports `LOOP_PROGRESS_SECONDS=1`
  (the reading does not need it any more, and its absence is what makes the case red on HEAD), and its writer
  stands for 10 s while the write lives 8 s, so the margin is ~7 s against a 1 s reading cadence instead of ~1 s.
- `tools/loop-mutations.sh` — record `12.12` ("the candidates read from the child tree instead of from the sweep
  that has moved on") is **retired**: the writer case is the only case that could notice it, it could only do so
  through timing (the sweep finds a writer that is still standing), and `bash tools/loop-mutations.sh --only 12.12`
  measured `GREEN 12.12 … the case did not notice the mutation` on the repaired tree
  (`evidence/mutation-12.12.log`).
  A mutation nobody notices is not evidence (the rule the change's own `13.x` records follow). Records `12.10` and `12.11` stay and were
  re-measured red (`RED 12.10`, `RED 12.11`, both restored green).
- `guidelines/Backlog.md`, "The loop" — the paragraph that stated the cadence now states the reading's own and
  the measurement that moved it.
- `openspec/specs/agent-issue-loop/spec.md` — one ADDED requirement, "The reading that places a write has its own
  cadence", with one scenario naming this case; and, in the correction round, one MODIFIED requirement — the
  scenario "A difference is attributed while the writer is alive" narrowed to the clause its case still holds, since
  the ordering that clause stated lost its only observer with record `12.12` (see `design.md`, correction round 1).

No compiled file is touched: no Kotlin, Java or C++ path is edited, so no Android build is owed (the gate owes the
shell suites below). Rollback is a revert of the three script edits, the guideline paragraph and the spec delta;
an older engine reads the new line and the removed `LOOP_PROGRESS_SECONDS` export as nothing at all.

## Impact

- One case rewritten, none added; one mutation record retired with the measurement that retired it
  (`evidence/mutation-12.12.log`: `GREEN 12.12 … the case did not notice the mutation`, the record restored verbatim
  for the one run); one shipped scenario narrowed to what its case holds.
- The retired record's consequence, stated: the ordering `run.sh` argues for (#288 — the child's own tree before
  the host-wide sweep, because the sweep walks every pid and a writer is gone when it ends) keeps its code and its
  paragraph in `guidelines/Backlog.md` ("The loop"), and loses the case that noticed it. Nothing else observes that ordering,
  because the two readings differ only in *when* they run.
- The cost of one reading per second per child: `git status --porcelain` in the project's own tree, measured on
  this host at 0.035 s per call (10 calls in 0.347 s wall, `time (for i in $(seq 10); do git status --porcelain
  >/dev/null; done)`), i.e. ~3.5 % of one core while a child runs — against a gate round of ≈695 s
  (`evidence/gate-round2.log`: `LOOP-GATE: tests=608 …`, its 20 step durations summing 695 s).
- A long gate child now costs ~1 reading per second instead of one per 30 s, and every difference it sees is
  recorded where it was recorded before, with the same note and the same decision rule.
