# Tasks

The change's own line numbers are the tree's as this change leaves it; the base is `6b0e01a7`. Its
`tools/loop/prompts/reviewer.md`, `tools/loop/tests/loop-cases.sh` and `tools/loop/tests/loop-mutations.sh` are the
tree this change edits. The red, green and mutation runs are kept in the run's evidence directory,
`.pi/issue-loop/items/954/`, beside the change root, and copied into the change's own `evidence/`.

## 1. The red case

- [x] 1.1 `prompt#reviewerCarriesTheEightItems` asserts the clause on the rendered reviewer prompt — `non-interactive`,
      `never ask a question`, and `an answer that is a question is a cut-short answer` — and
      `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` asserts the first two on the rendered pre-gate
      prompt. Verify: against HEAD's `reviewer.md`,
      `LOOP_ENGINE=<copy whose prompts/reviewer.md is git show HEAD:tools/loop/prompts/reviewer.md> bash
      tools/loop/tests/loop-selftest.sh --case …` →
      `FAIL prompt#reviewerCarriesTheEightItems … the reviewer is told the round is non-interactive: 'non-interactive'
      not found in: # Reviewer — the fixed contract` and
      `FAIL prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle … and that this round is non-interactive like the
      round that follows the gate (issue #954): 'non-interactive' not found in: …` — both `exit=1`
      (`.pi/issue-loop/items/954/red-on-HEAD.log`, 2026-11-17T17:32Z).
      The red run drives an engine copy and not the shared tree: a suite started for one named case takes no
      repository lock (`tools/loop/tests/loop-selftest.sh:404`), so the run is isolated and the shared
      `prompts/reviewer.md` is never mutated — `grep -c 'never ask a question' tools/loop/prompts/reviewer.md` → `1`
      after both runs.
- [x] 1.2 Both cases declare `tools/loop/prompts/reviewer.md` in their first-line `# files:` line, which neither did:
      both assert text that comes from that file, so a delta to the prompt has to select them. Verify:
      `bash tools/loop/tests/loop-selftest.sh --list --files tools/loop/prompts/reviewer.md` lists both, and
      `driver#everyCaseDeclaresTheFilesItCanReach` passes.

## 2. The prompt — `tools/loop/prompts/reviewer.md`

- [x] 2.1 The clause is inserted between "Your whole output is the review below." and "Check these eight items, in this
      order…" — below the placeholders the pre-gate round fills (`tools/loop/prompts/reviewer.md:44`) and in the body
      both renderings share, so the pre-gate round and the round after the gate both carry it. Verify:
      `prompt#reviewerCarriesTheEightItems` and `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` both pass
      (`.pi/issue-loop/items/954/green-on-fix.log`, 2026-11-17T17:32:28Z, `exit=0`, 10 s and 13 s).
- [x] 2.2 Each asserted phrase stays on one line: `prompt#reviewerCarriesTheEightItems` matches with `assert_contains`
      over `cat`, without the flattening its sibling does, so a phrase broken across a line would be a phrase the case
      cannot find — the failure the first wording of the clause produced. Verify: the case is green (2.1).
- [x] 2.3 The placeholders are untouched. Verify: `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` reads
      `grep -qF 'not a missing artefact.{{REVIEW_INPUTS}}'` against the template
      (`tools/loop/prompts/reviewer.md:31`) and the clause does not change that line; no case asserts a size for the
      template, and the bound `LOOP_REVIEW_INPUT_MAX_BYTES=32000` (`tools/loop/loop.conf:927`) caps what the run hands
      a round, not the prompt.

## 3. The cases — `tools/loop/tests/loop-cases.sh`

- [x] 3.1 `case_prompt_reviewerCarriesTheEightItems` gains the three assertions and the comment that carries the
      incident (the four rounds of the run of `2026-11-17T10:58Z`, items #916 and #917). Verify: green (2.1) and red
      on HEAD (1.1).
- [x] 3.2 `case_prompt_thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` gains two assertions — `non-interactive` and
      `never ask a question` — on the rendered pre-gate prompt. Verify: green (2.1) and red on HEAD (1.1).

## 4. The mutation — `tools/loop/tests/loop-mutations.sh`

- [x] 4.1 Record `954.1` mutates the clause (`s|never ask a question|feel free to ask a question|`) in
      `tools/loop/prompts/reviewer.md` and names `prompt#reviewerCarriesTheEightItems`. Verify:
      `bash tools/loop/tests/loop-mutations.sh --engine <copy of tools/loop> --only 954.1` →
      `RED 954.1 prompt#reviewerCarriesTheEightItems` … `restore 954.1 prompt#reviewerCarriesTheEightItems green again`
      and `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (2026-11-17T17:33:50Z, 28 s,
      `.pi/issue-loop/items/954/mutation-954.1.log`). The run drives an engine copy through `--engine`, so the shared
      tree is not mutated.
- [x] 4.2 The record still applies: `bash tools/loop/tests/loop-mutations.sh --check --only 954.1` → `all 1 cases
      passed — every selected record applies to the file it names` (`exit=0`).

## 5. The documents

- [x] 5.1 `openspec/changes/fix-the-review-round-is-non-interactive/specs/agent-issue-loop/spec.md` adds one
      requirement with two scenarios, its body under the validator's 500-character bound (493). Verify:
      `openspec validate fix-the-review-round-is-non-interactive --strict` parses `deltaCount: 1`, `operation: ADDED`
      (`openspec show … --deltas-only`); its exit 1 is the `.openspec.yaml` `issue` field's warning, which every
      change created for an issue since 2026-11-17 carries. `bash tools/check-openspec-hygiene.sh` → exit 0 and
      `bash tools/check-change-readiness.sh openspec/changes/fix-the-review-round-is-non-interactive` →
      `LOOP-CHANGE-READY: ok`.
- [x] 5.2 No guideline paragraph changes: the rule this change edits is the reviewer prompt's own text, and
      `guidelines/Backlog.recipes.md`'s review-phase paragraphs describe the round's price and its bounds, not the
      contract's wording. The change's reading set is derived when the review reads the diff.
- [x] 5.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-review-round-is-non-interactive` → `PASS`
      (`.pi/issue-loop/items/954/evidence-check.txt`).

## 6. The landing

- [x] 6.1 The clause landed as `41dc62e3 fix: the-review-round-is-non-interactive (#954)` — `tools/loop/prompts/reviewer.md` alone,
      `9 insertions(+)`, nothing else staged. Verify: `git show --stat 41dc62e3` → one file changed;
      `git show 41dc62e3:tools/loop/tests/loop-cases.sh | grep -c non-interactive` → `4` (the assertions that predate it).
- [x] 6.2 The case assertions and the `954.1` record landed **inside `b08c26b7 fix: a block carries the question the run
      holds (#960)`**, which committed the two shared test files whole — so those two halves of this change are recorded
      under an issue number that is not this one, and `41dc62e3` is the clause they assert. Verify:
      `git log --oneline 6b0e01a7..41dc62e3 --stat` shows `b08c26b7` carrying `tools/loop/tests/loop-cases.sh` and
      `tools/loop/tests/loop-mutations.sh` and `41dc62e3` carrying only the prompt. The window between the two commits
      left the base carrying the assertions and not the clause; it closed with the second commit, and no case ran against
      the split state except the two `--case` runs of 6.3.

## 7. Verification

- [x] 7.1 The cases this change extends are green on the change: `prompt#reviewerCarriesTheEightItems`,
      `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` and `driver#everyCaseDeclaresTheFilesItCanReach`
      (`exit=0`, 10 s / 13 s / 18 s, 2026-11-17T17:32:28Z, `green-on-fix.log`).
- [x] 7.2 The same three are green at the landed commit `41dc62e3` (`exit=0`, 20 s / 19 s / 30 s,
      2026-11-17T18:02Z, `green-on-HEAD-41dc62e3.log`) — single named cases, which take no repository lock
      (`tools/loop/tests/loop-selftest.sh:404`).
- [x] 7.3 The suite the delta itself owns — `bash tools/loop/tests/loop-selftest.sh --files
      tools/loop/prompts/reviewer.md` — was **refused**, not failed: the sibling change's gate
      (`--from-git 6b0e01a7`, owner 1678210, from 2026-11-17T17:53:46Z) held the repository lock for the whole window, so
      the run of 7.1 and 7.2 and the mutation check of 4.2 are this change's evidence
      (`gate-files-refused.log`). One suite per repository is the rule the refusal carries; the run's own gate owns the
      run of that selection, and it covers this file because the base the sibling gate measured (`6b0e01a7`) predates
      `41dc62e3`.
- [x] 7.4 The red case is red on the base's prompt and only for the clause's absence (1.1).
- [x] 7.5 The falsification is recorded and restores clean (4.1, 4.2).
- [x] 7.6 The structural checks of a delta that compiles nothing: `bash -n tools/loop/tests/loop-cases.sh`,
      `bash -n tools/loop/tests/loop-mutations.sh`, `bash tools/check-openspec-hygiene.sh`,
      `bash tools/check-change-readiness.sh <change>` and `bash tools/loop/evidence-check.sh <change>`. No build and no
      suite beyond the named cases is run for a delta of one prompt file, its cases and a spec delta.

The change's one gate is not this session's: the run defers the diff review and then the item's single gate to the
children that follow, and the gate's suite runs the cases this delta owns. The gate owed for a `tools/loop/**` delta is
`bash tools/loop/tests/loop-selftest.sh --from-git <base>` plus
`bash tools/loop/tests/loop-mutations.sh --check --from-git <base>`, per the file-filtered selection.

## 8. The review rounds

The clause landed in 6.1 is a defect-shaped improvement, so it owes a reviewer before archive; the loop ran two rounds,
both driven by `tools/loop/run.sh`'s own renderer (`render_reviewer_prompt`, `tools/loop/run.sh:2578`) and read by no
person before their verdict lines, and each is a **reading** round — neither ran the gate or the suite.

- [x] 8.1 Round 1 (the round after the gate), at `41dc62e3`: report `.pi/issue-loop/items/954/reviewer-report-1.md` (18 261 B,
      2026-11-17T18:31Z), child `ollama-cloud/minimax-m3.1-flash`, ~970 s — `LOOP-BLOCKING: 1`, `VERDICT: FAIL`,
      `LOOP-CORRECTIONS-ONLY: yes`. The blocking finding: the clause as landed (`tools/loop/prompts/reviewer.md:62-63`)
      forbids the `LOOP-DECISION` line the same file's contract (`:165`, `:172`) and the shipped capability
      (`openspec/specs/agent-issue-loop/spec.md:566`, `:5672`) require of a refusal — the defect of issue #894, reachable
      through this file. Its one prescribed fix: scope the clause to the round's **own** answer and name the contract's
      exception.
- [x] 8.2 The correction (2026-11-17T20:42Z): the clause scoped to the round's own answer with the `LOOP-DECISION`
      carve-out named, `proposal.md`, `design.md` and the spec delta scoped to match, the requirement body trimmed inside
      the validator's 500-character bound. `git diff --stat 41dc62e3 HEAD -- tools/loop/prompts/reviewer.md` →
      `6 insertions(+), 4 deletions(-)`; the three asserted phrases stay
      (`grep -c 'never ask a question'` → `1`, `grep -c 'an answer that is a question is a cut-short answer'` → `1`).
- [x] 8.3 Round 2 (the corrective round a `LOOP-CORRECTIONS-ONLY: yes` refusal earns,
      `tools/loop/run.sh:3965-3970`, scope text `review_correction_scope()` at `:1662`): report
      `.pi/issue-loop/items/954/reviewer-report-2.md` (18 247 B, written 2026-11-18T06:25+0100), ~77 min —
      `LOOP-BLOCKING: 2`,
      `VERDICT: FAIL`, `LOOP-CORRECTIONS-ONLY: yes`. Both findings are about the landing rather than the wording: the
      correction was swept into another issue's commit (`a08cb2a3`, #958/#962) and that change's own next commit
      `a412345e` **reverted it deliberately** — "tools/loop/prompts/reviewer.md is restored: the hunk came from #954's
      owner and no task here names it" — so the tree's prompt is again `41dc62e3`'s blob (`2eb97a82…`), the clause at
      `:60-63` forbids the `LOOP-DECISION` line again, and the change's own documents state a wording the tree does not
      implement. Its prescribed fix is section 9.

## 9. The re-landing and the archive

- [x] 9.1 The narrowed clause (the wording of 8.2) landed as `c2d326ef fix: the-review-round-is-non-interactive (#954) —
      the clause scoped to the round's own answer`, on `main` at `33fb1714` (the sibling change's archive), one file:
      `git diff --stat 33fb1714 c2d326ef -- tools/loop/prompts/reviewer.md` → `6 insertions(+), 4 deletions(-)`, and
      `git hash-object tools/loop/prompts/reviewer.md` → `34c2fe4f…`, the blob 8.3 names. Verify:
      `git show c2d326ef:tools/loop/prompts/reviewer.md` carries `never ask a question of your own` and the carve-out,
      `the \`LOOP-DECISION\` line a refusal owes is part of that contract`, and `grep -c 'never ask a question'` → `1`.
- [x] 9.2 Green on the re-landed tree: the three cases
      (`prompt#reviewerCarriesTheEightItems`, `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle`,
      `driver#everyCaseDeclaresTheFilesItCanReach`) `exit=0` at 2026-11-18T05:57:21Z (`green-on-relanded.log`);
      `bash tools/loop/tests/loop-mutations.sh --check --only 954.1` → `all 1 cases passed` `exit=0`
      (`mutation-check-relanded.log`); the gate of the selection this delta owns —
      `bash tools/loop/tests/loop-selftest.sh --files tools/loop/prompts/reviewer.md` →
      `all 67 cases passed … 122.66 s, selection: --files tools/loop/prompts/reviewer.md` `exit=0`
      (`gate-files-relanded.txt`, 2026-11-18T05:58:10Z) and
      `bash tools/loop/tests/loop-mutations.sh --from-git 33fb1714` →
      `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0` `exit=0` (`mutations-relanded.txt`,
      2026-11-18T06:00:16Z).
- [x] 9.3 The change's documents and the tree agree again: the tree's clause and `proposal.md`, `design.md` and the
      spec delta all state the narrowing 8.2 wrote (review-2's finding 2). Verify: the three named in 9.1 sit in the
      same clause as the wording the spec delta's requirement quotes. One gap is recorded rather than closed: no
      assertion quotes the `LOOP-DECISION` carve-out, so a later delta that dropped it would be caught by no case —
      review-2's own prescription (8.3) asks for no new claim, case or assertion, and this change adds none.
- [x] 9.4 `openspec archive fix-the-review-round-is-non-interactive --yes` wrote the delta into
      `openspec/specs/agent-issue-loop/spec.md` — `agent-issue-loop: update`, `+ 1 added`, at `:5720`, the two lines a
      review round cites (`:566`, `:5672`) lying before it and unchanged — and moved the change directory to
      `openspec/changes/archive/2026-11-18-fix-the-review-round-is-non-interactive/`, which is gitignored
      (`.gitignore:82`) and so is staged with `git add -f`. Its `evidence/` carries the run's logs, the two graded
      diffs, and `review-rounds.txt` (the two rounds' verdict lines); the full reports stay in the run's own item
      directory, which 9.1 and section 8 name, because a report quotes `§N` and gate tallies and
      `evidence-check.sh` reads those as claims of this change (`evidence/` held no `§` and no quoted tally:
      `grep -c '§'` → `0`).
- [x] 9.5 `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-11-18-fix-the-review-round-is-non-interactive`
      → `PASS` (`ok` on C1–C5, 364 `file:line` references resolved), and `bash tools/check-change-readiness.sh` is run
      no more: the change is archived. `bash tools/check-openspec-hygiene.sh` → `exit=0`.
