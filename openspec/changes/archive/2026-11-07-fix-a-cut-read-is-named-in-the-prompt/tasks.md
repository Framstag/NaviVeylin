# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case

Requirement: "A prompt whose child reads a long text names the cap that cuts it", scenario "Every phase prompt names
a cut read and how to page it".

- [x] 1.1 Extend `prompt#thePhasePromptsCarryTheirOwnRoute` with five assertions inside the loop it already runs over
      the four skeletons: the prompt names `LOOP-TRIMMED`, names `LOOP_TOOL_RESULT_MAX_CHARS`, names
      `ctx_read mode="lines:N-M"`, names `offset` and names `arrived whole`. The case's `# files:` declaration gains
      `tools/pi-trim-result.mjs` — the file whose notice the prompts quote, so a change to the marker is a change the
      case can be reached by. Verify: on the base `a3cefa93` the case fails at its first assertion with
      `worker.md names the marker a cut result carries (issue #818): 'LOOP-TRIMMED' not found in: …`, `1 of 1 cases
      failed`, 2026-11-07T12:32:32Z–12:32:34Z (`evidence/red-on-HEAD.log`, tally `evidence/red-on-HEAD.xml`). That run
      was taken with the marker assertion in place and the log prints the skeleton it read, so it shows the other four
      clauses absent from the base text as well.

## 2. The prompts

- [x] 2.1 `tools/loop/prompts/reviewer.md`: one paragraph beside the input list naming the bound, the marker, the
      paging call and its arguments, the "a window that arrived whole" rule, and what the round owes (`UNPROVEN`,
      never `PASS`).
- [x] 2.2 `tools/loop/prompts/worker.md`: the same paragraph in "One call per measurement, and one turn for the
      independent ones", beside the rule that a file read once is cited rather than read again.
- [x] 2.3 `tools/loop/prompts/worker-gate.md` and `tools/loop/prompts/worker-tail.md`: the same paragraph where each
      child is told where the project's rules are, so all four phase prompts carry it. Verify for 2.1–2.3:
      `bash tools/loop-selftest.sh --case prompt#thePhasePromptsCarryTheirOwnRoute` passes (4.1).

## 3. The rule's record

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7a: the sentence that every phase prompt names the cut and how to page it,
      with the case that holds it, beside the result-bound paragraph it belongs to.
- [x] 3.2 `tools/loop-mutations.sh`: one record, `258.1`, mutating the marker sentence in `reviewer.md` so the case
      goes red. Verify: 4.2.

## 4. Verification

- [x] 4.0 The first check the delta owes, run before the rest of them: `bash tools/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — `PASS`, 2026-11-07T12:36:16Z–12:36:26Z
      (`evidence/first-check.log`).
- [x] 4.1 The red case passes on the change — the same `--case` selection, `PASS`, 2026-11-07T12:35:59Z–12:36:00Z
      (`evidence/green-on-change.log`), against the red run in `evidence/red-on-HEAD.log`; reverting the four prompt
      paragraphs is the red run itself.
- [x] 4.2 The revert-check: `bash tools/loop-mutations.sh --only 258.1` — `RED 258.1`, the case failing for the named
      reason (`reviewer.md names the marker a cut result carries (issue #818): 'LOOP-TRIMMED' not found`), and
      `restore 258.1 … green again` — `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-258.1.log`).
- [x] 4.3 Every record the change's paths select still applies: `bash tools/loop-mutations.sh --check --from-git
      a3cefa93` — `selected 9 of 271 records`, `all 9 cases passed — every selected record applies to the file it
      names` (`evidence/mutation-check.log`), which is what holds the two records that already quote `worker.md`
      (`66.2`, `87.3`) against the paragraph this change added beside them.
- [x] 4.4 The structural checks of the two shell files the diff touches — `bash -n tools/loop-cases.sh` and
      `bash -n tools/loop-mutations.sh`, both exit 0 — and `bash tools/check-change-readiness.sh
      openspec/changes/archive/2026-11-07-fix-a-cut-read-is-named-in-the-prompt` reports `LOOP-CHANGE-READY: ok`
      (`evidence/readiness.log`). `bash tools/check-doc-routes.sh` reports `all 329 section references resolve, all 7
      skill references resolve` (`evidence/doc-routes.log`), which is the check a section 7a edit owes.
      The gate of this change is the loop's whole self-test, run once by the child that follows the diff review.
