# Tasks — the block's question, read from the run's own hands

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate: `bash tools/loop/tests/loop-selftest.sh`
(one full run before the change is complete), and `bash tools/loop/tests/loop-mutations.sh --files tools/loop/run.sh`
for the invariant. No Gradle build is owed — no compiled file changes (`guidelines/Build.md` §2, the scope block).

## 1. The reader

- [x] 1.1 The block arm falls back to the worker's decision: `[ -n "$decision_field" ] || decision_field="$worker_decision"`,
  with the comment saying which source is which and what the fallback is for. Verify: cases 1.2–1.4. Satisfies
  `The question a block carries is the one the run holds`.
- [x] 1.2 Case `run#aBlockedItemCarriesTheWorkersDecision`: a worker report with `LOOP-VERDICT: blocked` and a
  `LOOP-DECISION`, no review round (`--review off`), the tree unchanged. Asserts the item's comment carries the
  worker's text and not `retry the item, or abandon it`. Declares
  `# files: tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json`.
- [x] 1.3 The two neighbours keep their meaning. `run#blockedItemAsksItsOwnQuestion`'s fixtures are unchanged —
  `STUB_WORKER_REPORT` states `LOOP-VERDICT: closed` and neither report carries a `LOOP-DECISION`, so that block still
  holds no question and still gets the run's own sentence; only the comment above it was rewritten to say which case
  is which. `run#escalationCarriesTheDecision` still quotes a reviewer.
- [x] 1.4 Mutation `29.3` turns the fallback line into a no-op; case 1.2 must go red; restore; green. Log:
  `.pi/issue-loop/items/960/mutation-29.3.log`.
- [x] 1.5 Case `run#aReviewersDecisionOutranksTheWorkers`: **both** reports carry a `LOOP-DECISION`, the round runs,
  and the escalation quotes the reviewer's and not the worker's. This is the precedence the requirement states, and no
  other case in the family can see it — measured by review round 1 on this change, which graded the third scenario
  `UNPROVEN` for exactly that gap. Mutation `29.6` makes the fallback unconditional (the worker's overrides the
  reviewer's) and this case goes red; log `.pi/issue-loop/items/960/case-s6.log` and `mutation-29.6.log`.

## 2. The rule

- [x] 2.1 `guidelines/Backlog.md` "Rule: a block asks its own question" now names the order — the reviewer's decision,
  the worker's, then the run's own two-way sentence — and cites both cases. Verify: `bash tools/check-doc-routes.sh`.
- [x] 2.2 The requirement and its three scenarios are declared in the delta below, so the invariant is machine-readable
  rather than prose only — each scenario naming the case that asserts it.

## 3. Verification

- [x] 3.1 Focused run green: `run#aBlockedItemCarriesTheWorkersDecision`, `run#blockedItemAsksItsOwnQuestion`,
  `run#aReviewersDecisionOutranksTheWorkers` and `run#escalationCarriesTheDecision` — `PASS` lines at
  `.pi/issue-loop/items/960/gate-2.txt:485-488`; the precedence case's own log is `case-s6.log`.
- [x] 3.2 Full suite green at the branch tip: `.pi/issue-loop/items/960/gate-2.txt` — **481 of 481 cases**, the loop
  suite 1075.08 s, `LOOP-GATE: tests=1180 failures=0 errors=0`, taken on a clean tree at `LOOP-GATE-TREE:
  head=26655a8a6bbe1c0e6958078b6affa62ba6ad4e2d diff=3dd7ff006c7a767c` (base `6b0e01a7`). The earlier manual run
  (`full-suite.txt`, 480 cases, 887.22 s) predates the precedence case and is kept only as that history.
- [x] 3.3 Mutation set for the touched files: `.pi/issue-loop/items/960/gate-2.txt:1266` — `loop-mutations: all 416
  cases passed — every selected record applies`; records `29.3` and `29.6` red for the right reason, restored, green
  (`mutation-29.3.log`, `mutation-29.6.log`).
- [x] 3.5 The branch carries issue #954's commit `41dc62e3` above this change's `b08c26b7` (another session stacked it),
  so the gate record is the **branch's** — `gate-2.txt:13` includes `tools/loop/prompts/reviewer.md`, which only #954
  touches — and #960's own share of it is the loop cases named in 3.1 plus records `29.3`/`29.6`. Stated so a merge
  reader knows the merge lands both items.
- [ ] 3.4 File every unrelated defect this work finds with `.pi/skills/create-issue` — nothing is fixed here that this
  change did not start.
