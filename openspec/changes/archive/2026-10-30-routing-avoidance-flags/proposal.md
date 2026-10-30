# routing-avoidance-flags

## Why

The routing profile can avoid toll roads, ferry routes and unpaved roads — the bridge implements all three
(`routing-profile`, shipped in the submodule) — and the app asks for none of them: both of its
`RoutingProfile` constructions pass the vehicle and its maximum speed, and the avoid flags keep their
`false` defaults. A user who wants a route without tolls has no way to say so, on either surface.

The setting belongs next to the per-vehicle maximum speed, which `routing-max-speed` already owns: same
options sheet, same persistence, same rule that the phone edits and the car reads.

## What Changes

- Three persisted phone settings — avoid toll roads, avoid ferry routes, avoid unpaved roads — all
  defaulting to off, so a settings file written before they existed keeps today's behaviour.
- One builder produces the routing profile for a vehicle from the stored values, so no calculation path
  can carry the maximum speed and forget an avoidance flag.
- Every calculation path uses it: the route panel's calculation, the navigation engine's acquisition for a
  car destination, and the engine's reroute. The car reads the same values the phone edits.
- The options sheet shows the three switches beside the vehicle speed.

## Impact

- Affected capability: new `routing-avoidance`; it depends on `routing-max-speed`'s persistence and editing
  rule, and on the submodule's `routing-profile` for what the bridge does with the flags.
- Affected code: `AppSettings` and its mapping, a routing-profile builder beside `maxSpeedKmhFor`, the two
  `RoutingProfile` call sites, the map's UI state and settings view model, and the options sheet.
- Not affected: the car's screens (no avoidance control there — the phone edits, the car reads) and the
  native bridge.
- Honest expectation: on a map database that records no toll information, the toll switch changes nothing;
  that is a property of the data, not of the setting, and the spec states it rather than hiding it.
