# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "Version control belongs to the run's script, not to the agent", scenarios "An answered attempt is a
rework round, not a question asked again" and "An answer to another attempt's question leaves this attempt's question
standing".

- [x] 1.1 Add `prescreen#anAnsweredBranchIsWorkNotADecision`: a branch carrying a finished attempt whose review round
      1 refused the change, an item whose comments record that branch's question and an answer quoting it, and the
      pre-screen asked for its verdict. Verify: on `87c523cd` the case fails with `expected 'eligible', got
      'needs-decision'` (2026-11-11T16:13:04Z, `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml` `tests=1
      failures=1`).
- [x] 1.2 Add `prescreen#anAnswerToAnotherAttemptDoesNotReworkTheBranch`: the same shape, with the answer quoting a
      question that names the branch and a head commit it no longer carries. Verify: green on the change
      (2026-11-11T16:17:01Z, `evidence/green-on-change.log`) and green on `87c523cd` per the `LOOP-MEASURE-CHECK
      tools/loop/tests/loop-selftest.sh --from-git 87c523cdcd6f968614ac2db28dfdbf106a86a2d2` line of
      `evidence/measure-block.log` — it is the boundary of 1.1, so it is green on both sides by design.

## 2. The engine

- [x] 2.1 `tools/loop/prescreen.sh`, `branch_question_answered`: read the item's comments once, take the question the
      last answer quotes (`<!-- answers: … -->`, else the nearest earlier non-answer comment's first line) and read
      the question as answered when it names this branch and this attempt's head.
- [x] 2.2 `tools/loop/prescreen.sh`, the branch arm's `*` case: the reason names the head commit it is about; an
      answered branch is set aside and the verdict stays `queued`, with the leftover named in the record — the two
      cases of task group 1 above.
- [x] 2.3 `tools/loop/worktree.sh`: `leftover_name` (one owner of the `loop/leftover/issue-…` name, which `start-item`
      now reads too), `set_aside_branch`, and the `set-aside <issue> <branch>` verb the pre-screen calls.
- [ ] 2.4 `tools/loop/prescreen.sh`, a branch whose rename git refuses, and a dry run, keep the decision and say so
      in the reason — the code path exists (pre-gate review, `prescreen.sh:467-475`) but no case in this change
      exercises either path; owed a case before it is claimed.

## 3. The rule's other owners

- [x] 3.1 `guidelines/Backlog.recipes.md`, section 7d, the adoption paragraph: the owner's answer outranks the branch record,
      the shape it is executed in, and what binds an answer to an attempt.
- [x] 3.2 `tools/loop/tests/loop-mutations.sh` record `274.1`: the answer read and the attempt set aside dropped, so
      the pre-screen records the same question again.
- [x] 3.3 The spec delta: the requirement's clause on an answered branch, and the two scenarios with their cases.

## 4. Verification

- [x] 4.1 The red case passes on the change, and the mutation proves the change is what does it:
      `RED 274.1 … expected 'eligible', got 'needs-decision'`, `restore … green again`,
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (2026-11-11T16:17:09Z,
      `evidence/mutation-274.1.log`), and `--check` reports the record still applies.
- [x] 4.2 The cases the rule is read beside still pass: `--group prescreen` (36 cases) and `--group worktree` (25
      cases), plus `run#leftoverBranchIsRecordedNotDropped`, `run#mergesAFinishedAttemptAndClosesTheItem` and
      `run#doesNotMergeAnAttemptWithoutAGreenVerification` — all `PASS`, 64 cases, 2026-11-11T16:18:11Z
      (`evidence/focused-cases.log`).
- [ ] 4.3 The gate over the completed change: the gate child's step, over the tree this change is archived into.
