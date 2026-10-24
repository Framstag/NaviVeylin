# Tasks

## 1. The phone lines format through one fixed locale

- [x] 1.1 `app/src/main/java/com/naviveylin/ui/map/DiagnosticLines.kt` (new):
      `internal fun diagnosticNumber(format, value) = String.format(Locale.US, format, value)` and
      `internal fun followDiagnosticLine(fixTime, speedKmH, smoothedBearing, dbg, offset)` — the screen's
      line with its field order and `-` placeholders unchanged (spec: `i18n-l10n` — Log and diagnostic
      numbers are locale-independent; design D1, D2). Verify: `:app` compiles and the cases of 3.1/3.2
      assert the builder's exact text.
- [x] 1.2 `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`: the frame loop's `follow`
      concatenation is replaced by the builder call, the every-30th-fix throttle and the logcat sink
      unchanged. Verify: 3.1's `theFollowLineKeepsItsNumbersOnADecimalCommaLocale`.
- [x] 1.3 `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`: the GPS-fix line, both auto-zoom
      lines and both halves of the browse re-center line (logcat and the file-backed `DiagnosticsLog` entry)
      format through `diagnosticNumber`. Verify: 3.1's two ViewModel cases.
- [x] 1.4 `guidelines/Regulatory.md` §9: the follow recipe's provenance sentence names both variants and both
      changes. Verify: the sentence names `fix-phone-diagnostic-locale` beside `fix-car-follow-reengage-render`.

## 2. The test seam

- [x] 2.1 `app/src/test/java/com/naviveylin/test/DefaultLocaleRule.kt` (new): one rule that makes a locale the
      JVM default for a case and restores it whatever the outcome, plus `withLocale(locale) { … }` for a case
      that needs a second locale for one block. Verify: the classes of 3.1 use it and no case carries an
      inline `setDefault`/`finally` pair.
- [x] 2.2 `app/src/test/java/com/naviveylin/ui/map/DiagnosticLineLocaleTest.kt` (new, Robolectric, default
      sandbox): three cases — the `follow` builder's dot line and field set, the builder's `-`/`NaN`
      placeholders, and the GPS-fix line captured through `ShadowLog` under `Locale.GERMANY`.
- [x] 2.3 `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelBrowseReCenterTest.kt`: one case that
      drives the real reveal and asserts the re-center evidence line's numbers carry dots.

## 3. Verification and archive

- [x] 3.1 Focused green, forced:
      `./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests
      "com.naviveylin.ui.map.DiagnosticLineLocaleTest" --tests
      "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"` — `DiagnosticLineLocaleTest`
      `tests="3" failures="0"`, `MapCanvasViewModelBrowseReCenterTest` `tests="24" failures="0"` (the class
      held 23 cases before this change). Verify: `evidence/revert-check-restored.xml` (`17:04:24.967Z`),
      `evidence/revert-check-restored-recenter.xml`, `evidence/revert-check-restored.log`.
- [x] 3.2 Revert-check (skill `revert-check`): `DiagnosticLines.kt`'s `Locale.US` →
      `Locale.getDefault()` fails exactly four cases — `DiagnosticLineLocaleTest` 3/3 plus
      `theReCenterEvidenceLineKeepsItsNumbersOnADecimalCommaLocale` — and the German output is quoted in the
      failures (`acc=5,0`, `off=400,0px … mag=15,00`, `spd=50,0 … off=3,4,-1,4`); restoring the constant makes
      both classes green again, and `git status` shows the file back at its committed content. Verify:
      `evidence/revert-check-mutated.xml` (`tests="3" failures="3"`, `17:03:31.625Z`),
      `evidence/revert-check-mutated.log`, `evidence/revert-check-restored.*`.
- [x] 3.3 The gate this delta owes: `tools/loop/gate.sh --base origin/main` — **exit 0**,
      `LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 386s,
      … 7 shell suites)`, the Gradle part `BUILD SUCCESSFUL in 6m 25s` (`evidence/gate.log:225`), no suite
      failing. The case count comes from the result XMLs, every directory `failures=0 errors=0`: `:app`
      mobile 2113, `:app` automotive 2113, `:auto` 880, `:core` 646, `:osmscout-client-java` 48
      (`evidence/gate-tallies/`, `evidence/evidence.md` §1). Two pre-existing `w:` lines in untouched test
      sources are attributed there.
- [x] 3.4 Archive the change and confirm the delta merged into `openspec/specs/i18n-l10n/spec.md`.
