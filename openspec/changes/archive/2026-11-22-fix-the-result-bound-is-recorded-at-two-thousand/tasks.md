# Tasks — the result bound is recorded at 2 000

Every step is a step of the plan; nothing here is a step the deferred gate takes (the gate is the gate child's, and
this change archives after it).

## 1. The red case, before the value moves

- [x] Add `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` to `tools/loop/tests/loop-cases.sh` (case list, the
      dispatch table, and a body whose first line declares `tools/loop/tests/loop-cases.sh tools/loop/loop.conf
      tools/pi-trim-result.mjs`): a 3 003-character result against the bound the file records, which must arrive cut
      and named.
- [x] Rewrite `trim#aResultUnderTheBoundIsHandedOverWhole` to read the recorded bound from `tools/loop/loop.conf`
      instead of passing `--bound 8000`, so the arm that must stay whole is measured against the number the tree
      stands on, and add `tools/loop/loop.conf` to its `# files:` declaration.
- [x] Verify, with the recorded value still 4 000: `bash tools/loop/tests/loop-selftest.sh --case 'trim#*'` →
      `FAIL trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut`, the three shipped `trim#*` cases `PASS`,
      `1 of 4 cases failed`, exit 1. Log: `evidence/red-on-HEAD.log`; tally `evidence/red-on-HEAD.xml`.

## 2. The value

- [x] `tools/loop/loop.conf` — `LOOP_TOOL_RESULT_MAX_CHARS=4000` → `=2000`, with the paragraph #1004 owes the key
      (why 2 000 and not 1 000, and that `GATE_EVIDENCE_BOUND` keeps gate/evidence output at 8 000 so a red log is
      not what moves); the two sentences the new number falsifies ("the number is the archive's p90 rounded up",
      "nine results in ten") reattributed to the 8 000 they were measured at.
- [x] Verify: `bash tools/loop/tests/loop-selftest.sh --case 'trim#*'` → all four `PASS`, including the case that
      was red in step 1.

## 3. The prose the number falsifies

- [x] `tools/pi-trim-result.mjs` — `VERIFICATION_READ_BOUND`'s doc comment stops calling 2 000 "a quarter of the
      recorded result bound" and says it is the number the recorded bound holds.
- [x] `guidelines/Backlog.recipes.md` section 7a — the two 8 000s become the number `tools/loop/loop.conf` records, and the
      `9.0 %` / `14 %` figures are stated as the measurement of the 8 000 the bound shipped with.
- [x] `tools/loop/tests/loop-cases.sh` — the two comments that say "the 4 000 this project records" about a
      measurement taken then say "then recorded".
- [x] Verify: `./tools/look.sh 'the 4 000 this project records' --path tools` → `0 matches in 0 files` (the
      phrase still appears in this change's own artifacts, which is where it is a quotation and not a claim).

## 4. The capability delta

- [x] `specs/agent-issue-loop/spec.md` — one `MODIFIED` requirement, "A child's tool results are bounded and a trim is
      named", carrying all five scenarios (four shipped, one added for the new case) and the one clause the tighter
      number falsifies dropped from the at-or-under scenario.
- [x] Verify: `openspec validate fix-the-result-bound-is-recorded-at-two-thousand --strict` → `is valid`.

## 5. The checks this delta owes

- [x] No compiled file is touched, so no build stands in for one; the structural check of the files edited is
      `bash -n tools/loop/tests/loop-cases.sh`, `node --check tools/pi-trim-result.mjs`,
      `bash tools/check-case-names.sh` and `bash tools/check-doc-values.sh` — all green (`evidence/structural.log`).
- [x] The cases that read the recorded value, run against it: `trim#*` (4), `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst`,
      `adapter#theChildIsStartedWithTheResultBound`, `adapter#theResultBoundExemptsGateEvidence`,
      `look#aResultOverTheBoundIsCutAndNamed`, `gate#theBlockStaysInsideTheRecordedBound`,
      `phase#aResultOverTheBoundIsCutAndNamed`, `phase#theIdentitySurvivesTheBoundOnAnyRoot`,
      `prompt#thePhasePromptsCarryTheirOwnRoute` — all green (`evidence/green-focused.log`).
- [x] The full `--from-git` selection a `tools/loop/**` delta owes — 126 cases declare `tools/loop/loop.conf` in the
      tree this change leaves, 124 on the base (`evidence/structural.log`) — is the gate's own step and is not run
      here: this item's run defers the gate to a fresh child.
