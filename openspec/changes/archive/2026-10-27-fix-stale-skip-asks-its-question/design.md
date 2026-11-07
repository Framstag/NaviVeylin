# Design

## Root cause

One mechanism: the run's skip path keeps a taxonomy of "skips that state a question" beside a fall-through that
parks everything else, and `stale` — whose meaning is that the claim is gone — sits in the fall-through. The
hold it writes states no question by contract (`openspec/specs/agent-issue-loop/spec.md`, "A hold is not a
decision"), so the pre-screen must report the item as `held` and no other reader picks it up.

## The single fix

`tools/loop/run.sh`'s skip path admits `stale` to the escalation branch it already has
(`run.sh:1849`), where the run writes `agent/needs-decision` + `status/on-hold` and one comment through
`tools/loop/labels.sh escalate`. When the worker's report carries no `LOOP-DECISION`, the run states the fork the
verdict leaves — retire the item as stale, or re-diagnose it — with the worker's own finding as the ground, the
shape the block path already uses for a block that reached no review (`run.sh:1899`).

`tools/loop/labels.sh` loses its `stale` verdict: no caller writes it any more, and its mapping (a comment-only
hold) is exactly the state this change removes. The verdict goes from the hold list (`labels.sh:88`), from the
usage message that advertised it (`labels.sh:128`) and from the header that explained it (`labels.sh:18-20`); the
refusal is asserted rather than assumed. What the label writer can still write as a hold are the verdicts
that genuinely ask nothing — `unspecifiable`, `too-wide`, `not-spec-shaped`, `needs-target` — and the device and
decision verdicts' rows are untouched.

The contract is narrowed, not routed around: two shipped requirements still said the opposite of the diff —
"A hold is not a decision" listed the worker's skip verdicts among the writers that state no question, and "A
state the run writes is readable from the item" pointed its hold scenario at a `stale` skip — so both are
restated in the delta and merged, together with the falsification requirement's "every other skip", which now
names the four hold verdicts instead of a moving "today".

Why not close it, the way `falsified` closes: a falsification is closed only on a measurement **and** a review of
an unchanged tree (`run.sh:1539`, spec "A skip that falsifies the premise is closed as mis-diagnosed"), and the
worker prompt gives a `stale` verdict neither — the worker stops at the verification, and the run starts no
review for a skip. Closing on it would record a settled state on a claim nobody measured. That is the trade-off
the item leaves open, and the tree settles it.

## Blast radius

- `run.sh`: one condition in the skip path and one fallback question beside the existing one. The hold branch,
  the falsification branch, the block branch and the decision cap are untouched.
- `labels.sh`: one verdict row removed from the hold list and the dispatch, its name out of the usage message and
  the header's explanation; `--list`/`--check` consumers of the vocabulary are the cases and the mutation set, both
  updated here (the matrix case now asserts the refusal).
- Two shipped requirements restated and one scenario re-pointed; no other requirement's text moves.
- `prompts/worker.md`: two sentences — the stale verdict's stop now names what the run does with it, and
  `LOOP-DECISION` is required for it.
- No compiled file, so no build: the verification is the loop's own self-test, its mutation set, the doc-route
  checks and the openspec hygiene checks the gate maps for `tools/loop/**` and `*.md`.

## Rollback

Revert the four files (run.sh, labels.sh, worker.md, loop-cases.sh) and the one mutation record. Nothing
persists: the labels this writes are ordinary decision labels the issue tooling can remove, and no state file,
branch or archive is created by the change itself.

## Alternatives eliminated

- **Close a stale skip as `falsified` does.** Eliminated by the spec's own terms: closure there requires the
  measured number and the review of an unchanged tree; the worker prompt gives a stale verdict neither field and
  no review runs for a skip, so this would close an item on an unmeasured claim — the state
  `run#falsificationWithoutAMeasurementIsEscalated` refuses.
- **Leave `labels.sh stale` as it is and change only `run.sh`.** Eliminated because the label writer would keep
  a reachable verdict that parks an item for good, and two cases (`labels#verdictToLabelMatrix`,
  `labels#holdCarriesItsReason`) would keep asserting that park as correct while the run no longer produces it.
  A rule the test suite still states is not removed by routing around it.
- **Have the worker close the item itself.** Eliminated: an agent process writes nothing on the tracker
  (`guidelines/Backlog.md` §5), and a stale verdict is the worker's judgement about the tree, not a closure.
- **Require the run to measure before escalating.** Out of scope: the item's repair is the missing question, and
  a measurement the worker did not take cannot be manufactured by the run; the person answering the escalation
  can ask for the re-diagnosis.
