# Design — fix-car-style-switch-costs-one-load

## Root cause

The car map screens re-apply the `daylight` flag after a style load they performed
(`MapScreen.kt:485`, `NavigationScreen.kt:478` — `pushDark(force = styleChanged)`), and `force` bypasses
`CarDaylightApplier`'s by-value dedupe (`CarDaylightApplier.kt:28-31`), so the flag goes to
`setStyleSheetFlag`, which reloads the main **and** the basemap stylesheet (`DBThread.cpp:455-491`). One
user-initiated switch therefore loads the stylesheet set twice.

The assumption behind the force — "a style load might not carry the flag" — is contradicted by the native load
path: `loadStyleSheet` hands the load the enabled flags (`OSMScoutClient.cpp:2226-2235`), and
`DBThread::LoadStyleInternal` stores them for the new style and its basemap
(`DBThread.cpp:455-460`) and installs them as overrides before the parse
(`DBThread.cpp:366-369`).

## The single fix

The style apply is the whole cost of a switch: `styleApplier.apply(style)` and nothing else. The `force`
parameter loses its last caller and is removed end to end — `CarDaylightApplier.apply`/`needsPush`,
`RendererGate.DaylightPush`/`requestDaylightPush`, and the three car screens' `pushDark` — so the applier keys
on the `daylight` value alone and no dead branch survives.

## Where the first push goes instead (round 3: nowhere else — the push is fine as it is)

Round 2 deferred the first push behind a successful style load, on the theory that the native side drops a flag
pushed before the databases are open. That theory is wrong, and round 3 withdraws it:

- `DBThread::SetStyleFlag` returns false only when `!isInitializedInternal()`, and `isInitializedInternal()` is
  `std::all_of(databases …)` (`DBThread.cpp:91-95`) — **vacuously true while the set is empty**, which is the
  warmup state. So the push is *stored* (`DBThread.cpp:422-427`), not dropped.
- A load over an empty set also reports success (`DBThread.cpp:455-467`, `:492`), so "a successful load" is not a
  DB-open proof at all: the deferral's trigger proved nothing.
- The stored flag reaches the maps through the open path: a database is pushed only after `Open` succeeded
  (`DBThread.cpp:290`, `:323`) and its style config comes from `makeStyleConfig`, which applies `stylesheetFlags`
  (`DBThread.cpp:360-369`). The one state that would drop a push — a listed database that is not open — is
  unreachable: a list change closes and clears the set before rebuilding it under the same write lock
  (`DBThread.cpp:183-188`).

So there is nothing to defer to. `pushDark` keys on the value alone, as it did before round 2, and no re-push
follows a load. Withdrawing the deferral also removes the hole it introduced: `NavigationScreen` loads its style
from exactly one call site (`NavigationScreen.kt:452-465`), so a failed settings read or a rejected stylesheet
there would have left the flag unapplied for the whole screen instance.

## Blast radius

| file | change |
|---|---|
| `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` | drop `pushedForStyleSheet`, `styleChanged` and the forced push; `pushDark()` loses its parameter |
| `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` | the same four lines |
| `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` | `pushDark()`/`requestDaylightPush`/`apply` without the parameter |
| `auto/src/main/java/com/naviveylin/auto/CarDaylightApplier.kt` | `apply(dark)`, `needsPush(dark)`; KDoc states why no re-application follows a load |
| `auto/src/main/java/com/naviveylin/auto/RendererGate.kt` | `DaylightPush(dark, token)`, `requestDaylightPush(dark)` |
| `auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt` | new case `aStyleSwitchLoadsTheStylesheetSetOnce` (the red case) and, round 3, `theFlagIsAppliedBeforeAnyStyleLoadSucceeds` |
| `auto/src/test/java/com/naviveylin/auto/CarDaylightApplierTest.kt` | `forceRepushesEvenAfterSuccess` removed with its subject; `needsPushReportsWhetherAPushWouldHappen` without the force assertions |
| `auto/src/test/java/com/naviveylin/auto/RendererGateTest.kt` | the daylight-push case without the force field |
| `guidelines/MapRendering.md` §15 | the force-after-a-load sentence replaced by the native fact; the startup-race bullet states why no push is lost and why no re-application follows a load |
| `openspec/specs/map-styles/spec.md` (delta) | one modified requirement, two scenarios |

No native source, no build input, no public API, no new dependency. The phone path
(`MapCanvasViewModel.ensureMapStyle`, `STARTUP_DB_READY`) is untouched.

## Rollback

`git revert` of the change: the two screens regain the four lines, the applier its `force` parameter. Nothing
persisted, migrated or cached changes shape, so a revert needs no data step. If the load were ever to stop
carrying the flags, the fix's premise fails and the correct move is to restore the force and record it as
required behaviour in `guidelines/MapRendering.md` §15 (the item's own alternative), not to paper over it in one
screen.

## Alternatives eliminated by the evidence

- **Keep the force and record it as required behaviour in §15** (issue #16's second branch). Eliminated:
  `OSMScoutClient.cpp:2226-2235` states that the load keeps the enabled flags for the newly selected style, and
  `DBThread.cpp:366-369` installs them before the parse. There is no native readback of the installed pair, so
  the assertion lives in the load path's code, not in an accessor.
- **Add a native flag accessor and a submodule test asserting the flag survives** (issue #16's suggested
  check). Eliminated as the evidence carrier: the JavaScout suite is not part of this project's JVM gate
  (`LOOP-MEASURE-CHECK ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`), so a case there would be a
  claimed result the run cannot carry, and `guidelines/MapRendering.md:869-871` records that the app is the only
  writer of the pair, which an accessor would either duplicate or contradict.
- **Keep `force` as an unused seam for a future DB-ready re-push.** Eliminated as a *parameter*: nothing on the
  car fires on a DB-ready event that a reusable parameter could serve, and the native side does not need one — a
  push made before the databases are open is stored, not dropped (`DBThread.cpp:91-95`, `:422-427`).
- **Defer the first push behind a successful style load** (round 2, withdrawn in round 3). Eliminated: a load over
  an empty database set succeeds (`DBThread.cpp:455-467`, `:492`), so it proves nothing; the push it deferred was
  never at risk; and `NavigationScreen`'s single style-apply call site means a failed settings read or a rejected
  stylesheet would have left the flag unapplied for the whole screen instance.
- **Remove only `force = styleChanged` and leave the parameter.** Eliminated: the same dead branch with an extra
  caller-shaped hole, and the guideline sentence it comes from would still describe a re-application that no
  longer happens.
- **Restore the force on the successful-style-load path** (round 1's first suggestion, and the shape the tree
  had). Eliminated: a successful load is exactly what a style switch performs, so the force would fire on every
  switch again — the defect this change exists for.

## Why a capability delta and not `skip_specs`

The change alters observable behaviour of the shipped capability `map-styles` (what a switch costs on the car,
and that the variant survives it), so the delta states it and its scenario names the case that pins it.
`car-host-fault-isolation`'s "Bounded periodic render work" requirement is not changed: its scenario "Settings
re-read with an unchanged style flag" already forbids the redundant push this change removes from the
style-load path.
