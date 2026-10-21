## 1. Red test (spec: decision-queue — "The question is the sentence the escalation asks, not its verdict word")

- [x] 1.1 Add the fixture `tools/issues/fixtures/scenarios/verdict-question` — one item whose last comment is
  `needs-decision`, a blank line, then `**The decision:** ship the fixed cut at 800 lines, or ratchet …` — and
  three cases before touching the predicate: `decisions#theQuestionIsNotTheVerdictWord`,
  `answer#theAnswerQuotesTheQuestionNotTheVerdictWord`, `WorkPackage#questionIsNotTheVerdictWord`. Verify: red on
  HEAD — the queue prints `needs-decision`, the answer records `<!-- answers: needs-decision -->`, and the package
  carries `question: needs-decision` (`evidence/red-decisions-case.log`, `evidence/red-answer-case.log`,
  `evidence/red-workpackage-case.log`, HEAD `8a45db6`). — **done 2026-10-21**.

## 2. The fix (spec: the same requirement, all three scenarios)

- [x] 2.1 `tools/issues/lib.sh`: `ISSUES_QUESTION_JQ` (first `**The decision:**` line, else first non-empty line)
  and `issues_question_of`. Verify: `bash tools/issue-tooling-selftest.sh --case
  'decisions#theQuestionIsNotTheVerdictWord'` passes. — **done 2026-10-21**.
- [x] 2.2 `tools/issues/decisions.sh`: the `question:` field splices that program into its single `jq` pass.
  Verify: `decisions#theQuestionIsNotTheVerdictWord` and `decisions#listsTheQueueAndItsQuestions` both pass. — **done
  2026-10-21**.
- [x] 2.3 `tools/issues/answer.sh`: the `<!-- answers: … -->` marker reads the wrapper instead of the first
  non-empty line. Verify: `answer#theAnswerQuotesTheQuestionNotTheVerdictWord` and
  `answer#theAnswerNamesTheQuestion` pass, and `answer#continueReturnsTheItemToTheQueue` still passes. — **done
  2026-10-21**.
- [x] 2.4 `tools/issues/core/WorkPackage.java`: `questionLine` restates the rule for the pairing fallback, with
  the cross-reference to `ISSUES_QUESTION_JQ` in its comment. Verify:
  `WorkPackage#questionIsNotTheVerdictWord` passes. — **done 2026-10-21**.
- [x] 2.5 `guidelines/Backlog.md` §5: one clause naming what "the question" is. Verify:
  `bash tools/check-doc-routes.sh` exits 0. — **done 2026-10-21**.

## 3. Green (spec: every scenario above)

- [x] 3.1 `bash tools/issue-tooling-selftest.sh`. Verify: `all 116 cases passed`, exit 0
  (`evidence/green-issue-tooling.log`, 2026-10-21T18:00:32Z). — **done 2026-10-21**.
- [x] 3.2 No loop suite owed: no `tools/loop/**` file changes (`git status --short` names none), because the run
  writes an escalation and this change only reads one. Verify: `git diff --name-only` lists no `tools/loop/`
  path. — **done 2026-10-21**.

## 4. Falsification (revert-check)

- [x] 4.1 Add `tools/decision-question-mutations.json` — `A1.1` reverts the program in `tools/issues/lib.sh` to
  `| .[0]`, `A1.2` disables the `**The decision:**` branch in `WorkPackage.java` — and run
  `bash tools/mutate.sh --mutations tools/decision-question-mutations.json`. Verify: both `RED(exit 1)` and
  restored `GREEN(exit 0)`, `2 mutation(s) run, 2 went red and restored green` (`evidence/mutations-A1.log`).
  — **done 2026-10-21**.
- [x] 4.2 List the set in the issue-tooling CI step (`.github/workflows/build.yml`) beside the other mutation
  sets, so a set that no longer parses fails the step instead of rotting. Verify:
  `bash tools/mutate.sh --mutations tools/decision-question-mutations.json --list` prints both labels, and
  `bash tools/sensor-selftest.sh --case 'workflow#onlyTwoTriggers'` still passes — the one line is inside an
  existing `run:` block, so the step count that case watches does not move. — **done 2026-10-21**.

## 5. Gate: none owed, and why

- [x] 5.1 Run no Gradle gate: no compiled file is touched (the Java core is compiled by the issue tooling's own
  self-test). Verify: `git diff --name-only` lists only `tools/**`, `guidelines/Backlog.md`,
  `.github/workflows/build.yml` and this change. — **done 2026-10-21**.

## 6. Archive

- [x] 6.1 Archive the change and confirm the requirement merged into `openspec/specs/decision-queue/spec.md`.
  Verify: `grep -n 'not its verdict word' openspec/specs/decision-queue/spec.md` matches. — **done 2026-10-21**.
