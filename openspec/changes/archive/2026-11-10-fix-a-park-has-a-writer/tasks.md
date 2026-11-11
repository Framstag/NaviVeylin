# Tasks

Reference: `proposal.md` for scope, root cause and the red run; `specs/decision-queue/spec.md` for the contract.

## 1. The red cases

Requirement: "A park writes its answer and moves no label", scenarios "A park writes its answer and moves no
label" and "A parked item prints the condition it waits for" — in
`openspec/changes/archive/2026-11-10-fix-a-park-has-a-writer/specs/decision-queue/spec.md`.

- [x] 1.1 `answer#parkWritesTheAnswerAndKeepsTheMode`: the park posts the answer comment carrying the decision
      and the `answers:` marker and performs no transition (`issue edit|close` count 0), keeping the label and
      the status. Verify: red on the base — `answer: unknown argument --park: expected '0', got '2'`,
      `evidence/red-on-HEAD.xml` `tests="2" failures="2"` (2026-11-10T10:28:12Z).
- [x] 1.2 `decisions#aParkedItemIsListedAsParked`: the parked item stays listed and counted and its entry reads
      `parked: wait for #844` in both the JSON and the human form. Verify: red on the same base run —
      `expected 'parked: wait for #844', got 'Decision: wait for #844, the prerequisite, before either row moves'`.

## 2. The writer

- [x] 2.1 `tools/issues/answer.sh`: `--park` sets the outcome, the `park)` arm carries its comment and `;;` and
      writes no state, and the usage/help lines name the fourth outcome. Verify: case 1.1 green.
- [x] 2.2 `tools/issues/lib.sh`: `ISSUES_ANSWER_PREFIX`, `ISSUES_ANSWER_MARKER` and `ISSUES_PARKED_JQ` — one home
      for the answer's shape beside `ISSUES_QUESTION_JQ`; the program answers the empty string for a comment that
      is not an answer. Verify: both cases green.
- [x] 2.3 `tools/issues/decisions.sh`: the question slot reads `ISSUES_PARKED_JQ` first and falls back to
      `ISSUES_QUESTION_JQ` only when it answers empty, so an ordinary question is printed unchanged. Verify:
      `decisions#listsTheQueueAndItsQuestions` and `decisions#theQuestionIsNotTheVerdictWord` stay green beside
      case 1.2.

## 3. The revert-checks the new invariants owe

- [x] 3.1 A2.1 mutates the empty `park)` arm in `tools/issues/answer.sh` (the arm whose emptiness *is* "a park
      performs no transition"); the case that must fail is `answer#parkWritesTheAnswerAndKeepsTheMode`. Verify:
      red when mutated, restored green — `mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)`
      (`evidence/revert-check.log`), and the failing assertion itself — `a park performs no transition: …
      --remove-label agent/needs-decision: expected '0', got '1'` — is in the same log's captured block.
- [x] 3.2 A2.2 mutates the `| if $parked == "" then` branch in `tools/issues/decisions.sh` (the branch that
      selects the queue entry); the case that must fail is `decisions#aParkedItemIsListedAsParked`. Verify: same
      shape, same log, and its captured assertion in the same log's captured block.

## 4. The prose the rule lives in

- [x] 4.1 `guidelines/Backlog.md` §5: "Six rules" → "Seven rules" and the new rule "a park writes its answer and
      moves no label", with the falsifying observation ("A park that moved a label would spoil the queue either
      way"); §5's reader sentence reworded to "the same writer every other answer leaves through". Verify:
      `tools/check-doc-routes.sh` green (`evidence/structural-checks.log`).
- [x] 4.2 `.pi/skills/answer-decision` sections 6 and 7: the park lane, its command line, and what the queue then says.
      Verify: the same structural run.
- [x] 4.3 The delta `specs/decision-queue/spec.md` (ADDED requirement, two scenarios bound to the two cases) plus
      this `proposal.md`. Verify: `tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent`, exit 0
      (`evidence/structural-checks.log`). The change is archived, so the hygiene check is the check it gets;
      `openspec validate` is run over the open set, not over an archived change.

## 5. Verification

- [x] 5.1 No compiled file changes (`tools/**`, `guidelines/**`, `.pi/**`, tests only), so no build and no
      Gradle suite is owed — the structural checks stand in (`guidelines/Build.md` §2 scope block):
      `tools/check-doc-routes.sh`, `tools/check-doc-values.sh`, `tools/check-openspec-hygiene.sh`,
      `tools/check-todo-references.sh`, `tools/loop/evidence-check.sh`, each ending `exit=0`
      (`evidence/structural-checks.log`).
- [x] 5.2 The reached cases green on the change: the whole 166-case self-test the gate runs over the landing tree
      (`evidence/loop-gate/tools_issues_tests_issue-tooling-selftest.sh.xml`, `tests="166" failures="0"
      errors="0"`, stamped with the landing HEAD), which is the tree this change ships; the five stamped cases of
      the round-2 tree — both park cases and their two siblings — are `evidence/green-cases-round2.log`
      (`HEAD 08062c83`, 2026-11-10T11:02:34Z). The 33 `PASS` lines in `evidence/green-cases.log` are the round-1
      record: that log carries no HEAD and no timestamp and was written before the round-2 correction, so they are
      kept as the round-1 run and claim nothing about the landing tree.
- [x] 5.3 The artifacts: `tools/check-openspec-hygiene.sh`, green after this delta was written
      (`evidence/structural-checks.log`).
