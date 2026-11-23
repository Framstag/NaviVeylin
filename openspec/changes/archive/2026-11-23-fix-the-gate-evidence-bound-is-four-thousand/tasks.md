# Tasks

## 1. The gate class's width (spec `agent-issue-loop`)

- [x] 1.1 Add the red arm to `adapter#theResultBoundExemptsGateEvidence` (`tools/loop/tests/loop-cases.sh`): a
      5 000-character gate log fed at `--bound 2000` must arrive cut at 4 000, head and tail, with the middle withheld
      and named; on HEAD it arrives whole.
      — done: red (`evidence/red-on-HEAD.log`), green (`evidence/green-case.log`).
- [x] 1.2 Lower `GATE_EVIDENCE_BOUND` to `4000` in `tools/pi-trim-result.mjs` and make the class's cut keep both
      ends: `boundedTail`, `trimNotice(tool, total, bound, tail)` and the `head`/`tail` split in `trimResult`, with
      the file's comments moved to the new width and shape.
      — done: `PASS adapter#theResultBoundExemptsGateEvidence`.
- [x] 1.3 Retune the case's other arms to the recorded bound — (b) gate text and a suite XML at `--bound 2000`, and
      the by-path arms on a 3 000-character result — so "held as gate evidence" is proved against 2 000 and not
      against a fixture that a 4 000 class would cut.
      — done: `PASS` in `evidence/green-case.log`.
- [x] 1.4 Move the two prose homes: `tools/loop/loop.conf`'s bound comment and `guidelines/Backlog.recipes.md` §7 name
      the class's 4 000 and the head-and-tail cut.
      — done: `grep -rn '8 000' tools/loop/loop.conf guidelines/Backlog.recipes.md` finds only the history sentences.

## 2. Verification

- [x] 2.1 The red proof of 1.1 against `git show HEAD:tools/pi-trim-result.mjs` (`GATE_EVIDENCE_BOUND = 8000`): the
      5 000-character gate log arrives whole (5 000 bytes, no `LOOP-TRIMMED` line), so the arm fails — copied at the
      moment it was produced to `evidence/red-on-HEAD.log`.
- [x] 2.2 The cases the delta owes, focused: `--group adapter`, `--group trim`, `--group repeat` all pass
      (`evidence/affected-cases.log`).
- [x] 2.3 `openspec validate fix-the-gate-evidence-bound-is-four-thousand --strict` — `is valid`
      (`evidence/openspec-validate.log`), and `tools/check-change-readiness.sh <change dir>` — `ok`
      (`evidence/change-readiness.log`).
- [x] 2.4 The undercount the issue names as owed (how many exempt results the class really hands over) is **not**
      taken: the item records under `.pi/issue-loop/items/` are not readable from an agent's shell, so the count stays
      the trim log's 338-cut lower bound. Stated in `proposal.md` rather than presented as measured.
