# Route steps for live guidance

## Why

Live guidance still runs the prose path. `JavaNavigationController` builds its engine with
`RouteInstructionAgent<JavaRouteInstruction, JavaRouteInstructionBuilder>`
(`OSMScoutClient.cpp:3973`), and that builder generates English text per position update: `"Turn left into X"`,
`"Keep left onto A7"`, `"Exit 3 onto Y"`, `"Leave motorway"`. The Java enum `TurnType` has no value for a
motorway change or a motorway leave — `MoveToTurnType` collapses both onto a plain move — so **the only thing
that distinguishes them is that generated text**, and the app parses it back:

- `core/TurnInstructionLocalizer.kt` matches `"Keep "` as a prefix and `"Leave motorway"` for equality, and
  chooses the composed wording with the same match;
- the fallback promise of the whole localization capability ("native English when unmapped") exists only
  because the native side writes prose in the first place;
- the "next next" hint crosses JNI as five scalar fields (`nextNextTurnType`, `nextNextDescription`,
  `nextNextShortDescription`, `nextNextDistanceTo`, `nextNextRoundaboutExitNumber`), three of them text;
- matching the live manoeuvre into the route's list compares description strings
  (`navigation/StepIndexSelection.kt:24`).

The structured model is available and unused: `osmscout::client::RouteStepBuilder` satisfies the
`RouteInstructionAgent` contract (`Tests/src/RouteStepModelTest.cpp:277-281`) and fills the live progress facts
(`remainingDistance`, `remainingTime`, `nextNext`) in `RouteStepBuilder.cpp`; `CreateJavaRouteSteps` already
marshals them, `nextNext` included. The engine's builder type is the only thing standing in the way, and it is
a template parameter.

This is the second of three changes. `route-panel-from-steps` gave the route list the step model and the row
builder; this change gives the live path the same model; `retire-instruction-prose` removes what is left.

## What Changes

- **BREAKING (submodule API)**: the navigation listener replaces `onRouteInstructions(RouteInstruction[])` and
  `onNextRouteInstruction(RouteInstruction)` with `onRouteSteps(RouteStep[])` and `onNextRouteStep(RouteStep)`.
  The engine runs `RouteInstructionAgent<osmscout::client::RouteStep, osmscout::client::RouteStepBuilder>`, and
  the dispatch marshals steps through the projection that already ships. The legacy instruction builder
  (`JavaRouteInstructionBuilder`, `CollectCallback`, `MoveToDescription`, `MoveToTurnType`) stops being called.
- **Submodule pin bump** carrying that engine swap and listener extension as one fork commit. This is the only
  change of the three that touches the pin.
- **Sibling consumer**: JavaScout consumes the same fork and loses its instruction callbacks with this pin.
  Its adaptation is explicitly out of scope (owner directive); `retire-instruction-prose` removes the leftover
  instruction types, after JavaScout's own change.
- The app consumes steps end to end: the shared navigation state carries the step list and the step ahead, every
  live surface resolves its text through the row builder `route-panel-from-steps` introduced, and the
  manoeuvre-based icon replaces the turn-type-based one.
- `nextNext` becomes one nested step instead of five scalars, so a "next next" hint carries its own kind, exit
  ordinal and circulation direction as values.
- Step matching for the route's highlight stops comparing text: the live step is matched by its distance from
  the route start **and** its kind, because one node can produce two instructions (a turn and a motorway entry).
- The instruction-based localizer and its prefix matching are deleted; after this change `app`, `auto` and
  `core` contain no reference to `RouteInstruction`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `turn-instruction-localization` — **MODIFIED** "Instruction text is rebuilt from structured fields" (from the
  step model), "All native instruction kinds are covered" (motorway change and leave are now told apart by the
  step's kind, not by matching English); **REMOVED** "Fallback to native text" (there is no native guidance text
  to fall back to).
- `i18n-l10n` — **MODIFIED** "Native-generated navigation text is localized at the frontend" (the native layer
  no longer produces guidance text).
- `osmscout-jni` — **ADDED** "Live guidance publishes route steps"; **MODIFIED** "A route instruction carries the
  roundabout exit ordinal", "…the roundabout circulation direction", "The next-next hint carries its own exit
  ordinal" and "An unbuildable instruction never reaches a listener as null", each re-pointed at the step model.
- `next-turn-overlay` — **MODIFIED** "Turn type icon" (chosen from the step's manoeuvre kind and move) and
  ""Next next" hint" (the hint is a nested step).
- `navigation-controller` — **MODIFIED** "Navigation state exposed as StateFlow" (the state updates on the step
  callback).
- `rerouting-visual-feedback` — **MODIFIED** "Rerouting state is exposed in navigation state" (a completed
  reroute is signalled by the step callback).

Unchanged on purpose: `navigation-state-display`, `nav-hints-layout`, `immediate-turn-instruction`,
`navigation-ongoing-notification` and `nav-hud-visuals` describe content, layout and flags whose contract does
not change; they are re-verified by their existing cases against the new state type, and named as exclusions
where a case cannot be re-pointed without a device.

## Impact

**Modules and files**

| Area | File |
|---|---|
| submodule (fork commit) | `libosmscout-client-java/java/.../NavigationListener.java`, `libosmscout-client-java/src/OSMScoutClient.cpp` (engine wiring `:3973`, dispatch `:4273-4300`, `JavaNavigationController`) |
| `:osmscout-client-java` | `NaviveylinCarAppService`-facing wrappers only if a listener adapter exists here; no override file should be needed |
| `:core` | `NavigationState.kt` (step list + step ahead), the row builder's public entry, deletion of `TurnInstructionLocalizer.kt` |
| `app/navigation` | `NavigationEngine.kt` (listener, state, step index), `StepIndexSelection.kt`, `RoundaboutInstructionDiagnostics.kt`, `VoiceGuidance.kt` untouched (samples, not text) |
| `app/ui/navigation`, `app/service`, `app/ui/map` | `NextTurnOverlay.kt`, `NavigationArrowRenderer.kt`, `NavigationDetailsOverlay.kt`, `NavigationNotificationContent.kt`, `CarSessionSurface.kt` |
| `auto` | `NavigationTemplateMapper.kt`, `ManeuverGlyphs.kt`, `RouteDescriptionScreen.kt`, `NavigationScreen*` |
| docs | `guidelines/Design.md` §5 (native boundary) and §8 (car/cross-variant wiring), `guidelines/UI.md` §7/§8 (live guidance), `guidelines/Build.md` §10b (judging the car surface) |

**Previous specifications changed**: `turn-instruction-localization`, `i18n-l10n`, `osmscout-jni`,
`next-turn-overlay`, `navigation-controller`, `rerouting-visual-feedback`.

**Guidelines**: `Design.md` §5 owns the "patch the submodule in one place" rule this pin bump follows;
`Design.md` §8 and `UI.md` §3/§3a own the host-thread and fault-isolation rules the dispatch must keep
(every host-facing send stays guarded, no screen releases a surface).

**Native/JNI**: **submodule patch** — one fork commit (engine swap + listener methods + dispatch), minimal and
upstreamable, followed by a pin bump in the app. No local override in `:osmscout-client-java`.

**Rollback**: revert the app-side commits and pin the submodule back to its previous commit; nothing persists
the new state, so a rollback loses no user data. The two route-calculation paths that publish `RouteEntry.steps`
are untouched, so a rollback leaves the route list working.

**Additive or breaking**: **breaking** for the submodule's navigation listener (JavaScout, by owner directive)
and additive for the app's own surfaces beyond the type change.
