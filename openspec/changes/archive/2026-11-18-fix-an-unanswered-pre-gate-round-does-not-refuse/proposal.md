# The round before the gate cannot refuse the item, so a round that never answered does not

## Why

`tools/loop/prompts/pre-gate-review.md` says, in its own words, that the round before the gate is advisory: a
`BLOCKING:` finding there "is a correction, not a refusal" and "this round does not block the item". The code did
not keep that promise. A pre-gate answer that arrived cut twice — no closing `VERDICT:` line, twice — set
`pre_gate_refusal` (`tools/loop/run.sh`, the arm issue #758 landed) and the item ended `blocked` with the reason
`the pre-gate reviewer's report was cut short twice`, the gate child was never started, and the branch was handed
to a person.

**The measurement:** the run of `2026-11-17T10:58Z` → `16:25Z` (14 items, 326 min, 120.3 Mtok). Two items paid it
whole. #916: 6 m 18 s, 6.0 Mtok, the red case green on the branch, and the reviewer's two answers were questions
(`Want me to execute review against diff?`, then `Questions shown above — answer when ready.`); no gate result and
2 commits left for a person. #917: 11 m 3 s, 14.4 Mtok, the same shape, 1 commit left. **20.4 Mtok — 17.0 % of the
run — three commits and two branches** whose gate had never been asked.

**Root cause, in one sentence:** a round that answered nothing is not a round that refused, and the pre-gate
round's only power is to hand findings to the child that runs the gate — so the second transport cut must be a
recorded non-answer, not a refusal that ends the item before its gate exists.

Sibling issue #954 (archived as `2026-11-18-fix-the-review-round-is-non-interactive`) removed one source of such
answers by stating in `tools/loop/prompts/reviewer.md` that the round is non-interactive. This change removes what
the run did with one when it arrived anyway: any item whose child answers with a question pays the worker's spend
and then blocks, and the queue fills with half-finished branches.

Owning capability: `agent-issue-loop`.

surface: 5 files under `tools/loop/**` (+93/−32 lines, `git diff --numstat`), 1 spec delta with 5 requirements and
29 scenarios (4 scenario bodies re-cut, 2 requirement bodies re-cut, 1 scenario added) — measured with
`grep -c '^#### Scenario:' openspec/changes/fix-an-unanswered-pre-gate-round-does-not-refuse/specs/agent-issue-loop/spec.md`
→ **29**, and `grep -c '^### Requirement:' …` → **5**; 1 case re-cut and 1 mutation record added —
`bash tools/loop/tests/loop-selftest.sh --list | wc -l` → **486** declared cases and
`bash tools/loop/tests/loop-mutations.sh --list --files tools/loop/run.sh | wc -l` → **150** records that name
`run.sh`.

## What Changes

1. **A pre-gate round whose answer never arrived is recorded `unanswered`** — a fourth value beside `PASS`, `FAIL`
   and `NOTES` in the round's own field (`metrics.reviewVerdict`, and `state.sh done --review-verdict` accepts it),
   counted like the round it was (`metrics.reviewRounds: 1`) and named on the item's `pre-gate-review` step. The
   second cut no longer writes a refusal: `pre_gate_refusal` stays empty for that shape, so the arms below it do
   what they do for a round that answered.
2. **The gate child runs.** An unread diff was never the reason to skip the gate — the gate is the item's one
   verdict, and it is what the run was going to ask for anyway. It is started with the findings placeholder saying
   the round handed nothing forward, rather than the false `the round read before the gate found no blocking
   finding`.
3. **What still ends the item before its gate is unchanged:** a round the run's own bound or deadline ended is
   still `cut` (issue #790), a reviewer that wrote in the tree still discards its round, and a `VERDICT: FAIL` with
   a blocking count still hands its findings to the gate child instead of refusing anything. A round **after** the
   gate that is cut twice still blocks, because there the round is the item's last judgement (issue #224).
4. **The reviewer prompt's cost sentence names the new outcome** (`tools/loop/prompts/reviewer.md`): a question is a
   cut-short answer, the retry asks it again, and the round is recorded as unanswered — which is true of both
   rounds, where "the item is then refused" was not (`issue #954`).
5. **Four shipped requirements are re-cut and one is added to, in the delta:** `The diff is read before the gate…`
   (the transport cut leaves the bound/deadline arm and gets a scenario of its own), `An item the run's own bound
   cut is recorded cut…` (one sentence), `A review finding is blocking or it is not…` (its cut-twice scenario scoped
   to the round after the gate), `A review round a bound ended is restarted…` (same scoping), and `A review round
   is non-interactive…` (the cost sentence).

## Impact

- Production: `tools/loop/run.sh` — the cut-twice arm, the verdict the record carries, the step's note and the gate
  child's findings placeholder; `tools/loop/state.sh` — one value in the `--review-verdict` vocabulary and its
  usage line; `tools/loop/prompts/reviewer.md` — the cost sentence.
- Cases: `tools/loop/tests/loop-cases.sh` — `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun` re-cut as
  `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides` (a non-answer no longer leaves the change; the gate
  child decides it), with its declaration and dispatch; `tools/loop/tests/loop-mutations.sh` — record `953.1`, and
  `79.4` re-pointed at `run#aPreGateReviewEndedAtItsBoundNamesIt`, the case that still takes the outcome arm a
  bound-ended round records.
- Documents: this change's delta — 5 requirements, 29 scenarios; `guidelines/Backlog.recipes.md` — the sentence
  that says two cut answers in one round block the item is scoped to the round after the gate.
- No compiled file, no configuration key, no new dependency: the run's behaviour for a bound-ended round, a
  writing reviewer and a round after the gate is unchanged, and each is held by a case that is not re-cut here.
