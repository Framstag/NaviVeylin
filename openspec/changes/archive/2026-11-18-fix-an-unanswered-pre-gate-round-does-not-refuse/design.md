# Design — the pre-gate round records a non-answer, and the gate child decides

## Root cause

The round before the gate is one block in `tools/loop/run.sh`, and it has four endings it writes itself:

| the round's ending | what the run records today | what it should |
|---|---|---|
| it answered `PASS`/`NOTES` | the round's verdict, nothing handed on | unchanged |
| it answered `FAIL` with blocking findings | the verdict, the findings handed to the gate child | unchanged |
| a bound the run enforces ended it (deadline, role's turn bound, context bound) | the round `cut`, the item `cut` with `bound` (issues #790, #918) | unchanged |
| **its answer never arrived, twice** (the transport cut it, or the child answered with a question) | **`pre_gate_refusal` set, the item `blocked` `refused`, no gate child** | **the round `unanswered`, the gate child decides** |

The fourth row is the defect. The prompt the round is handed (`tools/loop/prompts/pre-gate-review.md`) states that a
finding there "is a correction, not a refusal" and that "this round does not block the item", and the run's own
outcome comment says the round "refuses nothing, the item is never blocked by what it finds". A round that answered
nothing is the furthest thing from a refusal: nobody read the diff, so nobody judged the item. Ending it there
throws away the change child's whole spend and hands a branch to a person — measured twice in one run
(2026-11-17T10:58Z, #916 and #917, 20.4 Mtok of 120.3).

Two sources of such an answer are known: the transport losing the answer's tail (issue #224's shape rule, retried
once) and a child answering with a question instead of the round (issue #954, whose prompt clause landed in
`2026-11-18-fix-the-review-round-is-non-interactive`). The retry is already there and stays. What changes is who
decides after the retry is cut as well.

## The single fix

In the cut-twice arm of that block, `pre_gate_unanswered=1` replaces the two lines that wrote the refusal. The
verdict the run carries out of the block becomes `unanswered` when no `VERDICT:` line was read, the step's note
says the round answered nothing, and everything below the block runs as it does for an answered round — because
`pre_gate_refusal` is empty:

- the gate child is started (`if [ -z "$pre_gate_refusal" ] && [ "$head_now" != "$base" ]`), with the findings
  placeholder saying the round handed nothing forward rather than the false "found no blocking finding";
- the item's record carries `metrics.reviewVerdict: unanswered` and `metrics.reviewRounds: 1` when the item ends on
  the gate child (the arm issue #813 opened for a pre-gate round's answer), and carries the round after the gate's
  verdict when that round runs and decides — the field's meaning either way;
- `state.sh done --review-verdict` accepts the value, and `report.sh`'s review line counts it as undecided, which
  is exactly what it is.

What is deliberately **not** touched:

- **the bound/deadline endings** still record the item `cut` with `bound`, because a bound the run enforces is the
  run's end of the item and the next run resumes it (issue #790). Continuing to a gate child the same run's deadline
  would end on its first second would spend a child to learn nothing;
- **`reviewer-wrote`** still discards the round and blocks, because the reviewer wrote in a tree it may only read —
  a fault about the child, not an answer that never arrived;
- **the round after the gate** still blocks when its retry is cut, because there the round is the item's own last
  judgement (issue #224, `run#aReportCutTwiceBlocksAsTheTransportsFault`). `block unanswered` is that arm's class,
  and the word now appears in both fields — the item's block class there, the round's verdict here. They are two
  fields of two different records, and the spec states each where it belongs.

## Blast radius

- `tools/loop/run.sh` — three hunks, +28/−4 lines: the new flag and its comment, the cut-twice arm, and the
  verdict/step/placeholder reading. `layout.tsv` bounds the file at 4 742 lines; it is 4 418 after this change.
- `tools/loop/state.sh` — the vocabulary the record writer validates (+6/−3): `unanswered` joins
  `--review-verdict`'s list, its usage line and the comment that says the vocabulary is `run.sh`'s.
- `tools/loop/prompts/reviewer.md` — one sentence of the #954 clause, because "the item is then refused" was never
  true of the round before the gate.
- No key, no configuration, no compiled file, and no reader of `metrics.reviewVerdict` outside `run.sh`, `state.sh`
  and `report.sh`: `lib.sh`'s bound derivation (`cut-short`, `deadline`) and `report.sh`'s `decided` predicate both
  read a value they do not know as "not a verdict", which is the right reading of `unanswered`.
- Rollback: revert the commit. The re-cut case then fails on its second assertion (the gate child is never
  started), which is the change's own red case.

## The alternatives the evidence eliminated

- **Keep the refusal and let the next run adopt the branch.** Eliminated by the measurement: the branch carries a
  change no gate was ever run on, so the next run pays the gate the run could have paid — with the work already
  warm — and a person pays the landing in between.
- **Start the gate child and also record the round as a refusal.** Eliminated by the record's own vocabulary: a
  refusal is a finding about the change (`block refused`), and an item that closes on the gate would carry the
  class of an arm it did not take.
- **Record `cut-short` like a bound-ended round.** Eliminated by what `cut-short` already means in this record: a
  bound the run enforces ended the round, and a reader looks up an item's cut by asking which bound fired
  (issue #923). No bound ended this one.
- **Extend the retry to a third attempt, or make the retry prompt differ.** Eliminated by issue #954's finding: the
  retry is byte-identical by design, so a second answer is a second chance at the same reading, and a third
  attempt is the run paying for a child that has twice answered nothing. The gate child is the better next reader.
- **Treat the bound/deadline endings the same way.** Eliminated by the run's own deadline: a phase started past it
  is ended on its first second, and a round restarted past it is not started at all (`review_restart_before_deadline`).

## Open decisions

none
