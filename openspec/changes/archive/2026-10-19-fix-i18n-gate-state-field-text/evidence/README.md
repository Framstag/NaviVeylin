# Evidence — fix-i18n-gate-state-field-text

| file | what it proves |
|---|---|
| `red-buildsrc-scan.xml` | the red test on HEAD `5d65687`: `HardcodedStringScannerTest` `tests="19" failures="3" timestamp="2026-10-19T19:22:05.995Z"` — `stateFieldIsFlagged`, `prefixedStateFieldIsFlagged`, `reportCallMessageIsFlagged` |
| `buildsrc-green.xml` | the scanner's suite after the fix: `tests="20" failures="0" timestamp="2026-10-19T20:49:56.163Z"` (the fifth new case is `aPositionInsideALiteralIsNotAnAssignment`) |
| `buildsrc/` | all eleven `buildSrc` suites of that run — `tests=127 failures=0 errors=0`, the aggregate the 127 count comes from |
| `gate.txt` | the project gate: one run in `Build.md` §3's form, `BUILD SUCCESSFUL in 3m 47s`, with what round 1's two failed attempts did and did not produce |
| `gate-results.txt` | the five tallies of that gate run summed from the result directories, each with a digest of the directory it was summed from |
| `gate-reports/` | Gradle's own aggregate report per test task (`app-mobile`, `app-automotive`, `auto`, `core`, `osmscout-client-java`) — tests/failures/errors counters |
| `module-gates.txt` | `:app:checkHardcodedStrings :auto:checkHardcodedStrings :core:checkHardcodedStrings` — the widened scanner over HEAD's sources reported 20 positions (7 + 1 + 6 + 5 + 1), and BUILD SUCCESSFUL after the sweep |
| `suites/` | the per-class XMLs of the focused suites (round 1) plus `MapCanvasViewModelDatabaseOpenTest`, `MapCanvasViewModelInitThreadingTest` and `RoutingFailureNoticeTest` from the gate run |
| `focused-suites.txt` | the focused runs the changed classes own, before the gate |
| `revert-check.txt` | mutating the `DISPLAY_FIELD` arm fails the seven named cases (`revert-check-mutated.xml`, `failures="7"`, `20:49:50.105Z`), and the restore is shown by an empty `git status --porcelain`, no `drop(999)` and the unchanged file digest |
| `revert-check-mutated.xml` | the failing suite of that mutation, archived as it was produced |
