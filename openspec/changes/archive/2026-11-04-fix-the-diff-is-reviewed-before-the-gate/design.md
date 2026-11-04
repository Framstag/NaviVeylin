# Design — the diff is read before the gate

## The root cause, one mechanism

The item's gate sits **in the middle** of the change child's session: `tools/loop/prompts/worker.md`'s Phase D runs it,
and only after that child exits does `run.sh` start the reviewer. A finding a reader can make from the diff alone
therefore cannot be made before the gate, so it costs a gate the change did not need and a rework child that re-reads
the item, the diff and the guidelines and then runs the gate **again**. Over the archive (227 items, 2026-11-03) that
is 1 581 of 2 591 Mtok — 61 % — spent after the item's first gate, and 117 child sessions that ran the gate three
times or more.

## The single fix

The change ends the child that made it, the diff is read, and a fresh child runs the item's one gate:

```
worker-prompt.md          phases A–D, the change applied  →  LOOP-DIFF-READY  (gate-deferred.md)
reviewer-pre-gate-prompt  the diff against the base, one reading round         (reviewer.md + pre-gate-review.md)
worker-gate-prompt.md     the findings applied, the gate once  →  LOOP-GATE     (worker-gate.md)
worker-tail-prompt.md     phase F                                            (worker-tail.md, #744)
reviewer-prompt-N.md      the rounds after the gate, as they were
```

`{{GATE_STEP}}` is one section of **one** worker prompt, not a second worker prompt: the route, Phases A–C and the
report block are the same in both shapes, and two copies of them would be two rules. `gate_step_text` substitutes it
**first** — the section names `{{GATE_CMD}}` and `{{EVIDENCE_DIR}}` itself, and `loop_render` replaces one pair at a
time on the text it has accumulated (`review_correction_scope` records the same trap, #245).

The round before the gate is not a round of the review budget: `--max-review-rounds` bounds the rounds **after** the
gate, where a change is judged on its verification as well. Nothing about the branch's review record changes — a
pre-gate round cannot be announced on it anyway, because `worktree.sh record-review-round` refuses a change that is
not archived, and the archive is written by the tail, after the gate.

## The six things the split breaks, and how each is avoided

1. **The tail's start condition read the worker's files.** `first_verdict`, `tail_after_gate` and the commit path named
   `$item_dir/worker-report.md` and `$worker_exit` directly. They now read `$gate_report` / `$gate_exit` /
   `$gate_write_decides`, which default to the change child and are replaced by the gate child — so an item with no
   gate child behaves exactly as before, and the case that counts children still counts two.
2. **`run_child`'s `worker*` glob clears test results.** `worker-gate-prompt.md` matches it, and clearing there would
   delete the change child's own focused runs — the results the diff's reviewer has just read — before the gate that
   supersedes them runs. The arm names `worker-gate*` beside `worker-tail*`.
3. **`clear_stale_test_results`' sibling, the tree work in the fixtures.** `stub_write`'s `worker*)` arm rewrote the
   change for every worker prompt; a gate child that rewrote it would erase the diff the round has just read. The arm
   names `worker-gate*)` first, and the same in `stub_per_item_worker`.
4. **The round's report is arbitrary text.** The findings are **appended** to the gate child's prompt rather than
   substituted into it: a value substituted into a skeleton is a value the skeleton's own placeholders would then
   rewrite. The post-gate rework already appends its findings for the same reason.
5. **The pre-gate round can be cut, and the run has no arm for it.** Its deadline, its turn bound and its twice-cut
   transport end the item the way the post-gate round's do — recorded, not refused, left for the next run — but they
   happen **before** any gate result exists, so the outcome block gains the arm that names the round. Without it the
   item would proceed to the round *after* a gate that never ran; `79.4` is that arm's mutation, and the case that
   owns it asserts the round after the gate was never started.
6. **A child can run the gate anyway.** The engine cannot stop an agent with a shell, so the run reads the shape the
   child left: a report with a gate line and no `LOOP-DIFF-READY` is the older one-child shape and is read exactly as
   it was — no round before a gate that has already run, and no second gate. That is also why the change realigns no
   existing case: the suite's stub change children print the old shape, and the two new cases stage the new one.

## Blast radius

| surface | what it is |
|---|---|
| `tools/loop/run.sh` | one flag, one section helper, one block, three renamed reads in the tail/commit path, one outcome arm, one stale-result arm, one `outside-write` step |
| `tools/loop/prompts/` | 3 new files (2 Phase D sections, 1 round section), 1 new child prompt, 2 edits |
| `tools/loop/loop.conf` | one recorded default |
| `tools/loop/phase.sh` | one arm |
| `tools/loop/fixtures/pi` | one role |
| `tools/loop-cases.sh` | 2 cases, 1 realigned, 1 extended, 3 fixtures |
| `tools/loop-mutations.sh` | 4 added, 3 re-pointed |

## Alternatives the evidence eliminated

- **Review the change *plan* before the apply, instead of the diff.** Cheaper still, and it cannot see what the
  failure actually is: item 7 of the review contract grades the delta against the reading set the **diff** derives,
  and four of the eight items (1, 2, 4, 6) are about claims the tree carries.
- **Keep one child and have the worker wait for a reviewer.** A child cannot be spoken to by the run, and a reviewer
  that reads a tree while the worker still writes is not reading a diff; `--review-read-only` exists because a
  reviewer that writes cannot be the reviewer (D3).
- **Move the gate into the tail.** The #744 design eliminated it: the tail would then re-read the plan and the change
  to run the gate, and it is the archive phase the split exists to make cheap. A gate child is handed the four lines
  the change child established and runs one command.
- **Fold the pre-gate round into the review budget.** It refuses nothing and it never blocks, so counting it would
  cost an item a round of the budget that decides it — and the branch cannot carry it (a change that is not archived
  is not a change `record-review-round` will write for).
- **Let the gate child repair a red gate.** The prompt's own rule is that the gate runs once per item and its result
  is quoted, not improved; a red gate is the tail's `blocked` with the failing output quoted into it (#744).

## Falsification

Each of the four new invariants is mutated once by `tools/loop-mutations.sh` and restored
(`evidence/loop-mutations.txt`): the section the change child is handed (`79.1`), the block that reads the diff and
starts the gate child (`79.2`), the findings hand-off (`79.3`), and the outcome arm a cut round records (`79.4`). The
red case itself is `run#thePreGateReviewRunsBeforeTheGate`, whose failure on the base is `evidence/red-on-HEAD.log`.

`79.4` was **not noticed** on its first run: the case asserted the phrase "cut short twice", which the round after
the gate produces as well — so the mutation moved the block to a later child and the item was still blocked with the
same words. The case now asserts the sentence only the pre-gate arm writes and that no round after the gate was
started, and the mutation is red for the right reason.

## Rollback

`--pre-gate-review off` restores the older shape through `gate-own.md` alone, with no code change. Reverting the files
removes the marker, the gate child and the round; `run#thePreGateReviewRunsBeforeTheGate` then fails exactly as
`evidence/red-on-HEAD.log` records.

## Not measured, and named

- **The saving is a model estimate, not a measurement.** Two `tools/loop-spend.py --json` runs either side of a
  landing, compared per item, are what decide it; what is measured here is the shape (the round before the gate, the
  one gate child, the outcome arm).
- **The extra child a passing item pays** is a project baseline (#744 records `LOOP-SPEND-BASELINE tokens=17056`).
  This change does not measure whether that is smaller than the gate console's removal from the derivation's context.
- **The pre-gate round's own quality** is not measured: whether a reader with no gate result finds the findings the
  post-gate reader would have found is a question two runs' worth of items answer, not this change.
