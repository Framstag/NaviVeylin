# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done
**Entry metadata:** every numbered section carries `**id:** … · **category:** … · **class:** bug|improvement|feature · **status:** …` on the line under its heading. `class` describes the **remaining** work: a landed fix whose only residue is verification is an `improvement`, one whose residue is still a defect stays a `bug`. Ids are identity — never renumbered (a collision is reported and the duplicate gets a fresh id: the `§79` duplicate became `§119` on 2026-10-03). An id is retired only by **absorption**: the survivor's heading says `absorbed §N` and its body carries the folded entry's record verbatim, so the retired id needs no renumbering and its history is not lost.

**Clusters** (category, then class — jump targets, not an order):
- **build-and-harness** — bug: §146
- **build-and-harness** — improvement: §14 §17 §22 §37 §44 §66 §95 §123 §128 §132 §142 §143 §145
- **car** — bug: §57 §83 §92 §93
- **car** — improvement: §29 §34 §46 §56 §64 §103 §120 §127 §133
- **data-and-maps** — bug: §91 §99
- **favorites** — improvement: §102
- **location** — feature: §8
- **map-rendering** — bug: §19 §65
- **map-rendering** — improvement: §49 §71 §78
- **map-rendering** — feature: §6
- **native-jni** — improvement: §81 §82 §119
- **native-jni** — feature: §23
- **persistence** — bug: §157
- **persistence** — improvement: §9
- **route-and-navigation** — bug: §129 §130 §139 §144 §154
- **route-and-navigation** — improvement: §126
- **route-and-navigation** — feature: §1 §2 §3
- **search** — bug: §27 §75 §110
- **search** — improvement: §24 §76 §77 §109
- **specs-and-process** — improvement: §40 §113 §134 §153
- **stylesheets** — bug: §36
- **ui** — bug: §70 §74
- **ui** — improvement: §39 §73 §136 §155
- **ui** — feature: §4 §5
- **verification** — bug: §131 §147 §148
- **verification** — improvement: §10 §15 §16 §35 §84 §106 §108 §111 §141 §156

---

## 156. The pinned band's device containment at font scales 1.0/1.3/2.0, and the screen's report→fit/inset join, are still unverified — Left pending by `fix-pinned-band-height` (archived 2026-10-09, task 5.1)
**id:** 156 · **category:** verification · **class:** improvement · **status:** open (device)

- **Observed** ℹ: `fix-pinned-band-height` proved the band keeps its action's tap target **host-side**
  (font-scale 2.0 case: End 42.67 dp → 53.33 dp, `BandGeometry` rows retained in the case's XML `system-out`),
  but its own `openspec-verify-change` run recorded one CRITICAL — task 5.1 unchecked. Two claims therefore
  stand unverified: (a) the device symptom that opened §138, that the labelled action leaves the card and
  reaches **no** UI dump at font scale 2.0 (a host harness measures ~1 px/char and cannot reproduce the
  device's label wrapping), and (b) the screen's wiring between the card's report and its consumers
  (`MapCanvasScreen.kt:2401-2404` → `setOverlayCoveredPx` / `bottomInset`), which the host case reproduces
  one-for-one instead of executing (no Hilt host).
- **Consequence** ⏳: a device-level regression of the same user-visible symptom would again be found by a user,
  not by the suite; the host case pins the band's shortfall, not the containment.
- **Fix candidate**: with a phone emulator, run at font scale 1.0/1.3/2.0 and take a UI dump per scale:
  assert the labelled action's node exists with a ≥48 dp box inside the card's bounds and that
  `onOverlayHeightChanged`'s reported value equals the covered band the map inset consumes
  (`guidelines/Build.md` §10/§11 recipes, `pixel-check`/`compose-geometry` skills); record the dumps with their
  numbers in the change's task 5.1 rather than a new claim.

---

## 155. The stop control's own accessibility target is asserted by geometry only, not by its semantics action — Found 2026-10-09 by `bugfix-loop` (iteration 1) while refuting §122
**id:** 155 · **category:** ui · **class:** improvement · **status:** open

- **Observed** ℹ: `NavigationStateOverlayComposeTest` pins the control's box
  (`stopControlHitAreaIsAtLeast48Dp`, `stopControlBoundsLieOutsideDetailsRegionBounds`,
  `stopControlCentreEndsNavigationWithoutOpeningDetails`) but nothing asserts that the control is an
  **actionable semantics node of its own** — the property that decides whether a tap or a screen reader
  resolves to the stop action rather than to the surrounding card. The §122 investigation measured that
  property out of band (probe over the merged tree: `Tag:'stopNavigation' Role='Button' Actions=[OnClick]`),
  so the measurement exists but no case holds it.
- **Consequence** ⏳: a refactor could fold the control back into a card-wide clickable and every existing
  assertion would stay green — exactly the regression class §122 reported (device dump, 2026-10-07).
- **Fix candidate**: one host-only case in the same class asserting the semantics contract — the control
  node `assertHasClickAction()` with its own `contentDescription`, and the details region's node does not
  own that action (`SemanticsNodeInteraction` fetchers on the merged tree; no device needed).

---

## 154. The navigation overlay's stop path may never enter the session's stopped state, and the map returns to `FREE_DRIVE` where the ViewModel path asserts `BROWSE` — Filed 2026-10-06 by `fix-nav-overlay-stop-tap` as §140 (an id that never reached this file); re-filed 2026-10-09 by `bugfix-loop`
**id:** 154 · **category:** route-and-navigation · **class:** bug · **status:** open

- **Observed** ℹ: the archived `fix-nav-overlay-stop-tap` records — in `design.md:47` and its device tasks —
  that the card's stop ends navigation and clears the route **without** entering the session's stopped state
  (`RoutePanelVM: session grace period expired` appeared in none of its six runs, no Restart/End screen was
  ever observed), and that the map afterwards showed `exit_free_drive` (= `FREE_DRIVE`) although the
  pre-navigation label was `start_free_drive` (= `BROWSE`, the mode `map-modes` returns to and
  `MapCanvasViewModelModeTest.navigationEndRestoresBrowseMode` asserts for the ViewModel path). Its own
  caveat, quoted: one run, on a tree with other in-flight work.
- **Consequence** ⏳: the grace period of `route-planning-session` has no **observed** reachable UI path on
  the phone, and the map can land in a mode the ViewModel-level case excludes. Nothing tracks the subject:
  the archived change's own reference `TODO.md` §140 is dangling (grep 2026-10-09: no `## 140.` heading and
  no `§140` mention in this file or in the archive).
- **Fix candidate**: diagnose on a quiet tree with a device — `adb logcat -s NavigationEngine RoutePanelVM`,
  stop from the status card, then record the mode label and whether the grace line appears; if the ViewModel
  path is correct and only the UI entry is missing, the fix is the missing entry point (a spec decision for
  `route-planning-session`), which makes this entry **not loop-eligible until diagnosed** (`.pi/skills/bugfix-loop`
  gate condition 2: one root cause, no option to weigh).
- **Provenance** ℹ: filed by `fix-nav-overlay-stop-tap` (2026-10-06) as `§140`; that id does not exist. Ids
  are append-only and never renumbered, so the content is re-filed under the next free id by the loop's
  own bookkeeping rule (append-only, report the anomaly).

---

## 120. The car re-applies the `daylight` flag after every style switch, so a switch costs a second stylesheet reload — Found 2026-10-03 while implementing `dedupe-stylesheet-loads` (out of that change's scope)
**id:** 120 · **category:** car · **class:** improvement · **status:** open

- **Observed** ℹ: `MapScreen.kt:468` and `NavigationScreen.kt:477` force the flag after a style load
  (`pushDark(force = styleChanged)`), and `setStyleSheetFlag` reloads the style variant
  (`OSMScoutClient.java:92-102`), which reloads **both** the main and the basemap stylesheet
  (`guidelines/MapRendering.md` §16). A user-initiated style switch therefore costs one `loadStyleSheet`
  plus one flag reload of the whole set. The appliers themselves already dedupe by value
  (`CarStyleApplier` by style name, `CarDaylightApplier` by `daylight`), so an unchanged pair and an
  unchanged settings re-read cost nothing — this is the only redundant application left on the car.
- **Why it was left** ✗: removing the force assumes a style load carries the database's stored flag.
  `MapRendering.md` §15 documents the flags as the load's argument (`LoadStyleInternal(stylesheet, flags)`)
  but only for the flag path, so the assumption needs a native check — a submodule test asserting
  `loadStyleSheet("cycle")` leaves the `daylight` flag as set, or an AVD run switching style while dark.
  Unverifiable in the 2026-10-03 session (`adb devices` empty, no `emulator` binary).
- **Fix candidate**: if the check shows the flag survives a style load, drop `force = styleChanged`
  (`CarDaylightApplier` then keys on the pair) and tighten the `map-styles` scenario "Style switch loads
  once for the style change" to the phone's rule (one load, no flag application) in the same change; if it
  does not, keep the force and record it as required behaviour in `guidelines/MapRendering.md` §15.
- **Related** ℹ: `dedupe-stylesheet-loads` fixed the phone side of the same class and left the measured
  per-startup attribution to a device run (its tasks 1.1/1.2). If that attribution names repeated `initMap`
  re-entry (two loads per entry) rather than a re-observation, the next change belongs in that path.
- **Folded §116 (2026-10-05, `cleanup-todo`) ℹ** — the phone half of the same class, with its measurement: one
  startup of the debug build emitted **104** `Unknown types in '…'` lines — 8 files (`standard.oss` plus `basemap`,
  `place`, `religious`, `shop`, `tourism`, `natural`, `amenity`) × **13 loads**, all inside one process (`Start proc`
  count 1, same pid, 13:08:28-13:08:41); the native Info line agreed — `Created new style with …/standard.oss`
  appeared **12** times in the same window, the basemap's own stylesheet loading per basemap database open on top.
  Every load parses `standard.oss` and its ~40 `MODULE` includes, so a startup paid that parse cost a dozen times
  (and, before `condense-style-load-warnings`, 9159 log lines for it); the condensation did not create the
  repetition, it attributed each line to a file (`TODO.md` §91/§99 are the data side of the same lines). Fix
  candidate (taken by `dedupe-stylesheet-loads`): find what re-applied the stylesheet per startup (the settings
  emission into `MapCanvasViewModel.applyStyleSheet`, the basemap database open, `reloadBasemap` after the asset
  refresh, the additional-map `openDatabases` batch) and keep one load per (style file, flag set) per startup — e.g.
  skip a reload whose stylesheet path and flags equal the active ones, which needs `DBInstance`/`DBThread` to expose
  the active stylesheet plus its flags; verify with `adb logcat -s NaviVeylin | grep -c 'Created new style'` (expect
  1-2 per surface) and the report count (8, not 104).

---

## 113. Origin changes ship without a falsification step — every later fix change then adds one — Proof gap of the 2026-08-13 … 2026-09-19 origin changes, found 2026-10-01 auditing the 15 newest `fix-*` changes (process)
**id:** 113 · **category:** specs-and-process · **class:** improvement · **status:** open

- **Observed** ℹ: of the origin changes behind those 15 bugfix changes, the ones from `2026-08-13-auto-startup-crash-diagnostics` to `2026-09-19-fav-order-in-group` carry **0** `verify:` clauses in `tasks.md` (`2026-09-11-tile-cache-perf` 11 lines / 0 clauses, `2026-09-08-turn-instructions-not-l10n` 0, `2026-09-04-enlarge-phone-nav-overlays` 0, `2026-09-04-fix-route-refresh-and-auto-zoom-reengage` 0, `2026-08-13-auto-startup-crash-diagnostics` 0; only `2026-09-17-basemap-own-stylesheet` has 13). Every fix that followed added the falsification the origin lacked: `fix-favorite-store-write-race` tasks 1.5 and 3.3 remove one mutex and require the interleaving case to fail, `fix-native-database-open-race` task 1.4 removes six `scoped_lock` lines and requires SIGSEGV (3/3 runs). Where an origin did see the risk it deferred instead of proving it — `2026-09-19-fav-order-in-group` task 3.4: "Append the pre-existing, unrelated risk found while implementing this change — interleaved repository writes …".
- **Consequence** ⏳: an assertion that guards an invariant is never shown to be *capable of failing*, so a case with the wrong arguments, the wrong seam or a tautology passes as proof (the `FollowAnchorFramingTest` anchor case, §111, is exactly that shape). Two capabilities were patched for the same class inside one week (`fav-service` + `osmscout-jni`, `native-tile-data-cache` + `smooth-zoom`).
- **Fix candidate**: rules text — one revert-check task per new invariant in `tasks.md` (remove the guard, one mutation, name the case that must fail; restore it), plus the `guidelines/Build.md` §6 bullet drafted by this audit. Cross-ref §40 (guardrails), §111.
- **Infrastructure landed 2026-10-03** ✅ (rules text + skill; the per-change history stays open until a change
  written under the new rules archives):
  - **`revert-check` skill** (`.pi/skills/revert-check/SKILL.md`, machine-local like the other skills) — the full
    discipline: name the triple (invariant / single mutation / case that must fail) *before* touching code, one
    mutation only (§40.12), run **every** case the guard protects, require the failure to be the expected
    assertion (not a premise or a compile error), restore and grep the mutation marker away, then force the
    green run (`--rerun-tasks`) — because restoring returns the tree to a cached state that would answer
    `UP-TO-DATE` while the XML keeps the previous `timestamp` — and quote both runs in the task. It also covers
    the honest outcome when a guard is structural and cannot be falsified through the public API (the
    `fix-phone-gesture-pan-tracking` task 7.6 precedent).
  - **`openspec/config.yaml` `rules.tasks`** now asks for one revert-check task per new invariant, bound,
    predicate or refusal path, and names the skill — so a change written from here carries the task.
  - **`guidelines/Build.md`** §4 gained the two rules this needed (a restored tree is a cached tree — check the
    XML `timestamp`; sweep the suites for the constants/values a change moves *before* the gate), §1 lists the
    fourth skill, and §2's output contract was corrected to the foreground + redirect pattern — all three
    build skills (`build-app`, `run-tests`, `release-build`) prescribe it now, and the old `nohup` flow is gone.
    `AGENTS.md`, `openspec-apply-change` and `run-tests` point at `revert-check` by name.
  - **Evidence for the discipline**: `fix-area-fit-zoom-rounding` ran four revert-checks this way (loop removed →
    3 cases, favourite call site unverified → 1, floor back to 14 → 1, embedded fit unverified → 2), and one of
    them showed a case was *not* falsifying its guard — which is what the skill exists to catch.

---

## 111. The follow blit anchor is proven only as pure geometry — no test drives the production call site, and the pipeline was patched twice on 2026-09-27 — Proof gap of `anchor-per-surface-visible-area` (and of `fix-follow-vehicle-jumps`), found 2026-10-01 auditing `fix-phone-follow-blit-anchor-mismatch` (spec `smooth-follow`)
**id:** 111 · **category:** verification · **class:** improvement · **status:** open

- **Observed** ℹ: `FollowAnchorFramingTest` (`app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt`) proves the framing contract with the anchor fractions it is *handed* — `:203` "resolved anchor keeps the marker on the content with overlays" was green while the follow display block in `MapCanvasScreen.kt` passed `ui.activeFollowAnchor` (raw preset ≈ 0.9) to `FollowPrediction.displayOffsetPx`, so the map content sat at the raw anchor while the marker drew at the resolved one (≈ 0.76) — a `(0.9 − 0.76)·H` offset plus per-500 ms re-render churn. Production now passes `state.resolvedAnchor` (`MapCanvasScreen.kt:1506`), the same day `fix-follow-vehicle-jumps` had already reworked this pipeline without catching it. The mismatch fix's own regression case (`:269`) asserts the *defect geometry* by handing the raw preset in, so nothing observes which anchor the **call site** passes — its task 1.1 closed that by inspection only ("grep `activeFollowAnchor.fx|fy`").
- **Consequence** ⏳: the call site can regress to the raw preset again and the suite stays green; the symptom (marker riding ahead of the route content in the driving direction, permanent render churn) is only visible on device. Two fixes to the same anchor in one day is the evidence that geometry tests do not protect the wiring.
- **Fix candidate**: assert the anchor at the call site — drive the follow display block, or extract a `followOffsetArgs(state, ui)`-style seam the screen must call, with overlays measured and assert the fractions equal `state.resolvedAnchor.fx/fy`; add a guard that fails when `activeFollowAnchor` is read from the follow offset path. Cross-ref §109 (same class: helper proven, wiring not).

---

## 110. The scope filter can leave a scoped search with fewer free-text candidates than the requested limit — Found 2026-10-01 while implementing `fix-cross-database-search-scope` (design Open Question)
**id:** 110 · **category:** search · **class:** bug · **status:** open (device)

- **Observed** ℹ: the free-text walk stops once it has collected `limit` hits (`freeTextEntries.size() >= static_cast<size_t>(limit)`, `OSMScoutClient.cpp:3958-3960` — the per-source budget that keeps the text index from crowding structured results out, spec `search-free-text`), and the scope filter runs **after** the walk (`fix-cross-database-search-scope`). When the scope drops 30 of 60 collected hits, the caller receives fewer than the requested candidate count even though more in-scope hits may exist behind the walk's stopping point.
- **Consequence** ⏳: a scoped search can display fewer results than it should — a recall loss only on installs where a database holds objects outside the scope's extent, so it does not affect the phone's common single-map case. The displayed list stays correct (ranked, no wrong entries), it is just shorter than the candidate budget allows.
- **Fix candidate**: count only *kept* hits against the budget (test the scope inside the collection loop, before `freeTextLimitReached()`), or raise the collection budget by a bounded factor when a scope is active and trim after filtering. Both need a native measurement of the walk's cost on a real index (the reason the budget exists), so this waits for a device run.
- **Not verifiable today** ✗: the difference is only observable with two loaded maps of different regions plus a GPS scope, i.e. the same device setup the change's task 5.3 needs.

---

## 109. The cross-database scope filter is host-tested only as a pure helper — the wiring inside the JNI search has no database-backed test — Found 2026-10-01 while implementing `fix-cross-database-search-scope`
**id:** 109 · **category:** search · **class:** improvement · **status:** open

- **Observed** ℹ: the geographic decision itself is covered by host tests (`Tests/src/SearchScopeTest.cpp`, 9 cases / 50 assertions: inside/outside, corner normalization, unset box fail-open, node fallback, the superset property, non-finite positions), but the code that *uses* it — deriving the extent from a region's object and dropping free-text hits in `DoSearchLocations` — runs only inside the JNI translation unit, which the host build cannot compile (TODO §66: `jni.h` is configured against a JDK that is not installed).
- **Consequence** ⏳: a regression that stops calling the filter, or that derives the extent from the wrong region, is not caught by any test — only by the on-device check of that change (task 5.3, device-gated) and by code review.
- **Fix candidate**: a database-backed native test in the submodule's `Tests/` that imports two small maps (the runtime-import pattern of `LocationServiceTest.cpp`, full non-eco imports per §40.34) and asserts that a hit outside the scope's box from the second database is absent and an in-scope one is present; alternatively a narrower seam that exposes the extent derivation for a host test.

---

## 102. Four scenarios added by `reorder-favorite-groups` have no automated test — Found 2026-09-29 (during that change's task 5.5 traceability pass)
**id:** 102 · **category:** favorites · **class:** improvement · **status:** open

- **Coverage gaps** ⏳: the change's specs are behaviour-complete, but these scenarios rest on a library
  contract, on a surface the unit tests cannot compose, or on a failure the test doubles cannot inject:
  1. `group-ordering` — *A group used as a single-group default is the first stored group*: the sheet picks
     the first group of `orderedGroupNames(state.groupOrder, state.groups.keys)` for "Add current location",
     but that line is inline in `FavoritesSheet`'s dialog wiring, so no test asserts which group it picks
     (the helper itself is covered in `:core`). A sheet-level Compose test with a ViewModel fake would
     close it.
  2. `group-grid-display` — *Dragged card follows the finger* and *Nothing is persisted during the drag*:
     the lifted card and the shift-to-make-room are `sh.calvin.reorderable`'s contract; the tests only
     observe that nothing is committed until the release (`FavoritesSheetGroupReorderComposeTest`). The
     same two scenarios are equally untested for the favorite reorder (spec `fav-management-ui`), so this
     is a gap of the pattern, not of this change.
  3. `group-grid-display` — *Sheet dismissed during a drag*: the deferred-commit design covers it (the
     commit is a `LaunchedEffect`, cancelled with the composition), and the code states it, but no test
     dismisses the sheet mid-drag; the favourite equivalent has the same gap.
  4. `group-ordering` — *Persistence failure keeps the app usable*: no test injects a failing
     `saveFavoriteLocations` (the fakes always return `true`), so a persist that fails while the native
     move succeeded is unverified — inherited verbatim from `fav-ordering`'s identical requirement.
- **Fix candidate**: (1) a `FavoritesSheet` Compose test over a fake `FavoritesViewModel`; (2)/(3) accept the
  library contract and record the visual checks in the on-device pass, or drive a dismissal in the Compose
  test; (4) give both fakes a `saveFailure` switch, then assert the snackbar/message path in
  `FavoriteRepositoryTest` and `FavoritesViewModelTest`.

---

## 95. The retention release had no on-device run: the installed build is Play-signed, so a local build needs an uninstall — and the map data goes with it — Found 2026-09-27, **RESOLVED 2026-09-27 20:27** by a Play release (see the update at the end)
**id:** 95 · **category:** build-and-harness · **class:** improvement · **status:** on-hold (decision)

- **Observed** ℹ: the phone's installed build (`2026-09-27-3`, versionCode 89, `installerPackageName=com.android.vending`) came from the Play Store, so `adb install -r` with a locally built APK fails on the signing key, and a fresh install means `adb uninstall` first — which deletes the app's files **including the installed map databases** the on-device checks render from (region + basemap; §99/§91 already report that this install's map data is the weak part). Play App Signing cannot be reproduced locally.
- **Consequence** ⏳: `bound-tile-data-retention` tasks 5.1-5.5 (the walk ceiling, the platform-level release, the poll firing, the car-session scoping) cannot run on this phone without a map re-download; the AAOS AVD has the car side but no phone map path, and the walk protocol needs the phone surface.
- **Fix candidate**: decide with the owner — either a second sideloaded variant with its own `applicationId` for measurements (no data loss, maps re-downloaded once), or a dedicated sideloaded test device, or accept an uninstall+re-download for the measurement pass. The measurement itself is scripted already (`guidelines/Build.md` §10: keyevent/walk protocol + the high-water-mark rule).
- **Update 2026-09-27 20:27 — RESOLVED for the phone, with a residual** ✅: the owner published the memory work through Play (`2026-09-27-4`, versionCode **90**, `installerPackageName=com.android.vending`, `lastUpdateTime=2026-09-27 20:27:17`), so the phone now runs the new code — verified by pulling the installed `split_config.arm64_v8a.apk` and finding `OSMScoutClient_renderInto` in `libosmscout_client_java.so`, not by trusting the version string. Device verification therefore works again for **Play-released** builds; the residual is that a *local* build still cannot be installed over it, so an in-build A/B (e.g. one path reverted) still needs the uninstall/re-download or a second variant. Plan the next measurement pass around a release rather than around a sideload.
- **Two device facts from that pass worth keeping** ℹ: (1) `am send-trim-memory <pid> UI_HIDDEN` is *not* a valid level name — the shell's names are `HIDDEN`/`BACKGROUND`/`RUNNING_*`/`MODERATE`/`COMPLETE`, and the platform refuses `BACKGROUND` on a **foreground** process ("Unable to set a background trim level on a foreground process"); the reliable trigger is `HIDDEN` (works on the foreground app) or just pressing HOME, where the platform delivers `UI_HIDDEN` itself. (2) The app's `ActivityManager.MemoryInfo.lowMemory` poll produced **no** record across a 43-render walk and a long session on this chronically-pressured device, but a later pass the same evening caught it firing (`retention released: trigger=poll-low (avail=215MB threshold=216MB)`), so the poll is a late-but-real trigger on this hardware and the band was deliberately not widened. (1) is the trim-level recipe to reuse.

---

## 99. The AAOS AVD's basemap is a format version behind the submodule, so car-side device work on it cannot draw the basemap until it is re-downloaded — Found 2026-09-27 during the emulator A/B (absorbed §90 on 2026-10-05)
**id:** 99 · **category:** data-and-maps · **class:** bug · **status:** open (device)

- **Observed** ℹ: on `emulator-5554` (Automotive_Distant_Display, x86_64, SDK 33) the app opened its installed
  region (`openDatabase(nordrhein-westfalen-27-20260820-0826) -> true`) but the **basemap** failed:

  ```
  E NaviVeylin: File '…/files/maps/basemap/basemap/types.dat' does not have the expected format version!
                Actual 26, expected: 27
  W NaviVeylin: Cannot open db '…/files/maps/basemap/basemap'!
  ```

  So map rendering works (the region carries the tiles), but anything needing the basemap (sea/land background,
  borders, country names — and the phone-side "basemap present" paths) silently degrades on that AVD. The
  expected version moves with the libosmscout submodule, so this AVD's data predates a bump.
- **Consequence** ⏳: car-side device checks that involve the basemap (or compare against a phone that has one) are
  not comparable on this AVD until its basemap is re-downloaded through the app's map manager. Region-only checks
  (render counts, surface health, memory) are unaffected — the A/B in `reduce-render-peak-memory` used the region
  and was valid.
- **Fix candidate**: after any submodule bump that changes the data format, re-download the basemap (and ideally
  the region) on the AVDs that carry installed maps; note it in the `update-to-current-libosmscout-master` skill so the
  next person does not read the failure as an app defect.
- **Folded §90 (2026-10-05, `cleanup-todo`) ℹ**: the rejection's full log shape, from the same `emulator-5554` (AAOS)
  session — every start logged the download layer finding the basemap (`D MapDownloadModule: basemap found at
  /data/user/10/com.framstag.naviveylin/files/maps/basemap`, then `found valid map 'basemap' …` for **both**
  `…/maps/basemap` and `…/maps/basemap/basemap`, i.e. the package carries a nested directory), followed by
  `E NaviVeylin: File '…/maps/basemap/types.dat' does not have the expected format version! Actual 26, expected: 27`,
  `E NaviVeylin: Cannot load 'types.dat'!` and `W NaviVeylin: Cannot open db '…/maps/basemap/basemap'!`, while the
  region database opened normally (`openDatabases -> 1/1 registered`, `openDatabase(nordrhein-westfalen-27-20260820-0826) -> true`),
  so `Installed map databases: 1` and every render ran **without the basemap overlay**. Further candidates from the
  folded entry: a data-version-aware download that detects the mismatch and re-fetches, a diagnostics entry when a
  known package is rejected for a format reason (instead of a per-start `E` line), and confirming the nested
  `maps/basemap/basemap` layout against the downloader's expected layout.

---

## 93. The AA start crash of release `2026-09-27-2` has no root cause yet — only its confinement is fixed — Found 2026-09-27 (release 88, versionCode 88, built 08:47)
**id:** 93 · **category:** car · **class:** bug · **status:** open (device)

- **Observed** ℹ: Android Auto crashes directly after start on release `2026-09-27-2`. The release's **only** car/AA-path source change is `d1e94f2` (`one-navigation-engine`, 08:26 — `AutoServiceModule`, `NavigationViewModelModule`, `NavigationNotificationService`, `auto/NavigationSession`), whose own commit message records that its on-device AA checks (tasks 7.5/7.6) were "recorded as done without a device run"; the other change in the window (`09a0e3c`, 08:45) touches the phone renderer only, and everything after 08:47 is docs/archive plus the libosmscout submodule bump (which the release does **not** contain).
- **What is ruled out** ✅ so far: the release artifact itself is sound — `app-mobile-release.aab` carries all three ABIs for all eight native libraries (including `libosmscout_client_java.so` under its release name), the stylesheets and the license assets, `mapping.txt` shows the minified build keeps `OSMScoutClient`/`OSMScoutClientBuilder`/`NaviVeylinCarAppService` unrenamed with the native-method keep rule present, and the manifest carries the FGS types/permissions, the car-app service, `automotive_app_desc` and `minCarApiLevel`. My own AAOS runs today start the car session fine on a debug build of a *newer* tree.
- **Consequence** ⏳: the reported symptom (AA dies immediately) is unexplained. `fix-navigation-engine-fault-isolation` (this change) only guarantees that an engine fault is confined, recorded (`ENGINE_FAULT`, with the faulting coroutine and first stack frame) and raised as an engine error — so the next occurrence becomes diagnosable instead of invisible, and a fault of this family can no longer kill the process. It is **not** a root-cause fix.
- **Evidence still needed** ℹ: the crash stack from the phone (`adb logcat -b crash -d`, or Play Console → Crashes, or `adb shell dumpsys dropbox --print | grep -B5 -A40 naviveylin`) — it decides between (a) an unconfined engine fault, (b) the car-only engine resolution introduced by `one-navigation-engine` (the warmup now resolves the phone Hilt graph), (c) a phone-side rendering regression from `09a0e3c`, and (d) a host/template constraint on projection. Also useful: does the phone UI start normally, and does the previous release (versionCode 87) run AA fine?
- **Fix candidate / follow-up**: re-run the archived `one-navigation-engine` device checks (phone start/stop/reroute; car-only deep-link start + connect/disconnect mid-navigation) plus a **release-variant** AA smoke on a real phone + head unit before the next release, and re-open or supersede that change if the stack points into it. Consider a release gate: build the minified variant for any AA-path change and run the `guidelines/Build.md` §10 recipe against it, because a device run on a debug build does not cover R8.

---

## 92. The car free-driving surface can stay blank for ~30 s after the screen opens — Found 2026-09-27 on `emulator-5554` (AAOS) during the `compass-day-night-palette` on-device pass (low confidence: 1 of 4 runs)
**id:** 92 · **category:** car · **class:** bug · **status:** open (device)

- **Observed** ℹ: when the free-driving screen was pushed (`Freie Fahrt`) right after a session that had toggled the host day/night signal twice, the map surface showed **no frame at all for ≈31 s**: `FreeDrivingScreen: Free driving renderer ready` at 11:36:38 and `AutoMapRenderer: renderer#1 surface created` immediately after, but the first `Diag/MAP: render center=…` only at **11:37:07**; the screenshot at 11:36:52 was uniform `#040505` (black surface, host chrome still drawn), and the log's last event before the frame was a second layout pass (`FreeDrivingScreen: visible area Rect(9, 66 - 1071, 519) -> pillTopInset=66` at 11:36:51). Four later runs on the same build rendered the first free-driving frame within **≤4 s** (content already visible in the first sample), so the delay did not reproduce and is not attributed to `compass-day-night-palette`.
- **Consequence** ⏳: if a driver taps `Freie Fahrt` at a moment the host is re-composing (e.g. after a day/night switch), the map can stay black for tens of seconds with only the template chrome visible — no error, no spinner, nothing to tell the driver whether the app is working. The same blank window was also seen on one day/night switch sample (`fd-day2` after the host re-delivered the surface), then not again on the sampled switch run.
- **Fix candidate**: commit a first frame as soon as the renderer has a usable surface + a viewport (the renderer currently waits for the next fix/layout change), and/or make the free-driving screen show its last frame or a placeholder while no frame has landed. Verify with a repeat of the measurement (`screencap` + `Diag/MAP: render center` per 5 s for 60 s after the push), and with the host deliberately re-composing (`cmd uimode night yes/no`) before the push.

---

## 91. POI name search finds nothing for objects the category index returns — this install's name index does not carry its POIs — Found 2026-09-27 on `emulator-5554` (AAOS) during the `fix-compound-name-matching` car pass (data side; absorbed §89 on 2026-10-05)
**id:** 91 · **category:** data-and-maps · **class:** bug · **status:** open (device)

- **Observed** ℹ: on the car, `POIs suchen → Restaurants` lists **`Mensa`** (`amenity_restaurant - 600` m), but the free-text search for that exact name answers `D SearchScreen: Search results: 0 for 'Mensa'`. The same asymmetry explains the folded §89's `Hilpert` result: the object can exist in the *category* (location index) while the *name* query (text index) finds nothing. One startup also emits **3078** `W NaviVeylin: Unknown type '…'` warnings, i.e. the stylesheet declares far more types than the installed database's typeconfig carries.
- **Consequence** ⏳: any on-device check that searches a POI by name (the `fix-compound-name-matching` 5.4/5.5 positive cases, `Theater Dortmund`, `Hilpert Theater Lünen`) is blocked by the install, not by the matcher; a name search that returns 0 for an object the category list shows is also a user-visible inconsistency (the app offers a POI it then cannot find by name).
- **Fix candidate**: same as the typeconfig gap (§89, folded below) — rebuild/re-download the map set from a typeconfig that matches the shipped stylesheet (which should fill both indexes); and add the small startup diagnostic the folded entry proposes (compare the stylesheet's declared types against the database's typeconfig once and report the missing set) so this is visible as a data report instead of a wall of per-render warnings.
- **Folded §89 (2026-10-05, `cleanup-todo`) ℹ**: the same data side seen from the other index — the loaded database's type config lacks POI types the stylesheet declares, so their objects were never imported: `Hilpert Theater Lünen` returned the town `Lünen` (12 km) and nothing else, and `Hilpert` alone returned **0 candidates** (`searchLocations: query='Hilpert', adminRegionHandle=1, candidates=0`) although `fix-compound-name-matching`'s proposal names that POI (`Heinz-Hilpert-Theater Lünen`, OSM way 38028287); every render logged `W NaviVeylin: Unknown type 'amenity_theatre'` (and `amenity_cinema`, `amenity_fire_station`, `amenity_parking`, …), so `Theater Dortmund` returned only streets/garages. The phone half of that change's task 5.4 is blocked by data, not by code; `Erbstollenstraße 10 58454 Witten` (perfect match + house levels) and `Erbstollenstrasse` (transliteration → perfect match) do verify on the same install — road/street types are present.
- **Folded §89 — adjacent observation and its fix in flight** ℹ: on a device with several installed maps, `NavGraph.kt` opened `installed.first()` (the first directory the filesystem lists) as the primary database (here `iceland`, which is what `viewport-iceland.json` and the diagnostics `map=iceland` lines name, while the Dortmund data actually came from the additional databases); there was no persisted "last used / default map" for the phone's first open. **Fix in flight** ⏳ (2026-10-02): `fix-start-map-selection` gives the phone one shared discovery for its start map (`StartMapResolver` + the pure `StartMapSelection` rule in `:core`), records the opened database in `AppSettings.lastMapPath` from `MapCanvasViewModel.initMap` (normalized to the database directory, so a container-root open still matches) and falls back to the lexicographically smallest installed database when the recorded one is gone. Specs: new `start-map-selection`, modified `map-download-infrastructure` ("Installed maps discovered at startup" now binds every caller to the one rule). Verification: rule 8/0, resolver 8/0 (`--tests` runs, three revert-checks quoted in its tasks), recording 5/0, automotive `:app` 196 of 197 classes / 1471 tests / 0 failures, both flavors built with all three ABIs; the phone recipe is in `guidelines/Build.md` §10 ("Start-map selection on the phone") and its on-device half is device-gated (`adb devices` empty, 2026-10-02). This part is removed when that change is archived.

---

## 84. Two surfaces in one process shared their process-global resources without an owner rule — FIXED by `shared-resource-arbitration` (2026-09-26); device verification pending
**id:** 84 · **category:** verification · **class:** improvement · **status:** open (device)

- **Fixed** ✅ by change `shared-resource-arbitration` (specs: `location-updates-lease`, `settings-persistence`, `native-tile-data-cache`, `car-session-presence`):
  - `LocationService` now hands out named leases (`acquire(consumer)` → `LocationLease`, consumers `phone-map`/`phone-nav`/`car-session`/`car-nav`); updates run while at least one is held and stop with the last release, so one surface's stop can no longer kill another's fixes. The bare `start/stopLocationUpdates` pair is gone. The engine call sites hold their lease only while a navigation attempt/session is active (the old `init` starts are gone), which is the semantics `one-navigation-engine` targets. Tests: `LocationServiceTest` 19/0 (6 lease cases incl. idempotent acquire, one-release-keeps-the-other, last-release-stops, diagnostics naming).
  - `SettingsStorage.update { }` serializes every read-modify-write under one lock (and `load()` takes the same lock so a read cannot see a half-written file); all 13 phone/car writers were converted. Revert-checked: with the lock removed, `concurrentWritersLoseNoUpdate` (barrier-forced interleave) fails. Tests: `SettingsStorageTest` 18/0.
  - `NativeTileDataCache` keeps the **highest** requested capacity and never lowers it, so car-first ordering no longer degrades the phone to 128 tiles/db; a car-only process keeps the car value. Revert-checked: 3 cases fail with first-writer-wins. Tests: `NativeTileDataCacheTest` 11/0.
  - New process-scoped `CarSessionPresence` (in-memory only) published by the car session on start/destroy; the phone shows an advisory pill (`car_session_active_indicator`, de+en) and disables nothing. Tests: `CarSessionPresenceImplTest` 5/0, `CarSessionIndicatorComposeTest` 2/0, German completeness green.
- **Resolves** ✅ the deferred item in `fix-client-dpi-surface-leak` (proposal decision 3): the phone `ON_PAUSE` and the car `stop()` started/stopped one non-refcounted subscription, so the later call won. The lease is that arbitration.
- **Still open on device** ⏳ (tasks 6.5/6.6 of the change): pause the phone during a car drive and confirm car fixes continue; end the car session and confirm phone fixes continue; a car and a phone settings change in the same minute must both persist; `adb logcat -s NaviVeylin` should show `location lease acquire/release: <consumer> (held=N)` and the raised cache value; on the AAOS AVD confirm the car-only process keeps the car capacity and the presence signal has no phone-side effect. Recipe in `guidelines/Build.md` §10.
- **Verification gap** ⏳: no `:auto` test constructs `NavigationSession` (needs a `CarContext` + Hilt entry point), so the two presence publish sites are compile/inspection-verified plus seam-tested; and placement of the phone pill (centre-left) is a UI-review decision open to change without touching the seam.
- **Not addressed here** ℹ: the `CAR_TILES = 128` measurement is still reasoned, not measured — the policy change does not alter the car-only value. The recipe lives in §65, the tile-cache entry this one closed on 2026-09-27.

---

## 83. The rail-widget tap fix is unit-verified but not confirmed on device — Found 2026-09-26 (reported on phone + head unit under Android Auto) and implemented by `fix-car-rail-widget-tap` (2026-09-26)
**id:** 83 · **category:** car · **class:** bug · **status:** in-flight fix-car-rail-widget-tap

- **Reported** ℹ: while navigating, switching to another car app keeps the turn hint in the rail widget, but tapping it does nothing — the app never comes back to the car foreground (music players do). Phone + head unit under Android Auto (projection).
- **Cause** ✅: the ongoing notification had only one tap target — `PendingIntent.getActivity` at `openTargetActivity()`, which returned `CarAppActivity` only on automotive hardware and `MainActivity` otherwise, while `CarAppExtender` carried no `setContentIntent` at all. Per the car-app contract the host then falls back to the notification's content intent, so on projection the tap started a phone activity on the phone and the car screen never switched.
- **Fixed** ✅: `NavigationNotificationBuilder.carExtender` now sets a car tap target, built by `NavigationNotificationService.carOpenIntent` as `CarPendingIntent.getCarApp(…, CarAppService component)` — projection: a broadcast the host answers with `startCarApp`; AAOS: the `CarAppActivity` launch (the hand-built AAOS branch is gone, one car path for both). Unit evidence: `NavigationNotificationBuilderTest` 13/0 (4 new cases: car target present and distinct, phone target unchanged, absent without a caller target), `NavigationNotificationServiceTapTargetTest` 2/0 (projection broadcast, AAOS activity via `FEATURE_AUTOMOTIVE`), full suite 3453/0 (`:app` mobile 1191, `:app` automotive 1191, `:auto` 691, `:core` 354, JNI module 26).
- **Still open on device** ⏳: no car/AAOS target was attached at implementation time (`adb devices` empty), so both on-device checks of `fix-car-rail-widget-tap` (tasks 4.2/4.3) are pending. Recipe: phone projecting to a head unit, start navigation, switch to another car app, tap the rail widget → the car screen returns to the app with guidance still running, `adb logcat -s Diag/SESSION Diag/HOST` shows the session handling the incoming intent (`action=android.intent.action.VIEW`) with no `HOST` rejection, and the phone-shade tap still opens the phone UI; then the same tap on an AAOS AVD/head unit (automotive flavor) for the migrated path. Re-close this entry when the runs are recorded in the change's tasks.

---
## 82. The native search-scope diagnostics were dropped with the branch's last diff — Found 2026-09-26 during the libosmscout merge / PR #1773 closure (`update-to-current-libosmscout-master`)
**id:** 82 · **category:** native-jni · **class:** improvement · **status:** open

- **Observed** ℹ: `naviveylin-local`'s last difference from upstream `master` was `libosmscout-client-java/src/OSMScoutClient.cpp` (+35/-7) and consisted of 13 `osmscout::log.Info()` diagnostic lines (ResolveSearchScope parent chain + expansion, the `searchLocations: scope for db has …` line, resolveAdminRegion db/bbox/service/reverse-lookup, getAdminRegionScopeName) plus a behaviour-identical restructure of the multi-database scope selection. It was dropped (submodule `a50ae3b15`, parent gitlink `4ae2c13`), so the branch now equals upstream `master` and PR #1773 was closed as superseded (empty diff).
- **Consequence for an existing task** ⏳: `fix-compound-name-matching` task 5.4 asks the on-device run to grep `adb logcat -s NaviVeylin` for the native `searchLocations: scope for db has …` line. That line no longer exists in the pinned submodule; the surviving evidence for the same decision is the Kotlin side (`MapCanvasViewModel` logs `resolveAdminRegion(...) -> handle=…` and `getAdminRegionScopeName(handle=…) -> '…'`, both through `android.util.Log`, not the `NaviVeylin` tag). Update that task's recipe before the device run, or accept the Kotlin-side logs as the evidence.
- **Fix candidate if the detail is wanted again** ℹ: add the per-database scope lines as `osmscout::log.Debug()` (gated, so a release build stays quiet) in a focused upstream PR — not by re-forking the file; the `log.Debug(true)` switch is documented in `AGENTS.md` (native logging) and the app's bridge forwards them to the `NaviVeylin` tag.
- **Related** ℹ: `TODO.md` §81 records the same pattern for the matcher test's allocation-cost case; both are "the merge resolved a file to upstream and a local diagnostic went with it".

---
## 81. The word-matching allocation-cost case did not travel upstream with the matcher work — Found 2026-09-26 during the libosmscout merge of PR #1849 (`update-to-current-libosmscout-master`)
**id:** 81 · **category:** native-jni · **class:** improvement · **status:** open

- **Observed** ℹ: PR #1849 merged our `fix-compound-name-matching` matcher work into upstream `master` (`aaa437a16`, commits `7a036fa51` "feat: match query words across separators in names" + `197a5c05c` "fix: make the new matcher tests portable and non-duplicated"). Upstream's `Tests/src/StringMatcherTest.cpp` carries the same case set as our `naviveylin-local` copy, but **not** the allocation-cost case and its counter harness: our copy is a 331-line fork of upstream's 189-line file, and the only content unique to ours is `TEST_CASE("A substring hit does not split the candidate into words")` plus the `STRINGMATCHER_HAVE_ALLOCATION_COUNTER` block that replaces global `operator new`/`delete` to count allocations (so a substring hit must not pay for splitting the candidate into words).
- **Why it is listed** ℹ: the merge resolved that file to upstream to stop it conflicting on every update (`git diff origin/master` for the file is now empty, and the branch's residual dropped from 14 files / +1042 -18 to 1 file / +35 -7). The cost claim is therefore no longer pinned anywhere: `git grep CountAllocations origin/master -- Tests/` returns nothing.
- **Preserved** ✅ at submodule commit `eb0ac0ff2` ("tests: measure the StringMatcher cost claim only where it is measurable"), reachable in the submodule's history; restore locally with `git -C app/src/main/cpp/libosmscout checkout eb0ac0ff2 -- Tests/src/StringMatcherTest.cpp`. The harness needs care in CI (the local version already had `SKIP` paths for sanitizer runtimes and shared-library/DLL builds, which is the likely reason it was not part of the PR).
- **Fix candidate**: re-add it as a focused upstream PR on top of upstream's file (the counter harness + one case), so the cost claim is pinned where the matcher now lives; do not re-fork the whole test file.
- **Adjacent state from the same merge** ℹ: upstream **deleted** `origin/fix-compound-name-matching` in the submodule after merging it, and the submodule's `openspec/changes/fix-compound-name-matching/` artifacts are now upstream's copies (our local stubs were replaced). The parent repo's own copy of that change (16/20, device checks 5.2/5.4-5.6 pending) remains the tracker; when it archives, it must not resurrect the submodule stubs.

---

## 77. Words joined without a separator stay unmatched (`Bahnhof Straße` vs `Bahnhofstraße`) — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, boundary of the word rule)
**id:** 77 · **category:** search · **class:** improvement · **status:** open

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
**id:** 76 · **category:** search · **class:** improvement · **status:** open

- **Observed** ℹ: `TextSearchIndex` is a MARISA trie whose keys are complete normalized names, looked up with
  `predictive_search` (a prefix search), so a query matching a word in the *middle* of a name only works through
  the structured search, which needs a region or a default admin region. `Heinz` finds "Heinz-Hilpert-Theater"
  through the text index; `Theater` alone cannot.
- **Fix candidate**: token-keyed text index entries (one key per word plus the object reference) — an import-side
  change, so every installed map has to be re-imported server-side and re-downloaded before users benefit; the
  query side would then intersect the per-word candidate sets instead of prefix-matching one name.

---

## 75. Android Auto search passes no default admin region, so POI search needs the city in the query — Found 2026-09-25 during `fix-compound-name-matching` (out of scope, own change)
**id:** 75 · **category:** search · **class:** bug · **status:** in-flight fix-car-search-default-admin-region

- **Observed** ℹ: `provideAutoSearchProvider` calls `searchLocations(query, limit, OSMScoutClient.NO_ADMIN_REGION)`,
  and the native structured search visits POIs only inside an admin region matched from the query tokens
  (`LocationService::SearchForLocationByString`). A car query naming only a POI (`Hilpert Theater`, without
  `Lünen`) therefore finds nothing, while the phone searches inside the GPS-derived default region
  (spec: `location-search` — "Search scoped by current admin region").
- **Fix candidate**: give the car search the same default-region treatment (resolve the region from the last car
  GPS fix or the map viewport center), or add a bounded region-less POI pass; both need a cost check against the
  300 ms search debounce, since a region-less pass walks the POI index of the whole database.
- **Fix in flight** ⏳ (2026-10-01): `fix-car-search-default-admin-region` resolves the region for the car search
  through one shared rule (`core/src/main/java/com/naviveylin/core/search/SearchRegionScope.kt`) that the phone's
  `MapCanvasViewModel.searchAdminRegionHandleForFix` now delegates to as well, and passes that handle from
  `AutoServiceModule.provideAutoSearchProvider` instead of `NO_ADMIN_REGION` (car adapter
  `app/src/main/java/com/naviveylin/di/CarSearchRegionSource.kt`). Design decision: the region comes from the
  position fix only, never the map viewport center, so the car scopes exactly like the phone — which leaves
  §34 (car search from the root/history screens has no distance reference) open as the sibling change that would
  introduce the shared car reference the viewport-center fallback needs. This entry is removed when that change
  is archived.
- **Verification** ⏳: unit level green and recorded in the change's `design.md` — shared rule 14/0 with a
  revert-check, the phone's 20 region cases green **unmodified**, car wiring 6/0 with a revert-check, both flavors
  1462/0/0, `:auto` 713/0/0, both debug APKs with all three ABIs. The on-device halves (a car query naming a POI
  without its region, the phone regression) are device-gated: no device was attached on 2026-10-01 (`adb devices`
  empty), and the POI-only positive case additionally waits on the §91 data gap in the installed map sets.
  Recipe with that caveat: `guidelines/Build.md` §10 ("Car search scoped by the driver's region").

---

## 74. The debug app ANRs on a cold emulator start with no map data, and the ANR trace is unreadable without root — Found 2026-09-24 during `compass-day-night-palette` task 7.3 (verification blocker, unverified)
**id:** 74 · **category:** ui · **class:** bug · **status:** open (device)

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
**id:** 73 · **category:** ui · **class:** improvement · **status:** on-hold dark-presentation direction for the two overlays (dim vs lighten; and whether the accuracy ring branches at all)

- **Loop verdict** ⏳ (bug-fix loop 2026-10-09, `bugfix-loop` iteration 8 — reclassified `bug` → `improvement`, not
  eligible: gate condition 3 fails): the literals are real (`MiniMap.kt:91` `Color(0xFF1A73E8)` — literally
  `ui/theme/Color.kt:6 PrimaryLight`; `LocationMarkerOverlay.kt:239-240` `0x1A4A90D9`/`0x664A90D9`; §73's line
  numbers are stale by two), but the **authorities conflict on the fix's direction**:
  `openspec/specs/dark-mode/spec.md:64` ("a control that carries status through a fixed hue family … SHALL dim in
  dark presentation by using a **dark tone** of its own hue family") covers neither a position dot nor a
  translucent halo, while the sibling convention `guidelines/UI.md:785-796` (vehicle marker) goes the *other* way
  ("**lighter** core `#BBDEFB` → `#1E88E5`") and ends "The accuracy circle **is untouched**". For the accuracy
  ring even the *existence* of a branch is a new requirement: `gps-location-marker/spec.md:17-19` mandates a
  presentation palette only for the marker's casing and core gradient, its accuracy bullet (`:13`) has none, and
  `mini-map/spec.md:89` says only "distinct style (e.g. a blue dot…)". Decisions needed: (a) does the accuracy
  circle get a branch at all, given `UI.md:795-796`; (b) direction for both overlays — dark tone of the same hue
  (`dark-mode` rule) or the marker convention's lighter tone for dark land; (c) the dark value for the mini-map dot
  (no free theme role: `primary`/`secondary`/`tertiary` already mean object/additional/selected marker,
  `MiniMap.kt:88-90`). Not the flag source — `NaviVeylinTheme(darkTheme = darkPresentation)` (`MainActivity.kt:113`)
  and the mini-map's `STYLE_FLAG_DAYLIGHT` push resolve to the same value, and `isSystemInDarkTheme()` is already
  ruled out (`CompassButton.kt:84-86`). Host seam exists for later (`CompassNeedleStrokeTest` rasterizes a
  production Canvas under Robolectric `@GraphicsMode(NATIVE)` and prints drawn pixels into the XML `system-out`).

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
**id:** 72 · **category:** ui · **class:** bug · **status:** fixed-by `fix-compass-needle-stroke-density`

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
**id:** 71 · **category:** map-rendering · **class:** improvement · **status:** open (device)

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
  because the skill file lives in `.pi/skills/` (gitignored). **Fixed 2026-10-05 (text/config pass)**:
  `guidelines/Build.md` §3 now prints the flavor tasks (`:app:testMobileDebugUnitTest` /
  `:app:testAutomotiveDebugUnitTest`) — the two rows naming the nonexistent `:app:testDebugUnitTest` are gone.
  The other three findings of this entry stay open.
- **Before this change no test could obtain a rendered front buffer** ℹ: every ViewModel test was state-level because
  `initMap` + `advanceUntilIdle` never produced an emitted frame (`renderViewport` stayed null; not investigated
  further). The change added the `MapCanvasViewModel.publishRenderedFrameForTest` hook (the same path the frame
  collector uses) so the walk is deterministically testable; a real-render harness for the ViewModel is still worth
  having for render-pipeline behaviour (why no frame is emitted is unexplained).

---

## 70. BROWSE still mixes the free-driving anchor preset into a street-pill placement, and an off-screen vehicle has no cue but the re-center button — Found 2026-09-22 during `fix-browse-recenter-visibility` (out of scope)
**id:** 70 · **category:** ui · **class:** bug · **status:** open

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

## 66. `hostbuild`'s JNI target is configured against a JDK that is not installed — Found 2026-09-21 during `fix-native-database-open-race` task 1.1 (harness gap)
**id:** 66 · **category:** build-and-harness · **class:** improvement · **status:** open

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
**id:** 65 · **category:** map-rendering · **class:** bug · **status:** open (device)

- **Observed** ℹ: on the AAOS AVD (`emulator-5556`, automotive debug, navigation active with a fix every 2 s for 10.5 minutes) `dumpsys meminfo` reports the app `TOTAL 227 MB -> 278 MB` (native heap `133 -> 187 MB`) and the templates host `TOTAL 299 -> 343 MB` (native heap `162 -> 215 MB`). No pressure symptoms in the same window: 0 `lmkd`/lowmemorykiller lines, 0 host crashes, 0 app fatals, 0 surface failures, 294 full renders.
- **Why it is not attributed to the change** ℹ: the change only *reduces* per-rebuild work (the lane-guidance bitmap is reused while its state is unchanged, the template distance comparisons are bucketed); it adds no buffer, cache or thread. The host-side growth is on Google's side of the IPC.
- **Not measured** ✗: the samples are single snapshots before/after the drive, the vehicle walked past the destination (so routes/tiles across a wide area were loaded), and the car path now runs on the configured car cache (128 tiles per database, `core/NativeTileDataCache.kt`) instead of the library default of 25. A leak therefore cannot be separated from legitimate tile/cache growth from these numbers.
- **Measured 2026-09-27** ✅ (phone, scripted walk, no driving — `guidelines/Build.md` §10): the *app* process' native heap went **115 MB → 337 MB** and `TOTAL PSS` **377 MB → 600 MB** over a wide-area walk, **saturating** under continued panning (7 samples flat to ±0.4 MB) and **retaining 324 MB** after returning to the start viewport, while `Bitmap (malloced)` stayed flat at 62 MB. So the growth this entry went looking for is on the native **tile-data retention** side, not the bitmap cache and not the per-render transients; it is now owned by `bound-tile-data-retention` (release path + the ceiling requirement). Still owed there: the same walk on a build with a different `PHONE_TILES` (512 vs 128 vs the library default 25), which is the only way to separate the cache's share from the allocator's retention of freed pages.
- **Next step**: repeat with a *stationary* session and a *repeat* of the identical route, sampling `dumpsys meminfo` every minute, so growth without new tiles can be told from cache fill. If the app side still grows, the candidates in §49 (per-render transient buffers) and the tile-cache capacity (configured per surface) are the first places to look.
- **Inherited from §63 (closed 2026-09-27)** ⏳: the `CAR_TILES = 128` value is reasoned, not measured — 5.1× the library default and 4× below the phone, chosen because no car/AAOS device or head unit was attached. Follow-up on the AAOS AVD or a head unit (`guidelines/Build.md` §10): enable `osmscout::log.Debug(true)`, confirm `adb logcat -s NaviVeylin` shows `[JNI] setNativeDataCacheSize(128)` and the per-render `applied tile data cache size 128 to N db(s)`, then compare `dumpsys meminfo <pkg>` native heap against the library-default run; re-tune by changing the one constant.
- **Partially addressed 2026-09-21** ✅: the per-screen overrun buffer is now released on every screen stop (`fix-car-surface-ownership-and-host-callbacks`), so the car stack no longer retains up to four extra ~3.7–8 MB buffers while its screens sit stopped. §49 (per-render transient buffers) stays open, and the stationary-route measurement above is still the next step.

## 64. The car template rebuild rate is still bounded by the arrival estimate, not by the distance buckets — Found 2026-09-21 during `fix-host-crash-residual-paths` (task 4.2/4.4)
**id:** 64 · **category:** car · **class:** improvement · **status:** open (device)

- **Observation** ℹ: the change buckets the template rebuild to the *displayed* distance (both the distance-to-turn and the remaining route distance use the host's own rounding — 50 m steps below 1 km, 100 m above) and reuses the lane image while the lane state is unchanged, but `hasStateChanged` still compares `etaMillis / 1000`, and the native engine re-emits the arrival estimate on every position update in practice. The residual template rate is therefore the estimate's update rate, i.e. close to the position rate (~1/s while driving), not the distance-bucket rate. The lane-image allocation and its IPC payload per rebuild **are** gone.
- **Why not fixed here** ✗: bucketing the arrival estimate to the displayed minute (what the ETA card shows) would make the host's own countdown authoritative for up to a minute. Whether the host ticks that countdown itself is not known — the change's spec deliberately leaves the arrival estimate out ("a shifted estimate is displayed content") — so this needs a device measurement first: run navigation with the estimate bucketed to the minute and watch the ETA card for a frozen countdown (rail-widget card and the navigation template both).
- **Fix candidate**: `hasStateChanged` compares `etaMillis / 60_000` instead of `/ 1000`; measure `Diag/HOST`-correlated template refreshes per minute before/after and watch the card. If the host does not tick, keep the per-second comparison and consider publishing the remaining time only on manoeuvre/step changes instead.

---

## 46. Phone notification `Stop` action does not end navigation — FIXED by `background-navigation-notification` and closed by `one-navigation-engine` (2026-09-27)
**id:** 46 · **category:** car · **class:** improvement · **status:** open (device)

- **Closed 2026-09-27 by `one-navigation-engine`** ✅: the whole class of defect is gone, not just its symptom. `NavigationStateProvider` and `NavigationStopRequests` are deleted; the process-scoped `NavigationEngine` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`) is the single navigation source every surface observes, so the shade's stop action reaches the one session directly (`NavigationNotificationService.handleAction` → `engine.stopNavigation()`). There is no callback slot to overwrite, no per-source state mirror and no second engine to mis-route to. The phone surface keeps clearing the route panel and the drawn route on every stop path through its adapter (`NavigationViewModel.setRoutePanelViewModel`). Regression tests: `NavigationEngineStopPathTest` (stop clears the panel, notification stop ends navigation/clears the view/leaves nothing for the notification to keep alive, follow released), `NavigationNotificationServiceActionTest` (only the stop-navigation action stops the engine), `NavigationEngineTwoSurfaceTest` (stop from either surface ends it for both). Evidence: `./gradlew test` green — mobile 1255 / automotive 1255 / `:auto` 697 / `:core` 369 / `:osmscout-client-java` 26, 0 failures.
- **Superseded history (kept for the record)** — the fix `background-navigation-notification` task 6.1 shipped on 2026-09-20: (`core/src/main/java/com/naviveylin/core/NavigationStopRequests.kt`), implemented by `NavigationStateProvider`: `stopNavigation()` broadcasts instead of routing to one callback slot, and the phone `NavigationViewModel` + `AANavigationController` each collect it and stop their own navigation (idempotent). The provider's mirror became a per-source registry where the navigating source wins — an idle car registrant can no longer blank live navigation, and `ViewModel.onCleared` unregisters the dead phone surface. `NavigationViewModel.stopNavigation()` also clears the route panel (`setNavigating(false)` + `clearRouteFromMap()`) so the shade stop matches the in-app button, and the service logs `onStartCommand action=…`. New tests: `NavigationStateProviderTest` (5), `NavigationViewModelStopPathTest` (2), `NavigationNotificationServiceActionTest` (2). Evidence: mobile 1068 / automotive 1068 / core 302 / auto 516 tests, 0 failures; both debug APKs assemble; no new compiler warnings.
- **Still open on device** ⏳: tap `Stop` in the phone shade while navigating **with a car session live in the same process** (the original failure scenario) — confirm navigation ends, the notification withdraws and the route panel is left non-navigating. That re-run also re-opens `background-navigation-notification` task 4.1 and unblocks `car-turn-by-turn-rail-widget` task 7.4.
- **Residual, not fixed (same last-wins shape)** ⏳: `NavigationStateProvider.navigateTo` / `reportError` still route to the *last* registrant. Both controllers can route with the same JNI client, so a mis-routed call is not lost (unlike the stop command), which is why it was out of scope here. Fix candidate: reuse the new registry — route `navigateTo` to the navigating source, else the first registered one — or give `navigateTo` its own broadcast seam if double-routing ever becomes a risk.
- **Stale premise (2026-10-03, `cleanup-todo` pass)** ℹ: the files this entry's history and this residual cite
  no longer exist — `navigation/NavigationStateProvider.kt`, `navigation/AANavigationController.kt` and
  `core/NavigationStopRequests.kt` were deleted by the archived change `one-navigation-engine`, which also
  removed the last-wins routing the residual describes. The bullet above is kept for the record, not as work;
  what stays open here is the on-device re-run (shade `Stop` with a live car session), and the class was
  lowered from `bug` to `improvement` for that reason.
- **Original finding 2026-09-20 on device during `background-navigation-notification` task 4.1 (phone, ongoing notification visible)** ✗: tapping `Stop` in the notification shade does not end navigation — guidance keeps running and the notification stays. The same path is gated for the car hint by `car-turn-by-turn-rail-widget` task 7.4 ("…the phone stop action still works"), so that task is blocked on this. Two causes are visible without a device:
  - (a) **`NavigationStateProvider` keeps ONE callback slot per command** (`app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt:32-46` — `stopCallback`, `navigateToCallback`, `reportErrorCallback`), overwritten by every `observe(source)` call. Two sources register in one process: the phone `NavigationViewModel.init` (`app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt:64`) and the singleton `AANavigationController.init` (`app/src/main/java/com/naviveylin/navigation/AANavigationController.kt:85`). The latter is resolved on **every car-session warmup** (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:328`, log step "Activating navigation controller"), so as soon as a head-unit/AAOS session starts after the phone app it owns `stopCallback` and the phone notification's action stops only the car controller's own (idle) engine. The same clobbering affects the mirrored `state`: the car controller's initial empty `NavigationState` is written into the provider when it subscribes, which can trip the service's active gate (`app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt:105-113`) and `stopSelf()` the phone notification mid-navigation.
  - (b) **Not at parity with the in-app stop**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:1991-1993` performs `navigationViewModel.stopNavigation()` **plus** `routePanelViewModel.setNavigating(false)` and `clearRouteFromMap()`; the notification path (`NavigationNotificationService.kt:69-79`) performs only `stateProvider.stopNavigation()` — the route panel keeps its navigating flag and the route stays drawn on the map.
  - **No diagnostics, no test**: the `ACTION_STOP_NAVIGATION` branch logs nothing, and nothing covers `onStartCommand` or the provider's callback routing (`NavigationNotificationBuilderTest` only asserts the `stopIntent` shape), so logcat cannot separate "intent never delivered" from "wrong target".
  - Fix candidates (both taken 2026-09-20, see the first bullet): route the stop to the source that is actually navigating (the provider now keeps a per-source registry where the navigating source wins the mirror and the stop broadcasts), put the shared stop in one place so the route-panel cleanup cannot be skipped, log the incoming action in `onStartCommand`, and cover the provider routing + service action with tests.

## 34. Car search opened from the root/history screens has no distance reference
**id:** 34 · **category:** car · **class:** improvement · **status:** open

- **Noticed 2026-09-19 while implementing `search-result-ranking`** ℹ: the spec's distance reference for the car is "last known GPS fix, else the current car map viewport center, else none (order by tier and quality only)". Only `MapScreen` can supply that center (it owns the renderer gate, so it passes `{ rendererGate.renderer.value?.markerViewport() }`); `RootScreen` and `SearchHistoryScreen` push `SearchScreen` without any map, so their results are ordered by tier and quality and show **no distance at all** — spec-legal, but a driver comparing results from the root list has no proximity signal. Fix candidate: publish the last rendered viewport center through a small shared provider (e.g. alongside `AutoLocationProvider`, updated by `NavigationSession`/`AutoMapRenderer` whenever the displayed center changes) and pass it from all three `SearchScreen` call sites. Not a defect of this change — the "neither exists" case is specified and covered by tests.

## 29. AA follow jumping — open points handed over (2026-09-17, stable-version handover)
**id:** 29 · **category:** car · **class:** improvement · **status:** open (device)

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
**id:** 19 · **category:** map-rendering · **class:** bug · **status:** open

Findings detected while fixing the AA follow overlay projection; all out of that change's scope
(it deliberately keeps the follow re-anchor cadence and the overlay frame bookkeeping only).

**Landed 2026-10-03 — change `fix-car-follow-reengage-render`** ✅: items (a) and (c) above are fixed
for the car surface (specs `auto-smooth-follow` — A follow re-engage requests the frame it
re-anchored; `auto-diagnostics` — Diagnostic numbers are locale-independent). Evidence: a test-visible
`renderRequestCount` plus `AutoMapRendererTest` 75/0 (3 new cases: schedule/draw the re-anchored frame,
blit-eligibility after a rotation commit, locale-independence), `AutoMapRendererRenderCadenceTest` 5/0
(one frame per commit path), two revert-checks (guard removed → the bare-re-engage case fails; default
locale restored → the German-locale case fails). **This entry stays open** for the residue:

- **The phone mirrors of the locale item** ⏳: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:720-728`
  (the follow log this car entry mirrors) and `MapCanvasViewModel.kt:1157-1159` (the GPS-fix log) still
  format through the default locale. Same rule, two sites — a phone-surface change.
- **§19(b) stays a measurement** ⏳: the extrapolation loop's ~11 Hz (not the nominal 30 Hz) was never
  re-measured after the render-cadence work; see the item above.
- **On-device verification of the fix is device-gated** ⏳: the pan-release re-anchor and the
  German-locale `follow` line could not be observed on 2026-10-03 (`adb devices` empty,
  `emulator -list-avds` empty). Recipe when a unit is attached: free driving with a fix stream, pan,
  release → the map re-anchors within the next tick instead of holding the panned viewport, with
  `adb logcat -s NaviVeylin | grep follow` showing the re-anchored frame and its numbers with dots
  (`off=3.4,-1.5 frameMag=15.00`).

- **✅ FIXED for the car surface (2026-10-03, `fix-car-follow-reengage-render`)** — `reengageFollow` relies on a preceding `setViewport` to have requested the render ✗: it writes `viewportLat/Lon` + `emitViewportState()` but never called `requestRender()`; it worked only because the fix path calls it immediately after `setViewport` (which does request). A caller that re-engages follow on its own — `MapPanHandler.onPanModeChanged(false)` (pan release), `NavigationScreen`, `FreeDrivingScreen` start/resume, the `RendererGate` replay — silently kept the stale frame on the surface. Now: `blitEligible = true; if (!pendingRender) requestRender()`.
- **ℹ Extrapolation loop measured ~11 Hz, not the nominal 30 Hz** (`EXTRAPOLATION_FRAME_MS = 33`): in the 104 s AA window, 38 diagnostic lines at one line per 30 ticks is ~1140 ticks / 104 s ≈ 11 Hz. Each tick locks the shared surface and draws the full 1296×720 overrun bitmap plus the overlays, so the period is dominated by the draw — measure before tuning the constant (a smaller period would not raise the rate).
- **✅ FIXED for the car `follow` entry (2026-10-03, `fix-car-follow-reengage-render`)** — diagnostics used the default locale: `"%.6f".format(...)`/`"%.1f".format(...)` printed decimal commas on a German device (`51,513637`, `off=3,4,-1,5`), so those lines were not machine-parseable. The car `follow` entry now formats every number through `Locale.ROOT` (`followDiagnosticLine`, `AutoMapRenderer.kt`). The phone mirrors are still open (see the note below).

---

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing
**id:** 17 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.
- **A stale unit-test asset APK makes asset-reading Robolectric tests lie (found 2026-09-29 while proving a new packaged-asset gate is not vacuous, change `fix-poi-symbol-icons`)** ⚠: `packageMobileDebugUnitTestForUnitTest` stayed **UP-TO-DATE** after `mergeMobileDebugAssets` produced a tree with one file removed, so `PackagedPoiIconsTest`/`AssetCopierTest` kept reading the *previous* `apk-for-local-test.ap_` — a test asserting a packaged icon passed with the icon absent from every on-disk asset tree. Two consequences: (1) a mutation that removes a file only from a `Sync` task's **output** proves nothing at all — the sync restores it, and the asset APK may not even be rebuilt; mutate the real source under `app/src/main/cpp/libosmscout/`; (2) after any asset change, clear `app/build/intermediates/apk_for_local_test/<variant>` (or run `--rerun-tasks`) before trusting an asset assertion, and when such a test disagrees with the disk, suspect the APK before the code. Fix option: wire the unit-test asset package task to the merged-assets content explicitly, or make the run-tests skill's asset-affecting recipe clear that directory. Measured the same session: `--rerun` is ignored on the aggregate `test`, and deleting `test-results/<flavor>` does **not** force execution when the build cache can restore the outputs (`:core`/`:auto` came back `FROM-CACHE` twice) — `--rerun-tasks` is the only reliable forcing move here.

---

## 16. On-device verification pending: fix-zero-distance-to-turn (native step distance)
**id:** 16 · **category:** verification · **class:** improvement · **status:** open (device)

- **⏳ Pending device run (change `fix-zero-distance-to-turn`, tasks 5.4/6.3)** ✗: the native instruction-distance fix (submodule `3c761d618` on `naviveylin-local`, gitlink bumped at main `195f278`) is implemented and unit-tested (Kotlin state handling + step-index lookup + mapper arrival step — 14 new tests green, full `./gradlew test` green, both flavors all ABIs build). The **native engine path is not verifiable on the host JVM** (the JNI stub .so is symbol-free, so `PositionAgent`/`RouteInstructionAgent`/`GenerateNextRouteInstruction` run only on-device). On next device/emulator session: boot the Automotive AVD, drive a GPX-replay or mock-GPS route with one deviation → reroute, and check via `adb logcat -s NaviVeylin` (plus a temporary `osmscout::log.Debug()` of `distanceTo`/`nodeDist`/`abscissa`): (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset-to-begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Also re-run AA/phone instruction-panel screens at the destination tail — this is where the 0 m freeze appeared.

- **2026-09-15 emulator attempt: blocked by harness, still unverified** ⏳: on a Pixel_8 AVD (GMS disabled → `LocationManager` fallback; regional NRW map; Decathlon Aplerbeck route, 7,4 km) the fix stream worked (`vel=6.69 m/s`, follow camera tracked every hop), but `geo fix` always emits `bear=0.0` — a bearing cannot be injected — so `PositionAgent` never establishes travel direction: the instruction card froze on the first turn across ~2 km and no reroute fired on a 1 km deviation, i.e. the four criteria could not be exercised. Unconfirmed finding for the real-device pass: the next-turn overlay showed **"0 m" while the route panel showed "40 m" for the *same* turn** ("Links abbiegen → Ruhrallee") — check for a distance-source divergence (overlay vs panel step distance). Needs a real drive, or a mock-GPS app using `setTestProvider` with real timestamps/velocity/bearing.

---

## 1. Route Calculation & Visualization
**id:** 1 · **category:** route-and-navigation · **class:** feature · **status:** open

| Feature | Status | Notes |
|---------|--------|-------|
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |

## 2. Turn-by-Turn Navigation
**id:** 2 · **category:** route-and-navigation · **class:** feature · **status:** open

| Feature | Status | Notes |
|---------|--------|-------|
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI |

## 3. GPX Track Import & Playback
**id:** 3 · **category:** route-and-navigation · **class:** feature · **status:** open

> libosmscout submodule (master) already has GPX import/render support (archived change `javascout-gpx-track-import-render`) and JavaScout has `TrackPlayer` — but nothing is wired into the NaviVeylin app yet.

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI — no app UI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 4. Object Description & Long-Press
**id:** 4 · **category:** ui · **class:** feature · **status:** open

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 5. UI / Shell
**id:** 5 · **category:** ui · **class:** feature · **status:** open

| Feature | Status | Notes |
|---------|--------|-------|
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| Double-tap to zoom | ✗ | Candidate feature (not in JavaScout either). No double-tap gesture exists (`map-pan-zoom` covers pan/pinch only). If added later, wire it into the smooth-zoom animation path and the continuous fractional magnification (see `continuous-pinch-zoom`). |

## 6. Rendering
**id:** 6 · **category:** map-rendering · **class:** feature · **status:** open

| Feature | Status | Notes |
|---------|--------|-------|
| Track rendering on map | ✗ | |

## 8. GPS / Position
**id:** 8 · **category:** location · **class:** feature · **status:** open

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

## 157. `viewport-persist` describes one `viewport.json` while `ViewportStorage` writes one file per map key — Found 2026-10-09 by `bugfix-loop` (iteration 6) while gating §9
**id:** 157 · **category:** persistence · **class:** bug · **status:** open

- **Observed** ℹ: the spec's storage sentence (`openspec/specs/viewport-persist/spec.md:11`) reads "The file SHALL be
  written to `filesDir/maps/viewport.json`" — a single file, no per-map key — while `ViewportStorage.fileFor`
  writes `viewport-<key>.json` per map key and every load path keys by `currentMapKey`
  (`MapCanvasViewModel.kt:2071`, `:2112`). The spec's only key sentence (`:111`, "nor persist a viewport under the
  new map key") presupposes a key, so the two statements cannot both describe the shipped storage.
- **Consequence** ⏳: a reader implementing the storage contract from the spec would build a different layout; same
  class as §118 (a spec pinned behaviour the code never applied). No user-visible defect observed — the divergence
  is between two artefacts, which is why no test caught it — but it is why §9's un-keyed case has no destination.
- **Fix candidate**: correct the spec to the per-map layout (file pattern, key derivation
  `substringAfterLast('/')` — or whatever §9's decision makes canonical — and the pre-`initMap` behaviour), then add
  the conformance case the way `fav-auto-zoom` got one (`FavAutoZoomClampRangeTest` reads the spec text and compares
  it to the code's constants; `MapMenuBackOrderComposeTest` is the precedent). Decide together with §9 so the spec
  sentence and the un-keyed behaviour land in one change.

## 9. Viewport Save — Residual Bug
**id:** 9 · **category:** persistence · **class:** improvement · **status:** on-hold un-keyed viewport semantics (skip vs named bucket vs unify)

- **Loop verdict** ⏳ (bug-fix loop 2026-10-09, `bugfix-loop` iteration 6 — reclassified `bug` → `improvement`,
  not eligible: gate conditions 3 and 4 fail): the four fallbacks disagree in text, but the stated effect is only
  half-reachable. `MapCanvasViewModel.kt:2112` sets `currentMapKey = mapPath.substringAfterLast('/')` **before**
  `:2115`/`:2071` read it, so the load-side `mapPath` fallback is unreachable — loads are always keyed by the
  basename; the saves at `:2957`/`:4415` use `"default"`, which fires only pre-init, and such a save is
  **orphaned under both literals** (`viewport-default.json` has no reader, `viewport-<full path>.json` ≠ basename),
  so the entry's candidate (share one key helper) makes the literals equal and leaves the orphan in place. No
  authority names a destination for an un-keyed save: `openspec/specs/viewport-persist/spec.md:11` says one file
  (`filesDir/maps/viewport.json`) and its only key sentence `:111` presupposes a key; `guidelines/Design.md` §9
  (537-556) says "persist on every meaningful change" and nothing about keys. Today's deliberate handling of "no
  map yet" is **skip** (`:4410` `if (!viewportRestored) { Log.d(…, "skipped, viewport restore not yet applied");
  return }`, from archived `2026-09-06-fix-viewport-save-race-residual`). Decision needed, one of: (a) skip the
  save when `currentMapKey == null` (matches the `:4410` pattern; makes the `"default"` literal removable;
  rewrites the assertion at `MapCanvasViewModelNavEndRestoreTest.kt:157`), (b) keep writing a named bucket **and**
  consult it in `initMap`, or (c) hygiene-only unify the literals without behavioural proof. Settle it in a normal
  (non-loop) change together with **§157** (the spec's single-file sentence vs the per-map files), because no
  WHEN/THEN is stateable before this decision.

- **Residual: pre-init viewport key mismatch (`mapPath` vs `"default"`) ℹ**: `MapCanvasViewModel.initMap` loads with `viewportStorage.load(currentMapKey ?: mapPath)` (`MapCanvasViewModel.kt:2095`, and the navigation-end restore at `:2194`), while `saveViewport()` (`:2932`) and the save at `:4367` persist with `currentMapKey ?: "default"`. Harmless today because `initMap` sets `currentMapKey` before the load, so the fallbacks never meet — but any future save/load before `initMap` (or after a failed init) would write and read different files. Fix candidate: share one key helper (`currentMapKey ?: mapPath`) across all four call sites. Found 2026-09-13 while triaging `MapCanvasViewModelNavEndRestoreTest` (change `smooth-decimal-auto-zoom`, task 6.2) — test-only fix there, production untouched.
- **Citations refreshed 2026-10-04 (`cleanup-todo` pass)** ℹ: the call sites above read `:1408` / `:2167` / `:2811` when the finding was written; the code has moved since, and the premise was re-verified before this refresh rather than trusted — `grep -n 'currentMapKey ?: ' app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` → `2095`, `2194` use `mapPath`, `2932`, `4367` use `"default"`. The mismatch is unchanged; the fix candidate now names **four** call sites, not three.

## 14. Kover deprecation on the Gradle 10 path
**id:** 14 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 22. Gradle 10 deprecation sweep (build-level, pre-upgrade audit)
**id:** 22 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Every build prints “Deprecated Gradle features were used in this build, making it incompatible with Gradle 10” (observed 2026-09-15 on the §17-verified `./gradlew test`)** ⏳: Gradle 9.6.1 tolerates the deprecations, Gradle 10 hard-fails; §14 already tracks Kover's Project-object notation (not fixable from our scripts). Sweep the remaining deprecations once before any Gradle 10 upgrade: `./gradlew test :app:assembleMobileDebug --warning-mode all`, triage the emitted list, and separate plugin-owned warnings (AGP/Kover — expect one each, document and ignore) from our own scripts' (`buildSrc/*.gradle.kts`, root/app/auto/core `*.gradle.kts` — fixable locally). Two adjacent, still-untried items: configuration cache (`--configuration-cache`, Gradle suggests it each build) — verify it against the license-assets Variant-API tasks and the `release` version-state bump (config-time execution) before enabling in CI; and `org.gradle.configuration-cache=true` interplay with the `release` task gating (bump runs at configuration time, which config-cache may re-run per invocation). **Answered 2026-10-05 (applying `speed-up-build-test-gate`):**
the configuration cache cannot be enabled by a flag — `--configuration-cache` on an `:app` test invocation
reports **8 problems** and stores nothing, all of them script-level `DefaultTask` registrations whose
`doLast` closures capture script objects: `:app:checkHardcodedStrings`, `:app:checkNoCoordinatesInLogs`,
`:app:generateSbomMobileDebug` (a script object and a `DefaultProject`), `:app:mergeNativeSbom`,
`:app:downloadSbomCli`, plus `:auto:`/`:core:checkHardcodedStrings`. Clearing them means moving those tasks
into `buildSrc` classes with serializable inputs. The release-state half of the suspicion is settled
the other way: `:app:checkLicensePolicy` left `app/release-version.properties` byte-identical. Recorded in
`guidelines/Build.md` §7.

## 10. Pending On-Device Verification
**id:** 10 · **category:** verification · **class:** improvement · **status:** open (device)

| Item | Status | Notes |
|------|--------|-------|
| Vehicle anchor visible-area + per-surface parity (change `anchor-per-surface-visible-area`, tasks 6.3–6.6, 8.1–8.3) | ⏳ | Implementation green (suite 2026-09-15: app mobile/automotive 964 each, auto 373, core 216; incl. `markerRidesTheBlittedContentWithABlitOffset` + `hostPaneClampsTheLeadingEdgeAnchorOnly`). On-device: routing/free-driving anchors stay clear of the overlays (turn card, routing-status card, street-name pill, widget column) at large font scale; browse framing unchanged (identity); car↔phone per-surface anchor values independent; upgraded install keeps its pre-split value until the car gets its own; `center/center` on both surfaces frames identically to the previous build; AA: leading-edge preset clears the host pane (LTR + RTL), vehicle marker + destination pin ride the blitted content without lead-then-snap during extrapolation glides. |
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |
| Bounded zoom walk on the phone (change `fix-phone-zoom-animation-parity`, tasks 6.3-6.6) | ⏳ | Implementation + unit tests green (walk arithmetic `:core` 14/14, ViewModel wiring 7/7, renderer immediate path 3/3, screen rules 7/7; full `ui.map` package suite green). On-device: enter free driving from a far browse viewport - no consecutive frame change above 0.25 levels, walk ends exactly on the speed target, render count per entry recorded; bottom-center anchor keeps the vehicle pixel fixed through zoom in/out with no correction jump at landing; a large zoom-out never exposes bare surface color; pinch/buttons/keys still request their first frame immediately; the persisted viewport holds the final target. Blocked 2026-09-24: no device/emulator attached (`adb devices` empty). |

## 15. Kover coverage attribution for Robolectric-tested classes
**id:** 15 · **category:** verification · **class:** improvement · **status:** open

- **Pre-existing tooling gap found during fix-contact-address-resolution (2026-09-13)** ℹ: in the merged and `:app` Kover XML reports, classes exercised only by Robolectric tests show near-zero instruction coverage while their tests pass and assert behaviour — `ContactsRepository` 5 covered/0 missed, `FavoriteRepository` 16/0, `AddressBookSheetKt` 41 missed/0 covered (its four Compose tests pass). Plain-JUnit-covered classes report plausible numbers. Suspected cause: Robolectric loads app classes in its own sandbox classloader, so JaCoCo/Kover exec data is recorded against a different class identity. Investigate: Kover/Robolectric instrumentation options (offline instrumentation, `kover { }` filters, or `robolectric.properties` sandbox config) before trusting any coverage gate. Until then, use the revert-check (new test fails on pre-change code) as coverage evidence instead.

## 23. libosmscout-kotlin port stubs — verify the binding is unused before anyone wires it in
**id:** 23 · **category:** native-jni · **class:** feature · **status:** on-hold (decision)

- **Submodule `app/src/main/cpp/libosmscout/libosmscout-kotlin/` carries 7 unfinished port markers (found 2026-09-15)** ℹ: `objecttypes/TypeConfig.kt` skips feature-description handling in `loadFromData` (“TODO Fetch feature”, “TODO: Add description to feature”), `registerType` has “TODO: Calculate wayTypeIdBytes & Co.” plus two “TODO: Fix” lines, and `index/AreaWayIndex.kt:120` has “TODO: Reserve capacity for offsets”. Grep across every `*.gradle*`/`CMakeLists.txt` shows **no module references `libosmscout-kotlin`** — the binding is inert today (the app uses the C++ JNI bridge + `:osmscout-client-java`; a plain-JUnit or Kotlin binding is not on any build path). The submodule is our own fork (`naviveylin-local`), so this is decision material, not urgent: either finish the port to match C++/Java behavior (TypeConfig without feature descriptions would render/query differently), or document the binding as deliberately unbuilt and add a code comment so a future dependency addition fails loudly instead of silently using a stub.

## 24. Sharp-s uppercase form (ẞ U+1E9E) is not folded
**id:** 24 · **category:** search · **class:** improvement · **status:** open

- **Known limitation left in place by `fix-sharp-s-transliteration-match` (2026-09-16)** ℹ: the character map row for U+1E9E (capital sharp S) transliterates to itself, so the case-normalized transliterated comparison that change introduced still cannot match a query spelling a name with `ẞ` against an index name spelled `SS`/`ss` (nor the reverse). No name in the NRW or Iceland map databases uses U+1E9E (verified by extracting `location.idx` and counting spellings: `straße` 65 848×, `strasse` 22×, zero `ẞ`), so nothing is unfindable today. Fix candidate: an upstream character-map row that transliterates `ẞ` to `SS` (or `ss`, now equivalent because the comparison is case-normalized) when the table is regenerated. Land upstream when the table is next touched.

## 27. Unconstrained search of a second loaded database returns out-of-area noise
**id:** 27 · **category:** search · **class:** bug · **status:** in-flight fix-cross-database-search-scope

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 6.5** ℹ: with the map view centered on Iceland and the GPS scope resolved to Regierungsbezirk Arnsberg, the query `Am Birkenbaum 6 Dortmund` returned Iceland-database entries (`Leiðhamrar Dofri`, `Lokinhamrar`, 5,8 km) instead of the Dortmund address. Cause is the documented per-database scope rule: a region handle is database-local, so the database that does *not* own the handle is searched unconstrained (`OSMScoutClient.cpp`, string-search scope comment) and its free-text index answers on short partial tokens ("am" inside "…hamrar…"). Not created by this change — the characters in the returned names (`ð`, `ö`, `í`, `æ`, `á`) are not affected by the transliteration fix, and no pre-change baseline run was made. Investigate: when a scope exists for one database, either skip the other databases' free-text hits or rank them below scoped results (and/or apply a distance limit), so an address query cannot be answered from another map region's data.
- **Fix in flight** ⏳ (2026-10-01): `fix-cross-database-search-scope` takes the recorded mechanism — the text index is scope-blind in **every** database (`OSMScoutClient.cpp:4057` passes no region), so a free-text hit outside the resolved scope's extent is now dropped before the per-source cap, and every structured result carries whether it lies inside the scope (new `LocationEntry.inSearchScope`, spec `osmscout-jni`) so the app's ranker places out-of-scope close matches below in-scope ones (`SearchResultRanker`, spec `search-result-ranking`). Databases keep being searched, so a fully qualified query for another installed map still resolves (spec `location-search`). This entry is removed when that change is archived; the residual test gap is §109 and the candidate-budget question is §110.

## 35. On-device re-check of the area-favorites and POI-search fit zooms
**id:** 35 · **category:** verification · **class:** improvement · **status:** open (device)

- **Created 2026-09-18 by `route-overview-fit`** ℹ: that change corrected `computeAreaZoom`'s ground resolution (display DPI + Mercator `cos(lat)`), which *intentionally* changes the zoom the phone picks when selecting an area favorite and when the POI search fits its results — both now zoom out to the geometrically correct level (previously over-zoomed by `dpi / 96 * (1 / cos(lat))`, ≈2 levels on a 420-dpi phone). Unit tests stay green, but no on-device/visual check of those two flows is part of that change. Follow-up: pick an area favorite and run a POI radius search on a real phone/emulator and confirm the framing is sane (not too far out, markers inside the visible area).

## 36. `public-transport` stylesheet draws no route  — ⏸ ON HOLD (owner decision 2026-09-20)
**id:** 36 · **category:** stylesheets · **class:** bug · **status:** on-hold (owner decision)

- **ON HOLD 2026-09-20 — deliberately not the next change.** Re-investigated 2026-09-20 and the blast radius is far larger than this entry first recorded: **5 of the 8 user-selectable styles cannot draw the active route at all**, not one. See the scope block below; the fix is understood and small, but it waits on option A/B/C being chosen.
- **Corrected scope (verified 2026-09-20).** `BundledMapStyles.USER_SELECTABLE` (`core/src/main/java/com/naviveylin/core/BundledMapStyles.kt:38`) is the 8 styles `boundaries, coastlines, cycle, motorways, public-transport, railways, standard, winter-sports`. `stylesheets/include/route.oss` is the ONLY definition site of `[TYPE _route]` (:56-57), `[TYPE _track]` (:62), `[TYPE _route_start]`/`_route_end` (:68-69), `[TYPE _favorite]`/`_search_selected` (:76-77) and of `SYMBOL route_start`/`route_end`/`favorite_marker`/`search_marker` (:26-46). It is included by `standard.oss`, `cycle.oss` and `winter-sports.oss` only — so the other five (`public-transport`, `railways`, `motorways`, `boundaries`, `coastlines`) draw no route line, no start/end pins, no GPX track, no favourite markers and no selected-search marker. `public-transport.oss` is the tell: it declares `GROUP _route` in `ORDER WAYS` (:4) but never wires the rule. No app-side escape hatch: one stylesheet is loaded (`MapCanvasViewModel.applyStyleSheet` → `loadStyleSheet`) and route/POI drawing goes through `MapRenderer.kt:714 renderWithRouteAndPois` with no per-render style override.
- **Three requirements are contradicted as written:** `route-panel-ui` scenario "Route polyline rendered on map" (unconditional: route polyline SHALL be rendered AND `_route_start`/`_route_end` markers SHALL appear), `fav-markers` ("SHALL render every favorite location … via `renderWithRouteAndPois`", and "the existing `_favorite` synthetic node type already defined in the libosmscout stylesheet" — defined only in `include/route.oss`), and `route-appearance` Req "Every map style draws the route with the shared route colors" + scenario "Non-default styles have a route casing" (that change fixed `cycle`/`winter-sports` and left these five behind).
- **Open decision (A/B/C):** **(A)** add `MODULE "include/route"` to all five — minimal, satisfies the three requirements, specialty styles keep their character because `route.oss` adds only route/track/marker rules and no ORDER sections (recommended at the time); **(B)** drop the partial styles from the picker — needs a `map-styles` spec change, users lose those styles; **(C)** make route/marker drawing style-independent — larger, changes the render contract.
- **Fix shape if A:** five `MODULE` lines in the submodule `stylesheets/*.oss` (upstreamable) + submodule commit on `naviveylin-local` + gitlink bump in the main repo + an app-side guard test asserting every `USER_SELECTABLE` stylesheet transitively includes `include/route.oss` (precedent: `StylesheetHexColorCaseTest`, `AssetCopierTest`). Note `ORDER` sections are optional — `coastlines.oss` draws with none, and `_favorite`/`_track`/`_route_start` appear in no `ORDER` group anywhere yet render in `standard`/`cycle`/`winter-sports` — so the `MODULE` line is the load-bearing part.
- **Original note (2026-09-19, `map-marker-route-contrast`), now superseded by the scope block above:** `stylesheets/public-transport.oss` declares `GROUP _route` in its `ORDER WAYS` block but has no `[TYPE _route]` rule, so an active route is not drawn at all while that style is selected (it is user-selectable via `BundledMapStyles.USER_SELECTABLE`). The route rule lives in `stylesheets/include/route.oss`; that change made `cycle.oss` include it like `standard.oss`/`winter-sports.oss`, and left `public-transport` untouched because the missing rule is a pre-existing gap, not one of the reported appearance defects. Fix candidate: add `MODULE "include/route"` to its MODULE block (same pattern) and verify on-device that a route then appears in that style.

## 37. Pre-existing Kotlin deprecation warning in the marker overlay
**id:** 37 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed 2026-09-19 during `map-marker-route-contrast` task 4.1** ℹ: every `:app` build prints `w: .../ui/map/LocationMarkerOverlay.kt:167:14 'fun quadraticBezierTo(x1, y1, x2, y2)' is deprecated. Use quadraticTo() for consistency with cubicTo()`. Pre-existing — that change only replaced the gradient color selection in the same function. Fix: rename the call (behavior-identical) in a build-hygiene change, or wait until the Compose version makes it an error.

## 44. Pre-existing Kotlin build warnings beyond the marker overlay
**id:** 44 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Found 2026-09-20 during `fix-favorite-store-write-race` (full-suite build)** ℹ: `./gradlew test --continue --rerun-tasks` prints 66 Kotlin warnings, none of them from that change's files. Beyond `LocationMarkerOverlay.kt:167` (already tracked as §37), three classes are untracked: (a) `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:35` — "This annotation is currently applied to the value parameter only, but in the future it will also be applied to field" (annotation-target migration, a hard change in a future Kotlin); (b) `app/src/test/java/com/naviveylin/data/AmbientLightMonitorTest.kt:35` — Robolectric's `ShadowSensorManager.addSensor` is deprecated in Java; (c) the `ExperimentalCoroutinesApi` opt-in warnings spread over ~13 test files (`MapCanvasViewModelAutoZoomCommitTest`, `MapCanvasViewModelRoadInfoTest`, `MapCanvasViewModelSingleFollowCenterTest`, `RoutePanelViewModelSearchRankingTest`, ...). The archiving guidance requires a warning-free build, so this is build-hygiene debt rather than a defect. Fix candidate: one build-hygiene change that adds the missing `@OptIn` annotations, replaces the deprecated shadow call, and sets the annotation target explicitly.
- **Recount 2026-10-03 (four-module forced run during `fix-area-fit-zoom-rounding`)** ℹ: **106** warnings, all of the same debt, and none from a file that change touched. Additions since the 2026-09-20 count: `app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:118` — `Java type mismatch: inferred type is 'String?', but 'String' was expected` (arrived with `fix-start-map-selection`, still in flight); `service/NavigationNotificationServiceTapTargetTest.kt:40,69` — the deprecated `isBroadcastIntent`/`isActivityIntent` shadows; and in `:auto`: `RendererTestRule.kt:64` ("Type 'AutoMapRenderer' is final, so the value of the type parameter is predetermined"), `RenderLoopSupervisorTest.kt:40` ("Condition is always 'true'"), `TestCarContext.kt:23` (unchecked cast), `CarStyleLoadNotifierTest.kt:88` (deprecated `field defaults: Int`), plus further `ExperimentalCoroutinesApi` opt-ins (`DetailsScreenTest`, `FavoritesScreenTest`, `MapScreenTest`, `SearchScreenTest`, `TemplateFaultIsolationTest`). Recount rather than trust this list: `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :core:testDebugUnitTest :auto:testDebugUnitTest --rerun-tasks 2>&1 | grep -c '^w: '`.

## 118. `fav-auto-zoom`'s clamp scenario says 4–18 while the code clamps area-favorite fits to 14–20
**id:** 118 · **category:** favorites · **class:** bug · **status:** fixed-by `fix-fav-auto-zoom-clamp-range`

- **Found 2026-10-03 during `fix-area-fit-zoom-rounding`** ℹ: the spec's scenario "Magnitude clamped to valid range" says "WHEN computed magnification is outside valid range (4–18) THEN magnification is clamped to the nearest valid value", but `computeAreaZoom` clamps `coerceIn(minZoom, MAX_MAG)` with `maxZoom = MAX_MAG = 20.0` and, for the area-favorite caller, `minZoom = MIN_AREA_ZOOM = 14.0` (`MapCanvasViewModel.kt`, the constant's own comment: "Minimum zoom level for area-type favorites (prevents too-zoomed-out view)"). So the enforced range is **14–20**, not 4–18 — the spec's numbers are stale on both ends (`MIN_MAG`/`GESTURE_MIN_MAG` are 4.0, and `MAX_MAG` is 20.0). Not fixed in that change because its spec delta had to keep the existing requirement block whole, and the change's `design.md` records it as an Open Question. Fix candidate: one spec-only change that states the range the code actually enforces (and, if 4–18 was ever the intent, the corresponding code change) — or split the clamp scenario per caller, since the floor is caller-specific (area favorites 14, route overview and the POI fit 4).

## 39. Real-life and Android Auto verification for the marker/route colors
**id:** 39 · **category:** ui · **class:** improvement · **status:** open (device)

- **Created 2026-09-19 by `map-marker-route-contrast`** ⏳: the visual checks (daylight route over a primary road and a white residential road, GPX track and search marker alongside, dark-presentation route unchanged, lighter dark marker without a white ring, 38 dp marker size, map-style switch to `cycle`/`winter-sports`) were performed on the phone emulator and reported working, but the user noted the concrete colors still need real-life validation, and the Android Auto half (host day/night, route/marker parity) was accepted by assumption instead of being measured on a head unit or the `Automotive_Distant_Display_with_Google_Play` AVD. Follow-up: check the violet route and the lighter dark marker on a real display outdoors and in a dark car interior, and run the AA day/night comparison once on the automotive AVD or a head unit. If the violet reads too close to the magenta search marker or the blue GPX track in practice, the hue can be adjusted in `stylesheets/include/route.oss` alone (the spec pins the contrast contract, not the hex).

---

## 40. Guardrails extracted from `ki_processing_failures.log` (2026-09-19)
**id:** 40 · **category:** specs-and-process · **class:** improvement · **status:** open

Every entry of that log was processed on 2026-09-19 and removed; each action below is what prevents
re-making the mistake. **Discharged 2026-10-05 in full:** every item names the artifact that carries the
rule (`guidelines/Build.md`, `guidelines/Design.md`, `guidelines/UI.md`, `guidelines/MapRendering.md`,
`AGENTS.md`, a CI step or a skill), the `[open]` marker is gone, and the docs tag the rule with its item
number (`§40.N`) so the mapping stays auditable. This section is a pointer list now, not a to-do. The five
items that only name an existing `TODO.md` section (§17, §14/§15, §6, §8) were already tracked there and are
kept so the guardrail is not lost.

Re-run the extraction with the `.pi/skills/process-failure-log` skill (gitignored, like the other
`.pi` skills — copy to `~/.pi/agent/skills/` for cross-project use).

### A. Harness / shell / Gradle invocations

1. Do not plan on the allowlist blocking a tool: `python3` (3.14.7), `perl` and `tesseract` (5.5.3) all
   **run** in this shell as of 2026-10-03 (this item said they were blocked; `§106`'s device pass already
   used `tesseract`). `jq`, `grep`, `sed` and `awk` stay the house style for text work and OpenSpec
   interaction (`openspec/config.yaml` — tooling), so reach for `python3` only when shell text tools
   genuinely cannot express it, and never for `openspec` commands or their output.
   (guidelines/Build.md — shell constraints)
2. Never `pkill -f "gradlew …"`: `-f` matches the calling tool's own command line, kills the shell, and the
   rest of the chained command (e.g. a `git commit`) silently never runs. Kill by PID
   (`ps -o pid,args` → `kill -9 <pid>`). (guidelines/Build.md)
3. Long builds/tests (`./gradlew test`, `release`, full CMake) exceed the shell output **cap** — run them in
   the **foreground** with the output redirected (`./gradlew … > /tmp/x.log 2>&1; echo "exit=$?"; grep -E
   'BUILD SUCCESSFUL|BUILD FAILED' /tmp/x.log`) and a generous `timeout`. Never `nohup … &`: the harness
   kills the process group when the tool call ends, so the run dies mid-build with no verdict. All three
   skills (`build-app`, `run-tests`, `release-build`) and `guidelines/Build.md` §2 prescribe this —
   **resolved 2026-10-03**.
4. Never start a second Gradle build against the same output directories while an aborted one is still
   running (the daemon keeps rewriting SBOM/assets; the next build then reports bogus parse errors).
   (guidelines/Build.md)
5. A `BUILD SUCCESSFUL in 5s` / `… up-to-date` line is not test evidence — see §17. Quote executed-task
   count + elapsed time and verify counts from the result XMLs. (tracked: §17)
6. Verify artifacts by comparing them to their inputs (content/mtime), never by the existence of an output
   path: build-cache-restored APKs in `outputs/apk` and stale `build/outputs/sbom/*` look like product bugs.
   (guidelines/Build.md)
7. Do not use `/tmp` as a workspace for large map imports (RAM tmpfs, ~7.7 GB quota); use a disk path.
   (guidelines/Build.md)
8. Test-suite heap and class batching: both modules declare their fork budgets (`guidelines/Build.md` §6), so
   batching is a DIAGNOSTIC fallback only (e.g. to isolate one class), never the procedure. When it is used:
   `--tests "com.x.[A-E]*"` is NOT a glob in Gradle (one pattern per prefix or explicit class names), per-batch
   XMLs must be copied out because Gradle cleans `test-results` on each invocation, and coverage runs in a
   separate invocation from the test gate. A build-cache hit (`FROM-CACHE`, no test executor) is not test
   evidence — use `--rerun`.

### B. Editing discipline (edit/ctx_patch)

9. Multi-edit calls are atomic: one ambiguous or missing `oldText` rejects the WHOLE call, silently leaving
   the other edits unapplied. Re-read every changed region before compiling, and make each anchor unique
   with its distinguishing surrounding line. (guidelines/Design.md or a skill note)
10. Never retype prose/code from memory into `oldText` — copy it from the read output. Never emit
    overlapping/nested `oldText` regions in one call; for pure insertions keep the entire matched block in
    `newText` and append to it.
11. After an edit reports a missing `oldText`, re-read the file before re-issuing: a corrective pass can
    silently duplicate a helper (duplicate blocks then make exact-match edits ambiguous).
12. One mutation per revert check — a combined mutation masks the other behaviour and the assertion becomes
    vacuously true. (guidelines/Build.md — test evidence)

### C. Kotlin / Compose / car-app build traps

13. `while (isActive)` in a coroutine needs the explicit `kotlinx.coroutines.isActive` import.
    (guidelines/Design.md)
14. `KProperty0.isInitialized` works only for `lateinit`; for `by lazy` state use a nullable `var` or a flag.
   
15. Test coroutine extensions need a receiver: declare helpers as `private fun TestScope.foo()`;
    `advanceTimeBy(Long)` is an extension, `advanceUntilIdle` a `TestScope`/`TestCoroutineScope` extension.
   
16. Lifetime background tickers (`while (true) { delay(1s) }`) must run on a real dispatcher
    (`Dispatchers.Default`) with test hooks — on the test scheduler they hang every `runTest`.
   
17. Compose plurals containing `%d` need the count as an explicit format argument
    (`pluralStringResource(id, count, count)`).
18. Never call `composeRule.setContent {}` twice in one test; collect all values in the single composition.
   
19. Never nest two `verticalScroll` containers; give an embedded scrollable an opt-out parameter.
   
20. Robolectric's Compose root is clamped to 320×470 dp — assert against the screen, not hardcoded sizes, and
    read the failing bounds from the assertion message.
21. Robolectric's shadow canvas discards `drawBitmap`/`drawPath` (0 opaque pixels) and
    `@GraphicsMode(NATIVE)` does not fix it here — assert the bitmap contract (size/config/caching/no-throw)
    and leave visuals to on-device; do not add a second Robolectric sandbox config.
22. Compose dropdown popups do not appear in `uiautomator` dumps — assert the field's text instead.
    (guidelines/UI.md)
23. Java types from the JNI bridge: positional constructor args only (no named arguments), read the ctor
    before writing helpers (`RouteInstruction` has 5- and 10-arg forms), and update EVERY call site in the
    same change when such a ctor changes — stale test bytecode surfaces as `NoSuchMethodError`, not a
    compile error.
24. Do not mock Kotlin `object` `@JvmStatic` methods (mockk cannot intercept them); stub the real
    `applicationContext` and the Java delegate the object calls (`dagger.hilt.EntryPoints`).
25. `Notification.actions` is nullable (`actions?.isEmpty() != false`); use `getIcon()` (the Kotlin field is
    deprecated and breaks warning-free builds).
26. Every user-facing number/coordinate formatter takes an explicit locale
    (`String.format(Locale.US, …)`) — default-locale formatting broke tests and reads ambiguously in German
    (the `:core/CoordinateFormat` seam is the fix for that family, `fix-comma-decimal-coordinate-entry`).
    `"%.5f".format()` rounds, it does not truncate.
27. `NavigationTemplateMapper.distanceForDisplay` reports display units — assert `displayDistance` plus
    `displayUnit`, not metres.
28. Verify constructor-argument edits against the file's imports in the same pass (a rename dropped a
    still-used import and invented a non-existent class).
29. For car-app screens, check the real API surface first (getter names, resolved lifecycle version, no
    `Lifecycle.getObservers()`); drive lifecycle through `dispatchLifecycleEvent`, ON_CREATE + ON_START
    before ON_DESTROY.

### D. Test / verification discipline

31. Never pace a unit test off a timer or a debounce (`Thread.sleep`): control the loop
    (`asyncLoopsEnabled = false/true`) and land a frame deterministically (`renderFrame()`).
    (guidelines/Build.md)
32. After a boundary-signature semantic change, grep the tests for the observable field first
    (`lastRenderMag` now holds the raw scale → compare `2^level`).
33. Compute `log2` expectations with a calculator, not mentally.
34. Native index test fixtures must be FULL (non-eco) imports — `--eco true` skips the POI indexes.
   
35. `DBThread` loads databases sequentially: wait for two consecutive identical results before asserting.
   
36. Verify `md5` after any `git stash` cycle before rebuilding — a stash+pop silently reverted a submodule
    patch and the "patched" host library was unpatched.
37. Coverage: Kover/JaCoCo per-class numbers are meaningless for Robolectric-only classes (sandbox
    classloader, §15) — use revert-checks as evidence; never apply Kover to a Kotlin-plugin-free module
    (use the Gradle `jacoco` plugin there). (tracked: §14, §15)
38. Test-harness flakes of unknown origin get an entry with the rerun evidence instead of a silent retry.
    `FavoritesSheetReorderComposeTest` was the worked example: the 5 s bound was never the problem —
    the awaited state came from `Dispatchers.Default` and the fix pinned the dispatcher
    (`guidelines/Build.md` §6).

### E. On-device / emulator preconditions

39. Never install an ABI-filtered APK (`-Pandroid.injected.build.abi=…`): packaging strips the other ABIs,
    the install succeeds and the app dies at `System.loadLibrary`. Verify with `unzip -l <apk> | grep <abi>`
    and check the APK mtime against the newest source edit before an on-device run.
    (guidelines/Build.md — on-device verification)
40. Emulator GPS cannot inject a bearing: `geo fix` has no bearing argument (always `bear=0.0`), and NMEA RMC
    is dropped by FusedLocationProviderClient ("too close / too fast"). Do not plan bearing/heading on-device
    tests on a GMS emulator — cover them with unit tests.
41. Headless emulators need `-dns-server 8.8.8.8` (broken DNS → "Unable to resolve host" while raw IP works) —
    compare `adb shell ping` against the app's network code before debugging the app.
42. Check which maps are already installed BEFORE attempting a catalog download — `adb install -r` preserves
    data.
43. After toggling `location_mode`, re-send `geo fix` (Fused may need provider re-registration).
44. `adb shell input text` goes through the active IME (GBoard rewrote `Erbstollenstrasse` →
    `Er Stollenstraße`): disable the IME for the replay and assert the field's actual value from the
    `uiautomator dump` before interpreting results.
45. The AAOS/car AVD is not usable for on-device steps in a headless agent session: car system-UI ANRs swallow
    `input tap`, `uiautomator dump` returns an empty hierarchy, `geo fix` is answered OK but no fix reaches the
    app, and the host `RendererService` disconnects ~40 s after launch. Plan car verification for an
    interactive window or a real head unit, and state the blocker instead of burning a session.
    (guidelines/Build.md + the OpenSpec apply guidance for car changes)
46. Before an apply/verify pass, check for a concurrent writer in the tree (`find <module> -newermt "-10 minutes"`,
    active Gradle clients) — one writer per tree; if a peer is mid-edit, quote the evidence already collected and
    stop. (AGENTS.md — parallel sessions)

### F. Native / libosmscout

47. Any change inside an `#ifdef OSMSCOUT_HAVE_LIB_MARISA` block must be verified in BOTH configurations — the
    Android build always defines it (vcpkg provides marisa), so CI's non-Marisa path is invisible here.
    Reproduce locally: insert `#undef OSMSCOUT_HAVE_LIB_MARISA` after the include block of
    `OSMScoutClient.cpp`, build `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then remove
    the `#undef`. (guidelines/Build.md — native verification)
48. Native test binaries are not directly executable (allowlist): run them through `ctest -R <Test>
    --output-on-failure` from the build directory. `ctest` does not build — `ninja -C <build> <Target>` first, and
    filter `ctest -N` output for `Test #` lines. For the meson `hostbuild/` directory this does NOT work: it has no
    `CTestTestfile.cmake`, so `ctest` answers "No tests were found!!!" even for a registered, green test — use
    `meson test -C hostbuild "<test name>" --print-errorlogs` there, and note that meson prefixes its target names
    with the subdirectory (`Tests/FavoriteStoreTest`, not `FavoriteStoreTest`), with one throwaway `ninja` invocation
    needed after a `meson.build` edit so the regeneration lands before the target lookup
    (found 2026-09-20, `fix-favorite-store-write-race`).
   
49. `System.loadLibrary` needs the plain-name `.so` — versioned `.so.1` symlinks are not found.
50. Plain `openDatabase(containerRoot)` wipes the DBThread (the root has no `types.dat`); load a container of maps
    through the builder's map-lookup scan.
51. The `:osmscout-client-java` Gradle JAR excludes `OSMScoutClient.java`/`Builder` — never feed it to JavaScout
    Maven (a stale `~/.m2` JAR breaks the signature); build the JAR from the submodule `java/` sources.
   
52. JavaScout Maven tests need `JAVA_HOME=java-21` on this machine (JUnit 5.10.2 on Java 26 discovers but
    executes 0 tests).
53. Stale meson host builds: `sed` the ninja link line to a stub path before rebuilding (`/usr/lib` is not
    writable, no sudo).
54. Before pushing a submodule branch, run `git ls-remote origin <branch>` (not the local remote-tracking ref)
    immediately before the push, and let ONE session own the submodule update — two sessions created the same
    JNI commits and force-rewrote `naviveylin-local` (reconciled by a merge, no force-push, nothing lost).
    (AGENTS.md — submodule workflow)
55. Stylesheet hex literals are lowercase-only (`Color::GetHexValue` asserts), and a stylesheet defect does NOT
    fail the build: run the app once after the first stylesheet change and grep
    `adb logcat -s NaviVeylin | grep -i "style error"`. A rejected stylesheet no longer crashes the renderer —
    the client keeps the previous style and reports the failure (closed 2026-09-27), so the user now sees it;
    the silent-build gap above stays.

### G. Design / scope discipline

56. Verify the premise before building a change on it: the tile-path condition (pinned by
    `TileCacheRenderTest`), the real pixels-per-degree the renderer produces (DPI × `cos(lat)`, cross-checked
    through `ProjectionUtils` rather than re-derived with the formula under test), and the "single source of
    truth" location of a dedup change (removing the duplicate must not remove the only instance).
    (guidelines/Design.md)
57. Log BOTH sides of a seam (pending vs displayed frame, both path counters) in one grep-able line — five
    changes shipped while the AA follow defect was live because no log line compared the two frames.
    (guidelines/MapRendering.md)
58. When two layers can both own a transition, decide the pacing (render vs fix) before coding; never call a
    stateful controller twice for a check-then-use pair; scope a renderer-wide rule with an explicit opt-in
    flag. (guidelines/Design.md)
59. Write down which coordinate frame each number lives in before comparing (pre-shift projection vs post-shift
    visible band).
60. In ViewModel tests, assert pre-state through an entry path that does not mutate the snapshot field
    synchronously before the collector resumes.
61. Initialize progress state to the "0 % traveled" invariant (`remainingDistance = totalDistance`), not the
    data-class default; state machines with wall-clock state need a "never set" sentinel and an explicit
    first-event branch.

---

## 134. Guardrails extracted from `ki_processing_failures.log` (2026-10-05)
**id:** 134 · **category:** specs-and-process · **class:** improvement · **status:** open

Every entry in `ki_processing_failures.log` up to 2026-10-05 (61 dated entries / 202 items) was processed
and removed from that file; the actions derived from them live here. `**[open]**` means the named artifact
does not carry the rule yet — an item *without* the marker is only a cross-reference to a rule that already
exists (`TODO.md` §40, a `guidelines/*` section, a skill). Item numbers continue §40's list (62 onward);
§40's items 1–61 are not restated and are never renumbered. Group headings A–G match §40's. Cross-refs used
instead of new items: §40.1–§40.61, `guidelines/Build.md` §4/§6/§10, `TODO.md` §17/§66/§95/§106.

### A. Harness / shell / Gradle

62. **Verify a liveness guard against a known-running build before trusting it.**
    `pgrep -f 'GradleWrapper[M]ain'` can never match — the wrapper runs as `java … -jar gradle-wrapper.jar`, so
the main class lives inside the jar. Use `pgrep -af 'gradle-wrapper\.ja[r]'` (or the launcher PID), cross-check
the `BUILD SUCCESSFUL|BUILD FAILED` verdict line, and never let a guard's own `echo` state a verdict the guard
did not measure. Seen 2026-09-21, 09-22, 09-26, 10-04 and 10-05.
Carried by `guidelines/Build.md` §2 ("Liveness guard and shell recipes"), `AGENTS.md` rule 4, and the three build skills.

63. **One builder per working tree.** Two Gradle builds in one tree corrupt each other's
    `app/build/intermediates/**` and build-cache packs (a foreign build killed `:app:mergeAutomotiveDebugResources`;
a foreign edit mid-run inflated a measured 8m25s gate to 22m37s). Isolate a long verification run with
`git worktree add`, or confirm the other session is done; a wait loop that never saw a quiet window must abort,
not fall through into the run. Carried by `AGENTS.md` ("Agent iteration loop", rule 4) and `guidelines/Build.md` §2.

64. **Recover from a killed run in this order:** `./gradlew --status` for a `BUSY` daemon, `./gradlew --stop`
    for orphans, then run per module/variant; check `free -m`, kill orphan test workers by PID
(`ps -eo pid,rss,args --sort=-rss`, confirm the worker's `-Djava.library.path` points at this repo), and retry
with `--max-workers=1`. (Extends §40.4.)

65. **Address the variant unit-test task** — `:app:testDebugUnitTest` is ambiguous (the `dist` dimension) and
    `:core:test`/`:auto:test` take no `--tests`: use `:app:testMobileDebugUnitTest`,
`:app:testAutomotiveDebugUnitTest`, `:core:testDebugUnitTest`, `:auto:testDebugUnitTest`. Add `--continue` for
any run meant to cover the whole suite, and read the executed-task list, not the flavors you expect.
(The skills carry it; §79/§100 were closed for it.)

66. **"No verdict in the log" is *killed*, not failed** — split per module instead of re-running the aggregate
    (§40.3). After a killed run, `rm -rf app/build/test-results/<flavor>` before the next one, or it aborts with
`java.nio.file.NoSuchFileException: …/in-progress-results-generic.bin` after printing the tallies. Carried by
`guidelines/Build.md` §2 ("A suite run that died mid-flight is not neutral").

67. **Allowlist recipes that work, instead of re-discovering them:** `find … -print | xargs -r rm -rf`
    (never `-exec … {} +`); `awk` for arithmetic (`bc` is blocked); `perl -0pi` is blocked → use the editor's
`replace_all` with an anchored multi-line pattern; no `$()`/backtick at command position (print the prepared
command once with `sed`, paste it verbatim); no `/tmp` scripts; the projection head unit needs
`lean-ctx allow desktop-head-unit`. Carried by `guidelines/Build.md` §2.

68. **Shell and build-script traps:** zsh does not word-split (`$filters` / `for x in "a b"` arrive as one argv
    element — "Task ' …' not found"), and an `adb` call inside a `printf | while read` loop eats the pipeline's
stdin (add `</dev/null`); `adb logcat -d --pid <pid>` hangs → `-t <N>` plus an `awk` filter on the PID column;
in `*.init.gradle.kts` a deprecated Gradle member is a compile error (probe the API by reflection in a
throwaway Groovy init script first), and an override of a build-script value must be applied in
`gradle.projectsEvaluated { }` and verified by an observable (worker count), not by the flag it was given.
Carried by `guidelines/Build.md` §2 (shell traps; init-script traps).

69. **A tool that infers a directory layout needs a shape check, and a dry run only validates the exact
    invocation that is repeated** — `tools/prune-native-configs.sh` deleted ABI directories inside hash trees
when its root shifted one level; refuse a root whose grandchildren are ABI names. Carried by
`guidelines/Build.md` §2.
(`guidelines/Build.md`)

### B. Editing

70. **One file per `edit` call, and every `edits[]` entry holds exactly `oldText` + `newText`** — a prose note
    key or an anchor from another file rejects the whole atomic call (three instances). Carried by
`guidelines/Design.md` §12 ("Change hygiene when editing files").
(`guidelines/Design.md`)

71. **One writer per file per turn** — a `sed -i` and an `edit` in the same message clobber each other (the
    edit writes the file from its own read); use one tool for both changes. Carried by
`guidelines/Design.md` §12.

72. **Split a large or function-spanning `oldText` on function boundaries, and never re-issue a rejected
    fragment unchanged** (§40.9–§40.11) — a >40-line anchor, or an inner fragment of one long prose line, is
rejected while its parts apply.

### C. Kotlin / Compose / car-app

73. **Never mock a class that bears `native` methods or returns Kotlin flows** — `native` cannot be
    relaxed/intercepted (`UnsatisfiedLinkError`), a relaxed mock of a `StateFlow`-returning member throws
`KotlinNothingValueException` on `collect`, and a relaxed `Boolean`/object answer is `false`/`null` (stub
`isValid = true` for a surface a renderer draws on). Use the module's fakes (`FakeOSMScoutClient`,
`FakeMapScreenClient`, `FakeAutoRenderClient`, `FakeClientWithBbox`, `FakeStyleLoadClient`) and real
`MutableStateFlow`s, and expect to grow a fake one native call at a time. (Cross-ref §40.24.)

74. **A *new* native method is invisible to the compiler** — the fakes compile without overriding it and the
    first call throws `UnsatisfiedLinkError`; update every fake in the same change and run the affected suites
(the `native-bridge-signature-change` skill catches changed signatures, not new ones). Carried by
`guidelines/Build.md` §4 ("Native verification — new methods, hidden visibility").
(`guidelines/Build.md` — native verification)

75. **Check visibility before designing a test around a helper** — `internal` in `:core` is unreachable from
    `:auto`/`:app` tests (`DiagnosticsLog.flushNow`/`awaitDrained`/`workerThreadOrNull`), a JUnit rule exposed
through a public property cannot be `internal`, and a public class cannot take an internal parameter type.
Assert the property structurally instead. Carried by `guidelines/Design.md` §11 ("Check a helper's *visibility*
before designing a test around it").

76. **Car-app / Compose / lifecycle traps:** drive `LifecycleRegistry` through the states a host would
    (INITIALIZED → CREATED → DESTROYED, never straight to DESTROYED); `androidx.car.app.Screen`'s constructor is
protected (subclass it); `ShadowDialog.getLatestDialog()` is null for a Box overlay (dispatch back through the
activity); Material3 `PartiallyExpanded` is a fraction of *content*, so keep anchor state in the state owner;
`assertIsDisplayed` fails for content inside a `verticalScroll` in Robolectric's 320×470 dp window (assert
existence, and re-check the product consequence); Compose test symbols are either rule members (`onAllNodes`) or
extensions (`assertIsDisplayed`) — read the whole error list before importing. (Extends §40.20/§40.21/§40.29.)

77. **A drag surface needs its tap/drag split tested, and a distance is not a direction** — a long press on a
    `sh.calvin.reorderable` handle also delivers the click of a clickable *descendant* (suppress the next tap
from `onDragStarted`, clear it on a committed move); sign the gesture delta from the two items, not from a
positive distance; bound the drag to about one item width (a drag parked at the grid edge leaves the library's
auto-scroll running into later tests); `assertSame` against a second `dagger.Lazy.get()` compares different
objects (capture the instance inside the lambda). Carried by `guidelines/UI.md` §1 ("Drag surface checklist").

### D. Test / verification discipline

78. **Assert what the contract requires — never an invented count.** Read (or measure) the current behaviour
    before writing an assertion about a call/persist/emission count: a `StateFlow` re-delivers its value to every
new collector (snapshot, assert the delta), a stress test must not compare two observations of shared state (one
read per assertion, all writers stopped before the final state), and a count the code never had makes a test fail
on correct code. Three documented instances (favorites persists, `CarScreenObservations`, `DiagnosticsLog`) —
treat it as a reflex. Carried by `guidelines/Design.md` §11 (and `guidelines/Build.md` §4).

79. **Assert the invariant, not "nothing happened"** — asynchronous seams make emptiness assertions racy
    (assert the specific value/host-thread invariant and poll with a bound), and a test of fault confinement must
make *every* line of the failure path runnable: a `Log`/`DiagnosticsLog` call inside an `.onFailure { }` throws
"not mocked" under plain JUnit and kills the collector outside the guard (run it under Robolectric). Never
re-init a buffered logger between act and assert (`DiagnosticsLog.initForTest` clears the ring silently). Carried by `guidelines/Design.md` §11 ("Assert the
invariant, not \"nothing happened\"…").

80. **Run a new regression test against the *unfixed* revision first** — one that passes against the bug is
    worse than none; if it cannot fail there, delete it and record that a device run is the only evidence. Carried by
`guidelines/Build.md` §4.

81. **Revert-check scope** — run *every* case the guard protects (a check that fails only one of them measures
    the test, not the code), make the mutation unreachable at **run** time (a compile-time constant breaks Kotlin
smart casts), and confirm the failure is the expected assertion — a fault on a background dispatcher is
invisible to a main-looper assertion. Assert the positive fact (the buffer *was* acquired once), not the absence
of growth. Carried by `guidelines/Build.md` §4 (and the `revert-check` skill; §40.12).

82. **A timeout-shaped failure tests the resource hypothesis first** (fork heap/cadence: 512 MB → 1024 MB
green), and "pre-existing" is proven by a control run (`git stash push -u` → same command → `git stash pop`),
never by reasoning about the dependency graph. Carried by `guidelines/Build.md` §6.

83. **A flaky victim needs a rate per configuration (N ≥ 3) before a bisect means anything**, and the victim's
    package says nothing about the leak's owner — one process-wide leak produced failures in three unrelated
packages. Get the real execution order from the result-XML `timestamp`s and bisect with literal `--tests`
filters. The rate rule is carried by `guidelines/Build.md` §4; §4 also carries the ritual.

84. **Virtual-time and async seams** — `advanceTimeBy` is exclusive at its boundary, so a debounce landing
    exactly on the advanced-to instant never fires (advance past it by more than a tick, or `runCurrent()`); a
production `withContext` inside a suspend accessor needs its own dispatcher seam before a virtual-time test can
rely on it; when a seam becomes asynchronous, grep the *test tree* for it before the suite (focused runs hide
the timing); and do not make a Compose test depend on a real dispatcher hop (split the loader wrapper from the
pure view). Carried by `guidelines/Build.md` §4 and `guidelines/Design.md` §4.

### E. On-device / emulator

85. **Device paths, dumps and taps:** the harness refuses absolute device paths (`/sdcard/…`,
    `/data/local/tmp/…`) — keep them relative inside `adb shell`, or build them from an octal-escaped variable
(`S=$(printf '\57')`) / read through `cat $EXTERNAL_STORAGE/window_dump.xml`; a dump is only usable if provably
fresh (require `dumped to`, use a unique literal path, retry) — a failed dump leaves the previous file and every
later tap aims at the old screen; resolve a node once and tap *that* node (`clickable="true"`, `content-desc`,
`mCurrentFocus`) instead of re-grepping text or estimating coordinates; the top strip (y≈63) is the
notification-shade gesture. Carried by `guidelines/Build.md` §10 ("Device recipes that cost a round every
    time").

86. **Verify the install, not the script that installed it** — compare `dumpsys package <pkg> … lastUpdateTime`
    with the build's timestamp (a timed-out `adb install` silently left the previous build running); probe the
packaged entry that exists (the debug library is `libosmscout_client_javad.so`) and grep a *calibrated* control
literal; treat a surprising probe result as a verification bug before reporting a product defect.
    Carried by `guidelines/Build.md` §10.
(extends §40.39/§40.49)

87. **Host load explains ANR-shaped device evidence** — read `uptime`/`ps` before blaming the app
    (`./gradlew --stop` does not kill native compiler children it already spawned); check
`isKeyguardShowing`/`mDreamingLockscreen` before trusting any UI-driven step (a locked phone silently redirects
taps, and `run-as` does not work on a Play-signed build); keep `logcat -G 16M` for a long pass.
    Carried by `guidelines/Build.md` §10.

88. **A long-press drag on device is a motionevent sequence** — `input draganddrop`/`swipe` never starts the
    reorder library's drag: `input motionevent DOWN x y`, `sleep 1`, a MOVE loop (~120 ms per step),
`input motionevent UP x' y'`; consecutive invocations share pointer 0. Carried by `guidelines/Build.md` §10.

89. **Multi-user images and an a11y-less car surface** — the car session may live in user 10 while `run-as`
    reaches user 0 (`/data/user/<id>/…` in the app's own log lines; `run-as --user` is rejected on API 33
toybox): build the state through the UI instead of seeding, or use a userdebug image. The car surface exposes no
accessibility nodes, so read text and positions from `adb exec-out screencap` + `tesseract … tsv`, and expect
the distant-display mirror to re-assert `CarAppActivity` within seconds (the phone UI is not a stand-in).
Carried by `guidelines/Build.md` §10 ("Multi-user and a11y-less car surfaces"); the multi-user facts stay
recorded in `TODO.md` §106.

90. **Record a partial device pass as partial** — a disappeared AVD, an unreachable state or a stale frame goes
    into the change's tasks with the numbers actually collected, never implied as proof. Carried by
`guidelines/Build.md` §10.

### F. Native / libosmscout

91. **Native test invocation:** list the registered name first (`meson test -C hostbuild --list | grep -i
    <topic>`; the runnable name is the `test()` label — `libosmscout:Check search scope`, not the subdir path);
`meson test` refuses unbuilt suites and `ctest` finds nothing in `hostbuild`; build only the test targets
(`ninja -C hostbuild $(ninja -t targets all | sed -n 's/^\(Tests\/[A-Za-z0-9_]*\): .*/\1/p')`) instead of the
world, and read the Catch2 tally from `-v`. (Extends §40.48.)

92. **A hidden-visibility library may only expose *exported* types on its interface** — holding an older
    un-annotated class by value fails with `undefined reference to vtable for …` for every consumer outside the
library; hold the exported factory handle instead. And `ctest` after a partial `ninja` is not evidence about
current sources — `ninja -k 0` first. Carried by `guidelines/Build.md` §4 ("Native verification").

93. **Stylesheet and log-parsing fixtures** — a fixture stylesheet must follow the grammar (`NODE.TEXT`/
    `NODE.ICON` for node rules, no suffix for way rules); two emitted lines that intentionally share a prefix are
selected by level (or an exact marker), never by substring; and a parser test seam should print `GetErrors()` on
failure instead of only asserting `false`. Carried by `guidelines/MapRendering.md` §15a.

### G. Design / scope

94. **A `Map` is never the carrier for "same contents, different order"** — a `StateFlow` drops an emission
    equal to its current value and `Map.equals` ignores iteration order, so an order-only change never reaches
the collector (it "worked" only because the bridge handed out identity-unequal objects). Give order its own
`List` channel, and before leaning on a derived value as a contract, check *what makes it change*.
Carried by `guidelines/Design.md` §3 and `guidelines/UI.md` §1.
(`guidelines/Design.md` §3)

95. **Read the *constant's* deprecation text before building on a lifecycle signal** —
    `TRIM_MEMORY_RUNNING_LOW`/`_CRITICAL`, `MODERATE` and `COMPLETE` are never delivered since API 34 and
`onLowMemory()` is deprecated since 35, so the designed cache release would have shipped inert. Pass intent as
an explicit flag, never infer it from a display string; assert the negative case (a comfortable device releases
nothing) explicitly. Carried by `guidelines/Design.md` §4.

96. **A process-scoped ticker a test constructs keeps running for the whole suite's JVM** — give it a test
    switch and `runCatching`-wrap the loop body. In a bounded writer, a "here is what is missing" marker stored
*in* the ring is evicted by the very bound it reports (carry it in the flush), the write target must be captured
with the batch (a writer that reads its target at write time writes one target's lines into another), and a
high-water flush only carries the entries present at the crossing. Carried by `guidelines/Design.md` §4.

97. **"No record in my run" is evidence about the run, not about the code** — read the trigger's *condition*
    and construct it before writing a finding (`MemoryInfo.lowMemory` "never fired" and then fired mid-walk); a
design table is a hypothesis, not evidence (read every assignment to a buffer before planning to remove it — one
bitmap was counted three times); and a footprint number is meaningless without its UI state, render count and
car-session presence (`guidelines/Build.md` §10 carries that half). Carried by `guidelines/Design.md` §12
("Reason from the measurement, not from the model"; extends §40.56).

98. **A gate needs one rule per delivery *shape*, and it must be run against the fixed shape** — a
    source-level privacy gate keyed on identifiers (`lat`/`lon`) missed whole-object and URI-read hand-overs
(`$request`, `${original?.data}`), and widening it to "any `.data` in an interpolation" then flagged the fix
itself (`scheme=…` is identity, not position). A brand-new gate that fails on its first run may be the wrong
gate; a `Sync` task's output is not a mutation point (the sync restores it), and the unit-test asset APK can
answer stale. Carried by `guidelines/Regulatory.md` §9.

---

## 49. One full car render allocates ~15 MB of transient buffers — app-side FIXED by `fix-render-buffer-reuse` (2026-09-26), native half FIXED by `reduce-render-peak-memory` (2026-09-27); the `Graphics` footprint and the growth measurement stay open
**id:** 49 · **category:** map-rendering · **class:** improvement · **status:** open (device)

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
- **Native half fixed ✅ by `reduce-render-peak-memory`** (2026-09-27; spec `osmscout-jni` — Render
  entry point writing into a caller-supplied pixel buffer; spec `render-performance` — A render
  writes into caller-owned pixel storage, Per-render transient allocation is bounded): the JNI
  bridge gained a buffer-taking entry point (`OSMScoutClient.renderInto`, a direct `ByteBuffer`) and
  the allocating one is unchanged; both run **one** shared render body, so they cannot render
  different frames. The frame is written straight into the caller's pooled storage, so the C++
  `std::vector<uint32_t>`, the `jintArray` and the per-pixel BGRx→ARGB loop this entry names are gone
  from the pooled render path — at the car's overrun size that is the ~37 MB of per-render transient
  buffers. Format contract: `0xAARRGGBB` ints, stride `width*4`, native byte order, which is what
  `Bitmap.copyPixelsFromBuffer` reads. Tests: the buffer-path frame is byte-identical to the
  allocating path's, no frame-sized buffer is allocated after the first render of a size, a failed
  render leaves the caller's target untouched, and the pool's bound/refusal are pinned.
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
  revert-checks quoted in that change's tasks. This entry stays open for the two items its header
  names as unfixed: the `Graphics` footprint and the on-device growth measurement (device-gated).
- **Still open ✗ — the measurement**: no device was attached (`adb devices` empty) and the AAOS AVD is
  unusable for headless sessions (§40.45), so the `dumpsys meminfo` before/after comparison did not
  run. Recipe for a session with a device: note native + Java heap and the `MAP` render count, drive
  ~10 minutes (stationary + repeated identical route, per §65), compare against the pre-change build;
  the pool changes churn, not peak, so expect dampened heap saw-teeth rather than a lower peak.
- **Original fix candidate (2026-09-21, now half taken)**: reuse a caller-supplied pixel buffer across
  renders (bitmap + `int[]`), and hoist the overlay paints/paths into fields. Measure with
  `dumpsys meminfo` before/after; a phone render-path change, not a car-only one.

## 56. Trip publishing is the one host sender that deliberately keeps firing while the session is stopped — Found 2026-09-21 (same review)
**id:** 56 · **category:** car · **class:** improvement · **status:** on-hold host-stability trade-off (needs an AVD measurement)

- **Loop verdict** ⏳ (bug-fix loop 2026-10-09, `bugfix-loop` iteration 5 — reclassified `bug` → `improvement`,
  not eligible): the bug framing is wrong twice. (a) The send **is** bounded today:
  `NavigationManagerController.kt:101` gates on `navigating` (set in `onNavigationStarted :69`, cleared in
  `onNavigationEnded :81`, `onDestroy :127` and on a rejected `updateTrip :109`; KDoc `:31-33`), so publishing
  happens only inside a navigation period. (b) This entry's own second fix candidate is already shipped and
  asserted — `NavigationManagerControllerTest.kt:144-161` `aRejectedTripUpdateEndsTripPublishing`
  (`verify(exactly = 1) { nm.updateTrip(any()) }`). The only surviving option — gate the send on the session's
  started period — is a **decision**, not a repair: it contradicts `guidelines/Design.md:495-499` ("guidance
  updates and trip metadata keep flowing" while the car app is not visible) and
  `openspec/specs/car-host-fault-isolation/spec.md:240-243` ("Guidance progresses while backgrounded").
  Copying the sibling pattern (`NavigationSession.kt:664`) onto the trip collector (`:684-690`) would *delete*
  that scenario. Path: close this as resolved by the existing gate, or open a **normal (non-loop)** change that
  modifies the scenario + `Design.md:499` + the collector when the stale-cluster trade-off is chosen — settled
  by an AVD run counting `HOST` trip lines against host stability, not by a host case.

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
**id:** 57 · **category:** car · **class:** bug · **status:** in-flight fix-details-screen-observation-scope

- **Observation** ℹ: `DetailsScreen` starts its favorites, basemap and position collectors in `init`
  into `loadScope` (`DetailsScreen.kt:189-219`), cancelled only in `onDestroy` (`:266`). While the
  screen is stopped (backgrounded, or covered by a pushed screen) they keep calling `invalidate()` —
  a library no-op while the screen is not at least STARTED (`androidx/car/app/Screen.java:102-106`) —
  plus `invalidateData()` and `setGpsMarker`, which do request full native renders. Same defect class
  as the observer leak fixed by `fix-car-screen-observer-leak` on the three map screens, but here it
  is "runs while invisible" instead of "grows per start".
- **Fix candidate**: move them onto the same per-start tracking that change introduces, or stop them in
  `onStop` and restart in `onStart`.

## 78. Overrun-window offset math lives in `FollowPrediction` and the follow overlay path is not unified — Found 2026-09-24 (while fixing the phone pan tracking)
**id:** 78 · **category:** map-rendering · **class:** improvement · **status:** open

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

## 119. The Android bridge override trails the submodule's Java favorites API — Found 2026-09-28 (while adding the cross-group favorite move)
**id:** 119 · **category:** native-jni · **class:** improvement · **status:** open

- **Debt** ℹ: `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  shadows the submodule's Java source (that file is excluded in `osmscout-client-java/build.gradle.kts`)
  and is one API family behind it. `getFavoriteFileFormatVersion` and
  `isFavoriteFileFormatSupported` are declared in
  `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  (submodule, added by `530ac8768`) and implemented in the linked JNI (`OSMScoutClient.cpp`), but are
  absent from the override, so the app cannot reach them. `moveFavoriteToGroup` was declared while landing
  `move-favorite-between-groups`, `moveGroup` while landing `reorder-favorite-groups` and
  `moveStarredFavorite`/`getStarredFavorites` while landing `order-starred-favorites`; the two file-format
  ones stay unreachable (nothing reads the format version).
- **Fix candidate**: declare the missing natives in the override, and add a buildSrc/CI gate that compares
  the override's `native` declarations with the submodule's Java source and fails on a mismatch — the drift
  is silent otherwise (no compiler error, no failing test: only a `NoSuchMethodError` at the call site).
- **Why deferred** ✗: the in-flight change `move-favorite-between-groups` needed exactly one of them;
  widening it would have mixed an API-sync refactor into a feature change.
- **Id note** ℹ: this entry was `§79` until 2026-10-03, when the duplicate number was resolved to `§119`
  (the other `§79` was closed and removed). It is still quoted as `§79` by
  `fix-open-database-path-validation/design.md`, the `reorder-favorite-groups` traceability and
  `condense-style-load-warnings`; those historical citations are left as they are.

## 103. `FavoritesScreen` (car) is the one screen still outside the `CarScreenObservations` pattern — Found 2026-09-29 during `order-starred-favorites`
**id:** 103 · **category:** car · **class:** improvement · **status:** in-flight fix-details-screen-observation-scope
- **Debt** ℹ: `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` collects its favorites, group-order
  and starred-order flows in one `combine` inside `init`, on `carScreenScope("FavoritesScreen")`, and
  cancels it through its own `onDestroy` lifecycle observer. Map, Navigation and FreeDriving screens use
  `CarScreenObservations` plus a `<Screen>Observations` class with started-period lifetime (change
  `fix-car-screen-observer-leak`, spec `auto/screen-observation`). The screen has no accumulation bug
  today — it launches one collector per screen instance and destroys the scope with the screen — so this is
  consistency, not correctness.
- **Fix candidate**: give `FavoritesScreen` a `FavoritesScreenObservations` owned by
  `CarScreenObservations` (favorites map, group order and starred order as its observations),
  `start()`/`stop()` from `onStart`/`onStop`, and a test for the started-period lifetime like the other
  screens' observations tests.
- **Why deferred** ✗: `order-starred-favorites` needed one more input on that existing collector; migrating
  the screen's whole observation lifetime was scope its specs do not require.

## 106. The AAOS AVD's car session lives in user 10 while adb reaches user 0 only, so its favorites storage cannot be seeded — Found 2026-09-29 on `emulator-5554` (automotive distant-display AVD) during `order-starred-favorites`
**id:** 106 · **category:** verification · **class:** improvement · **status:** open (device)

- **Observed** ℹ: `pm list users` shows `0:Fahrer` and `10:Driver`, `am get-current-user` is `10`, and the car
  session's own warmup log writes to `/data/user/10/com.framstag.naviveylin/files/...`. `run-as`,
  however, resolves to **user 0** (`ls -la files/` reports `u0_a235`), so a fixture written with
  `run-as … cat > files/favorites.json` lands in the `Fahrer` profile and the car screen shows an empty store.
  `run-as --user 10` is rejected (`run-as: unknown package: --user`) on the API 33 toybox, `adb root` fails
  (`adbd cannot run as root in production builds`), and shell-initiated `am start --display 1` is a
  `SecurityException`. **What that blocks:** seeding starred favorites for a car-side pass — the car UI can add
  and remove a favorite but has **no star action**, and the phone UI cannot stand in because the distant-display
  mirror owns display 0 and re-asserts `CarAppActivity` within seconds of `MainActivity` starting.
- **Workarounds that did work** ℹ: the car's *own* UI can create an unstarred favorite (map → POI search →
  result → `Zu Favoriten hinzufügen`), which is enough for the mode-split checks (`Alle Favoriten` keeps its
  group section, `Markierte Favoriten` shows the nothing-starred hint). `tesseract` on `adb exec-out screencap`
  reads the car surface (it exposes no accessibility nodes, so `uiautomator dump` sees an empty window).
- **Fix candidate**: use a **userdebug/eng** AAOS image (`adb root` then write the car session's own
  `files/favorites.json`), or an AAOS AVD whose car session runs in the current user, for any later car-side
  favorites verification. A debug-only test hook that seeds the store would remove the dependency on root.
- **Also observed** ℹ: `am force-stop` of the app while its car session is live crashes the **host's** renderer
  process — `FATAL EXCEPTION: main`, `Process: com.google.android.apps.automotive.templates.host:renderer_service`,
  `IllegalStateException: Accessed the car host after it became invalidated`. Not an app defect (the host's own
  process, its own invariant), but it means a force-stop is not a neutral way to restart the app on this AVD:
  the car screen returns to the launcher and needs a fresh launch.

## 108. Three surfaces of the i18n sweep could not be seen on a device — found 2026-09-30 while landing `fix-remaining-untranslated-strings` (task 5.2)
**id:** 108 · **category:** verification · **class:** improvement · **status:** open (device)

- **Device-verified** ✅ that pass: the favorites group-card count reads `0 Favoriten` / `2 Favoriten` / `3 Favoriten` on `emulator-5554` (API 37, de-DE) with the change's `mobileDebug` APK installed (`lastUpdateTime 2026-09-30 21:40:30`) — the German plural resource, where the pre-change build showed `2 favorites`. The same dump shows the German resource set loading in that build (`Was ist hier?`, `Ort suchen…`, `Favoriten`, `Aktuellen Kartenstandort hinzufügen`).
- **Not verified on device** ⏳ (unit + revert-check only):
  1. **The generic details title** (`Standort`) — the `geo:` deep link and the map long-press both land in the **`CandidatePickerSheet`** (`Was ist hier?`) on this build, whose candidates are objects; no coordinate-labelled row appeared at the tapped point, so `LocationDetailsDialog` (the sheet whose title the change made localized) was never reached. A coordinate typed into the search field produced no result row, and `ENTER` left the search view rather than committing the coordinate.
  2. **The notification titles** — starting free driving needs the on-map `Freie Fahrt starten` control (bounds `[955,1673][1018,1736]` in the map dump); the tap did not reach it before the session left the map, and no `com.framstag.naviveylin` notification appeared in `dumpsys notification`.
  3. **The map-download channel name** — `dumpsys notification --noredact` still shows `NotificationChannel{mId='map_download', mName=Map Download}` because `MapDownloadService` has not started since the install: per design D4 the name is re-applied on the next `createNotificationChannel`, i.e. the next download start. The German name is `values-de`-parity-verified, not observed.
  4. **The car favorites titles** — fixed 2026-09-30 by `fix-remaining-untranslated-strings` (`R.string.favorites` / `R.string.starred_favorites`, German titles asserted at unit level), but never seen in German: no AAOS AVD or head unit attached; also the outstanding car-side German run for `fix-comma-decimal-coordinate-entry`'s coordinate row.
- **Fix candidate** (a verification pass, not a code change): with a car surface attached, run §10 of `guidelines/Build.md` against the release build for (1)-(3) — a coordinate `geo:` link or long-press with a coordinate candidate, free driving from the phone, and a map download to re-create the channel — plus the car favorites screens in German. The recipes that worked here are worth reusing: `uiautomator dump <path under /data/local/tmp>` (a `/sdcard` path is refused by this harness), `exec-out screencap -p` + `tesseract … -l deu tsv` for coordinates, and the map screen's German `content-desc` nodes (`Favoriten`, `Ort suchen`, `Freie Fahrt starten`) for tap targets — the Compose canvas exposes almost no text nodes.


## 122. The navigation overlay's stop button cannot be tapped reliably — Found 2026-10-03 while verifying the grace period of `route-planning-session` (task 10.5)
**id:** 122 · **category:** ui · **class:** bug · **status:** fixed-by `fix-nav-overlay-stop-tap`

- **Fixed** ✅ by `fix-nav-overlay-stop-tap` (archived 2026-10-06), **refuted by measurement** in the
  bug-fix loop on 2026-10-09 (`bugfix-loop`, iteration 1): at HEAD the stop control **is** its own
  actionable semantics node — a probe of the merged tree printed
  `Tag:'stopNavigation' Role='Button' Actions=[OnClick] ContentDescription='[Stop Navigation]'` at
  48×48 dp, disjoint from `navStatusDetailsRegion` (bottom 93) and `navStatusDetailsStatsRegion`
  (right 268) — so the mechanism this entry cites (one card-wide `.clickable` covering the control's
  band, `:63`) no longer exists (`grep`: the two remaining `.clickable`s, `:93` and `:140`, are both
  disjoint from the control), and the card class's 8 cases are green
  (`tests=8 failures=0`, XML `ts=2026-10-09T17:01:15.029Z`). The residue of the original report is
  **`§154`** (post-stop mode and session state), not a tap target; the missing assertion that the
  control owns its accessibility action is **`§155`**.

- **Observed** ℹ: while turn-by-turn navigation is running, the status row's stop control
  (`NavigationStateOverlay`, `IconButton` 40 dp, `content-desc="Navigation beenden"`, bounds
  `[986,2233][1049,2296]` on the 1080×2400 phone) is **not** the node the semantics tree marks as
  clickable: the only clickable node covering that band is the overlay's **outer container**
  `[0,2018][1080,2400]` (`.clickable(onClick = onClick)`, `NavigationStateOverlay.kt` line 63, which
  opens the expanded details). Tapping the stop icon's own coordinates turned **free driving** on in
  three attempts — and free driving ends navigation itself, so the session never entered `STOPPED`
  and the grace period never ran.
- **Impact** ℹ: the session's stopped state and its grace after navigation stops (spec
  `route-planning-session`) cannot be verified end-to-end on the phone; a user aiming at
  "Navigation beenden" can end up in free driving instead. The state machine itself is unit-verified
  (`RoutePanelViewModelSessionTest` + the revert-check `expected:<INACTIVE> but was:<STOPPED>`).
- **Fix candidates** ℹ: give the stop control its own semantics node
  (`clearAndSetSemantics { }`/`semantics { }` on the `IconButton`) *and* keep the container's
  `.clickable` out of the status row's hit area (the container click currently overlaps a 40 dp
  control). Then re-run the task 10.5 recipe: start navigation → tap the stop icon (verified
  clickable and small) → the panel returns with the route still drawn → the grace line
  `RoutePanelVM: session grace period expired - ending the session` ~45 s later → no overlay, no
  pill, no route.
- **Recipe notes** ℹ: `adb shell uiautomator dump /sdcard/ui-x.xml` + `adb pull /sdcard/ui-x.xml`
  works (a bare device-side `$EXTERNAL_STORAGE` argument is *not* expanded by `adb pull`); a
  clickable ancestor read from a flattened dump is not necessarily the node a tap must aim at, and
  the map/canvas screens expose almost no text nodes (`tesseract … -l deu tsv` is available for the
  ones they do expose).

## 123. Test harness: `SearchHistoryRepository` is usually built without the test dispatcher, so a test that depends on a result selection races a real-thread file write — Found 2026-10-04 while implementing `fix-search-history-chip-replay` (task 2.3, out of that change's scope)
**id:** 123 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: `MapCanvasViewModelSharedLocationTest` built its repository as
  `SearchHistoryRepository(context)` and left `defaultDispatcher` at `Dispatchers.Default`, while
  `onSearchResultSelected` records the search **before** it publishes the selection to `uiState`.
  `advanceUntilIdle()` cannot wait for a real-thread file write, so the test asserted against the
  pre-selection state. It passed for months (the write was short when no history file existed) and
  only lost the race once the repository had to *rewrite* a collapsing file — the failure looked
  like a defect in the change under test and cost an attribution round
  (`assertion: query='Bochum' results=[Bochum] details=false` — the selection had not run yet).
- **Impact** ℹ: 41 test sites in `app/src/test/java/com/naviveylin/ui/map/` construct the repository
  inline (73 sites do set `defaultDispatcher = mainDispatcherRule.dispatcher`); every test whose
  assertion follows a result selection, a favourite write or any other repository-then-state path
  is exposed to the same flake, and the failure mode mimics a real bug.
- **Fix candidates** ℹ: (1) give the repository a test-friendly default (a constructor-injected
  dispatcher resolved through Hilt, so tests must pass the test dispatcher) or have it derive the
  dispatcher from the injected scope; (2) sweep the VM test classes to set
  `repo.defaultDispatcher = mainDispatcherRule.dispatcher` like the fixed class does; (3) where a
  test needs the selection's outcome, await the observable state (`uiState.first { it.showDetailsSheet }`)
  instead of `advanceUntilIdle()`. Any of these is a test-only change; the discipline that made it
  visible is the `run-tests` attribution step (baseline green alone, red together).

## 126. A failed reroute releases the navigation lease of the guidance that is still running — Found 2026-10-04 while implementing `show-route-calculation-progress` (task 2.4, out of that change's scope)
**id:** 126 · **category:** route-and-navigation · **class:** improvement · **status:** fixed-by `fix-reroute-lease-release` — the code half is landed and unit-pinned (two revert-checks, 2026-10-06); the on-device half is blocked (no device, no `emulator` binary)

- **Observed** ✗: `NavigationEngine.confirmReroute` reaches `calculateAndStart(fromReroute = true)`, whose
  `onError` (and its synchronous-catch twin) calls `releaseNavLease()`. During a reroute `isNavigating` is
  still true and the lease belongs to the running navigation (`startInternal` → `acquireNavLease`), so a
  reroute that fails — a disconnected map edge, a missing destination node, a thin map set — drops the
  navigation-scoped lease while guidance continues. `LocationService` stops device updates when the last
  lease goes, so the map and the navigation engine keep working from the last fix until something else
  takes a lease (`TODO.md` §106's AVD reproduced route failures with exactly such an error message:
  "No routable node near destination").
- **Why it was left** ✗: it is a pre-existing defect on a path this change only passes through. The fix is
  small and now has a seam (`cancelAcquisition` in the same change releases the lease only while
  `!isNavigating`), but changing *when a failure releases the lease* touches the reroute-failure UX
  (the driver must still be told) and wants its own verification on a reroute that fails while navigating.
- **Fix candidate**: release the lease on a failed acquisition only when the attempt owned it — the
  surface-less acquisition (`!isNavigating`), exactly as `cancelAcquisition` does — and add a test that a
  failed reroute leaves `heldLeaseConsumers()` holding `nav-engine`.
- **Related** ℹ: `show-route-calculation-progress` (engine task 2.4) added the guard for the cancel path;
  the failure path was left alone.
- **Fix 2026-10-06 — change `fix-reroute-lease-release`** ✅: `calculateAndStart` now captures
  `ownsLease = !_state.value.isNavigating` before its native call (`NavigationEngine.kt:448`) and **both**
  failure handlers release the lease only when the attempt owned it (`:488` `onError`, `:514` the synchronous
  `catch`), the same rule `cancelAcquisition` already applied (`:550-565`). The failure is still published
  (`errorMessage` + `SurfaceOrigin.ENGINE`), so the driver is told exactly as before — only the release is
  gated. Spec: `navigation-engine` — new requirement "A failed route attempt releases only the lease it
  took" with one scenario per direction. Cases: `NavigationEngineRerouteTest.aFailedRerouteKeepsTheRunningNavigationLease`
  (drives the production reroute entry, holds the calculation, fails it → `heldLeaseConsumers()` still holds
  `nav-engine`, `isNavigating` true, the error published) and
  `NavigationEngineCalculationCancelTest.aFailedSurfaceLessAcquisitionReleasesItsOwnLease` (the boundary that
  keeps the rule from being "never release on failure"). Both falsified once: mutating the `onError` site back
  to an unconditional release fails the reroute case (`java.lang.AssertionError: a failed reroute must not
  starve the running guidance of position updates`, `tests="9" failures="1"`), mutating `ownsLease` to a
  constant `false` fails the control. Restored and forced green; both-flavor gate green
  (mobile/automotive 1721 tests each, `:auto` 781, `:core` 447, JNI 26, `buildSrc` 113, 0 failures, 0
  warnings). Rule recorded in `guidelines/Design.md` §4.
- **Still owed** ⏳: the device half — a reroute that fails while guidance is live, read via
  `adb logcat -s NaviVeylin` (expect **no** `location lease release: nav-engine` in the window). Not run:
  `adb devices` empty and no `emulator` binary in this session; the blocker is recorded rather than implied
  as proof (`guidelines/Build.md` §10).
- **Surfaced by the fix, filed as its own entry** ℹ: a failed reroute leaves `isRerouting`/`isOffRoute` set
  until the next instruction list — `TODO.md` §144.

## 127. `LoadingScreen` is production-dead and now has a sibling wait notice — Found 2026-10-04 while implementing `show-route-calculation-progress` (task 3.1)
**id:** 127 · **category:** car · **class:** improvement · **status:** open

- **Observed** ℹ: `SafeScreen.kt`'s `LoadingScreen` ("Loading map data…" / "Preparing navigation") has no
  production caller — only `StartupScreensTest` constructs it; `NavigationSession` serves the real root
  screen instead (its own KDoc says the screen "remains available for temporary/overlay use").
  `show-route-calculation-progress` then added `RouteCalculatingScreen`, a second transient
  `PaneTemplate` wait notice with the same shape plus a percentage and a cancel action.
- **Why it was left** ✗: retiring `LoadingScreen` (or folding it into the calculation notice as the one
  wait-screen implementation) is a cleanup outside the change's scope; the new screen's strings, guards
  and tests would have to be re-pointed in the same step.
- **Fix candidate**: delete `LoadingScreen` and its test case, or make it the single wait-notice base
  (message + optional action) that `RouteCalculatingScreen` builds on. Keep `guidelines/UI.md` §3c as the
  rule either way.

## 128. `NavigationEngineAcquisitionTest` pins `@Config(sdk = [34])` although the project's classloader rule forbids it — Found 2026-10-04 while adding `NavigationEngineCalculationStateTest`
**id:** 128 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ⚠: `app/src/test/.../NavigationEngineAcquisitionTest.kt:30` carries `@Config(sdk = [34])`
  while instantiating `FakeOSMScoutClient`, the combination `AGENTS.md` forbids ("do NOT set
  `@Config(sdk=…)` … a different sandbox gets its own classloader and the stub .so can only load in one").
  Every other engine test in the same package uses the default sandbox. The suite is green today, so the
  collision the rule describes (a second sandbox in the same JVM failing with "already loaded in another
  classloader") is currently only latent — it depends on test-class execution order and on how the
  forked JVMs are split.
- **Why it was left** ✗: out of this change's scope, and un-pinning the sandbox may surface a real
  classloader conflict that then needs a fork policy decision in the test configuration.
- **Fix candidate**: drop the `@Config` (as the newer engine tests do) and run the full `:app` test task
  with forced re-execution to prove no sandbox collision; if it fails, record the sandbox/classloader
  contract for the JNI stub as a build rule rather than per-class config.

---

## 129. The router's overall route distance and the route description's node distances disagree, by 34 % on a 70 km route — Found 2026-10-05 while applying `fix-step-leg-distance-and-time` (device run, task 5.1)
**id:** 129 · **category:** route-and-navigation · **class:** bug · **status:** open

- **Observed** ⚠: two native numbers describe the same route and do not match. The instrumented check
  `RouteInstructionPositionDeviceTest.perStepValuesAreTheStepsOwnLegs` on the AAOS AVD (`emulator-5556`,
  `nordrhein-westfalen-27-20260820-0826`, candidate chain that resolved to Dortmund Hbf → Cologne Hbf)
  measured the per-step legs at **97 416 m** while `RouteEntry.distance` (the router's
  `GetOverallDistance()`) reported **72 771 m** — a ratio of **1.34**. The legs telescope to the route
  description's last cumulative distance (`RoutePostprocessor::DistanceAndTimePostprocessor` sums
  `GetEllipsoidalDistance` between consecutive route nodes), so the description's total is the larger of
  the two. Both are now user-visible side by side: the phone's card header shows `routeEntry.distance`
  (`RouteReadyPill`/header statistics) while the step list sums the legs.
- **Why it surfaced now** ℹ: before `fix-step-leg-distance-and-time` each step's bracket was the *last
  geometry edge* before its manoeuvre (a few hundred metres in total), so nobody ever compared the two
  totals. The change makes the rows legs and the discrepancy visible — it is not caused by it.
- **Impact** ℹ: the header and the sum of the steps disagree (34 % is far beyond rounding, and on a
  short route the start/target sections make the relative gap larger still). The route's real length is
  whatever the map data and the routing profile say, so one of the two sources is wrong — a caller that
  relies on either number (the card, the car's trip summary, the progress line's denominator) inherits it.
- **Fix candidate** ℹ: measure both numbers against a known reference (the GPX of a driven route, or
  `osmscout`'s own `Demos/src/Routing.cpp` output for the same two coordinates, which prints the
  description's cumulative distance per node), then make the bridge publish **one** length: either
  compute `RouteEntry.distance` from the description's last node (which is what the step list sums to) or
  find why the description's node-to-node sum exceeds the router's accumulated distance (a node whose
  `GetCurrentNodeIndex()` resolves against a different way would produce exactly such inflation). Note in
  `guidelines/` which of the two is the route's length; the app's `stepValuesDiverge` threshold (50 %) and
  the device test's `ratio > 0.5` are deliberately loose until this is settled.

---

## 130. The description's first node and the route polyline's first point sit ~200 m apart on a long route — Found 2026-10-05 while applying `fix-step-leg-distance-and-time` (device run, task 5.2)
**id:** 130 · **category:** route-and-navigation · **class:** bug · **status:** on-hold start-point authority (requested position vs snapped node) · needs automotive AVD

- **Blocked** ⏳ (bug-fix loop 2026-10-09, `bugfix-loop` iteration 3 — gate conditions 3 and 6 fail):
  host-decidable = **no**. Both arrays are native outputs — `instructionLats` is filled from the first
  description node (`OSMScoutClient.cpp:6241`, `:6249`), `latitudes` from `TransformRouteDataToPoints`
  (`:6463`, `:6481-6489`, geometry `AbstractRoutingService.cpp:1854-1859`) — and host JVM tests load the stub
  `app/src/test/jniLibs/libosmscout_client_java.so` with `FakeOSMScoutClient`, so a host fixture would
  hardcode both arrays and assert only the consumer, never the divergence. Condition 3 fails as well: this
  entry's own fix candidate is a **choice** ("publish the polyline's start as the first instruction's
  position (or the description's node, consistently)"), i.e. an unapproved product decision. The cited ~198 m
  is **not retained**: it came from a device JUnit XML on the automotive AVD `emulator-5556`, not attached
  today (`adb devices` → only `emulator-5554`).
- **Related** ℹ: **§131** already explains a ~200 m gap as the offset between two consecutive instructions
  (not step 0 vs `latitudes[0]`) — read §131 before re-diagnosing, and do not open a second entry for one
  measurement. The seam that would make this closable: a host-runnable route calculation over a test database
  (submodule C++/ctest level — not wired into this repo's Gradle gate) or a recorded real `RouteEntry` checked
  in as evidence.

- **Observed** ⚠: `RouteInstructionPositionDeviceTest.analysedStepSegmentsStayMonotonicAndOnThePolyline`
  reported `step 1's segment starts 198 m away from its manoeuvre` on the AAOS AVD (`emulator-5556`,
  NRW database, the candidate chain resolved to Dortmund Hbf → Cologne Hbf, ~70 km). Step 0 is the
  route's start line, so its segment starts at the polyline's first vertex — i.e. the start line's
  manoeuvre position (from the route description's first node, `instructionLats[0]`) is 198 m from the
  polyline's first point (`RouteEntry.latitudes[0]`, from `TransformRouteDataToPoints`). The case's own
  bound is 60 m; every later leg stays inside it.
- **Why it surfaced now** ℹ: two effects, neither from the change being applied. (1) The test's map
  discovery only looked one level below `files/maps`, so on a device whose region was downloaded
  through the app (`files/maps/europe/germany/nordrhein-westfalen-<version>`) every case failed earlier
  with "No databases loaded" and the geometric assertions never ran; that discovery is now recursive.
  (2) The route it then resolved is a long intercity one, where the start section is large.
- **Impact** ℹ: the first leg of a long route is drawn/measured from the polyline's first point, which
  can be ~200 m from where the description says the route starts; analysis of step 0 therefore
  highlights a leg whose start is off the manoeuvre it names. The app's own `instructionAnchors` and
  `stepSegments` are unchanged by the fix and inherit it. The device case now allows 300 m for the
  first leg and keeps 60 m for the rest, so the finding is recorded rather than hidden.
- **Fix candidate** ℹ: compare the description's first node with `RoutePointsResult`'s first point for
  the same route (both are available in `calculateRouteWithObjectsWithProfile`), and decide which is
  the route's start: if the description starts at the snapped routable node while the polyline starts
  at the requested position, publish the polyline's start as the first instruction's position (or the
  description's node, consistently) instead of leaving two nearly-equal sources.

---

## 131. The device-test case for `stepSegments` kept the pre-flip segment orientation and only became runnable on 2026-10-05 — Found 2026-10-05 while applying `fix-step-leg-distance-and-time` (device run, task 5.2)
**id:** 131 · **category:** verification · **class:** bug · **status:** in-flight fix-step-leg-distance-and-time

- **Observed** ⚠: `RouteInstructionPositionDeviceTest.analysedStepSegmentsStayMonotonicAndOnThePolyline`
  asserted that a step's segment **starts** at the step's own manoeuvre (`lats[range.first]` against
  `anchors[index]`). That held while a step owned the leg *after* its manoeuvre; `route-planning-session`
  (task 12.12, 2026-10-03) corrected the orientation to the leg **leading to** it (`stepSegments` returns
  `previousVertex..ownVertex`), and the assertion was not updated with it. It was invisible because the
  case could not run on an install whose region was downloaded through the app: the test only looked one
  level below `files/maps`, found `europe` (a directory, not a database) and failed earlier with
  "No databases loaded". With the discovery made recursive it reported `step 1's segment starts 198 m away
  from its manoeuvre` — the *previous* manoeuvre's vertex measured against this step's anchor, i.e. the
  offset between two different instructions, not a geometry error.
- **Fix** ✓ (in this change): the case now checks the segment's **last** vertex against the step's own
  manoeuvre and its **first** vertex against the previous step's, both within the 60 m bound. The
  observation that motivated the wrong reading — the description's first node sitting ~200 m from the
  polyline's first point on a long route — is real and is filed as its own finding (TODO.md §130).
- **Why it is listed here** ℹ: a device case that cannot run is not a guard. The recursive discovery and
  the corrected orientation belong together, and the next device run should be read as "the case ran"
  (`tests="4"`, no skip) rather than as "green".

## 132. The declared fork counts are tuned for a single-suite run, but the full gate runs every module's suite in one invocation — Found 2026-10-05 while applying `speed-up-build-test-gate` (task 8.4)
**id:** 132 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: with `maxParallelForks = 2` declared for `:app` (change `speed-up-build-test-gate`, spec
  `unit-test-suite-runtime`), one suite alone in a clean window takes 2m02s — but in the documented full gate
  (`test -PforceTests --no-build-cache`, all four modules in ONE invocation) the same suites take **4m54s**
  (`:app` mobile) and **3m56s** (`:app` automotive) while `:auto`/`:core`/JNI run alongside. Measured
  2026-10-05 17:47-17:57 on an idle machine (4.4 GB RAM free): 12m00s of test-task work in a 9m46s wall, i.e.
  the suites overlap, and 2+2+1 test JVMs plus the Gradle daemon contend for CPU and for memory on a 15.6 GB
  box. The isolated numbers and the aggregate are not comparable, and the per-suite table in
  `guidelines/Build.md` §6 does not say which one it describes.
- **Impact** ⚠: anyone quoting "2m02s per `:app` suite" for a gate decision is off by ~2.5×; conversely the
  fork declaration is a *concurrency budget* that is only correct for the invocation it was measured in. The
  aggregate gate is bounded by suite work, not by the levers this change added, so the change's headline
  improvement applies to iteration, not to the full gate.
- **Fix candidates** ℹ: (a) run the full gate as one invocation per module (serialising the suites, so the
  declared forks are the only concurrency), (b) re-measure and declare forks for the all-modules invocation —
  possibly 1 for `:app` when `:auto`/`:core` share the graph, (c) cap concurrency with `--max-workers` so the
  test JVMs do not outrun the memory ceiling. Whichever is chosen: state in §6 which invocation shape the
  numbers describe, and record peak memory alongside wall time (a heap-bound box swaps, and every suite slows
  down non-linearly).
- **Confounds to control in the follow-up measurement** ℹ: three variables differ between the 2m02s reading and
  the 4m54s reading besides cross-module overlap — (1) the Kover agent was attached in the full gate and
  detached in the isolated runs (worth ~10-12 %, §7 of `guidelines/Build.md`), (2) free memory was ~8.2 GB in
  the isolated window and ~4.4 GB in the gate window, and (3) the full gate ran `:core`/`:auto`/`:app` suites
  in parallel while the isolated runs had the machine to themselves. Any decision needs all three fixed while
  the invocation shape varies, and peak RSS sampled per suite. Also note the consequence for the recipes:
  changing the gate to one invocation per module changes what CI does, and `build-test-gate` ("Local and CI
  gate recipes agree") then requires the workflow to follow — either CI splits the same way or the divergence
  is a documented, spec-sanctioned exception, not an oversight.

## 133. A route that finishes just after the notice delay flashes the car notice for a few hundred milliseconds — Found 2026-10-05 while measuring the delay of `show-route-calculation-progress` (task 5.3)
**id:** 133 · **category:** car · **class:** improvement · **status:** open

- **Observed** ℹ: measured on the AAOS AVD (automotive `x86_64`, North Rhine-Westphalia database): a 2 km
  town route took **847 ms**. The calculation notice is pushed after `CALCULATION_NOTICE_DELAY_MS` (400 ms),
  so this route pushed the notice at 18:10:38.668 and the navigation view replaced it at 18:10:39.111 —
  **~443 ms of notice**. Longer routes (24.6 s, 28.7 s) are unaffected; no successful route below 847 ms was
  observed in the samples, and the failed one (1961 ms) shows no notice at all.
- **Why it was left** ✗: the band looked empty during exploration of the change, so the owner declined the
  minimum-visible dwell (design D2) to avoid taxing fast routes with latency. The measurement says the band
  *is* populated, and the fix (once the notice is up, keep it for a minimum time — e.g. 700 ms — before the
  navigation view may replace it) changes when the navigation view appears, which deserves its own
  verification (a route in the 0.4–1.2 s range, plus a reroute where the notice is not cancellable).
- **Fix candidate**: add a minimum-visible dwell to the notice's removal path in `NavigationSession` (the
  removal is currently immediate on the state change), gate it on the notice actually having been pushed,
  and re-measure the same 2 km route so the dwell is proven to remove the flash instead of adding latency.
- **Related** ℹ: `show-route-calculation-progress` tasks 5.2/5.3 carry the measurements;
  `guidelines/UI.md` §3c is the wait-notice rule the dwell would extend.
- **Note** ℹ: filed as §129 on 2026-10-05 and renumbered to §133 the same day — the concurrent
  `fix-step-leg-distance-and-time` session had taken §129 (per `TODO.md`'s id-collision rule).

## 136. The docked wide panel shows the anchor toggle, which cannot change its size — Found 2026-10-05 while applying `fix-route-session-exit` (design D1, Open Questions)
**id:** 136 · **category:** ui · **class:** improvement · **status:** open

- **Observed** ℹ: `RoutePanel`'s title row (`RoutePanel.kt`, the `^`/`v` collapse control) is part of the
  shared `body(docked)` content, so a wide surface (>tall) renders it inside the docked side panel as
  well. The anchor it sets only feeds `phoneCardHeightDp`, which the docked frame never calls — so on a
  tablet, foldable or landscape phone the control looks live and does nothing visible (it only changes a
  value the panel does not use). The `X` beside it also ends the session from the docked panel, which is
  intended.
- **Why deferred** ✗: this change's scope is the phone card's exit (`fix-route-session-exit`); hiding a
  control in the docked panel is a visible wide-layout change with its own parity question (the spec says
  the docked panel presents the same content and actions as the expanded phone anchor), so it needs its
  own decision rather than a silent ride-along.
- **Fix candidate** ℹ: either hide the anchor toggle when `docked`, or give the docked panel a real
  width/size control; either way update spec `route-planning-session` ("Overlay docks when width allows"
  — same content and actions) and `guidelines/UI.md` §537 in the same change.
- **Not caught** ✗: no test composes the docked frame (`useDockedPanel` is unit-tested as a pure
  predicate, and the card tests use a portrait window), so a no-op control in the wide layout has no
  assertion either way.
- **Note** ℹ: filed as §134 on 2026-10-05 and renumbered to §136 the same session — a concurrent
  session took §134 (`process-failure-log` guardrails) while this change was being applied.

## 138. At font scale 2.0 the max card's labelled End action is clipped off the screen, which leaves the header close as the only visible exit — Found 2026-10-05 while applying `fix-route-session-exit` (device measurement, task 4.3)
**id:** 138 · **category:** ui · **class:** bug · **status:** fixed-by `fix-pinned-band-height`

- **Observed** ℹ: same flow, same route, two font scales on the 1080x2400 emulator (`Bürgermeister-Smidt-Straße`
  → Hauptbahnhof): the card occupies `[0,1320][1080,2400]` (1080 px = 45 % of the screen) at both scales. At
  **1.0** the labelled `Analyse beenden` is on screen (`bounds=[401,2233][680,2254]`, header close
  `[944,1363][1007,1426]`). At **2.0** the action row's labels sit at `[105,2106][466,2211]` / `[613,2106][975,2211]`
  and `Analyse beenden` is **absent from the whole UI dump** — the pinned band (row + the End button below it)
  overflows the card's 45 % cap at the screen's bottom edge, so the button is drawn below y=2400. The header's
  close control stays reachable (`[944,1398][1007,1461]`).
- **Impact** ℹ: the spec's explicit labelled End action ("Session lifetime and its only exits" — both card
  states) is not visible at large font scale, so the exit depends on one icon-only control. Before
  `fix-route-session-exit` that icon minimised onto an affordance with no exit, i.e. exactly the trap the owner
  reported; after it the icon ends the session, so the flow is escapable — but the discoverable labelled action
  is still missing for accessibility-scale users.
- **Fix candidate** ℹ: let the pinned band participate in the card's height budget (band height measured, not the
  fixed `ACTIONS_BAND_DP = 120f`), cap the band's label with a smaller typography at large scale, or place the
  End action inside the scroll region. Measure again at font scale 1.0/1.3/2.0 and assert the labelled action's
  bounds are inside `[0,1320][1080,2400]`.
- **Not caught** ✗: no test asserts the action band's *visibility* at any font scale — `RoutePanelComposeTest`
  asserts `assertExists()` on the tags, which a node drawn off-screen still satisfies in the compose test
  harness; a `getBoundsInRoot()` assertion against the card's bounds would be the missing case.
- **Note** ℹ: the card band property has no pixel detector today — `tools/measure-highlight.py` finds the
  analysed-segment highlight by its casing colour, so the band evidence here is UI-dump geometry (numbers,
  coordinate-free) rather than a pixel verdict.

## 139. The router's overall distance under-counts the route it produced — by 25 % on a long route and 45 % on a short one — Found 2026-10-05 while measuring `fix-route-length-disagreement` (the native half of `TODO.md` §129)
**id:** 139 · **category:** route-and-navigation · **class:** bug · **status:** open

- **Observed** ⚠: `GetOverallDistance()` on a successful route reports a distance that departs from the
  geometry the same routing produced. Measured on `emulator-5554` (Pixel_8 AVD, `nordrhein-westfalen` data,
  case `RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute`), with the length of
  the published polyline (`RouteEntry.latitudes`/`longitudes`, great-circle distance between consecutive
  vertices) as the witness — it uses neither native figure:

  | route | router total | drawn polyline | router / polyline |
  |---|---|---|---|
  | Dortmund Hbf -> Cologne Hbf | 72 771 m | 97 283 m | 0.748 |
  | Dortmund Hbf -> Bochum Hbf | 16 677 m | 20 966 m | 0.795 |
  | short hop within Dortmund | 824 m | 1 493 m | 0.552 |

  The description's own total tracked that polyline to within 0.4 % on all three routes (1.0014 / 1.0021 /
  0.9964), so the defect is on the router's side. The relative error grows as the route shortens
  (0.748 -> 0.795 -> 0.552), which rules out both a fixed ratio and a fixed absolute offset as its
  explanation; the absolute deficits are 24.5 km / 4.3 km / 0.7 km. External check: the real road distance
  Dortmund Hbf -> Cologne Hbf via A1/A45 is ~95-100 km, matching the drawn 97.3 km; 72.8 km is close to the
  ~66 km great-circle distance, i.e. implausible for a road route.
- **Why it is filed rather than fixed** ℹ: `fix-route-length-disagreement` (2026-10-05) made the description's
  total the route's length, so the app no longer reads this figure — the card statistic, the step list, the
  progress denominator and the car trip now agree on one number (spec `osmscout-jni` — One route length for a
  calculated route). The router's figure survives as the fallback for a route whose description produced
  nothing, and any future consumer of `GetOverallDistance()` would inherit the under-count unseen, which is
  why it is recorded here rather than left in that change's artifacts.
- **Fix candidate** ℹ: find where `RouteResult` accumulates its distance over a multi-way route — the deficit
  is neither proportional to the route nor constant, so a segment or node whose distance never contributes is
  the shape to look for. Compare against the description's per-node accumulation for the same route, which
  the case above already prints (three routes per run). Submodule `naviveylin-local`, pushed 2026-10-05 at
  `96fb43a20`.
- **Related** ℹ: `TODO.md` §129 is the user-visible half (one route with two totals); this entry is the native
  half. Neither of the two device cases in the change asserts the router's figure any more, so the numbers
  above are the evidence of record.

---

## 141. `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher` failed once under the forced gate and was green on its rerun — Found 2026-10-06 while gating `fix-route-session-stop-path`
**id:** 141 · **category:** verification · **class:** improvement · **status:** open

- **Observed** ⚠: in a forced focused run (`./gradlew :app:testMobileDebugUnitTest -PforceTests
  --no-build-cache -PnoCoverage --tests "com.naviveylin.ui.map.MapCanvasViewModel*" …`) the case failed with
  "the fix-quality tick dispatched on the main dispatcher from another thread **1** times in the second
  300 ms window while nothing changed (first window: 1)", the dispatch coming from
  `NavigationEngine.tickStaleness` → `runStaleSpeedTicker`.
- **Rerun evidence** ℹ: the case alone, same invocation → `BUILD SUCCESSFUL`, and it is green in the
  change's both-flavor gates (`testMobileDebugUnitTest`/`testAutomotiveDebugUnitTest`: 1717 tests,
  0 failures each). **Second occurrence** the same day (two forced focused runs of
  `MapCanvasViewModel*`, both failed the same way and both green on the immediate rerun): a rate of
  2/4 observed in focused runs, which is rule §83's point — a flake needs N ≥ 3 per configuration before
  a bisect (or a "pre-existing" label) means anything. The change under test touched neither the ticker
  nor the fix-quality pipeline (its files are the stop path, the session surface and the mode snapshot),
  so no subject-code change is implicated.
- **Why it still matters** ✗: it appears as a failure inside a *focused* `MapCanvasViewModel*` run, where a
  reader takes it for a regression of the change being gated — the silent-retry case rule §38 forbids.
- **Fix candidate**: the assertion counts main-dispatcher dispatches in two consecutive 300 ms windows and
  requires the second to be 0; a loaded runner that lands the first window's tick late makes that a race on
  the *window boundary* rather than on the subject. Either key the count to a tick the test itself provokes
  (`tickStaleness` is reachable directly, as the sibling cases show) or require "no dispatch per window"
  across N ≥ 3 windows (rule §83) and record the rate.

---

## 142. A viewport-restore case costs 6.2 s although it contains no wait — the class's remaining cost sits outside its own waits — Found 2026-10-06 while applying `speed-up-test-iteration` (task 2.5, out of that change's scope)
**id:** 142 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: task 2.5 removed `MapCanvasViewModelViewportRestoreTest`'s bounded real-clock polls
  (`awaitHeldBlock`, a `System.currentTimeMillis()` deadline loop with `Thread.sleep(10)`) and its three
  `Thread.sleep` sites, replacing them with `driveUntil` asserts on the test scheduler and pinning every
  dispatcher the restore path hops through. The class now measures **7.22 s** of class time against a
  **16.083 s** baseline taken minutes earlier on the same machine, and five of its six cases run in
  0.15-0.24 s (`initMap renders at restored viewport`: **5.549 s → 0.233 s**). But
  `saveViewport during re-entry window keeps persisted viewport` still costs **6.2 s** — and the file now
  contains no `Thread.sleep`, no `System.currentTimeMillis()` and no deadline loop at all.
- **Baseline for the same case** ℹ: 7.996 s measured on this machine in the same session *before* the
  conversion, 3.762 s in the 05:00 suite run, i.e. the cost is pre-existing and load-sensitive — it is not
  created by the conversion and not detected by the wall-clock-wait scan (which by design only refuses
  sleeps and system-clock deadline loops).
- **Why it was left** ✗: it is not a wait the change is chartered to convert (its own waits are gone), the
  change's task 2.5 measured and recorded the improvement, and attributing the remainder needs the same
  phase instrumentation the change used elsewhere — out of scope for this change's tasks.
- **Fix candidate**: instrument that one case by phase (its two `initMap` calls, `saveViewport`,
  `viewportStorage.load("mapB")`, and `runTest`/teardown) and name which one consumes the 6.2 s. Most
  likely candidates: a real-thread wait inside `ViewportStorage` despite the pinned `ioDispatcher`, the
  renderer's teardown cancel waiting on in-flight work (`MapRenderer`'s retry path carries a real
  `delay(100)`), or a JNI render still running on a real thread. Then give that component a seam the case
  owns, exactly as this change did for the ViewModel's clock and the renderer's scope.

---

## 143. Cases that await a real route calculation cost 5-9 s today, though the same cases measured 0.05 s on the quiet machine — Found 2026-10-06 while applying `speed-up-test-iteration` (task 3.2)
**id:** 143 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: converting the `:app` wall-clock waits showed that in five different classes the slowest
  case is always the one that calls `navVm.acquire(...)` and awaits navigation becoming active, and that it
  now costs seconds where it used to cost milliseconds. Focused forced runs today
  (`-PforceTests --no-build-cache -PnoCoverage`, per-case `time=` from the result XML):
  `MapCanvasViewModelModeTest.navigationEndRestoresBrowseMode` **5.572 s alone** and 8.364 s inside its
  class (the 05:00 suite measured 0.047 s), `MapCanvasViewModelNavEndRestoreTest.freeDriveBeforeNav…`
  **8.308 s** (0.055 s at 05:00), `MapCanvasViewModelAutoZoomCommitTest.first commit jumps…` **8.5 s**,
  `MapCanvasViewModelSingleFollowCenterTest.display active - fix renders…` **8.6 s**,
  `MapCanvasViewModelViewportRestoreTest.saveViewport during re-entry window…` **6.2 s**. Sibling cases in
  those same classes measure 0.06-0.1 s, so this is neither JVM/Robolectric warm-up nor case interaction —
  the case alone still costs 5.57 s.
- **Where the time is not** ℹ: the case bodies were instrumented. `MapCanvasViewModelAutoZoomCommitTest`
  reports `nav=1 ms inject=2 ms pump=59 ms` while its reported time is 6.27 s, i.e. the seconds sit outside
  the body, in the await of the engine's real work (route calculation on real dispatchers with native
  lookups). A baseline check of the *unconverted* class in the same session gave 18.337 s for the class
  against 9.42 s after conversion, so the conversion did not create the cost.
- **Why it was left** ✗: it is not a wall-clock wait in the test source (no sleep, no clock deadline), so the
  check this change adds does not see it, and attributing it needs the engine/client path rather than the
  test that awaits it. The change's task 3.2 recorded the conversions and their measured class times.
- **Fix candidate**: time the `acquire` → `isNavigating` transition and the client's route call to find where
  the seconds go — candidates are a queue/debounce in the engine's own dispatcher path (its timing seam is
  injectable since `navigation-engine`), the synthetic route path in `FakeOSMScoutClient`, or plain host CPU
  starvation (this machine is shared and was busy all session). If it is engine timing, the same cure as
  `speed-up-test-iteration` applies: a seam the case drives instead of a wait.

## 147. `measure-highlight.py` returns `inside` on a frame with no route — 18 pixels of parking-glyph colour pass its dense-run test — Found 2026-10-06 while applying `screenshot-evidence-via-view-image` (device measurement, task 4.1)
**id:** 147 · **category:** verification · **class:** bug · **status:** open

- **Note on the id** ℹ: filed as §143 on 2026-10-06; `speed-up-test-iteration` filed its route-cost finding as §143
  the same day and keeps that id (the cluster index and `guidelines/Build.md:612` cite it), so this duplicate took
  the next free id — ids are never renumbered once referenced (`TODO.md` legend).

- **Observation** ✓: on a phone frame that shows no route and no analysed segment, the script printed
  `highlight (dark): bbox=[619, 641, 413, 734] px=18` / `canvas=1080x2400 band=[0,2400] margin=126` /
  `verdict: inside`, exit 0. The same frame looked at with `view_image` is an ordinary map (a red primary
  road `L 684`, light-blue `P` parking glyphs, bicycle icons); a crop of the reported box contains nothing
  but those glyphs.
- **Why the guard missed it**: the detector's dense-run test (`MIN_RUN_PX`) was written for the dark-mode
  casing colour appearing on map *labels*; the light `P` parking glyph and its area fill also land on
  `#E0F7FA`, and 18 pixels spread over a wide box are enough to pass. `band=[0,2400]` was the visible
  tell — `--dump` found no card top, so the frame was not in the state the detector assumes.
- **Why it matters**: `verdict: inside` reads as a measurement, and any consumer that records it as
  evidence would record a false positive. The look-first step is what caught it.
- **Fix candidates**: require the route/precondition explicitly (a measured card top inside the canvas,
  i.e. `band_bottom < canvas height`, or an `inside=` line from `MapCanvasVM` in the same moment), and
  refuse with a distinct exit code/verdict (e.g. 3 `no analysed segment`) instead of reporting `inside`
  when no highlight of plausible size was found — a real highlight is a many-row stroke, so a total
  `px` floor or a per-row run longer than `MIN_RUN_PX` would separate the two cases.
- **Verified by**: `tools/measure-highlight.py` against `.pi/logs/view-image-check/shot.png` (captured
  2026-10-06 22:03 from `emulator-5554`) plus its own `.pi/skills/pixel-check/selftest.sh`, which covers
  inside / behind-card / no-highlight but not "no route on screen". The result **reproduces**: a second
  capture into `.pi/logs/skill-recipe-check/` on the same screen printed the identical
  `bbox=[619, 641, 413, 734] px=18` / `band=[0,2400]` / `verdict: inside`, so it is a deterministic
  colour coincidence, not noise.

---

## 144. A failed reroute leaves `isRerouting`/`isOffRoute` set until the next instruction list — Found 2026-10-06 while landing `fix-reroute-lease-release` (adjacent finding, out of that change's scope)
**id:** 144 · **category:** route-and-navigation · **class:** bug · **status:** open

- **Observed** ℹ: `confirmReroute` sets both flags when it starts an attempt
  (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:908` — `it.copy(isRerouting = true,
  isOffRoute = true)`), and the only writer that clears them is `onRouteInstructions` (`:800-805`) — i.e. a
  **successful** reroute whose new instruction list arrives. A reroute that **fails** (the `TODO.md` §126
  failure path) leaves `isRerouting = true` and `isOffRoute = true` in the shared state until the next
  `onRouteInstructions` emission, so every surface rendering the reroute/off-route indicator (spec
  `rerouting-visual-feedback`, `off-route-indicator`) keeps showing it for that whole window; on a stretch
  with no further instructions there is nothing left to clear it.
- **Why it is filed rather than fixed here** ✗: `fix-reroute-lease-release`'s subject is *which lease a
  failure releases*; clearing the attempt's own state flags is the failure path's second half, with its own
  observable (which surface shows what, for how long) and its own decision — a failed reroute should stop
  claiming `isRerouting`, while `isOffRoute` may genuinely still be true (the vehicle is still off route) and
  is better left to the native position reports.
- **Fix candidate**: in both failure handlers of `calculateAndStart`, clear the flag the attempt itself set
  (`isRerouting = false`; leave `isOffRoute` to the native reports, or clear it and let the next
  `onPositionEstimate` re-set it), and pin it with a case beside
  `NavigationEngineRerouteTest.aFailedRerouteKeepsTheRunningNavigationLease`.
- **Not caught** ✗: no case asserts the state flags after a *failed* reroute — the reroute suite's
  instruction case (`instructionListUpdatesAfterAReroute`) covers the success path only, where
  `onRouteInstructions` clears them anyway.
- **Related** ℹ: `TODO.md` §126 — the same failure path's lease release, fixed by `fix-reroute-lease-release`;
  the fix's own case (`aFailedRerouteKeepsTheRunningNavigationLease`) does not assert these flags, and
  deliberately does not, because the change's delta says nothing about them.

---

## 145. `gradle.properties` commits a machine-local JDK path that every other machine must override — Found 2026-10-06 while implementing `fix-daemon-jvm-provisioning` (adjacent finding, out of that change's scope)
**id:** 145 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: `gradle.properties` carries `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk` under the
  comment "Java home for Gradle daemon (AGP 8.7+ requires Java 17+)". A machine-local absolute path in a
  versioned file is wrong on every other machine: CI must pass `-Dorg.gradle.java.home="$JAVA_HOME"` and
  write the same property into `local.properties` (`.github/workflows/build.yml`, steps *Write
  local.properties* and *Build debug APK*), while `setup-vcpkg.sh` reads that property — so the committed
  value is load-bearing for the native JDK lookup yet correct only on the machine that committed it.
- **Observed, second half** ℹ: the comment's claim is the opposite of the behaviour
  `fix-daemon-jvm-provisioning` documented: the checked-in daemon JVM criteria
  (`gradle/gradle-daemon-jvm.properties`) take precedence over `org.gradle.java.home` and `JAVA_HOME`
  (Gradle 9.6.1 manual, *Daemon JVM Toolchains*), so this line no longer selects the daemon JVM at all —
  even where the path exists.
- **Fix candidate**: drop the line and let each machine and CI pass its JDK explicitly
  (`-Dorg.gradle.java.home` / `JAVA_HOME`, which `setup-vcpkg.sh` already tolerates), or keep it and correct
  the comment.
- **Related** ℹ: `guidelines/Build.md` §2, "The build JVM (daemon JVM criteria)" (change
  `fix-daemon-jvm-provisioning`) records that rule; the local JDK 17 is a *different* requirement — see §146.

---

## 146. `buildSrc` requires a Java 17 toolchain it cannot provision, so a machine with only JDK 21 fails at configuration — Found 2026-10-06 while implementing `fix-daemon-jvm-provisioning` (probe finding, out of that change's scope)
**id:** 146 · **category:** build-and-harness · **class:** bug · **status:** on-hold pin 17 + provision it vs move buildSrc onto the daemon JDK

- **Observed** ℹ: `buildSrc/build.gradle.kts:13-14` declares
  `toolchain { languageVersion = JavaLanguageVersion.of(17) }`, and `buildSrc/` has **no**
  `settings.gradle.kts`. The toolchain download repository comes from the *main* `settings.gradle.kts:9`
  (`org.gradle.toolchains.foojay-resolver-convention` 1.0.0), which does not apply to the buildSrc build, so
  buildSrc can only **auto-detect** a local JDK 17.
- **Reproduced** ℹ: `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` fails in 699 ms with
  "Failed to calculate the value of task ':buildSrc:compileJava' property 'javaCompiler' → Cannot find a Java
  installation on your machine (Linux … amd64) matching: {languageVersion=17, vendor=any vendor,
  implementation=vendor-specific, nativeImageCapable=false}. Toolchain download repositories have not been
  configured." Disabling auto-detection is equivalent to the real condition for this requirement — no JDK 17
  is findable — and the run stops before any task, including `help`, with a message that names neither the
  file nor the fix.
- **Why it is filed rather than fixed here** ✗: `fix-daemon-jvm-provisioning` settles which JVM runs the
  Gradle **daemon**; buildSrc's compile toolchain is a second, independent JDK requirement, and changing it
  (add `buildSrc/settings.gradle.kts` with the resolver, or raise the toolchain) changes what compiles the
  build logic — its own decision with its own evidence.
- **Fix candidate**: add `buildSrc/settings.gradle.kts` applying the same foojay-resolver-convention so a
  machine without a 17 can provision one, or state the 17 requirement in `guidelines/Build.md` §2 beside the
  daemon-JVM rule so a failing configuration is diagnosable.
- **Invisible today** ✗: no check asserts that a machine satisfying the daemon criteria can also configure
  buildSrc. CI was green on 2026-10-03 (inference: its runner had a JDK 17 for Gradle to detect), so the
  requirement stays unseen until a machine lacks one. The probe above is the only reproduction recorded.

- **Loop verdict** ⏳ (bug-fix loop 2026-10-09, iteration 10 — not eligible: gate condition 3 fails,
  `needs decision`): the claim is real in the tree — `buildSrc/build.gradle.kts:13-15` pins
  `languageVersion = JavaLanguageVersion.of(17)` and `buildSrc/` has no `settings.gradle.kts`, so the
  foojay resolver that `settings.gradle.kts:9` applies to the main build does not reach the buildSrc
  build. Re-reproduced on this machine 2026-10-09T19:04:17Z:
  `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` → `Could not resolve all
  dependencies for configuration ':buildSrc:buildScriptClasspath' … Failed to calculate the value of
  task ':buildSrc:compileJava' property 'javaCompiler'. > Cannot find a Java installation on your
  machine (Linux 7.2.9-arch1-1 amd64) matching: {languageVersion=17, vendor=any vendor,
  implementation=vendor-specific, nativeImageCapable=false}. Toolchain download repositories have not
  been configured.` → `BUILD FAILED in 827ms`. The `-D…auto-detect=false` flag is the proxy for the real
  condition ("no JDK 17 findable"): this machine is **not** JDK-21-only — the daemon runs JBR 21
  (`/home/tim/.jdks/jbr-21.0.11`, `javaToolchains` "Detected by: Current JVM") and
  `/usr/lib/jvm/java-17-openjdk` is present ("Detected by: Common Linux Locations") — which is why plain
  `./gradlew help` configures green here (exit 0) and the defect stays invisible on this workstation.
  **Why exactly one fix does not follow** (condition 3): the only authority touching a JVM pin,
  `openspec/specs/build-jvm-toolchain/spec.md`, scopes itself to the **daemon** JVM ("Defines how the
  build declares and satisfies the JVM its Gradle daemon runs on"; "The daemon JVM requirement names a
  version, not a vendor … SHALL require only a Java version (21)") and says nothing about the buildSrc
  compile toolchain — so it dictates neither "17 stays" nor "bump". The guidelines record the pin only
  as a rationale (`guidelines/Build.md:990-991` "Java 17 toolchain is pinned there so Kotlin and Java
  targets agree (a newer daemon JVM otherwise emits an inconsistent-target warning)"; `:280-282` names
  this entry as the second requirement), which three different fixes all satisfy: (a) add
  `buildSrc/settings.gradle.kts` with the foojay resolver — keeps 17, but a JDK-21-only machine then
  **downloads** a JDK 17, which the archived `fix-daemon-jvm-provisioning` was written against ("No
  routine run pays for a download"); (b) raise the pin to 21 — the daemon's own JDK 21 is already a
  detected toolchain (`javaToolchains`, "Detected by: Current JVM"), so no download, but it changes what
  compiles the build logic (the entry's own recorded caveat); (c) drop the pin — the same effect class as
  (b) without a version. Decision needed: keep the 17 pin and provision it, or move buildSrc onto the
  daemon JDK? The entry's other half ("state the 17 requirement … so a failing configuration is
  diagnosable") is documentation, not a fix, and satisfies no spec scenario. No check exists today (no CI
  step names `buildSrc`; `.github/workflows/build.yml:60-64` installs Temurin 21 only), so the later
  change owes the check that makes the chosen behaviour red/green; the host seam is a plain
  configuration-phase invocation (`./gradlew -Dorg.gradle.java.installations.auto-detect=false help`),
  which cannot decide the choice itself.

---

## 148. Timing-sensitive cases fail in the loaded aggregate test run — four distinct cases across five aggregate runs, every one of them green in a single-module or solo run — Found 2026-10-07 while landing `fix-daemon-jvm-provisioning` (out of that change's scope)
**id:** 148 · **category:** verification · **class:** bug · **status:** open

- **Observed** ℹ — four aggregate runs (the CI command `test -PforceTests --no-build-cache`, all four modules in
  one invocation) reached the test suites; all four failed, each on **one** case, and **three different
  cases** appeared:
  1. `:auto:testDebugUnitTest` → `MapScreenTest.theTapPathDoesNotResolveTheNativeClientOnTheHostThread` — CI
     attempt 1 (job `112505184659`), `781 tests completed, 1 failed` (`AssertionError at MapScreenTest.kt:198`);
  2. `:core:testDebugUnitTest` → `DiagnosticsLogWritePathTest.pendingBufferIsBoundedAndMarksTheDropOnce` — CI
     attempt 3 (job `112629042978`), `447 tests completed, 1 failed`
     (`AssertionError at DiagnosticsLogWritePathTest.kt:142`), **and** worktree run 1 below;
  3. `:auto:testDebugUnitTest` → `AutoMapRendererRenderCadenceTest.theInFlightFlagIsClearedAndTheDurationMeasuredAfterARender`
     — worktree run 2, `781 tests completed, 1 failed`.
  (CI attempt 2 never got past `:buildSrc` dependency resolution — a separate, transient failure.)
- **Reproduced in isolation, with the messages CI never uploaded** ℹ: from an isolated worktree at the same
  commit (`git worktree add --detach /tmp/nv-148 cf513f7`, submodule and `vcpkg/` symlinked in, so the shared
  tree's in-flight edits cannot interfere): `./gradlew test -PforceTests --no-build-cache`
  - run 1 (cold, 105 tasks executed, 2m15s): the `:core` case failed with
    **`java.lang.AssertionError: the ring held exactly its capacity expected:<5> but was:<10>`** — i.e. 10 of
    the 20 burst lines reached the file where the case demands exactly 5;
  - run 2 (warm, 3m00s): the `:auto` cadence case failed with
    **`java.lang.AssertionError: the in-flight flag must be cleared`**.
- **Single-module runs do not reproduce it** ℹ: five forced `:core:testDebugUnitTest` reruns (~40-60 s each) in
  that worktree were green, as were the earlier forced single-module runs in the main tree (`:core` 41/447/0,
  `:auto` 76/781/0, `:app` mobile 221/1721/0). The trigger is the loaded, parallel, multi-module run.
- **A fourth case, with a different mechanism, on 2026-10-07** ℹ (change `fix-router-overall-distance`):
  `:app:testAutomotiveDebugUnitTest` failed with `MapCanvasViewModelModeTest.cardStopRestoresBrowseMode`
  **and** `…exitFreeDriveAppliesBrowsePreset` — `java.lang.IllegalStateException: Dispatchers.Main is used
  concurrently with setting it` (221 classes, 1725 tests, 2 failures), while the **same class solo is green
  in 22 s** and the **same suite re-run is green** (221/1725/0, 1m49s): 1 of 2 suite runs red, both cases
  clean alone. The class's file was last modified by the same `3ba476b` that the other three come from (and is
  not part of the concurrent session's working tree), so the shape is the same — a case reworked into
  state-awaiting leaves work touching the JVM-wide `Dispatchers.Main` that the next case's setup then sets —
  but the mechanism is *not* the assertion-across-an-async-boundary one the other three show: this is a
  Main-dispatcher set/reset race between cases in one JVM. Treat it as one family with two mechanisms, and
  note that a suite that is red once and green on the re-run is exactly what the `build-test-gate` rule
  ("evidence must quote the executed task count and the tallies") exists to make visible.
- **Mechanism, case 2** ℹ: `DiagnosticsLog.flushNow` signals and then returns once `pending` is empty and
  nothing is being flushed (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt:305-337`), while
  `workerLoop` only waits when `pending.isEmpty()` and otherwise **drains immediately** (`:385`, `:394-402`).
  The burst of 20 lines therefore needs only to land in the window between a finished flush and the worker
  parking again: the worker drains the first 5 without waiting, the ring refills with 5, and the case's final
  `flushNow` writes those — 10 lines where the case expects 5. The in-memory bound (`maxPendingEntries`) holds
  throughout; what varies is the **file content after a drain**, which the case asserts as if it were bounded.
  The `3ba476b` rework moved this case from a `poll {}` on the file to `awaitDrained()` but kept the assumption
  its own comment states — "the worker is parked and the burst below is bounded entirely in the ring":
  `awaitDrained` returns when the buffer is empty, not when the worker is parked.
- **Mechanism, case 3** ✓ (traced 2026-10-07, closing the "partially traced" note above): `renderFrame()`
  sets `renderInFlight = true`, renders inline and clears it in `finally`
  (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt:1307-1319`). The stale-read half of the note is
  **disproved** ✗: the field **is** `@Volatile` (`:790`, as is `lastRenderDurationMs` `:798`). The whole
  mechanism is a concurrent loop render. The renderer's loops are live on
  `carScreenScope("AutoMapRenderer", Dispatchers.Default)` (`:103`) because case 3 is the **only** test in its
  file that drives the renderer without `renderer.asyncLoopsEnabled = false` (its sibling
  `theFixPathStillDrawsOneFramePerCommit` sets it, `:46`, as does every other renderer test in `:auto`), and
  `onSurfaceCreated` requests a frame (`:407`). The render collector therefore wakes ~`RENDER_DEBOUNCE_MS = 100 ms`
  (`:2198`) later and calls `renderFrame()` concurrently with the test's own call — both write the same flag, so
  it can still read `true` after the test's own render returned. The window is exactly that 100 ms: an idle host
  finishes the case inside it, a loaded one does not. **Production has no such overlap** ✓ — `renderFrame()`'s
  only non-test caller is the single, sequential render collector (`frameIteration` `:895`/`:928`) — so the fix
  is one line in the test, not a change to the renderer.
- **Case 1 is traced, message included — and it corrects this entry's method** ✓ (2026-10-07): one run of the CI
  command in the main tree (`./gradlew test -PforceTests --no-build-cache`, 3m14s, 19 of 141 tasks executed,
  `:app` automotive / `:core` / `:auto` suites live at once, no source file touched during the window, log kept as
  `.pi/nv-148-run1.log`) failed exactly this case: `:auto:testDebugUnitTest`, `782 tests completed, 1 failed`. Its
  message, which no CI log carries: **`java.util.ConcurrentModificationException`**, with
  `ArrayList$Itr.checkForComodification` reached from `AbstractCollection.toString`, thrown in
  `MapScreenTest$theTapPath…$1.invokeSuspend(MapScreenTest.kt:218)`
  (`auto/build/test-results/testDebugUnitTest/TEST-com.naviveylin.auto.MapScreenTest.xml`).
- **Where the console's line number comes from** ✓: the earlier "case 1 has the same shape" reading is **withdrawn**
  ✗ — this run printed `java.util.ConcurrentModificationException at MapScreenTest.kt:198` for an exception thrown
  at `:218`. Gradle's short location is the frame of the **test method itself**, not the throw site, so a
  `runTest`-wrapped case's location always collapses to the `runTest(...)` call and identifies no statement (case
  2's `:142` is informative only because that test's body is a plain method). CI attempt 1's
  `java.lang.AssertionError at MapScreenTest.kt:198` is therefore a **real assertion failure** of that case — the
  exception class is accurate, the line is not — and the 60 s-timeout elimination drops out as unnecessary.
- **Mechanism, case 1** ✓: `clientThreads` is a plain `mutableListOf` on the test instance
  (`auto/src/test/java/com/naviveylin/auto/MapScreenTest.kt:55`) and is appended by `clientProvider.client()` from
  **whatever thread resolves the client** (`:68-73`) — here the tap path's `withContext(Dispatchers.Default)`
  (`auto/src/main/java/com/naviveylin/auto/MapScreen.kt:800`, and the renderer init at `:296`). `advanceUntilIdle()`
  cannot own that hop (the screen takes no dispatcher parameter although `MainDispatcherRule`'s contract requires
  one), so the test thread reads the list while a real thread appends to it: `:218` either sees it still empty
  (CI's AssertionError) or the append lands inside the `"… got $clientThreads"` message's `toString()` (this run's
  CME, thrown before the assertion can report). One defect, two observed faces; the class's other awaits
  (`awaitDaylightPush`: `runBlocking` + `withTimeoutOrNull(3_000)`) are real-thread waits for the same reason.
- **Why it hides locally** ℹ: `3ba476b`'s own gate was green in 4m54s on an unloaded machine with the same case
  counts CI reports (`:app` mobile/automotive 221 each, `:auto` 76, `:core` 41), so the variable is machine
  load and parallelism, not inputs. The JVM-vendor hypothesis is now **disproved for case 2**: the worktree run
  that reproduced it used the local JBR 21 daemon, not the runner's Temurin 21.
- **Fix direction** ✗: per case, await the state instead of asserting across an asynchronous boundary — a
  "worker parked" seam for `DiagnosticsLog` (or assert the contract instead of the file: newest entries
  survive, one drop marker per flush, and the ring never exceeded `maxPendingEntries`), and an "await the
  frame"/ownership check for the renderer. `DiagnosticsLog.awaitDrained` is already the shape to copy.
  Case 3 now has a known fix ✓: set `renderer.asyncLoopsEnabled = false` before `onSurfaceCreated` (the pattern
  of every sibling case). That is deterministic, not merely likely: `renderSignal` starts at `0L` with
  `lastRender = 0L` (`:320`/`:884`), so the collector's initial StateFlow emission is neutralised, and the flag
  is read before the debounce (`:886`).
  Case 1's fix follows from its mechanism ✓: await the candidate-lookup state on a dispatcher the test owns (or
  inject that dispatcher into `MapScreen`, as `MainDispatcherRule`'s contract intends) and never read a test-side
  collection from a background thread — the assertion's own message must not be built from state the renderer's
  threads still mutate.
- **Next step** ℹ: fix the three cases (each is small), then re-run the aggregate command until it is green;
  only then is a green CI run reproducible evidence. A single green aggregate run is not proof of a fix here —
  the measured failure rate is 4 in 4 aggregate runs, but the *cases* vary, so the check is a green aggregate
  run plus the single-module reruns staying green. Case 1's message is now in hand (above), and that run makes
  this the fifth aggregate run of the family and the second to fail in `:auto`. CI's console format cannot carry a
  message at all — no module sets `testLogging` — so a run that is red only in CI has to be diagnosed from a local
  report XML, or the modules' `Test` tasks should set `exceptionFormat = "full"`.
- **Not attributable to `fix-daemon-jvm-provisioning`** ✗: that change touched the daemon JVM criteria, a CI
  guard step and a guideline section; it contains no test or diagnostics code. Its own evidence is in
  `openspec/changes/fix-daemon-jvm-provisioning/traceability.md`.

---

## 149. The `view_image` package's extension filter can stop matching and restore the bundled libtui UI drift without any error — Found 2026-10-07 while removing the libtui host extension from the screenshot-reading tooling
**id:** 149 · **category:** build-and-harness · **class:** improvement · **status:** open

- **Observed** ℹ: `@luan.sh/pi-view-image` declares two extensions in its manifest — `./src/extension.ts`
  (the tool) and `./node_modules/@luan.sh/pi-libtui/src/extension.ts` (a TUI host). That host is what
  replaces the editor/user-message layout, drives the streaming status row (waiting animation), restyles
  markdown tables, and leases the mouse/cursor/split-pane and tool-renderer lookups of the Pi TUI; the
  symptom of it loading is a changed Pi UI, never an error. The fix lives in `~/.pi/agent/settings.json`:
  one version-pinned entry per package with `"extensions": ["./src/extension.ts"]` (an allowlist,
  because the host path is version-nested inside `node_modules`). The package itself stays — it is the only
  route for a text-only session, since Pi's built-in `read` returns image content only for a vision-capable
  model while `view_image` falls back to a description from `openai-codex/gpt-6-luna`.
- **Why it is filed rather than fixed here** ✗: the guard is a check against installed Pi state, not app
  code — nothing in this repo can assert it, and the settings file is machine-local (outside the working
  tree, so an agent session cannot read or edit it). What this entry owns is the *check*, recorded in
  `AGENTS.md` next to the `view_image` paragraph.
- **Fix candidate**: after any `pi update --extensions`, confirm `pi list` shows exactly one entry for
  `@luan.sh/pi-view-image` and that `/libtui:colors` — a command registered *only* by the libtui host — is
  absent from the command list; if the allowlist form ever stops narrowing, fall back to the documented
  exclusion form (`"!node_modules/@luan.sh/pi-libtui/src/extension.ts"`). A durable alternative is a local
  vendored copy with the manifest trimmed to one extension entry (pinned, but owned).
- **Not covered by the filter** ✗: the tool's own row still renders through libtui **library** components
  (`ToolActivity`, `toolCallPreview`, `ComponentStack` in `pi-view-image/src/tools/view-image/presentation.ts`),
  and `installPendingMessageTransformer` still re-renders queued rows. Removing the host removes the two
  symptoms that started the investigation (message layout, waiting animation), not every libtui pixel.
- **Invisible today** ✗: no check asserts which extensions a package actually loaded, so a filter that stops
  matching is indistinguishable from an upstream Pi UI change — the same misattribution this entry was
  filed from. The oracle is one command (`/libtui:colors`), so the cost of missing it is re-walking this
  analysis.

## 150. After arrival the reroute path re-acquires the destination that was already reached — Found 2026-10-07 while applying `auto-end-navigation-after-arrival` (device run on the AAOS AVD, tasks 3.2/3.3)
**id:** 150 · **category:** navigation · **class:** improvement · **status:** on-hold suppression of the post-arrival reroute vs the recorded owner decision

- **Loop verdict** ⏳ (bug-fix loop 2026-10-09, `bugfix-loop` iteration 9 — not eligible: gate condition 3 fails,
  `needs decision`): the claim is real in the tree — `NavigationEngine.kt:887` `onRerouteRequest` reaches
  `:950` `confirmReroute` with no arrival guard, and the arrival fact is deliberately carried across the reroute
  (`:333` `val arrivedBefore = keepRerouteCooldown && _state.value.hasReachedDestination`, comment `:327-328`
  "a reroute re-acquires the destination that was already reached") — but the authorities state the **opposite**
  of the fix, as a recorded owner decision: `proposal.md:31-32` of the archived change
  `auto-end-navigation-after-arrival` ("**No reroute behaviour change.** Reaching the destination does not
  suppress the engine's reroute path (owner decision); the arrival fact is instead retained across a reroute"),
  its `design.md:150` ("D7 — No reroute suppression, no setting (owner decisions)") and `:137` ("(c) suppress
  the reroute after arrival — rejected by the owner (D7)"), plus its risk note `:173-174` ("[A drive-by within
  30 m already counts as arrival, and with reroute unsuppressed the driver can be guided back afterwards] →
  accepted semantics … Documented in the spec, not silently assumed"). The live spec encodes that same
  behaviour: `openspec/specs/navigation-engine/spec.md:187` ("SHALL keep it while a reroute replaces the
  route") with scenario "Arrival survives a reroute" (`:200-203`), asserted today by
  `NavigationEngineArrivalTest#rerouteKeepsTheArrivalFact` (`:176`); no spec or guideline states that a reached
  destination must not be re-acquired — the nearest text, `guidelines/UI.md:736`, documents the reroute as
  re-acquiring the retained destination. So the fix reverses an explicit owner decision and needs its own
  `reroute-trigger` delta with a driver-visible acceptance criterion (what the driver sees while parking, not
  what the engine computes — the entry's own line). Decision needed: does the post-arrival reroute stay
  unsuppressed as decided in D7, or is that decision reversed (and then with which guard: refuse only a reroute
  whose destination is the running session's arrival, per the fix candidate). Host seam exists for the later
  change — `NavigationEngineArrivalTest` reports arrival through `listener().onTargetReached(...)` and drives
  the reroute through `requestReroute()`, `NavigationEngineRerouteTest` asserts the calculation — so only the
  decision is missing, not the host decidability.

- **Observed** ℹ: the native `RouteStateAgent` reports the target reached only inside a 30 m circle while the
  position is on route (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/RouteStateAgent.cpp`),
  so continuing past the destination — looking for a parking spot — leaves that circle, the position goes off
  route, and after 5 s the native emits a `RerouteRequestMessage`. The engine accepts it as it always did and
  re-acquires the same destination that was already reached: "turn around, N m to destination" while the driver
  parks. The arrival fact added by this change (`NavigationState.hasReachedDestination`, spec `navigation-engine`
  — Arrival is part of the shared navigation state) makes the situation decidable, and it is deliberately
  preserved across such a reroute, so the guidance keeps working on a destination already reached.
- **Why it is filed rather than fixed here** ✗: the owner took this decision explicitly while planning
  `auto-end-navigation-after-arrival` ("leave the reroute path as it is"), so suppressing it is a behaviour
  change of its own — with its own delta for `reroute-trigger` and its own evidence, because the acceptance
  criterion is what the driver sees while parking, not what the engine computes.
- **Fix candidate**: once `hasReachedDestination` is set, refuse a reroute whose destination is the arrival of
  the running session (`NavigationEngine.onRerouteRequest` is the single decision site; a reroute to a *new*
  destination must stay possible). Verify with a case per path in `NavigationEngineRerouteTest` (arrival plus
  an off-route report → no route calculation; arrival plus a new destination → a calculation) and one
  revert-check on the guard; the device trigger is the same `adb emu geo fix` stream used in task 3.2 — drive
  past the destination and watch for `Diag/ROUTE: calc done: source=reroute`.
- **Also observed in the same run** ℹ: after arrival the car ETA card keeps being updated with `remaining=0m`
  (host trip updates, once per position fix) and the native arrival estimate keeps arriving — expected while the
  session is live, but it is the thing a driver sees if the exit never fires.

## 151. The navigation-time right-side widget column never publishes its width, so the follow anchor can resolve under it — Found 2026-10-07 while applying `fix-phone-map-layer-stack` (adjacent finding, out of that change's scope)
**id:** 151 · **category:** ui · **class:** bug · **status:** open

- **Observed** ℹ: `LocalOverlayWidthProbe` is provided around the browse overlay block only
  (`MapCanvasScreen.kt`, the `CompositionLocalProvider` in the chrome band), while the navigation-time copy
  of the same column (`MapRightWidgetColumn`, composed in the chrome band's navigation overlay block) reads
  the probe at its own call site. Outside the provider the probe is the default no-op, so
  `overlayRightInset` stays 0 for the whole navigation, `setMapOverlayInsets(right = 0)` publishes no right
  band, and the follow anchor may resolve under the compass / speed / zoom column — the exact overlap the
  probe exists to prevent (spec `smooth-follow` — visible-area scenarios). `fix-phone-map-layer-stack` moved
  the two blocks into the chrome band but did not widen the provider scope: that would change what the probe
  measures during navigation, i.e. followed-map framing, which is a change of its own with its own device
  evidence.
- **Why it is filed rather than fixed here** ✗: the fix changes the follow framing a driver sees during
  navigation, so it needs a `smooth-follow` delta and an on-device check of the anchor position — not a side
  edit inside a layering change.
- **Fix candidate**: provide the probe once for the whole chrome band (a `CompositionLocalProvider` around
  the chrome band's content instead of around the browse block), then verify with a case that the navigation
  column reports a non-zero width and with one device run that the follow anchor sits left of the column
  (`pixel-check` on the marker's pixels against the column's UI-dump bounds).

## 152. The follow-anchor insets during a navigating session panel follow the chrome the panel covers — Found 2026-10-07 while applying `fix-phone-map-layer-stack` (adjacent finding, out of that change's scope)
**id:** 152 · **category:** ui · **class:** improvement · **status:** open

- **Observed** ℹ: the chrome band publishes the turn card's, the routing status card's and the street pill's
  measured heights to `setMapOverlayInsets` (spec `smooth-follow`), and `fix-phone-map-layer-stack` now
  composes the route-planning session card in the modal band, i.e. over the bottom part of that chrome. While
  the panel is open during navigation, the published bottom inset still describes the routing status card
  (which is suppressed) and the widget column (which the card covers), while the band the anchor actually has
  to avoid is the card — whose own height already reaches the session's fit path (`RoutePanel`'s
  `onOverlayHeightChanged`, spec `route-planning-session` — Session holds the camera while active). Whether
  the follow anchor should also be clamped out of the card band is a decision for the session surface, not
  for the layer stack; the band change only made the overlap visible.
- **Why it is filed rather than fixed here** ✗: the session panel owns its band and its camera lease, so the
  inset contract between the chrome band and the panel is a `route-planning-session` / `smooth-follow` change
  with its own device evidence.
- **Fix candidate**: let the card's measured height feed the same bottom inset while it is open (one writer
  per inset: the band that paints the pixels), then verify with a case that the published bottom inset equals
  the card's height while the panel is open, and one device run of the follow anchor with the panel at MAX.

## 153. Rotating the phone while follow mode is active crashes on an empty coerce range in the follow drift clamp — Found 2026-10-07 while applying `fix-phone-map-layer-stack` (device run on the Pixel_8 AVD, task 4.3)
**id:** 153 · **category:** ui · **class:** bug · **status:** open

- **Observed** ✗: with navigation running (follow mode engaged) on the phone AVD, `adb shell settings put
  system user_rotation 1` killed the process:
  `java.lang.IllegalArgumentException: Cannot coerce value to an empty range: maximum -552.0 is less than
  minimum 552.0` at `com.naviveylin.core.FollowPrediction$Companion.displayOffsetPx(FollowPrediction.kt:317)`,
  called from the follow display loop (`MapCanvasScreen.kt:747`), `FATAL EXCEPTION: main`. The mechanism is in
  the clamp itself: `marginX = (bitmapW - canvasW) / 2.0` goes negative when the *displayed* bitmap still has
  the old orientation (1296x2880 portrait) while the canvas has the new one (2400x1080 landscape), so
  `driftX.coerceIn(-marginX, marginX)` becomes `coerceIn(552, -552)` — an empty range. Rotating back and forth
  during a drive therefore crashes the app instead of re-rendering.
- **Why it is filed rather than fixed here** ✗: nothing in `fix-phone-map-layer-stack` touches the follow
  display loop or the drift clamp; the layer bands only decide paint order. It is a rotation/follow bug of its
  own with its own `smooth-follow` delta and its own device evidence (rotate during a followed drive, both
  directions).
- **Fix candidate**: treat a negative margin as zero (`marginX.coerceAtLeast(0.0)`) or, better, skip the drift
  clamp while the displayed bitmap's aspect does not match the canvas (a frame from the previous orientation is
  stale and will be re-rendered); verify with a case on `displayOffsetPx` for swapped bitmap/canvas
  dimensions, a forced configuration change in a Robolectric case, and one device rotation mid-drive in each
  direction.

---

## 153. `guidelines/MapRendering.md` numbers two sections `## 14.`, so a `§14` reference is ambiguous in eleven citations — Found 2026-10-07 while applying `scope-guideline-reads` (out of that change's scope)
**id:** 153 · **category:** specs-and-process · **class:** improvement · **status:** open

- **Observed** ℹ: `guidelines/MapRendering.md:553` is `## 14. Rotation Gesture Display-Layer Handoff` and
  `:630` is `## 14. Android Auto renderer — smooth follow (overrun + blit + extrapolation)`. `§14` of that
  document is cited **11** times outside it (`grep -rhoE 'MapRendering\.md[` ]*§14' --include=*.md .`), and ten
  of those citations mean the Android Auto renderer — the surrounding text makes it explicit in each
  ("§14 (Android Auto renderer — smooth follow)", "§14/§15a", "the loop-liveness invariant"). `§15` is cited
  15 times and `§1` 30 times, so this numbering is load-bearing beyond the one duplicated pair.
- **Why it is filed rather than fixed here** ✗: renumbering the second `## 14.` (to `§20`, say) would
  silently repoint all ten AA-renderer citations — including `guidelines/Design.md:427` and nine archived
  changes — at the rotation-gesture section, and the archived text cannot be corrected without rewriting
  history. `scope-guideline-reads` resolves the ambiguity where it is read instead: the routing table names a
  section by number **and** heading text, and `tools/check-doc-routes.sh` refuses a bare number that its
  document uses twice (`check-doc-routes-selftest.sh` covers that case).
- **Fix candidate**: one change that renumbers the second `## 14.` and updates every citation in the same
  commit (the archived changes keep their historical text), or that decides references are by heading text
  only and records that decision in the routing convention. Either way `check-doc-routes.sh` stops a future
  bare `§14` from being added.
- **Related** ℹ: unnumbered `## ` sections exist beside the numbered ones — `MapRendering.md` carries two
  (`Known Pitfalls (Regression Checklist)`, `Parameter Overview`), `Design.md` its two appendices, `UI.md`
  one (`Keeping this document honest`). The routing table names those by their text alone, which the check
  resolves.
