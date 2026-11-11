# Tasks

Reference: `proposal.md` for scope, root cause and the red runs; `specs/decision-queue/spec.md` for the contract.

## 1. The red cases

Requirement: "Every writer and reader of an answer's shape reads one home" and "A park writes its answer and moves no
label", in `specs/decision-queue/spec.md`.

- [x] 1.1 `decisions#anAnsweredCreationIsNotAParkedItem` with the `answered-creation` fixture: an item carrying the
      decision label whose last statement is the shape the filing path writes prints the question that answer binds,
      in the JSON and the human form, and no `parked:` entry. Verify: red on the base `372abed0` —
      `expected 'merge or keep the file?', got 'parked: keep the file'`, `tests="2" failures="1"`
      (`.pi/issue-loop/items/859/red-on-HEAD.log`, `red-on-HEAD.xml`, 2026-11-11T20:24:40Z).
- [x] 1.2 `create#theAnswerShapeIsWrittenFromTheConstants`: the filing path's recorded answer is the literal shape, and
      mutating either constant moves it. Verify: red on the base in the form a shell gate has — both mutations
      `STAYS-GREEN` (`.pi/issue-loop/items/859/red-mutations-on-HEAD.log`, 2026-11-11T20:25:11Z).

## 2. The mark the park writes, and the shape's one home

- [x] 2.1 `tools/issues/lib.sh`: `ISSUES_PARK_MARKER` and `ISSUES_ANSWER_BOUND_JQ`; `ISSUES_PARKED_JQ` requires the
      mark; the comment above the answer's two halves names the filing path as a reader. Verify: case 1.1 green and
      `decisions#aParkedItemIsListedAsParked` still green.
- [x] 2.2 `tools/issues/answer.sh`: the park outcome appends the mark to its comment. Verify:
      `answer#parkWritesTheAnswerAndKeepsTheMode` green with its new assertion, and A3.1 goes red when the line is
      removed.
- [x] 2.3 `tools/issues/decisions.sh`: `--arg parkMarker`, `$bound`, and the branch that prints the park, the bound
      question, or the stated question. Verify: cases 1.1, `aParkedItemIsListedAsParked`,
      `theQuestionIsNotTheVerdictWord` and `listsTheQueueAndItsQuestions` green.
- [x] 2.4 `tools/issues/create.sh`: the recorded answer is written from the two constants. Verify: case 1.2 green, and
      2.1/2.2 of `create-mutations.json` red.

## 3. The fixtures the change realigns

- [x] 3.1 `scenarios/parked/issues.json` carries `<!-- park -->` in the park's comment, because it stands for a park
      `answer.sh --park` wrote. Verify: `decisions#aParkedItemIsListedAsParked` green (it would print the bound
      question without the mark).
- [x] 3.2 `scenarios/answered-creation/issues.json` added. Verify: case 1.1 green.

## 4. The revert-checks the new invariants owe

- [x] 4.1 2.1 and 2.2 of `tools/issues/tests/create-mutations.json` mutate `ISSUES_ANSWER_PREFIX` and
      `ISSUES_ANSWER_MARKER` in `tools/issues/lib.sh`; the case is `create#theAnswerShapeIsWrittenFromTheConstants`.
- [x] 4.2 A3.1 mutates the park's mark line in `tools/issues/answer.sh` (`answer#parkWritesTheAnswerAndKeepsTheMode`),
      A3.2 the mark's requirement in `ISSUES_PARKED_JQ`, A3.3 the bound-question branch in `tools/issues/decisions.sh`
      (both `decisions#anAnsweredCreationIsNotAParkedItem`). A2.2's search follows the branch it mutates.
- [x] 4.3 Verify: `bash tools/mutate.sh --mutations tools/issues/tests/create-mutations.json` and the same over
      `decision-question-mutations.json` — `5 of 5` and `7 of 7` went red and restored green, 0 problems
      (`.pi/issue-loop/items/859/revert-check.log`, 2026-11-11T20:48:47Z).

## 5. The prose the rule lives in

- [x] 5.1 `guidelines/Backlog.md` §5: the park rule states the mark, why the answer's shape is not evidence, and what
      the queue prints for an answer without it. Verify: `tools/check-doc-routes.sh` and `tools/check-doc-values.sh`
      green (`structural-checks.log`).
- [x] 5.2 The delta `specs/decision-queue/spec.md` (one added requirement, one modified with three scenarios) and this
      change's `proposal.md`/`design.md`/`tasks.md`. Verify: `tools/check-openspec-hygiene.sh` and
      `tools/check-change-readiness.sh` green (`structural-checks.log`).

## 6. Verification

- [x] 6.1 No compiled file changes (`tools/**`, tests, spec, guideline only), so no build and no Gradle suite is owed;
      the structural checks stand in: `tools/check-openspec-hygiene.sh`, `tools/check-change-readiness.sh`,
      `tools/check-doc-routes.sh`, `tools/check-doc-values.sh`, `tools/check-todo-references.sh`, and `--list` over
      the two mutation sets, each `exit=0` (`structural-checks.log`).
- [x] 6.2 The suite the gate runs over `tools/issues/*`: `bash tools/issues/tests/issue-tooling-selftest.sh` — `all 168
      cases passed` (`.pi/issue-loop/items/859/green-suite.log`, 2026-11-11T20:44:13Z), the two cases this change adds
      among them.
- [x] 6.3 The reached cases, one at a time: both new cases, the parked case, the park's writer case, and the two queue
      cases whose predicate moved (`.pi/issue-loop/items/859/green-cases.log`, 2026-11-11T20:41:37Z).
