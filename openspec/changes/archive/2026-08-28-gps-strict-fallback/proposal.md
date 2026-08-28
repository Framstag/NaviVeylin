# Proposal: GPS provider strict fallback (Fused only when available)

## Why

On real devices the app shows random GPS/map jumps in free driving mode. `LocationService` starts **both** `FusedLocationProviderClient` and raw `LocationManager` (GPS/NETWORK/PASSIVE providers) in parallel on Play Services devices. Raw GPS fixes carry different timestamps than Fused's processed output, so the duplicate filter (`shouldEmit`) lets both streams through — the marker bounces between Fused-smoothed and raw-noisy fixes. The OS location service already applies the necessary smoothing; the app should use it exclusively when available, and only talk to `LocationManager` when Play Services is absent.

## What Changes

- `LocationService.startLocationUpdates()` starts `LocationManager` **only** when Fused is unavailable (no Play Services). Never both.
- On GMS-less devices (AAOS head units, Huawei, sideload installs) `LocationManager` remains the sole provider — behavior there is unchanged (GPS + NETWORK + PASSIVE in parallel, deduplicated by `shouldEmit`).
- The `shouldEmit` duplicate filter stays — it is still needed on the `LocationManager`-only path where multiple providers run in parallel.
- No changes to consumers (`MapCanvasViewModel`, `NavigationViewModel`, `AANavigationController`, `RoutePanelViewModel`, `AutoServiceModule`) — they keep consuming the same `StateFlow<Location?>`.
- No new dependencies, no manifest changes.

## Capabilities

### New Capabilities
- `gps-provider-selection`: policy for which location provider the app uses — Fused when Play Services is available, `LocationManager` only as strict fallback, never both simultaneously.

### Modified Capabilities
<!-- None: gps-location-marker covers marker rendering, location-permissions covers the permission flow — neither specifies provider selection. -->

## Impact

- `app/src/main/java/com/naviveylin/location/LocationService.kt` — gate `startManagerUpdates()` behind `!useFusedProvider`
- New unit tests for `LocationService` provider-selection logic (none exist today)
- No new dependencies, no manifest changes, no native changes
