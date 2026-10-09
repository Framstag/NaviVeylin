# Tasks

## 1. The failing test that reproduces the defect (red on HEAD)

- [x] 1.1 Add `app/src/test/java/com/naviveylin/ui/map/CompassNeedleStrokeTest.kt`: a Robolectric case under
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)` that composes the production `CompassButton` at `Density(1f)` and
  `Density(4f)` in one tree, rasterizes the decor view into a `Bitmap`, measures the needle-colored pixel run
  across the needle at its centre column, and asserts the drawn width is the same in dp at both densities
  (`|dp(4x) − dp(1x)| ≤ 1.5 dp`) and that it grows with density (`px(4x) ≥ 2 · px(1x)`). Verified by compiling
  it — `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.CompassNeedleStrokeTest"` runs it.
- [x] 1.2 Red run on HEAD: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.CompassNeedleStrokeTest" -PnoCoverage` →
  `tests="1" skipped="0" failures="1" errors="0" timestamp="2026-10-09T18:38:26.091Z"`, case
  `CompassNeedleStrokeTest#needle stroke keeps its width in dp at 1x and 4x`, failure
  `java.lang.AssertionError: the needle stroke must be the same width in dp at 1x and 4x (1x=4px=4.0dp, 4x=4px=1.0dp)`;
  the case printed `CompassNeedleStroke 1x=4px (4.0dp) 4x=4px (1.0dp)` into its XML `system-out` before it
  asserted. That XML is **not retained** — the later green runs, the 4.1 mutation re-run and the 4.2 gate each
  overwrote it, and Gradle copies no test stdout into its console log — so the pre-fix row stands as recorded
  prose from that run, re-creatable by 4.1's mutation (which reproduced the same row and message, its own XML
  again overwritten). The retained numbers are the gate's after row (2.2, 4.2).
- [x] 1.3 Coexistence probe for the new sandbox config (the change adds the first `@GraphicsMode(…)` class to
  `:app`, so the `guidelines/Build.md` §6 classloader hazard must be shown not to fire):
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*" -PnoCoverage` → 103 classes /
  777 tests, `failures=1` (this case, the expected red) and `grep -c 'already loaded in another classloader'`
  over the run log = 0. Recorded from that run; the retained form of the same check is the 4.2 gate (228 classes
  per `:app` flavor, 0 failures, no classloader line).

## 2. The fix

- [x] 2.1 `CompassButton.kt`: give the needle one density-independent stroke width — `val needleStrokeWidth = 3.dp.toPx()`
  beside `needleLength` in `drawCompassNeedle` (now `:222`) — and pass it to both `drawLine` calls, replacing
  `strokeWidth = 3f` at HEAD `:229` / `:237` (now `:233` / `:241`). Verified by `git diff`: 6 insertions /
  2 deletions in that one production file, touching nothing else.
- [x] 2.2 Focused green: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.CompassNeedleStrokeTest" -PforceTests --no-build-cache -PnoCoverage`
  → BUILD SUCCESSFUL, `113 actionable tasks: 25 executed, 88 up-to-date` (`/tmp/loop-72-green2.log` — a Gradle
  tally, not test stdout; the test-derived numbers below come from the XML), XML
  `tests="1" skipped="0" failures="0" errors="0" timestamp="2026-10-09T18:41:48.942Z"`, and the case printed
  `CompassNeedleStroke 1x=4px (4.0dp) 4x=12px (3.0dp)` — the 4x stroke grew from 4 px to 12 px, i.e. 3.0 dp
  against 4.0 dp at 1x (`|diff| = 1.0 dp`, inside the 1.5 dp antialiasing tolerance). (That XML was later
  overwritten by the gate; the same row is retained in the gate's XMLs, quoted in 4.2.)

## 3. Spec scenario → case

- [x] 3.1 Scenario "Needle stroke keeps its width in dp at 1x and 4x" (`compass-button` — Compass needle stroke
  is density-independent) → `com.naviveylin.ui.map.CompassNeedleStrokeTest#needle stroke keeps its width in dp at 1x and 4x`.
  The first assertion (dp equality across densities) is the scenario's THEN; the second (px grows with density)
  is its AND. Verified by the case failing on the dp comparison on HEAD (1.2) and passing in both flavors in
  4.2 — no other case asserts this scenario (`grep -rn "needleStrokeWidth\|strokeWidth" app/src/test` finds only
  this class's measurement helper).

## 4. Falsification and the gate

- [x] 4.1 Revert-check (one mutation, the named case must fail for the right reason, restore, forced green):
  mutated the fixed production line back to the defect — `needleStrokeWidth = 3.dp.toPx()` → `3f`, marked
  `// REVERT-CHECK MUTATION (task 4.1)` — ran
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.CompassNeedleStrokeTest" -PnoCoverage` →
  `tests="1" skipped="0" failures="1" errors="0" timestamp="2026-10-09T18:41:31.161Z"`,
  `java.lang.AssertionError: the needle stroke must be the same width in dp at 1x and 4x (1x=4px=4.0dp, 4x=4px=1.0dp)`
  thrown from `CompassNeedleStrokeTest.kt:130` (the dp-equality assertion, not a premise), with
  `CompassNeedleStroke 1x=4px (4.0dp) 4x=4px (1.0dp)` printed; 113 actionable tasks: 25 executed, 88 up-to-date
  (`/tmp/loop-72-revert.log` — a Gradle tally, not test stdout).
  That mutation XML is **not retained** (the restore run and the gate overwrote it) — the row and message above
  are recorded prose from it, reproducible by re-running this mutation. Restored the fix
  (`grep -rn 'REVERT-CHECK MUTATION' app/src` → 0 matches, `git diff HEAD -- app/src/main` = the 6/2 fix) and the
  forced re-run went green: `tests="1" failures="0"`, ts `2026-10-09T18:41:48.942Z`, 113 actionable tasks:
  25 executed, 88 up-to-date.
- [x] 4.2 Forced both-flavor gate: `./gradlew test -PforceTests --no-build-cache` → BUILD SUCCESSFUL in 4m 59s,
  `188 actionable tasks: 20 executed, 168 up-to-date`; `:app:testMobileDebugUnitTest` **228 classes, 1752 tests,
  0 failures, 0 errors** (this case `tests="1" failures="0"`, ts `2026-10-09T18:53:43.465Z`) and
  `:app:testAutomotiveDebugUnitTest` **228 classes, 1752 tests, 0 failures, 0 errors** (this case `tests="1"
  failures="0"`, ts `2026-10-09T18:51:28.811Z`); `:core:testDebugUnitTest` 41 classes / 448 tests / 0 failures,
  `:auto:testDebugUnitTest` 77 classes / 789 tests / 0 failures, `:osmscout-client-java:test` 2 classes /
  26 tests / 0 failures. Both flavor XMLs of this case carry the after row
  `CompassNeedleStroke 1x=4px (4.0dp) 4x=12px (3.0dp)`. `grep -c 'already loaded in another classloader'` over
  the gate log = 0 (the `@GraphicsMode(NATIVE)` sandbox coexists with the JNI-stub classes). Log
  `/tmp/loop-72-gate2.log` (not retained as evidence; the XMLs above are).
  — The first forced gate on the same tree (`/tmp/loop-72-gate.log`, BUILD FAILED in 5m 1s, `188 actionable
  tasks: 30 executed, 158 up-to-date`) failed on two cases of `MapCanvasViewModelModeTest`
  (`navigationEndRestoresFreeDriveMode`, `cardStopRestoresBrowseMode`,
  `java.lang.IllegalStateException: Dispatchers.Main is used concurrently with setting it` from
  `MainDispatcherRule.finished`), i.e. a teardown race, not an assertion of this change. Attribution runs:
  `MapCanvasViewModelModeTest` alone → `tests="9" failures="0"` (ts `2026-10-09T18:47:32.029Z`); together with
  this case → `tests="9" failures="0"` and `tests="1" failures="0"` (ts `2026-10-09T18:47:43.847Z` /
  `18:47:43.845Z`); the whole automotive flavor forced on the same tree → BUILD SUCCESSFUL in 1m 54s, 228
  classes / 1752 tests / 0 failures. Those three attribution XMLs are **not retained** either — the flavour-wide
  re-run and the gate replaced them — so their numbers stand as recorded prose from those runs. It is recorded
  here rather than hidden: a reviewer re-running the gate can hit it, and it is not caused by this change's
  class.
- [x] 4.3 No test expectation moves: `grep -rn 'strokeWidth = [0-9]*f' app/src` → 0 matches (no raw-pixel stroke
  remains anywhere in `app/src`), and `strokeWidth` in `CompassButton.kt` is the two `needleStrokeWidth`
  arguments (`:233`, `:241`). No case asserts a stroke width other than this class's own measurement helper
  (`grep -rn strokeWidth app/src/test` → only `CompassNeedleStrokeTest.kt`). The neighbouring compass cases
  stayed green in 4.2 — `CompassButtonComposeTest` tests=6, `CompassNeedleTargetTest` tests=6,
  `CompassPaletteTest` tests=6, all failures=0 in both flavors.

## 5. Documentation and bookkeeping

- [x] 5.1 `guidelines/UI.md` §8: added one bullet — a drawn dimension (stroke width, radius, marker geometry) is
  density-independent, expressed in dp (`.dp.toPx()`), never a raw device-pixel count — citing the measurement
  this change retains (`CompassNeedleStroke 1x=4px (4.0dp) 4x=12px (3.0dp)` after the fix against
  `4x=4px (1.0dp)` on HEAD).
- [x] 5.2 `TODO.md` §72: set `**status:** fixed-by \`fix-compass-needle-stroke-density\`` (removal is not
  allowed in this run), keeping the entry's id, order and metadata. **Done in phase F, in the archive commit**:
  the §72 metadata line now reads `fixed-by \`fix-compass-needle-stroke-density\``, the Clusters index's
  `- **ui** — bug:` row dropped `§72` (it reads `§70 §73 §74`), and the structure re-check holds
  (`## ` headings = `**id:** ` lines = 94, every heading followed by its metadata line, no id collision). The
  independent review of this change returned PASS on round 1 — its single P2 note (the ±1.5 dp tolerance is
  deliberately loose) is report-only, recorded as a design Non-Goal — and the change moves to
  `openspec/changes/archive/2026-10-09-fix-compass-needle-stroke-density/` with the `compass-button` requirement
  merged into the main spec in this same commit.

## Device follow-up

- None: the invariant is fully host-decidable (the drawn width is measured from the rasterized view hierarchy),
  and neither the widget's layout nor its rendering path changes for a device to re-measure. No device task is
  implied as done.
