# Tasks

Parent spec for every task below: `documentation-ownership` — delta in
`specs/documentation-ownership/spec.md` ("The validator's own strict rules are read before the first write").
Design: `design.md` (D1 = the rules' home, D2 = the case, D3 = ADDED never MODIFIED, D4 = the bound's scope,
C1 and C2 = the corrections after review round 1).
This change touches no compiled file, so every verification below is a structural check
(`guidelines/Build.md` §2, scope block): no Gradle build of its own, no test class, no device.

## 1. Reproduce the defect

- [x] 1.1 Write the case of both scenarios and record its red-on-HEAD run before touching `AGENTS.md`
  (spec: documentation-ownership — An author reads both rules before writing an artifact; The section names
  the command that catches them; design D2). The case is `evidence/probe.sh`, which reads the entry
  document's `OpenSpec Workflow` section because no harness reads that prose. Verify: both cases `FAIL`, the
  absent statement named, exit 1.
  **Done 2026-10-22T11:55:44+02:00:** `evidence/red-on-head.log` —
  `FAIL theEntryDocumentStatesTheValidatorStrictRules — the OpenSpec Workflow section does not state:
  [MODIFIED] [repeated verbatim] [500 characters] [the body, not its scenarios]` and
  `FAIL theEntryDocumentNamesTheStrictValidationCommand — … [openspec validate <change> --strict]`,
  `probe: 2 of 2 cases failed`, `EXIT=1`; tally `evidence/red-on-head.xml` `tests="2" failures="2"`. The
  probe resolves its root by walking upward for `AGENTS.md`, so the same case runs unchanged from the
  archived directory the change ends in.

## 2. State the two rules where an author reads them

- [x] 2.1 `AGENTS.md` "OpenSpec Workflow": add two bullets beside the three CI-gated artifact rules and the
  command line that names the strict check (spec: documentation-ownership — The entry document states the
  validator's strict rules; design D1, D4). Verify: the section states both rules, the 500-character bound
  with its scope (body, not scenarios; `MODIFIED` exempt) and the command, and the three CI-gated bullets are
  byte-identical to HEAD.
  **Done 2026-10-22T11:56:** `AGENTS.md` "OpenSpec Workflow" carries the paragraph "Two more rules are the
  validator's own …", the bullets "a `MODIFIED` block replaces its requirement when the change archives, so it
  carries the requirement **whole** … repeated verbatim" and "a requirement's text stays at most 500
  characters — the body, not its scenarios …", and "Run `openspec validate <change> --strict` — or `openspec
  validate --changes --strict` over the open set — before the artifact is finished rather than at archive
  time." `git diff --stat AGENTS.md` → `1 file changed, 15 insertions(+)`, `git diff AGENTS.md | grep -c
  '^-[^-]'` → `0` (no line removed or rewritten).

## 3. State the rule the entry document now carries

- [x] 3.1 Add one requirement to `documentation-ownership` with its two scenarios, each naming the probe case
  that exercises it, and keep its body inside the 500 characters the requirement itself states (spec:
  documentation-ownership — The validator's own strict rules are read before the first write, as first
  written here and renamed in 6.2; design D3, D4). Verify:
  the delta holds one `## ADDED Requirements` block and no `MODIFIED` block, the body measures under 500
  characters, and `openspec validate <change> --strict` exits 0.
  **Done 2026-10-22:** `specs/documentation-ownership/spec.md` — `## ADDED Requirements`,
  `### Requirement: The entry document states the validator's strict rules` (renamed in 6.2 to
  `The validator's own strict rules are read before the first write`), scenarios
  `An author reads both rules before writing an artifact` and `The section names the command that catches
  them`. Body measured by the same `awk` extraction the validator's body reader performs: **425 characters**
  (`awk '/^### Requirement: The entry document states/{f=1;next} f && /^#### /{exit} f{print}' … | tr '\n' ' '
  | wc -c`), re-measured at 426 by that pipeline during review round 1 — the count differs by one newline,
  not by the bound; the corrected body (task 6.2) measures **473** with the same pipeline. `openspec validate fix-openspec-strict-rules-unread --strict` → `Change
  'fix-openspec-strict-rules-unread' is valid`, exit 0.

## 4. Verify

- [x] 4.1 The case turns green: `evidence/probe.sh` → both cases `PASS`, exit 0, timestamp after the edit
  (spec: documentation-ownership — An author reads both rules before writing an artifact). Verify: the log,
  the tally, and a fresh timestamp rather than a cached line.
  **Done 2026-10-22T11:55:48+02:00:** `evidence/green-after-fix.log` —
  `PASS theEntryDocumentStatesTheValidatorStrictRules`, `PASS theEntryDocumentNamesTheStrictValidationCommand`,
  `probe: 0 of 2 cases failed`, `EXIT=0`; tally `evidence/green-after-fix.xml` `tests="2" failures="0"`.
  Re-run from the archived path after the move, 2026-10-22T12:12:40+02:00: both `PASS`, `EXIT=0`
  (`evidence/green-after-fix.log`).
- [x] 4.2 Revert-check: one mutation, the named case must fail, restore, green again (`revert-check` skill;
  the shell-gate form — the probe's `PASS`/`FAIL` line and exit code are the evidence). Mutation: delete the
  500-character bullet from `AGENTS.md`, nothing else.
  **Done 2026-10-22:** mutated (`evidence/revert-check-mutated.log`, 2026-10-22T11:56:25+02:00) —
  `FAIL theEntryDocumentStatesTheValidatorStrictRules — … does not state: [500 characters] [the body, not its
  scenarios]`, `PASS theEntryDocumentNamesTheStrictValidationCommand`, `probe: 1 of 2 cases failed`, `EXIT=1`;
  restored byte-identical and re-ran (`evidence/revert-check-restored.log`, 2026-10-22T11:56:28+02:00) —
  `probe: 0 of 2 cases failed`, `EXIT=0`. No marker comment is left: the mutation was the removal of a
  documented sentence, and `git diff AGENTS.md` shows the 15 added lines of task 2.1 only. Re-checked from
  the archived path, 2026-10-22T12:12:45+02:00 (mutated, `1 of 2 cases failed`) and 12:12:48+02:00 (restored,
  `0 of 2`), `git diff --stat AGENTS.md → 1 file changed, 15 insertions(+)` and `grep -c '^-[^-]'` → 0.
- [x] 4.3 The structural pair the loop's gate maps these paths to (`guidelines/Build.md` §2, scope block):
  `bash tools/gen-feature-list.sh --check-classification` and `tools/check-doc-routes.sh`, plus the CLI's own
  `openspec validate --strict` (task 3.1). Exclusions named: no Gradle target, no test suite, no device, no
  emulator — the change compiles nothing.
  **Done 2026-10-22:** in `evidence/gate.log` — `check-doc-routes: all 120 section references resolve, all 5
  skill references resolve` and the classification gate green; its tally `evidence/gate-tallies/` carries
  both. The gate ran the whole pathspec the loop's `gate.sh` derives, which included the accumulated
  worktree's compiled paths: `./gradlew test -PforceTests --no-build-cache` →
  `BUILD SUCCESSFUL in 3m 21s, 189 actionable tasks: 21 executed, 168 up-to-date`.
- [x] 4.4 The gate, run once, quoted rather than improved (worker prompt, Phase D).
  **Done 2026-10-22T11:56:32+02:00:** `LOOP-GATE: tests=481 failures=0 (./gradlew test -PforceTests
  --no-build-cache, tools/loop-selftest.sh, tools/loop-mutations-selftest.sh, tools/issue-tooling-selftest.sh,
  tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh, bash
  tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)`, exit 0 —
  `evidence/gate.log`.

## 5. Archive

- [x] 5.1 Sync the added requirement into `openspec/specs/documentation-ownership/spec.md` and archive this
  change to `openspec/changes/archive/<date>-fix-openspec-strict-rules-unread/`. Verify: `openspec validate
  documentation-ownership --type spec` reports it valid, no delta-style header is left in the shipped spec,
  the merged block equals the delta block, and the archived directory exists under `archive/`.
  **Done 2026-10-22:** `openspec archive fix-openspec-strict-rules-unread --yes` → `+ 1 added`,
  `Totals: + 1, ~ 0, - 0, → 0`, `Specs updated successfully`, `Change … archived as
  '2026-10-22-fix-openspec-strict-rules-unread'`. The requirement sits at
  `openspec/specs/documentation-ownership/spec.md:415` with both scenarios and their case lines; the shipped
  spec holds no delta header; `openspec validate documentation-ownership --type spec` → `Specification
  'documentation-ownership' is valid` with two pre-existing `>500 characters` warnings on
  `requirements[11]` and `requirements[13]` (neither is this change's requirement, which measures 425), and
  `openspec validate --changes --strict` → `No items found to validate` because this change has left the open
  set. The directory exists at `openspec/changes/archive/2026-10-22-fix-openspec-strict-rules-unread/` and the
  open path no longer exists.

## 6. Corrections after review round 1

Review round 1 refused two blockings: the command scenario's case asserted one of its two conjuncts, and the
entry document's block stated both rules while naming no owner. Both are corrections of claims this change
already made, not new scope; no compiled file changed, so the verification is structural.

- [x] 6.1 The case asserts both invocations the second scenario's `THEN` names, and reads the section's
  strict-rules passage rather than the whole section (spec: documentation-ownership — The section names the
  command that catches them; design C1). Verify: removing the `--changes` form fails the case and the restore
  is byte-identical.
  **Done 2026-10-22T12:19:01+02:00:** `evidence/probe.sh` `case_b` names `openspec validate <change>
  --strict` and `openspec validate --changes --strict`; `case_a` and `case_b` both read the passage the
  lead-in sentence opens. `evidence/revert-check.sh`'s `changes` mutation → `FAIL
  theEntryDocumentNamesTheStrictValidationCommand — the OpenSpec Workflow strict-rules passage does not state:
  [openspec validate --changes --strict]`, `probe: 1 of 2 cases failed`, `EXIT=1`, `RESTORED byte-identical`
  (`evidence/revert-check.log`). Before the correction the same deletion left the case green.
- [x] 6.2 The requirement now states both rules normatively and requires the entry document to name it; the
  entry-document block names `openspec/specs/documentation-ownership/spec.md` and the requirement, and
  summarises each rule in one sentence (spec: documentation-ownership — The validator's own strict rules are
  read before the first write; design C2). Verify: red with the owning clause absent, green with it present,
  the requirement body inside its own 500-character bound.
  **Done 2026-10-22:** owning clause deleted → `FAIL theEntryDocumentStatesTheValidatorStrictRules — … does
  not state: [openspec/specs/documentation-ownership/spec.md] [The validator's own strict rules are read
  before the first write]`, `probe: 1 of 2 cases failed`, `EXIT=1`, restored byte-identically
  (`evidence/red-on-correction.log`, 2026-10-22T12:18:59+02:00; the same cut is the `owning` mutation of
  `evidence/revert-check.sh`). Restored tree → both `PASS`, `0 of 2`, `EXIT=0`
  (`evidence/green-after-correction.log`, 2026-10-22T12:19:01+02:00). Whole passage deleted → `2 of 2 cases
  failed` (`evidence/revert-check.log`, `passage` mutation). The requirement body measures 473 characters by
  the same `awk | tr '\n' ' ' | wc -c` pipeline; the delta block and the merged block are byte-identical
  (`diff`, clean).
- [x] 6.3 Structural checks over the corrected tree, the pair the loop's `gate.sh` maps these paths to plus the
  CLI's own validation (spec: documentation-ownership — both scenarios). Verify: each exits 0, and no Gradle
  target is run because no compiled file changed (`guidelines/Build.md` §2, scope block).
  **Done 2026-10-22T12:18:35+02:00:** `tools/check-doc-routes.sh` → `all 120 section references resolve, all 5
  skill references resolve`; `bash tools/gen-feature-list.sh --check-classification` → `specs read: 177,
  classified: 177, unclassified: 0, stale: 0`; `openspec validate documentation-ownership --type spec` →
  valid; `openspec validate fix-openspec-strict-rules-unread --strict` → `Change
  'fix-openspec-strict-rules-unread' is valid` (the archived change copied to the open path for the call and
  removed again). Evidence: `evidence/structural-checks.log`, `evidence/openspec-validate.log`. The gate
  itself is not re-run: it was run once for this item (task 4.4) and the correction touches no compiled file.
