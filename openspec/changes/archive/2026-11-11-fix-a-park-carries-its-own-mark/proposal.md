# A park carries its own mark

## Why

Issue **#859** — the two UNPROVEN findings of #845's round-2 review, carried unresolved into that landing
(`.pi/issue-loop/items/845/reviewer-report-2.md`, section 8).

**Root cause.** The answer's shape is treated as saying what wrote it, and it does not. #845 gave the shape one home
— `ISSUES_ANSWER_PREFIX` and `ISSUES_ANSWER_MARKER`, `tools/issues/lib.sh:334-335` — but left both of the other
places that spell or read it standing: the filing path writes the same two strings out as literals when it records a
tier-B finding's answered forks (`tools/issues/create.sh:402`), and the queue's park predicate (`ISSUES_PARKED_JQ`,
`tools/issues/lib.sh:360`) reads "the last statement is an answer" as "the item is parked". A comment of that shape
therefore carries nothing that says which writer made it.

Two consequences, both measured before the change:

- **The second home is live.** Mutating either constant in `tools/issues/lib.sh` moves nothing the filing path
  writes: `STAYS-GREEN` for 2.1 and 2.2 of `tools/issues/tests/create-mutations.json`
  (`.pi/issue-loop/items/859/red-mutations-on-HEAD.log`, 2026-11-11T20:25:11Z).
- **The misprint is reachable.** An item whose last statement is the shape the filing path writes, and which carries
  `agent/needs-decision`, is printed as `parked: <clause>` although nobody parked it. The label need not be written
  with the comment — no writer under `tools/issues/` sets it at filing time, so it arrives by a later write — which
  is why the label cannot be the discriminator.

**Red evidence on the base** `372abed096d8fe5183f1a69c2ee3d4a7e177b426`, in the item's evidence directory
(`.pi/issue-loop/items/859/red-on-HEAD.log`, `red-on-HEAD.xml`, 2026-11-11T20:24:40Z):

- `decisions#anAnsweredCreationIsNotAParkedItem` fails: `expected 'merge or keep the file?', got 'parked: keep the
  file'` — `tests="2" failures="1"`.
- `create#theAnswerShapeIsWrittenFromTheConstants` passes on the base, and that is its red state for a shell gate:
  its assertions are the literal shape, so the mutation that must kill it is green instead — the two `STAYS-GREEN`
  lines above.

**Owning requirement:** `openspec/specs/decision-queue/spec.md`, "A park writes its answer and moves no label" (its
predicate is what cannot tell a park from an answered creation) and "An answer is recorded on the item before any
state changes" (the shape an answer is made of). This delta modifies the first and adds one requirement for the shape
having one home, the filing path included. **Owning guideline section:** `guidelines/Backlog.md` §5 "Who may write
what", the rule "a park writes its answer and moves no label".

**The single fix.** A park writes a mark only a park writes — `ISSUES_PARK_MARKER` (`<!-- park -->`,
`tools/issues/lib.sh:341`), written by `tools/issues/answer.sh:109` — and the queue requires that mark before it
calls a statement a park; a statement of the answer's shape without it prints the question its answer binds
(`ISSUES_ANSWER_BOUND_JQ`, `tools/issues/lib.sh:347`). The filing path stops spelling the shape out and reads the same
two constants (`tools/issues/create.sh:402`), so the shape has one home and a park has a meaning the label does not
have to carry.

surface: 2 cases added, 0 rewritten, 1 fixture added, 1 fixture realigned, 12 mutations over 2 sets, 4 engine files
(`lib.sh`, `answer.sh`, `decisions.sh`, `create.sh`) — `tools/look.sh 'ISSUES_ANSWER_PREFIX|ISSUES_ANSWER_MARKER|ISSUES_PARK_MARKER|ISSUES_ANSWER_BOUND_JQ|ISSUES_PARKED_JQ'`
names every file that holds the shape and the predicate.

## What Changes

1. **`tools/issues/lib.sh`** — `ISSUES_PARK_MARKER`, the mark only a park writes; `ISSUES_ANSWER_BOUND_JQ`, the
   question an answer binds to itself; `ISSUES_PARKED_JQ` requires the park's mark as well as the answer's shape; the
   comment above the two answer halves now names the filing path as a reader of them rather than a second home.
2. **`tools/issues/answer.sh`** — the park outcome writes its mark on the line under the answer's marker.
3. **`tools/issues/decisions.sh`** — the entry is the park when the statement carries the mark, otherwise the question
   the answer binds, otherwise the question the statement states (unchanged for an ordinary escalation).
4. **`tools/issues/create.sh`** — the two halves of the recorded answer are read from `tools/issues/lib.sh`.
5. **Cases, fixtures, mutations** — `decisions#anAnsweredCreationIsNotAParkedItem` with the `answered-creation`
   fixture, `create#theAnswerShapeIsWrittenFromTheConstants`, the `parked` fixture realigned to a real park (it now
   carries the mark), `answer#parkWritesTheAnswerAndKeepsTheMode` asserting the mark, and five mutations: 2.1/2.2 of
   `create-mutations.json`, A3.1–A3.3 of `decision-question-mutations.json`.
6. **`guidelines/Backlog.md` §5** — the park rule states the mark and the case it exists for.

## What this does not do

- It does not touch `tools/issues/core/WorkPackage.java`: the package reads the answer's own `<!-- answers: … -->`
  marker, which a park still writes, and it never read the queue's rendering.
- It does not change which items the queue lists, counts or refuses: the requirement "The queue reads the whole
  labelled set" is untouched, and a park still spends a decision slot.
- It does not change the escalation's `**The decision:**` convention, `ISSUES_QUESTION_JQ`, or the fallback for an
  answer written before markers existed (issue #196's bare `Decision: 2`).
- No compiled file changes: shell, tests, spec and one guideline section only.

## Capabilities

### Modified Capabilities

- `decision-queue` — the park's mark and the queue entry an answer without it prints; one home for the shape, the
  filing path included (`specs/decision-queue/spec.md` in this change).

## Impact

- **Affected files:** `tools/issues/lib.sh`, `tools/issues/answer.sh`, `tools/issues/decisions.sh`,
  `tools/issues/create.sh`, `tools/issues/tests/issue-tooling-cases.sh`,
  `tools/issues/tests/fixtures/scenarios/answered-creation/issues.json`,
  `tools/issues/tests/fixtures/scenarios/parked/issues.json`, `tools/issues/tests/create-mutations.json`,
  `tools/issues/tests/decision-question-mutations.json`, `guidelines/Backlog.md`, and this change's
  `specs/decision-queue/spec.md`.
- **Behaviour:** a park's comment gains one line; every other comment the tooling writes is unchanged, and the 168-case
  self-test the gate runs is green (`.pi/issue-loop/items/859/green-suite.log`).
- **Rollback:** drop `ISSUES_PARK_MARKER`, `ISSUES_ANSWER_BOUND_JQ` and the two branches that read them, restore the
  queue's `$parked == ""` fallback and the filing path's literals, and drop the `<!-- park -->` line from the `parked`
  fixture — the two new cases fail again, which is the rollback's own check.
