## 1. Red tests (spec: agent-issue-loop — "The package carries the decision a person recorded")

- [x] 1.1 Add `WorkPackage#recordedDecisionIsCarried` and `WorkPackage#noDecisionIsRecorded` to
  `tools/issues/core/Cases.java`, before touching `WorkPackage.java`. Verify: on HEAD,
  `bash tools/issue-tooling-selftest.sh --case 'WorkPackage#recordedDecisionIsCarried'` fails with "the package
  carries a decision block of its own — expected to find '<<<ITEM_DECISION'", and the second case fails with
  "the block is present even with no comments" (`evidence/red-workpackage-case.log`,
  `evidence/red-no-decision-case.log`, 2026-10-21T17:13:32Z–17:13:33Z). — **done 2026-10-21**.
- [x] 1.2 Add `answer#theAnswerNamesTheQuestion` to `tools/issue-tooling-cases.sh` and the loop's
  `run#workPackageCarriesTheRecordedDecision` with the fixture `tools/loop/fixtures/scenarios/decided`, both
  before the production change. Verify: red on HEAD — "'answers:' not found in: gh issue comment 71 --body
  Decision:\ merge\ them…" and the loop case failing on the absent block (`evidence/red-answer-case.log`,
  `evidence/red-loop-case.log`, 2026-10-21T17:13:35Z / 17:13:56Z). — **done 2026-10-21**.
- [x] 1.3 Make the fixture `gh` honest: `issue view --json a,b` returns only the named fields, as the CLI does,
  so the missing `comments` field is observable at all. Verify: no case changed verdict — the full suites below
  are green with the projection in place. — **done 2026-10-21**.

## 2. The fix (spec: the same requirement, all four scenarios)

- [x] 2.1 `WorkPackage.render` appends the `<<<ITEM_DECISION` block (`recorded:` / `question:` / `decision:`,
  or `recorded: none`), reads the question from the answer's `<!-- answers: … -->` marker with the nearest
  earlier non-answer comment as fallback, escapes both blocks' delimiters, and clips each field at one named
  bound. Verify: `WorkPackage#recordedDecisionIsCarried` and `WorkPackage#noDecisionIsRecorded` pass. — **done
  2026-10-21**.
- [x] 2.2 `tools/loop/lib.sh`: `loop_issue_json <n> --comments`; `tools/loop/run.sh` passes it only at the
  work-package read. Verify: `grep -n 'loop_issue_json' tools/loop/*.sh` shows one `--comments` call and one
  pre-screen call without it. — **done 2026-10-21**.
- [x] 2.3 `tools/issues/answer.sh`: the recorded comment carries `<!-- answers: <the question that stood> -->`
  under the answer, with `>` stripped and the quotation clipped at a named bound. Verify:
  `answer#theAnswerNamesTheQuestion` passes and `answer#continueReturnsTheItemToTheQueue` still passes. — **done
  2026-10-21**.
- [x] 2.4 `tools/loop/prompts/worker.md`: a recorded decision is settled — implement, do not re-ask; an
  escalation after one names it and says what it leaves open, numbering its own options from 1. Verify:
  `prompt#workerTreatsARecordedDecisionAsSettled` passes. — **done 2026-10-21**.
- [x] 2.5 `guidelines/Backlog.md` §5: the rule beside the ones that keep the decision queue answerable, and
  the heading's count corrected — it said "Two rules" above a list of three, and the list now holds four.
  Verify: `bash tools/check-doc-routes.sh` — all 120 section references resolve. — **done 2026-10-21**.

## 3. Green (spec: every scenario above)

- [x] 3.1 `bash tools/issue-tooling-selftest.sh`. Verify: `all 113 cases passed`, exit 0, no exclusion
  (`evidence/green-issue-tooling.log`, 2026-10-21T17:21:16Z). — **done 2026-10-21**.
- [x] 3.2 `bash tools/loop-selftest.sh`. Verify: `all 143 cases passed`, exit 0 (`evidence/green-loop.log`).
  — **done 2026-10-21**.

## 4. Falsification (revert-check)

- [x] 4.1 Add mutation `36.1` to `tools/loop-mutations.sh` — `tools/loop/run.sh`, the `--comments` argument
  removed, case `run#workPackageCarriesTheRecordedDecision` — and run
  `bash tools/loop-mutations.sh --only 36.1`. Verify: `RED 36.1`, `restore 36.1 … green again`,
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-36.1.log`), and
  `bash tools/loop-mutations.sh --list` prints it. — **done 2026-10-21**.

## 5. Gate: none owed, and why

- [x] 5.1 Run no Gradle gate: the delta touches no compiled file (the Java core is compiled by
  `tools/issue-tooling-selftest.sh` itself, `javac --release 17`), so the two harnesses are the whole
  verification. Verify: `git status --short` lists only `tools/**`, `guidelines/Backlog.md` and this change.
  — **done 2026-10-21**.

## 6. Archive

- [x] 6.1 Archive the change and confirm the requirement merged into `openspec/specs/agent-issue-loop/spec.md`
  under "Each item is worked by a fresh process, reviewed by a different one". Verify: the archived directory
  exists and `grep -n 'The package carries the decision' openspec/specs/agent-issue-loop/spec.md` matches.
  — **done 2026-10-21**.
