# Tasks

## 1. `:core` resources

- [x] Create `core/src/main/res/values/strings.xml` with the English instruction strings (nav_start, nav_arrive, nav_roundabout, nav_exit_roundabout, nav_enter_motorway, nav_leave_motorway, nav_turn_* ×7, nav_straight_on, nav_keep_* ×7, nav_turn_into, nav_keep_onto, nav_exit_onto, nav_enter_onto).
- [x] Create `core/src/main/res/values-de/strings.xml` with the complete German set (same keys).

## 2. `TurnInstructionLocalizer` in `:core`

- [x] Add `core/src/main/java/com/naviveylin/core/TurnInstructionLocalizer.kt`: `StringResolver` fun interface, `Context.stringResolver()` extension, `shortDescription(resolver, instruction)`, `description(resolver, instruction)`, private move/keep text tables, exit-count parse, native-text fallback.

## 3. `:auto` wiring

- [x] `NavigationTemplateMapper.stepForInstruction`: add `resolver: StringResolver`; cue = `TurnInstructionLocalizer.shortDescription(resolver, instruction)`.
- [x] `NavigationTemplateMapper.routingInfoFromState`: add `resolver`; pass through to both `stepForInstruction` calls.
- [x] `NavigationTemplateMapper.routeDescriptionRows`: text = `TurnInstructionLocalizer.description(resolver, instruction)`; target fallback uses localized short description.
- [x] `NavigationScreen.buildTemplate`: pass `carContext.stringResolver()` to `routingInfoFromState`.

## 4. `:app` wiring

- [x] `NextTurnOverlay`: build resolver from `LocalContext`; localized short description into `splitInstruction` (keep native description for destination parse + streetName); localize next-next hint the same way.

## 5. Tests

- [x] `core/src/test/java/com/naviveylin/core/TurnInstructionLocalizerTest.kt`: all turn types (en), German set, keep/leave/exit disambiguation, unknown-type fallback, description composition with/without street.
- [x] `core/src/test/java/com/naviveylin/core/GermanTranslationCompletenessTest.kt`: `:core` values vs values-de key completeness.
- [x] Update `auto/.../NavigationTemplateMapperTest.kt` call sites with `testCarContext().stringResolver()`; assert localized cue; add German-qualifier cue test.

## 6. Verify

- [x] `./gradlew :core:test :auto:test :app:testMobileDebugUnitTest` green.
- [x] `./gradlew :app:assembleMobileDebug` builds (arm64-v8a); `:app:compileAutomotiveDebugKotlin` compiles.
- [x] On-device German check: phone next-turn overlay + Android Auto host panel show German instructions.
