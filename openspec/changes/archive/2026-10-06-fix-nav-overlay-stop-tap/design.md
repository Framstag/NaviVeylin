# Design

## Context

See `proposal.md` — Why. The pieces that shape the approach:

- The routing status card is one composable, `NavigationStateOverlay` (`app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`): a `Card` whose modifier carries `.clickable(onClick = onClick)` (`:63`), containing the road-name text, the two progress lines, and the stats row. The stop control is an `IconButton(Modifier.size(40.dp))` inside the shared `NavigationStatsRow` (`:~193`), drawn only when the caller passes a non-null `onStopNavigation`.
- Two hosts share that row: `MapCanvasScreen`'s bottom card (`:2364`, `onStopNavigation` at `:2380`, `onClick = { showNavDetails = true }` at `:2383`) and the expanded `NavigationDetailsOverlay` (`:113-116`, stop callback at `:58`). The car surfaces pass `onStopNavigation = null`, so the control does not exist there.
- The `:auto` module is not involved; everything here is phone Compose, main thread, stateless composables fed by a `StateFlow` (`guidelines/Design.md` §4).
- Existing test seam: `NavigationStateOverlayComposeTest` (Robolectric, `createComposeRule`, default sandbox — the JNI-stub classloader rule forbids `@Config(sdk=…)`). It has no case for the stop control today.
- The device evidence (`TODO.md` §122) says the UI dump exposed only the container as a clickable node over the control's band. Compose normally routes a pointer event to the innermost target, so "the tap never arrives" was never explained by the code as read — and the measurement this change owed (D1, measured outcome below) shows it does arrive: the control's **own** node is not exposed as actionable, while the only actionable node over it is the container. The fix therefore targets the geometry and the semantics of the target, not the dispatch.

## Goals / Non-Goals

**Goals:**

- A tap at the stop control's centre ends navigation on the phone, in both the card and the expanded view, and can never be interpreted as "open the details" — the control is an actionable node of its own and its hit area is disjoint from the card's. (What the map mode does *after* the stop is not this change's subject: see the measured outcome and `TODO.md` §140.)
- The two tap areas are provably disjoint by geometry, not by dispatch-order assumption, so a future refactor of the card cannot silently re-create the overlap.
- The device-observed mechanism is recorded with numbers (node identity, bounds, resulting state) before and after the fix.

**Non-Goals:**

- The card's height/action-band layout at large font scale (`TODO.md` §138) — same file, different defect.
- The compass mode toggle (`MapCanvasScreen.kt:993`) — read only, to rule it in or out as the tap's consumer.
- Any car-surface change: the car card has no stop control by design.
- Restyling the card (colors, elevation, icon choice).

## Decisions

### D1 — Diagnosis first: measure which node consumes the tap

Three alternatives for establishing the mechanism:

1. **Assume the container swallows the tap** (the reading in `TODO.md` §122) and go straight to the geometry fix. Cheapest, but it would leave the observed free-driving outcome unexplained — and a fix aimed at the wrong consumer would leave the defect in place while looking done.
2. **Logcat only** (`adb logcat -s NaviVeylin`): shows whether `NavigationEngine.stopNavigation` ran and whether the map mode changed, but not which node received the touch.
3. **UI-dump geometry + logcat + resulting session state (chosen).** From a *fresh* dump in the same interaction window, tap `adb shell input tap` at the control's node centre, then read: the dump's clickable node covering that point (`bounds`, `content-desc`/`resource-id`, `clickable`), the engine log line for a stop, and whether the map mode changed. Repeat three times, as the original observation did, and record the numbers coordinate-free (bounds + node identity + boolean state, never a position).

Rationale: `enterFreeDrive()` has exactly one caller — the compass mode toggle — so the container's own action does not explain the end state. Candidate confounds the measurement must distinguish: a stale dump (bounds from an earlier layout), the tap landing on a different surface layer, and the emulator's input coordinate space. Whichever it reports is what the fix addresses; if it reports "the tap never reached the app", that is recorded as the finding and the geometry fix still stands on the spec requirement that the two targets be disjoint.

Alternatives 1 and 2 are kept as fallbacks only if no device is attached during the change — the change is not blocked on the diagnosis, because the disjointness requirement holds regardless.

**Measured outcome (2026-10-05).** A device became available in the implementing session (`emulator-5554`, Pixel_8, maps installed), so tasks 1.1/1.2 were run on the **pre-fix** install (`lastUpdateTime` 21:53:42, before this change's edits):

- **The geometry is confirmed; the attribution is not.** The control node carries `clickable="false"` / `focusable="false"` at `[986,2233][1049,2296]`, and the only clickable node over it is the card's own area `[0,2018][1080,2400]` — both identical to `TODO.md` §122. But a tap at that node's centre, resolved from a **fresh** dump, **ran the intended action** (`adb logcat -s NavigationEngine` → `stopNavigation: stopped`), and the resulting screen showed `exit_free_drive` **because the mode toggle is hidden during navigation and reappears after the stop**: navigation stopped, route cleared, no session panel, no grace line. §122's mechanism is therefore corrected — the tap reached the control, and "free driving" is where the map sits once navigation ends (`map-modes`: navigation active → `NAVIGATION`, otherwise follow → `FREE_DRIVE`, otherwise `BROWSE`). The report's three attempts are better explained by a stale dump (`provision-phone-emulator` pitfall 9) than by a mode action.
- **What the fix is justified by, restated:** (i) the control is **not** exposed as its own actionable node — the only actionable node over it opens the expanded description, an accessibility and tap-target defect regardless of dispatch order; (ii) the overlap is real as geometry and mutation-provable; (iii) the 48 dp minimum. It is **not** justified by "the tap never arrives".
- **The revert-check measurement agrees from the other side.** Under mutation (a) (details tap moved onto the whole stats row, so the container covered the 48 dp box) the geometry case failed while the click case **passed** — Compose delivers the pointer event to the innermost target, which is also why the pre-fix tap worked on the device.
- **Not this change's defect, filed instead:** the card's stop ends navigation and clears the route **without** entering the session's stopped state (no `RoutePanelVM: session grace period expired …`, no Restart/End), and the map lands in `FREE_DRIVE` although the pre-navigation label was `start_free_drive` (= `BROWSE`, the mode `map-modes` returns to and `MapCanvasViewModelModeTest.navigationEndRestoresBrowseMode` asserts for the ViewModel path). One run, on a build from a tree with other in-flight work — recorded as `TODO.md` §140 with that caveat, out of scope here.
- **One gate attempt was invalidated by a foreign build** (another session started `:app:testMobileDebugUnitTest` while this change's forced run was in flight, and `:app:mergeMobileDebugNativeLibs` failed on the shared `app/build/intermediates`), so the recorded gate is the run made after the tree went quiet.

Test-harness note for whoever writes the next geometry assertion: in this Compose version `getBoundsInRoot()` returns a `DpRect`, so compare Dp bounds directly (`region.right <= control.left`) and use `assertWidthIsAtLeast(48.dp)` / `assertHeightIsAtLeast(48.dp)` for size — arithmetic on the DpRect (`bounds.right - bounds.left`) fails to compile with "actual type is 'Float', but 'Dp' was expected".

### D2 — Make the two tap areas disjoint by construction, not by dispatch order

Three alternatives:

1. **Minimal diff** — keep `Card.clickable` as the details target and give the control `Modifier.clickable` plus explicit semantics, relying on Compose's innermost-wins dispatch. Smallest change, but it is exactly the arrangement the device run did not behave like; it also leaves no testable statement about geometry.
2. **Remove the container tap** — the card is not clickable; the details view gets an explicit affordance (for example a chevron in the road-name row) with its own target. Clean separation, but it changes the established interaction ("tap the status card to open the description", spec `navigation-status-details`) and shrinks the touch area for the driver.
3. **Disjoint regions (chosen)** — the container's details tap is applied to a region that ends at the stop control's leading edge, so the card surface minus the control's band opens the details and only the control's own area stops navigation. The stop control keeps an explicit hit box of at least 48 dp per dimension (see D3).

Rationale: it satisfies the spec's non-overlap requirement as geometry (assertable with `getBoundsInRoot()` in the Compose test harness), it preserves the existing interaction, and it cannot be undone by a change in pointer-dispatch order. Implementation shape: the shared `NavigationStatsRow` stops being inside the container's clickable region, or the control is lifted out of that region — decided where the code lands, but the invariant is "the control's hit area lies outside the details tap area", asserted by a test.

### D3 — Explicit deterministic size for the control

1. **Rely on Material's `minimumInteractiveComponentSize`** (48 dp default applied by `IconButton`) while the visual icon stays 40 dp. Idiomatic, but the effective hit box then depends on theme and on Material's internal defaults, which makes a 48 dp assertion brittle.
2. **Keep 40 dp and add `Modifier.semantics`** — passes a semantics test but leaves a hit box below the driver-seat minimum the spec now states.
3. **Explicit `Modifier.size(48.dp)` for the button's touch box with a 24 dp icon inside (chosen)**, plus a `testTag` on the control and on the details region so both bounds can be asserted.

Rationale: deterministic bounds make the spec's "at least 48 dp in each dimension" scenario a real test rather than a theme-dependent one, and a stable `testTag` is also the seam the on-device UI-dump evidence uses.

### D4 — Keep one shared stats row, add the tag there

The control lives in `NavigationStatsRow`, shared by the card and the expanded view.

1. **Duplicate the control per host** — two implementations drift (the exact failure mode of the current defect's second half).
2. **Keep the shared row, tag the control inside it (chosen)** — one implementation, both hosts get an identically targetable control; the expanded-view case asserts the same tag.
3. **Parameterise the row with a tag per host** — no benefit: only one of the two is on screen at a time.

### Threading and lifecycle

No new component, no dispatcher, no lifecycle owner: pure Compose layout/pointer changes on the main thread, driven by the existing state (`navState`) from the ViewModel. Nothing native, nothing persisted, no `:core` seam needed.

## Risks / Trade-offs

- **Shrinking the details tap area removes a "tap anywhere" affordance** → the region still spans the road-name row, the progress lines and the stats row minus the 48 dp control; a Compose case asserts the region's bounds cover the card's width and reach at least to the control's leading edge.
- **A 48 dp control narrows the stats row** → the row's other three columns use `Modifier.weight(1f)`; the change also asserts the row's bounds stay inside the card at font scale 1.0 and 2.0, which keeps `TODO.md` §138 from regressing silently.
- **Robolectric does not reproduce the device's touch routing faithfully** → the Compose cases assert bounds and the callback identity (which action fired), while the *delivery* on real hardware is settled by the on-device run; the design does not claim the unit suite proves the device behaviour.
- **The diagnosis may show the tap never reached the app** (stale dump, wrong display, emulator input space) → recorded as the outcome, with the geometry requirement still implemented and the recipe corrected for the next run; the change does not fake a mechanism.
- **The compass mode toggle is a plausible consumer** → it is read, not changed; the change records whether its action fired during the diagnosis, so `TODO.md` §122's mechanism is closed either way.

## Verification

- **Unit / Compose** (`app/src/test/java/com/naviveylin/ui/navigation/`): card cases in `NavigationStateOverlayComposeTest` — control's `testTag` exists and is displayed; its hit bounds are ≥ 48 dp both ways and lie outside the details region's bounds; a click on the control invokes only the stop callback (a details-click counter stays 0); a click on the details region invokes only `onClick`; no case pins the theme-dependent default. Expanded-view case in `NavigationDetailsOverlayTest` — the same tag behaves the same way. Revert-check per new invariant (mutate one, name the case that must fail, restore, forced green with `--rerun-tasks`).
- **On device** (`guidelines/Build.md` §10 recipe, phone, navigation running): from a fresh UI dump, `adb shell input tap` at the control's node centre → `adb logcat -s NaviVeylin` shows the engine's stop line, the map mode is unchanged, and the route-panel grace line appears ~45 s later; the dump's clickable node at that point is the control, not the container. Geometry is recorded as bounds + node identity (`uiautomator dump` + `adb pull`), never as coordinates in prose.
- **Not a pixel property**: the claimed property is tap geometry and state, so `tools/measure-highlight.py` does not apply; the verdict is the UI-dump geometry plus the log lines, stated as such.
