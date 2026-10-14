# Design

## Context

Motivation, root cause and evidence are `proposal.md` — Why; the requirements this design must satisfy are the
seven spec deltas under `specs/`. Only the current state that shapes the approach is repeated here.

```
  native (submodule)                          Java bridge            Kotlin
  ─────────────────                          ───────────            ──────
  RoundaboutLeaveDescription.GetExitCount()  ┐
  RoundaboutEnter/LeaveDescription           ├─ flattened into      no field        the UI cannot
    .IsClockwise()                           ┘  English prose        exists         see either value
  RoutePostprocessor: roundaboutCrossingCounter == ordinal of the exit taken
  RoutePostprocessor: HandleMiniRoundabout computes per-exit bearings, then discards them
```

Constraints that decide the shape:

- `RouteInstruction.java` and `OSMScoutClient.cpp` are **submodule files**; `:osmscout-client-java` compiles the
  submodule's Java sources *except five overridden names* (`OSMScoutClient`, `OSMScoutClientBuilder`,
  `BasemapManager`, `MapDownloadManager`, `AvailableMapEntry`) — `guidelines/Design.md` §5.
- JNI-bridge Java types are **positional-argument only**; `RouteInstruction` has 5- and 10-argument forms, and a
  stale caller compiled against an old arity surfaces as `NoSuchMethodError`, not a compile error — §5.
- The submodule is pinned; uncommitted submodule changes are built by neither CI nor a fresh clone, and one
  session owns its push — §5.
- One artwork source for the maneuver symbols, so phone and car cannot drift:
  `:core` `ManeuverSymbols` is already the shared geometry behind the phone `NavigationArrowRenderer`, the car
  `ManeuverGlyphs` and the notification's car large icon.
- The maneuver bitmap is 48 px (`ManeuverSymbols.BITMAP_SIZE`); the car host scales a `CarIcon` into a 128 dp
  box; the phone overlay draws the icon at 64 dp and step icons at 28 dp.

## Goals / Non-Goals

**Goals:**

- One place that knows *which* exit a roundabout instruction takes, read by every surface.
- No surface guesses: an unknown exit is rendered as today's plain arrow, never as an invented or zero number.
- The Android Auto instruction panel and rail widget get the host's own numbered roundabout where the host can
  draw it; every bitmap surface and the phone get ours.
- The change stays a `fix-*`: the submodule patch is minimal and upstreamable.

**Non-Goals** (design-level boundaries, beyond the proposal's Out-of-scope):

- Computing or carrying exit **angles**, and the ring's **total** exit count. Both need new geometry work
  (per-exit bearings for a ring roundabout) and neither is needed to show an exit number.
- Giving `NavSymbol.Roundabout` a call site. It needs the ring total to draw honest markers; feeding it the
  ordinal would draw a 3-exit ring for a 5-exit roundabout.
- Any change to how the localized *text* reads; only the number's provenance moves.

## Decisions

### D1 — the exit ordinal and the circulation direction cross the boundary as two fields

Alternatives the evidence eliminates:

- **Read the ordinal out of the native English text.** `TurnInstructionLocalizer.exitCount()` already does
  this and returns `0` for anything unparseable, which is indistinguishable at the call site from "no exit";
  and the text carries **no circulation direction at all**, which the Android Auto maneuver type needs. Not a
  candidate for the source once a direction is required.
- **Decode the ordinal from `TurnType`.** Impossible: `TurnType` has exactly `ROUNDABOUT_ENTER` and
  `ROUNDABOUT_LEAVE`. There is one bit of information; the ordinal is not in it.
- **Derive the ordinal from geometry on the Kotlin side.** No source exists. `RoundaboutLeaveDescription`
  carries only a counter and a boolean, and the per-exit bearings are computed inside
  `HandleMiniRoundabout` purely as a sort key and then dropped (`RoutePostprocessor.cpp:706-712`).

Chosen: `int roundaboutExitNumber` and `boolean roundaboutClockwise` on the route instruction, filled in
`OnRoundaboutEnter`/`OnRoundaboutLeave` from `IsClockwise()`/`GetExitCount()`. The counter that feeds
`GetExitCount()` is incremented once per exit passed (`:719`) and accumulated across intermediate nodes
(`:738`), so it *is* the 1-based ordinal in circulation order.

### D2 — the field is named `roundaboutExitNumber`, not `exitCount`

Three different quantities already carry an `exitCount` name, and two of them disagree:

| Name | Means |
|---|---|
| `RoundaboutLeaveDescription::GetExitCount()` | ordinal of the exit **taken** (1..N) |
| `CrossingWaysDescription::GetExitCount()` | exits at **one crossing node** |
| `NavSymbol.Roundabout.exitCount` (Kotlin) | **total** exits on the ring (2..12) |
| Android Auto `Maneuver.getRoundaboutExitNumber()` | ordinal of the exit **taken** |

Naming the new field after the last of those keeps it consistent with the consumer that reads it and removes
the one collision that would silently swap "which exit" for "how many exits".

### D3 — a minimal upstreamable submodule patch, not a local override

- **Local override in `:osmscout-client-java`.** Impossible as a whole: the C++ side (`OSMScoutClient.cpp`)
  lives only in the submodule and has no override mechanism at all. Half the change has nowhere else to go, so
  an override for the Java half would split one data type across two definitions of `RouteInstruction`.
- **Work around it in Kotlin.** `guidelines/Design.md` §5: a defect behind an unchanged JNI contract is fixed
  upstream as a minimal patch rather than worked around, because a workaround has to guess the library's
  internal semantics. Here the contract itself is what is missing, so the contract is extended.

Chosen: patch the two submodule files, commit them, push, then bump the gitlink — one session, per §5 and the
`update-to-current-libosmscout-master` skill's discipline.

### D4 — keep the existing constructors and add the fields, rather than replace the full constructor

Per §5 (positional-argument only, update every call site in the same change), the two fields are appended to
the **full** constructor and the 5- and 10-argument forms stay, delegating with "no exit" defaults. Rationale:
twelve test files and `FakeOSMScoutClient` construct instructions positionally, and keeping the old arities
means this change cannot break them silently by leaving stale bytecode that then throws `NoSuchMethodError` at
runtime. The new arity is used by the bridge only.

The default for `roundaboutClockwise` on the delegating forms is `true` (clockwise), which is the behaviour
the maneuver mapping had before this change — so an instruction built without the field behaves exactly as it
did on HEAD.

### D5 — the bridge dispatches nothing rather than an array with holes

`CreateJavaRouteInstruction` already returns `nullptr` when the constructor method id is missing, and
`DispatchRouteInstructions` allocates the array at full size and fills only the entries it built — so a
constructor-signature mismatch today delivers an array whose tail is `null`, and the first dereference of
`instructions[0]` in `NavigationEngine.onRouteInstructions` faults there instead of at the bridge. The
signature string is edited by hand in the same commit as the Java constructor, so this is a real failure mode
for this change.

Chosen: when the constructor method id is missing the bridge reports no instructions at all instead of an array
with holes. This is a two-line change, strictly smaller than what it replaces, and it converts a silent null
leak into an observable absence.

### D6 — the car host draws the numbered roundabout; the app draws the rest

For a roundabout exit with a known ordinal the maneuver is built as the number-bearing
`TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW`/`_CCW` with `setRoundaboutExitNumber(n)` and **no** `setIcon`, so the host
renders its own numbered glyph. With no ordinal it stays `TYPE_ROUNDABOUT_EXIT_CW`/`_CCW` with the app's icon —
which is exactly today's behaviour, so the fallback is the pre-change path.

Why not keep our icon and set the number as well: unverified. `Maneuver.getIcon()` is documented as optional
and "when not set may be shown by a generic image representing the specific maneuver", and Organic Maps added
its own `ic_roundabout_exit_[1-12]` drawables selected by exit number when it did the same thing — evidence
that a custom icon wins over the host's numbered glyph, but not proof of the precedence rule. See Open
Questions; the fallback if the host turns out to draw the number anyway is to keep our icon and the number,
which satisfies the same requirement.

The number-bearing types carry the constraint that drives the guard: `Maneuver.Builder.build()` throws
`IllegalArgumentException` when the type includes an exit number and none was set, and
`setRoundaboutExitNumber` requires a value greater than zero. `roundaboutCrossingCounter` can be `0` — it only
grows when a crossing way reports more than one exit (`RoutePostprocessor.cpp:738`) — so the guard tests the
ordinal, not the turn type.

### D7 — the circulation direction selects the clockwise or counter-clockwise constant

`maneuverTypeFromTurnType` currently maps both roundabout types to the `_CW` constants unconditionally.
`TurnType` cannot express the direction, so the mapping has to see the instruction: the selection of the
maneuver constant moves into the per-instruction path, and the existing `maneuverTypeFromTurnType` keeps
serving the non-roundabout turn types unchanged. Its two tests (`maneuverType_roundaboutEnter`,
`maneuverType_roundaboutLeave`) assert the old unconditional `_CW` result and are updated in the same task.

### D8 — one shared numbered glyph, keyed by the ordinal

`NavSymbol.RoundaboutExit(exitNumber)` in the phone renderer, and a matching `ManeuverSymbols` entry for the
bitmap consumers, drawn by one implementation in `:core` (spec: `roundabout-renderer` — Phone and car
roundabout symbols stay one artwork). Drawing, not an asset: the number is unbounded in principle, so a
drawable per ordinal would be an asset-per-number and would trip the icon-leaf packaging contract
(`MapRendering.md` §16a), which this change deliberately leaves untouched.

Cache key moves from `TurnType` to `(TurnType, exitNumber)`, bounded, because
`car-host-fault-isolation` requires a template asset to be reused while its state is unchanged — a cache keyed
on the turn type alone would hand the previous roundabout's digit to the next one.

The digit is drawn with a `Paint` centred on the ring (`textAlign = CENTER`, vertically centred on the font
metrics) at a size derived from the ring radius, so a two-digit ordinal fits inside the same ring. No font
metrics assumption is baked into a constant: the size follows the bitmap size, which is what the car host
rescales.

### D9 — the bitmap consumers pass the instruction, not the turn type

`Row.setImage` and `Maneuver.setIcon` need different things: the route-description row and the car hint take a
bitmap (no host maneuver), the template steps take an optional `CarIcon`. So the provider changes shape from
`(TurnType) -> CarIcon` to `(RouteInstruction) -> CarIcon?`, and `stepForInstruction` omits `setIcon` when the
provider returns nothing. Call sites: `routingInfoFromState`, `tripFromState`, `NavigationScreen.buildTemplate`
and the three bitmap consumers.

### D10 — the step-change predicates gain the ordinal

`hasStateChanged` and `hasTripChanged` compare `turnType`, `shortDescription`, `streetName` and distance. The
ordinal changes the native text today, so they would still fire, but relying on that couples the dedup to text
the bridge may stop producing. Comparing the ordinal directly is one clause per predicate and removes the
coupling.

### Threading model and lifecycle

- **No new thread, dispatcher or coroutine.** The bridge fills fields inside the existing navigation dispatch
  on the navigation engine's own thread; the Kotlin side reads the fields from the immutable instruction
  object it already receives.
- **The glyph caches stay main-thread-confined**, as `ManeuverSymbols` and `ManeuverGlyphs` are today. Both
  consumers are on the main thread: `NavigationNotificationService` builds on `Dispatchers.Main`, and the car
  template build runs on the host callback (main) thread inside the app's host guard. No lock is added, and
  none is needed.
- **Lifecycle**: the caches are process-scoped, bounded by `(TurnType, ordinal)` pairs actually seen, and hold
  no surface, context or session reference — so neither a stopped navigation nor a disposed car surface
  leaves anything behind.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| The constructor-signature string and the Java constructor drift apart → nulls in the dispatched array | Edit both in one commit; D5 turns the failure into "no instructions" instead of null holes; the emulator run shows the instruction list |
| The host draws the number even when a custom icon is set (D6 unverified) | One Desktop Head Unit run answers it; if true, keep our icon plus the number — the spec holds either way |
| `setRoundaboutExitNumber`'s upper bound is undocumented (`@IntRange(from = 1)` only) | Guard accepts the ordinal for a small range and falls back beyond it; the clamp constant is an Open Question, not a spec change |
| A digit at 48 px is illegible at the car's smaller surfaces | The number is drawn from the ring radius, and the legibility is settled by a `pixel-check` verdict at the minimum icon size rather than by reading the code |
| The submodule patch is rejected upstream, or the gitlink bump races another session | The patch is minimal and self-contained; §5's one-session push rule and `git ls-remote` before the push |
| Two-digit ordinals overflow the ring | The ring radius drives the text size, and a Robolectric case covers a two-digit ordinal |

## Migration Plan

No data migration: nothing is persisted, no JSON format, protocol, resource key or asset changes.

Order of deployment matters in one place: the submodule commit is pushed to the local branch **before** the
gitlink bump lands, because a fresh clone builds the pinned SHA only. Rollback is the reverse — revert the
gitlink bump and the Kotlin commit together; the submodule commit becomes inert.

## Verification

**Red on HEAD.** `auto: NavigationTemplateMapperTest` — "a roundabout exit maneuver carries the exit number".
Red today without any new API, because `Maneuver.getRoundaboutExitNumber()` already exists and returns `0`.

**Unit cases** per spec scenario: `core: ManeuverSymbolsTest` (glyph differs per ordinal, two-digit ordinal, no
per-exit markers), `core: TurnInstructionLocalizerTest` (ordinal-sourced wording, no-ordinal wording),
`app: NextTurnOverlayTest`, `app: NavigationDetailsOverlayTest`, `app: NavigationNotificationBuilderTest`,
`auto: NavigationTemplateMapperTest` (type selection, CW/CCW, no-ordinal fallback, cue parity),
`auto: ManeuverGlyphsTest`, `auto: RouteDescriptionScreenTest`, and
`osmscout-client-java: RouteInstructionFieldsTest` for the field semantics.

**On-device.** `app(device): RouteInstructionRoundaboutDeviceTest` replays a route through a roundabout and
reads the ordinal and direction off the delivered instructions — the only way to prove the native side filled
them, since no JVM test executes the bridge.

**Measurements.**

| Claim | Measurement |
|---|---|
| the bridge carries the ordinal and the direction | coordinate-free diagnostics line for the instruction: turn type, ordinal, direction, and the maneuver constant chosen for it |
| the digit is legible at the minimum icon size | `pixel-check` verdict on the phone overlay at 64 dp and the phone notification's large icon |
| the phone overlay still fits its band at 64 dp with a number | UI-dump geometry of the overlay row (icon bounds and text bounds, no overlap) |
| the host renders its own numbered roundabout | Desktop Head Unit run — **not** a stationary phone emulator: the phone emulator cannot produce the car host's maneuver rendering, and the claim is not provable there |
| the maneuver type is direction-correct | the diagnostics line's maneuver constant on a clockwise and a counter-clockwise roundabout |

**Falsification.** Mutate `OnRoundaboutLeave` to leave the exit ordinal at `0`; the named red case must fail.
Restore, then re-run green forced with `-PforceTests --no-build-cache`.

## Open Questions

- **What is the largest ordinal the host renders?** The API documents no upper bound. The guard needs a clamp;
  the value only affects which ordinals take the number-bearing path and can be set from a DHU observation
  without touching a requirement. **Still open, and now with its two halves separated by measurement:** the
  DHU observation could not be taken (the binary is refused by this session's shell allowlist and, by the
  entry below, answers the wrong program anyway), while the app's own glyph was measured and needs **no**
  upper clamp — `fittedTextSize` shrinks the number to fit the ring and never clips it, at 112 → 48×24 px
  inside a 57 px inner diameter. The measurement also refuses to supply a digit-count clamp: the same
  ordinal is 0.250 of the symbol's edge at 48 px but 0.143 at the phone overlay's 168 px, so legibility
  beyond two digits is size-dependent rather than a threshold. Whether the guard takes an upper bound at all
  is therefore an owner decision, not a measurement a session can take (`RoundaboutExitGlyphDeviceTest`,
  tasks 7.3 and 7.4).

Resolved during apply, before the code was finalised:

- **Does a custom `Maneuver` icon suppress the host's numbered roundabout?** (was D6.) **Closed by removing the
  dependence on it, not by answering it.** Investigating it showed the two hosts are different programs — phone
  projection is Google's, the DHU is the AOSP reference renderer — so a DHU run would have answered the question
  for a host no driver uses. The implementation now supplies both the exit number and the app's numbered glyph
  (D6), which identifies the exit under either precedence, so the question no longer gates the code. Task 7.3's
  DHU run is therefore corroboration of the AOSP renderer's behaviour, not a decision, and its outcome cannot
  invalidate the requirement.
