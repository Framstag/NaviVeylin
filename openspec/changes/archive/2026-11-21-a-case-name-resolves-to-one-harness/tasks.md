# Tasks — the case-name traceability check

Spec: `change-readiness` (delta `specs/change-readiness/spec.md`). Gate: the new self-test
`bash tools/tests/check-case-names-selftest.sh` (offline) plus `bash tools/check-case-names.sh` on the committed
tree. No Gradle build is owed — no compiled file changes (`guidelines/Build.md` §2, the scope block) — and no case
of the loop suite was renamed, so `bash tools/loop/tests/loop-selftest.sh` was not re-run.

Depends on the epic `#895`. Baseline and every figure below: `evidence/traceability-baseline.md`.

## 1. The registry

- [x] 1.1 `tools/case-harnesses.json`: one row per harness, the schema
  `{id, kind, path|pattern|prefix|command, roots, groups, exclude, bare, why}`. **41 rows**, not the four the
  proposal planned: the 2026-11-17 comment on the item proves four rows leave 176 of 779 `group#case` names
  unresolved and cannot express the ~100 Gradle `Class#method` names, so every suite a shipped spec cites carries a
  row — `loop`, `sensors`, `issues-core` (with `exclude: "^check#"`), `check-metadata`, `resolve-stale`, `gradle`
  (seven roots, `app(device)` among them), `native` (`"bare": false`), `build-inputs`, `change-readiness`,
  `glossary`, `portability`, `sonar-workflow`, `sonar-findings`, `gen-glossary`, `action-runtimes`, `doc-routes`,
  `doc-values`, `jvm-test-graph`, `openspec-hygiene`, `rule-routes`, `todo-references`, `vcpkg-cache`,
  `dex-gate-graph`, `gate-artifacts`, `compose-sandbox`, `submodule-pin`, `gen-feature-list`, `look`,
  `project-metrics`, `declared-cases`, `prune-native`, `gate-timings`, `mutate`, `measure-highlight`,
  `loop-mutations`, `sensor-mutations`, `build-inputs-mutations`, `loop-spend`, `loop-quota`, `probe`,
  `case-names`. The schema's comment says a group two harnesses claim is *declared*, not forbidden.
  Verify: `jq '.harnesses | length' tools/case-harnesses.json` → `41`, and the check prints `harnesses=41`.
- [x] 1.2 Record the baseline on the committed tree: `CASE-NAMES: ok harnesses=41 lines=1524 named=1316 none=129
  unnamed=79 unclaimed=5985`, `unresolved=0`, `duplicates=0`. The issue's 1252/597/572/69 measured one suite's
  slice; the divergence and its two causes are `evidence/traceability-baseline.md` §2–§3.

## 2. The check

- [x] 2.1 `tools/check-case-names.sh`: forward direction — extract every `**Case**:` name from
  `openspec/specs/**/spec.md`, resolve it against the registry, refuse an unresolved name and a name two harnesses
  list. Backward direction — count the harness names no spec names. Verdict line
  `CASE-NAMES: ok harnesses=41 lines=1524 named=1316 none=129 unnamed=79 unclaimed=5985` or `CASE-NAMES: refused
  unresolved=… duplicates=… rows=… undeclared=… hinges=…`, `--json` carrying the counts and the three lists, exit
  0/1/2. Reads no sentence for meaning. Satisfies `A scenario's case name resolves to exactly one harness` and `A
  case no scenario claims is reported as a number`.
- [x] 2.2 Refuse a registry row whose list file or command is absent or fails (`traceability#aRowWhoseListIsAbsentIsRefused`),
  so a harness cannot silently leave the resolution set.
- [x] 2.3 `tools/tests/check-case-names-selftest.sh` with the 12 `traceability#` cases and throwaway fixtures: a
  fixture spec naming a loop case, one naming a sensors case, one naming a Gradle case, one naming nothing, one
  naming a shared group's name, one whose prose mentions a case-shaped name outside a `**Case**:` line, a scenario
  with no `**Case**:` line, a registry with a missing list, a row emitting an unclaimed group, an exclusion that
  hides a name, a harness whose every case is unclaimed, and a duplicate full name. Verify: all 12 pass offline in
  1.4 s with no tracker, no build and no network.
- [x] 2.4 Rename the sensors suite's `run#dryRunTouchesNothing` (no spec and no requirement names it) to
  `run#aDryRunWritesNothingToTheTracker`, so the one duplicate is gone in the same change that begins refusing
  duplicates. Verify: `bash tools/sensors/tests/sensor-cases.sh` green and the check's `duplicates=0`.
- [x] 2.5 Three refusal kinds beyond the two the proposal planned, each proven by its own case: `undeclared` (a row
  emits a `group#name` in a group it never claimed), `hinges` (an `exclude` hides a name no other row declares) and
  `rows` (a row's list is absent or fails). `aRowThatEmitsAnUnclaimedGroupIsRefused` and
  `anExclusionThatHidesANameIsRefused` are in the delta spec.

## 3. Wiring, so it is not a tool nobody runs

- [x] 3.1 `tools/tooling.json`'s `checks` map gains `caseNames: tools/check-case-names.sh`, the way `changeReady`
  and `glossary` are there, plus the sentence a reader needs. `bash tools/check-portability.sh` passes.
- [x] 3.2 `.github/workflows/build.yml`: the artifact-hygiene step runs `bash tools/check-case-names.sh` and
  `bash tools/tests/check-case-names-selftest.sh`, with the comment stating it is deliberately not in the loop's
  gate. Verify: the step's own run on the committed tree prints `ok`.
- [x] 3.3 `report.md` states that the check is **not** in the loop's gate and why (a spec-wide invariant must not
  redden one item's gate; the loop's selection names no spec).

## 4. Documents

- [x] 4.1 `guidelines/BuildTests.md` §6 gains the rule: a scenario names a case that resolves to exactly one
  harness, a shared group is declared, an unclaimed case is a number and never a refusal — with the command that
  re-takes it and the mutation file that falsifies it. The rule is routed in `tools/doc-routes.json`'s
  `guidelines/BuildTests.*` entry, so `bash tools/check-rule-routes.sh` and `bash tools/check-doc-routes.sh` pass.
- [x] 4.2 `guidelines/Backlog.md` §6 names the unclaimed-case count beside the readiness verdict a tier-B finding
  needs, so both numbers a filing path quotes have one home.
- [x] 4.3 No capability was created, so `tools/feature-list/specs.json` needs no row; `report.md` says so.

## 5. Verification

- [x] 5.1 `bash tools/check-case-names.sh` on the committed tree: `ok` in 15.5 s, with the six counts above.
- [x] 5.2 Self-test green: 12 cases, 1.4 s wall.
- [x] 5.3 Revert-check for the resolution rule: remove one case name from the loop suite's list while a spec still
  names it, and `traceability#aNameNoHarnessRunsIsRefused` must fail; restore, forced green — record
  `unresolved-name-is-refused` in `tools/tests/check-case-names-mutations.json`.
- [x] 5.4 Revert-check for the duplicate rule: put `run#dryRunTouchesNothing` back into the sensors list and
  `traceability#aDuplicateFullNameIsRefused` must fail; restore, forced green — record `duplicate-full-name-is-refused`.
- [x] 5.5 Revert-check for the absent-list rule: point one registry row at a path that does not exist and
  `traceability#aRowWhoseListIsAbsentIsRefused` must fail; restore, forced green — record `absent-list-is-refused`.
- [x] 5.6 Revert-check for the no-refusal rule: make an unclaimed case exit 1 and
  `traceability#anUnclaimedCaseNeverRefuses` must fail; restore, forced green — record
  `an-unclaimed-case-never-refuses`. `bash tools/mutate.sh --mutations tools/tests/check-case-names-mutations.json`
  → 4 red, 4 restored green, 0 problems.
- [x] 5.7 Defects this work found, and what was done with each: seven shipped specs named a case that no harness ran
  under that name (corrected in the spec, table in the evidence); the sensors suite held a latent duplicate
  (renamed); `check-rule-routes` was red for three rules left by #938 and #894 (routed); `check-openspec-hygiene`
  reports 26 open changes with no `x-issue:` field — this change carries `x-issue: 896`, and the rest are filed as
  `#999` rather than repaired under the changes that hold them.
- [x] 5.8 The readiness checker's own falsification did not cover a refusal it had gained: a case
  `readiness#everyRefusalHasAMutation` now asserts every `record <name>` refusal has a mutation record whose case is
  in the suite and whose search line is unique, and the record `every-refusal-has-a-mutation` falsifies that list
  itself. `bash tools/tests/check-change-readiness-selftest.sh` → 14 cases green, and `bash tools/mutate.sh
  --mutations tools/tests/check-change-readiness-mutations.json` → 8 red, 8 restored green.