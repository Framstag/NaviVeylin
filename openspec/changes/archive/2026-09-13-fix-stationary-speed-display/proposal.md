## Why

On a real device the speed badge shows a nonzero value (observed 7 km/h) while the vehicle stands still. The location provider (Fused on GMS devices) stops delivering fixes at standstill because of the 5 m min-distance request, so the last pre-stop speed stays pinned on the widget indefinitely; when fixes do arrive they are shown verbatim with no low-speed dead-band, and the native fallback path computes "speed" from stationary GPS drift on GMS-less devices. The invariant "standing still ⇒ speed 0" is nowhere enforced.

## What Changes

- **Stale-fix zeroing**: when no fresh GPS fix arrives for more than N seconds (N ≈ 2–3× the 1 s update interval), the displayed speed SHALL read 0 km/h instead of freezing at the last fix. Applied at the state layer so follow mode, navigation mode, and the AA session all inherit it.
- **Stationary dead-band**: while the fix is fresh, if the reported speed is at or below a dead-band (~8 km/h — covers the observed 7 km/h residual) AND consecutive fixes show negligible displacement (≲ 2 m), the displayed speed SHALL snap to 0 — position evidence beats a residual/stale velocity estimate.
- **Native fallback min-displacement** (`SpeedAgent`): in the position-difference fallback branch (GPS speed unknown), a segment with displacement below a threshold (~3 m) SHALL contribute 0 distance, so stationary GPS drift never converts into a faux speed. Closes the gap the archived `speed-accuracy` change left open (its FIFO-clear only guards the GPS-speed branch).
- **Filter decay**: the spike filter (`filterSpeed`) currently returns `lastValidSpeedKmH` forever when speed is unknown (NaN / negative). It SHALL degrade that stale last-good value toward 0 over time instead of freezing it.
- **Spec updates**: extend `gps-speed-priority` (stationary contract, Kotlin side) and `speed-spike-filtering` (stale-value decay, low-speed snap) with new WHEN/THEN requirements.

Exact thresholds (timeout, dead-band, displacement) are design-time constants, validated during apply; values above are starting points.

## Capabilities

### New Capabilities

None — the behavior changes extend two existing capabilities; no new surface is introduced.

### Modified Capabilities

- `gps-speed-priority`: The SpeedAgent/Kotlin speed contract gains a stationary rule — speed SHALL read 0 while the fix is stale (provider silent) and SHALL snap to 0 when the reported speed is below the dead-band with negligible displacement; the native position-diff fallback SHALL ignore segments below a minimum displacement so drift cannot become speed.
- `speed-spike-filtering`: The last-known-good value SHALL decay to 0 when no valid speed is received beyond a staleness window (no infinite freeze at the last value), and speeds below the dead-band with stationary evidence SHALL read 0.

## Impact

| Area | Files |
|------|-------|
| Kotlin – provider | `app/src/main/java/com/naviveylin/location/LocationService.kt` (stationary/dead-band evidence, optional stale monitoring) |
| Kotlin – phone VM | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (follow-mode speed zeroing/staleness) |
| Kotlin – nav VM | `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` (filter decay) |
| Kotlin – AA | `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt` (AA mirror of the same rule) |
| Kotlin – widget | `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` / `speedWidgetInput` (only if a display-layer snap is chosen over state-layer) |
| Native | `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/SpeedAgent.cpp` — submodule patch (minimal, upstreamable), commits into the `Framstag/libosmscout` submodule and bumps the pinned reference, same as the existing `gps-speed-priority` change (committed in submodule HEAD `13c158e3a`) |
| Tests | `LocationServiceTest.kt`, `SpeedWidgetTest.kt`, `MapCanvasViewModelSpeedWidgetTest.kt`, new staleness/dead-band unit tests |
| Docs | `guidelines/Design.md` (speed data-flow notes), `guidelines/UI.md` (only if the widget's visible behavior changes) |

- **Additive**, no breaking API changes; rollback = revert the Kotlin edits and the submodule ref bump (existing APKs unaffected).
- Scope: **general** — the same invariant applies to phone follow mode, phone navigation, and the Android Auto session; the native fallback gate additionally protects GMS-less devices (AAOS, Huawei, sideload).
- Dependencies: none new.

Known limitation: the exact failing mode (Fused velocity residual vs provider-silent pin) was not captured from device logs; both are covered by the stale-timeout + dead-band combination, and logcat inspection during apply will confirm which dominated.
