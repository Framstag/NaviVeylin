# Tasks

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Add the case `favoriteFileFormatNativesAreDeclaredOnTheOverride` to
  `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/OSMScoutClientFavoriteFileFormatApiTest.java`:
  reflectively require `getFavoriteFileFormatVersion` (native, no arguments, returns `int`) and
  `isFavoriteFileFormatSupported` (native, no arguments, returns `boolean`) on `OSMScoutClient` — the red half
  of the added requirement "The Android override declares the submodule's favorite file-format natives".
  Verify: on HEAD, `./gradlew :osmscout-client-java:test --tests "com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest"`
  → BUILD FAILED, tests="1" failures="1" (`java.lang.NoSuchMethodException: com.framstag.libosmscout.client.OSMScoutClient.getFavoriteFileFormatVersion()`),
  2026-10-10T07:45:58.051Z, retained under
  `evidence/HEAD-red-TEST-com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest.xml`.

## 2. The fix

- [x] 2.1 In `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`, declare
  `public native int getFavoriteFileFormatVersion();` and
  `public native boolean isFavoriteFileFormatSupported();` with the submodule's KDoc, in the submodule's order
  (after `getStarredFavorites`, before `setStarred`). Verify: the focused module suite is green —
  `./gradlew :osmscout-client-java:test` passes and the case's XML carries tests="1" failures="0"
  (`evidence/afterfix-focused-TEST-com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest.xml`,
  2026-10-10T07:46:55.188Z); `git diff` on the file is the two declarations plus their KDoc and nothing else
  (`1 file changed, 22 insertions(+)`).

## 3. Scenario coverage

- [x] 3.1 Added requirement "The Android override declares the submodule's favorite file-format natives" —
  scenario "The favorite file-format natives are declared on the override" is exercised by
  `OSMScoutClientFavoriteFileFormatApiTest#favoriteFileFormatNativesAreDeclaredOnTheOverride`. Verify: red on
  HEAD (task 1.1), green after the fix (task 2.1), and the case asserts the declaration/return-type claims the
  scenario makes and no stronger claim (the host JVM does not load the native library, so it does not assert an
  invocation).

## 4. Falsification

- [x] 4.1 Revert-check the added declaration, one mutation: remove the `getFavoriteFileFormatVersion`
  declaration from the override (marked `// REVERT-CHECK MUTATION`) → the case failed at its
  `getDeclaredMethod` premise with `java.lang.NoSuchMethodException: …getFavoriteFileFormatVersion()`,
  tests="1" failures="1", 2026-10-10T07:47:02.612Z (retained as
  `evidence/mutation-TEST-com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest.xml`).
  Restored (`grep -rc 'REVERT-CHECK MUTATION'` → 0) and re-ran the module suite forced (`--rerun-tasks`, 7
  actionable tasks: 7 executed) green, tests="1" failures="0", 2026-10-10T07:47:13.282Z (retained as
  `evidence/restore-green-TEST-com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest.xml`).
  The failure is at the assertion that describes the invariant, not a compile or premise failure.

## 5. Gate

- [x] 5.1 Ran the one forced both-flavor gate for this change shape (production code changed):
  `./gradlew test -PforceTests --rerun-tasks` → BUILD SUCCESSFUL in 8m 24s, 189 actionable tasks: 189 executed
  (console; log not retained). Per-module tallies of the modules this gate ran, each read from that
  module's own XMLs under `build/test-results/` at run time — **not retained** (`build/test-results/`,
  overwritten by the next run) — as classes/tests/failures/errors: `:app` mobile 248/1872/0/0, `:app`
  automotive 248/1872/0/0, `:auto` 78/793/0/0, `:core` 51/532/0/0, `:osmscout-client-java` 5/34/0/0. The
  case's gate tally is tests="1" failures="0" at
  2026-10-10T07:47:25.195Z (`evidence/gate-TEST-com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest.xml`).
  No `MapCanvasViewModelModeTest` failure occurred (the known §148 flake was not hit: both flavors 9/0/0).
- [x] 5.2 Confirmed no unrelated expectation moved: no reference outside the submodule —
  `grep -rn --exclude-dir=libosmscout 'getFavoriteFileFormatVersion\|isFavoriteFileFormatSupported' app/src/main auto/src/main core/src`
  prints nothing, exit 1 (the unexcluded form returns 27 lines, all under the submodule checkout
  `app/src/main/cpp/libosmscout/**`; `grep -rn … | grep -v 'app/src/main/cpp/libosmscout/' | wc -l` → 0).
  And `git -C app/src/main/cpp/libosmscout status --porcelain` is empty (no submodule change).

## 6. Evidence retention

- [x] 6.1 Copied every XML the change cites into `openspec/changes/fix-jni-override-parity/evidence/`
  (HEAD-red, afterfix-focused, mutation, restore-green, gate) and ran
  `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-jni-override-parity` in live mode.
  Verify: exit 0, every quoted tally carried by a retained XML, C4 finds no mutation marker.

## 7. Bookkeeping

- [x] 7.1 Left the `TODO.md` §119 status line and the `native-jni — improvement` cluster index for the
  orchestrator's post-review bookkeeping. Verify: `git diff --stat TODO.md` is empty (the orchestrator owns
  the bookkeeping).
