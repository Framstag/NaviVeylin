# Design — fix-location-permission-scope

## Context

See `proposal.md` — Why for the audit findings. Constraints and current state that shape the approach:

- `LocationService` (`app/src/main/java/com/naviveylin/location/LocationService.kt`) already owns the
  provider choice, the request and the lease bookkeeping: `hasPermission` is fine-only (`:501-504`),
  `startProviderUpdates` picks Fused or the strict LocationManager fallback (`:599-614`), the Fused
  request is built once with `PRIORITY_HIGH_ACCURACY` (`:627-640`), the fallback requests
  GPS/NETWORK/PASSIVE per provider with a per-provider `SecurityException` guard (`:652-680`).
- `:auto` cannot depend on `:app`, so any rule the car must also apply has to live in `:core`
  (precedent: `NavigationStopRequests`, `LocationConsumers`, `NativeTileDataCache`).
- Both navigation engines are already permission-guarded no-ops (`NavigationViewModel.kt:87`,
  `AANavigationController.kt:106`); the new gate replaces "silently nothing" with a visible refusal.
  The car's error/hint path and its guarded host seams exist (`CarHostGuards.kt`, `SessionHostGate`).
- `MapDownloadService` is plain (no download-manager knowledge): the download lives in
  `MapManagerViewModel`/`BasemapViewModel` + the native managers, and the service only shows the
  notification and holds the wake lock — so a platform-ended service must tell those callers, not
  claim completion itself.
- Permission-request code is Compose-only today (`MapCanvasScreen.kt:764-793`, plus the resume
  re-check at `:834`) and unit-tested through ViewModel seams, not through the launcher.

## Goals / Non-Goals

**Goals**

- A fresh install on API 31+ can actually obtain location — approximate or precise.
- An approximate grant is a usable, non-erroring state for the map.
- No route is ever started from a ~2 km fix, and the refusal is explained where it happens.
- The `dataSync` download FGS ends cleanly under the platform's own timeout and never loses
  download state.
- The in-app privacy claim matches the implemented behaviour.

**Non-Goals**

- The Android location button / session-based permission model (target 36; continuous navigation).
- Background location, geofencing, FGS type changes.
- Any new location source, any change to `LocationConsumers` lease semantics
  (`shared-resource-arbitration`), or any change to the diagnostics retention work (sibling change).
- Requesting the precise grant again automatically after a denial.

## Decisions

### D1 — The accuracy rule is a pure `:core` seam (chosen)

`core/src/main/java/com/naviveylin/core/LocationGrant.kt` exposes `AccuracyClass { PRECISE, APPROXIMATE, NONE }`
and `accuracyClass(context)` / `hasPrecise(context)`, derived from the two runtime grants.

*Alternatives:* **(B)** keep the check inside `LocationService` and let the car re-implement it —
rejected, `:auto` cannot depend on `:app`, so the car would duplicate the rule and the two would
drift (the §84 lesson: process-global rules need one owner). **(C)** a Hilt-provided
`LocationGrantProvider` in `:app` plus a car-side copy — same duplication with more wiring.
The seam is pure and stateless: no threading or lifecycle question, no cache (the grant can change in
system settings while the app lives, so it is read on demand).

### D2 — One request for both permissions (chosen)

`MapCanvasScreen` switches to `ActivityResultContracts.RequestMultiplePermissions()` and launches
`arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)`; the result maps to `PRECISE` when fine is
granted, `APPROXIMATE` when only coarse is, and to the existing rationale/`Don't ask again` path when
neither is.

*Alternatives:* **(B)** keep `RequestPermission` and launch it twice (fine then coarse) — the platform
documents the fine-only request as *ignored*, and two launches show two dialogs where one belongs.
**(C)** request only coarse and rely on the platform to offer the precise upgrade — that would give up
precise location entirely, which the navigation gate needs.

### D3 — `APPROXIMATE` uses a balanced update configuration (chosen)

Fused: `PRIORITY_BALANCED_POWER_ACCURACY` with the existing interval/min-distance values, because the
map must still move at the current cadence; fallback: only the providers the grant allows.

*Alternatives:* **(B)** `PRIORITY_LOW_POWER` — updates arrive on the order of minutes, which makes the
follow modes stutter. **(C)** keep `PRIORITY_HIGH_ACCURACY` with a coarse-only grant — the platform
fuzzes the fix anyway, so it buys nothing and asks for more precision than the grant allows, which is
the opposite of the policy's minimum-scope expectation.

### D4 — Fallback providers follow the class (chosen)

`PRECISE` → GPS + NETWORK + PASSIVE (today's behaviour); `APPROXIMATE` → NETWORK + PASSIVE. The GPS
provider requires the precise grant in the platform, so requesting it with a coarse grant only produces
a swallowed `SecurityException` per start.

*Alternatives:* **(B)** request all three and rely on the per-provider `SecurityException` guard —
keeps the request list stable but logs a security exception at every start on an approximate-only
device and hides the reason for the missing GPS fixes.

### D5 — One navigation gate at the shared entry points (chosen)

The guard sits in `NavigationViewModel.navigateTo` / `startDirectRoute` and
`AANavigationController.navigateTo`: when `hasPrecise` is false, no route request reaches the engine
and a refusal state is published. One guard covers every call site (details sheet, POI, favourite,
long-press, car destination picker, free driving "navigate to"), including the surfaces that cannot be
unit-tested through the UI.

*Alternatives:* **(B)** check at each UI call site — six-plus sites, each of which can rot; the defect
class this change is fixing (a request that silently cannot work) is exactly a per-site miss.
**(C)** check in the JNI/native routing layer — the bridge knows nothing about Android grants, and
`AGENTS.md` keeps the native side platform-independent.

### D6 — Refusal surfaces through the existing error/hint channel (chosen)

Phone: the refusal appears where the user asked for the route (existing snackbar/dialog idiom) with an
action that re-requests the precise grant, or opens the app's system settings when the permission is
permanently denied. Car: the same wording through the existing hint/error path, routed through
`CarHostGuards` (`armScreenPush`/`guardedHostCall`), never a bare host mutation.

*Alternatives:* **(B)** a dedicated blocking screen — heavier than the refusal warrants, and a
distraction risk on the car surface. **(C)** a message without an action — fails the new requirement
("actionable on the phone"). **(D)** open system settings from the car — the car app must not launch
arbitrary activities; platform constraint, recorded as the deviation the spec allows.

### D7 — The download FGS reports a platform timeout instead of claiming completion (chosen)

`MapDownloadService` overrides both timeout hooks (`onTimeout(startId)` and the two-argument form
available on newer API levels), stops itself and releases the wake lock; the download state stays
"not completed" and resumable, and the affected UI state is updated through the existing listener
path. `START_NOT_STICKY` stays (a resurrected notification without an active download would be a lie).

*Alternatives:* **(B)** restart the download automatically — a silent background restart after the
platform deliberately capped the FGS is both policy- and UX-hostile; the download managers already
support the user resuming. **(C)** ignore the timeout and keep a long wake lock — the platform stops
the service anyway (Android 15+ `dataSync` cap), so the only difference is whether the app notices.

### D8 — The privacy statement lives in the About dialog (chosen)

The `about-dialog` capability already owns the dialog's identity/legal text; the statement is added
there in both locales, and the car about screen reuses the same wording where the car-app content rules
allow.

*Alternatives:* **(B)** a separate privacy screen — a new navigation destination for four sentences.
**(C)** docs only (`Regulatory.md`) — Play expects the privacy policy to be reachable, and the dialog is
the app's existing legal-text surface.

## Risks / Trade-offs

- **[Refusing navigation on approximate location surprises a user who chose it deliberately]** →
  The refusal names the missing precision and offers the one-tap upgrade; free driving and the map keep
  working, so the app is not dead.
- **[A second request on the phone could look like re-prompting after a denial]** → The gate's action
  only *offers* the request; an automatic re-request stays impossible, and the rationale path is
  unchanged.
- **[The timeout overrides differ across API levels]** → Override both forms; the one-arg form is
  harmless on newer levels and the two-arg form on older ones. Verify with lint + both flavor builds;
  if lint demands an API guard, annotate rather than branching the behaviour.
- **[Wording drift between phone and car]** → One string in `:core` (`values` + `values-de`), the same
  idiom as `nav_hint_neutral`; the car screen resolves the same resource.
- **[The car message becomes a host-fault path]** → All host interaction goes through the existing
  guarded seams; no new host mutation is introduced (the message reuses the hint/error send).
- **[The audit's "no change needed" findings rot]** → They are written into `Regulatory.md` §6 with the
  2026-10-28 date and the §10 cadence row, so the next review re-checks them rather than re-deriving
  them.

## Migration Plan

No data migration. On an updated install: an already-precise grant behaves as before; an
approximate-only or ungranted install starts receiving (approximate) fixes for the first time and gets
the explanation when a route is requested instead of a silent no-op. Rollback = revert the touched files
and the spec deltas, which returns the fine-only request and the silent no-op (the pre-change defect).

## Open Questions

None that change the specs, the approach or the task breakdown. The exact refusal wording and the
statement's final phrasing are wording decisions inside the implementation tasks (both locales).
