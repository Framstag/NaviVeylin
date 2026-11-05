# Tasks — contact links in the phone details dialog

Specs: `enhanced-details-sheet` (requirements "Contact values are actionable", "An unhandled contact link shows a
hint") and `auto-destination-details` (requirement "Contact rows on the car stay non-actionable").

Scope note: this change touches compiled code, so its gate is the build plus the affected suites
(`guidelines/Build.md` §2, the scope block). No native source, no JNI, no manifest and no Gradle configuration is
edited, so the native ABIs are not rebuilt for this change and that exclusion is deliberate.

## 1. The classifier (`:core`)

- [x] 1.1 Add `core/src/main/java/com/naviveylin/core/details/ContactLinks.kt`: `ContactLink(kind, display, target)`
  with `LinkKind.WEB` and `LinkKind.PHONE`, and a function resolving one `DescriptionEntry` to its
  `List<ContactLink>`, empty for every entry whose section is not `Contact` or whose label is neither `Phone` nor
  `Website`. Split on `;`, trim, resolve each part independently. *Verification*: the module compiles and 1.2's
  cases pass. *Parent spec*: `enhanced-details-sheet` — Contact values are actionable.
- [x] 1.2 Add `core/src/test/java/com/naviveylin/core/details/ContactLinksTest.kt` with these cases — the delta's
  four classifier-backed scenarios, the classifier's own guard and two normalisation guards (seven cases in all):
  - `aBareHostGetsHttpsAndIsLinked` — `www.cafe.de` resolves to `https://www.cafe.de`, kind `WEB`.
  - `aNonWebSchemeResolvesToNoTarget` — `javascript:alert(1)`, `file:///etc/passwd`, `data:text/html,x`,
    `intent://x`, `content://x` each resolve to no target.
  - `aPhoneValueBelowThreeDigitsResolvesToNoTarget` — `12` resolves to no target.
  - `aMultiValuedTagSplitsIntoOneTargetPerPart` — `https://a.de; https://b.de` yields two `WEB` targets in order,
    and `030 / 12 34 56; +49 30 7654321` yields two `PHONE` targets, normalised.
  - plus `aNonContactEntryYieldsNoTarget` (a `General`/`Name` entry carrying a URL), `anHttpWebsiteValueKeepsItsOwnScheme`
    and `aPhoneValueIsNormalisedToDigitsAndOnePlus` as guards. The fifth scenario (an entry outside the `Contact`
    section stays inert) is the `:app` case `aNonContactEntryStaysInert`.
  *Verification*: `./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.details.ContactLinksTest"`, all green.

## 2. The launcher (`:app`)

- [x] 2.1 Add the browser and dialer launcher beside `openUrl` in
  `app/src/main/java/com/naviveylin/ui/attribution/OsmAttributionOverlay.kt`: one `internal` function taking a
  `ContactLink`, building `ACTION_VIEW`/`ACTION_DIAL` with the target as data, returning `false` when
  `startActivity` throws. *Verification*: the module compiles; the return value is exercised by 2.4's hint cases.
  *Parent spec*: `enhanced-details-sheet` — An unhandled contact link shows a hint.

## 3. The dialog (`:app`)

- [x] 3.1 Render each resolved target in `LocationDetailsSheet.kt` as a clickable `Text` (underline,
  `colorScheme.primary`, 48 dp minimum touch target), one node per target; a part with no target renders as plain
  text, and every non-`Contact` entry keeps its current rendering. Add the defaulted
  `onLaunch: ((ContactLink) -> Boolean)? = null` parameter, whose production default is
  `launchContactLink(context, link)` taken inside the composable. *Verification*: 3.4's link cases.
  *Parent spec*: `enhanced-details-sheet` — Contact values are actionable.
- [x] 3.2 Wrap the dialog's `Column` in a `Box`, add a `SnackbarHost` with a local `SnackbarHostState`, and show the
  hint from a `rememberCoroutineScope()` when `onLaunch` returns `false`; the dialog stays open. *Verification*:
  3.4's hint cases. *Parent spec*: `enhanced-details-sheet` — An unhandled contact link shows a hint.
- [x] 3.3 Add the hint string to `app/src/main/res/values/strings.xml` and
  `app/src/main/res/values-de/strings.xml`. Wording names the failure and no coordinate. *Verification*:
  `./gradlew :app:lintDebug` reports no `HardcodedText` or missing-translation finding for it.
- [x] 3.4 Extend `app/src/test/java/com/naviveylin/ui/map/LocationDetailsDialogComposeTest.kt` with exactly these
  cases, one per scenario of the delta:
  - `websiteValueStartsTheBrowser` — tapping the `Website` value yields `ACTION_VIEW` with `https://cafe.de`, and the
    dialog is still displayed afterwards.
  - `phoneValueOpensTheDialer` — tapping the `Phone` value yields `ACTION_DIAL` with `tel:+49301234567`.
  - `aNonContactEntryStaysInert` — a `General`/`Name` value that is a URL starts no intent when tapped.
  - `anUnhandledLinkShowsTheHint` — with `onLaunch` returning `false`, the hint text is displayed and the dialog is
    still displayed.
  - `aHandledLinkShowsNoHint` — with `onLaunch` returning `true`, the hint text does not exist.
  *Verification*: `./gradlew :app:testMobileDebugUnitTest --tests
  "com.naviveylin.ui.map.LocationDetailsDialogComposeTest"`, all green.

## 4. The car (`:auto`)

- [x] 4.1 Extend `auto/src/test/java/com/naviveylin/auto/DetailsScreenTest.kt` with
  `contactRowsCarryTheirValueAndStartNoIntent`: the built row for a `Contact`/`Website` entry has the title `Website`
  and the text `https://cafe.de`, and the case records no started activity afterwards. *Verification*:
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.DetailsScreenTest"`, green. *Parent spec*:
  `auto-destination-details` — Contact rows on the car stay non-actionable. No production car file is edited.

## 5. Guidelines

- [x] 5.1 Add the rule to `guidelines/UIPhone.md` §5 "Phone details dialog structure": the two `Contact` label keys
  render as links, a refused value renders as text, and an unhandled launch shows the hint. *Verification*:
  `grep -n 'Contact' guidelines/UIPhone.md` shows the rule in §5, and the section's rules stay one line each.
- [x] 5.2 Add the deviation row to `guidelines/UI.why.md` §1 ("The parity decisions, and why"): phone = links
  (browser/dialer), car = plain text, constraint = a Car App Library template cannot start an activity outside the
  car app. *Verification*: the row is present in that table and names the constraint, as `UI.md` §1's "a documented
  deviation names its platform constraint" requires.

## 6. The refusal's own guard (revert-check)

- [x] 6.1 Mutate `ContactLinks.kt` once so that the scheme allow-list is removed (accept any parsed URI). Run
  `./gradlew :core:test --tests "com.naviveylin.core.details.ContactLinksTest" -PforceTests --no-build-cache`: the
  case `aNonWebSchemeResolvesToNoTarget` must fail. Restore the file, re-run the same command forced and green,
  and record both runs (command, case name, XML tests/failures tally) in the change's evidence.

## 7. Gate and device

- [x] 7.1 Run the focused suites named in 1.2, 3.4 and 4.1. *Verification*: every XML reports `failures="0"
  errors="0"`; record the four tallies.
- [x] 7.2 Run the project gate once, both flavors, before the change is marked complete (`guidelines/Build.md` §2
  and the `build-app` / `run-tests` skills). *Verification*: the gate's verdict, with the counts it prints.
  *Exclusion*: the native ABIs are not rebuilt — no native source or JNI file is edited.
  *Result*: `./tools/loop/gate.sh` exit 0, `BUILD SUCCESSFUL in 5m 12s`, 458 tests with zero failures and zero
  errors across the compiled step and the 31 `tools/*` suites; the change's three suites are green in it
  (`evidence/gate-details-dialog-suite.xml`, `evidence/gate-contact-links-suite.xml`,
  `evidence/gate-car-details-suite.xml`) — `evidence/gate.md`, `evidence/gate.log`.
- [ ] 7.3 On device: open the details dialog for a POI carrying a website and a phone number; tap the website and
  record that the browser opens on the same host; tap the phone and record that the dialer opens prefilled with the
  digits of the value; on a device or emulator with no dialer installed, tap the phone and record that the hint
  appears and the dialog stays open. *Verification*: the device-check skill's record, with the observed action, the
  host dialled and whether the hint appeared. *Note*: the hint needs a target with no handler, which a normal phone
  cannot produce — the emulator image without a dialer is the case that does.
  *Open in this session*: no device or emulator was available; the failure path is proven on the JVM through the
  injected launcher (design D6, `LocationDetailsDialogComposeTest#anUnhandledLinkShowsTheHint`), and the device
  record stays for the `device-check` skill.
- [x] 7.4 `openspec validate contact-links-in-details --strict`, and `tools/check-change-readiness.sh
  openspec/changes/contact-links-in-details` prints `LOOP-CHANGE-READY: ok`. *Verification*: both commands quoted
  with their output.

No Compose preview is added because no new composable is introduced — the link rendering is a branch inside the
existing row, and the hint uses Material 3's own `SnackbarHost`.

## 8. What this session recorded (`evidence/`)

- **The red case on HEAD**: `LocationDetailsDialogComposeTest#websiteValueStartsTheBrowser` — XML
  `tests="1" failures="1" errors="0"` (`2026-11-05T09:44:36.004Z`), `AssertionError:
  expected:<android.intent.action.VIEW> but was:<null>` — `evidence/red-on-HEAD.xml`, `evidence/red-on-HEAD.log`.
- **The focused suites** (each `-PforceTests --no-build-cache`): `:core` 7/0/0
  (`evidence/contact-links-suite.xml`), `:app` 22/0/0 (`evidence/details-dialog-suite.xml`, the class's last run),
  `:auto` 33/0/0
  (`evidence/car-details-suite.xml`) — commands, timestamps and the measure block's lines in
  `evidence/focused-suites.md`.
- **The revert-check** (6.1): the mutated run fails `aNonWebSchemeResolvesToNoTarget`
  (`javascript:alert(1) must resolve to no target`), the restore greps 0 markers, and the forced re-run is 7/0/0
  with a later timestamp — `evidence/revert-check.md`.
- **The gate** (7.2): `./tools/loop/gate.sh` → exit 0, `BUILD SUCCESSFUL in 5m 12s`; 458 tests, zero failures, zero
  errors over the compiled step and the 31 `tools/*` suites, and the three suites above green in it
  (`evidence/gate-*.xml`) — `evidence/gate.md`, `evidence/gate.log`.
- **Lint** (3.3): `./gradlew :app:lintMobileDebug` → `0 errors, 101 warnings`, none naming
  `details_contact_link_unhandled` (`HardcodedText`/`MissingTranslation` absent from the report) —
  `evidence/lint-mobile-debug.txt`.
- **OpenSpec** (7.4): `openspec validate contact-links-in-details --strict` → `Change 'contact-links-in-details' is
  valid`; `tools/check-change-readiness.sh` → `LOOP-CHANGE-READY: ok` — `evidence/openspec-validate.txt`,
  `evidence/change-readiness.txt`.
- **Two task commands this build does not have, quoted rather than hidden**: `tasks.md` names `:core:test` and
  `:app:lintDebug`; the tasks that exist are `:core:testDebugUnitTest` (an Android library has no plain `:core:test`)
  and `:app:lintMobileDebug` (plain `lintDebug` is ambiguous between the automotive and mobile flavors). The runs
  used the tasks that exist.
