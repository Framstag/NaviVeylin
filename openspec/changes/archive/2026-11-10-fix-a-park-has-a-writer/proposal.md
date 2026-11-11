# A park is an answer with a writer

## Why

Issue **#845**. Root cause, as the change reported it: the answer verb has no outcome that records a decision
and moves nothing, so a choice to park is unwritable on the item it was made on and the queue can only print the
question it settles — a person who decides "wait for #844" had no way to record that choice, and the queue kept
showing the fork as if it were still open.

Evidence, red on the base `95efb9036e86b0eb72930085d00027e9949a3273`
(`evidence/red-on-HEAD.xml`, `evidence/red-on-HEAD.log`,
2026-11-10T10:28:12Z): `tests="2" failures="2"` —
`answer#parkWritesTheAnswerAndKeepsTheMode` fails with `answer: unknown argument --park: expected '0', got '2'`,
and `decisions#aParkedItemIsListedAsParked` prints the answer's own first line
(`Decision: wait for #844, the prerequisite, before either row moves`) where the queue owes the condition.

**The single resulting fix:** `tools/issues/answer.sh` gains the fourth outcome `--park`, which writes the answer
— the `Decision: …` line and its `<!-- answers: … -->` marker, whose shape this change puts in one place,
`tools/issues/lib.sh` beside the question program, for the writer and the reader it names — and performs no
transition; `tools/issues/decisions.sh` prints
`parked: <the decision's first clause>` for an item whose last statement is such an answer.

**Owning requirement:** `openspec/specs/decision-queue/spec.md`, "An answer performs the transition it implies"
(its three named outcomes leave no park, no no-transition arm) and "The queue names what awaits a decision and
what each item asks" (it prints the statement the item carries, where a parked item owes its condition instead of
the settled question). This delta adds one requirement carrying both, bound to the two cases above.

## What Changes

1. **`tools/issues/answer.sh`** — `--park`, and an empty `park)` arm in the transition case: the comment is
   written first, exactly as the other outcomes write it, and nothing else happens. Usage and help lines name the
   fourth outcome.
2. **`tools/issues/lib.sh`** — `ISSUES_ANSWER_PREFIX` and `ISSUES_ANSWER_MARKER` (the two halves of an answer,
   next to `ISSUES_QUESTION_JQ`) and `ISSUES_PARKED_JQ`, the program that answers `parked: <first clause>` or the
   empty string when a comment body is not an answer.
3. **`tools/issues/decisions.sh`** — when the last statement is an answer, the entry is `parked: <clause>` rather
   than the question read from it; the item stays listed and counted because it still carries the label.
4. **`guidelines/Backlog.md` §5** — the rule list grows from six to seven: "a park writes its answer and moves no
   label", with the falsifying observation; §5's reader sentence now says "the same writer every other answer
   leaves through".
5. **`.pi/skills/answer-decision` sections 6 and 7** — the park lane, its command line, and what the queue then says.
6. **Cases, fixture, mutations** — `answer#parkWritesTheAnswerAndKeepsTheMode` and
   `decisions#aParkedItemIsListedAsParked`, the `parked` scenario fixture, revert-checks A2.1 (the empty arm) and
   A2.2 (the queue branch).

## What this does not do

- It does not touch the `continue|device` arm, `tools/issues/update.sh` or `tools/loop/run.sh`'s decision cap
  (the item's own *Not in scope*).
- It does not change how many decision slots a park spends: the ceiling counts the label *and* the loop's opt-in,
  and a park removes neither (`agent/eligible` is the owner's label), so the cap behaves exactly as before.
- `guidelines/Backlog.md` §1's ledger row (row 7) names the verb's three outcomes and now names the fourth
  (`--park`, applied 2026-11-10) beside them; the rule itself lives in §5's list.
- No compiled file changes: shell, docs, skill and tests only.

## Capabilities

### Modified Capabilities

- `decision-queue` — a fourth answer outcome that moves nothing, and the queue entry a parked item prints
  (`specs/decision-queue/spec.md` in this change).

## Impact

- **Additive**, no breaking change: `--park` is a new argument, the three existing outcomes keep their behaviour
  and their cases.
- **Affected files:** `tools/issues/answer.sh`, `tools/issues/lib.sh`, `tools/issues/decisions.sh`,
  `tools/issues/tests/issue-tooling-cases.sh`, `tools/issues/tests/fixtures/scenarios/parked`,
  `tools/issues/tests/decision-question-mutations.json`, `guidelines/Backlog.md`,
  `.pi/skills/answer-decision/SKILL.md`, and this change's `specs/decision-queue/spec.md`.
- **Guidelines affected:** `guidelines/Backlog.md` §5 (the rule list), §14 unchanged; no `Design.md`/`UI.md`
  section is touched.
- **Rollback:** drop the `park)` arm, the `--park` flag and `ISSUES_PARKED_JQ`, restore the queue's
  question-only read, and drop this delta — the two cases that hold the behaviour fail again, which is the
  rollback's own check.
