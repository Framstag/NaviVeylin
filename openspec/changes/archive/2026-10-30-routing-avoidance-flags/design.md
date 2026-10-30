# Design — routing-avoidance-flags

## Context

`AppSettings` already carries `carMaxSpeedKmh`, `bicycleMaxSpeedKmh` and `footMaxSpeedKmh`, and a single
extension `AppSettings.maxSpeedKmhFor(vehicle)` maps a vehicle to its speed so no call site maps the
fields itself (`routing-max-speed`, "Every calculation path carries the vehicle's maximum speed"). The two
places that build a `RoutingProfile` are `RoutePanelViewModel` and `NavigationEngine`.

The bridge accepts `RoutingProfile(vehicle, avoidTolls, avoidFerries, avoidUnpaved, maxSpeedKmh)`, and the
submodule's `routing-profile` spec now says what each flag costs. The app passes `false` for all three.

## Decision 1 — where the values live

**Chosen: three booleans on `AppSettings`, next to the per-vehicle speeds.**

The choices are not per vehicle — a user who avoids tolls avoids them in the bicycle profile too, and a
per-vehicle matrix would triple the sheet for a distinction nobody asked for. `AppSettings` is already
additive-by-default, which is what makes an older file decode without migration.

**Rejected: one enum per surface (car/phone).** The car does not edit avoidance, so there is nothing to
keep apart; `routing-max-speed` already settled that shape for the speed values.

## Decision 2 — how the call sites stay honest

**Chosen: replace `maxSpeedKmhFor` with a `routingProfileFor(vehicle)` builder.**

The failure this prevents is the one that produced this change in the first place: two call sites, each
constructing a profile from raw values, each free to forget a new field. A builder makes forgetting
impossible without deleting the call, and it keeps the vehicle→speed mapping in one place.

**Rejected: add a `flagsFor()` helper and let each site combine it.** Two maps to combine is exactly the
shape that drifts.

**Rejected: a settable default in the Java `RoutingProfile`.** Making the profile read global state would
put the app's storage policy inside the client library, which the bridge spec deliberately keeps out.

## Decision 3 — the sheet

**Chosen: three switches in the same section as the vehicle speed, phone only.**

The sheet is the surface that already owns "how should a route be calculated", and the car exposes no
equivalent — matching `routing-max-speed`'s "The phone is the only surface that edits them".

## What the user must not be promised

On the maps the app downloads, only the ferry flag has data behind it (fetching a ferry is a way type).
Toll and unpaved information reach a database only through the importer that records them, so those two
switches can be inert for a user until the provider rebuilds. The spec states that a choice the data cannot
express changes nothing — the honest contract — rather than implying the route changed.

## Verification

- Unit tests over the builder: each stored choice appears in the profile it produces, and the vehicle's
  speed still does.
- A test that both call sites go through the builder: the profile built for a car destination on the engine
  path equals the one the route panel builds from the same stored settings.
- The persisted-field test that `routing-max-speed` established: an older settings file decodes with all
  three off.
- The bridge side is already measured in the submodule (`routing-profile`), so no device run is owed for
  this change; a device check that a stored switch survives a restart is folded into the persistence case.
