# Tasks

Specs: `compass-button` (fill + resolved-presentation palette), `dark-mode`
(status-carrying controls), `auto-map-layout` (rose follows the resolved surface
presentation). Design: `design.md` (D1–D8).

## 1. Phone compass palette

- [x] 1.1 Replace the three fixed light fill literals in
      `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` with a per-presentation
      palette (`compassFillColor(quality, isDark)`, `compassOnFillColor(isDark)`,
      `compassRimColor(isDark)`) using the D2/D6 values; verify with
      `./gradlew :app:compileMobileDebugKotlin` that it compiles and no light literal
      remains reachable in dark presentation (grep the file for the old literals).
      Spec: `compass-button` — GPS fix status fill color.
- [x] 1.2 Draw the needle, the "N" label and the rim from the palette instead of
      `MaterialTheme.colorScheme.onSecondaryContainer` / `outline`; verify the same
      compile command succeeds and no color role read remains in the compass draw path.
      Spec: `compass-button` — Compass colors follow the resolved day/night presentation.
- [x] 1.3 Add the `isDarkPresentation: Boolean` parameter to `CompassButton`,
      `MapCompassBlock` and `MapRightWidgetColumn` in
      `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` and pass
      `state.isDarkPresentation` at every call site (both orientations); verify
      `./gradlew :app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin`
      succeeds for all three ABIs and no call site falls back to
      `isSystemInDarkTheme()` (grep). Spec: `compass-button`, `dark-mode` — Dark
      presentation applies to UI controls. Design: D3.

## 2. Phone compass tests

- [x] 2.1 Extend `app/src/test/java/com/naviveylin/ui/map/CompassButtonComposeTest.kt`
      so `fillColorReflectsGpsFixQuality` asserts three distinct fills per presentation
      and that light and dark fills differ per quality; verify
      `./gradlew :app:testMobileDebugUnitTest --tests '*CompassButtonComposeTest*'` passes.
      Spec: `compass-button` — fill scenarios, quality change stays inside the active
      presentation.
- [x] 2.2 Add `app/src/test/java/com/naviveylin/ui/map/CompassPaletteTest.kt`: WCAG
      relative-luminance contrast of needle-on-fill ≥ 4.5:1 for all six
      (quality × presentation) combinations, hue-family membership per quality across
      presentations, needle color unchanged by a quality change, and needle/rim/fill
      identical between presentations are asserted to differ; verify
      `./gradlew :app:testMobileDebugUnitTest --tests '*CompassPaletteTest*'` passes.
      Spec: `compass-button` — Compass colors follow the resolved day/night presentation
      (contrast, manual override, quality-independence scenarios). Design: D6.

## 3. Android Auto compass rose palette

- [x] 3.1 Give `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` a
      per-presentation rose palette (chip, foreground, north pointer, night rim) and a
      `darkPresentation` parameter on `draw`; verify
      `./gradlew :auto:compileDebugKotlin` succeeds. Spec: `auto-map-layout` — Compass
      rose follows the resolved surface presentation. Design: D7.
- [x] 3.2 Draw the night rim only in dark presentation and confirm the rose geometry
      (radius, center, rotation, pointer direction) is byte-identical in both branches —
      no geometry expression inside the presentation `if`; verify by reading the diff and
      by the geometry assertions from 5.1. Spec: `auto-map-layout` — Rose geometry is
      presentation-independent.
- [x] 3.3 Pass the presentation into `SurfaceIndicators.draw` from
      `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` using the renderer's
      current value for that screen's gate; verify `./gradlew :auto:compileDebugKotlin`
      succeeds. Spec: `auto-map-layout` — Rose and map never disagree.

## 4. Android Auto free-driving presentation plumbing

- [x] 4.1 Add `resolvedDark: StateFlow<Boolean>` to `FreeDrivingScreen` and a `KEY_DARK`
      observation with an `onDark` callback in
      `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreenObservations.kt` (never a
      bare `scope.launch`); verify with a unit test that the observation stops on
      `stop()` and does not duplicate on a second `start()`.
      Spec: `auto-map-layout` — Free driving rose follows the same presentation,
      Host day/night change re-renders the rose. Design: D4, D8.
- [x] 4.2 Apply the pushed value with `rendererGate.setDarkPresentation(dark)` and add
      the `pushDark()` shape from `NavigationScreen` (guarded on an existing renderer,
      deduped via the existing `DaylightApplier`, applied through
      `rendererGate.requestDaylightPush` on a background dispatcher); verify
      `./gradlew :auto:testDebugUnitTest` passes and free driving's map surface now
      follows the resolved presentation. Spec: `auto-map-layout` — Rose and map never
      disagree; `dark-mode` — Dark presentation applies to map rendering.
- [x] 4.3 Pass `resolvedDark` into both construction sites
      (`auto/src/main/java/com/naviveylin/auto/MapScreen.kt:496`,
      `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:768`) and pass the
      presentation to `SurfaceIndicators.draw` in `FreeDrivingScreen.kt`; verify
      `./gradlew :auto:compileDebugKotlin` succeeds.
- [x] 4.4 Add a unit test that a dark-mode preference of `ON` with a day host resolves
      dark and reaches the free-driving gate (and `OFF` with a night host resolves
      light), driven through `resolveCarDark`; verify the test passes and covers all
      three preference values. Spec: `auto-map-layout` — Dark-mode preference applies on
      the car as on the phone.

## 5. Android Auto tests

- [x] 5.1 Extend `auto/src/test/java/com/naviveylin/auto/SurfaceIndicatorsTest.kt`:
      palette differs between presentations while all geometry outputs stay equal, and
      the day palette equals today's colors; verify
      `./gradlew :auto:testDebugUnitTest --tests '*SurfaceIndicatorsTest*'` passes.
      Spec: `auto-map-layout` — Rose geometry is presentation-independent, Day
      presentation uses the day palette.
- [x] 5.2 Add contrast assertions for the rose (foreground and north pointer against the
      chip ≥ 3:1 non-text, chip or rim separating from a dark land tone) mirroring the
      phone palette test; verify the assertions fail if a palette entry is reverted to
      the old single-chip value. Spec: `auto-map-layout` — Night presentation uses the
      night palette.

## 6. Documentation

- [x] 6.1 Update `guidelines/UI.md` §8 (compass button colors: per-presentation palette,
      measured contrast, AA rose night rim) and §9 (dark mode: status-carrying controls
      dim by their own dark tone, and the AA surface presentation is preference × host
      for every screen including free driving); verify the described behavior matches the
      implementation by re-reading the changed source files against the text.
      Spec: all three deltas. Design: D1, D2, D7.
- [x] 6.2 Check `AGENTS.md` and `README.md` for statements this change invalidates (car
      screen list, observation ownership, per-screen renderer/dark pushes) and update
      what is stale; verify by grepping both files for "dark" and "observation" and
      confirming each hit still holds.
- [x] 6.3 Document the out-of-scope findings in `TODO.md`: the needle stroke width
      `3f` (px, not dp) in `CompassButton.kt` shrinking visually on high-density screens,
      and the fixed non-presentation-aware colors in `MiniMap.kt:91` and the
      `LocationAccuracy` overlay; verify both entries are present with file references.
- [x] 6.4 Append the planning-phase dead end to `ki_processing_failures.log`: renaming
      scenarios inside an OpenSpec `## MODIFIED Requirements` block is rejected at
      archive time (the whole block is replaced, so original scenario names must be kept
      and new cases added alongside); verify the entry has a timestamp and states the fix.

## 7. Build, test and on-device verification

- [x] 7.1 Run the full unit test suites and confirm no regressions:
      `./gradlew test` (all modules, both flavors) via the `run-tests` skill; verify zero
      failures and zero build warnings.
- [x] 7.2 Run the license gate and both flavor builds via the `build-app` skill
      (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and confirm all
      three ABIs compile without warnings; verify the resulting APKs exist.
- [x] 7.3 Phone on-device check (emulator or device) — **done 2026-09-27 on `emulator-5554`** (API 37, x86_64, de-DE, 1080x2400, densityDpi 420; system night mode "auto"; the compass overlay at `[912,1473][1059,1620]`). Method: Location options → Dunkelmodus Ein/Aus/Automatisch (the setting is persisted — `files/maps/settings.json` gains `"darkMode":"ON"` and drops the field again for AUTOMATIC, both verified), each presentation captured as a screenshot and reduced to the crop's dominant colours; the system toggle by `adb shell cmd uimode night yes|no`.
  - **Off (light)**: compass fill **#C8E6C9** (the `GpsFillGoodFix` tone), symbols **#282928** ≈ `CompassOnFillLight` **#1F1F1F** → contrast ≈ **12.7:1**; whole-screen mean 222 (light map).
  - **On (dark)**: fill **#1B4A24** (`CompassDarkFillGoodFix`), symbols **#D2D9D7** ≈ `CompassOnFillDark` **#E8EAED** → contrast ≈ **8.4:1**; whole-screen mean 131 — the dark presentation shows the dark tones (the palette, not a theme role).
  - **Automatic follows the OS toggle live**: with `ambientLightSensitivity` set to OFF, `cmd uimode night no` → the compass switched at t≈2 s and the map bitmap at t≈3-4 s (per-second screen mean 137→137→137→222, compass crop 112→166→166→208); `night yes` → back to 131/112 within ≈3 s. **One prerequisite worth recording**: with the ambient-light option at its persisted HIGH the sensor classification deliberately wins over the system signal, so the OS toggle alone does *not* move the presentation — set the option to OFF to observe the Auto/system path.
  - **Fix qualities**: GOOD (green #C8E6C9 light / #1B4A24 dark) and POOR (**#FFF9C4** light / **#5C4401** ≈ `CompassDarkFillPoorFix` dark) both captured on device — the coarse-location grant produces `GPS fix acc=2000,0` → `MapCanvasVM: GPS fix quality: POOR`. **NONE (red) was not captured**: revoking the location permission kills the app process (nothing left to look at), and switching the device location off never re-evaluates the quality at all — that is the stale-fix defect recorded in `TODO.md` (a lost fix stays GOOD, so the red family is unreachable on a lost fix either way). The red tones are therefore code/branch-verified only.
  - **Logcat**: no error or warning from the palette change (no new `w:`/`e:` from Compose or the theme). Pre-existing and unrelated to this change: `E NaviVeylin: ERROR while loading image '<name>'` repeats per render because no icon directory is shipped or configured — recorded in `TODO.md`.
- [x] 7.4 Android Auto on-device check (desktop head unit or AAOS emulator): verify the
      navigation and the free-driving rose both switch palette with the host day/night
      signal, that the free-driving **map surface** now renders the dark variant at
      night (the defect this change fixes), that no rose/map mismatch frame is visible
      across the switch, and that the surface survives a background round trip
      (`onStop`/`onStart`) without a duplicate collector or a lost palette. Verify with
      `adb logcat -s NaviVeylin` plus the `HOST` diagnostics entries from the
      session/gate path. **Done 2026-09-27 on `emulator-5554` (`Automotive_Distant_Display_with_Google_Play`, AAOS, API 33, x86_64, 1080x600 @ 120 dpi, automotive debug build of the current tree).** Method: the car session was started (`am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity`), driven through the host's action strip / menu rows with `input tap`, with a live GPS fix stream (`adb emu geo fix` every 2 s — a single injection is a one-shot that never reaches a later subscriber), and the day/night switch was the host signal itself (`cmd uimode night yes|no`, answered by `Diag/SESSION: Host dark mode changed to true`). The app-drawn rose was located by its opaque red north pointer (0xFFE53935 is unique to it) and measured on the row through its centre (screenshots reduced with ImageMagick; the region is identified by the pointer's pixel bbox, so the measurement does not depend on knowing the template layout).
  - **Navigation surface, day vs night** (renderer#1, `AutoMapRenderer`): the map tone switched (`#ADD2A5` light land → `#737573`/`#526952` dark) and a new native style was created on the switch (`NaviVeylin: Created new style with /data/user/10/…/files/stylesheets/standard.oss` at +1.3 s). The rose chip spanned **x1005..1046** on the centre row in **both** presentations (identical bbox, so geometry/position/rotation unaffected), but the chip's edge differed: day — the day map met the chip directly (`#ADD2A5` → `#424942` chip edge), i.e. **no rim**; night — a **`#7B797B` rim pixel on both chip edges** (the `ROSE_RIM_NIGHT` 1.5 dp stroke) between the night map and the chip. That rim is the only palette delta of `rosePalette(darkPresentation)`, and it is exactly what the code draws at night.
  - **Free-driving surface** (`FreeDrivingScreen: Free driving renderer ready`, `GPS fix … acc=2000.0`, `applied free-driving anchor BOTTOM_CENTER`): day frames `#EFEBEF`/`#ADD2A5`/`#B5B694` with **0** rim pixels, night frames `#737573`/`#526952` with **8–38** rim pixels and the chip present — i.e. the free-driving map surface renders the dark variant at night with a night rose (the defect this change fixes), on the same surface, without a session restart.
  - **No mismatch frame across the switch**: 14 frames sampled every 5 s across `night yes` and `night no` — every night frame had a dark map tone (mean brightness 0.663–0.669) with rim pixels present, every day frame a light map tone (0.828–0.838) with 0–3 rim pixels; no frame mixed a light map with a night rose or vice versa. (The rose and the stylesheet variant are resolved once per render and drawn in the same pass; the sampling interval bounds what this method can exclude.)
  - **Background round trip**: `HOME` → relaunch the car activity while dark: the same navigation renderer (#1) adopted a new surface (`surface destroyed` → `releasing session surface` → `surface created`), kept rendering (`lock OK` continuing, 0 `surface invalid` / `lockCanvas failed`) and **kept the night palette** (map tone + rim unchanged in the post-return frame); no duplicate observation collector was visible (one `GPS fix` line per injected fix, no doubled render requests).
  - **One observed-but-not-reproduced anomaly** (recorded for the on-device log only, not as a task result): in the first run of the day the free-driving surface stayed **black for ≈31 s** after the screen was pushed (renderer ready 11:36:38 → first `MAP render` 11:37:07; the snapshot at 11:36:52 was uniform `#040505`) while the host was re-composing after the preceding day/night toggles; four later runs on the same build rendered the first free-driving frame within 4 s. Not attributed to this change.
  - **Logcat/diagnostics**: `Diag/SESSION: Host dark mode changed to true` on the switch plus the `Diag/HOST` surface adopt/release pairs; no `HOST` rejection, no confined fault, no palette-related warning from Compose/the theme (`adb logcat -s NaviVeylin` shows only the pre-existing `Unknown type …` / `ERROR while loading image …` noise recorded in `TODO.md` §85/§89).
- [x] 7.5 Confirm cross-cutting regressions are absent: compass rotation, sizing, shadow,
      hit targets and long/short press behavior unchanged, and the AA speed badge and
      speed-limit sign palettes untouched; verify by re-running
      `CompassNeedleTargetTest`, `CompassButtonComposeTest` and `SurfaceIndicatorsTest`
      and by diffing the two palette branches for the untouched constants.
