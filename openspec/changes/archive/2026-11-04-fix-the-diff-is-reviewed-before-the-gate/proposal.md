# The diff is read before the gate, and the gate is a child of its own

## Why

Issue **#758**. `python3 tools/loop-spend.py --json`, 2026-11-03, over the loop's archive (227 items):

```
LOOP-SPEND-TOKENS ... total=2591000000          (2 591 Mtok over 227 items)
LOOP-SPEND-GATE after=1581000000                (1 581 Mtok — 61 % — charged after the item's first gate)
```

The reviewer is not what costs that: 7 522 turns at 53.6 k tokens each, against the worker's 25 654 turns at 84.5 k.
It is what a review *triggers*. The same archive holds **117 child sessions that ran the gate three or more times**,
and a rework round is a full session on top of a full gate — the most expensive shape the loop has.

**Root cause: the gate sits in the middle of the change child's session, so a finding a reader could have made from
the diff is paid for twice.** `tools/loop/run.sh` starts one child for the worker phase
(`run_child "$item_dir/worker-prompt.md" …`), `tools/loop/prompts/worker.md`'s Phase D runs the gate in that child,
and only then does the reviewer read the diff. A refusal therefore costs a gate that the change never needed and a
rework child that re-reads the item, the diff and the guidelines and then runs the gate again.

**The requirement the defect contradicts** is `openspec/specs/agent-issue-loop/spec.md`, *"Each item is worked by a
fresh process, reviewed by a different one"* — read with the loop's own spend record, the review is a judgement about
a **diff**, and the run had no place to put that judgement before the verification it is cheaper than.

**The red case** is `run#thePreGateReviewRunsBeforeTheGate` (`tools/loop-cases.sh`): with the pre-gate review on, its
fixture's change child stops at the diff, the reviewer refuses it, and the case asserts that the reviewer's round is
logged **before** the gate child is started, that the findings are in that child's prompt, and that the item still
spends exactly **one** gate child and no rework pass. On the base it fails at *the round is read before the gate child
is started: expected 'review gate', got ''* — there is no gate child to start, no `{{GATE_STEP}}` section,
no `LOOP-DIFF-READY` marker (`evidence/red-on-HEAD.log`).

## What changes

**One fix: the change ends the child that made it, the diff is read by a fresh reviewer, and a fresh child runs the
item's one gate.**

1. **`tools/loop/prompts/worker.md`** — Phase D ends with a substituted section, `{{GATE_STEP}}`, instead of the gate
   paragraphs: `gate-deferred.md` ends the session at the diff and asks for `LOOP-DIFF-READY: <change>` in place of a
   gate line, `gate-own.md` is the older text (the gate is the child's). The report block gains `LOOP-DIFF-READY`
   beside `LOOP-GATE`, each required in its own shape.
2. **`tools/loop/prompts/worker-gate.md`** (new) states phase E alone: apply the findings the round wrote, in one
   pass, inventing nothing; run the gate once; copy the results it cites; repair nothing after the gate; print the
   four lines it was handed, verbatim, plus the gate line.
3. **`tools/loop/prompts/reviewer.md`** gains a `{{PRE_GATE}}` section (`prompts/pre-gate-review.md`, new, empty for
   every round after the gate): the round is read before the gate, item 3 (the gate's reality) is recorded as not yet
   applicable rather than graded, and a `BLOCKING:` finding here is a correction list for the gate child, not a
   refusal of the item.
4. **`tools/loop/run.sh`** — `--pre-gate-review on|off` (`LOOP_PRE_GATE_REVIEW_DEFAULT`, `on`), folded to `off` by
   every `--review off`; the block after the change child commits the diff, reads it, starts the gate child and hands
   it the findings; the tail, the report fields and the commit now follow `$gate_report` / `$gate_exit` /
   `$gate_child` rather than the worker's own files; the pre-gate round is a step (`pre-gate-review`) and its cut,
   write-through or deadline are recorded through the outcome arm this change adds first; the gate child's
   `outside-write` reading is snapshotted beside the change child's and the tail's; `clear_stale_test_results` skips
   `worker-gate*` for the reason it skips `worker-tail*`.
5. **`tools/loop/phase.sh`** — phase E's checklist is the gate child's own prompt, the rule phase F already follows.
6. **`tools/loop/fixtures/pi`** — `PI_STUB_REPORT_GATE`, and the `worker-gate*` arm ahead of `worker*` in the report
   and in `stub_write` / `stub_per_item_worker`'s action, whose tree work belongs to the change child alone.
7. **Cases** — 2 added (`run#thePreGateReviewRunsBeforeTheGate`,
   `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`), 1 realigned (`phase#oneCallPrintsThePhaseState`: phase E
   now has a checklist, and the phase without a section is `prescreen`), 1 extended (`prompt#thePhasePromptsCarryTheirOwnRoute`
   covers `worker-gate.md`), 3 fixtures (`stub_write`'s gate report and two action arms).
8. **Mutations** — `79.1`–`79.4` (the section the change child is handed, the block that reads the diff, the findings
   hand-off, the outcome arm a cut round records), and `76.1`/`9.5`/`40.3` re-pointed at the lines this change moves.
9. **`guidelines/Backlog.recipes.md`**, its section "Running one item: the script, the child and the package" — the
   phase diagram and one paragraph with the numbers above.

surface: 2 cases added, 1 realigned, 1 extended, 3 fixtures changed, 4 mutation records added and 3 re-pointed, 2
prompts added, 1 prompt rewritten, 1 prompt given a section, 1 section read by `run.sh` with 2 render sites, 1
outcome arm added, 2 `outside-write` step names, 1 arm in `clear_stale_test_results`, 1 line in `phase.sh`, 1
guideline section edited — `grep -rn 'worker-gate\|pre-gate\|pre_gate\|DIFF-READY' tools/loop tools/loop-cases.sh
tools/loop-mutations.sh | wc -l` → 74 sites

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | the flag and its default, the pre-gate block, the gate child, the report fields, the outcome arm, the stale-result arm, the two `outside-write` readings, the header |
| `tools/loop/prompts/worker.md` | `{{GATE_STEP}}`, the diff marker, the verdict rule |
| `tools/loop/prompts/gate-deferred.md`, `gate-own.md` | new — the two ends of Phase D |
| `tools/loop/prompts/worker-gate.md` | new — phase E |
| `tools/loop/prompts/pre-gate-review.md` | new — the section a round before the gate is read with |
| `tools/loop/prompts/reviewer.md` | `{{PRE_GATE}}` |
| `tools/loop/loop.conf` | `LOOP_PRE_GATE_REVIEW_DEFAULT=on`, with its record and the measurement |
| `tools/loop/phase.sh` | phase E's checklist |
| `tools/loop/fixtures/pi` | `PI_STUB_REPORT_GATE`, the `worker-gate*` arm |
| `tools/loop-cases.sh` | 2 cases added, 1 realigned, 1 extended, 3 fixtures |
| `tools/loop-mutations.sh` | 4 records added, 3 re-pointed |
| `guidelines/Backlog.recipes.md` | the diagram and the paragraph |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement added (merged at archive) |

**A bound this does not move**: the round before the gate is not a round of `--max-review-rounds`. It refuses
nothing, it never blocks, and the round the budget bounds is still the one after the gate.

**A cost this adds to a passing item**: one fresh child, charged the project's baseline once (`LOOP-SPEND-BASELINE
tokens=17056` in the #744 record), against the gate's console leaving the derivation's transcript and a refusal
before the gate no longer buying a second one. The trade is not measured here: only two `tools/loop-spend.py` runs
either side of a landing, compared per item, can decide it.

**Rollback**: set `--pre-gate-review off` (which restores the older shape through `gate-own.md` alone), or revert this
change's files — `run#thePreGateReviewRunsBeforeTheGate` then fails exactly as `evidence/red-on-HEAD.log` records.

No compiled file changes: the gate for `tools/loop/**` is `tools/loop-selftest.sh`, offline, and this is a shell,
prompt and configuration delta.
