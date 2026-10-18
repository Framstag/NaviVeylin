# Tasks

Parent spec for every task below: `documentation-ownership` — delta in
`specs/documentation-ownership/spec.md` ("A modified requirement carries the scenarios its spec still has").
Design: `design.md` (D1 = repair shape, D2 = the rule's home, D3 = no new guard, D4 = verification).
This change touches no compiled file, so every verification below is a structural check
(`guidelines/Build.md` §2, scope block): no Gradle build, no test suite, no device.

## 1. Reproduce the defect

- [x] 1.1 Write the case that reproduces the defect and record its red-on-HEAD run before touching anything.
  The case is `openspec validate fix-navigation-engine-fault-isolation --type change` — the change-artifact
  validation of the delta the defect lives in — because this change touches no compiled file, so no test class
  exists to carry it (spec: documentation-ownership — The change set is validated by command; design D4).
  Verify: exit 1 with the two MODIFIED errors naming the five omitted scenarios, plus `openspec validate
  --changes` = 14 passed / 1 failed of 15 items, both quoted with their timestamp in
  `evidence/red-on-head.log`.
  **Done 2026-10-18 19:18:** `evidence/red-on-head.log` — `Change 'fix-navigation-engine-fault-isolation' has
  issues`, `✗ [ERROR] car-host-fault-isolation/spec.md: MODIFIED "No fault escapes into the host path" omits
  scenario(s) ... "A fault in one frame keeps the frames coming", "A fault in a display tick keeps the follow
  pipeline running"` and the `navigation-engine/spec.md` error naming "A background writer cannot revert a
  concurrent publication", "The staleness tick reads the injected time source", "A native lookup result is
  published on the main thread", `exit=1`; `openspec validate --changes` → `Totals: 14 passed, 1 failed
  (15 items)`. TALLY: 2 errors, 5 scenarios the MODIFIED blocks drop.

## 2. Repair the two MODIFIED blocks

- [x] 2.1 `car-host-fault-isolation` — complete the block from its shipped requirement: restore the shipped
  per-loop-iteration sentence and the two scenarios that go with it, keeping the change's own engine sentence
  and its `Engine fault while a car session is live` scenario (spec: documentation-ownership — A completed
  block keeps the requirement whole; design D1). Verify: extraction of block and shipped requirement, then
  `git diff --no-index --word-diff` → 0 removed words, scenarios 11 → 12.
  **Done 2026-10-18:** `openspec/changes/fix-navigation-engine-fault-isolation/specs/car-host-fault-isolation/spec.md`
  — the block now carries `Work that a car map performs as one iteration of a continuing loop …detach it from
  the state it consumes.` followed by the change's `It SHALL also hold for the process-scoped navigation
  engine…` sentence; the two scenarios sit back in shipped order after `Rendering a car frame fails`. Word-diff
  against the shipped requirement: **0 removed word-groups**, 11 added; scenarios 11 (shipped) → 12 (block).
- [x] 2.2 `navigation-engine` — complete the block the same way: restore the shipped atomic-publication and
  injected-time-source sentences and the three scenarios that go with them, keeping the change's fault-handler,
  native-boundary and stays-usable sentences and its two new scenarios (spec: documentation-ownership — A
  completed block keeps the requirement whole; design D1). Verify: word-diff → 0 removed words, scenarios
  6 → 8.
  **Done 2026-10-18:** `openspec/changes/fix-navigation-engine-fault-isolation/specs/navigation-engine/spec.md`
  — the description keeps `…main-thread confined and SHALL be applied as an atomic update, so a publisher can
  never revert a field another publisher set concurrently. The staleness tick's decision SHALL be taken from
  the engine's injected time source, not from the system clock read at the tick site.` before the change's
  fault-handler sentences; `A background writer cannot revert a concurrent publication`, `The staleness tick
  reads the injected time source` and `A native lookup result is published on the main thread` are back in
  shipped order, the change's two scenarios after them. Word-diff: **0 removed word-groups**; scenarios
  6 (shipped) → 8 (block).
- [x] 2.3 Keep the two shipped specs and the guideline consistent with the repaired blocks (proposal; design
  Context). Verify: the shipped `openspec/specs/car-host-fault-isolation/spec.md` and
  `openspec/specs/navigation-engine/spec.md` are untouched by this change (`git status`), and
  `guidelines/Design.md` §4/§8 state the rules the blocks carry — the engine's fault handler (`Design.md:178`
  `engine's scope carries **its own fault handler**`) and the per-loop-iteration exception (`Design.md:490`
  `Unless that piece of work is one iteration of a continuing loop`) — so no guideline edit is owed.
  **Done 2026-10-18:** `grep -n 'fault handler|SupervisorJob' guidelines/Design.md` → the engine bullets at
  `:178`/`:188`, the loop-iteration exception at `:490` and the engine clause in §8 at `:505-509`, with
  `engineFaultHandler` named at `:179`, `:507` and `:525`; both shipped specs show no diff (only the two delta
  files under `openspec/changes/fix-navigation-engine-fault-isolation/` are modified). No guideline edit.

## 3. State the rule the defect broke

- [x] 3.1 Add one requirement to `documentation-ownership` with its two scenarios, each naming the command that
  exercises it (`openspec validate --changes`; `openspec validate <change> --type change`) — the capability
  that owns the project's change-artifact rules (`design.md` D2). Verify: `specs/documentation-ownership/spec.md`
  holds one ADDED requirement and `openspec validate fix-openspec-modified-delta-loss --type change` exits 0.
  **Done 2026-10-18:** `specs/documentation-ownership/spec.md` — `## ADDED Requirements` with
  `### Requirement: A modified requirement carries the scenarios its spec still has` and the scenarios
  `The change set is validated by command` and `A completed block keeps the requirement whole`.
- [x] 3.2 Record the guard decision where the fix is designed (spec: documentation-ownership — The change set is
  validated by command; design D3). Verify: `design.md` D3 names the alternatives the evidence eliminated
  (`tools/declared-cases.sh` parses `tasks.md`, not deltas; CI installs no OpenSpec CLI — no `setup-node`/`npm`
  step in `.github/workflows/build.yml`; a new checker has no cheap home) and names where the guard runs — this
  change's validate task and the archive step's delta assessment, with `.github/workflows/build.yml`
  "Check OpenSpec artifact hygiene" named as the only OpenSpec-artifact CI step and as *not* invoking the CLI.
  **Done 2026-10-18:** `design.md` D3 written with those three alternatives and the two run points;
  `grep -n 'setup-node|npm ' .github/workflows/build.yml` → no match, and `bash tools/declared-cases-selftest.sh`
  → 12 passed, 0 failed (the runner's own surface, unaffected by a delta).

## 4. Verify

- [x] 4.1 `openspec validate fix-navigation-engine-fault-isolation --type change` → exit 0 (the red-on-HEAD
  case turns green; spec: documentation-ownership — The change set is validated by command).
  **Done 2026-10-18:** `Change 'fix-navigation-engine-fault-isolation' is valid`, exit 0 (its only output is the
  pre-existing `>500 characters` warning on the `auto-diagnostics` ADDED requirement, present on HEAD too). The
  same command exited 1 in task 1.1.
- [x] 4.2 `openspec validate --changes` over the whole open set, and state whether the other unarchived changes
  are clean (spec: documentation-ownership — The change set is validated by command). Verify: the command's
  tally, recorded in `evidence/green.log`, with the other changes named.
  **Done 2026-10-18:** `evidence/green.log` §3 — `Totals: 16 passed, 0 failed (16 items)`, exit 0 (16 = the
  fifteen changes that were open before this one + this change). The fourteen others were already clean at HEAD
  and are untouched by this change: `dedupe-stylesheet-loads`, `fix-aggregate-run-test-flakes`,
  `fix-car-follow-reengage-render`, `fix-car-rail-widget-tap`, `fix-car-render-coordinate-redaction`,
  `fix-compound-name-matching`, `fix-cross-database-search-scope`, `fix-details-screen-observation-scope`,
  `fix-open-database-path-validation`, `fix-phone-map-layer-stack`, `fix-poi-symbol-icons`,
  `fix-start-map-selection`, `publish-requested-route-start`, `search-result-row-content`. The fifteenth,
  `fix-navigation-engine-fault-isolation`, is the one this change repairs — and the only one that had been
  failing (14 passed / 1 failed of 15 at HEAD, task 1.1).
- [x] 4.3 The structural checks CI runs for an `openspec/**` delta: the "Check OpenSpec artifact hygiene" step
  (task markers, config quoting) and `bash tools/check-doc-routes.sh`. Exclusions named: no Gradle build and no
  test suite (no compiled file), no device.
  **Done 2026-10-18:** hygiene replication → `hygiene fail=0`; `check-doc-routes: all 89 section references
  resolve` (exit 0). `bash tools/declared-cases.sh` selects nothing for this change and exits 0 (no `*Test`
  token in the artifacts, no test source in the diff) — the case that must fail is the CLI's, not a suite's.
  Excluded: `./gradlew` in every form, `adb`, the emulators — the delta compiles nothing.
- [x] 4.4 Revert-check the invariant this fix introduces. The invariant *is* the original symptom (a MODIFIED
  block keeping every shipped scenario), so its revert-check is the red-on-HEAD run itself: the mutation is
  deleting the two restored scenarios from the `car-host-fault-isolation` block again, and the case that must
  fail is `openspec validate fix-navigation-engine-fault-isolation --type change` — which is exactly what task
  1.1 recorded (`exit=1`, naming both scenarios). No further revert-check is owed (config: only for an
  invariant beyond the original symptom). Verify: `evidence/red-on-head.log` §1 (red) beside §2 (green).
  **Done 2026-10-18:** red = `evidence/red-on-head.log` (`exit=1`, the two frame scenarios named); green =
  `evidence/green.log` §2 (`Change 'fix-navigation-engine-fault-isolation' is valid`, exit 0).

## 5. Archive and close

- [x] 5.1 Sync the added requirement into `openspec/specs/documentation-ownership/spec.md` and archive this
  change with the `openspec-archive-change` skill (move, not copy) to
  `openspec/changes/archive/<date>-fix-openspec-modified-delta-loss/`. Verify: the synced spec validates, no
  delta-style section header is left in it, and the merged block equals the delta block.
  **Done 2026-10-18:** after `openspec instructions specs --change fix-openspec-modified-delta-loss --json`
  (exit 0, 9 rules applied), the requirement was written into
  `openspec/specs/documentation-ownership/spec.md` after *The change-artifact guidance reads by section*:
  `openspec validate documentation-ownership --type spec` → valid, `grep -c 'ADDED|MODIFIED|REMOVED|RENAMED
  Requirements'` → 0, and the extracted merged block diffs empty against the delta block (2 scenarios). The
  change directory was then **moved** (not copied) to
  `openspec/changes/archive/2026-10-18-fix-openspec-modified-delta-loss/` — the open path no longer exists, and
  `openspec validate --changes` reads 15 items because this change has left the open set.
- [x] 5.2 One commit `fix: fix-openspec-modified-delta-loss (#159)`, every touched file added, tree clean
  afterwards; the untracked `Agentisches-Coding-NaviVeylin.md` is left alone.
  **Done 2026-10-18 (one commit, message `fix: fix-openspec-modified-delta-loss (#159)`, on top of `b83e857`):**
  it carries the archived change
  (`proposal.md`, `design.md`, `tasks.md`, `specs/documentation-ownership/spec.md`,
  `evidence/{red-on-head.log,green.log,issue-159-close.md}`, `.openspec.yaml`) and the synced
  `openspec/specs/documentation-ownership/spec.md`. `git status --porcelain` afterwards shows only
  `?? Agentisches-Coding-NaviVeylin.md`, which is not this change's file.
  **The two repaired delta files are deliberately not in the commit:** `.gitignore:74-76` ignores
  `openspec/changes/*/` — *open changes are local WIP, only archived changes are versioned* — so the repair to
  `openspec/changes/fix-navigation-engine-fault-isolation/specs/…` lives in the working tree and ships when
  that change archives; force-adding an ignored open-change path is not this change's call. The repair is
  therefore recorded in this change's evidence and design, and the next `openspec validate --changes` sees it.
- [x] 5.3 Move issue #159 to `status/fixed-by` and close it (`tools/issues/update.sh`, `tools/issues/close.sh`),
  with the reason file under the archived change's `evidence/`, citing the archived change.
  **Done 2026-10-18:** `tools/issues/update.sh 159 --status fixed-by --json` → `{"updated": true, "issue":
  159, "labels": ["status/fixed-by"], "removedLabels": ["status/open"]}`; `tools/issues/close.sh 159 --reason
  "the two MODIFIED blocks … were completed from their shipped requirements …" --evidence "archived change
  openspec/changes/archive/2026-10-18-fix-openspec-modified-delta-loss/ (reason + evidence:
  evidence/issue-159-close.md) …"` → `✓ Closed issue Framstag/NaviVeylin#159`, `closed #159 (completed)`. The
  reason file `evidence/issue-159-close.md` carries the reason, the root cause and the red/green evidence, and
  it is in the commit above. No other issue belongs to this change.
