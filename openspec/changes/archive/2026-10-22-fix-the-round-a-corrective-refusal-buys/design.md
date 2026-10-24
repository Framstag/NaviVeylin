# Design

## Root cause

The round a corrective refusal buys is built and prompted exactly like the round before it: `run.sh` sets
`diff_base="$base"` once for the whole review loop (`run.sh:926`), so `write_review_diff "$diff_base"` at
`run.sh:931` hands the third round the whole change, and `render_reviewer_prompt` renders the same skeleton
with no trace of the findings the round exists to decide (`run.sh:935`, `prompts/reviewer.md`). A round that
exists only to answer "were these subtractive findings answered?" therefore re-reads the change two rounds have
already graded — the #204 cost, measured there at 435 KB and 9 935 changed lines for item #126.

A second mechanism, found by the round-1 review and fixed in the same helper: `loop_render` (`lib.sh:409`)
substitutes one `KEY=VALUE` pair at a time on the template it has accumulated so far, so a pair inserted **after**
`{{DIFF_PATH}}` cannot fill a `{{DIFF_PATH}}` it introduces itself — the section would have reached the reviewer
with the literal placeholder. The order is part of the fix, not a nicety, and the case now asserts it.

## The single fix

1. `tools/loop/run.sh` — `diff_base` becomes a loop variable, `$base` for every round and the refusing round's
   own HEAD (`git rev-parse HEAD` at the moment the extension is granted, `run.sh:991`) for the round it buys;
   both `write_review_diff` calls (`run.sh:931`, `run.sh:1014`) take it, so the correction round's diff is
   `git diff <refused>..HEAD`. The exclusion pathspec is unchanged: the same call, the same
   `LOOP_REVIEW_EXCLUDE_PATHSPEC` (`D16` — the phase names no project path).
2. `tools/loop/run.sh` — `review_correction_scope` renders the section the correction round's prompt carries:
   the range, the path of the refusing report, and the report itself. It is passed through
   `render_reviewer_prompt` as `{{CORRECTION_SCOPE}}`, at both of that function's call sites (the round and
   its one retry), and it is the **first** pair of that render (`run.sh:686-690`) so the `{{DIFF_PATH}}` and
   `{{CHANGE_ROOT}}` the section names are filled by the pairs that follow it. Substituted last — the shape the
   round-1 review refused — those two survive verbatim and the round that verifies corrections reads a file
   called `{{DIFF_PATH}}`.
3. `tools/loop/prompts/reviewer.md` — the `{{CORRECTION_SCOPE}}` placeholder after the inputs, and the
   paragraph that said a subtractive finding "needs no third review" replaced by what a corrective refusal
   now buys and what that round grades. The old sentence was already false for the budget (#187 grants the
   round) and silent about its input.
4. `guidelines/Backlog.md` §7 — the paragraph that states what the round is handed, and the cost it avoids.
5. `tools/loop-cases.sh` — one case, `run#aCorrectiveRoundIsHandedTheCorrections`: two refusals, the second
   corrective, then a pass; it asserts the round-2 diff carries the change, the round-3 diff carries the
   correction's added line and not the replaced line as a change, the round-3 prompt carries the refusing
   finding and the report's path and says what the diff is, that it names `review-3.diff` and carries no `{{`,
   and that the round-1 prompt carries no such section, names `review-1.diff` and carries no leftover
   placeholder either. The last four assertions are the round-1 review's: they are what would have caught the
   substitution order, and they are the reason the case now has a negative control on both rounds.
6. `tools/loop-mutations.sh` — `40.1` (the correction round's base), `40.2` (the refusing report in the prompt)
   and `40.3` (the section substituted before the paths it names, as a two-expression reorder of the pair), one
   per half of the invariant plus the order.

## Blast radius

`tools/loop/run.sh` (one helper, one render argument, three line changes, one loop variable, four comment
lines), `tools/loop/prompts/reviewer.md` (a placeholder and a paragraph), `guidelines/Backlog.md` (one
paragraph), `tools/loop-cases.sh` (one case, registered and dispatched, four placeholder assertions),
`tools/loop-mutations.sh` (three records), and the archived change. No compiled file, no device: the whole delta
is the shell gate's and its prose.

## Rollback

`git revert` of the change's commits. The helper, the placeholder and the case are additive; the behavioural
edit is `diff_base`, which `run#aCorrectiveRefusalEarnsOneMoreRound` (the cap is still extended) and
`run#reviewDiffExcludesTheEvidenceArchive` (every other round still reads from the base) both exercise, so a
revert that breaks either is caught by the same suite.

## Alternatives the evidence eliminated

- **Closing the item without a reviewer when the correction diff is documentation only** (the item's own fix
  shape, and the decision's option 1). Eliminated by the recorded decision: it lands the corrections unverified
  — the decision's own evidence, `items/126/reviewer-report-3.md:92-99`, is a documentation-only correction
  whose round refused it with a blocking finding on the change's requirement text.
- **Closing instead, after re-running the gate the corrected paths owe** (option 2). Eliminated by the same
  decision for the same reason: no check decides whether a sentence is true, so item 126's defect would land.
- **Lowering `LOOP_MAX_REVIEW_ROUNDS_DEFAULT`, or making the extension a per-run flag** (option 4). Eliminated
  by the decision: it removes the round a corrective refusal needs rather than the re-reading, and the 6 of 32
  twice-reviewed items that reach a third round are exactly those a person's decision bought.
- **Handing the third round the correction diff without the findings.** Eliminated by the round's own
  purpose: a diff of the corrections says nothing about which findings they were meant to answer, so the
  reviewer would re-derive the question the refusing report already wrote — the reconstruction `LOOP-DECISION`
  exists to stop.
- **Narrowing every round's diff to the previous round's HEAD.** Eliminated: rounds 1 and 2 exist to grade the
  change, and a diff of one round's increment is not the change; the scenario
  `run#aCorrectiveRefusalEarnsOneMoreRound` and `run#reviewDiffExcludesTheEvidenceArchive` both assert the
  whole-change reading surface the first rounds keep.
- **Extending `write_review_diff` with a second, correction-specific exclusion pathspec.** Eliminated: the
  exclusion is a recorded project value (`loop.conf`'s `LOOP_REVIEW_EXCLUDE_PATHSPEC`) and the correction
  range is the same change's output, so a second value would be a project fact invented for one round.
