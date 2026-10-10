# Design

## Context

The phone shows a step's distance and duration from the native `[x km, y min]` bracket of a route description
line. The bridge advances its reference in `BeforeNode`, i.e. once per route **node** (~hundreds for a 17 km
route), while a line is emitted only for the ~19 nodes that carry a description. A step's bracket is
therefore the last geometry edge before the manoeuvre. Measured: `14 m` + `2 s` for one row of the 17,3 km /
19-step Dortmund→Bochum route (archived change `route-planning-session`, task 12.12). libosmscout's own Qt
client advances per emitted step (`libosmscout-client-qt/src/osmscoutclientqt/RouteDescriptionBuilder.cpp`,
`Callback::MkStep`), so the bridge is the outlier.

Affected: `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (submodule),
`libosmscout-client-java/java/com/framstag/libosmscout/client/RouteEntry.java` (submodule Java API),
`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` (bridge-module
override), `app/src/main/java/com/naviveylin/ui/route/{RoutePanelViewModel,RouteSummary,RoutePanel,RouteStepSegments}.kt`,
`app/src/main/java/com/naviveylin/ui/navigation/{NavigationDetailsOverlay,NextTurnOverlay}.kt`,
`app/src/main/java/com/naviveylin/ui/map/CarSessionSurface.kt`,
`core/src/main/java/com/naviveylin/core/DistanceFormat.kt`, plus tests and the two test fakes.

Native classification: **submodule patch, minimal and upstreamable** on branch `naviveylin-local`. The Java
API addition also lands in the submodule (its own Java sources) — the bridge module only mirrors the
override file it already overrides; the main repo pins the new submodule SHA (gitlink) after the submodule
commit, and the submodule must be left clean (AGENTS.md — native integration).

## D1 — Compute the per-step leg values natively, per emitted line

*Alternatives*
- **App-side from the polyline**: the app already matches each instruction's anchor to a polyline vertex
  (`RouteStepSegments.stepSegments`) and could sum haversine distances along the leg. Rejected: the app has
  no per-leg **time**; the engine's node times are the only source, and re-deriving them from an average
  speed contradicts the routing profile's per-road-type speeds.
- **App-side from the bracket, scaled**: rejected for the same reason, plus the bracket is presentation text.
- **Native, per emitted line** (chosen): the description callback already visits every node with its
  cumulative distance and time; advancing the reference when a line is emitted makes the delta the leg, which
  sums to the route total by construction.

*Risk*: none beyond the semantic change itself; the walk over nodes stays the same, so performance is
unchanged (no extra pass, no extra allocation besides the two vectors that the positions already allocate).

## D2 — The app reads numbers, not the bracket

*Alternatives*
- **Keep parsing `[x km, y min]`** (status quo): rejected by the owner. It is presentation text in a C++
  locale (dot decimal in a German UI), it cannot express "no distance with a time" unambiguously — a bracket
  such as `[2 s]` (leg below 10 m) lands the time in the distance slot of `parseStepDisplay`, which is the
  positional parser's blind spot today.
- **A structured per-step object list** (one JSON-ish record per step): rejected as more marshalling than the
  need, and it would duplicate the instruction text the lines already carry.
- **Two arrays on `RouteEntry`, index-aligned like the existing `instructionLats`/`instructionLons`**
  (chosen): `instructionDistances` (metres, the leg that ends at that line's manoeuvre) and
  `instructionTimes` (seconds, same leg). Both are dropped together (left `null`) when they cannot be aligned
  one-to-one with the instruction lines — the same rule the positions already follow, so a mismatch can never
  shift a later step. The app treats them as optional and falls back to the (now unit-classified) bracket
  parser when absent, which is also the rollback path for a submodule revert.

*Risk*: two more global-ref-free `jdoubleArray`s per route calculation — negligible against the existing
geometry arrays (thousands of doubles), and they are allocated only once per calculation, on the routing
thread, not per frame. `RouteEntry.java` exists in both the submodule's Java sources and (as an override
source set) the bridge module — both must carry the fields or the field lookup fails; that is a checklist
item, not an uncertainty (the override inventory is explicit in AGENTS.md).

## D3 — Reference point: the previous emitted line

*Alternatives*
- **Keep the previous node** (status quo): rejected — it is the defect.
- **Advance in `AfterNode`**: identical to the status quo for lines, since the postprocessor calls it for
  every node as well (this is what upstream's debug callback does; its per-step values are a *secondary*
  debug column next to the cumulative distance, which is why it never mattered there).
- **Advance when a line is emitted** (chosen): matches `MkStep`, makes the sum telescope to the route total,
  and makes the values agree with the leg the analysis highlights.

*Consequence to accept*: when two lines are emitted for one node (e.g. start and a turn on the same node), the
second carries a zero leg and therefore no values. That is the honest reading of a zero-length leg, matches
"the start line owns no leg", and is what the analysis already does for a step without a position. The
alternative — merging the pieces of one node into a single line — changes the step list's contents and is out
of scope. *Risk*: an existing consumer that relied on a fragment value; none in tree (the string is display
only, and `currentStepOf` only tests emptiness).

## D4 — `RouteInstruction.timeTo` becomes the leg time (site 3)

The instruction list's `timeTo` uses the same per-node delta (`CollectCallback::SegmentTimeSeconds`), so the
navigation details list shows seconds where a leg time belongs and the car's per-manoeuvre arrival estimate
(`NavigationTemplateMapper.stepArrivalMillis = now + timeTo`) lands seconds away. Chosen: `timeTo` is the
travel time of the leg ending at that instruction's manoeuvre, advanced per emitted instruction — the same
rule as D3, and it makes the value agree with the route summary's, which `navigation-status-details` already
requires. *Risk*: a longer value is displayed in the car hint; the requirement ("the arrival time") is
unchanged, only the value becomes true.

## D5 — The next instruction's time is the remaining time of its leg

*Alternatives*
- **Report the whole leg time** (status quo after D4): the arrival estimate stays at its initial value while
  the manoeuvre is approached, next to a distance that does shrink — an incoherent row, and a visibly drifting
  ETA in a city.
- **Re-walk the description per position update**: rejected — a per-fix cost on the navigation path for a
  value that is linear in the remainder.
- **Scale the leg time by the remaining fraction of the leg** (the first idea): it needs the leg's own start,
  and the walk that produces the next instruction begins at the *current* node
  (`RouteInstructionAgent::Process` → `GenerateNextRouteInstruction(routeNode, last, …)`), so that start is not
  in the walk. Its `legDistance`/`timeTo` for the first emitted instruction would be a
  cumulative-from-the-route-start number — the very defect being fixed.
- **Interpolate the description's own node times at the current position** (chosen): `time(nextNode) -
  (time(node) + abscissa · (time(nextNode+1) − time(node)))`, using the same `abscissa` the remaining distance
  already uses, clamped to `>= 0`. It reaches zero at the manoeuvre, shrinks while the leg is driven, and
  needs no value the walk does not have.

*Consequence to accept*: an instruction whose leg began before the walk started (the first emitted instruction
of a windowed walk) reports unknown (0) values — a leg that is not in the walk cannot be measured, and a
plausible-looking number would hide that.

*Risk*: an unsnapped fix keeps the node's own time (already the case for the distance); both values are
clamped, so no negative time is published. A leg of zero length reports zero, i.e. "unknown", which the Java
API documents.

## D6 — App-side aggregation and formatting

- `RouteStepDisplay` gains the numeric distance (metres) and duration (seconds) for its step, taken from the
  arrays by the line index; `parseStepDisplay` keeps the instruction text and becomes the fallback that also
  classifies each bracket token by its unit (`km`/`m` vs `s`/`min`/`h`) instead of by position.
- Values are formatted by the app: `formatDistanceNumber` (locale-aware, already shared) and a new
  `formatStepDurationText` in `:core` with a seconds branch below a minute. *Alternatives*: extend the existing
  `formatDurationText` — rejected because it is shared with the route's total duration, the notification and
  the ETA strings, where "0 min" for a sub-minute total is acceptable but a seconds form is not wanted;
  format in the UI layer per surface — rejected, it would fork phone/car formatting (guidelines/Design.md §4,
  one shared formatter per value).
- The diagnostics entry (`RoutePanelVM: route analysis steps=… sumM=… totalM=… maxErrM=…`) and the divergence
  warning are computed when a route arrives (once per calculation, on the view model's own dispatcher) and
  written through `DiagnosticsLog`, which buffers and writes on its own worker thread — never the caller's
  (AGENTS.md). Numbers only, no coordinates (spec `auto-diagnostics`).
- The navigation details list shows each row's own segment: the leg distance from the bridge's per-instruction
  `legDistance` (agreed with the owner 2026-10-04; without it, row 0 of a list that has been popped from the
  front carries a route-start distance and *no* row could be trusted) and the leg time from `timeTo`. No third
  field with the name confusion of `distanceTo`; `distanceTo` keeps its "remaining distance for the next
  instruction" contract, which the next-instruction walk and the phone's next-turn overlay rely on.

Threading and lifecycle: no new component, no new coroutine scope, no new lifecycle owner. The bridge's
vectors are filled on the routing thread and marshalled in the existing JNI call; the app reads them on the
main thread in the existing `onSuccess`/`adoptRoute` state write. The aggregation is pure computation over
immutable arrays.

## Verification

Host (JVM/Robolectric, focused suites):
- `RouteStepDisplayTest` / new cases: numeric values win over the bracket; bracket fallback classifies by unit
  (`[2 s]` → no distance, `2 s` time; `[800 m]` → 800 m; `[1.2 km, 5 min]` → distance and time; `[0.0 km]`).
- A fixture test with a recorded route's per-step arrays (19 steps, 17 300 m total) asserting that the rows
  sum to the total within rounding and that a fragment array (the pre-fix values) fails it — this pins the
  app's aggregation, and it is *not* a guard for the native semantics (stated in tasks: only the device
  measurement proves those).
- `formatStepDurationText` cases (45 s, 90 s, 1 h 5 min) and locale cases (comma separator, metre unit).
- `NavigationDetailsOverlay`/mapper cases: the derived per-row leg distance and the `timeTo` from the state.
- Divergence-warning case: a synthetic array whose sum differs → exactly one diagnostics entry, no crash.

Device (the measurement that settles the change, `guidelines/Build.md` §10 recipe, `device-check` skill):
- Emulator with `north-rhine-westphalia`, `adb emu geo fix 7.4653 51.5136` (Dortmund), search `Bochum` → plan.
- Expected: header `17,3 km · 20 min`; the diagnostics line `RoutePanelVM: route analysis steps=19 sumM=… totalM=17300`
  with `sumM` within rounding of `totalM`; the step rows read leg-sized values (hundreds of metres, minutes)
  instead of `14 m`/`2 s`; the analysed row's numbers match its highlighted leg (logcat `RouteHighlight:
  range=…`); `adb logcat -s NaviVeylin` shows no per-step alignment warning and 0 fault-like lines.
- Car/AA: the navigation hint's arrival time for the next manoeuvre is no longer seconds away (host template
  read from a UI dump), and the navigation details list's per-step time matches the summary's.
- The step-row text is read from a `uiautomator dump` (text evidence), not judged from a screenshot; the change
  is numeric, so `tools/measure-highlight.py` is not the instrument here.
- Native rebuild must be proven: a submodule edit has silently not been rebuilt before — check the
  `app/.cxx/**/OSMScoutClient.cpp.o` mtime or force with `--rerun-tasks`.

**What the device runs actually produced (2026-10-05, phone AVD `emulator-5554`, NRW database).** The
instrumented `RouteInstructionPositionDeviceTest` carries the measurement instead of a UI dump: its five
cases route and navigate through the client, so they need no Compose surface, no taps and no rendering —
the first half of the bullet list above turned out to be unreachable on the available AVDs (the AAOS AVD's
car host owns the display with `touch NONE`, and `uiautomator dump` is denied by the tool policy), while the
numbers themselves came out of the instrumented test: `OK (5 tests)` in 36.8 s, with
`per-step values: steps=12 sumM=97416 totalM=72771 ratio=1.34 sumS=3702 firstM=0 maxLegM=73427 maxLegS=2547
legsCompared=10`, `live guidance: leg=4 beforeS=26 afterS=8 steps=16` and
`step-list parity: engineS=3653 perStepS=3702` (within 1.3 %). The **remaining-time shrink is therefore
device-verifiable without motion** — two synthetic fixes inside one leg make the engine report the same
manoeuvre twice — which also makes D5's interpolation falsifiable by mutation (R3). Still owed: the same
values read as *row text* and the car template that renders them.

Revert-checks (one mutation each, named failing case, restore, forced green):
- R1: advance the per-step reference in `BeforeNode` again → the device measurement must show `sumM` collapsing
  to a few hundred metres (and the fixture-based aggregation test must stay green, documenting that the fixture
  is not the native guard).
- R2: leave the arrays absent → the app must fall back to the bracket parser and still list the steps.
- R3: drop the remaining-time scaling for the next instruction → the "shrinks while the leg is driven" case
  must fail.

Gate: focused suites per edit, then one full both-flavor gate before the change is complete; the JNI module's
own tests and the main-repo unit tests are unaffected by the field addition beyond the two fakes.
