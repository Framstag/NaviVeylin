# Proposal: Fast reroute trigger

## Why

Rerouting currently triggers ~30s after the vehicle leaves the planned route (5 native reroute requests at 5s cadence + a 30s hard floor in `NavigationViewModel`). At 50 km/h that is ~400m past the deviation point; at 100 km/h ~830m. The 30s floor was set conservatively at initial import with no recorded false-positive incidents behind it, and it conflates two concerns — noise filtering and cascade prevention — that should be split. Mainstream navigation apps reroute within ~5-10s.

## What Changes

- **Split the confirmation gate into two concerns** in `NavigationViewModel.onRerouteRequest`:
  - **First-trigger latency**: lower `MIN_REROUTE_CONFIRM_COUNT` from 5 to 2 and `MIN_OFF_ROUTE_DURATION_MS` from 30s to 10s. Reroute fires ~10s after first off-route detection (was ~30s).
  - **Post-reroute cooldown**: add an explicit cooldown (~25s) after a confirmed reroute before the next reroute can be confirmed, preserving cascade protection that the 30s floor previously provided implicitly.
- **Distance-based fast path**: when the app-computed distance from the current position to the active route polyline exceeds 50m, confirm on the first `onRerouteRequest` (~5s). The polyline is already available via `RouteEntry.latitudes/longitudes`.
- **Unchanged**: native `RouteStateAgent` 5s cadence, `PositionAgent` 20m snap, `MAX_REROUTE_ACCURACY` (100m) gate, `TUNNEL_REROUTE_GUARD_MS` (30s) guard. No submodule change.

## Capabilities

- **New Capabilities**:
  - `reroute-trigger` — trigger timing and confirmation conditions for rerouting (fast first trigger, distance fast path, post-reroute cooldown, preserved noise guards).
- **Modified Capabilities**: none. `navigation-controller` (reroute handling: recalculate + continue) and `rerouting-visual-feedback` (state exposure) are unaffected — their requirements do not change.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — confirmation constants, cooldown state, distance fast-path check in `onRerouteRequest`.
  - New unit-testable helper for point-to-polyline distance (or reuse of existing geometry code) — likely `app/src/main/java/com/naviveylin/navigation/` or `util/`.
- **Tests**:
  - `app/src/test/java/com/naviveylin/navigation/` — new unit tests for confirmation logic (count/duration/cooldown/distance fast path) and polyline distance helper.
  - Existing `RoutePanelComposeTest.kt` and navigation tests must stay green.
- **No native change**: no submodule patch, no vcpkg/CMake impact, no ABI rebuild.
- **Scope**: phone and Android Auto/AAOS both benefit — reroute confirmation lives in the shared `NavigationViewModel`; no parity deviation.
- **Additive**, not breaking. Rollback: revert constants and remove cooldown/distance logic; behavior returns to current 30s gate.
- **Guidelines**: no `guidelines/` document is contradicted; `guidelines/Design.md` threading rules apply to the new distance computation (see design.md).
