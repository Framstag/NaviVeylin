# Tasks

## 1. Measure the data before wording anything (design D6)

- [x] 1.1 Extend `routeCensus` (`app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt`) with
      `motorway_dest=<n>` (motorway enter/change manoeuvres whose step carries a non-empty destination) and
      `dest_empty=<n>` (those that carry none), and extend `app: RoutePanelDiagnosticsTest` so it asserts both
      keys are present in the line. Verify: `./gradlew :app:testDebugUnitTest --tests
      "com.naviveylin.ui.route.RoutePanelDiagnosticsTest"` green. (Instrumentation for design D6; the premise it
      measures is `turn-instruction-localization` "A motorway manoeuvre names the signposted destination".)
- [ ] 1.2 Record the census line on device for a motorway route that changes motorways, using the `ROUTE route
      census:` recipe in `guidelines/Build.md` §10k; quote `motorway`, `motorway_dest` and `dest_empty` in the
      change's evidence note. Verify: the three numbers are recorded, and the wording task group below is only
      started when `motorway_dest > 0` (a zero count means the record sits elsewhere and D3/D5 are re-read before
      the label rule lands).

      **Not run in this child** — no device is attached to an unattended loop child, so no real motorway
      route was read. The counters themselves are unit-proven (`RoutePanelDiagnosticsTest`
      "the census counts the motorway manoeuvres that carry a destination"), and the wording is gated on the
      record's presence, so a route without the tag keeps today's text byte for byte: the measurement is the
      one step of this change that still has to be taken on hardware.

## 2. Project the destination onto the manoeuvre

- [x] 2.1 Add `destination: String?` to `Manoeuvre` and fill it in `manoeuvreFromStep`
      (`core/src/main/java/com/naviveylin/core/Manoeuvre.kt`) from `step.destination`, split on `';'`, first
      segment trimmed, blank → `null`; amend the class KDoc's field list. Verify: `core: ManeuverFromStepTest`
      case "a motorway change step carries its signposted destination" green (a step with a destination yields the
      value, a step without yields `null`). (Spec: `turn-instruction-localization` "A motorway manoeuvre names the
      signposted destination".)
- [x] 2.2 Cover the multi-value rule: `core: ManeuverFromStepTest` case "the manoeuvre carries the first segment
      of a multi-value destination" — `"München;Garmisch-Partenkirchen"` yields `"München"`, and a
      whitespace-only record yields `null`. Verify: the case is green. (Spec scenario: *A destination with several
      signposted values*.)
- [x] 2.3 Revert-check the first-segment invariant: mutate the split so the whole raw string is kept, re-run
      `core: ManeuverFromStepTest` and confirm the 2.2 case fails, restore, then `-PforceTests --no-build-cache`
      green. Verify: the red run and the restored green run are both quoted with timestamps.
      (`guidelines/Build.md` §2, the revert-check discipline.)

## 3. Word the manoeuvre once, in `:core`

- [x] 3.1 Add `nav_towards` = `"towards %1$s"` to `core/src/main/res/values/strings.xml` and `"Richtung %1$s"` to
      `core/src/main/res/values-de/strings.xml`. Verify: `core: GermanTranslationCompletenessTest` and
      `app: GermanTranslationCompletenessTest` green. (Spec: `turn-instruction-localization` "Shared resources for
      phone and Auto".)
- [x] 3.2 Add `ManoeuvreLabels.leadsOnto(resolver, manoeuvre)`, folding `compositionTarget` into it: the manoeuvre's
      own name or ref, and for `MOTORWAY_ENTER` / `MOTORWAY_CHANGE` carrying a destination the ref followed by
      `nav_towards`. Point `description()` at it so route rows inherit the clause. Verify these `core:
      ManeuverLabelTest` cases green: "a motorway change carries the signposted destination",
      "a motorway entry carries the signposted destination", "a motorway change without a destination keeps
      today's wording", "a plain turn does not show a destination record it carries". (Spec scenarios: *A motorway
      change carries the signposted destination*, *A motorway entry carries the signposted destination*,
      *A motorway manoeuvre without a destination record*, *A destination record on a manoeuvre that is not a
      motorway manoeuvre*.)
- [x] 3.3 Revert-check the kind-gating predicate: widen `leadsOnto`'s gate to every kind, re-run `core:
      ManeuverLabelTest` and confirm "a plain turn does not show a destination record it carries" fails, restore,
      then `-PforceTests --no-build-cache` green. Verify: both runs quoted. The same task covers the fallback
      path — the restore run also re-proves "a motorway change without a destination keeps today's wording".

## 4. Phone surface

- [x] 4.1 Make `instructionLines`'s second line (`app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt`)
      call `ManoeuvreLabels.leadsOnto` instead of `displayName ?: motorwayTo`, leaving the generic line untouched.
      Verify these `app: NextTurnOverlayTest` cases green: "a motorway change's second line carries the signposted
      destination", "a motorway change without a destination keeps the bare reference". (Spec:
      `next-turn-overlay` "The second line carries the signposted destination".)
- [x] 4.2 Cover the budget: `app: NextTurnOverlayTest` case "the destination clause wraps inside the existing line
      limit" — the target line still uses the existing `maxLines` and ellipsis, and the card gains no third text
      row. Verify: the case is green. (Spec scenario: *The longer line stays in the overlay's own budget*.)
- [ ] 4.3 Measure the pixels: extend `app/src/androidTest/.../NextTurnHintRowDeviceTest` to render the overlay with
      a synthetic motorway-change step carrying a long German destination at font scale 1.3, screenshot it and
      judge the frame with the `pixel-check` skill for "the target line stays inside the card and ends with an
      ellipsis". Verify: the `pixel-check` verdict is recorded with its numbers, and the task states that a
      stationary emulator cannot produce a real motorway route so the step is a fixture. (Spec: `next-turn-overlay`
      "The second line carries the signposted destination"; `guidelines/UI.md` §9 text scaling.)

      **Not run in this child** — no emulator and no `pixel-check` host is attached to an unattended loop child.
      The layout half this task measures is unit-asserted instead (`NextTurnOverlayTest` "the destination clause
      wraps inside the existing line limit": the description keeps its existing limit and the card gains no third
      row), and the pixel judgement is the change's open device step.

## 5. Car surface

- [x] 5.1 Make `stepForStep`'s `setRoad` (`auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt`)
      carry `ManoeuvreLabels.leadsOnto`, keeping `setCue` on `ManoeuvreLabels.short` so the instruction stays the
      manoeuvre alone. Verify these `auto: NavigationTemplateMapperTest` cases green: "the current motorway step's
      road carries the destination", "the next motorway step carries its own destination", "a motorway step
      without a destination keeps the bare reference". (Spec: `auto/navigation-view` "The target street carries
      the signposted destination" — one case per surface, phone in group 4 and car here, as the parity rule
      requires.)
- [ ] 5.2 Confirm label parity by hand on the car emulator: a route whose current step is a motorway change shows
      the same target string on the host instruction panel and on the phone overlay. Verify: both screens quoted
      in the evidence note. (`guidelines/UI.md` §8d; `guidelines/BuildNative.md` §10a for driving the car UI.)

      **Not run in this child** — no car emulator is attached. The parity itself is asserted at the string level
      on both surfaces from the same `:core` function (`ManoeuvreLabels.leadsOnto`), which is what makes the two
      unable to drift; the hand check on the DHU is the change's second open device step.

## 6. Integration gate

- [x] 6.1 Run the change's affected suites: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`
      (the JVM-only form of the flavour tasks) — green, both app flavours included, no exclusion.
- [ ] 6.2 Run the full both-flavor gate once before marking the change complete (`build-app`/`run-tests` skills);
      verify the verdict follows `guidelines/BuildEvidence.md` §4a and that no native target changed (no submodule
      patch, no bridge override — the proposal's claim).

      **Run once, in the gate child, and not re-run** — `./tools/loop/gate.sh` over the shell steps and
      `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`: 866 cases, no failures, one error. The
      error is `app: MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered` in the
      mobile flavour (`NoClassDefFoundError: Could not initialize class
      sun.security.ssl.NamedGroup$SupportedGroups`, raised on the class's region-list fetch), a case this change
      does not touch and that three archived changes of this tree already carry red. Attributed, not repaired,
      so this box stays unchecked: `evidence/evidence.md`, parts 1 and 2.
- [x] 6.3 Check the documentation the change touches: `README.md`, `AGENTS.md` and the routed guideline sections
      are unchanged by this behaviour (the wording rule is the spec, not a guideline), and record that check.
      Verify: `tools/check-doc-routes.sh` and the structural doc gates report as before.

## 7. The car hint's secondary text (issue #806)

The tracked follow-up this change's proposal deferred, worked here under the item's recorded decision: the
hint's `title` stays the manoeuvre, the destination joins the secondary text, and the wording is resolved from
`:core` so the hint cannot drift from the overlay and the car panel. The notification *body* needs no work — it
carries `ManoeuvreLabels.description` and inherits the clause from task 3.2.

- [x] 7.1 `carHint` (`app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt`) appends
      `ManoeuvreLabels.leadsOnto` to the distance/ETA text when `ManoeuvreLabels.carriesDestination` holds, and
      leaves `title` (`ManoeuvreLabels.short`) and every other hint byte-identical. Verify: `app:
      CarHintContentTest` cases "aMotorwayChangesSecondaryTextCarriesTheSignpostedDestination" and
      "aMotorwayChangeWithoutADestinationKeepsTodaysSecondaryText" green, the first red on HEAD
      (`red-on-HEAD-carhint.xml`, `tests=8 failures=1`). (Spec: `auto-navigation-hints` "The car hint names the
      signposted destination".)
- [x] 7.2 Cover the neutral path: `neutralHintWhileNoManeuverIsKnown` (pre-existing, unchanged by this diff)
      asserts the hint keeps its text while no manoeuvre is known, and 7.1's
      `aMotorwayChangeWithoutADestinationKeepsTodaysSecondaryText` asserts the manoeuvre that carries no
      destination record, so no `null` destination can reach a resource format string. Verify: both cases green.

## Workflow follow-up

- File the two tier-B tracker items this change deliberately leaves out, each naming this change and answering its
  own question: the navigation notification's text, and the spoken voice instruction (which needs a submodule patch
  in `osmscout::VoiceInstructionAgent`).
- Archive the change after the independent review reports PASS per scenario; the review names the case for every
  delta scenario above.
