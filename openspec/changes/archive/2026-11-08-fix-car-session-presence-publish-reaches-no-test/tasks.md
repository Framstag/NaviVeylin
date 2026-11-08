# Tasks

## 1. Confirm the claim

- [x] 1.1 The two publishes are inline and host-only. `bash tools/look.sh 'carSessionPresence().setActive'`
  names only `NavigationSession.kt` (the `onStart`/`onDestroy` steps), and no `:auto` test constructs
  `NavigationSession` (`NavigationSessionDarkModeTest`/`NavigationSessionRestoreTest` state the
  `CarContext` constraint in their KDoc). Verified.
- [x] 1.2 The red case. `auto/src/test/java/com/naviveylin/auto/SessionPresencePublishTest.kt` fails to
  compile on the base `b114dc619347dac664a7480f5e4874772fb4ef92` — `Unresolved reference
  'publishCarSessionPresence'` at four lines (`evidence/red-on-HEAD.log`).
- [x] 1.3 Route with the map: `AGENTS.md` derives `guidelines/DesignCrossVariant.md` §8a ("every host
  mutation goes through the guarded seam", "no fault escapes a host callback") for this change's paths;
  the shipped capability is `car-session-presence`. Read §8a; no guideline edit is owed.

## 2. Extract the publish seam

- [x] 2.1 Add `internal fun publishCarSessionPresence(presence: () -> CarSessionPresence, live: Boolean):
  Boolean` in `NavigationSession.kt`; it publishes through `guardedHostCall` under the existing
  "publish car session presence" / "clear car session presence" names and returns whether it landed.
  Verify: `bash tools/look.sh 'publishCarSessionPresence'` names the seam plus the two call sites.
- [x] 2.2 Route the `onStart` and `onDestroy` publishes through it (`live = true` / `live = false`),
  replacing the inline `guardedHostCall("…") { carSessionPresence().setActive(…) }` blocks. The provider
  keeps the lazy resolution inside the confined block. Verify: `bash tools/look.sh 'setActive' --path
  auto` names exactly one production call site, inside the seam.

## 3. The host-free cases

- [x] 3.1 Add `SessionPresencePublishTest` (4 cases): `sessionStartPublishesLive`,
  `sessionEndClearsTheSignal`, `aFaultingPublishIsConfinedAndNamed`,
  `clearingAFaultingPublishIsNamedForTheClear`. Verify: `./gradlew :auto:testDebugUnitTest --tests
  "com.naviveylin.auto.SessionPresencePublishTest" -PforceTests --no-build-cache` →
  `tests="4" failures="0" errors="0"` (`evidence/revert-check-green.xml`, 2026-11-08T15:14:46Z).
- [x] 3.2 The change compiles and the affected suite passes: `./gradlew :auto:testDebugUnitTest
  -PforceTests --no-build-cache` → `BUILD SUCCESSFUL` (36 insertions, 9 deletions, one file).
- [x] 3.3 Falsify the guard (one mutation, the `revert-check` skill): drop `.setActive(live)` from the
  seam — `tests="4" failures="4" errors="0"` (2026-11-08T15:14:38Z,
  `evidence/revert-check-red.xml`) — restore, re-run forced → `tests="4" failures="0" errors="0"`
  (`evidence/revert-check-green.xml`). `grep -c 'REVERT-CHECK MUTATION'` on the file → `0`.

## 4. Hand over

- [x] 4.1 This delta touches a compiled file, so the gate is owed — and the run has deferred it to a
  fresh gate child. No `LOOP-GATE` line is printed; the last line is `LOOP-DIFF-READY`. No first-line
  `# files:` case is written (the delta adds no `tools/loop-cases.sh` case).
