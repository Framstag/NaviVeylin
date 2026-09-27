# Proposal — fix-car-tile-data-cache

## Why

`TODO.md` §63: the car path never configures libosmscout's tile data cache. Only the phone map path
calls `setNativeDataCacheSize` (`MapCanvasViewModel.kt:1812`, constant 512 at `:3845`), while the car
warmup opens every installed database without touching it (`AutoServiceModule.kt:107-165`,
`client.openDatabases(...)` at `:141`). The JNI stores the value in `ClientData` and re-applies it to
**every** open database plus the basemap on each render (`OSMScoutClient.cpp:1117`, `:1530-1545`), so
the capacity a car session runs with depends on the process history:

- **car-only process** (AAOS standalone, or projection with the phone UI never opened): no value is
  ever set → `0` → the library default of **25 tiles per database**. That contradicts spec
  `native-tile-data-cache` — "The configured capacity SHALL exceed the library default (25 tiles) by
  a meaningful margin" — and it is the *slow* case, not the cheap one.
- **phone-first process** (projection with the phone UI open): the phone's 512 applies to every
  database the car opens too, i.e. `N × 512` tiles of native heap on a surface whose RAM budget is the
  §51 lmkd→host-FATAL frame.

Nothing tests the car path, so the deviation was invisible to the suite.

## What Changes

- **New `:core` seam `com.naviveylin.core.NativeTileDataCache`** — the single owner of the capacity
  values and of the apply/failure policy: a per-surface constant (`PHONE_TILES = 512`, unchanged;
  `CAR_TILES = 128`), an `apply(client, tiles)` that never throws (spec: cache configuration failure
  is non-fatal), and a **per-client first-writer-wins guard**.
  *Why the guard:* the value is client-global and re-applied per render, so two surfaces configuring
  different values in one process (Android Auto projection: phone UI + car session, one `OSMScoutClient`)
  would otherwise flip the capacity on every render and thrash the caches. The first surface to
  configure wins; a later, different request is logged and ignored.
- **Car warmup configures it**: `AutoServiceModule.provideAutoClientProvider.openMapDatabases` applies
  the car capacity before `client.openDatabases(...)`, on the existing background dispatcher.
- **Phone path uses the seam** instead of its own constant, keeping 512 and its current ordering
  (after a successful open; the JNI applies the stored value on the next render, which also covers
  databases opened later by a map scan or a basemap reload).
- **Spec delta** on `native-tile-data-cache`: the capacity is per-surface, each surface configures it
  explicitly, and the effective value is decided once per client process.
- **Not in scope**: changing the phone's 512; the JNI/native side (the mechanism already does what the
  spec asks, it is only never told the value); the `N × 512` footprint question for the *shared*
  projection process (unchanged by this change — the phone decides first there); any per-user setting
  (the spec keeps the capacity a tuned constant).

## Decision needed (large consequence)

The car's tuned capacity. `native-tile-data-cache` requires a value that exceeds 25 by a meaningful
margin, so "keep the library default on the car" is *not* available without a spec change.

| option | consequence |
|---|---|
| **A — `CAR_TILES = 128` (recommended)** | 5.1× the default, so the spec's margin holds; an AAOS car-only process goes 25 → 128 (more cache than today, far less than 512), and the shared projection process keeps the phone's 512. The exact number stays measurement-pending (head unit `dumpsys meminfo`, TODO §63/§65): it is one named constant in `:core` plus one test assertion to change. |
| B — `CAR_TILES = 512` (same as the phone) | Simplest and deterministic, no new policy question, but it raises an AAOS head unit's per-database cache 20× and leaves §63's own footprint concern unaddressed. |
| C — library default on the car | Needs a spec delta weakening "SHALL exceed the library default by a meaningful margin" for the car; keeps the car's rendering on the least cache. |
| D — measure first, then decide the number | Correct but blocked: no car/AAOS device or head unit is attached (`adb devices` empty), so the change would stall; A lands the structure and the policy now, and the number is a follow-up measurement. |

A is implemented by default (a value *must* be chosen to satisfy the spec); D is respected by keeping
the number a single constant and recording the measurement as a follow-up rather than pretending it is
tuned.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `native-tile-data-cache`: "Native tile data cache capacity configured" — the capacity becomes
  per-surface and SHALL be configured by every surface that opens databases, with the effective value
  fixed once per client process (first configuration wins); the values are named tuned constants that
  exceed the library default.

## Impact

`:core` (new):
- `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt` — constants, `apply`, first-writer guard
- `core/src/test/java/com/naviveylin/core/NativeTileDataCacheTest.kt`

`:app`:
- `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` — configure the car capacity in
  `openMapDatabases` before `client.openDatabases(...)`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — replace the direct
  `client.setNativeDataCacheSize(NATIVE_TILE_DATA_CACHE_SIZE)` (`:1812`) and the constant (`:3845`)
  with the seam
- tests: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelStyleTest.kt` (existing cases point
  at the seam constant), plus a new provider-level test that drives `openMapDatabases` against a temp
  `maps/<region>/types.dat` and asserts the car capacity reached the client

Specs: `openspec/specs/native-tile-data-cache/spec.md` (one modified requirement + scenarios).

Guidelines: `guidelines/Design.md` §12 (single source of truth per concern — one seam instead of a
constant per surface); `guidelines/MapRendering.md` if it states the cache rule (checked in design).
No native/JNI change, so no submodule patch and no gitlink bump; no `:auto` code change (the car
surface reaches the native client through the `:app`-provided `AutoClientProvider`).

Change class: additive/behavioural, no API or storage change. Rollback: revert the touched files and
the spec delta; the phone path's behaviour is unchanged throughout, and a reverted car path returns to
the library default (today's behaviour).
