# Design

## Root cause

The answer verb's vocabulary is the whole vocabulary of a decision: one flag per outcome
(`tools/issues/answer.sh:36-39`; `:31-33` on the base commit), one arm each in the transition block that follows the
answer comment (`tools/issues/answer.sh:107-121`). A person who decides that the item must wait has no outcome to
name, and the skill that answers decisions has no writer to leave through — so the decision is written beside the
item, in another item's body, and the queue keeps printing the question it settles.

## The single fix

A fourth outcome that writes and moves nothing.

- `--park` sets `outcome="park"` and adds one arm to the transition block (`tools/issues/answer.sh:108-111`) that
  performs no write at all, so the comment written at `tools/issues/answer.sh:104` is the whole effect. The item keeps
  `agent/needs-decision` (it still awaits an answer, and a later answer may still continue or retire it) and keeps
  whatever status it carries.
- The two strings an answer is made of move into `tools/issues/lib.sh`, beside `ISSUES_QUESTION_JQ`:
  `ISSUES_ANSWER_PREFIX` (`Decision: `) and `ISSUES_ANSWER_MARKER` (`<!-- answers: `). `answer.sh` writes them and
  `decisions.sh` reads them, so the shape of an answer has one home.
- `decisions.sh` learns one predicate: when the last statement's first line starts with the answer prefix **and** the
  comment carries the marker, the item has been answered and still carries the label — a park — so the entry is
  `parked: <the decision's first clause>` in the place the question stands. The first clause is the decision up to its
  first `,`, `;` or `.`, so an entry stays one line and the condition the park waits for is what a reader sees. The
  marker is required, so no ordinary comment whose first line begins `Decision:` is read as an answer.

The item stays in the queue and stays counted, because a park is a decision to wait, not a decision not to decide: the
queue's job is to keep it in front of a reader, and the difference a park makes is that the entry now says what it
waits for. The work package already carries the answer to the next worker — `tools/issues/core/WorkPackage.java:177-200`
reads the answer's marker, not the queue's rendering — so the parked item's `<<<ITEM_DECISION` block states the
recorded decision without a second change.

## Blast radius

- 3 engine files: `tools/issues/answer.sh` (an arm, its usage text and its header), `tools/issues/decisions.sh` (one
  predicate in the `jq` program it already runs), `tools/issues/lib.sh` (two constants and one documented program).
- 1 fixture scenario added (`tools/issues/tests/fixtures/scenarios/parked/`) — existing fixtures are untouched, so the
  cases that assert the queue's length and order still see the sets they describe.
- 2 cases added, 0 rewritten: `answer#parkWritesTheAnswerAndKeepsTheMode` is a sibling of
  `answer#continueReturnsTheItemToTheQueue` (same fixture, same stub, same shape), and
  `decisions#aParkedItemIsListedAsParked` is a sibling of `decisions#theQuestionIsNotTheVerdictWord`.
- 1 guideline passage (`guidelines/Backlog.md` section 5), 1 skill's two sections, 1 capability's two added
  requirements.
- Not touched, and named because a reader will ask: `tools/loop/run.sh`'s decision cap (it counts the label, and a
  park does not change the label), `tools/issues/update.sh`'s label vocabulary, and `tools/issues/close.sh`.

## Rollback

Revert the commit. The fourth outcome's arm, its usage line, the two constants and the queue's predicate leave with
it, the two cases and the fixture leave with it, and the three documents return to their previous text. Nothing the
run executes reads `--park`, so a reverted tree runs exactly as before.

## Alternatives the evidence eliminated

- **A label of its own (`agent/parked`).** Impossible as the fix: a new label is a new value of the reserved
  vocabulary, and an item carrying it stops carrying `agent/needs-decision` — so the queue it must stay in would stop
  listing it, and the permanence #281 measured (18 of 35 parked items already clean) is what a state nobody re-reads
  produces. The item's own body already records that a parked item is `agent/eligible` or not, so the park cannot be a
  label.
- **A hand-written comment, or a skill step that calls `gh issue comment`.** Impossible: `.pi/skills/answer-decision`'s
  never-list forbids the four direct writes, and the marker `<!-- answers: … -->` binds the answer to the question it
  quotes — a comment nobody wrote through the answer path has no writer keeping that binding right, which is why the
  verb exists (issue #196).
- **Making the park a status write (`--park` as sugar for `--status on-hold`).** Impossible: the item already carries
  the hold, so the write would be a transition that changes nothing and needs the profile's hold status as a second
  input; the red case asserts the opposite — no `issue edit` and no `issue close` at all.
- **Printing the answer's own first line in the queue and letting its reader notice.** Measured, not argued: that is
  the red case's actual output — `expected 'parked: wait for #844', got 'Decision: wait for #844, the prerequisite,
  before either row moves'` — and it is the shape #196 and #243 spent two changes removing, a line where a question
  stands that is not a question.
- **A separate `parked` field in the queue's JSON with a branch in the human form.** Rejected: the printed statement is
  one fact, and two fields would let the document and the human form disagree. `--json` and the human form read the
  same `.question`, which is what `decisions#listsTheQueueAndItsQuestions` holds today.

## Verification

- The two red cases (Phase A) become green, and the cases this delta reaches are run with them — `answer#*`,
  `decisions#*` and `shaping#theDecisionQueueIgnoresTheShapingLabel`. The full case set of the self-test is the gate's:
  it is selected once, by the child that runs it, and this change runs it no second time.
- Two revert-check records in `tools/issues/tests/decision-question-mutations.json`: one that removes the park arm (the
  transition case must go red), one that drops the parked predicate (the queue case must go red).
- No compiled file is touched, so the build the tasks would otherwise owe is the structural checks over the files this
  change edits: `tools/check-doc-routes.sh`, `tools/check-doc-values.sh`, `tools/check-openspec-hygiene.sh`,
  `tools/check-todo-references.sh` and `tools/loop/evidence-check.sh`.
- No device, no screen: every claim above is decided by a host case.

## Open decisions

none
