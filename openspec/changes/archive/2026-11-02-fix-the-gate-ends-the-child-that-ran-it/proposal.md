# The gate ends the child that ran it, and a fresh child writes the archive

## Why

Issue **#744**. `python3 tools/loop-spend.py`, 2026-11-02, over the archive (213 items / 524 sessions / 30 795 turns):

```
LOOP-SPEND-TOKENS input=64489099 cacheRead=2307607476 output=18922001 total=2391018576
LOOP-SPEND-GATE after=1426000635
LOOP-SPEND-BASELINE tokens=17056 charged=449721740
LOOP-SPEND-TURNS n=30795
```

Of the 274 sessions that ran a gate, **1.43 B of their 1.83 B tokens were charged after the first `LOOP-GATE` call** —
13 700 turns at 104 k of context per turn, against 7 868 turns at 52 k before it. Mean tokens per item by the run
records' own `metrics.reviewRounds`: 0 rounds = 2.88 Mtok (n=83), 1 = 11.81 (n=56), 2 = 19.16 (n=57), 3 = 20.90
(n=12), and the 40 `blocked` items average 16.45 Mtok — 658 M, 27 % of the archive — and merged nothing.

**Root cause: the worker phase is one child, and the gate sits in the middle of its session.**
`tools/loop/run.sh:1610` (base `b5d37f91`) opens that child with `run_child "$item_dir/worker-prompt.md"
"$item_dir/worker-report.md"`, and `tools/loop/prompts/worker.md`'s Phase D runs the gate and then Phase F (the
evidence copies, the pointer check, the archive, the report) in the same session. Everything the derivation put into
that context — Phase A's reads, the plan, the focused rounds, every tool result they injected — is re-read on each
turn of a tail that decides none of it. The exemplar #674: 480 turns, 54.4 Mtok, two workers of 22.4 M and 23.6 M
against reviewers of 3.8 M and 4.6 M, and the repair child re-read `tools/loop/run.sh` 15 times and
`tools/loop-cases.sh` 13 times on a change it was only patching.

**The requirement the defect contradicts** is the loop's own process contract,
`openspec/specs/agent-issue-loop/spec.md`, *"Each item is worked by a fresh process, reviewed by a different one"*:
it states that an item is worked by a process that **starts fresh**, and the tail is a phase with no fresh start —
it inherits one.

**The red case** is `run#childUsageIsRecordedPerItem` (`tools/loop-cases.sh`), extended: its fixture gives the gate
child's stream and the tail child's stream separately, and the case asserts that the item's spend is the sum of the
two children, that the tail's first turn is charged the baseline (17 056 input tokens), and that the largest context
recorded is still the gate child's compaction (150 000) rather than the tail inheriting it. On the base it fails at
*the worker phase is two children, not one: expected '2', got '1'* (`evidence/red-on-HEAD.log`), because the base
starts one child and its own stream is the whole item.

## What changes

**One fix: the item's first child ends at the gate, and a second, fresh child carries the tail.**

1. **`tools/loop/run.sh`** — `tail_after_gate` starts the tail child when the first child's report carries a
   `LOOP-GATE` line, handing it the gate line, the change name, the change's `tasks.md`, the root cause, the red test
   and a bounded extract of the first child's own output (the last 200 lines, `report_field`'s and one `tail`'s two
   forks instead of a child re-reading a transcript on every turn). The item's report fields are read from the tail's
   report when one ran, the commit and the "exited N" path follow the **last** child, and the gate child's own
   `outside-write` reading is snapshotted before the tail can overwrite it (recorded as `outside-write`, the tail's as
   `outside-write-tail`). Three traps the split set and the change clears:
   - `run_child`'s `worker*` glob would have run `clear_stale_test_results` for the tail — after the gate wrote the
     results the tail exists to read — so the arm now names the tail first;
   - the rework worker of a refusing round is split the same way (`worker-tail-round2-prompt.md`), because a round
     that paid the tail in-session costs what the item does;
   - a gate child whose own write decides the item (strict outside-write) starts no tail.
2. **`tools/loop/prompts/worker.md`** — Phase D ends at the gate: the child prints the gate block and stops, Phase F
   is removed, and the report contract says a verdict is the worker's only when no gate was run.
   **`tools/loop/prompts/worker-tail.md`** (new) states Phase F alone: read the gate result handed to it, never
   re-run the gate, archive nothing on a red gate, copy and check the evidence, archive, and print the final block
   carrying the four lines it was handed verbatim.
3. **`tools/loop/phase.sh`** — phase F's checklist is read from the tail prompt, because that is the prompt the
   process handed phase F is started with.
4. **`tools/loop/fixtures/pi`** — `PI_STUB_EVENTS_TAIL`, so a case can give the two children two streams. The
   `stub_write` and `stub_per_item_worker` fixtures write the tree for the gate child and leave the tail's tree
   alone (a tail that rewrote the change is what `run#aCorrectiveRoundIsHandedTheCorrections` measured).
5. **Cases** — one extended (`run#childUsageIsRecordedPerItem`, 12 assertions), one extended with a negative
   (`run#falsifiedSkipClosesWithItsMeasurement`: a report with no gate line of its own starts no tail), one rewritten
   for the moved phase (`prompt#workerNamesEveryPhaseAndSkill`), two given the new prompt
   (`prompt#thePhasePromptsCarryTheirOwnRoute`, `phase#oneCallPrintsThePhaseState`), eight process-count assertions
   realigned, and two fixtures of two cases adjusted so a tail child does not overwrite what the gate child staged
   (`run#aCorrectiveRoundIsHandedTheCorrections`, `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`).
6. **Mutations** — `76.1` added (the tail launch's condition made unreachable must turn the extended case red) and
   `9.5`/`45.1` re-pointed at the two lines this change renames (`$worker_exit` → `$last_exit`).
7. **`guidelines/Backlog.recipes.md`, its section "Running one item: the script, the child and the package"** — the phase diagram and one paragraph: where a gated item's tokens go, the
   split, and the numbers above.
8. **One pre-existing red the gate carried, cleared** — `labels#neverWritesReservedLabel` counts the authorisation
   label's occurrences in `tools/loop/**` and expects one; the comment #745 landed in `run.sh:700` named it literally
   and made the count two. The sentence now says "the authorisation label", which is what it meant.
9. **The red-gate verdict is the tail's, and the outcome block has an arm for it** (review round 1) — a tail that
   reports `blocked` was recorded with the opposite sentence ("no archived change appears in the run's diff") and the
   gate's numbers were dropped; the arm records the tail's verdict and its gate line, and
   `run#aRedGateIsBlockedByTheTail` stages it.
10. **The tail's `{{GATE_OUTPUT}}` is the gate's own tool output**, cut from the item's event file where a gate
   prints, followed by the child's closing answer — not, as the first version had it, only the child's final message,
   which is the report block and holds nothing the gate printed.
11. **The round-2 tail is snapshotted for outside writes like the item's** (review round 1) — the second call site of
   the same split had kept the defect the first one fixes.

surface: 2 cases added (`run#aRedGateIsBlockedByTheTail`, and `run#childUsageIsRecordedPerItem`'s red half), 2 extended (`run#childUsageIsRecordedPerItem` 14 assertions, `run#falsifiedSkipClosesWithItsMeasurement` a negative), 1 rewritten phase assertion, 2 cases given the new prompt (6 assertions), 9 process-count assertions realigned, 3 case fixtures adjusted (`run#aCorrectiveRoundIsHandedTheCorrections`, `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, `run#childWriteOutsideTheWorktreeIsNoted`), 3 engine fixtures changed (`tools/loop/fixtures/pi`, `stub_write`, `stub_per_item_worker`), 1 engine function added to `run.sh` with 3 call sites, 1 stale-result arm, 2 outside-write snapshots, 2 new outcome arms, 1 prompt rewritten, 1 prompt added, 1 line in `phase.sh`, 1 pre-existing red cleared, 2 guideline edits, 2 mutation records added and 2 re-pointed — `grep -rn 'worker-tail' tools/loop tools/loop-cases.sh | wc -l` → 30 sites; `grep -c "grep -c '\^invocation'" tools/loop-cases.sh` → 9, 7 of them rewritten

The count is a line of its own because `measure.sh` reads it (`LOOP-MEASURE-SURFACE`), and the surface is what the
estimate is set against.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | `tail_after_gate`; the tail started after the gate child and after the rework child; fields read from the final report; the commit and the exit path follow the last child; the stale-result arm names the tail first; the two `outside-write` readings |
| `tools/loop/prompts/worker.md` | Phase D ends at the gate; Phase F removed; the report contract's verdict rule |
| `tools/loop/prompts/worker-tail.md` | new — Phase F alone, and the final block |
| `tools/loop/phase.sh` | phase F's checklist is the tail prompt's |
| `tools/loop/fixtures/pi` | `PI_STUB_EVENTS_TAIL` |
| `tools/loop-cases.sh` | the extended red case, the realigned counts, the two fixtures, the phase-F assertion, the prompt-route list |
| `tools/loop-mutations.sh` | one record: the tail launch made unreachable |
| `guidelines/Backlog.recipes.md` | its section "Running one item: the script, the child and the package" — the diagram and the paragraph that states the split |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement added (merged at archive) |

**Subtractive in effect, additive in code**: an item's first child stops doing a phase it decides nothing about, and a
fresh child does it at the baseline. No behaviour an item's owner sees changes — the same archive, the same gate line,
the same blocker — except that a red gate's output is now quoted into the tail's prompt instead of being re-read by a
session that had already left it behind.

**Rollback**: delete the `tail_after_gate` call sites (the item's and the rework round's), restore `worker.md`'s Phase
F section and the report block, delete `worker-tail.md` and `phase.sh`'s phase-F arm, restore the `worker*` stale-result
arm and the `stub_write` fixture's single arm, and `run#childUsageIsRecordedPerItem` fails exactly as
`evidence/red-on-HEAD.log` records.

**Not in scope** (the item's own boundary): what the *reviewer* costs (the review round is already a fresh child), the
4 k tool-result step the same measurement names (15 % of the remaining spend), and the 40 `blocked` items' share —
which this change touches only in that a blocked gate now pays one baseline instead of a whole context per turn.

No compiled file changes: the gate for `tools/loop/**` is `tools/loop-selftest.sh`, offline, and the change is a
shell and prompt delta.
