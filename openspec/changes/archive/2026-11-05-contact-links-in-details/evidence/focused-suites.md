# Focused suites — contact links in the phone details dialog

Change `contact-links-in-details` (issue #768, base `ec58cb91`). Every run below was forced
(`-PforceTests --no-build-cache`), and the tallies are the JUnit XML's own `tests/failures/errors`
with the XML's `timestamp`; the XMLs sit beside this file.

Two measured facts from the pre-plan block (`./tools/loop/measure.sh --change contact-links-in-details`):

```
LOOP-MEASURE-SUITE cases=314 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-CHANGE contact-links-in-details scenarios=10 cases=10 unnamed=0
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
```

`scenarios=10 cases=10 unnamed=0` is the spec-to-case mapping: the ten scenarios of the two deltas each
name one case, and every case exists.

## The red case, on HEAD before any production code

| what | command | XML | tally | timestamp |
|---|---|---|---|---|
| `LocationDetailsDialogComposeTest#websiteValueStartsTheBrowser` | `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.LocationDetailsDialogComposeTest.websiteValueStartsTheBrowser" -PforceTests --no-build-cache` | `red-on-HEAD.xml` | `tests="1" failures="1" errors="0"` | `2026-11-05T09:44:36.004Z` |

Failure quoted: `java.lang.AssertionError: expected:<android.intent.action.VIEW> but was:<null>` — the tap on
the `Contact/Website` value starts no intent on HEAD, because the row renders it as plain text.

## The suites after the change

| module | command | XML | tally | timestamp |
|---|---|---|---|---|
| `:core` — the classifier | `./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.details.ContactLinksTest" -PforceTests --no-build-cache` | `contact-links-suite.xml` | `tests="7" failures="0" errors="0"` | `2026-11-05T09:48:18.235Z` |
| `:app` — the dialog | `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.LocationDetailsDialogComposeTest" -PforceTests --no-build-cache` | `details-dialog-suite.xml` | `tests="22" failures="0" errors="0"` | `2026-11-05T09:52:25.371Z` |
| `:auto` — the car screen | `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.DetailsScreenTest" -PforceTests --no-build-cache` | `car-details-suite.xml` | `tests="33" failures="0" errors="0"` | `2026-11-05T09:47:43.354Z` |

The five new dialog cases are in `details-dialog-suite.xml` (`websiteValueStartsTheBrowser`,
`phoneValueOpensTheDialer`, `aNonContactEntryStaysInert`, `anUnhandledLinkShowsTheHint`, `aHandledLinkShowsNoHint`);
the car case is `contactRowsCarryTheirValueAndStartNoIntent`; the classifier's seven cases carry the delta's
four classifier-backed scenarios plus `aNonContactEntryYieldsNoTarget` and two normalisation guards — the fifth
scenario, "An entry outside the Contact section stays inert", is the Compose case. The `:app` row is the **last** run of that class: it was re-run
(after an unused-import tidy) so the XML beside this file is newer than the code it proves.

**Task deviation, quoted not hidden**: `tasks.md` 1.2 names `:core:test`, which this build does not have — `:core`
is an Android library (`com.android.library`), so its unit-test task is `:core:testDebugUnitTest`. The run used
the task that exists.

## The project gate

The gate was run once, by the loop's gate child, after the pre-gate review of this diff: `./tools/loop/gate.sh`,
exit 0, `BUILD SUCCESSFUL in 5m 12s`, 458 tests with zero failures and zero errors (the compiled step plus the 31
`tools/*` suites), and the three suites above re-run green in it — `evidence/gate.md`, `evidence/gate.log`,
`evidence/gate-*.xml`. The tallies beside this file are the pre-gate measure; the gate's own are the `gate-*` copies.

## What this change did not run

- **Task 7.3 (device check)** — no device or emulator in this run; the hint path is proven on the JVM through the
  injected launcher (`design.md` D6), and the device record stays open for the device-check skill.
