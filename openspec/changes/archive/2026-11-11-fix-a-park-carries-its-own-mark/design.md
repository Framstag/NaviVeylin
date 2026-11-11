# Design

## Root cause

The answer's shape is read as evidence of its writer, and it is not evidence of anything: `ISSUES_ANSWER_PREFIX` and
`ISSUES_ANSWER_MARKER` (`tools/issues/lib.sh:334-335`) say what an answer looks like, not who wrote it — and two other
places were left standing when #845 declared that home. The filing path still spells both halves out as literals
(`tools/issues/create.sh:402`), so the shape has two homes; and `ISSUES_PARKED_JQ` (`tools/issues/lib.sh:360`) answers
`parked: <clause>` for any statement whose first line opens with the prefix and which carries the `answers` marker, so
the one shape the filing path writes is read as a park.

## The single fix

A park says it is a park, and the shape says where it is defined.

- `tools/issues/lib.sh` gains `ISSUES_PARK_MARKER` (`<!-- park -->`, `:341`), the line only a park writes, and
  `ISSUES_ANSWER_BOUND_JQ` (`:347`), which answers the question an answer binds to itself — the payload of the
  `answers` marker — or the empty string.
- `tools/issues/answer.sh:109` appends the mark for the `park` outcome only, under the answer and its marker, in the
  same comment: the park is still one answer written through the same writer, and the mark is one more line in it.
- `ISSUES_PARKED_JQ` requires the mark: a statement is a park when the first non-empty line opens with the answer
  prefix **and** a line of the statement is exactly `ISSUES_PARK_MARKER` (`:364`). The queue then reads
  `parked: …` only for a park, an answer-shaped statement without the mark prints the question its answer binds, and
  everything else falls through to `ISSUES_QUESTION_JQ` unchanged.
- `tools/issues/create.sh:402` writes both halves from the two `lib.sh` constants, so the filing path is a reader of
  the shape rather than its second home.

## Why the mark, and not the label or the order

The issue left two shapes of fix open and required the choice here: a park-specific mark, or a reader that also reads
the item's label state from the tracker. The second is eliminated by the reachability the item itself records, not by
taste:

- The queue already selects on the decision label (`select([(.labels // [])[] | …] | index($label) != null)`,
  `tools/issues/decisions.sh:101`) and no writer under `tools/issues/` sets it at filing time — `create.sh` files the
  `status` label it held back, and nothing else. So an answered creation carries the label as soon as a later write
  puts it there, which is exactly the state the queue cannot tell from a park. Reading the label state is reading a
  value that is identical in both cases.
- Ordering is no better: the comment's `createdAt` is not compared against any label write, because the tracker
  reports label writes with no timestamps, and a comment that predates the label is a property of the *item's life*,
  not of the statement the queue is asked about.
- A distinct prefix for a park's first line would work, but it would cost the one thing #845 bought: the package
  reads an answer by its prefix (`tools/issues/core/WorkPackage.java`, `DECISION_PREFIX`), so a park written under
  another prefix would vanish from the next worker's `<<<ITEM_DECISION` block. A third line keeps both readings
  intact.

## Blast radius

- 4 engine files: `tools/issues/lib.sh` (one constant, one program, one predicate and their comments, plus the
  comment above the answer halves), `tools/issues/answer.sh` (one line), `tools/issues/decisions.sh` (one `--arg`, one
  `as $bound` and one branch), `tools/issues/create.sh` (two literals → two constants, one comment).
- 1 case added (`decisions#anAnsweredCreationIsNotAParkedItem`), 1 case added for the shape's home
  (`create#theAnswerShapeIsWrittenFromTheConstants`), 1 case strengthened (`answer#parkWritesTheAnswerAndKeepsTheMode`
  asserts the mark), 0 rewritten.
- 1 fixture added (`scenarios/answered-creation`), 1 realigned (`scenarios/parked` now carries the mark, because it
  stands for a park that `answer.sh --park` wrote).
- 5 mutations: 2.1 and 2.2 of `create-mutations.json` (either constant moving the filing path's comment) and A3.1–A3.3
  of `decision-question-mutations.json` (the mark written, the mark required, the bound question printed). A2.2's
  search moves with the branch it mutates, which is the same invariant under the renamed condition.
- 1 guideline section (`guidelines/Backlog.md` §5), 1 capability delta.
- Not touched, and named because a reader will ask: `tools/issues/core/WorkPackage.java` (it reads the answer's own
  marker, which a park still writes), `tools/loop/run.sh`'s decision cap (a park still spends a slot),
  `.pi/skills/answer-decision` (it names `--park` and what the queue prints, both unchanged).

## Rollback

Revert the four engine files and the guideline passage; drop `ISSUES_PARK_MARKER` and `ISSUES_ANSWER_BOUND_JQ`, the
`--arg parkMarker` and the `$bound` branch, and restore `$parked == ""`; restore the two literals in `create.sh`; drop
the `<!-- park -->` line from the `parked` fixture; remove the two cases, the `answered-creation` fixture and the five
mutations. The two new cases then fail as they did on the base — `decision-queue`'s own check that the rollback
returned the tree to the state it fixed.

## Open decisions

none
