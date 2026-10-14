# Proposal

## Why

A roundabout exit is the one manoeuvre whose icon answers a question the driver actually asks — *which* exit —
and the app cannot answer it anywhere. Every roundabout surface shows the same abstract glyph: the phone's
next-turn overlay, the route step list, the full-screen route description, the Android Auto instruction
panel, the car route-description list and the car rail-widget large icon. Native computes the exit ordinal
and the circulation direction, then flattens both into an English sentence (`"Exit 3"`) that only the *text*
path reads back; the icon path never sees either value.

**Root cause and evidence** (one root cause, one fix — see "Single fix" below):

| Evidence | What it shows |
|---|---|
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:2272` | `struct JavaRouteInstruction` has `turnType`/`streetName`/`description`/`shortDescription` and **no field** for the exit ordinal or the direction |
| `.../OSMScoutClient.cpp:2589-2598` | `OnRoundaboutEnter` ignores its description (`/*roundaboutEnterDesc*/`); `roundaboutEnterDesc->IsClockwise()` is discarded |
| `.../OSMScoutClient.cpp:2601-2616` | `OnRoundaboutLeave` reads `roundaboutLeaveDesc->GetExitCount()` but writes it only into `description`/`shortDescription`; `IsClockwise()` is not read |
| `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt:46-47` | both roundabout types map to the **clockwise** constants unconditionally — wrong on a counter-clockwise roundabout (UK, Ireland, Japan, Australia), invisible only because the icon never follows the type |
| `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt:86-88` | the maneuver always sets the app's own `CarIcon`; `setRoundaboutExitNumber` has **0 occurrences** repo-wide |
| `core/src/main/java/com/naviveylin/core/ManeuverSymbols.kt:276-288` | `drawRoundaboutLeave` draws a fixed ring plus an exit arrow. No number, and no parameter that could carry one |
| `app/src/main/java/com/naviveylin/ui/navigation/NavigationArrowRenderer.kt:33-38, 496-550` | `NavSymbol.Roundabout(exitCount, exitAngles, selectedExit, entryAngle)` is implemented, 55 lines of real drawing — and has **no construction site anywhere**. The `roundabout-renderer` capability is unreachable from every screen |

No test on HEAD asserts the exit number, so the red-first case is added by this change as its first task:
`roundaboutLeaveCarriesExitNumber` asserts that the host `Maneuver` for a roundabout-leave instruction carries
the exit ordinal. It is red on HEAD without any new API, because `Maneuver.getRoundaboutExitNumber()` already
exists and returns `0`.

**The requirement the defect contradicts:** `auto/navigation-view` — *Next turn maneuver in host instruction
panel* (the panel shows a turn-type icon; for a roundabout exit that icon does not show which exit). Its phone
twin is `next-turn-overlay` — *Turn type icon*.

**Single fix.** One fix follows from the root cause: **carry the roundabout exit ordinal and the circulation
direction across the JNI bridge as structured fields.** Every listed surface is a consumer of that one value.
The choice of *who draws the glyph* — the app's own bitmap with the number drawn in, or the host's own
numbered roundabout reached through the number-bearing maneuver type — is a presentation decision on top of
that single fix, not a second root cause. That decision was taken deliberately during exploration and is
recorded under "What Changes": the Android Auto instruction panel and rail widget use the host's glyph, every
bitmap surface and the phone use the app's. Because the root cause is single, the entry is eligible for the
fix loop rather than skipped.

## What Changes

- **Bridge (`osmscout-jni`).** `RouteInstruction` gains `roundaboutExitNumber` (the ordinal of the exit taken,
  1-based) and `roundaboutClockwise`. `OnRoundaboutEnter`/`OnRoundaboutLeave` fill them from
  `IsClockwise()`/`GetExitCount()`. The field is named for the ordinal it holds, not `exitCount`: upstream's
  `RoundaboutLeaveDescription::GetExitCount()` returns the ordinal of the exit taken (it is
  `roundaboutCrossingCounter`, `RoutePostprocessor.cpp:719,738`), while `NavSymbol.Roundabout.exitCount` in
  Kotlin means the ring's *total* exits and `CrossingWaysDescription::GetExitCount()` means the exits at one
  crossing node. Three different quantities, two of them already called `exitCount`.
- **Maneuver types (`auto/navigation-view`).** The maneuver type is chosen per instruction: a roundabout exit
  with a known ordinal uses `TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW`/`_CCW` with `setRoundaboutExitNumber(n)` and
  **omits** `setIcon`, so the host draws its own numbered roundabout. An ordinal of `0` (reachable: the counter
  stays 0 unless a crossing way reports more than one exit, `RoutePostprocessor.cpp:738`) keeps today's
  `TYPE_ROUNDABOUT_EXIT_CW`/`_CCW` with the app's icon. `TYPE_ROUNDABOUT_ENTER_CW`/`_CCW` become direction-aware.
- **Numbered glyph (`roundabout-renderer`).** A new symbol, `NavSymbol.RoundaboutExit(exitNumber)`, draws the
  ring with the exit number in the middle — the same shape the head unit shows. `NavSymbol.Roundabout` stays
  as it is and stays unwired: its `exitCount` is the ring's total, which no part of the pipeline produces
  (native computes per-exit bearings only inside `HandleMiniRoundabout`, as a sort key it then discards).
- **Phone (`next-turn-overlay`, `navigation-status-details`).** The four phone surfaces that build a turn
  symbol — the next-turn row, the next-next row, the route panel's step list and the full-screen route
  description — use the numbered symbol for a roundabout-leave instruction. Two of them carry the requirement
  (`next-turn-overlay` *Turn type icon*, `navigation-status-details` *Route description list*); the route
  panel's own step list is specified only as "the route summary component contains the instructions"
  (`route-panel-ui`) and by `route-summary-dialog` *Scrollable step list*, neither of which names the step
  icon, so those two capabilities change in code with no spec delta.
- **Car bitmaps (`auto-navigation-hints`, `auto/navigation-view`).** The car route-description list rows and
  the rail-widget/notification large icon render the numbered glyph. These two take a bitmap, not a
  `Maneuver`, so the host cannot supply the number there.
- **Localization (`turn-instruction-localization`).** The localized roundabout-exit text reads the ordinal
  from the field, and the `"Exit N"` text parse is deleted. The localizer's remaining native-text
  dependencies are the motorway-change and motorway-leave markers, which have no field and keep their parse.
- **Parity.** The phone/Android Auto icon difference is deliberate and is recorded in `guidelines/UI.md` §1's
  parity decision table: the label stays identical (`"Exit 3"` / `"Ausfahrt 3"` on both), the glyph source
  differs only where the car host draws the maneuver icon itself.

**Additive, not breaking.** Two new fields; the existing 5- and 10-argument `RouteInstruction` constructors are
kept and delegate, so no caller compiled against the old arity breaks. An unknown ordinal falls back to
today's exact behaviour. **Rollback:** revert the Kotlin commit and the submodule gitlink bump together —
nothing is persisted, no JSON format, protocol or asset changes, so there is no migration to undo.

## Capabilities

### New Capabilities

None. The bridge contract belongs to the existing `osmscout-jni` capability and the symbol to the existing
`roundabout-renderer`; neither is a near-duplicate of a new name.

### Modified Capabilities

- `osmscout-jni`: a route instruction carries the roundabout exit ordinal and the circulation direction as
  structured fields instead of only inside the native English short description.
- `roundabout-renderer`: the numbered roundabout-exit symbol and its API.
- `next-turn-overlay`: the turn-type icon shows the roundabout exit ordinal.
- `navigation-status-details`: the route description list step icon shows the roundabout exit ordinal.
- `auto/navigation-view`: a roundabout exit reaches the host as the number-bearing maneuver type with the
  host's own glyph; an unknown ordinal falls back without throwing; the route-description screen's row icon
  shows the ordinal.
- `auto-navigation-hints`: the car hint's large icon shows the roundabout exit ordinal.
- `turn-instruction-localization`: the roundabout exit count comes from the instruction's structured field,
  not from parsing the native English text — a one-clause change to the scenario's WHEN only, no THEN changes,
  because the rendered text is unchanged.

`roundabout-renderer` *Exit position reflects actual geometry* is deliberately left untouched: its data-source
claim is a known false premise (`RouteInstruction` carries no angle field, and native computes per-exit
bearings only as a sort key it discards in `HandleMiniRoundabout`), but it is not the requirement this defect
contradicts, and a `fix-*` delta does not rewrite unrelated text. It is recorded under "Out of scope" instead.

## Impact

**Scope.** Both variants plus the shared bridge — the data path is variant-neutral, the glyph source is not.
Not an Android-Auto-only and not a phone-only change.

**Native / JNI.** A **submodule patch, minimal and upstreamable**, in the pinned libosmscout submodule — not a
local override. `libosmscout-client-java/src/OSMScoutClient.cpp` and
`libosmscout-client-java/java/com/framstag/libosmscout/client/RouteInstruction.java` are both submodule files;
none of `:osmscout-client-java`'s five overrides (`OSMScoutClient`, `OSMScoutClientBuilder`, `BasemapManager`,
`MapDownloadManager`, `AvailableMapEntry`) is touched. The submodule gitlink is bumped after the submodule
commit, per `guidelines/Design.md` §5.

**Code.**

| Module | File | Change |
|---|---|---|
| submodule | `.../src/OSMScoutClient.cpp` | struct fields; both `OnRoundabout*` handlers; the `GetMethodID` signature and `CreateJavaRouteInstruction`'s `NewObject` argument list, in lockstep |
| submodule | `.../java/.../RouteInstruction.java` | two fields, new full constructor, old constructors delegate |
| `:core` | `ManeuverSymbols`, `TurnInstructionLocalizer` | numbered roundabout-exit glyph, cached per ordinal; ordinal read from the field |
| `:app` | `NavigationArrowRenderer` | `NavSymbol.RoundaboutExit` and its drawing |
| `:app` | `NextTurnOverlay`, `RouteSummary`, `NavigationDetailsOverlay`, `NavigationNotificationService`, `NavigationNotificationBuilder` | numbered symbol for a roundabout exit; the bitmap provider receives the instruction instead of the turn type |
| `:auto` | `NavigationTemplateMapper`, `ManeuverGlyphs`, `NavigationScreen`, `NavigationSession`, `RouteDescriptionScreen` | per-instruction maneuver type, optional icon, numbered bitmap for the two bitmap consumers |
| `:app` tests | `FakeOSMScoutClient` and 12 files constructing `RouteInstruction` | follow the added constructor form |

**Android components.** No manifest, resource-qualifier, permission or Gradle dependency change. No new
drawable: the number is drawn, not shipped as an asset, which keeps the icon leaf of the stylesheet contract
untouched.

**Specs changed by this proposal.** `osmscout-jni`, `roundabout-renderer`, `next-turn-overlay`,
`navigation-status-details`, `auto/navigation-view`, `auto-navigation-hints`, `turn-instruction-localization`.
Four capabilities change in code but **not** in spec, because no requirement of theirs names the thing that
changes: `route-panel-ui` and `route-summary-dialog` (the route panel's step icon),
`navigation-ongoing-notification` (the phone notification's large icon — that capability specifies the
notification's *text* content only), and `i18n-l10n` (its *German roundabout exit* scenario requires the German
wording, which this change preserves).

**Out of scope.** `roundabout-renderer` *Exit position reflects actual geometry* keeps its false data-source
claim, and `NavSymbol.Roundabout` keeps no call site. Both need the ring's exit geometry, which is an upstream
feature — the per-exit bearings for the ring, and the Android Auto `TYPE_ROUNDABOUT_ENTER_AND_EXIT_*_WITH_ANGLE`
path. Named here so the next reader does not mistake this change for having fixed it.

**Guidelines affected.** `guidelines/Design.md` §5 (the submodule patch, the one-place rule, the
positional-constructor rule for `RouteInstruction`), `guidelines/UI.md` §1 (the parity rule and its decision
table gain the deliberate glyph-source deviation) and §8 (the numbered icon must stay legible at the minimum
overlay icon size), `guidelines/Build.md` §12 (native rebuild and the submodule sync) and §6 (test
constraints). `guidelines/MapRendering.md` is **not** affected — maneuver symbols are drawn by Compose and
Canvas, not by the stylesheet or the map pipeline.

**Dependencies.** None. Android Auto's `Maneuver.Builder.setRoundaboutExitNumber` and
`setRoundaboutExitAngle` are already available in the pinned `androidx.car.app:app:1.7.0`.
