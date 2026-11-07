# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

- [x] 1.1 Add `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable`: an excluded branch that carries one archived
      change of its own and a green verification record, in a project tree that carries an uncommitted file, is
      adopted and refused; the case asserts `unmergeable` as the item's mechanism, the refusal's text beside it, the
      branch as the evidence, no `.items` record, no `agent/needs-decision` write, no close, and a report whose
      decision list reads `none` with the mechanism on the excluded line. Verify: red on `ed509ee4` —
      `expected 'unmergeable', got 'null'` (2026-11-06T11:48:55Z, `evidence/red-on-HEAD.log`).
- [x] 1.2 The case's first-line `# files:` declaration names every path it reads: `tools/loop/run.sh`,
      `tools/loop/state.sh`, `tools/loop/report.sh`, the fixture scenario and the child-event adapter. Verify:
      `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` green.
- [x] 1.3 Add `state#anAdoptionRefusalIsRecordedBesideTheExclusion`: the writer keeps the pre-screen's verdict, reason
      and branch, adds `unmergeable` and the refusal's text, prints `excluded: #21 needs-decision (unmergeable)`,
      keeps the mechanism when a second pre-screen rewrites the exclusion, and refuses an issue that is not one of
      the run's exclusions or one that already carries its `.items` record — leaving the state byte-identical.
      Verify: green on the change; the command does not exist on the base, so the case cannot pass there.

## 2. The adoption's own result

- [x] 2.1 `state.sh`: add `adoption-refused <issue> --reason TEXT`, which writes `mechanism: "unmergeable"` and
      `adoptionRefusal` onto that item's entry in `.excluded`, keeps the entry's verdict, reason and evidence, and
      refuses an issue that is not one of the run's exclusions or one that already has its `.items` record
      (a mechanism belongs to one record). Verify: `state#anAdoptionRefusalIsRecordedBesideTheExclusion` green —
      the record, the report line and both refusals.
- [x] 2.2 `run.sh`: `adopt_finished_attempt` keeps `adopt-item`'s output in `adoption_refusal`;
      `adopt_finished_exclusions` reads the status, asks `worktree.sh branch-state`, and on `adoptable` records the
      refusal through `state.sh`. Verify: the new case passes.
- [x] 2.3 `run.sh`'s `escalate_prescreen_decisions`, `report.sh`'s decision count and decision list, and the
      excluded line of both `state.sh report` and `report.sh` skip or print the mechanism; `state.sh queue`
      carries the mechanism over when a second pre-screen rewrites the exclusions mid-run. Verify: the new case's
      `decisions the owner owes: none` and `excluded before any work: #21 needs-decision (unmergeable)`, and
      `state#anAdoptionRefusalIsRecordedBesideTheExclusion`'s carry-over assertion.

## 3. The boundary the fix must not cross

- [x] 3.1 `run#doesNotMergeAnAttemptWithoutAGreenVerification`: a branch whose change carries no green verification
      keeps its `needs-decision` exclusion and its escalation. Verify: green, unchanged.
- [x] 3.2 The pre-screen's own reading is untouched: `prescreen#aFinishedAttemptBranchIsADecision`,
      `prescreen#escalatedItemKeepsItsBranchAsEvidence` and `run#awaitingDecisionIsNotEscalatedAgain` stay green,
      because the pre-screen still writes the verdict and the run still escalates every exclusion it did not decide
      for itself. Verify: green, unchanged.
- [x] 3.3 The success path is untouched: `run#mergesAFinishedAttemptAndClosesTheItem` and
      `run#doesNotCloseOnAMergeTheBaseCannotTake` stay green (the second is the run's own merge, a block that keeps
      its escalation). Verify: green, unchanged.

## 4. The documents and the mutation

- [x] 4.1 `guidelines/Backlog.recipes.md`:586 "Refusals, records and the landing" states what the run records when an admissible branch cannot be
      merged, and the excluded line of the run's report carries the mechanism. Verify: `tools/check-doc-routes.sh`
      green (no route or price changes).
- [x] 4.2 `tools/loop-mutations.sh` record 86.1 drops the record the exclusion loop writes and names the new case.
      Verify: `bash tools/loop-mutations.sh --check --files tools/loop/run.sh` green, and the record run red/green
      with the case.

## 5. Review round 1's blocking finding — the delta's kind

- [x] 5.1 The delta carries `## MODIFIED Requirements` for the two shipped requirements the change contradicts:
      "Version control belongs to the run's script, not to the agent" — the finished branch's clause and its
      scenario "A finished attempt's branch is merged and its item closed" now carry the refusal's own outcome —
      and "A block records the mechanism that stopped it", which names the pre-screen's exclusion as the one
      second home of `unmergeable` and gains the scenario naming
      `state#anAdoptionRefusalIsRecordedBesideTheExclusion`. The 17 shipped scenarios are restated with them, so
      every requirement the archive carries and the delta names are one text. Verify: the requirement sections of
      the delta and of `openspec/specs/agent-issue-loop/spec.md` compare byte for byte — `3 MATCH` from the probe
      in `evidence/round-2-corrections.txt` §1.
- [x] 5.2 `openspec/specs/agent-issue-loop/spec.md` is re-merged from those sections — no requirement left saying
      an admissible finished branch is merged and closed unconditionally. Verify: `tools/check-openspec-hygiene.sh`
      rc=0, `tools/check-doc-routes.sh` rc=0 (all 329 section references resolve) and
      `tools/check-rule-routes.sh --diff ed509ee4b1f7746f271a4768ecb2be930d98e3a9` rc=0 —
      `evidence/round-2-corrections.txt` §2.
- [x] 5.3 The claims that were false are deleted or cut to what was measured: `proposal.md` no longer says "Nothing
      in the shipped spec contradicts it", its surface numbers are the commands' current output (330 / 125, the
      delta's 21 scenarios and 21 cases), the base pointer is `run.sh:395`, `#788` is the item's hypothesis and not
      a second measured refusal, and `design.md`'s blast radius and vocabulary argument name the two modified
      requirements. Verify: `./tools/loop/measure.sh --change
      2026-11-06-fix-an-adoption-the-tree-refuses-is-recorded` reads `scenarios=21 cases=21 unnamed=0` beside
      `LOOP-MEASURE-SUITE cases=330` — `evidence/round-2-corrections.txt` §3.
- [x] 5.4 The second batch of `PASS` lines the round-1 review could not find in `green-on-change.log` now cites the
      gate tally that carries it. Verify: the file it names carries `tests=330 failures=0`, and the log it no longer
      claims holds exactly the ten lines it has.
- [x] 5.5 The gate, run once after the correction because the run's configuration states it for this round: no case
      was added and none changed, so its numbers are the change's own. Verify: `LOOP-GATE: tests=925 failures=0
      errors=0`, whose 26 tallies sum to the same `tests=925 failures=0` — `evidence/gate-round-2.txt` and
      `evidence/gate-tallies-round2/`.
