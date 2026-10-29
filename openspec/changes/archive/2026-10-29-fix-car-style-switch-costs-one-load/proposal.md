# Proposal — fix-car-style-switch-costs-one-load

## Why

**The condition, verified in the tree at base `821d9fc1` (2026-10-29).** Issue #16 (`TODO.md` §120) says the car
re-applies the `daylight` flag after every style switch, so one switch costs a stylesheet load plus a flag
reload of the whole set. It holds, in both car map screens:

| site | what it does |
|---|---|
| `auto/src/main/java/com/naviveylin/auto/MapScreen.kt:476-485` | the settings re-read computes `styleChanged = style != pushedForStyleSheet`, loads the style through `CarStyleApplier`, then calls `pushDark(force = styleChanged)` |
| `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt:469-478` | the same four lines |
| `auto/src/main/java/com/naviveylin/auto/CarDaylightApplier.kt:28-31` | `force` defeats the by-value dedupe: `if (!force && lastApplied == dark) return true` |
| `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java:102` → `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:2071` → `DBThread.cpp:416-431` | `setStyleSheetFlag` → `DBThread::SetStyleFlag` → `LoadStyleInternal(stylesheetFilename, stylesheetFlags)`, which reloads the main stylesheet **and** the basemap stylesheet (`DBThread.cpp:455-491`) |

So a style switch that the user initiated costs two loads of the whole set (plus a full re-render through
`rendererGate.invalidateStyle()`), where one would do.

**Root cause, one sentence.** The car screens re-apply the `daylight` flag after a style load because the tree
assumed a load might not carry the flag — an assumption the native side does not share: `loadStyleSheet`
already hands the load the flags that are enabled (`OSMScoutClient.cpp:2226-2235`, `GetStyleFlags()`), and
`DBThread::LoadStyleInternal` installs them onto the new style and its basemap
(`DBThread.cpp:455-460`, `makeStyleConfig` -> `StyleConfig::AddFlag` at `DBThread.cpp:366-369`).

**The native check issue #16 asks for, answered from the tree.** The item left the force in place because
"removing the force assumes a style load carries the database's stored flag", unverifyable without a device. The
tree states it in code: `Java_com_framstag_libosmscout_client_OSMScoutClient_loadStyleSheet`
(`OSMScoutClient.cpp:2199-2245`) reads `data->dbThread->GetStyleFlags()` and passes that map into
`LoadStyle` (`OSMScoutClient.cpp:2229-2235`), and its own comment says so — "Keep the currently enabled style
flags (e.g. "daylight") applied to the newly selected style, mirroring DBThread::SetStyleFlag behavior". The
JavaScout suite already pins the surrounding contract in `OSMScoutClientStyleTest.testStyleFlagsSurviveSwitch`
(`app/src/main/cpp/libosmscout/JavaScout/src/test/java/com/framstag/libosmscout/client/OSMScoutClientStyleTest.java:154-163`:
a flag is set before the switch and the switch succeeds), but that suite is not part of this project's JVM gate,
so its assertion cannot carry the evidence for an app-side change; the app-side consequence is what this change
pins in a case the gate runs.

**Red test.** `aStyleSwitchLoadsTheStylesheetSetOnce`
(`auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt`), JUnit XML `evidence/red-on-head.xml` —
`tests="6" skipped="0" failures="1" errors="0"`, timestamp `2026-10-29T13:32:20.231Z`, run
`./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.MapScreenTest" -PforceTests --no-build-cache`:

```
java.lang.AssertionError: the style load carries the daylight flag — the switch adds no flag
application, got [(daylight, true), (daylight, true)] expected:<1> but was:<2>
```

The case drives the browse car map through a style switch (`standard` -> `cycle`, picked up by the periodic
settings re-read after a 5 s throttle) and pins the cost of that switch: one stylesheet load per style, and no
`daylight` flag application after it.

**Surface, counted before the plan was fixed** (`evidence/surface-on-head.txt`, all commands run on the base
tree):

- `grep -rn "force = styleChanged" auto/src/main/java` -> `2` (both map screens).
- `grep -rn "pushedForStyleSheet" auto/src` -> `6` lines in those two files (the field, its write and the
  `styleChanged` comparison) — dead once the force goes.
- `grep -rnE "requestDaylightPush|needsPush|daylightApplier\.apply" auto/src/main` -> `11` lines across five files
  (`CarDaylightApplier.kt`, `RendererGate.kt`, the three car screens): the `force` parameter's whole plumbing,
  which no caller passes `true` to once the two screens stop passing `styleChanged`.
- `grep -rn "force" auto/src/test/java/com/naviveylin/auto/{CarDaylightApplierTest,RendererGateTest}.kt` -> `8`
  lines: one case (`forceRepushesEvenAfterSuccess`) and one case's two assertions exist only to pin the force.
- `FakeMapScreenClient` already records `loadStyleSheet` names and `setStyleSheetFlag` pairs
  (`auto/src/test/java/com/framstag/libosmscout/client/FakeMapScreenClient.kt:23,34`), so no fixture is realigned.

surface: 2 cases rewritten, 1 case removed with its subject, 1 Gradle case added, 0 Gradle cases rewritten, 0 fixtures realigned — `grep -rn "force = styleChanged" auto/src/main/java` (`2`), `grep -rn "pushedForStyleSheet" auto/src` (`6`), `grep -rnE "requestDaylightPush|needsPush|daylightApplier\.apply" auto/src/main` (`11`), `grep -rn "force" auto/src/test/java/com/naviveylin/auto/CarDaylightApplierTest.kt auto/src/test/java/com/naviveylin/auto/RendererGateTest.kt` (`8`), evidence `evidence/surface-on-head.txt`.

**Owning capability and guideline section.** The behaviour is a style switch's cost on the car, which
`openspec/specs/map-styles/spec.md` owns (it already carries the phone's rule through
`MapCanvasViewModel`'s `(style, daylight)` dedupe key) and whose scenario this change adds. The guideline section
that states the rule and the assumption behind the force is `guidelines/MapRendering.md` §15
"Day/Night (daylight flag) contract" — its warmup bullet (`guidelines/MapRendering.md:845-851`) and its load
budget bullet (`guidelines/MapRendering.md:859-871`, the sentences "A platform-driven surface (Android Auto)
may apply the flag once more after a **style load it performed** … `TODO.md` §120 is the native check that
would let that re-application go"). Both are rewritten in this change.

## What Changes

- `MapScreen.kt` / `NavigationScreen.kt`: the settings re-read applies the style and nothing else — no
  `pushedForStyleSheet`, no `styleChanged`, no forced flag push after the load.
- `MapScreen.kt` / `NavigationScreen.kt`: a `databasesReady` flag holds the first `daylight` push back until a
  style load succeeded — that load is the DB-open proof — and applies it there, once (round 2; see below).
- `CarDaylightApplier`, `RendererGate.DaylightPush` and the three car screens' `pushDark`: the `force`
  parameter is removed with its last caller, so the applier keys on the `daylight` value alone (the phone's
  key minus the style name, because the native load already carries the flag).
- `guidelines/MapRendering.md` §15: the force-after-a-load sentence is replaced by the native fact (with its
  `file:line`), and the warmup bullet states what re-applies the flag once the databases are open (the deferred
  first push, not a repeat after a load).
- `openspec/specs/map-styles/spec.md`: one modified requirement, "A style switch loads the stylesheet set
  once", with two scenarios naming the two cases.

## Round 2 — the warmup window the first draft left open

Round 1's review blocked the draft on one thing: it deleted the only re-application that followed a *proof* that
the databases were open, and claimed in §15 that none was needed. The claim was wrong. A car map screen can
exist before the databases are open (`NavigationSession.kt:373-379` serves the root screen without waiting for
warmup, and `RootScreen.kt:127` pushes `MapScreen` with no warmup gate), the JNI discards the dropped push's
result (`OSMScoutClient.java:102` is `void` while `DBThread::SetStyleFlag` returns false before initialization,
`DBThread.cpp:422-424`), and the deleted force did fire on a **successful** load — which `DBInstance::LoadStyle`
only reports while every database is open (`DBInstance.cpp:35-37`).

The round-2 fix keeps what the change was for and pays that: instead of a re-push after every load, the first
flag application is **deferred** behind the same proof. `databasesReady` starts false, `pushDark` returns while
it is false, and the style apply's success branch — which already exists — sets it and calls `pushDark()` once.
The value dedupe does the rest: a style switch loads the style, finds the flag already applied and pushes
nothing. Net cost per startup stays one load of the set (the push that used to be dropped is now not attempted),
a switch stays one load, and the warmup window has no lost push. `FreeDrivingScreen` applies no style of its own
and so has no such proof; it is unchanged and the gap it keeps is filed as issue #705.

## Impact

- Two car screens, one applier, one gate data class, one guideline section, one capability spec, three test
  classes. No native change, no new dependency, no public API.
- Behaviour: a user-initiated car style switch costs one load of the stylesheet set instead of two, and the
  night variant still reaches the map (the load installs it). The day/night *change* path is untouched.
- Risk, stated: a car screen that never loads a style successfully applies no flag — a stylesheet the native
  side rejects fails the DB-open proof too, so a session that only ever has a broken stylesheet draws no
  day/night variant. The map has no style in that state, the failure is already surfaced through the car's
  style-load notice, and a later successful load (a different style picked in the settings) applies the flag.
