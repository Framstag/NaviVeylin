# Tasks

Reference: `proposal.md` for the red run and the surface, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The reason quotes the line that ended the round

- [x] 1.1 `tools/loop/run.sh`, arm after the gate: read `LOOP-TIMEOUT` from the reviewer's report into
      `reviewer_timeout_line` and quote it in `blocked_reason` in place of the parenthesised pair of bounds.
      Verify: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` green on the change, and the item's note carries
      `turn bound of 3 turns` and `LOOP_CHILD_TURNS_MAX_REVIEWER`.
- [x] 1.2 `tools/loop/run.sh`, arm before the gate: the same read into `pre_gate_timeout_line` and the same quoted
      sentence in `pre_gate_refusal`. Verify: `run#aPreGateReviewEndedAtItsBoundNamesIt` green on the change.

## 2. The cases that pin the sentence

- [x] 2.1 Strengthen `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`: its note assertion moves from `turn bound` to
      `turn bound of 3 turns` plus `LOOP_CHILD_TURNS_MAX_REVIEWER`, so the clause "names the bound and its figure"
      is asserted and not just the word. Verify: red on the base `d5d2e668` (2026-11-10T15:32:07Z–15:32:21Z,
      `evidence/red-on-HEAD.xml`, `tests=2 failures=2`), green on the change (2026-11-10T15:32:27Z–15:32:41Z,
      `evidence/green-on-change.log`).
- [x] 2.2 Add `run#aPreGateReviewEndedAtItsBoundNamesIt`, registered in the list and the dispatcher: the round
      before the gate answers a `LOOP-TIMEOUT` line and no verdict, and the case asserts `result=cut`,
      `reviewVerdict=cut-short`, the note's bound, figure and key, no retry, no gate child and no round after the
      gate. Verify: red on `d5d2e668` with the ambiguous sentence quoted in the failure (2026-11-10T15:32:07Z,
      `evidence/red-on-HEAD.xml`), green on the change (`PASS`, 2026-11-10T15:32:27Z).

## 3. The rule the arms follow

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7b: state that the reason a bound-ended round leaves quotes the child's
      own line, so it names the bound and the figure, with #753/#819 as the measurement. Verify:
      `tools/check-doc-routes.sh` green (structural check of the file this delta edits; no compiled file changes).

## 4. Structural checks the delta owes

- [x] 4.1 The structural checks this delta owes for the files it edits, plus the plan's first line:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'`,
      `tools/check-doc-routes.sh` (the guideline) and `tools/check-openspec-hygiene.sh` (the change's own
      artifacts). Verify: each green, `evidence/structural.log` (2026-11-10T15:34:23Z–15:34:37Z) — `PASS`,
      `all 330 section references resolve, all 7 skill references resolve`, `OpenSpec artifacts are consistent:
      17 open change(s)`, and `openspec validate --strict fix-a-cut-round-names-its-bound` → `Change … is valid`.
      The rest of the block's selection (`loop-selftest --from-git`, `loop-mutations --check`, the portability and
      issue-tooling pairs) is the gate child's and was not run here.
- [x] 4.2 `./tools/loop/measure.sh --change fix-a-cut-round-names-its-bound`, quoted from the block it printed
      after 5.3: `LOOP-MEASURE-SUITE cases=398`, `LOOP-MEASURE-CHANGE … scenarios=8 cases=8
      unnamed=0`, and `LOOP-MEASURE-SURFACE surface: 2 arms rewritten, 1 requirement modified (5 scenarios restored
      beside 2 new ones), 2 cases (1 strengthened, 1 added), 1 guideline section, 1 mutation record added and 1
      re-pointed …` — that last line is the `surface:` line `proposal.md` carries, so nothing is recounted by hand.

## 5. The three repairs the item's recorded decision owed

- [x] 5.1 The mutation record `73.1` quoted the presence test this change renamed; re-point it at
      `if [ -n "$reviewer_timeout_line" ]; then` so it still names a line that exists. Verify:
      `bash tools/loop/tests/loop-mutations.sh --check --only 73.1` green — `all 1 cases passed — every selected
      record applies to the file it names` (2026-11-10T15:34:09Z).
- [x] 5.2 One record for the new invariant: `73.2` replaces the quoted line in the pre-gate arm's reading with the
      ambiguous phrase, so the reason offers both bounds again, and `run#aPreGateReviewEndedAtItsBoundNamesIt` must
      notice. Verify: `bash tools/loop/tests/loop-mutations.sh --only 73.2` → `RED 73.2` on the note assertion
      (`'turn bound of 3 turns' not found in: … — one of its spend bounds —`), then `restore 73.2 … green again`
      (2026-11-10T15:34:15Z).
- [x] 5.3 The delta's `## MODIFIED Requirements` block held 3 of the 6 scenarios the shipped requirement carries,
      which would have dropped five of them at archive. Restore the omitted five verbatim from
      `openspec/specs/agent-issue-loop/spec.md:3431-3480`, in their shipped order, beside the two new scenarios.
      Verify: `openspec validate --strict fix-a-cut-round-names-its-bound` → `Change … is valid`, and
      `LOOP-MEASURE-CHANGE … scenarios=8 cases=8 unnamed=0`.
