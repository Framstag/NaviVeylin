# Tasks

## 1. Scanner — the positions the gate was blind to

- [x] 1.1 Replace the seven spelled-out `… =` names in `TEXT_POSITION` with a suffix-matched
      `DISPLAY_FIELD` (`text|title|label|description|hint|placeholder|message|error`, case-insensitive),
      add `reportError\(` to `TEXT_POSITION`, and skip a position that starts inside an already-open string
      literal. Verify: `./gradlew -p buildSrc test` — `HardcodedStringScannerTest` `tests="20" failures="0"`
      (`evidence/buildsrc-green.xml`, `20:49:56.163Z`), including the five new cases
      (`stateFieldIsFlagged`, `prefixedStateFieldIsFlagged`, `reportCallMessageIsFlagged`,
      `logPayloadFieldIsNotFlagged`, `aPositionInsideALiteralIsNotAnAssignment`) and the 15 pre-existing
      cases unchanged.
- [x] 1.2 Extend the class KDoc: the fourth blind spot (a named sink), and the line-based residual.
      Verify: no behaviour; the scanner test run of 1.1 is the check.

## 2. `:app` — the literals the widened gate exposes

- [x] 2.1 Add the new keys to `app/src/main/res/values/strings.xml` and their German translations to
      `app/src/main/res/values-de/strings.xml`. Verify: `GermanTranslationCompletenessTest` (`:app`).
- [x] 2.2 `ui/favorites/FavoritesViewModel.kt` (13 sentences), `ui/mapmanager/BasemapViewModel.kt` (1),
      `ui/mapmanager/MapManagerViewModel.kt` (7), `ui/map/MapCanvasViewModel.kt` (7): resolve every one
      through the `Context` the class already holds. Verify: `./gradlew :app:checkHardcodedStrings`
      succeeds, and the affected classes' unit tests are green.

## 3. `:auto` — the literals the widened gate exposes

- [x] 3.1 Add the new keys to `auto/src/main/res/values/strings.xml` and `values-de/strings.xml`. Verify:
      `GermanTranslationCompletenessTest` (`:auto`).
- [x] 3.2 `NavigationSession.kt:673` and `PoiResultsScreen.kt:143,158`: resolve through `carContext`; the two
      failure sentences use `:core`'s `navigation_failure_gps_signal_missing`. Verify:
      `./gradlew :auto:checkHardcodedStrings` and `PoiResultsScreenSearchLifecycleTest` +
      `RoutingFailureNoticeTest` green (`evidence/suites/`: `RoutingFailureNoticeTest` from the gate run at
      `20:46:20.964Z`, `PoiResultsScreenSearchLifecycleTest` from the focused run at `20:41:07.333Z`).
- [x] 3.3 `SurfaceAttribution`: `geometry`/`draw` take the notice text, `NavigationScreen` and
      `FreeDrivingScreen` pass `carContext.getString(R.string.osm_attribution)`. Verify:
      `SurfaceAttributionTest` green (its four cases pass the text; `tests="4"`).

## 4. The gate itself

- [x] 4.1 `./gradlew :app:checkHardcodedStrings :auto:checkHardcodedStrings :core:checkHardcodedStrings` —
      no finding in any of the three trees. This is the change's red-to-green witness for the sweep.
- [x] 4.2 `guidelines/UI.md` §10 "Lint + scan gate": list the new positions and the assignment suffix rule.
      Verify: read the bullet against the scanner's constants.

## 5. Verification and archive

- [x] 5.1 The project gate, `./gradlew test -PforceTests --no-build-cache` (`guidelines/Build.md` §3's form;
      `--rerun-tasks`, the run's configured string, is not that form) — **BUILD SUCCESSFUL in 3m 47s**,
      189 actionable tasks: 22 executed, 167 up-to-date. Tallies summed from the result directories:
      `:app` automotive 2062, `:app` mobile 2062, `:auto` 866, `:core` 642, JNI 48 — all
      `failures=0 errors=0`; `evidence/gate.txt`, `evidence/gate-results.txt` (each directory with a
      digest) and `evidence/gate-reports/` (Gradle's five aggregate reports). `buildSrc` is not part of
      `./gradlew test`: its own suite ran separately, `tests=127 failures=0` (`evidence/buildsrc/`).
- [x] 5.2 Revert-check the new rule (skill `revert-check`): `DISPLAY_FIELD.findAll(line)` →
      `.drop(999)` fails the seven cases the arm carries (`stateFieldIsFlagged` and
      `prefixedStateFieldIsFlagged` among them), the pre-mutation file is restored, and the restore is
      shown by an empty `git status --porcelain`, no `drop(999)` and an unchanged file digest. Verify:
      `evidence/revert-check.txt`, `revert-check-mutated.xml` (`failures="7"`, `20:49:50.105Z`),
      `buildsrc-green.xml` (`failures="0"`, `20:49:56.163Z`).
- [x] 5.3 Archive the change; confirm the delta merged into `openspec/specs/i18n-l10n/spec.md`.
