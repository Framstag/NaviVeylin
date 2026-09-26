# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

---

## 83. The rail-widget tap fix is unit-verified but not confirmed on device — Found 2026-09-26 (reported on phone + head unit under Android Auto) and implemented by `fix-car-rail-widget-tap` (2026-09-26)

- **Reported** ℹ: while navigating, switching to another car app keeps the turn hint in the rail widget, but tapping it does nothing — the app never comes back to the car foreground (music players do). Phone + head unit under Android Auto (projection).
- **Cause** ✅: the ongoing notification had only one tap target — `PendingIntent.getActivity` at `openTargetActivity()`, which returned `CarAppActivity` only on automotive hardware and `MainActivity` otherwise, while `CarAppExtender` carried no `setContentIntent` at all. Per the car-app contract the host then falls back to the notification's content intent, so on projection the tap started a phone activity on the phone and the car screen never switched.
- **Fixed** ✅: `NavigationNotificationBuilder.carExtender` now sets a car tap target, built by `NavigationNotificationService.carOpenIntent` as `CarPendingIntent.getCarApp(…, CarAppService component)` — projection: a broadcast the host answers with `startCarApp`; AAOS: the `CarAppActivity` launch (the hand-built AAOS branch is gone, one car path for both). Unit evidence: `NavigationNotificationBuilderTest` 13/0 (4 new cases: car target present and distinct, phone target unchanged, absent without a caller target), `NavigationNotificationServiceTapTargetTest` 2/0 (projection broadcast, AAOS activity via `FEATURE_AUTOMOTIVE`), full suite 3453/0 (`:app` mobile 1191, `:app` automotive 1191, `:auto` 691, `:core` 354, JNI module 26).
- **Still open on device** ⏳: no car/AAOS target was attached at implementation time (`adb devices` empty), so both on-device checks of `fix-car-rail-widget-tap` (tasks 4.2/4.3) are pending. Recipe: phone projecting to a head unit, start navigation, switch to another car app, tap the rail widget → the car screen returns to the app with guidance still running, `adb logcat -s Diag/SESSION Diag/HOST` shows the session handling the incoming intent (`action=android.intent.action.VIEW`) with no `HOST` rejection, and the phone-shade tap still opens the phone UI; then the same tap on an AAOS AVD/head unit (automotive flavor) for the migrated path. Re-close this entry when the runs are recorded in the change's tasks.

---
## 82. The native search-scope diagnostics were dropped with the branch's last diff — Found 2026-09-26 during the libosmscout merge / PR #1773 closure (`update-to-current-libosmscout-master`)

- **Observed** ℹ: `naviveylin-local`'s last difference from upstream `master` was `libosmscout-client-java/src/OSMScoutClient.cpp` (+35/-7) and consisted of 13 `osmscout::log.Info()` diagnostic lines (ResolveSearchScope parent chain + expansion, the `searchLocations: scope for db has …` line, resolveAdminRegion db/bbox/service/reverse-lookup, getAdminRegionScopeName) plus a behaviour-identical restructure of the multi-database scope selection. It was dropped (submodule `a50ae3b15`, parent gitlink `4ae2c13`), so the branch now equals upstream `master` and PR #1773 was closed as superseded (empty diff).
- **Consequence for an existing task** ⏳: `fix-compound-name-matching` task 5.4 asks the on-device run to grep `adb logcat -s NaviVeylin` for the native `searchLocations: scope for db has …` line. That line no longer exists in the pinned submodule; the surviving evidence for the same decision is the Kotlin side (`MapCanvasViewModel` logs `resolveAdminRegion(...) -> handle=…` and `getAdminRegionScopeName(handle=…) -> '…'`, both through `android.util.Log`, not the `NaviVeylin` tag). Update that task's recipe before the device run, or accept the Kotlin-side logs as the evidence.
- **Fix candidate if the detail is wanted again** ℹ: add the per-database scope lines as `osmscout::log.Debug()` (gated, so a release build stays quiet) in a focused upstream PR — not by re-forking the file; the `log.Debug(true)` switch is documented in `AGENTS.md` (native logging) and the app's bridge forwards them to the `NaviVeylin` tag.
- **Related** ℹ: `TODO.md` §81 records the same pattern for the matcher test's allocation-cost case; both are "the merge resolved a file to upstream and a local diagnostic went with it".

---
## 81. The word-matching allocation-cost case did not travel upstream with the matcher work — Found 2026-09-26 during the libosmscout merge of PR #1849 (`update-to-current-libosmscout-master`)

- **Observed** ℹ: PR #1849 merged our `fix-compound-name-matching` matcher work into upstream `master` (`aaa437a16`, commits `7a036fa51` "feat: match query words across separators in names" + `197a5c05c` "fix: make the new matcher tests portable and non-duplicated"). Upstream's `Tests/src/StringMatcherTest.cpp` carries the same case set as our `naviveylin-local` copy, but **not** the allocation-cost case and its counter harness: our copy is a 331-line fork of upstream's 189-line file, and the only content unique to ours is `TEST_CASE("A substring hit does not split the candidate into words")` plus the `STRINGMATCHER_HAVE_ALLOCATION_COUNTER` block that replaces global `operator new`/`delete` to count allocations (so a substring hit must not pay for splitting the candidate into words).
- **Why it is listed** ℹ: the merge resolved that file to upstream to stop it conflicting on every update (`git diff origin/master` for the file is now empty, and the branch's residual dropped from 14 files / +1042 -18 to 1 file / +35 -7). The cost claim is therefore no longer pinned anywhere: `git grep CountAllocations origin/master -- Tests/` returns nothing.
- **Preserved** ✅ at submodule commit `eb0ac0ff2` ("tests: measure the StringMatcher cost claim only where it is measurable"), reachable in the submodule's history; restore locally with `git -C app/src/main/cpp/libosmscout checkout eb0ac0ff2 -- Tests/src/StringMatcherTest.cpp`. The harness needs care in CI (the local version already had `SKIP` paths for sanitizer runtimes and shared-library/DLL builds, which is the likely reason it was not part of the PR).
- **Fix candidate**: re-add it as a focused upstream PR on top of upstream's file (the counter harness + one case), so the cost claim is pinned where the matcher now lives; do not re-fork the whole test file.
- **Adjacent state from the same merge** ℹ: upstream **deleted** `origin/fix-compound-name-matching` in the submodule after merging it, and the submodule's `openspec/changes/fix-compound-name-matching/` artifacts are now upstream's copies (our local stubs were replaced). The parent repo's own copy of that change (16/20, device checks 5.2/5.4-5.6 pending) remains the tracker; when it archives, it must not resurrect the submodule stubs.

---
## 80. The generic details title is the hardcoded literal "Location" — Found 2026-09-25 on `emulator-5554` (de-DE) during `fix-comma-decimal-coordinate-entry` (out of scope, i18n gap)

- **Observed** ℹ: a `geo:` deep link to a coordinate with no address and no object name shows the details sheet title as the English "Location" on a German device, while the row labels around it are German ("Adresse:", "Gebiet:", "Anzeigen"). Source: `core/src/main/java/com/naviveylin/core/details/DetailsResolver.kt:128` — `return nameHint?.takeIf { it.isNotBlank() } ?: "Location"` is a Kotlin literal in `:core`, so it is not a resource and cannot be translated. The same string is the title on the car details screen (`auto/DetailsScreen.kt` uses the same resolver) and is pinned by specs/tests as the literal (spec `enhanced-details-sheet` — Coordinate label falls back to generic; `DetailsResolverTest.titleFallsBackToGeneric`, `LocationDetailsDialogComposeTest.coordinateLabelTitleShowsGenericLocation`).
- **Why it is a gap** ℹ: `i18n-l10n` ("All user-facing text is translatable") allows no hardcoded user-facing literal, and the `HardcodedText` lint / `checkHardcodedStrings` gate only inspects `:app` and `:auto` UI code — a literal in `:core` is invisible to both. Same family as §30 (hardcoded notification strings).
- **Fix candidate**: give the resolver an injected generic-title string (`resolveTitle(input, nameHint, genericTitle)`) or resource ids per module, with the phone passing `R.string.location_title`-style resources (a `values-de` entry included) and the car `:auto` equivalent; update the two tests that pin the literal and the `enhanced-details-sheet`/`auto-destination-details` wording. Touches both surfaces, so it is its own change.
- **Adjacent device gap recorded here** ⏳: the car `DetailsScreen` coordinate row of `fix-comma-decimal-coordinate-entry` is covered by unit tests only — no car/AAOS device or head unit was attached during that change's on-device pass (the phone half ran on `emulator-5554`, API 37, de-DE). A car-side German-locale run on the AAOS AVD (`guidelines/Build.md` §10, TODO §40.45 harness limits) is still outstanding for that row.

---
## 79. The per-module unit-test task names in the run-tests skill are wrong for `:auto` and `:app` — Found 2026-09-25 during `fix-comma-decimal-coordinate-entry` (harness)

- **Observed** ℹ: `./gradlew :auto:test --tests "com.naviveylin.auto.DetailsScreenTest"` fails immediately with `Problem configuring task :auto:test from command line. > Unknown command-line option '--tests'` — `:auto:test` is not filterable (it is not the AGP unit-test task). The working invocation is `./gradlew :auto:testDebugUnitTest --tests "<fqcn>"`. `:core:test` accepts `--tests` (verified with the same change). The `.pi/skills/run-tests` table documents `:app:testDebugUnitTest` (which does not exist — the `dist` flavor dimension splits it, §40 item 30 / §71) and lists no `:auto` task at all.
- **Consequence** ℹ: a single-class run against `:auto` looks like a real build failure and costs a build cycle to diagnose; the skill is gitignored, so the fix has to happen on the machine that owns `.pi/skills/` (not in this repo).
- **Fix candidate**: correct the run-tests table to the flavor/task matrix that actually exists (`:app:testMobileDebugUnitTest`, `:app:testAutomotiveDebugUnitTest`, `:auto:testDebugUnitTest`, `:core:test`), or add a filterable aggregate task per module.
- **Evidence for the working names (2026-09-25)**: `:auto:testDebugUnitTest --tests com.naviveylin.auto.DetailsScreenTest --tests com.naviveylin.auto.NavigationTemplateMapperTest` → BUILD SUCCESSFUL, `DetailsScreenTest` 28 tests / 0 failures, `NavigationTemplateMapperTest` 59 / 0; `:core:test` 339 / 0; `./gradlew test` app mobile 1174 / automotive 1174 / auto 683 / core 339, all 0 failures.

---

## 77. Words joined without a separator stay unmatched (`Bahnhof Straße` vs `Bahnhofstraße`) — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, boundary of the word rule)

- **Observed** ℹ: `StringMatcherTransliterateToken` matches whole words across separators (space, hyphen, slash,
  dash), so a name that joins two words with *no* separator still hides from a query that spells them apart —
  `Bahnhofstraße` is one word for the matcher, so `Bahnhof Straße` does not match it (the native test
  "Words joined without a separator do not match" pins this deliberately).
- **Fix candidate**: a word-internal split rule (compare the query's word run against the *concatenation* of a
  candidate's words) — a broader tolerance that needs its own boundary analysis (risk of false positives, e.g.
  `Ost Straße` matching `Oststraße` in a different street than the user meant) and therefore a spec scenario for
  each direction.

---

## 76. The free-text index keys whole names, so a mid-name word is unreachable without a region token — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, index format)

- **Observed** ℹ: `TextSearchIndex` is a MARISA trie whose keys are complete normalized names, looked up with
  `predictive_search` (a prefix search), so a query matching a word in the *middle* of a name only works through
  the structured search, which needs a region or a default admin region. `Heinz` finds "Heinz-Hilpert-Theater"
  through the text index; `Theater` alone cannot.
- **Fix candidate**: token-keyed text index entries (one key per word plus the object reference) — an import-side
  change, so every installed map has to be re-imported server-side and re-downloaded before users benefit; the
  query side would then intersect the per-word candidate sets instead of prefix-matching one name.

---

## 75. Android Auto search passes no default admin region, so POI search needs the city in the query — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, own change)

- **Observed** ℹ: `provideAutoSearchProvider` calls `searchLocations(query, limit, OSMScoutClient.NO_ADMIN_REGION)`,
  and the native structured search visits POIs only inside an admin region matched from the query tokens
  (`LocationService::SearchForLocationByString`). A car query naming only a POI (`Hilpert Theater`, without
  `Lünen`) therefore finds nothing, while the phone searches inside the GPS-derived default region
  (spec: `location-search` — "Search scoped by current admin region").
- **Fix candidate**: give the car search the same default-region treatment (resolve the region from the last car
  GPS fix or the map viewport center), or add a bounded region-less POI pass; both need a cost check against the
  300 ms search debounce, since a region-less pass walks the POI index of the whole database.

---

## 74. The debug app ANRs on a cold emulator start with no map data, and the ANR trace is unreadable without root — Found 2026-09-24 during `compass-day-night-palette` task 7.3 (verification blocker, unverified)

- **Observed** ℹ: on a fresh `Pixel_8` AVD (API 34, `-no-window -gpu swiftshader_indirect`), the freshly installed
  `mobileDebug` APK starts, its native renderer initialises (stylesheet loaded, the usual empty-data
  "Unknown type …" warnings), then the app shows `Application Not Responding: com.framstag.naviveylin` and the map
  screen with its overlay column never appears — a screenshot of the running process contains none of the six
  compass palette fills, so the on-device half of that change could not be verified.
- **Not diagnosed** ✗: `adb root` does not take on this image (shell stays uid 2000) and `/data/anr/*` is
  permission-denied, so the main-thread stack is unavailable; the `am_anr` event was already rotated out of the
  events buffer. Whether this is an emulator/no-data artifact or a product defect is therefore unknown.
- **Fix candidate**: reproduce with a basemap installed and a writable trace (`adb shell setprop` or a `userdebug`
  image), and compare against the previous release build on the same AVD to separate a real regression from the
  environment. Until then treat any on-device colour/lifecycle verification of that change as outstanding.

---

## 73. Two phone overlays still use fixed colors with no presentation branch — Found 2026-09-24 during `compass-day-night-palette` (out of scope, own change)

- **Observed** ℹ: `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt:91` (`gpsMarkerColor = Color(0xFF1A73E8)`) and
  `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:241-242` (accuracy fill 10 % / border 40 % of
  `#4A90D9`) are literals with no presentation branch, while the vehicle marker right next to them branches through
  `core/VehicleMarkerGeometry`. The compass was the only status-carrying overlay, so `compass-day-night-palette`
  fixed that one and left these alone.
- **Duplication** ℹ: the mini-map's marker blue repeats the vehicle marker's day blue — a single-source-of-truth
  violation (`guidelines/Design.md` §12) that also means a palette change has to be made twice.
- **Not caught** ✗: no spec scenario or test covers overlay colors on the mini map or the accuracy circle in dark
  presentation.
- **Fix candidate**: route both through the established palette-branch convention (the marker geometry object for the
  marker, a small palette function for the accuracy ring) and add the dark case to whichever spec owns them.

---

## 72. The compass needle is stroked in raw pixels, so it thins on high-density screens — Found 2026-09-24 during `compass-day-night-palette` (out of scope, sizing/geometry)

- **Observed** ℹ: `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` draws both needle halves with
  `strokeWidth = 3f` — device pixels, not dp — while every other dimension in the same file is density-aware
  (`needleLength = 10.dp.toPx()`, rim `1.dp.toPx()`, canvas 48.dp). On a 3.5× density screen the needle is ~0.86 dp
  wide, i.e. visibly thinner than on a 1× screen.
- **Not caught** ✗: no test asserts the needle's stroke width; `CompassButtonComposeTest` only pins the 56 dp layout
  and `CompassPaletteTest` only the colors, so a density regression is invisible to the suite.
- **Fix candidate**: use `3.dp.toPx()` (or a named dp constant) and pin it with a unit-tested geometry helper, the way
  the phone palette now pins contrast instead of leaving it to review.

---
## 71. Zoom-walk follow-ups from `fix-phone-zoom-animation-parity` — Found 2026-09-24 during that change (out of scope / pending device)

- **On-device cost unmeasured** ⏳: the phone now walks a magnification change the frame in hand cannot serve
  (`core/ZoomWalk`, `window = 0.25` levels, one step per landed render, `MapRenderer.requestRenderImmediate`).
  A four-level entry is ~16 renders. The car's measurement (1.5-4 s for the same 16 steps) does not transfer: the
  phone's tile path serves fractional steps from the per-level tile cache, so most steps are cheap and only the
  level crossings render natively - which the on-device task (change tasks 6.3) has to confirm before anyone
  considers design D7 Alt C (a larger `OVERRUN_FACTOR`, i.e. a bigger window and fewer steps) or D1 Alt B
  (clamp the display scale and land the remainder in one render).
- **Pinch commits now walk when their gap exceeds the window** ℹ: `updateMagnification` is the commit path for
  pinch, zoom buttons, keyboard shortcuts and the scroll wheel (only the POI camera fit passes `walk = false`),
  so any of them with a gap above 0.25 levels renders stepped frames instead of one. The spec's requirement is
  unconditional ("a change larger than the window SHALL be applied as a sequence of displayed steps"), and the
  walk's steps are cheap via the tile cache - but a manual large zoom-in (e.g. several wheel ticks) is a visual
  judgement that needs a device, not a unit test.
- **`:app` has no aggregate unit-test task** ℹ: `:app:testDebugUnitTest` does not exist (the `dist` flavor dimension
  splits it into `testMobileDebugUnitTest` / `testAutomotiveDebugUnitTest`), so the run-tests skill's documented
  command fails with "Ambiguous matches". Use the flavor-specific task, or add a `test` aggregate. Not touched here
  because the skill file lives in `.pi/skills/` (gitignored).
- **Before this change no test could obtain a rendered front buffer** ℹ: every ViewModel test was state-level because
  `initMap` + `advanceUntilIdle` never produced an emitted frame (`renderViewport` stayed null; not investigated
  further). The change added the `MapCanvasViewModel.publishRenderedFrameForTest` hook (the same path the frame
  collector uses) so the walk is deterministically testable; a real-render harness for the ViewModel is still worth
  having for render-pipeline behaviour (why no frame is emitted is unexplained).

---

## 70. BROWSE still mixes the free-driving anchor preset into a street-pill placement, and an off-screen vehicle has no cue but the re-center button — Found 2026-09-22 during `fix-browse-recenter-visibility` (out of scope)

- **Observed** ℹ: (a) `MapCanvasScreen.kt` derives `pillAtTop = state.activeFollowAnchor.fy == 0.9` and moves the
  free-driving street pill to the top of the screen "so it never covers the vehicle marker" — in BROWSE too, using the
  **free-driving** anchor preset; browse framing no longer applies anchor presets at all (spec `map-modes` — Browse
  re-center), so the pill can sit at the top in browse for a preset that browse does not use. Cosmetic only; the pill
  and the button do not overlap.
- **Observed** ℹ: (b) when the vehicle is outside the viewport the marker overlay draws nothing (`projectMarker` returns
  null past `MARGIN_PX`, `LocationMarkerOverlay.kt`), so in BROWSE the derived re-center button is the only signal that
  a position exists. No edge arrow, no direction indicator.
- **Why it was left alone** ℹ: found while replacing the sticky `browseDrifted` flag; (a) is a placement question this
  change did not need to touch and a wrong fix would move the pill for the driving modes too, (b) is a new affordance
  with its own questions (where to draw it, how it clears the action/widget columns on phone and foldable, phone/AA
  parity).
- **Fix candidate**: (a) derive `pillAtTop` from the mode — in BROWSE the vehicle sits at the centre, so keep the pill
  bottom-centre (or key it off the centre preset) and leave the anchor-based rule to FREE_DRIVE/NAVIGATION; (b) a small
  viewport-edge indicator pointing at the vehicle, driven by the same projected offset the re-center rule already
  computes, with a `map-modes` spec delta and a phone/AA parity decision.

---

## 69. Location/FGS surface has not been checked against the Play location policy effective 2026-10-28 — Found 2026-09-22 during the regulatory review (`guidelines/Regulatory.md` §6/§9)

- **Scope of the change** ✗: the Play "Permissions and APIs that Access Sensitive Information" policy was updated 2026-04-15 and takes effect **2026-10-28**; the app's location surface predates it. Relevant deltas: (a) precise location is expected at minimum scope, with the **location button** as the recommended minimum for precise/one-time requests; (b) background location still requires the Permissions Declaration form, a ≤ 30 s demo video, prominent in-app disclosure and a privacy policy in-app **and** on the listing; (c) foreground-service location must be the continuation of a user-initiated action and must stop once that action completes; (d) **geofencing was removed** as an approved FGS use case (use the Geofence API).
- **Current surface** ℹ: `app/src/main/AndroidManifest.xml:6-7` declares `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (no `ACCESS_BACKGROUND_LOCATION`); `:102` `MapDownloadService` is `foregroundServiceType="dataSync"`; `:111` `NavigationNotificationService` is `foregroundServiceType="location"`. Continuous navigation with the screen off is exactly the FGS-as-background-location case the policy scrutinises, so the "user-initiated + terminated when the action ends" shape has to be demonstrable (the notification's `Stop` action and stop-on-arrival are the visible ends of that action).
- **Not verified** ✗: whether the app requests precise (fine) rather than coarse location where a coarse fix would do, whether a one-time precise request should use the location button, whether `MapDownloadService`'s `dataSync` type is still the right one under the current FGS policy, and whether the Play Data safety form currently matches what §68 lists.
- **Next step**: audit the request path (fine vs coarse, one-time vs ongoing), confirm the FGS start/stop shape against the policy text, then update the Data safety declaration + privacy policy/prominent disclosure if anything changed. `guidelines/Regulatory.md` §6 carries the requirement text and the review cadence.

## 68. Precise coordinates are written to Logcat and to the diagnostics buffer — Found 2026-09-22 during the regulatory review (`guidelines/Regulatory.md` §3/§9)

- **Observed** ℹ: 6-decimal lat/lon pairs are logged via `android.util.Log` in `ui/map/MapCanvasViewModel.kt:1035` (GPS fix), `:1440` (`resolveAdminRegion`), `:2382` (`onLongPress`), `:2471` (shared location), `ui/map/MapRenderer.kt:402` and `:455` (viewport centre), `navigation/AANavigationController.kt:167` (route start/destination), `auto/AutoInitialViewport.kt:37`, `auto/DetailsScreen.kt:280,285`.
- **Why it is a compliance item, not a style nit** ℹ: precise location is personal data. `com.naviveylin.core.DiagnosticsLog` buffers lines in memory and writes them to a file, and `exportTextAsync` hands the result to the user — so the file is personal data **at rest** and the export is a disclosure of it. Under GDPR/ePrivacy that needs a purpose and a retention bound, and under the Play Data safety rules the app must declare it; India's DPDP Rules would additionally require a minimum 1-year retention of such logs before erasure (`guidelines/Regulatory.md` §3). The debug stream itself is not the problem — the accumulated file and the export are.
- **What is not established** ✗: whether `DiagnosticsLog` already bounds retention/rotates, whether coordinates in the exported text are needed for the diagnostics use case at all (rounding to ~3 decimals or 5-6 significant digits would keep the diagnostic value and drop the precise fix), and whether the Data safety form mentions location in diagnostics.
- **Fix candidate**: decide what precision diagnostics actually need and round/redact at the call site or in the logger; give `DiagnosticsLog` an explicit retention bound (and document it in `guidelines/Regulatory.md` §9 and the About/diagnostics UI); state the resulting behaviour in the Data safety declaration. The car path additionally has to keep passing the host-fault-isolation rules (`HOST`/`SESSION` diagnostics tags) — a redaction must not remove the information those entries rely on.

---

## 67. `openDatabase` reports success for a path that does not exist — Found 2026-09-21 during `fix-native-database-open-race` (out of scope, own change)

- **Spec deviation** ✗: the JNI `openDatabase` (`OSMScoutClient.cpp:688-701`) never validates the
  path — it registers it and returns `JNI_TRUE` even when the directory does not exist. Spec
  `map-render` says the opposite: scenario "Open invalid map database" — "**WHEN** the map screen
  receives an invalid or missing map database path **THEN** `OSMScoutClient.openDatabase()` returns
  false **THEN** the system displays an error message: 'Could not open map database'". The phone's
  error branch (`MapCanvasViewModel.initMap` on `!opened`) is therefore dead code for a missing or
  bogus map directory: the map opens, and the render silently has no data for that database.
- **Not caught** ✗: no unit test covers `openDatabase` on a missing path; the JVM fakes return a
  configured boolean, so `openDatabaseResult = false` is set by the test rather than produced by the
  path check.
- **Why it was left alone** ℹ: found while implementing the path-list fix; `fix-native-database-open-race`
  deliberately keeps its contract "single open == a batch containing just that path" (a batch also
  registers without touching the filesystem) so the app's behavior for a deleted map directory is
  unchanged there. Changing it changes what the user sees (an error state instead of a silently
  data-less render), which belongs to its own change.
- **Fix candidate**: validate in the native layer — accept a path only when it is an existing
  directory, in `Register`/`RegisterAll` (then `openDatabase` and `openDatabases` agree), and cover it
  with a native test plus a `MapCanvasViewModel.initMap` test that asserts the "Could not open map
  database" state. Needs a `map-render` delta scenario for the batch form.

## 66. `hostbuild`'s JNI target is configured against a JDK that is not installed — Found 2026-09-21 during `fix-native-database-open-race` task 1.1 (harness gap)

- **Observed** ℹ: `ninja -C hostbuild libosmscout-client-java/src/libosmscout_client_java.so.1.1.1`
  fails with `fatal error: jni.h: Datei oder Verzeichnis nicht gefunden`; the compile line carries
  `-I/usr/lib/jvm/java-26-openjdk/include`, and this machine has java-11/17/21/27 (no 26). So the
  native JNI translation unit cannot be compiled through the host build, while the rest of
  `hostbuild` (libraries, tests) works.
- **Consequence** ℹ: a JNI-only change can only be checked by the Android build (slow, needs the NDK
  toolchain) or by a hand-rolled compile. Workaround used by the change: the meson compile line with
  `-I/usr/lib/jvm/java-21-openjdk/include ...` plus `-fsyntax-only`, which builds the whole TU in ~4 s
  with `-Wall -Wextra -Wpedantic`.
- **Fix candidate**: re-configure `hostbuild` against an installed JDK (or pin the Java path via
  `local.properties`/`JAVA_HOME` in the setup script) so an incremental JNI compile is available
  locally. Not a product defect — a developer-harness gap.
- **Adjacent** ℹ: no CMake host build directory exists on this machine (only the meson `hostbuild`),
  so a `ctest`-based native test run cannot be exercised locally even though `Tests/CMakeLists.txt`
  registers the tests (see `ki_processing_failures.log`, 2026-09-21).

## 65. Both processes grow by ~50 MB over a 10-minute car drive — Found 2026-09-21 during `fix-host-crash-residual-paths` task 8.2 (post-change baseline)

- **Observed** ℹ: on the AAOS AVD (`emulator-5556`, automotive debug, navigation active with a fix every 2 s for 10.5 minutes) `dumpsys meminfo` reports the app `TOTAL 227 MB -> 278 MB` (native heap `133 -> 187 MB`) and the templates host `TOTAL 299 -> 343 MB` (native heap `162 -> 215 MB`). No pressure symptoms in the same window: 0 `lmkd`/lowmemorykiller lines, 0 host crashes, 0 app fatals, 0 surface failures, 294 full renders.
- **Why it is not attributed to the change** ℹ: the change only *reduces* per-rebuild work (the lane-guidance bitmap is reused while its state is unchanged, the template distance comparisons are bucketed); it adds no buffer, cache or thread. The host-side growth is on Google's side of the IPC.
- **Not measured** ✗: the samples are single snapshots before/after the drive, the vehicle walked past the destination (so routes/tiles across a wide area were loaded), and the car path uses the library's default 25-tile cache per database (§63). A leak therefore cannot be separated from legitimate tile/cache growth from these numbers.
- **Next step**: repeat with a *stationary* session and a *repeat* of the identical route, sampling `dumpsys meminfo` every minute, so growth without new tiles can be told from cache fill. If the app side still grows, the candidates in §49 (per-render transient buffers) and §63 (the tile cache) are the first places to look.
- **Partially addressed 2026-09-21** ✅: the per-screen overrun buffer is now released on every screen stop (`fix-car-surface-ownership-and-host-callbacks`), so the car stack no longer retains up to four extra ~3.7–8 MB buffers while its screens sit stopped. §49 (per-render transient buffers) and §63 (the tile cache) stay open, and the stationary-route measurement above is still the next step.

## 64. The car template rebuild rate is still bounded by the arrival estimate, not by the distance buckets — Found 2026-09-21 during `fix-host-crash-residual-paths` (task 4.2/4.4)

- **Observation** ℹ: the change buckets the template rebuild to the *displayed* distance (both the distance-to-turn and the remaining route distance use the host's own rounding — 50 m steps below 1 km, 100 m above) and reuses the lane image while the lane state is unchanged, but `hasStateChanged` still compares `etaMillis / 1000`, and the native engine re-emits the arrival estimate on every position update in practice. The residual template rate is therefore the estimate's update rate, i.e. close to the position rate (~1/s while driving), not the distance-bucket rate. The lane-image allocation and its IPC payload per rebuild **are** gone.
- **Why not fixed here** ✗: bucketing the arrival estimate to the displayed minute (what the ETA card shows) would make the host's own countdown authoritative for up to a minute. Whether the host ticks that countdown itself is not known — the change's spec deliberately leaves the arrival estimate out ("a shifted estimate is displayed content") — so this needs a device measurement first: run navigation with the estimate bucketed to the minute and watch the ETA card for a frozen countdown (rail-widget card and the navigation template both).
- **Fix candidate**: `hasStateChanged` compares `etaMillis / 60_000` instead of `/ 1000`; measure `Diag/HOST`-correlated template refreshes per minute before/after and watch the card. If the host does not tick, keep the per-second comparison and consider publishing the remaining time only on manoeuvre/step changes instead.

---

## 46. Phone notification `Stop` action does not end navigation — FIXED in code by `background-navigation-notification` task 6.1 (2026-09-20, on-device re-check pending)

- **Implementation done 2026-09-20** ✅: new `:core` seam `NavigationStopRequests` (`core/src/main/java/com/naviveylin/core/NavigationStopRequests.kt`), implemented by `NavigationStateProvider`: `stopNavigation()` broadcasts instead of routing to one callback slot, and the phone `NavigationViewModel` + `AANavigationController` each collect it and stop their own navigation (idempotent). The provider's mirror became a per-source registry where the navigating source wins — an idle car registrant can no longer blank live navigation, and `ViewModel.onCleared` unregisters the dead phone surface. `NavigationViewModel.stopNavigation()` also clears the route panel (`setNavigating(false)` + `clearRouteFromMap()`) so the shade stop matches the in-app button, and the service logs `onStartCommand action=…`. New tests: `NavigationStateProviderTest` (5), `NavigationViewModelStopPathTest` (2), `NavigationNotificationServiceActionTest` (2). Evidence: mobile 1068 / automotive 1068 / core 302 / auto 516 tests, 0 failures; both debug APKs assemble; no new compiler warnings.
- **Still open on device** ⏳: tap `Stop` in the phone shade while navigating **with a car session live in the same process** (the original failure scenario) — confirm navigation ends, the notification withdraws and the route panel is left non-navigating. That re-run also re-opens `background-navigation-notification` task 4.1 and unblocks `car-turn-by-turn-rail-widget` task 7.4.
- **Residual, not fixed (same last-wins shape)** ⏳: `NavigationStateProvider.navigateTo` / `reportError` still route to the *last* registrant. Both controllers can route with the same JNI client, so a mis-routed call is not lost (unlike the stop command), which is why it was out of scope here. Fix candidate: reuse the new registry — route `navigateTo` to the navigating source, else the first registered one — or give `navigateTo` its own broadcast seam if double-routing ever becomes a risk.
- **Original finding 2026-09-20 on device during `background-navigation-notification` task 4.1 (phone, ongoing notification visible)** ✗: tapping `Stop` in the notification shade does not end navigation — guidance keeps running and the notification stays. The same path is gated for the car hint by `car-turn-by-turn-rail-widget` task 7.4 ("…the phone stop action still works"), so that task is blocked on this. Two causes are visible without a device:
  - (a) **`NavigationStateProvider` keeps ONE callback slot per command** (`app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt:32-46` — `stopCallback`, `navigateToCallback`, `reportErrorCallback`), overwritten by every `observe(source)` call. Two sources register in one process: the phone `NavigationViewModel.init` (`app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt:64`) and the singleton `AANavigationController.init` (`app/src/main/java/com/naviveylin/navigation/AANavigationController.kt:85`). The latter is resolved on **every car-session warmup** (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:328`, log step "Activating navigation controller"), so as soon as a head-unit/AAOS session starts after the phone app it owns `stopCallback` and the phone notification's action stops only the car controller's own (idle) engine. The same clobbering affects the mirrored `state`: the car controller's initial empty `NavigationState` is written into the provider when it subscribes, which can trip the service's active gate (`app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt:105-113`) and `stopSelf()` the phone notification mid-navigation.
  - (b) **Not at parity with the in-app stop**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:1991-1993` performs `navigationViewModel.stopNavigation()` **plus** `routePanelViewModel.setNavigating(false)` and `clearRouteFromMap()`; the notification path (`NavigationNotificationService.kt:69-79`) performs only `stateProvider.stopNavigation()` — the route panel keeps its navigating flag and the route stays drawn on the map.
  - **No diagnostics, no test**: the `ACTION_STOP_NAVIGATION` branch logs nothing, and nothing covers `onStartCommand` or the provider's callback routing (`NavigationNotificationBuilderTest` only asserts the `stopIntent` shape), so logcat cannot separate "intent never delivered" from "wrong target".
  - Fix candidates (both taken 2026-09-20, see the first bullet): route the stop to the source that is actually navigating (the provider now keeps a per-source registry where the navigating source wins the mirror and the stop broadcasts), put the shared stop in one place so the route-panel cleanup cannot be skipped, log the incoming action in `onStartCommand`, and cover the provider routing + service action with tests.

## 42. `AANavigationController` starts an endless ticker per instance and has no release hook

- **Noticed 2026-09-20 during `fix-auto-unit-test-heap-overflow` task 1.6 (scope scan of `:app`/`:core`)** ℹ: `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt:52` owns `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and launches, in `init`, an endless `scope.launch(Dispatchers.Default) { while (true) { delay(SPEED_STALE_TICK_MS); … } }` (`:119-129`, 1 Hz). Nothing cancels that scope — the file contains no `scope.cancel()`, no `release()` and no `@PreDestroy` — so every instance keeps its stale-speed ticker alive for the process lifetime. Same defect class as the `AutoMapRenderer` leak that `fix-auto-unit-test-heap-overflow` fixed, but **not a measurable leak today**: the loop retains no large buffer, ticks once per second, and only 5 instances are constructed across the `:app` tests (`AANavigationControllerStepIndexTest` line 41 and `AANavigationControllerRoadInfoTest` line 30, neither with an `@After`), while `:app` runs 1054 tests per flavor in one JVM at its declared 1024 MB fork budget (`guidelines/Build.md` §6) without an OOM. Fix candidate: cancel the scope on the stop/destroy path (or add a `release()` the tests call in `@After`), mirroring `AutoMapRenderer.shutdown()` plus the teardown rule in `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`.
- **Adjacent, weaker observation from the same scan** ℹ: the other `:app` classes that own a scope are not comparable — `DarkModeController` (`app/src/main/java/com/naviveylin/data/DarkModeController.kt:26`, `Dispatchers.IO`, constructed once per test in ~13 test classes) and `NavigationStateProvider` (`app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt:23`, `Dispatchers.Main`, constructed in ~13 test classes) launch only short-lived jobs and hold no buffer, so a finished test leaves nothing running; they merely have no release hook. `:core` has no scope-owning class at all. Hygiene only — no action needed unless one of them grows a loop.

## 34. Car search opened from the root/history screens has no distance reference

- **Noticed 2026-09-19 while implementing `search-result-ranking`** ℹ: the spec's distance reference for the car is "last known GPS fix, else the current car map viewport center, else none (order by tier and quality only)". Only `MapScreen` can supply that center (it owns the renderer gate, so it passes `{ rendererGate.renderer.value?.markerViewport() }`); `RootScreen` and `SearchHistoryScreen` push `SearchScreen` without any map, so their results are ordered by tier and quality and show **no distance at all** — spec-legal, but a driver comparing results from the root list has no proximity signal. Fix candidate: publish the last rendered viewport center through a small shared provider (e.g. alongside `AutoLocationProvider`, updated by `NavigationSession`/`AutoMapRenderer` whenever the displayed center changes) and pass it from all three `SearchScreen` call sites. Not a defect of this change — the "neither exists" case is specified and covered by tests.

## 30. Notification-channel names and neutral navigation strings are hardcoded Kotlin constants

- **Noticed 2026-09-18 during `car-turn-by-turn-rail-widget`** ℹ: the new automotive channel had to be named through a string resource (adding `values-de` entries for the name, description and the car hint text), which exposed that other notification channels and the phone formatter still carry user-facing text as Kotlin constants: `MapDownloadService.CHANNEL_NAME` (`app/src/main/java/com/naviveylin/service/MapDownloadService.kt`, a plain `private const val` passed to `NotificationChannel`), the navigation channel name that this change converted to `navigation_notification_channel_name`, and `NavigationNotificationContent.TITLE_NAVIGATION_ACTIVE` / `TITLE_FREE_DRIVING` (`app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt:63-64` — the formatter file was renamed since this note) plus the `"Offroad"` fallback in `currentRoadText` (`app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt:245`). None of them are translatable today, yet `GermanTranslationCompletenessTest` and the app's `checkHardcodedStrings` gate only look at resource files and at Compose/`setTitle`-style literals — the gate also misses `NotificationChannel(id, CONSTANT, …)`. Fix candidate: move the remaining names/neutral labels into resources with German translations, and (optionally) teach `checkHardcodedStrings` to flag `NotificationChannel(` name arguments so the next channel cannot ship untranslated.

## 31. Coordinate text is formatted with the default locale at several UI call sites — FIXED by `fix-comma-decimal-coordinate-entry` (2026-09-25)

- **Fixed ✅** with §45 by the same change: every coordinate display site now goes through `core/CoordinateFormat.kt` (`FavoritesSheet` group row, `LocationDetailsSheet` via the `coordinates_format` resource pattern, `FavoritePickerDialog`, `MapCanvasViewModel` label fallback and long-press label, car `DetailsScreen` row), so a German device shows `51.50000, 7.40000` instead of the ambiguous `51,50000, 7,40000`. `guidelines/UI.md` ("Locale-aware numbers") now names the coordinate carve-out. Removed when the change is archived.
- **Noticed 2026-09-18 during `car-turn-by-turn-rail-widget`** ℹ: the new destination fallback for the car trip metadata needed a coordinate string and the repo showed two conventions: `MapCanvasViewModel.kt:2332` formats the selected-location *label* with `String.format(Locale.US, "%.5f, %.5f", …)` for stability, while `FavoritePickerDialog.kt:135`, `FavoritesSheet.kt:619` and `MapCanvasViewModel.kt:2357` use `"%.5f, %.5f".format(lat, lon)` with the default locale — in German that renders `52,51628, 13,37770`, and the comma between the two numbers makes the pair ambiguous to read. The car mapper deliberately uses `Locale.US`. Fix candidate: one shared `formatCoordinates(lat, lon)` helper in `:core` using `Locale.US`, used by every display site (and by the map logs that print the centre).

## 32. Phone notification neutral strings duplicated by the car hint resources

- **Introduced knowingly by `car-turn-by-turn-rail-widget` (2026-09-18)** ℹ: the car hint resolves its neutral fallback through the new `core` string `nav_hint_neutral` ("Navigation active"), while the phone formatter keeps the identical wording as the Kotlin constant `TITLE_NAVIGATION_ACTIVE`. The duplication is deliberate for now — the phone path had to stay byte-identical (its tests pass unedited) and the car path needed a resource for the i18n gate — but the two must not drift. Fix candidate: route the phone neutral title through `nav_hint_neutral` too (one formatter, one wording, German included) once the phone notification tests are allowed to change.

## 29. AA follow jumping — open points handed over (2026-09-17, stable-version handover)

Status: all four defects fixed and device-verified (2026-09-18, AAOS), including P2 and P3. `overlay-projects-against-displayed-frame` and `aa-follow-framing-and-zoom-parity` (27/27) are both archived. What remains below: one open decision (the auto-zoom first commit), the deferred P4 option, and the device-verification method.

**Open points:**

1. **⏳ The auto-zoom's FIRST commit jumped straight to the speed target — FIXED in `aa-entry-zoom-animation` (2026-09-19), device verification pending:** the measured `mag 13.000 -> 17.000` in ONE frame (16x area) on entering free driving is now walked: a transition-eligible zoom commit farther than `ZOOM_BLIT_LIMIT` becomes a walk target, stepped ≤ the blit window once per LANDED render, ending exactly on the requested value (11 new `AutoMapRendererTest` cases; revert-checked: 6 fail pre-change). The walk has its own loop (`startZoomWalkLoop`) because the extrapolation loop is movement-gated — a parked entry would otherwise stall. It is deliberately NOT a controller seed (fix-paced ≈8 s, no visual gain) and stays render-paced. Remaining: on-device judgement of the walk (change tasks 4.1-4.5: magnitude sequence, `lock OK` render count vs the ~4 s bound, parked entry, navigation start, re-enable). Note for whoever takes it: P3's transition cannot smooth it as-is, because for a zoom-IN the frame carries the *new* magnification and reaching the old displayed one would need the buffer scaled below the overrun limit — the phone solves this by queueing the render while the animation plays (`smooth-zoom`: "the debounced native render SHALL be queued while the animation plays"), i.e. render at the old magnification first and let the displayed scale animate upward.
2. **⏳ P4 (smooth heading-up between fixes) — mechanism recorded, deliberately deferred** (design D4 of the follow-up change): rotate the canvas about the follow anchor by the accumulated heading residual. Bound: the exposed corner sliver is ~`r·θ` with `r` up to ~1000 px on a 1080x600 surface, i.e. ~1.5-2 deg of accumulated rotation before the overrun margin (108/60 px) is exceeded; cost: a filtered 1296x720 rotate per tick on a loop already measured at ~11 Hz (§19); risk: resampling softness and composing the overlay rotation with the marker's own bearing rotation. Revisit only if rotation stepping is still visible after P3.
3. **ℹ Device-verification method (do not re-derive):**
   - full renders = `adb logcat -d -t 2000 | grep -c 'lock OK'` (`blitToSurface` logs nothing, so `lock OK` counts ONLY full native renders); fixes = `grep -c 'FreeDrivingScreen: GPS fix'`. Baseline before the fixes: 101 renders / 105 fixes in 104 s.
   - frame placement = log `dy` + the frame centre + the display per drawn frame, then per consecutive pair compute `(Δdy + Δframe_px) − Δdisp_px` (the display's advance IS the expected content scroll); rms should stay sub-pixel, and any commit step shows as a spike. This is the check that found defect 3.
   - the AVD is `Automotive_Distant_Display_with_Google_Play` (AAOS, x86_64, API 33, 1080x600 main + virtual distant displays); the app id is `com.framstag.naviveylin`; the debug APK is `testOnly`, so install with `adb install -r -t`; build it with `-Pandroid.injected.build.abi=x86_64` (an arm64-only APK will not install); relaunch with `adb shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` and then tap Free driving (the car UI is not reachable from `uiautomator`).
   - the emulator console has NO `geo gpx`; `geo fix` / `geo nmea` injections are IGNORED by whatever feeds this AVD (the feed reports `bearing+360` at times), so a straight-line measurement must be produced by controlling the feed itself, not by injecting.

---

## 19. Findings from `overlay-projects-against-displayed-frame` (2026-09-17)

Findings detected while fixing the AA follow overlay projection; all out of that change's scope
(it deliberately keeps the follow re-anchor cadence and the overlay frame bookkeeping only).

- **⏳ `reengageFollow` relies on a preceding `setViewport` to have requested the render** ✗: it writes `viewportLat/Lon` + `emitViewportState()` but never calls `requestRender()`; it works only because every current call site calls it immediately after `setViewport` (which does request). A caller that re-engages follow on its own would silently keep the stale frame on the surface. Fix option: request the render in `reengageFollow` when the target actually moved.
- **ℹ Extrapolation loop measured ~11 Hz, not the nominal 30 Hz** (`EXTRAPOLATION_FRAME_MS = 33`): in the 104 s AA window, 38 diagnostic lines at one line per 30 ticks is ~1140 ticks / 104 s ≈ 11 Hz. Each tick locks the shared surface and draws the full 1296×720 overrun bitmap plus the overlays, so the period is dominated by the draw — measure before tuning the constant (a smaller period would not raise the rate).
- **ℹ Diagnostics use the default locale:** `"%.6f".format(...)` in the AA follow log and the `Diag/MAP` render lines prints decimal commas on a German device (`51,513637`), so those lines are not machine-parseable. Use `Locale.ROOT` for diagnostic formatting.

---

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.

---

## 16. On-device verification pending: fix-zero-distance-to-turn (native step distance)

- **⏳ Pending device run (change `fix-zero-distance-to-turn`, tasks 5.4/6.3)** ✗: the native instruction-distance fix (submodule `3c761d618` on `naviveylin-local`, gitlink bumped at main `195f278`) is implemented and unit-tested (Kotlin state handling + step-index lookup + mapper arrival step — 14 new tests green, full `./gradlew test` green, both flavors all ABIs build). The **native engine path is not verifiable on the host JVM** (the JNI stub .so is symbol-free, so `PositionAgent`/`RouteInstructionAgent`/`GenerateNextRouteInstruction` run only on-device). On next device/emulator session: boot the Automotive AVD, drive a GPX-replay or mock-GPS route with one deviation → reroute, and check via `adb logcat -s NaviVeylin` (plus a temporary `osmscout::log.Debug()` of `distanceTo`/`nodeDist`/`abscissa`): (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset-to-begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Also re-run AA/phone instruction-panel screens at the destination tail — this is where the 0 m freeze appeared.

- **2026-09-15 emulator attempt: blocked by harness, still unverified** ⏳: on a Pixel_8 AVD (GMS disabled → `LocationManager` fallback; regional NRW map; Decathlon Aplerbeck route, 7,4 km) the fix stream worked (`vel=6.69 m/s`, follow camera tracked every hop), but `geo fix` always emits `bear=0.0` — a bearing cannot be injected — so `PositionAgent` never establishes travel direction: the instruction card froze on the first turn across ~2 km and no reroute fired on a 1 km deviation, i.e. the four criteria could not be exercised. Unconfirmed finding for the real-device pass: the next-turn overlay showed **"0 m" while the route panel showed "40 m" for the *same* turn** ("Links abbiegen → Ruhrallee") — check for a distance-source divergence (overlay vs panel step distance). Needs a real drive, or a mock-GPS app using `setTestProvider` with real timestamps/velocity/bearing.

---

## 1. Route Calculation & Visualization

| Feature | Status | Notes |
|---------|--------|-------|
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |

## 2. Turn-by-Turn Navigation

| Feature | Status | Notes |
|---------|--------|-------|
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI |

## 3. GPX Track Import & Playback

> libosmscout submodule (master) already has GPX import/render support (archived change `javascout-gpx-track-import-render`) and JavaScout has `TrackPlayer` — but nothing is wired into the NaviVeylin app yet.

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI — no app UI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 4. Object Description & Long-Press

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 5. UI / Shell

| Feature | Status | Notes |
|---------|--------|-------|
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| Double-tap to zoom | ✗ | Candidate feature (not in JavaScout either). No double-tap gesture exists (`map-pan-zoom` covers pan/pinch only). If added later, wire it into the smooth-zoom animation path and the continuous fractional magnification (see `continuous-pinch-zoom`). |

## 6. Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Track rendering on map | ✗ | |

## 8. GPS / Position

| Feature | Status | Notes |
|---------|--------|-------|
| GPS simulation / dead reckoning when no fix (PositionSimulator) | ✗ | Guess vehicle movement from last position + speed + heading (+ route if navigating) when GPS is stale. Separate service from `LocationService` — never talk to raw GPS directly when there is no fix. See detail below. |

### PositionSimulator — aggregated design notes (from GPS jump investigation)

**Problem:** free driving mode shows random GPS/map jumps on real devices. Root cause: `LocationService` ran Fused AND raw `LocationManager` (GPS/NETWORK/PASSIVE) in parallel on Play Services devices; raw fixes bypassed OS smoothing. Fixed by change `gps-strict-fallback` (Fused only when available). This entry covers the *next* gap: when there is no GPS fix at all.

**Goal:** when GPS is lost, estimate vehicle movement instead of freezing the marker at the last fix (current behavior in free mode).

**State machine:**

```
GPS fresh (acc<50m, age<5s)  →  REAL
GPS stale > N s              →  ESTIMATED (extrapolate)
estimate age > M s / drift > D m → LOST (give up, show signal-lost)
GPS back                     →  REAL
```

**Estimation math:** `position(t) = lastFix + ∫ speed(τ) · heading(τ) dτ`

- heading: free mode → course-over-ground from position history (already implemented in `MapCanvasViewModel` for bearing, low-pass alpha 0.3/0.7); nav mode → route bearing
- speed: filtered GPS speed (150 km/h cap, `filterSpeed` exists in `NavigationViewModel`/`AANavigationController`); AAOS: CAN bus speed via Vehicle HAL later (`AutomotiveDevice` is feature-check only today)
- route: nav mode → snap to route geometry; free mode → heading projection (drifts, must be bounded)

**What already exists (reuse):**
- Native `PositionAgent` (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/PositionAgent.cpp:267-355`) dead-reckons along the route at vehicle speed (capped by max speed) — but only in tunnels and only during navigation. Outside tunnels → `NoGpsSignal`, estimate holds.
- `GpsFixQuality` enum (NONE/POOR/GOOD) + `GPS_FIX_FRESHNESS_MS = 5000`, `GPS_FIX_MAX_ACCURACY_M = 50f` in `MapCanvasViewModel` — freshness/accuracy thresholds exist but nothing acts on them.
- Course-over-ground history + smoothed bearing in `MapCanvasViewModel` (`addCoursePoint`/`computeCourseBearing`/`smoothCourseBearing`).

**Open design questions:**
1. Scope: free mode only, or also open-road GPS loss during navigation (native agent only covers tunnels)?
2. Give-up bounds: after N s / M m of estimation → LOST (real apps ~10-30 s).
3. Marker UX: ESTIMATED position visually distinct from REAL (color/opacity)?
4. Where: new Kotlin `@Singleton` service feeding a derived position flow with state (REAL/ESTIMATED/LOST); consumers = marker, center, nav engine, AA.

## 9. Viewport Save — Residual Bug

- **Residual: pre-init viewport key mismatch (`mapPath` vs `"default"`) ℹ**: `MapCanvasViewModel.initMap` loads with `viewportStorage.load(currentMapKey ?: mapPath)` (`MapCanvasViewModel.kt:1408`), while the navigation-end save (`:2167`) and `saveViewport()` (`:2811`) persist with `currentMapKey ?: "default"`. Harmless today because `initMap` sets `currentMapKey` (`:1314`) before the load, so the fallbacks never meet — but any future save/load before `initMap` (or after a failed init) would write and read different files. Fix candidate: share one key helper (`currentMapKey ?: mapPath`) across all three call sites. Found 2026-09-13 while triaging `MapCanvasViewModelNavEndRestoreTest` (change `smooth-decimal-auto-zoom`, task 6.2) — test-only fix there, production untouched.

## 14. Kover deprecation on the Gradle 10 path

- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 22. Gradle 10 deprecation sweep (build-level, pre-upgrade audit)

- **Every build prints “Deprecated Gradle features were used in this build, making it incompatible with Gradle 10” (observed 2026-09-15 on the §17-verified `./gradlew test`)** ⏳: Gradle 9.6.1 tolerates the deprecations, Gradle 10 hard-fails; §14 already tracks Kover's Project-object notation (not fixable from our scripts). Sweep the remaining deprecations once before any Gradle 10 upgrade: `./gradlew test :app:assembleMobileDebug --warning-mode all`, triage the emitted list, and separate plugin-owned warnings (AGP/Kover — expect one each, document and ignore) from our own scripts' (`buildSrc/*.gradle.kts`, root/app/auto/core `*.gradle.kts` — fixable locally). Two adjacent, still-untried items: configuration cache (`--configuration-cache`, Gradle suggests it each build) — verify it against the license-assets Variant-API tasks and the `release` version-state bump (config-time execution) before enabling in CI; and `org.gradle.configuration-cache=true` interplay with the `release` task gating (bump runs at configuration time, which config-cache may re-run per invocation).

## 10. Pending On-Device Verification

| Item | Status | Notes |
|------|--------|-------|
| Vehicle anchor visible-area + per-surface parity (change `anchor-per-surface-visible-area`, tasks 6.3–6.6, 8.1–8.3) | ⏳ | Implementation green (suite 2026-09-15: app mobile/automotive 964 each, auto 373, core 216; incl. `markerRidesTheBlittedContentWithABlitOffset` + `hostPaneClampsTheLeadingEdgeAnchorOnly`). On-device: routing/free-driving anchors stay clear of the overlays (turn card, routing-status card, street-name pill, widget column) at large font scale; browse framing unchanged (identity); car↔phone per-surface anchor values independent; upgraded install keeps its pre-split value until the car gets its own; `center/center` on both surfaces frames identically to the previous build; AA: leading-edge preset clears the host pane (LTR + RTL), vehicle marker + destination pin ride the blitted content without lead-then-snap during extrapolation glides. |
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |
| Bounded zoom walk on the phone (change `fix-phone-zoom-animation-parity`, tasks 6.3-6.6) | ⏳ | Implementation + unit tests green (walk arithmetic `:core` 14/14, ViewModel wiring 7/7, renderer immediate path 3/3, screen rules 7/7; full `ui.map` package suite green). On-device: enter free driving from a far browse viewport - no consecutive frame change above 0.25 levels, walk ends exactly on the speed target, render count per entry recorded; bottom-center anchor keeps the vehicle pixel fixed through zoom in/out with no correction jump at landing; a large zoom-out never exposes bare surface color; pinch/buttons/keys still request their first frame immediately; the persisted viewport holds the final target. Blocked 2026-09-24: no device/emulator attached (`adb devices` empty). |

## 12. Regional maps emit unknown-type warnings loading standard.oss

- **Pre-existing, not caused by basemap-own-stylesheet** ℹ: on-device logcat shows ~9159 "Unknown type" warnings per startup from loading `standard.oss` (incl. `include/basemap.oss`, `include/place.oss`, `include/tourism.oss`, `include/natural.oss`) into the REGIONAL map databases (Iceland/NRW/Dortmund on the test emulator). The regional maps lack types standard.oss references: `basemap_boundary_country`, `boundary_municipality`, `boundary_suburb`, `place_ocean`, `place_sea`, `tourism_apartment`, `natural_rock`, etc. Likely the installed maps were imported with an older map.ost (newer types missing) — re-importing with the current submodule should clear most of them. The basemap itself now loads `basemap-render.oss` with zero warnings (change `basemap-own-stylesheet`). Investigate: check map import date/version vs current map.ost; consider whether standard.oss should guard `include/basemap.oss` behind a flag (it already has `IF boundary` for some rules).

## 15. Kover coverage attribution for Robolectric-tested classes

- **Pre-existing tooling gap found during fix-contact-address-resolution (2026-09-13)** ℹ: in the merged and `:app` Kover XML reports, classes exercised only by Robolectric tests show near-zero instruction coverage while their tests pass and assert behaviour — `ContactsRepository` 5 covered/0 missed, `FavoriteRepository` 16/0, `AddressBookSheetKt` 41 missed/0 covered (its four Compose tests pass). Plain-JUnit-covered classes report plausible numbers. Suspected cause: Robolectric loads app classes in its own sandbox classloader, so JaCoCo/Kover exec data is recorded against a different class identity. Investigate: Kover/Robolectric instrumentation options (offline instrumentation, `kover { }` filters, or `robolectric.properties` sandbox config) before trusting any coverage gate. Until then, use the revert-check (new test fails on pre-change code) as coverage evidence instead.

## 21. README build commands are stale

- **Pre-existing, noticed 2026-09-13 during `license-compliance-baseline` task 8.4** ℹ: `README.md` › Build Commands still lists `./gradlew :app:assembleRelease` and `:osmscout-jni:assembleRelease`, and the project-structure tree lists `osmscout-jni/` as a JNI bridge AAR — neither exists: the module is `:osmscout-client-java`, and the app builds per flavor (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`, `./gradlew release` for both AABs). The license additions from this change were added to the same document, so the two stale commands now sit next to correct ones. Fix: replace the stale commands with the flavor-aware ones and correct the structure tree.

## 20. OpenSpec config rules: add `openspec doctor` to CI

- **Residual hardening after the `config.yaml` rules bug (2026-09-13)** ℹ: three rule sets in `openspec/config.yaml` (proposal/specs/design) were silently dropped — colon+space entries parsed as YAML mappings, the array failed the array-of-strings check; quoting the entries fixed it. No CI guard exists (checked `build.yml`); add `openspec doctor`, or a tasks.md marker-style validation (§17), so a silently dropped rules file fails loudly.

## 23. libosmscout-kotlin port stubs — verify the binding is unused before anyone wires it in

- **Submodule `app/src/main/cpp/libosmscout/libosmscout-kotlin/` carries 7 unfinished port markers (found 2026-09-15)** ℹ: `objecttypes/TypeConfig.kt` skips feature-description handling in `loadFromData` (“TODO Fetch feature”, “TODO: Add description to feature”), `registerType` has “TODO: Calculate wayTypeIdBytes & Co.” plus two “TODO: Fix” lines, and `index/AreaWayIndex.kt:120` has “TODO: Reserve capacity for offsets”. Grep across every `*.gradle*`/`CMakeLists.txt` shows **no module references `libosmscout-kotlin`** — the binding is inert today (the app uses the C++ JNI bridge + `:osmscout-client-java`; a plain-JUnit or Kotlin binding is not on any build path). The submodule is our own fork (`naviveylin-local`), so this is decision material, not urgent: either finish the port to match C++/Java behavior (TypeConfig without feature descriptions would render/query differently), or document the binding as deliberately unbuilt and add a code comment so a future dependency addition fails loudly instead of silently using a stub.

## 24. Sharp-s uppercase form (ẞ U+1E9E) is not folded

- **Known limitation left in place by `fix-sharp-s-transliteration-match` (2026-09-16)** ℹ: the character map row for U+1E9E (capital sharp S) transliterates to itself, so the case-normalized transliterated comparison that change introduced still cannot match a query spelling a name with `ẞ` against an index name spelled `SS`/`ss` (nor the reverse). No name in the NRW or Iceland map databases uses U+1E9E (verified by extracting `location.idx` and counting spellings: `straße` 65 848×, `strasse` 22×, zero `ẞ`), so nothing is unfindable today. Fix candidate: an upstream character-map row that transliterates `ẞ` to `SS` (or `ss`, now equivalent because the comparison is case-normalized) when the table is regenerated. Land upstream when the table is next touched.

## 27. Unconstrained search of a second loaded database returns out-of-area noise

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 6.5** ℹ: with the map view centered on Iceland and the GPS scope resolved to Regierungsbezirk Arnsberg, the query `Am Birkenbaum 6 Dortmund` returned Iceland-database entries (`Leiðhamrar Dofri`, `Lokinhamrar`, 5,8 km) instead of the Dortmund address. Cause is the documented per-database scope rule: a region handle is database-local, so the database that does *not* own the handle is searched unconstrained (`OSMScoutClient.cpp`, string-search scope comment) and its free-text index answers on short partial tokens ("am" inside "…hamrar…"). Not created by this change — the characters in the returned names (`ð`, `ö`, `í`, `æ`, `á`) are not affected by the transliteration fix, and no pre-change baseline run was made. Investigate: when a scope exists for one database, either skip the other databases' free-text hits or rank them below scoped results (and/or apply a distance limit), so an address query cannot be answered from another map region's data.

## 26. Flaky `BasemapSectionComposeTest.availableShowsDownloadButton` — and a second flaky Compose class (`FavoritesSheetReorderComposeTest`)

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 4.1** ⏳: the first full `./gradlew test` run failed once with `BasemapSectionComposeTest > availableShowsDownloadButton FAILED — android.view.ViewRootImpl$CalledFromWrongThreadException at ViewRootImpl.java:11357` (972 tests, 1 failed). Re-running the class alone (`--tests com.naviveylin.ui.mapmanager.BasemapSectionComposeTest`) passed, and the next full `./gradlew test` was green (972/0/0 for both variants). Unrelated to that change (native-only + gitlink bump; no basemap file in the dirty tree), but a wrong-thread violation indicates a real ordering race in the test (Compose/Robolectric) rather than pure noise. Fix candidate: identify the View access happening off the main thread (probably a `LaunchedEffect`/callback in the basemap section composing a download button) and make the assertion wait for idle instead of racing it.
- **Second class, same shape — observed 2026-09-22 during `fix-native-database-open-race` task 4.2** ⏳: `./gradlew :koverXmlReport --rerun-tasks` (the whole `:app:testAutomotiveDebugUnitTest` suite re-executed under load) failed with `FavoritesSheetReorderComposeTest > chips follow the reordered favorites and a new star appends FAILED — androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms` at `FavoritesSheetReorderComposeTest.kt:233` (`awaitCondition`). The same suite was green twice earlier that day (mobile and automotive, 1100 tests each, 0 failures) and the class alone passes in 13 s, so it is a load-sensitive timeout, not a defect: the 5 s bound at `FavoritesSheetReorderComposeTest.kt:379` is too tight when the JVM is busy. Fix candidate: raise/replace the fixed bound (or wait for idle on the specific node) so a heavily loaded runner cannot fail it; unrelated to that change (database registration vs. favorites-sheet reordering).
- **Worse on this machine — observed 2026-09-24 during `fix-phone-zoom-animation-parity` tasks 6.2** ✗: `FavoritesSheetReorderComposeTest > chips follow the reordered favorites and a new star appends` now fails **on its own**, not only under load - three consecutive runs (whole `:app` mobile suite, the class alone twice) all failed at `FavoritesSheetReorderComposeTest.kt:233` with the same `ComposeTimeoutException: Condition still not satisfied after 5000 ms`. Proven unrelated to that change: the class was re-run with the change's files stashed (clean tree, `git stash push --include-untracked`) and failed identically, and `:app`'s `ui.map` package suite (the change's surface) is green. It is still only *flaky*, not broken: the `:app:testAutomotiveDebugUnitTest` suite immediately afterwards ran the same class green (11 tests, 0 failures). The 5 s `awaitCondition` bound waiting for the asynchronous `FavoriteRepository` persist is the suspect; raising it (or waiting for idle on the concrete node) is the fix candidate.

## 28. Whole-level rounding in `computeAreaZoom` still over-fits area favorites and POI search

- **Found 2026-09-18 during `route-overview-fit` (DPI/`cos(lat)` ground-resolution fix)** ℹ: the shared bbox→magnification helper rounds to whole levels (`Math.round`), which can round the exact fit down by up to half a level, so the fitted content ends up to ~13% larger than the 80%-margin target. The route overview is now protected by an explicit projection check (`routeFitsVisibleArea`, design Decision 8), but the other two callers — the area-favorites zoom (`onFavoriteSelected`, the details/area zoom helper) and the POI/radius search fit (`SearchDialog.poiFitMagnification`) — have no such verification, so their fitted bbox can slightly overflow the mini map / map viewport (previously masked by the over-zoom the ground-resolution fix removed). Fix candidate: round *up* for fit callers (never clip, at the cost of ≤1 level more zoom-out) or reuse the projection check; note that the favorites zoom is additionally clamped to 14–20, which hides the effect for small objects.

## 35. On-device re-check of the area-favorites and POI-search fit zooms

- **Created 2026-09-18 by `route-overview-fit`** ℹ: that change corrected `computeAreaZoom`'s ground resolution (display DPI + Mercator `cos(lat)`), which *intentionally* changes the zoom the phone picks when selecting an area favorite and when the POI search fits its results — both now zoom out to the geometrically correct level (previously over-zoomed by `dpi / 96 * (1 / cos(lat))`, ≈2 levels on a 420-dpi phone). Unit tests stay green, but no on-device/visual check of those two flows is part of that change. Follow-up: pick an area favorite and run a POI radius search on a real phone/emulator and confirm the framing is sane (not too far out, markers inside the visible area).

## 36. `public-transport` stylesheet draws no route  — ⏸ ON HOLD (owner decision 2026-09-20)

- **ON HOLD 2026-09-20 — deliberately not the next change.** Re-investigated 2026-09-20 and the blast radius is far larger than this entry first recorded: **5 of the 8 user-selectable styles cannot draw the active route at all**, not one. See the scope block below; the fix is understood and small, but it waits on option A/B/C being chosen.
- **Corrected scope (verified 2026-09-20).** `BundledMapStyles.USER_SELECTABLE` (`core/src/main/java/com/naviveylin/core/BundledMapStyles.kt:38`) is the 8 styles `boundaries, coastlines, cycle, motorways, public-transport, railways, standard, winter-sports`. `stylesheets/include/route.oss` is the ONLY definition site of `[TYPE _route]` (:56-57), `[TYPE _track]` (:62), `[TYPE _route_start]`/`_route_end` (:68-69), `[TYPE _favorite]`/`_search_selected` (:76-77) and of `SYMBOL route_start`/`route_end`/`favorite_marker`/`search_marker` (:26-46). It is included by `standard.oss`, `cycle.oss` and `winter-sports.oss` only — so the other five (`public-transport`, `railways`, `motorways`, `boundaries`, `coastlines`) draw no route line, no start/end pins, no GPX track, no favourite markers and no selected-search marker. `public-transport.oss` is the tell: it declares `GROUP _route` in `ORDER WAYS` (:4) but never wires the rule. No app-side escape hatch: one stylesheet is loaded (`MapCanvasViewModel.applyStyleSheet` → `loadStyleSheet`) and route/POI drawing goes through `MapRenderer.kt:714 renderWithRouteAndPois` with no per-render style override.
- **Three requirements are contradicted as written:** `route-panel-ui` scenario "Route polyline rendered on map" (unconditional: route polyline SHALL be rendered AND `_route_start`/`_route_end` markers SHALL appear), `fav-markers` ("SHALL render every favorite location … via `renderWithRouteAndPois`", and "the existing `_favorite` synthetic node type already defined in the libosmscout stylesheet" — defined only in `include/route.oss`), and `route-appearance` Req "Every map style draws the route with the shared route colors" + scenario "Non-default styles have a route casing" (that change fixed `cycle`/`winter-sports` and left these five behind).
- **Open decision (A/B/C):** **(A)** add `MODULE "include/route"` to all five — minimal, satisfies the three requirements, specialty styles keep their character because `route.oss` adds only route/track/marker rules and no ORDER sections (recommended at the time); **(B)** drop the partial styles from the picker — needs a `map-styles` spec change, users lose those styles; **(C)** make route/marker drawing style-independent — larger, changes the render contract.
- **Fix shape if A:** five `MODULE` lines in the submodule `stylesheets/*.oss` (upstreamable) + submodule commit on `naviveylin-local` + gitlink bump in the main repo + an app-side guard test asserting every `USER_SELECTABLE` stylesheet transitively includes `include/route.oss` (precedent: `StylesheetHexColorCaseTest`, `AssetCopierTest`). Note `ORDER` sections are optional — `coastlines.oss` draws with none, and `_favorite`/`_track`/`_route_start` appear in no `ORDER` group anywhere yet render in `standard`/`cycle`/`winter-sports` — so the `MODULE` line is the load-bearing part.
- **Original note (2026-09-19, `map-marker-route-contrast`), now superseded by the scope block above:** `stylesheets/public-transport.oss` declares `GROUP _route` in its `ORDER WAYS` block but has no `[TYPE _route]` rule, so an active route is not drawn at all while that style is selected (it is user-selectable via `BundledMapStyles.USER_SELECTABLE`). The route rule lives in `stylesheets/include/route.oss`; that change made `cycle.oss` include it like `standard.oss`/`winter-sports.oss`, and left `public-transport` untouched because the missing rule is a pre-existing gap, not one of the reported appearance defects. Fix candidate: add `MODULE "include/route"` to its MODULE block (same pattern) and verify on-device that a route then appears in that style.

## 37. Pre-existing Kotlin deprecation warning in the marker overlay

- **Observed 2026-09-19 during `map-marker-route-contrast` task 4.1** ℹ: every `:app` build prints `w: .../ui/map/LocationMarkerOverlay.kt:167:14 'fun quadraticBezierTo(x1, y1, x2, y2)' is deprecated. Use quadraticTo() for consistency with cubicTo()`. Pre-existing — that change only replaced the gradient color selection in the same function. Fix: rename the call (behavior-identical) in a build-hygiene change, or wait until the Compose version makes it an error.

## 44. Pre-existing Kotlin build warnings beyond the marker overlay

- **Found 2026-09-20 during `fix-favorite-store-write-race` (full-suite build)** ℹ: `./gradlew test --continue --rerun-tasks` prints 66 Kotlin warnings, none of them from that change's files. Beyond `LocationMarkerOverlay.kt:167` (already tracked as §37), three classes are untracked: (a) `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:35` — "This annotation is currently applied to the value parameter only, but in the future it will also be applied to field" (annotation-target migration, a hard change in a future Kotlin); (b) `app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt:35` — Robolectric's `ShadowSensorManager.addSensor` is deprecated in Java; (c) the `ExperimentalCoroutinesApi` opt-in warnings spread over ~13 test files (`MapCanvasViewModelAutoZoomCommitTest`, `MapCanvasViewModelRoadInfoTest`, `MapCanvasViewModelSingleFollowCenterTest`, `RoutePanelViewModelSearchRankingTest`, ...). The archiving guidance requires a warning-free build, so this is build-hygiene debt rather than a defect. Fix candidate: one build-hygiene change that adds the missing `@OptIn` annotations, replaces the deprecated shadow call, and sets the annotation target explicitly.

## 45. Add-favourite dialog cannot save on a comma-decimal locale — FIXED by `fix-comma-decimal-coordinate-entry` (2026-09-25)

- **Fixed ✅** by change `fix-comma-decimal-coordinate-entry` (spec: `fav-management-ui` — Coordinate entry accepts either decimal separator; `i18n-l10n` — Coordinate string is locale-stable): new `:core` seam `core/CoordinateFormat.kt` (`formatCoordinatePair`, `formatCoordinate`, `parseLatitude`, `parseLongitude`) formats coordinate strings with a fixed locale and parses both `.` and `,`. The dialog prefills through the formatter and parses through the parser, so the prefilled map center saves unchanged; `FavoritesSheet`'s row, `LocationDetailsSheet`, `FavoritePickerDialog`, `MapCanvasViewModel` (long-press label + candidate label fallback) and the car `DetailsScreen` row all go through the same helper. Tests: `CoordinateFormatTest` (11), `AddFavoriteDialogComposeTest` (6, incl. the German-locale prefill round-trip and the rejected `51,51,391` / out-of-range cases), `FavoriteItemComposeTest`, `FavoritePickerItemComposeTest`, `DetailsResolverTest.titleExcludesTheCoordinateLabelTheAppActuallyProduces`, `DetailsScreenTest` (locale-stable row + destination-text agreement). Evidence: `./gradlew test` green — app mobile 1174 / automotive 1174 / auto 683 / core 339, 0 failures; both flavor debug APKs build with no new warnings. This entry is removed when the change is archived.
- **Original finding 2026-09-20 during the on-device smoke run of `fix-favorite-store-write-race` (Pixel_8 AVD, German device locale)** ✗: `FavoritesSheet`'s add-favourite dialog prefills the coordinate fields with `"%.5f".format(initialLat)` / `format(initialLon)` (`app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt:938-941`) — no explicit locale, so a German device shows `51,51391` / `7,47434` — while the confirm path parses with `latText.toDoubleOrNull()` / `lonText.toDoubleOrNull()` (`:977-978`) and gates the button on `enabled = name.isNotEmpty() && latText.toDoubleOrNull() != null && lonText.toDoubleOrNull() != null` (`:983`). `toDoubleOrNull()` accepts a dot only, so the prefilled comma form can never be parsed: **the Save button is inert and the favourite cannot be added at all** — no error, no feedback, the dialog simply stays open. Proven on-device: with the prefilled comma coordinates Save does nothing and `files/favorites.json` keeps its previous mtime; after retyping the same values with dots (`51.5` / `7.4`) the save succeeds immediately and the entry appears in the file and survives a restart. This is the §31 / §40.26 family (default-locale number formatting) with a functional consequence rather than a display nuisance, on the app's primary non-English locale (the display in the group view then shows the ambiguous `51,50000, 7,40000`). Fix candidate: one shared coordinate formatter/parser with an explicit locale (format with `Locale.US`/`Locale.ROOT`, accept both `.` and `,` when parsing), used by the dialog and the §31 display sites, plus a unit test for the comma-decimal case.
- **On-device re-check done 2026-09-25** ✅ on `emulator-5554` (API 37, locale `de-DE`): the dialog prefilled `51.51391` / `7.47434`, Save was enabled after typing only the name, and the favourite landed in `files/favorites.json` (`Testort`, 51.51391 / 7.47434) with the group count going 1 → 2. The group row showed `51.51391, 7.47434` afterwards. Full evidence in `openspec/changes/fix-comma-decimal-coordinate-entry/tasks.md` task 5.2. `FavoritesSheet`'s add-favourite dialog prefills the coordinate fields with `"%.5f".format(initialLat)` / `format(initialLon)` (`app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt:938-941`) — no explicit locale, so a German device shows `51,51391` / `7,47434` — while the confirm path parses with `latText.toDoubleOrNull()` / `lonText.toDoubleOrNull()` (`:977-978`) and gates the button on `enabled = name.isNotEmpty() && latText.toDoubleOrNull() != null && lonText.toDoubleOrNull() != null` (`:983`). `toDoubleOrNull()` accepts a dot only, so the prefilled comma form can never be parsed: **the Save button is inert and the favourite cannot be added at all** — no error, no feedback, the dialog simply stays open. Proven on-device: with the prefilled comma coordinates Save does nothing and `files/favorites.json` keeps its previous mtime; after retyping the same values with dots (`51.5` / `7.4`) the save succeeds immediately and the entry appears in the file and survives a restart. This is the §31 / §40.26 family (default-locale number formatting) with a functional consequence rather than a display nuisance, on the app's primary non-English locale (the display in the group view then shows the ambiguous `51,50000, 7,40000`). Fix candidate: one shared coordinate formatter/parser with an explicit locale (format with `Locale.US`/`Locale.ROOT`, accept both `.` and `,` when parsing), used by the dialog and the §31 display sites, plus a unit test for the comma-decimal case.

## 38. A rejected stylesheet crashes the renderer instead of degrading

- **Observed 2026-09-19 during `map-marker-route-contrast`** ⏳: when a stylesheet fails to load (that change's first device run had an uppercase hex literal in `include/route.oss`, which asserts in `Color::GetHexValue`), the sequence is: `Style error:243,8 Error: Cannot load module '.../include/route.oss'` → `Failed to load stylesheet .../standard.oss` → `Fatal signal 11 (SIGSEGV) in osmscout::StyleConfig::HasNodeTextStyles` called from `MapPainter::PrepareNode`. The client keeps rendering with a rejected `StyleConfig` instead of falling back to a previous style or refusing to render, so a stylesheet defect becomes an app crash. Not introduced by that change (the uppercase literal was; the crash path is pre-existing) — `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` now guards the specific trigger, but not the fragility itself. Fix candidates: on a failed stylesheet load, keep the previously loaded `StyleConfig` and surface an error to the user (`DiagnosticsLog` + a snackbar), and/or validate a stylesheet on the client side before swapping it in.
- **Split into two OpenSpec changes (2026-09-19)** ⏳: the guard belongs to the client library, the reporting to the app.
  - **libosmscout** (`app/src/main/cpp/libosmscout/openspec/changes/client-style-load-resilience`, capability
    `client-java-style-switching`): adopt a candidate style configuration only after a clean parse, keep the
    previously active style, install the (currently unused) safe configuration when nothing has loaded yet,
    never hand a rejected/absent configuration to the painter, report the per-database outcome and the active
    style on every load path. Verified facts: `StyleConfig::Load` returns `false` on parser errors
    (`StyleConfig.cpp:1785`), `DBInstance::LoadStyle` then leaves the database without a configuration
    (`DBInstance.cpp:56-70`), `DBThread::LoadStyleInternal` ignores that result and only logs
    (`DBThread.cpp:440-473`), and the safe configuration created at `DBThread.cpp:61`/`:447` is never installed.
    **Done 2026-09-19** (commits `9f99f7edf`/`ac8168f25` pushed to `origin/naviveylin-local`, 20/20,
    revert-checked) — still unarchived, its deltas unsynced.
  - **NaviVeylin** (`openspec/changes/fix-stylesheet-load-crash`): report the failure to the user (one wording,
    non-blocking, phone + car), keep the previous style visible, verify the crash-free degradation on device,
    and bump the submodule gitlink — sequenced after the client change lands. **In progress 2026-09-19:**
    gitlink bumped (`f8ffe98`), phone reporting (seam `MapStyleLoadReporter` + snackbar, switch/flag/persisted
    paths with `standard` startup fallback) and car reporting (notification `map_style` via
    `CarStyleLoadNotifier`, owner decision — see design D2's correction) implemented and unit-tested; the
    build/test/doc gates and the on-device pass remain.

## 39. Real-life and Android Auto verification for the marker/route colors

- **Created 2026-09-19 by `map-marker-route-contrast`** ⏳: the visual checks (daylight route over a primary road and a white residential road, GPX track and search marker alongside, dark-presentation route unchanged, lighter dark marker without a white ring, 38 dp marker size, map-style switch to `cycle`/`winter-sports`) were performed on the phone emulator and reported working, but the user noted the concrete colors still need real-life validation, and the Android Auto half (host day/night, route/marker parity) was accepted by assumption instead of being measured on a head unit or the `Automotive_Distant_Display_with_Google_Play` AVD. Follow-up: check the violet route and the lighter dark marker on a real display outdoors and in a dark car interior, and run the AA day/night comparison once on the automotive AVD or a head unit. If the violet reads too close to the magenta search marker or the blue GPX track in practice, the hue can be adjusted in `stylesheets/include/route.oss` alone (the spec pins the contrast contract, not the hex).

---

## 40. Guardrails extracted from `ki_processing_failures.log` (2026-09-19)

Every entry of that log was processed on 2026-09-19 and removed; each action below is what prevents
re-making the mistake. The parenthetical names the artifact that should carry it. Items marked **[open]**
are not implemented yet; items referencing an existing TODO section are already tracked there and are
listed only so the guardrail is not lost.

Re-run the extraction with the `.pi/skills/process-failure-log` skill (gitignored, like the other
`.pi` skills — copy to `~/.pi/agent/skills/` for cross-project use).

### A. Harness / shell / Gradle invocations

1. Never plan on `python3`, `perl` or `tesseract` — all blocked by the lean-ctx shell allowlist (permanent).
   Use `jq`, `sed -i -E`, `openspec … --json` + grep; there is no OCR route without a config change.
   **[open]** (guidelines/Build.md — shell constraints)
2. Never `pkill -f "gradlew …"`: `-f` matches the calling tool's own command line, kills the shell, and the
   rest of the chained command (e.g. a `git commit`) silently never runs. Kill by PID
   (`ps -o pid,args` → `kill -9 <pid>`). **[open]** (guidelines/Build.md)
3. Long builds/tests (`./gradlew test`, `release`, full CMake) exceed the shell output cap — run detached
   (`nohup ./gradlew … > /tmp/x.log 2>&1 &`) and poll with short `tail`/`grep`. Live in `run-tests`;
   **[open]** for `build-app` and `release-build`.
4. Never start a second Gradle build against the same output directories while an aborted one is still
   running (the daemon keeps rewriting SBOM/assets; the next build then reports bogus parse errors).
   **[open]** (guidelines/Build.md)
5. A `BUILD SUCCESSFUL in 5s` / `… up-to-date` line is not test evidence — see §17. Quote executed-task
   count + elapsed time and verify counts from the result XMLs. (tracked: §17)
6. Verify artifacts by comparing them to their inputs (content/mtime), never by the existence of an output
   path: build-cache-restored APKs in `outputs/apk` and stale `build/outputs/sbom/*` look like product bugs.
   **[open]** (guidelines/Build.md)
7. Do not use `/tmp` as a workspace for large map imports (RAM tmpfs, ~7.7 GB quota); use a disk path.
   **[open]** (guidelines/Build.md)
8. Test-suite heap and class batching: both modules declare their fork budgets (`guidelines/Build.md` §6), so
   batching is a DIAGNOSTIC fallback only (e.g. to isolate one class), never the procedure. When it is used:
   `--tests "com.x.[A-E]*"` is NOT a glob in Gradle (one pattern per prefix or explicit class names), per-batch
   XMLs must be copied out because Gradle cleans `test-results` on each invocation, and coverage runs in a
   separate invocation from the test gate. A build-cache hit (`FROM-CACHE`, no test executor) is not test
   evidence — use `--rerun`.

### B. Editing discipline (edit/ctx_patch)

9. Multi-edit calls are atomic: one ambiguous or missing `oldText` rejects the WHOLE call, silently leaving
   the other edits unapplied. Re-read every changed region before compiling, and make each anchor unique
   with its distinguishing surrounding line. **[open]** (guidelines/Design.md or a skill note)
10. Never retype prose/code from memory into `oldText` — copy it from the read output. Never emit
    overlapping/nested `oldText` regions in one call; for pure insertions keep the entire matched block in
    `newText` and append to it. **[open]**
11. After an edit reports a missing `oldText`, re-read the file before re-issuing: a corrective pass can
    silently duplicate a helper (duplicate blocks then make exact-match edits ambiguous). **[open]**
12. One mutation per revert check — a combined mutation masks the other behaviour and the assertion becomes
    vacuously true. **[open]** (guidelines/Build.md — test evidence)

### C. Kotlin / Compose / car-app build traps

13. `while (isActive)` in a coroutine needs the explicit `kotlinx.coroutines.isActive` import.
    **[open]** (guidelines/Design.md)
14. `KProperty0.isInitialized` works only for `lateinit`; for `by lazy` state use a nullable `var` or a flag.
    **[open]**
15. Test coroutine extensions need a receiver: declare helpers as `private fun TestScope.foo()`;
    `advanceTimeBy(Long)` is an extension, `advanceUntilIdle` a `TestScope`/`TestCoroutineScope` extension.
    **[open]**
16. Lifetime background tickers (`while (true) { delay(1s) }`) must run on a real dispatcher
    (`Dispatchers.Default`) with test hooks — on the test scheduler they hang every `runTest`.
    **[open]**
17. Compose plurals containing `%d` need the count as an explicit format argument
    (`pluralStringResource(id, count, count)`). **[open]**
18. Never call `composeRule.setContent {}` twice in one test; collect all values in the single composition.
    **[open]**
19. Never nest two `verticalScroll` containers; give an embedded scrollable an opt-out parameter.
    **[open]**
20. Robolectric's Compose root is clamped to 320×470 dp — assert against the screen, not hardcoded sizes, and
    read the failing bounds from the assertion message. **[open]**
21. Robolectric's shadow canvas discards `drawBitmap`/`drawPath` (0 opaque pixels) and
    `@GraphicsMode(NATIVE)` does not fix it here — assert the bitmap contract (size/config/caching/no-throw)
    and leave visuals to on-device; do not add a second Robolectric sandbox config. **[open]**
22. Compose dropdown popups do not appear in `uiautomator` dumps — assert the field's text instead.
    **[open]** (guidelines/UI.md)
23. Java types from the JNI bridge: positional constructor args only (no named arguments), read the ctor
    before writing helpers (`RouteInstruction` has 5- and 10-arg forms), and update EVERY call site in the
    same change when such a ctor changes — stale test bytecode surfaces as `NoSuchMethodError`, not a
    compile error. **[open]**
24. Do not mock Kotlin `object` `@JvmStatic` methods (mockk cannot intercept them); stub the real
    `applicationContext` and the Java delegate the object calls (`dagger.hilt.EntryPoints`). **[open]**
25. `Notification.actions` is nullable (`actions?.isEmpty() != false`); use `getIcon()` (the Kotlin field is
    deprecated and breaks warning-free builds). **[open]**
26. Every user-facing number/coordinate formatter takes an explicit locale
    (`String.format(Locale.US, …)`) — default-locale formatting broke tests and reads ambiguously in German
    (also §31). `"%.5f".format()` rounds, it does not truncate. **[open]**
27. `NavigationTemplateMapper.distanceForDisplay` reports display units — assert `displayDistance` plus
    `displayUnit`, not metres. **[open]**
28. Verify constructor-argument edits against the file's imports in the same pass (a rename dropped a
    still-used import and invented a non-existent class). **[open]**
29. For car-app screens, check the real API surface first (getter names, resolved lifecycle version, no
    `Lifecycle.getObservers()`); drive lifecycle through `dispatchLifecycleEvent`, ON_CREATE + ON_START
    before ON_DESTROY. **[open]**

### D. Test / verification discipline

31. Never pace a unit test off a timer or a debounce (`Thread.sleep`): control the loop
    (`asyncLoopsEnabled = false/true`) and land a frame deterministically (`renderFrame()`).
    **[open]** (guidelines/Build.md)
32. After a boundary-signature semantic change, grep the tests for the observable field first
    (`lastRenderMag` now holds the raw scale → compare `2^level`). **[open]**
33. Compute `log2` expectations with a calculator, not mentally. **[open]**
34. Native index test fixtures must be FULL (non-eco) imports — `--eco true` skips the POI indexes.
    **[open]**
35. `DBThread` loads databases sequentially: wait for two consecutive identical results before asserting.
    **[open]**
36. Verify `md5` after any `git stash` cycle before rebuilding — a stash+pop silently reverted a submodule
    patch and the "patched" host library was unpatched. **[open]**
37. Coverage: Kover/JaCoCo per-class numbers are meaningless for Robolectric-only classes (sandbox
    classloader, §15) — use revert-checks as evidence; never apply Kover to a Kotlin-plugin-free module
    (use the Gradle `jacoco` plugin there). (tracked: §14, §15)
38. Test-harness flakes of unknown origin get an entry with the rerun evidence instead of a silent retry —
    see §26 (still open). (tracked: §26)

### E. On-device / emulator preconditions

39. Never install an ABI-filtered APK (`-Pandroid.injected.build.abi=…`): packaging strips the other ABIs,
    the install succeeds and the app dies at `System.loadLibrary`. Verify with `unzip -l <apk> | grep <abi>`
    and check the APK mtime against the newest source edit before an on-device run. **[open]**
    (guidelines/Build.md — on-device verification)
40. Emulator GPS cannot inject a bearing: `geo fix` has no bearing argument (always `bear=0.0`), and NMEA RMC
    is dropped by FusedLocationProviderClient ("too close / too fast"). Do not plan bearing/heading on-device
    tests on a GMS emulator — cover them with unit tests. **[open]**
41. Headless emulators need `-dns-server 8.8.8.8` (broken DNS → "Unable to resolve host" while raw IP works) —
    compare `adb shell ping` against the app's network code before debugging the app. **[open]**
42. Check which maps are already installed BEFORE attempting a catalog download — `adb install -r` preserves
    data. **[open]**
43. After toggling `location_mode`, re-send `geo fix` (Fused may need provider re-registration). **[open]**
44. `adb shell input text` goes through the active IME (GBoard rewrote `Erbstollenstrasse` →
    `Er Stollenstraße`): disable the IME for the replay and assert the field's actual value from the
    `uiautomator dump` before interpreting results. **[open]**
45. The AAOS/car AVD is not usable for on-device steps in a headless agent session: car system-UI ANRs swallow
    `input tap`, `uiautomator dump` returns an empty hierarchy, `geo fix` is answered OK but no fix reaches the
    app, and the host `RendererService` disconnects ~40 s after launch. Plan car verification for an
    interactive window or a real head unit, and state the blocker instead of burning a session.
    **[open]** (guidelines/Build.md + the OpenSpec apply guidance for car changes)
46. Before an apply/verify pass, check for a concurrent writer in the tree (`find <module> -newermt "-10 minutes"`,
    active Gradle clients) — one writer per tree; if a peer is mid-edit, quote the evidence already collected and
    stop. **[open]** (AGENTS.md — parallel sessions)

### F. Native / libosmscout

47. Any change inside an `#ifdef OSMSCOUT_HAVE_LIB_MARISA` block must be verified in BOTH configurations — the
    Android build always defines it (vcpkg provides marisa), so CI's non-Marisa path is invisible here.
    Reproduce locally: insert `#undef OSMSCOUT_HAVE_LIB_MARISA` after the include block of
    `OSMScoutClient.cpp`, build `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then remove
    the `#undef`. **[open]** (guidelines/Build.md — native verification)
48. Native test binaries are not directly executable (allowlist): run them through `ctest -R <Test>
    --output-on-failure` from the build directory. `ctest` does not build — `ninja -C <build> <Target>` first, and
    filter `ctest -N` output for `Test #` lines. For the meson `hostbuild/` directory this does NOT work: it has no
    `CTestTestfile.cmake`, so `ctest` answers "No tests were found!!!" even for a registered, green test — use
    `meson test -C hostbuild "<test name>" --print-errorlogs` there, and note that meson prefixes its target names
    with the subdirectory (`Tests/FavoriteStoreTest`, not `FavoriteStoreTest`), with one throwaway `ninja` invocation
    needed after a `meson.build` edit so the regeneration lands before the target lookup
    (found 2026-09-20, `fix-favorite-store-write-race`).
    **[open]**
49. `System.loadLibrary` needs the plain-name `.so` — versioned `.so.1` symlinks are not found. **[open]**
50. Plain `openDatabase(containerRoot)` wipes the DBThread (the root has no `types.dat`); load a container of maps
    through the builder's map-lookup scan. **[open]**
51. The `:osmscout-client-java` Gradle JAR excludes `OSMScoutClient.java`/`Builder` — never feed it to JavaScout
    Maven (a stale `~/.m2` JAR breaks the signature); build the JAR from the submodule `java/` sources.
    **[open]**
52. JavaScout Maven tests need `JAVA_HOME=java-21` on this machine (JUnit 5.10.2 on Java 26 discovers but
    executes 0 tests). **[open]**
53. Stale meson host builds: `sed` the ninja link line to a stub path before rebuilding (`/usr/lib` is not
    writable, no sudo). **[open]**
54. Before pushing a submodule branch, run `git ls-remote origin <branch>` (not the local remote-tracking ref)
    immediately before the push, and let ONE session own the submodule update — two sessions created the same
    JNI commits and force-rewrote `naviveylin-local` (reconciled by a merge, no force-push, nothing lost).
    **[open]** (AGENTS.md — submodule workflow)
55. Stylesheet hex literals are lowercase-only (`Color::GetHexValue` asserts; a rejected style then crashes the
    renderer, §38), and a stylesheet defect does NOT fail the build: run the app once after the first stylesheet
    change and grep `adb logcat -s NaviVeylin | grep -i "style error"`. (tracked: §38)

### G. Design / scope discipline

56. Verify the premise before building a change on it: the tile-path condition (pinned by
    `TileCacheRenderTest`), the real pixels-per-degree the renderer produces (DPI × `cos(lat)`, cross-checked
    through `ProjectionUtils` rather than re-derived with the formula under test), and the "single source of
    truth" location of a dedup change (removing the duplicate must not remove the only instance). **[open]**
    (guidelines/Design.md)
57. Log BOTH sides of a seam (pending vs displayed frame, both path counters) in one grep-able line — five
    changes shipped while the AA follow defect was live because no log line compared the two frames.
    **[open]** (guidelines/MapRendering.md)
58. When two layers can both own a transition, decide the pacing (render vs fix) before coding; never call a
    stateful controller twice for a check-then-use pair; scope a renderer-wide rule with an explicit opt-in
    flag. **[open]** (guidelines/Design.md)
59. Write down which coordinate frame each number lives in before comparing (pre-shift projection vs post-shift
    visible band). **[open]**
60. In ViewModel tests, assert pre-state through an entry path that does not mutate the snapshot field
    synchronously before the collector resumes. **[open]**
61. Initialize progress state to the "0 % traveled" invariant (`remainingDistance = totalDistance`), not the
    data-class default; state machines with wall-clock state need a "never set" sentinel and an explicit
    first-event branch. **[open]**

## 48. JNI bridge: `ClientData::knownPaths` is mutated without a lock in `openDatabase` — FIXED by `fix-native-database-open-race` (2026-09-21)

- **Defect** ✗ (original): `OSMScoutClient.cpp:687-693` finds/pushes into `data->knownPaths` (a
  `std::vector<std::filesystem::path>`, `ClientData` at `:435`, **no mutex** — unlike
  `gpsMarkerMutex`/`routingMutex`/`adminRegionMutex` right beside it) and then passes the live
  vector to `OnDatabaseListChanged`, which copies it by value on the calling thread. Two concurrent
  openers in one process (the AA warmup loop `AutoServiceModule.provideAutoClientProvider` →
  `openMapDatabases`, and the phone `MapCanvasViewModel.initMap` / a map scan / a download
  completion) therefore race a reallocation against a read → use-after-free / heap corruption →
  native SIGSEGV.
- **Second-order cost** ℹ: every `openDatabase` call triggers `OnDatabaseListChanged`
  (`DBThread.cpp:173`), which closes and reopens **all** databases under the write lock — so the
  warmup's per-directory loop is O(N²) DB opens and blocks every render's read lock while it runs.
- **Fixed** ✅ by change `fix-native-database-open-race` (spec `native-database-open`): the path list
  moved into the JNI-free `osmscout::DatabasePathRegistry` (`libosmscout-client`, one mutex per
  state — the same shape the favorite-store race got), `openDatabase` publishes a value snapshot
  taken under that lock, and the new batch entry point `openDatabases(String[])` registers a whole
  directory list as **one** database-set change. Both callers (car warmup, phone `initMap`) hand
  their list over in one call. Regression tests: `Tests/src/DatabaseOpenTest.cpp` (9 cases,
  including the concurrent-opener stress that dies SIGSEGV 3/3 with the mutex removed). This entry
  is removed when the change is archived.

## 49. One full car render allocates ~15 MB of transient buffers — app-side FIXED by `fix-render-buffer-reuse` (2026-09-26); native hand-off + measurement still open

- **Observation** ℹ: a full render at the 1.2× overrun size walks four full-size buffers — C++
  `std::vector<uint32_t>` (`OSMScoutClient.cpp` render entry), the Cairo RGB24 surface, the Java
  `jintArray` from `NewIntArray`, and the `Bitmap` in `MapRenderUtil.renderToBitmap` — plus a
  per-pixel C++ conversion loop. At 1296x720 that is ~3.7 MB per buffer, up to ~5 renders/s while
  the extrapolation loop is clamped, i.e. tens of MB/s of churn (Java heap + native graphics).
  The car overlay draw (`AutoMapRenderer.drawGpsMarker`/`drawDestinationMarker`) also allocates
  `Paint`/`Path`/`LinearGradient`/`BlurMaskFilter` per frame at ~30 fps.
- **Mitigation landed in `fix-aaos-host-crash`** ✅: the full-render request interval is now bounded
  by the measured render duration with one render in flight (design D7), which caps the rate rather
  than the per-render cost.
- **App-side half fixed ✅ by `fix-render-buffer-reuse`** (2026-09-26; spec `render-performance` —
  Reusable render target for map frames, A frame handed to the display layer is never overwritten;
  spec `auto-map-renderer` — Marker drawing allocates no per-frame objects): `core/RenderBitmapPool`
  now owns the ARGB_8888 render targets (hand out → release, ≤2 free per size class, ≤2 size classes,
  double release refused), `MapRenderUtil.renderInto` writes into a caller-supplied target, and both
  renderers use it — the phone's tile-composition and full-render targets (`MapRenderer`) and the
  car's displayed overrun frame (`AutoMapRenderer`, which therefore holds two distinct slots while a
  render is in flight, as its blit loop requires). The car's marker draw objects
  (`Path`/`Paint`/`BlurMaskFilter`/`LinearGradient`) are built once per presentation/density/bounds
  instead of per drawn frame. Tests: `RenderBitmapPoolTest` (7), `MapRendererSmokeTest` +3 cases,
  `AutoMapRendererPooledTargetTest` (4), `AutoMapRendererMarkerDrawCacheTest` (4), with three
  revert-checks quoted in that change's tasks. This entry is removed when the change is archived.
- **Still open ✗ — the native half (the bigger two of the four buffers)**: the C++ `argbPixels`
  `std::vector<uint32_t>`, the Cairo RGB24 surface and its per-pixel conversion loop, plus the JNI
  `jintArray`, are still allocated per render (`OSMScoutClient.cpp:1793-1818`). Removing them needs a
  new JNI entry point that renders into a caller-owned buffer (submodule commit on `naviveylin-local`
  + gitlink bump + `:osmscout-client-java` override, per `AGENTS.md`). Deliberately out of scope in
  `fix-render-buffer-reuse` (its Non-Goals).
- **Still open ✗ — the measurement**: no device was attached (`adb devices` empty) and the AAOS AVD is
  unusable for headless sessions (§40.45), so the `dumpsys meminfo` before/after comparison did not
  run. Recipe for a session with a device: note native + Java heap and the `MAP` render count, drive
  ~10 minutes (stationary + repeated identical route, per §65), compare against the pre-change build;
  the pool changes churn, not peak, so expect dampened heap saw-teeth rather than a lower peak.
- **Original fix candidate (2026-09-21, now half taken)**: reuse a caller-supplied pixel buffer across
  renders (bitmap + `int[]`), and hoist the overlay paints/paths into fields. Measure with
  `dumpsys meminfo` before/after; a phone render-path change, not a car-only one.

## 51. The host-crash mechanism: an app-process death takes the templates host down — Found 2026-09-21 (triage frame for the "AA crashes while NaviVeylin drives" report)

- **Mechanism** ℹ: the ordering is proven (pitfall in `guidelines/Build.md` §10) — the app is force-stopped → the host's queued template
  operation runs against an invalidated `CarHost` → `IllegalStateException: Accessed the car host
  after it became invalidated` in `com.google.android.apps.automotive.templates.host:renderer_service`
  (FATAL). The app process going away is *sufficient*; no app code has to run at that instant. So for
  any "AA/AAOS host crashed" report the first question is **what killed or blocked the app process**,
  not which host API was called.
- **Second confirmed path** ℹ: `RemoteUtils.dispatchCallFromHost` (car-app 1.7.0 sources,
  `androidx/car/app/utils/RemoteUtils.java:140-158`) runs every host call on the app's main thread,
  answers the host with a `FailureResponse` when the callback throws — and then **rethrows** on the
  main thread. A throwing host callback therefore kills the process *and* reports the failure to the
  host, which is what makes an app-side exception look like a host fault from the driver's seat.
- **App-process killers still open** ✗, ranked: (1) §49 — ~15 MB of
  transient buffers per full render with up to three live renderers (lmkd kill). ~~§48 (the
  unsynchronised `ClientData::knownPaths` vector in `openDatabase`, native SIGSEGV)~~ is **fixed** by
  `fix-native-database-open-race`; ~~the main-thread native work of §52/§53~~ was fixed by
  `fix-aaos-host-crash`; ~~any uncaught exception on a host path~~ is now covered end to end by
  `fix-car-host-mutation-guards` (see the entry below).
- **The remaining unguarded host paths** ✗ (found 2026-09-22 while triaging the "AA crashes after
  seconds to minutes" report, **fixed** by `fix-car-host-mutation-guards`): the 2026-09-21 work
  guarded host *callbacks*, the template builds and the enumerated host sends, but three mutation
  paths were still open, and each one is a process death → host FATAL through the mechanism above.
  (1) **Template-row click listeners** did `ScreenManager.push` directly — seven sites plus the
  back actions — inside the library's `dispatchCallFromHost`, which answers the host with a
  `FailureResponse` and then **rethrows on the app's main thread**
  (`RemoteUtils.java:140-158`, car-app 1.7.0). (2) **The session's own coroutines**
  (`observeJob`/`errorJob`/`tripJob`, `showNavigationScreen`, `showRootScreen`, `restoreDrivingMode`,
  `showError`) called `getCarService(ScreenManager).push/popToRoot` on a
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` with **no** `CoroutineExceptionHandler` —
  only `CarScreenObservations` had one, so the guarantee was asymmetric in exactly the places that
  mutate the host. (3) **The session lifecycle callbacks** (`onStart`'s host sync, `onDestroy`'s
  cleanup) were not confined, so a throw escaped the lifecycle observer into the library's dispatch.
  Fix: one guarded seam (`CarHostGuards.kt`: `guardedHostCall`, `armScreenPush`,
  `armShowOnMapSwap`, `dismissErrorNotice`), the shared fault handler on the session scope, every
  car screen's own scope (and the renderer's), and every `ScreenManager` mutation routed through it;
  click paths *arm* the navigation and build the target screen after the callback returns. Two
  second-order defects came out of the same review: `showError`'s delayed `popToRoot()` **popped the
  navigation screen** off the stack while the flag claimed it was shown (guidance silently gone,
  `showNavigationScreen` early-returning forever), and the session recorded a screen push *before*
  the host accepted it. Both are fixed by the same change (`SessionScreenStack`). Found and left
  for later: **a confined fault in the renderer's own loops ends that loop** — the map would freeze
  instead of killing the process, and there is no restart path for a dead render loop yet; and
  **the `remove(notice)` dismissal needs the notice to still be on the stack** (a no-op otherwise,
  which is intended but means a notice skipped while stopped is only removed by the next started
  sync).
- **Evidence recipe** ℹ: `adb logcat -b crash` for the host process, plus
  `adb logcat -d | grep -E 'onPackageUpdateFinished|onHandleForceStop'` for the app and
  `adb logcat -d | grep 'Diag/HOST'` for what the app last sent. A package replace/force-stop in the
  window means the harness artifact (`guidelines/Build.md` §10); no such line means an app-side defect.

## 56. Trip publishing is the one host sender that deliberately keeps firing while the session is stopped — Found 2026-09-21 (same review)

- **Observation** ℹ: `NavigationSession.kt:573-579` collects every navigation-state emission and calls
  `NavigationManagerController.publishTrip` (`NavigationManagerController.kt:88`), which has no
  lifecycle gate — deliberately, so the cluster/heads-up trip keeps updating while backgrounded.
  `NavigationTemplateMapper.hasTripChanged` (`:337-350`) compares `etaMillis / 1000` and rounded
  distances, so while driving this reaches the host's navigation service at roughly 1 Hz, each update
  carrying a maneuver icon in the `Trip`.
- **Why it matters** ℹ: this is the only host-facing send not bounded by `SessionHostGate`, and it runs
  exactly while the host may be tearing the app's surface down. The controller already treats a
  rejected `updateTrip` as "host session ended" (`onFailure { navigating = false }`), so the risk is
  the window before the first rejection.
- **Fix candidate**: either gate it on the session being started (accepting a stale cluster while
  backgrounded) or stop at the first rejection until the next `onNavigationStarted`. Decide with an
  AVD run counting `HOST` trip lines against host stability (`guidelines/Build.md` §10 baseline).

## 57. `DetailsScreen` observers are init-scoped and keep rendering while stopped — Found 2026-09-21 (same review)

- **Observation** ℹ: `DetailsScreen` starts its favorites, basemap and position collectors in `init`
  into `loadScope` (`DetailsScreen.kt:189-219`), cancelled only in `onDestroy` (`:266`). While the
  screen is stopped (backgrounded, or covered by a pushed screen) they keep calling `invalidate()` —
  a library no-op while the screen is not at least STARTED (`androidx/car/app/Screen.java:102-106`) —
  plus `invalidateData()` and `setGpsMarker`, which do request full native renders. Same defect class
  as the observer leak fixed by `fix-car-screen-observer-leak` on the three map screens, but here it
  is "runs while invisible" instead of "grows per start".
- **Fix candidate**: move them onto the same per-start tracking that change introduces, or stop them in
  `onStop` and restart in `onStart`.

## 63. The car path never configures the native tile data cache, and the phone path's value leaks into it — FIXED by `fix-car-tile-data-cache` (2026-09-26)

- **Fixed ✅** by change `fix-car-tile-data-cache` (spec: `native-tile-data-cache` — configured by every surface, decided once per client, car < phone): new `:core` seam `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt` owns the per-surface constants (`PHONE_TILES = 512` unchanged, `CAR_TILES = 128`, `LIBRARY_DEFAULT_TILES = 25`) and the once-per-client policy; the car warmup configures it in `AutoServiceModule.openMapDatabases` before `client.openDatabases(...)`, the phone path uses the same seam, and a later surface's different value is rejected instead of flipping the client-global capacity mid-session. Tests: `NativeTileDataCacheTest` (8, `:core`), `MapCanvasViewModelStyleTest` (11, phone path), `AutoMapDatabaseOpenTest` (9, car warmup incl. the phone-first and repeated-warmup cases; revert-checked — two cases fail with the car call disabled). This entry is removed when the change is archived.
- **Measurement still open** ⏳ (`TODO.md` §65 sibling): `CAR_TILES = 128` is reasoned, not measured — 5.1× the library default and 4× below the phone, chosen because no car/AAOS device or head unit is attached. Follow-up on the AAOS AVD or a head unit (`guidelines/Build.md` §10): enable `osmscout::log.Debug(true)`, confirm `adb logcat -s NaviVeylin` shows `[JNI] setNativeDataCacheSize(128)` and the per-render `applied tile data cache size 128 to N db(s)`, then compare `dumpsys meminfo <pkg>` native heap against the library-default run; re-tune by changing the one constant.
- **Original finding 2026-09-21 (same review; spec deviation)** ✗: the only caller of `setNativeDataCacheSize` is the phone map path
  (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1769`, constant 512 at `:3571`);
  the car warmup opens every regional database without configuring it
  (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:107-144`).
- **Footprint asymmetry** ℹ: the value is stored in the shared `ClientData` and applied by the native
  render path to **every** open database (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:1411-1429`,
  only when `> 0`). So a car-only process (AAOS, or projection with the phone UI never opened) runs on
  the library default of 25 tiles per database, while a phone UI that ran first in the same process
  leaves 512 per database for every database the car session opens — `N × 512` tiles of native heap,
  basemap included, on a device whose RAM budget is the §51 lmkd frame.
- **Not caught (original)** ✓ addressed: the spec's "configured on every open database" now has tests for both surfaces (`AutoMapDatabaseOpenTest` drives the real warmup against a temp map tree; `MapCanvasViewModelStyleTest` covers the phone path).
- **Fix candidate (original, taken)** ✓: the cache is configured in one `:core` seam used by both surfaces that open databases, with a value per surface — the seam's per-client rule resolves the shared-client case the entry's "value chosen per surface" left open (a second, different value is rejected, so the phone's 512 is not flipped mid-session by a car session).

## 78. Overrun-window offset math lives in `FollowPrediction` and the follow overlay path is not unified — Found 2026-09-24 (while fixing the phone pan tracking)

- **Debt** ℹ: `FollowPrediction.displayOffsetPx` is now the single offset helper for follow scrolling,
  the pan display window and the renderer's coverage predicate (`MapRenderer.overrunWindowCovers`),
  but it still lives on `FollowPrediction` (a prediction class) and its `anchor` parameter carries the
  follow anchor, so the name misleads. The pan/coverage use is the default (center) anchor — the
  absolute overrun-window shift.
- **Fix candidate**: move it (with `DisplayOffset`) into a neutral home, e.g.
  `core/OverrunWindow.kt` as `OverrunWindow.shiftPx(...)`, and update the follow call site, the pan
  path (`MapCanvasScreen.panWindowOffset`), the renderer predicate and the tests
  (`FollowAnchorFramingTest`, `MapPanDisplayWindowTest`, `MapPanHandlerTest` on the AA side).
- **Debt** ℹ: the follow overlay projection expresses the drift through the anchor center of the
  displayed position (geo), while the pan path translates the overlay layer by the clamped offset
  (`markerDisplayShiftPx`). Both are correct today (and mutually exclusive: a pan disengages follow),
  but two mechanisms for one rule invite a double application. The unified end state is one derived
  **displayed viewport** (frame viewport shifted by the clamped offset via `screenToGeoRotated`) that
  every overlay projects against in both modes.
- **Why deferred** ✗: the follow pipeline (and its blit offset call site) was being edited by the
  in-flight change `fix-phone-follow-blit-anchor-mismatch`; unifying then would have mixed two
  changes in one verified path.
- **Not caught** ✗: `MapPanDisplayWindowTest`/`MarkerDisplayShiftTest` pin the pan-side contract; the
  duplication itself has no test, only the rule that a follow-mode overlay shift must stay zero.
