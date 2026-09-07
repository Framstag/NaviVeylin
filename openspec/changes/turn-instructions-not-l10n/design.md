## Context

See proposal.md — Why. Current state that shapes the approach:

- The native JNI bridge (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`, inside the pinned Framstag/libosmscout submodule) generates all turn-instruction text in hardcoded English: `MoveToDescription()` ("Turn left", "Turn sharp left", "Straight on", …), "Start", "Arrive", "Roundabout", "Exit N", "Enter motorway", "Keep <move>", "Leave motorway", plus composed descriptions ("… into <street>", "Take exit N onto <street>", "Enter <motorway>").
- The JNI bridge already exposes the structured fields needed to rebuild the text: `RouteInstruction.turnType` (enum), `streetName`, plus the English `description`/`shortDescription` as fallback. `TurnType` has no `MOTORWAY_CHANGE`/`MOTORWAY_LEAVE` values — the bridge reuses the plain move types (`LEFT`, `RIGHT`, …) for those, so the English short description ("Keep left", "Leave motorway") is the only disambiguator. The roundabout exit count is embedded in the English "Exit N" text only.
- Display paths that consume the native text verbatim:
  - Auto host instruction panel cue + next-step cue: `NavigationTemplateMapper.stepForInstruction` → `Step.setCue(shortDescription ?: description)`.
  - Auto route-description screen: `NavigationTemplateMapper.routeDescriptionRows` → `text = instruction.description`.
  - Phone next-turn overlay: `NextTurnOverlay` → `splitInstruction(description, shortDescription, streetName)` incl. the next-next hint.
- `:core` is a library module with no `res/` yet; both `:app` and `:auto` depend on it, so its resources merge into the app and are shared (label parity). The i18n-l10n spec already pins the "localize native English in the frontend, native unchanged" pattern for POI categories.
- Existing tests: `auto/.../NavigationTemplateMapperTest` (Robolectric, `testCarContext()` resolves strings against real resources), `GermanRenderingTest` (`@Config(qualifiers = "de")`), `GermanTranslationCompletenessTest` in both `:app` and `:auto` (plain JUnit, XML key comparison).

## Goals / Non-Goals

**Goals:**
- German (and English-default) turn-instruction text on phone and Android Auto, rebuilt from structured fields.
- Native layer (libosmscout submodule) unchanged.
- Fallback to native English text for any unrecognized turn type/pattern — never empty/wrong.
- Unit-tested mapping + German completeness for the new `:core` resources.

**Non-Goals:**
- No submodule/JNI changes (no new fields like `exitCount` or an instruction `kind` — the English text is parsed where the bridge omits structure).
- No localization of the route-summary dialog path (`RoutePanelViewModel.parseStepDisplay` parses `RouteEntry.descriptions`, a different native source with its own format; separate concern, routing-summary spec).
- No new locales beyond German.
- No in-app language switcher (device locale is the single source of truth).

## Decisions

### D1: Localizer lives in `:core` as a pure, context-free mapper
`TurnInstructionLocalizer` (object) in `core/src/main/java/com/naviveylin/core/` exposes:

- `fun shortDescription(resolver: StringResolver, instruction: RouteInstruction): String`
- `fun description(resolver: StringResolver, instruction: RouteInstruction): String`

`StringResolver` is a `fun interface` (`fun get(resId: Int, vararg args: Any): String`); a `Context.stringResolver()` extension adapts `Context.getString`. Pure logic + injected resolver keeps the mapper unit-testable without Android (plain JUnit with a fake resolver) and usable from both Compose (`LocalContext`) and Car App Library (`carContext`).

- **Alternative considered**: localizer in `:app` with app resources, auto referencing it. Rejected — `:auto` cannot depend on `:app`; breaks module direction.
- **Alternative considered**: duplicate mapping in `:app` and `:auto`. Rejected — violates the i18n-l10n label-parity requirement and doubles test surface.
- **Alternative considered**: add `exitCount`/`kind` fields to the JNI model. Rejected — requires editing the pinned upstream submodule; the i18n-l10n spec pattern is frontend mapping with native unchanged.

### D2: Instruction strings live in `:core` resources
`core/src/main/res/values/strings.xml` (English default) + `values-de/strings.xml` (German), one key per maneuver text plus composition templates:

- `nav_start`, `nav_arrive`, `nav_roundabout`, `nav_exit_roundabout` (`%1$d`), `nav_enter_motorway`, `nav_leave_motorway`
- `nav_turn_sharp_left` … `nav_turn_sharp_right`, `nav_straight_on` (7 move texts)
- `nav_keep_sharp_left` … `nav_keep_sharp_right`, `nav_keep_straight` (7 keep texts)
- `nav_turn_into` (`%1$s into %2$s`), `nav_keep_onto` (`%1$s onto %2$s`), `nav_exit_onto` (`Take exit %1$d onto %2$s`), `nav_enter_onto` (`Enter %1$s`)

German: "Links abbiegen", "Rechts abbiegen", "Scharf links/rechts abbiegen", "Leicht links/rechts abbiegen", "Geradeaus", "Links/Rechts halten", "Scharf links/rechts halten", "Leicht links/rechts halten", "Geradeaus halten", "Kreisverkehr", "Ausfahrt %1$d", "Autobahn auffahren", "Autobahn verlassen", "Ankunft", "Start", "%1$s in %2$s", "%1$s auf %2$s", "Ausfahrt %1$d auf %2$s", "Auffahren auf %1$s".

- **Alternative considered**: strings in `:app` + `:auto` separately. Rejected — duplication and drift risk; `:core` is the shared home and both modules already depend on it.

### D3: Disambiguation via stable native English markers
The mapper keys on `TurnType` for the unambiguous kinds and inspects the native `shortDescription` for the three cases the bridge does not structure:

1. `shortDescription.startsWith("Keep ")` → keep maneuver, move from `turnType`.
2. `shortDescription == "Leave motorway"` → motorway leave.
3. `shortDescription.startsWith("Exit ")` (with `turnType == ROUNDABOUT_LEAVE`) → exit count parsed as the integer after "Exit ".

Any unrecognized `turnType` or pattern falls back to the native English text. The markers are stable — they are literals in the pinned submodule — and the fallback guarantees no empty/wrong output if upstream ever changes them.

- **Alternative considered**: parse the composed English description. Rejected — the short description is the minimal, stable carrier of the disambiguating information.

### D4: Auto wiring — resolver threaded through the pure mappers
`NavigationTemplateMapper.stepForInstruction` and `routingInfoFromState` gain a `resolver: StringResolver` parameter; `NavigationScreen.buildTemplate` passes `carContext.stringResolver()`. `routeDescriptionRows(carContext, state)` builds the resolver internally. Cue becomes `TurnInstructionLocalizer.shortDescription(resolver, instruction)`; route-description text becomes `TurnInstructionLocalizer.description(resolver, instruction)`; the target fallback uses the localized short description. `streetName` (a proper name) is not translated.

- **Alternative considered**: keep the mappers context-free and localize at the screen layer. Rejected — the cue is built inside `stepForInstruction`; threading the resolver is the minimal change and keeps the mapping testable.

### D5: Phone wiring — localized text into the existing split
`NextTurnOverlay` builds a resolver from `LocalContext` and passes the localized short description to `splitInstruction` while keeping the native English `description` for destination extraction (the " into "/" onto " parse) and the proper-name `streetName`. The generic line renders localized; the destination line stays a proper name. The next-next hint is localized the same way.

- **Alternative considered**: localize the full description and drop the split. Rejected — the next-turn-overlay spec pins the generic/destination line break; keeping the split preserves it.

### D6: Tests
- `core/.../TurnInstructionLocalizerTest` (plain JUnit, fake resolver): every turn type → English text; German via a resolver over `values-de` strings loaded from resources (Robolectric `@Config(qualifiers = "de")` or a hardcoded German fake); keep/leave/exit disambiguation; fallback for unknown types; description composition with/without street.
- `core/.../GermanTranslationCompletenessTest`: mirror of the `:app`/`:auto` XML key-completeness test for the new `:core` resources.
- `auto/.../NavigationTemplateMapperTest`: update `stepForInstruction`/`routingInfoFromState` call sites with `testCarContext().stringResolver()`; assert cue is the localized English text; add a German-qualifier test asserting "Links abbiegen" in the cue.

## Risks / Trade-offs

- [English-marker parsing breaks if upstream changes the literals] → Fallback to native text keeps output correct (English); the pinned submodule makes change unlikely; tests pin the current literals.
- [German translations may overflow the car host panel] → Keep translations concise; verify on device/emulator (config rules require on-device verification for Auto changes).
- [Signature changes ripple through tests] → Update all call sites in the same change; run the full suite.
- [`:core` gains resources for the first time] → Standard Android library res merging; completeness test guards German parity.

## Migration Plan

1. Add `:core` resources (`values/` + `values-de/`) with the instruction strings.
2. Add `TurnInstructionLocalizer` + `StringResolver` + `Context.stringResolver()` to `:core`.
3. Wire `:auto` (`NavigationTemplateMapper`, `NavigationScreen`).
4. Wire `:app` (`NextTurnOverlay`).
5. Tests: localizer (en/de), core completeness, auto mapper updates + German cue test.
6. Verify: `./gradlew :core:test :auto:test :app:testDebugUnitTest`, then `:app:assembleMobileDebug`; on-device German check (phone + Auto).

**Rollback**: additive and non-breaking — English is the fallback locale and the fallback text, so any partial state degrades to the current English behavior, never to missing text.
