# Design — the gate ends the child that ran it

## The root cause, one mechanism

`tools/loop/run.sh` starts **one** child for the worker phase, and the gate sits in the middle of that child's
session: `tools/loop/prompts/worker.md`'s Phase D runs the gate and Phase F (evidence copies, the pointer check, the
archive) follows it in the same process. Everything the derivation put into that context — Phase A's reads, the
plan, the focused rounds, every tool result they injected — is re-read on every turn of a tail that decides none of
it: `python3 tools/loop-spend.py` (2026-11-02) charges 1.43 B of the 1.83 B tokens of the 274 gated sessions after
the first `LOOP-GATE` line, at 104 k of context per turn against 52 k before it.

## The single fix

The first child ends when it reports the gate line, and a second, fresh child (`tools/loop/prompts/worker-tail.md`)
performs Phase F from a prompt that carries what it needs and nothing else:

```
worker-prompt.md            phase A … D, the gate once  →  LOOP-CHANGE / LOOP-ROOT-CAUSE / LOOP-RED-TEST / LOOP-GATE
worker-tail-prompt.md       phase F                     →  the final block, the gate line carried verbatim
```

`tail_after_gate` renders the tail prompt with seven values: the gate line, the change name, the change's
`tasks.md`, the root cause, the red test, the evidence directory, and a bounded extract of the first child's own
transcript (its last 200 lines, which end at the gate it ran). The run reads the item's verdict, change, gate line
and decision from the tail's report when one ran, and from the first child's otherwise.

## The six things the split breaks, and how each is avoided

Each was found by a case or by the change's review round, not by reading:

1. **`run_child`'s `worker*` glob.** `clear_stale_test_results` runs for the worker phase (issue #253) and the tail's
   prompt name also begins `worker` — so the tail would delete the test results the gate had just written, the
   directory it exists to read. The arm names `worker-tail*` first and skips the clear for it.
2. **The `outside-write` reading is a global of the child that just ended.** The tail overwrote the gate child's
   difference in the project's tree, and `run#childWriteOutsideTheWorktreeIsNoted` measured it: the worker's step was
   recorded 0 times. The gate child's reading is therefore snapshotted before the tail starts (and recorded as
   `outside-write`; the tail's as `outside-write-tail`), and a gate child whose write decides the item (strict
   outside-write) starts no tail at all.
3. **A report that already answered for the item.** A falsified or skipped report carries its own `LOOP-GATE: not
   run` line — the record of the gate it did not run — so a rule keyed on the line alone started a tail for an item
   whose tree never changed (`run#falsifiedSkipClosesWithItsMeasurement`). The tail is started only for a child whose
   verdict is absent or `closed`; `closed` is kept because it is the shape a report written against the older
   one-child contract carries.
4. **The fixtures wrote the tree for both children.** `stub_write`'s `worker*)` arm rewrote `demo-fix.txt` for the
   tail, so the round-2 worker's content vanished before the run measured the round's diff
   (`run#aCorrectiveRoundIsHandedTheCorrections`). The arm leaves the tail's tree alone, and `stub_per_item_worker`'s
   per-item counter increments for the gate child only — otherwise item n's tail would write item n+1's tree.

5. **The red-gate verdict had no outcome arm.** The tail is the process that decides a red gate, and the dispatch read
   only the gate child's shapes: a tail reporting `blocked` fell to "reported success but no archived change appears in
   the run's diff" — the opposite of what it said — and its gate line was dropped. The arm records the tail's verdict
   with the gate's numbers, and `run#aRedGateIsBlockedByTheTail` stages it.
6. **Only the report was extracted, and a gate prints in its tool output.** `run_child` sends the child's stdout to the
   report, but the adapter prints only the last assistant message there: the gate's console lives in the item's event
   file as `tool_execution_end`. The extract is now both — the event stream's tool text first, then the closing answer —
   and the case stages a gate console line so the two sources cannot be confused again.

## Blast radius

| surface | what it is |
|---|---|
| `tools/loop/run.sh` | one function (`tail_after_gate`), two call sites (the item's worker, the rework worker), the field reads, the commit/exit path, the stale-result arm, the two `outside-write` readings |
| `tools/loop/prompts/worker.md` | Phase D ends at the gate; Phase F removed; the verdict rule |
| `tools/loop/prompts/worker-tail.md` | new — Phase F and the final block |
| `tools/loop/phase.sh` | phase F's checklist read from the tail prompt |
| `tools/loop/fixtures/pi` | `PI_STUB_EVENTS_TAIL` |
| `tools/loop-cases.sh` | 2 cases added, 2 extended, 9 process-count assertions realigned, 3 fixtures adjusted, the phase-F assertion, the prompt-route list |
| `tools/loop-mutations.sh` | `76.1`, `76.2` |
| `guidelines/Backlog.recipes.md` | its section "Running one item: the script, the child and the package" — the diagram and the paragraph |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement (merged at archive) |

## Alternatives the evidence eliminated

- **A smaller context for the same child** (keep one process and trim its tool results): the archive's 4 k tool-result
  step is measured at 627 M tokens of a remaining 962 M, i.e. 15 % of what is left — `LOOP-SPEND-CAP4K
  saved=627353226` against `LOOP-SPEND-CAP8K saved=255829564`, and the 8 k cap is already in place. It does not
  address the mechanism this item names: a per-turn re-read of a context that has already been paid for.
- **Ending the child earlier** (after Phase C, before the gate) — moves the gate into the tail, whose session then has
  to re-read the plan and the change to run it. The gate is the last thing that needs the derivation, so it is the
  correct boundary.
- **Letting the tail repair a red gate** — the prompt's own rule is that the gate runs once per item ("a second
  attempt at one gate is how a run spends its whole budget inside one child"). A red gate is `blocked` with its
  output quoted into the tail's prompt, not repaired by a session that did not run it.

## Rollback

Delete the two `tail_after_gate` call sites, restore `worker.md`'s Phase F and its report block, delete
`worker-tail.md` and `phase.sh`'s phase-F arm, restore the `worker*` stale-result arm and `stub_write`'s single arm,
and drop `76.1`. `run#childUsageIsRecordedPerItem` then fails exactly as `evidence/red-on-HEAD.log` records.

## Not measured, and named

- **The saving this change expects is a model estimate, not a measurement**: the issue estimates the tail at ~0.3 B
  instead of 1.43 B over the archive, and only two `python3 tools/loop-spend.py --quiet --since <day>` runs either
  side of this landing, compared per item, can decide it. What is measured here is the shape: two children, the sum,
  the baseline (`run#childUsageIsRecordedPerItem`).
- **The 40 `blocked` items' 658 M** is where the tail is paid most often (no archive to write, but the gate's output
  to read); this change does not measure that share, only that the tail starts at the baseline.
