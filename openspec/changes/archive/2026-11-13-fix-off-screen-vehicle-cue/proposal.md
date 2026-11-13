# Proposal

## Why

An off-screen vehicle draws nothing on the phone map: the marker overlay's only branch for "the
position does not project on screen" is to draw nothing, so in BROWSE the derived re-center button is
the sole signal that a position exists — no edge arrow, no direction.

**Root cause (one mechanism):** the overlay has exactly one off-screen branch — `return@Canvas` when
`projectMarker` returns `null` — and `projectMarker` culls every position past `MARGIN_PX` (`100`), so
an off-viewport position produces an empty layer and no cue at all.

Evidence, measured on HEAD `c5336234d386ada4988943cbadc0132ebebb0891` (2026-11-13):

- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:66-67` — `val projected =
  projectMarker(...)` / `if (projected == null) return@Canvas`: the off-screen branch draws nothing.
- `LocationMarkerOverlay.kt:126-129` — `projectMarker` returns `null` when `sx`/`sy` leave
  `-MARGIN_PX … screen + MARGIN_PX`; `LocationMarkerOverlay.kt:254` — `internal const val MARGIN_PX = 100`.
- `openspec/specs/gps-location-marker/spec.md:120-123` — scenario "Marker hidden when off-screen"
  currently states the opposite of the cue: "**THEN** the marker SHALL NOT be rendered (no off-screen
  indicators)". This is why the item is a spec delta first and code second, not a defect repair.
- The browse re-center rule already computes the same information the cue needs:
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelFollow.kt:573` (`refreshBrowseReCenter`)
  projects the fix through `MapCanvasViewModel.browseCenterOffsetPx` (`MapCanvasViewModel.kt:651`).

**Red case on HEAD** (the smallest case that exercises the absence, and it compiles against the
shipped API — the new inset parameters are defaults, so no case body has to change):

`app: LocationMarkerEdgeCueTest` "theOffScreenVehicleDrawsACueAtTheEdge" — composes the overlay for a
fix ~10° east of the viewport centre in a 300 px frame over one flat land colour, rasterizes the
Robolectric view hierarchy under `@GraphicsMode(NATIVE)`, and requires at least one pixel of the east
edge band to differ from the land. On HEAD the overlay draws nothing, so every pixel in the band is
the land colour and the case fails:

- XML `tests="1" skipped="0" failures="1" errors="0"`, timestamp `2026-11-13T06:55:21.648Z`
  (`evidence/red/TEST-LocationMarkerEdgeCueTest-red-head.xml`).

**The single fix** (the recorded decision on issue #725, 2026-11-08T16:19:33Z, option A): when the
vehicle projects outside the viewport, past the marker's own off-screen margin (`MARGIN_PX`), the
overlay draws one cue clamped to the viewport edge in the vehicle's direction, inside the inset bounds
the map band reserves for its chrome — a Compose overlay element on top of the marker layer, reusing
the projection the overlay already performs. The spec
delta comes first: a `MODIFIED` requirement on `gps-location-marker` whose off-screen scenario states
the cue instead of forbidding it, with a new scenario for the cue's presence and one for its edge
placement.

Owning requirement: `gps-location-marker` — "Marker position tracks map viewport".
Owning guideline: `guidelines/MapFollow.md` §8 "Marker Rules" (the marker's render-target, projection
and orientation rules; it carries no off-screen rule today, which is the rule this change adds).
Parity: `guidelines/UI.md` §1 — see scope below.

**Scope: phone only.** The car marker is drawn on the native surface by
`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`, so a car edge cue is a separate native
change (the recorded decision's sub-question (iii)); the deviation is deliberate and its platform
constraint (a host-rendered surface with its own draw loop) is recorded in the delta's requirement
prose, which is what `UI.md` §1's "a documented deviation names its platform constraint" rule asks of
it. `guidelines/UI.md` itself states the rule and needs no edit.

## What Changes

- **`openspec/specs/gps-location-marker/spec.md`** (via this change's delta) — `MODIFIED` requirement
  "Marker position tracks map viewport", carried as the whole requirement block with every surviving
  scenario verbatim: the off-screen scenario says the marker is not rendered *inside the viewport* and
  one cue is drawn at the edge in the vehicle's direction, and two scenarios are added — the cue is
  drawn when the vehicle is off-screen, and the cue stays inside the inset bounds on a foldable-width
  frame.
- **`app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt`** — `projectMarker` keeps its
  contract and is joined by `projectMarkerRaw` (no margin cull, `null` only for a NaN projection) and
  `edgeCuePlacement` (pure: the clamped cue centre plus its pointing angle; `null` when the position is
  inside the frame). The composable gains `insetLeftPx`/`insetTopPx`/`insetRightPx`/`insetBottomPx`
  (defaults `0`, so every existing call site and case is unchanged) and one draw function,
  `drawEdgeCue`, which paints a small chevron in the vehicle marker's own palette branch.
- **`app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt`** — the `LocationMarkerOverlay` call
  site (`:667`) passes the view model's `overlayInset*Px` values, which the band's map framing already
  respects (`MapCanvasViewModelFollow.kt:55-58`).
- **`guidelines/MapFollow.md` §8** — one rule: an off-viewport position, past the marker's own margin,
  draws no marker and one cue at the viewport edge, inside the inset bounds the band reserves.
- **`tools/doc-routes.json`** — four rows: two name `MapFollow.md` §8 "Off-screen cue" (the rule this
  change adds, reached from both of the section's route lists); two repair a pre-existing repo-wide red
  for `Backlog.md` §5 rules that exist verbatim at `guidelines/Backlog.md:184` and `:195` and that no
  route reached before this change. The second pair is outside the item's scope and is in this diff
  because the gate's owed check cannot pass without it. `tools/check-rule-routes.sh` reads that map and
  is the check this change's gate owes for the path.
- **Not in scope:** the car/native cue (a separate item, per the recorded decision), the re-center
  button's own rules (`map-modes` — Browse re-center, untouched), the marker's culling margin
  (`MARGIN_PX` stays `100`; the cue is what fills the off-screen state), and the accuracy circle.

Additive: no existing scenario is removed, `MARGIN_PX` and `projectMarker`'s contract are unchanged,
the new composable parameters are defaulted, and nothing persisted, logged or exported changes.
Rollback: revert the overlay hunks and the call site — the cue disappears, the case goes red, the spec
delta is dropped with the change.

surface: 4 cases added (1 red, 3 green), 0 cases rewritten, 2 production files changed, 1 spec scenario
modified + 3 scenarios added in one `MODIFIED` block, 1 guideline section, 0 fixtures realigned —
`./tools/look.sh 'MARGIN_PX|projectMarker' --path app/src` (2 files before the change: the overlay and its
case; the new cue case is the third), `./tools/look.sh 'no off-screen indicators'` (1 shipped spec
scenario, 0 guideline statements).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` (phone marker overlay, Compose).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt` (the overlay's call site inside the
  phone map band — no new layer, no new `Modifier.zIndex`, no manifest or resource change).
- `app/src/test/java/com/naviveylin/ui/map/LocationMarkerEdgeCueTest.kt` (new).
- `openspec/specs/gps-location-marker/spec.md`, `guidelines/MapFollow.md` §8.
- Android Auto: untouched — `auto/` compiles unchanged.
