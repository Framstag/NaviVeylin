# Design: GPS provider strict fallback

## Context

See proposal.md — Why. `LocationService` (`app/src/main/java/com/naviveylin/location/LocationService.kt`) currently starts Fused and raw `LocationManager` in parallel on Play Services devices. The `shouldEmit` dedupe only drops fixes with identical timestamp AND position, so raw GPS fixes (different timestamps than Fused's processed output) pass through and cause marker jumps in free driving mode.

## Goals / Non-Goals

**Goals:**
- On Play Services devices, Fused is the sole location source — OS-level smoothing reaches the marker.
- On GMS-less devices (AAOS head units, Huawei, sideload), `LocationManager` remains the sole source — no regression.
- Minimal, testable change to `LocationService` only; consumers unchanged.

**Non-Goals:**
- Dead-reckoning / GPS simulation when no fix is available — tracked separately in `TODO.md` (PositionSimulator).
- Changing fix cadence, accuracy thresholds, or marker rendering.
- Any native (libosmscout) changes.

## Decisions

### Decision 1: Gate `startManagerUpdates()` behind `!useFusedProvider`

`startLocationUpdates()` becomes:

```kotlin
fun startLocationUpdates() {
    if (!hasPermission) { ...; return }
    if (useFusedProvider) startFusedUpdates() else startManagerUpdates()
}
```

The existing `useFusedProvider` runtime check (`GoogleApiAvailability.isGooglePlayServicesAvailable`) already decides Fused vs fallback — it just never gates the fallback. This is a one-line behavioral change.

**Alternatives considered:**
- **Keep both, gate raw fixes by Fused staleness** (Option C from exploration): LocationManager runs but only emits when Fused is stale. More code, same user-visible effect on healthy devices, and duplicates the liveness logic that belongs in the future simulator. Rejected.
- **Remove LocationManager entirely**: breaks GMS-less devices — AAOS head units and sideload installs are a first-class distribution target (see AGENTS.md). Rejected.
- **Strict fallback (chosen)**: minimal, matches the expert guidance that the OS service already smooths; LocationManager exists only where Fused cannot.

### Decision 2: Keep `shouldEmit` dedupe

On the `LocationManager`-only path, GPS + NETWORK + PASSIVE still run in parallel and deliver the same underlying fix — the dedupe is required there. On the Fused-only path it is a harmless no-op guard. No change.

### Decision 3: Make provider selection testable

`LocationService` has no unit tests today. `GoogleApiAvailability` is a static singleton and hard to fake; `LocationManager` needs Robolectric shadows. Design:

- Extract the availability decision into an injectable seam: constructor param `playServicesAvailable: Boolean = <runtime check>` (or a `@VisibleForTesting` internal constructor), so tests can exercise both branches without faking GMS.
- Robolectric tests verify: Fused available → `requestLocationUpdates` called on Fused client, `LocationManager.requestLocationUpdates` never called; Fused unavailable → the reverse.
- Follow the existing classloader rule from AGENTS.md: tests touching `OSMScoutClient` statics must use default Robolectric sandbox — `LocationService` tests don't touch JNI, so they are free of that constraint.

**Alternatives considered:**
- Faking `GoogleApiAvailability` via Robolectric shadow: brittle, couples test to GMS internals. Rejected.
- No tests: violates project rules (config.yaml tasks rule: unit tests for new/modified code). Rejected.

## Risks / Trade-offs

- [Fused silently stalls on GMS devices (tunnel, GMS hiccup) → marker freezes where raw GPS previously delivered] → Accepted: Fused internally aggregates GPS+network+WiFi and degrades gracefully; sustained loss is exactly the case the future PositionSimulator (TODO.md) covers. Not a regression of this change — raw GPS cannot fix what Fused cannot (same GNSS hardware).
- [Behavior change on GMS devices: fixes now arrive only at Fused cadence (1 s / 5 m)] → Intended; this is the fix for the jump problem.
- [Regression on GMS-less devices] → None: the `LocationManager` path is byte-for-byte unchanged, only its activation condition changed.

## Migration Plan

Single commit, no data migration. Rollback = revert the gating line. Verify on device: `adb logcat -s LocationService` should show Fused requested and no `LocationManager onLocationChanged` lines on a GMS phone; on a GMS-less device the reverse.

## Open Questions

None — the simulator scope is explicitly deferred to TODO.md and does not change this design.
