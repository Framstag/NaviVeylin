# Evidence — fix-phone-diagnostic-locale

Base: `origin/main`. Worktree: `.pi/worktrees/issue44` (branch `fix/issue-44-phone-diagnostic-locale`).

## 1. The gate the delta owes

`tools/loop/gate.sh --base origin/main` — the Gradle gate (the delta touches compiled paths) plus the shell
suites the changed paths pull in. Full output: `gate.log`.

```
LOOP-GATE: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 386s,
tools/check-compose-sandbox-rule.sh 1s, tools/check-compose-sandbox-rule-selftest.sh 0s,
bash tools/gen-feature-list.sh --check-classification 8s, tools/check-doc-routes.sh 1s,
tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 1s,
tools/check-todo-references.sh 1s)
EXIT=0
```

`gate.log:225` — `BUILD SUCCESSFUL in 6m 25s`, `164 actionable tasks: 80 executed, 84 up-to-date`. The summary's
`tests=0` is the loop's own count of suite summary lines, not the suite's cases: the Gradle task prints no
`tests=` line, so the case count is summed from the result XMLs instead — every directory `failures=0
errors=0`:

| suite | result XMLs | tests | failures | errors |
|---|---|---|---|---|
| `:app` mobile (`testMobileDebugUnitTest`) | 290 | 2113 | 0 | 0 |
| `:app` automotive (`testAutomotiveDebugUnitTest`) | 290 | 2113 | 0 | 0 |
| `:auto` (`testDebugUnitTest`) | 87 | 880 | 0 | 0 |
| `:core` (`testDebugUnitTest`) | 61 | 646 | 0 | 0 |
| `:osmscout-client-java` (`test`) | 8 | 48 | 0 | 0 |

Per-suite tallies: `gate-tallies/`.

Two `w:` lines in the run, both in test sources this change does not touch and both pre-existing on the base
(open items: the nullable-inference family): `app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:131`
and `app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt:51`. No warning from a
touched file.

## 2. The focused cases, and the red the fix removes

Focused, forced — `./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests
"com.naviveylin.ui.map.DiagnosticLineLocaleTest" --tests
"com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"`:

| class | tests | failures |
|---|---|---|
| `DiagnosticLineLocaleTest` (new) | 3 | 0 |
| `MapCanvasViewModelBrowseReCenterTest` (23 cases on the base, 24 now) | 24 | 0 |

`revert-check-restored.xml` (`timestamp="2026-10-24T17:04:24.967Z"`),
`revert-check-restored-recenter.xml`, `revert-check-restored.log`.

**Revert-check** (skill `revert-check`): the one line the rule hangs on, `DiagnosticLines.kt`'s
`String.format(Locale.US, …)` → `String.format(Locale.getDefault(), …)`, i.e. the pre-fix behaviour.

- Four cases fail — `DiagnosticLineLocaleTest` 3/3 (`theFollowLineKeepsItsNumbersOnADecimalCommaLocale`,
  `theGpsFixLineKeepsItsNumbersOnADecimalCommaLocale`, `theFollowLineKeepsItsUnknownFields`) and
  `MapCanvasViewModelBrowseReCenterTest.theReCenterEvidenceLineKeepsItsNumbersOnADecimalCommaLocale`.
- The failures quote the German output, which is the defect: `expected:<…acc=5[.0 bearing=45.0 speed=30.]0…>
  but was:<…acc=5[,0 bearing=45,0 speed=30,]0…>`; `expected:<…spd=50[.0 … off=3.4,-1.]4 clamped=false> but
  was:<…spd=50[,0 … off=3,4,-1,]4 clamped=false>`.
- `revert-check-mutated.xml` (`tests="3" failures="3"`, `timestamp="2026-10-24T17:03:31.625Z"`),
  `revert-check-mutated.log`.
- The mutation was restored and the constant re-read (`String.format(Locale.US, format, value)`), and the
  restored file is the committed content — the class is green again in the forced run above.

## 3. The decision this change is

`Locale.US` on the phone, `Locale.ROOT` on the car, and why neither is arbitrary: `design.md` D1. The pick was
the whole change (issue #44's loop verdict, 2026-10-09: "Nothing in the evidence picks one"), and it was
decided by the operator on 2026-10-24.

## 4. Independent review

One read-only reviewer round over the delta before the commit. It returned `VERDICT: PASS` after these
findings, all of which are in the committed tree: no OpenSpec artifact for a shipped-spec edit (this change
directory, strict-valid, and the shipped spec untouched by the commit itself); a comment claiming the
diagnostics view for the `follow` line, which is logcat-only (scoped to the re-center line); "one fixed
locale" naming two (reworded, and the car's own line left to `auto-diagnostics`); the requirement-body
500-character limit (the rule lands as its own requirement instead of extending an already-over-limit one);
two save/restore shapes for the default locale (one `DefaultLocaleRule`); and `avg=NaN` undocumented (noted in
the builder's KDoc, behaviour unchanged). The base moved three commits during the round; the branch was
rebased onto it and the gate above is the rebased tree's run.
