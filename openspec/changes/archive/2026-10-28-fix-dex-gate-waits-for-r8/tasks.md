# Tasks

Every task below serves the one requirement this change adds to capability `test-coverage` — "The dex gate waits
for its variant's dex producer" — and its three scenarios (a minified variant's gate waits for R8, a plain
variant's gate waits for the dex chain, the packaging graph holds the gate and its producer).

## 1. The wiring (spec `test-coverage` — a minified variant's gate waits for R8)

- [x] 1.1 `app/build.gradle.kts` — the `clientApiDexVariants` loop names `minify${variant}WithR8` beside the dex
  builder and the three merge steps, and the comment states why a minified variant has neither of those. Verify:
  `./gradlew :app:checkClientApiInDexAutomotiveRelease --dry-run -q` prints
  `:app:minifyAutomotiveReleaseWithR8 SKIPPED` above `:app:checkClientApiInDexAutomotiveRelease SKIPPED` (before
  the change it printed the gate line alone).
- [x] 1.2 Measure the tree R8 writes, rather than assuming it: `./gradlew :app:minifyAutomotiveReleaseWithR8` —
  `BUILD SUCCESSFUL in 2m 18s`, `94 actionable tasks: 61 executed, 25 from cache, 8 up-to-date` — then
  `find app/build/intermediates -path '*dex*' -name '*.dex'` →
  `app/build/intermediates/dex/automotiveRelease/minifyAutomotiveReleaseWithR8/classes.dex`. The gate's scanned
  root needed no change; this is the measurement the first scenario's tree clause rests on.
- [x] 1.3 Both release gates green on the repaired wiring:
  `./gradlew :app:checkClientApiInDexAutomotiveRelease :app:checkClientApiInDexMobileRelease` —
  `BUILD SUCCESSFUL in 2m 3s`, `128 actionable tasks: 38 executed, 9 from cache, 81 up-to-date`
  (`:app:minifyMobileReleaseWithR8` executed).
- [x] 1.4 The command the report came from: `./gradlew release` — `BUILD SUCCESSFUL in 13m`,
  `223 actionable tasks: 130 executed, 5 from cache, 88 up-to-date`,
  `NaviVeylin release: 2026-10-28-2 (versionCode 107)`, both
  `app/build/outputs/bundle/mobileRelease/app-mobile-release.aab` and
  `app/build/outputs/bundle/automotiveRelease/app-automotive-release.aab` written,
  `:app:checkClientApiInDex{Automotive,Mobile}Release` executed before their bundles.

## 2. The guard (spec `test-coverage` — the packaging graph holds the gate and its producer)

- [x] 2.1 `tools/check-dex-gate-graph-selftest.sh` — 6 cases, each one Gradle `--dry-run` configuration, no
  build, no native tree, no network: a debug variant's gate follows the dex chain and names no R8 task, a
  release variant's gate follows R8, a release `bundle` holds the gate and its producer. `--case <id>` runs one
  case, which is what a revert-check names. Verify: `bash tools/check-dex-gate-graph-selftest.sh` →
  `check-dex-gate-graph-selftest: all 6 cases passed (--dry-run configurations, no build, no network)`.
- [x] 2.2 Revert-check (one mutation, the reported one): restore the merge-only dependency set and run
  `--case automotiveReleaseGateFollowsR8` → exit 1,
  `FAIL dexGateGraph#automotiveReleaseGateFollowsR8: the gate does not wait for minifyAutomotiveReleaseWithR8 — a
  minified variant has no dex builder or merge step, so the gate runs before any dex exists and fails with 'No
  dex archives to scan' (the reported './gradlew release' failure)`, its dry-run output showing
  `:app:checkClientApiInDexAutomotiveRelease SKIPPED` and nothing else, then
  `check-dex-gate-graph-selftest: 1 of 1 cases failed — dexGateGraph#automotiveReleaseGateFollowsR8`;
  `--case mobileReleaseGateFollowsR8` red the same way; wiring restored, all 6 green.
- [x] 2.3 `.github/workflows/build.yml` — one step, "Check the dex gate's producer", beside the JVM-only test
  graph probe: both configure `:app` and so both belong below 'Install Android SDK' and above 'Clone vcpkg'.

## 3. Documentation and review

- [x] 3.1 `guidelines/Design.md` §5, the "Both provenances reach the app" bullet — names the case file that
  asserts the gate's producer, so the next reader of the gate finds the graph check without searching `tools/`.
- [x] 3.2 `openspec validate fix-dex-gate-waits-for-r8 --strict` →
  `Change 'fix-dex-gate-waits-for-r8' is valid`. The shipped spec's own validator output, quoted rather than
  summarised: `openspec validate test-coverage --type spec` warns once —
  `requirements[0]: Requirement text is very long (>500 characters)` on "All test-bearing modules produce
  coverage metrics" (650 characters), which this change does not touch, and `--strict` therefore exits 1 on that
  pre-existing warning alone.
- [x] 3.3 Independent review (read-only child agent, no build), verdict **PASS** on all three scenarios with
  "OK with notes". Its notes are settled here: the unasserted "empty dex tree SHALL fail" sentence is dropped
  from the requirement, the tree-identity clause cites task 1.2 as a measurement, every task above names its
  parent requirement, and the two paraphrased evidence lines are replaced by the quoted output of 2.1 and 3.2.

## 4. Sync and archive

- [x] 4.1 Delta synced into `openspec/specs/test-coverage/spec.md` (user chose "Sync now"): pure append of the
  requirement and its three scenarios, 36 insertions and 0 deletions, no delta-style header left in the shipped
  spec, `### Requirement:` at 9 and `#### Scenario:` at 36.
- [x] 4.2 Archived to `openspec/changes/archive/2026-10-28-fix-dex-gate-waits-for-r8/`.
