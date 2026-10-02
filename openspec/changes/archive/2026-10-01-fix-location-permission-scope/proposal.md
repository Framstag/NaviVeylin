# Proposal — fix-location-permission-scope

## Why

`TODO.md` §69 / `guidelines/Regulatory.md` §6: the Play "Permissions and APIs that Access Sensitive
Information" policy updated 2026-04-15 takes effect **2026-10-28** and expects precise location at
minimum scope, foreground-service location as the continuation of a user-initiated action, and a Data
safety declaration that matches what the app does. The audit performed for this proposal (2026-09-26,
tree verified) found that the policy item sits on top of a **real defect**, not just a documentation
gap:

1. **The runtime request cannot succeed on a modern device.** `MapCanvasScreen.kt:764` uses
   `ActivityResultContracts.RequestPermission()` and launches it with `ACCESS_FINE_LOCATION` alone
   (`:793`, and the resume path checks fine only at `:834`). Per Android's own documentation, an app
   targeting API 31+ that requests fine without coarse has the request **ignored** by the system
   ("If you try to request only `ACCESS_FINE_LOCATION`, the system ignores the request"). Target SDK is
   36, so a fresh install on a real device never obtains location unless the permission was granted by
   other means. `location-permissions` currently *specifies* that fine-only request, so the stale spec
   is the root of the defect — it must change with the code.
2. **An approximate grant makes the app dead.** `LocationService.hasPermission` (`:501-504`) checks only
   `ACCESS_FINE_LOCATION`, and `startManagerUpdates` (`:655-665`) requests GPS/NETWORK/PASSIVE
   regardless of the grant — on the GPS provider that throws against a coarse-only grant. A user who
   chose "approximate" sees a map that never moves.
3. **Precise location is requested where approximate suffices.** The request is always
   `PRIORITY_HIGH_ACCURACY` (`:629`), i.e. every map/free-driving consumer pays the precise fix and the
   battery cost, although policy asks for the minimum needed.
4. **Navigation silently does nothing without a grant.** Both engines are permission-guarded no-ops
   (`NavigationViewModel.kt:87` note, `AANavigationController.kt:106`), so a car driver who never granted
   location taps "Navigate to" and gets no route and no explanation.
5. **The download service has no Android 15 timeout path.** `MapDownloadService` is a `dataSync` FGS
   started from the download UI and stopped on completion (correct shape), but it does not implement the
   foreground-service timeout hook — Android 15+ caps `dataSync` at 6 h/24 h, so a cap hit ends the
   service with no clean state, and its 4 h wake lock can outlive the service.
6. **No in-app privacy statement** exists, although Play requires a privacy policy for apps that use
   location and the Data safety form must match the implemented behaviour (including §68's outcome:
   diagnostics carry no coordinates and are kept 7 days).

**Owner decisions taken 2026-09-26** (recorded here for traceability, not re-opened):

| Decision | Consequence |
|---|---|
| Approximate location is enough for the map and free driving; **starting a route requires a precise grant**, with a prompt offering the upgrade | The map stays useful for users who decline precise location; turn-by-turn is never driven by a ~2 km fix |
| Diagnostics carry no coordinates at all, 7-day retention, identity fields instead | Handled by the sibling change `fix-diagnostics-coordinate-redaction`; this change only has to state the same facts in the privacy statement |

**Findings that need no change** (recorded in `Regulatory.md` instead of invented work):
`ACCESS_BACKGROUND_LOCATION` is not declared and not needed, so no Permissions Declaration form, no
≤ 30 s demo video and no background-location disclosure apply; geofencing is not used (and is no longer
an approved FGS use case); the "location button" mandate binds apps targeting **API 37+** whose features
are *session-based only* — this app targets 36 and its access is a continuous, user-initiated navigation
session, which is the documented foreground-service-location case.

## What Changes

- **One runtime request, both permissions.** The request asks for `ACCESS_COARSE_LOCATION` and
  `ACCESS_FINE_LOCATION` together and handles all three outcomes (precise / approximate / denied). A
  denial is still never re-prompted automatically; the "Don't ask again" rationale path stays.
- **Granted accuracy class becomes a first-class value.** A `:core` seam derives
  `PRECISE | APPROXIMATE | NONE` from the two grants, and both surfaces (phone, car) read the same
  rule. `LocationService` starts provider updates for either grant:
  `PRIORITY_HIGH_ACCURACY` when precise, a balanced priority when approximate, and on the
  LocationManager fallback only the providers the grant actually allows (GPS needs fine;
  network/passive need coarse).
- **Navigation gate.** Starting a route requires the precise class. Approximate or missing grant →
  the request is refused with a user-visible explanation and an upgrade action on the phone
  (re-request, or system settings when permanently denied); the car shows the same wording as a
  non-blocking message, because the car UI cannot launch the phone's settings. Free driving, the map,
  search, favourites and diagnostics are unaffected by the gate.
- **Download FGS respects the platform timeout.** `MapDownloadService` implements the foreground-service
  timeout hook (both the API-35 and the later overload), ends cleanly when the platform ends it, keeps
  the download resumable instead of reporting success, and binds its wake lock to the service lifetime.
- **In-app privacy statement** in the About dialog: location is used on-device only and never
  transmitted, diagnostics are kept at most 7 days and contain no coordinates, map data is
  OpenStreetMap/ODbL — strings in `values/` + `values-de/`.
- **Docs**: `guidelines/Regulatory.md` §6/§9/§10 rewritten as an audit result (what the policy asks,
  what the code does now, what was changed, the explicit no-change findings), plus the owner-side
  listing checklist (privacy policy URL, Data safety answers) that no code change can satisfy.
- **Not in scope:** introducing the Android location button or a session-based permission model
  (target 36 and a continuous navigation use case make it unnecessary and, for turn-by-turn, unsuitable);
  background location; the diagnostics retention/redaction work (sibling change); changing FGS types;
  the `location-updates-lease` semantics (`shared-resource-arbitration`), which this change consumes
  unchanged.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `location-permissions`: "Runtime permission request" — the request SHALL ask for coarse **and** fine
  together and handle the approximate outcome; new requirement "The granted accuracy class governs
  provider updates"; new requirement "Starting navigation requires precise location" (the gate, its
  upgrade action and the car's platform-forced deviation).
- `map-download-infrastructure`: "Foreground service for download" — the service SHALL handle the
  platform's foreground-service timeout, end cleanly and leave the download resumable.
- `about-dialog`: new requirement "About dialog states what the app does with location and diagnostics"
  (the in-app privacy statement).

## Impact

`:core`
- `core/src/main/java/com/naviveylin/core/LocationGrant.kt` (new) — `AccuracyClass`, `accuracyClass(context)`, `hasPrecise(context)`
- `core/src/test/java/com/naviveylin/core/LocationGrantTest.kt` (new)
- `core/src/main/res/values/strings.xml`, `values-de/strings.xml` — gate wording, upgrade action, privacy statement (one wording, both surfaces)

`:app`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — launcher contract (`:764-793`), resume check (`:834`), rationale path, upgrade action wiring
- `app/src/main/java/com/naviveylin/location/LocationService.kt` — `hasPermission` (`:501-504`), `startFusedUpdates` request priority (`:628-640`), `startManagerUpdates` provider selection (`:655-665`)
- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — `navigateTo` / `startDirectRoute` gate + refusal state (`:87`, `:340-391`)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — surface the refusal/upgrade state (no new native call)
- `app/src/main/java/com/naviveylin/service/MapDownloadService.kt` — timeout hook, clean end, wake-lock lifetime
- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — privacy statement
- tests: `LocationServiceTest`, `MapDownloadService*` (new), `NavigationViewModel*` (gate cases), Compose test for the About dialog

`:auto`
- `auto/src/main/java/com/naviveylin/auto/DetailsScreen.kt` / `MapScreen.kt` / `RootScreen.kt` — show the non-blocking refusal message where a route was requested
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — pass the gate's outcome through the existing error/hint path (no new host mutation)
- tests: `DetailsScreenTest`, the navigation-session/mapper tests for the new message

`AndroidManifest.xml`: unchanged (fine + coarse stay declared; `dataSync`/`location` FGS types stay —
the audit found them justified). No native/JNI change, so no submodule patch and no gitlink bump.

Guidelines: `guidelines/Regulatory.md` §6/§9/§10; `guidelines/UI.md` (permission/dialog wording, car
message rules); `guidelines/Design.md` §4 (threading/lifecycle for the new `:core` seam — pure, no
state) and §12 (one source of truth for the accuracy rule). Specs: `location-permissions`,
`map-download-infrastructure`, `about-dialog`.

Change class: **behavioural, additive** — a previously always-answered request becomes a
three-outcome one, and navigation can be refused where it used to silently do nothing. No storage
format change. Rollback: revert the touched files and the spec deltas; a reverted build returns to the
fine-only request (and with it the API-31+ defect).
