# Proposal

## Why

During active navigation the phone's routing status card carries a stop control (an `IconButton`,
40 dp, `content-desc="Navigation beenden"`) inside a card whose **whole surface** is
`.clickable(onClick = onClick)` — the tap that opens the expanded route description
(`NavigationStateOverlay.kt:63`). Only the container is exposed as the clickable node over the
control's band, so the driver has no reliable way to aim at "Navigation beenden".

On device (1080×2400 phone) the UI dump exposes **only the container** as clickable over the
control's band `[986,2233][1049,2296] ⊂ [0,2018][1080,2400]`, while the control's own node carries
`clickable="false"` / `focusable="false"` — reproduced 2026-10-05 (`emulator-5554`, Pixel_8) and
matching `TODO.md` §122's numbers exactly.

That report also read three attempts that "ended in **free driving**" as evidence that the tap never
reached the control. The measurement this change owed refutes the attribution: a tap at the control's
centre, resolved from a **fresh** dump, ran the intended action (`adb logcat -s NavigationEngine` →
`stopNavigation: stopped`; route cleared, panel gone). The free-driving label appears only **after**
the stop, because the mode toggle is hidden while navigating (`map-modes`: navigation active →
`NAVIGATION`, otherwise follow → `FREE_DRIVE`, otherwise `BROWSE`).

So the defect is the **target**, not the delivery, and the costs are:

- the only actionable node covering the control is the card's own tap area, so a driver aiming at the
  control's edge (a 63 px node inside a card-wide tap area) opens the expanded route description
  instead of stopping navigation, and the accessibility tree offers no stop action of its own;
- the control's hit box is below the driver-seat minimum the rule now states.

The earlier misreading also blocked a verification: the session's stopped state and its bounded grace
were never observed, because the card's stop ends navigation and clears the route without a stopped
session — a separate defect, filed as `TODO.md` §140 with its caveat and **not** claimed here.

Per the iteration rule the change measured first and fixed second: the diagnosis (which node consumed
the tap, with coordinate-free evidence) was a task of this change, and its result corrected this
premise rather than confirming it.

## What Changes

- **The stop control becomes its own tap target.** It gets an explicit, independently reachable
  click/semantics node with a touch size a driver can hit, so a tap at its centre invokes stop and
  nothing else.
- **The card's container tap stops overlapping it.** The container keeps its meaning (open the
  expanded route description, spec `navigation-status-details`) over the rest of the card, but its
  hit area no longer covers the control's band. Which of the two shapes is taken (container ends
  short of the control vs. control excluded from the container's merged semantics with its own
  minimum touch size) is settled in `design.md`.
- **The expanded details view's stop control is kept as its own target too** — it shares
  `NavigationStatsRow` with the status card (`NavigationDetailsOverlay.kt:113-116`), so a fix in one
  place must not regress the other.
- **A measurement records the mechanism**, coordinate-free: which node the tap at the stop control's
  centre resolves to (test tag / `content-desc` identity, bounds, resulting session state), on the
  phone, before and after the fix.
- **`guidelines/UI.md` gains the general rule** next to its existing tap-target paragraph: a phone
  overlay's own control is never inside its container's tap target.
- Non-breaking and additive: no public API, no manifest, permission or resource change (the existing
  `R.string.stop_navigation` label is reused). Rollback path: revert the two overlay files and the
  guideline paragraph — nothing else consumes the control, and no state or persistence format
  changes.

Scope: **phone only.** The car's status card passes `onStopNavigation = null` by design
(`NavigationStatusRow`, `NavigationStateOverlay.kt:~193`), so there is no car control to fix and no
parity requirement to satisfy; the change does not touch `:auto`.

Out of scope: the card's height/action-band layout at large font scale (`TODO.md` §138) and the
compass mode toggle itself (`MapCanvasScreen.kt:993`) — both touch the same screen but neither is the
tap-target defect.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `navigation-status-details`: the "Routing status card is clickable" requirement gains the
  counterpart it is missing — the card's stop control is a tap target of its own, and the container's
  tap area does not cover it. New scenarios: a tap at the stop control's centre ends navigation, a
  tap elsewhere on the card still opens the expanded view, and the two targets do not overlap.

## Impact

| Area | Detail |
|---|---|
| Specs | `openspec/specs/navigation-status-details/spec.md` (modified). No new capability. |
| Phone UI | `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` (card container `.clickable`, `NavigationStatsRow` stop control size/semantics), `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt` (shares `NavigationStatsRow`), `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (call sites `:2364-2383` and `:2397`; the mode toggle at `:993` is read, not changed) |
| Tests | `app/src/test/java/com/naviveylin/ui/navigation/NavigationStateOverlayComposeTest.kt` (Robolectric, default sandbox per the JNI-stub classloader rule), `NavigationDetailsOverlayTest.kt` for the expanded view's control |
| Guidelines | `guidelines/UI.md` — general tap-target rule (a control is never inside its container's tap target); `guidelines/Design.md` §12 (single source for shared UI) if the shared `NavigationStatsRow` gains a parameter |
| Modules | `:app` only. `:auto`, `:core`, `:osmscout-client-java` untouched; no native/JNI change, no submodule patch, no Gradle change |
| Unblocks | `route-planning-session`'s stopped-state/grace device recipe (task 10.5), re-run per `guidelines/Build.md` §10 with the UI-dump geometry as the verdict |
