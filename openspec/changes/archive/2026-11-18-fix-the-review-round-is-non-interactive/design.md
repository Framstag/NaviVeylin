# Design — one clause in the prompt both rounds share

## Root cause

`render_reviewer_prompt` (`tools/loop/run.sh:2578`) renders one template for every review round the run starts:
the round after the gate (`tools/loop/run.sh:3801`, and the retry at `:3840`, which re-renders the same file with
the same arguments), and the round before it (`tools/loop/run.sh:3185`, the same template with the pre-gate section
substituted for `{{PRE_GATE}}`). The template's body names what the round judges — the eight items — and the shape
of the answer's tail, and it never says the round is one-way.

A child handed that text can read it as a document: the answer the run measured was a summary of the file with a
question attached (`Pick: (1) run review, (2) summary done, (3) edit prompt. Awaiting answer.`), which carries no
closing `VERDICT:` line. The run's own reader then treats it as an answer that arrived cut — the shape rule issue
#224 added — so the message that should have been an answer is read as a lost tail, and the retry re-renders the
byte-identical prompt, which asks the same question again.

## The single fix

Add the clause to `tools/loop/prompts/reviewer.md`, in the space between "Your whole output is the review below."
and "Check these eight items, in this order…" — the region both renderings share, below the placeholders the
pre-gate round fills (`tools/loop/prompts/reviewer.md:44`) and outside the input section the bound cuts:

1. **The statement.** The round is non-interactive: nobody to ask, nothing to confirm while it runs; never ask a
   question of its own, never offer to proceed or to confirm before starting, never offer a summary in place of the
   round, never end the round with a question instead of the lines the contract below names; carry out the round and
   end with those lines. The clause is scoped to the round's **own** answer, so the `LOOP-DECISION` line a refusal
   owes stays part of the contract: a reading of "end with no question" that dropped it would escalate the run's own
   two-way choice, the defect issue #894 measured.
2. **The cost, in the run's vocabulary.** An answer that is a question is a *cut-short answer*: the run reads no
   verdict from it, spends its one retry on a byte-identical prompt that asks the same question again, and then
   refuses the item (issue #954). This is the word the retry rule already uses, so the prompt names the outcome
   the run will reach rather than a new threat.

Two paragraphs, and the sentence carrying each asserted phrase stays on one line: `prompt#reviewerCarriesTheEightItems`
matches the rendered prompt with `assert_contains` over `cat` — it does not flatten the text the way its sibling
does — so a phrase broken across a line would be a phrase the case cannot find.

No engine file changes. The run's treatment of an answer without the closing `VERDICT:` line is the behaviour issue
#224 specifies and `run#reviewRoundsCapAtTwo` asserts; this change is what the prompt states, so the two cannot
disagree about the outcome.

## Blast radius

- One prompt file, and nothing reads it programmatically: `gate.sh`, `state.sh` and `report.sh` never open it, and
  `run.sh` only substitutes its placeholders. The clause is text handed to a child.
- Both rounds inherit it by construction — the pre-gate round is the same template — which is the point of placing
  it above `{{PRE_GATE}}` rather than inside `pre-gate-review.md`.
- The placeholders are untouched: `{{REVIEW_INPUTS}}` stays on the bullet's own line (`tools/loop/prompts/reviewer.md:31`), which
  `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` reads with `grep -qF 'not a missing artefact.{{REVIEW_INPUTS}}'`,
  and `{{CORRECTION_SCOPE}}`, `{{PRE_GATE}}`, `{{REVIEW_RESTART}}` and `{{REVIEW_WALL_CLOCK}}` keep their lines.
- The prompt grows by 9 lines / 641 bytes. That is not the bounded quantity: `LOOP_REVIEW_INPUT_MAX_BYTES=32000`
  (`tools/loop/loop.conf:927`) caps what the run hands the round as its *inputs*, not the length of the template,
  and no case asserts a size for `reviewer.md`.
- Rollback: delete the two paragraphs. The three assertions this change adds to the two cases then fail, which is
  the change's own red case.

## The alternatives the evidence eliminated

- **The clause in `pre-gate-review.md` alone.** Eliminated by the run's own measurement: the round that asked twice
  per item is the pre-gate round, and the round after the gate asked the same way, so a section the post-gate round
  never sees leaves half the item.
- **A `MODIFIED` requirement widening the retry rule so a question is read as a cut.** Eliminated by what the run
  can check without a model: it already reads the missing `VERDICT:` line, and detecting that an answer is a
  question is a second reading of free text. The prompt is what was missing.
- **Making the retry prompt differ from the one it retries.** Eliminated by the retry's purpose: the retry exists
  because the *answer* was lost, so it must ask the same round; a different retry prompt is a second contract to
  keep in step with the first. It also would not help — the run cannot tell a lost answer from a question before it
  re-asks, which is exactly the incident.
- **A model-detecting arm that answers the question.** Eliminated by what the run is: a script with no person in
  the loop, so there is no answer to give, and a reader that classifies free text is a new failure mode beside the
  one it fixes.

## Open decisions

none
