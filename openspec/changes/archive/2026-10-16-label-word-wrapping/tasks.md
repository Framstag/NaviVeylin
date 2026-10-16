# Tasks

Parent spec for every task below: `map-render` — delta in `specs/map-render/spec.md`
("Labels wrap at word boundaries within the configured bounds"). Design: `design.md`
(D1 = which backend, D2 = where the bounds come from, D3 = the break rule, D4 = how it is testable).

## 1. State the wrapping bounds in the render

- [x] 1.1 Set the four label-line values in `renderMapIntoPixels`
  (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`, the `MapParameter`
  block): `LabelLineMinCharCount` 5, `LabelLineMaxCharCount` 15, `LabelLineFitToArea` `true`,
  `LabelLineFitToWidth` `min(width, height)` of the render buffer, with the comment stating that a
  break happens at a word boundary only (spec: map-render — Labels wrap at word boundaries within the
  configured bounds, decision D2 option A).
  Verify: `git grep -n 'SetLabelLine' -- app/src/main/cpp/libosmscout/libosmscout-client-java` lists
  the four calls.
  **Done** ✅: four calls at `OSMScoutClient.cpp:1602-1607`; the values are the Qt plane renderer's
  (`libosmscout-client-qt/src/osmscoutclientqt/PlaneMapRenderer.cpp:376-379`).

## 2. Wrap the text in the Pango-less Cairo layout

- [x] 2.1 Give the Cairo label the lines it draws: `CairoNativeLabel` (no-Pango branch) gains `lines`,
  `lineExtents` and `lineAdvance`
  (`libosmscout-map-cairo/include/osmscoutmapcairo/MapPainterCairo.h:81-88`) (spec: the same
  requirement — "A wrapped label reports the size it draws", decision D1 option A).
  Verify: the two cases of task 3.1 observe more than one baseline.
- [x] 2.2 Wrap in `MapPainterCairo::Layout` (Pango-less branch): compute the average character width
  from the resolved cairo font, ask `GetProposedLabelWidth`, split at word boundaries without breaking
  a word, measure each line, and report the widest line's ink width and the stacked block's height
  (`MapPainterCairo.cpp:1347-1418`, `WrapCairoText` at `:1285`, `CairoTextExtents` at `:1259`) (spec:
  the same requirement — all four wrapping scenarios, decision D2 option A, D3 option A).
  Verify: task 3.1's cases.
- [x] 2.3 Draw every line: `DrawLabel` for the text style and for the shield style places each line's
  ink at the label rectangle, centred in its width and one baseline advance lower per line; `ToGlyphs`
  emits the glyphs of every line on its own baseline
  (`MapPainterCairo.cpp:1474-1520`, `:1560-1590`, `ToGlyphs` at `:1193-1232`) (spec: the same
  requirement — "Every glyph is drawn once"; decision D1 option A).
  Verify: the glyph count of the wrap case equals the unwrapped count minus one per line break, and the
  two cases appear in the run's case list.
  **Done** ✅: the wrap case asserts exactly that
  (`wrapped.glyphs.size() == unwrapped.glyphs.size() - (baselines - 1)`).
  ⚠ **Limit of the evidence** (independent review, 2026-10-15): the baseline assertion reads
  `TextMetrics::glyphs[i].position`, which `ToGlyphs` produces (`MapPainterCairo.cpp:1215`), not the
  per-line `cairo_move_to` of `DrawLabel` (`:1512-1515`, `:1593-1596`). So "the glyphs sit on more than one
  baseline" is proven for the enumeration and the drawn placement is only read; a pixel assertion was
  ruled out by design D4. Do not read task 3.1's cases as covering the draw path.
- [x] 2.4 Keep a label that does not wrap byte-for-byte as it was: a single-line label keeps the ink
  height it reported before (`label->height` is the ink height for one line) and the draw position of
  its single line reduces to `rectangle.x - x_bearing`, `rectangle.y - y_bearing`
  (`MapPainterCairo.cpp:1411-1413`) (spec: the same requirement, decision D1 risk).
  Verify: the pre-existing `TextMetricsCairoTest` cases stay green in task 3.2.

## 3. Test the wrapping rule and falsify it

- [x] 3.1 Add the wrapping-aware measurement entry point and the two cases
  (`MapPainterCairo.h:332-340`, `MapPainterCairo.cpp:1431-1441`,
  `Tests/src/TextMetricsCairoTest.cpp:573` `Cairo wraps a multi-word label into the proposed width`,
  `:629` `Cairo never breaks a word and keeps a short label on one line`), guarded by
  `#if !defined(OSMSCOUT_MAP_CAIRO_HAVE_LIB_PANGO)` so they run in the shipped configuration (spec: the
  same requirement — one case per scenario; design D4 option A).
  Verify: task 3.2.
- [x] 3.2 Run the cases in a Pango-less host build:
  `cmake -S app/src/main/cpp/libosmscout -B <dir> -G Ninja -DCMAKE_BUILD_TYPE=Debug
  -DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON -DOSMSCOUT_BUILD_TESTS=ON -DOSMSCOUT_BUILD_IMPORT=ON …`,
  `cmake --build <dir> --target TextMetricsCairoTest -j16`, `ctest -R TextMetricsCairo`.
  Verify: `7/7` cases pass, exit 0, and the two new cases are named in the run's case list — a missing
  case is not a pass (see the include-order hole below).
  **Done** ✅: `100% tests passed, 1/1` (`ctest`), `test cases: 7 | 6 passed | …` → after the glyph-count
  assertion was corrected to the one-glyph-per-break rule: `7/7` green, no compiler warning.
  ℹ The pre-existing *"Cairo measures a configured font file as the face of that file"* case fails in
  that configuration until the source tree's generated `libosmscout-map/include/osmscoutmap/MapFeatures.h`
  (written into the tree by the Android CMake configure, gitignored) is out of the include path — the
  build lists the source `include/` before the build `include/`, so the Pango-less host build reads the
  Android-generated feature header and cannot read a font family. Temporarily aligning that gitignored
  header made the case pass; it is not caused by, and not fixed in, this change. Filed as `TODO.md` §202.
  ⚠ The same include-order hole bites the other way round: because these cases are `#if
  !defined(OSMSCOUT_MAP_CAIRO_HAVE_LIB_PANGO)`, a tree whose source-generated features header says Pango is
  present compiles them **away**, and the run then reads green with the cases absent — which is why the
  Verify above asks for the case names, not only the tally.
- [x] 3.3 Revert-check the new bound: mutate the fix once (force `proposedWidth = -1.0` in the
  Pango-less `Layout`, i.e. never wrap), run the two cases, restore, re-run green (spec: the same
  requirement, decision D4; `revert-check` skill).
  Verify: the mutation makes `Cairo wraps a multi-word label into the proposed width` fail; the restored
  binary passes.
  **Done** ✅: with `if (false && enableWrapping …)` the run reported
  `test cases: 7 | 6 passed | 1 failed` — `Cairo wraps a multi-word label into the proposed width FAILED`
  (`TextMetricsCairoTest.cpp:615`, `31 == 33`); the file was restored from a byte-identical backup
  (`diff` clean) and the run reported `100% tests passed`.

## 4. Document the contract

- [x] 4.1 Add `guidelines/MapRendering.md` §20 "Label wrapping": the four values and where they are set,
  that the character count is the bound that wraps, the word-boundary-only rule, and that the Android
  build has no Pango so the FreeType/cairo variant wraps (spec: the same requirement, design D2).
  Verify: `grep -n '^## 20' guidelines/MapRendering.md` **at HEAD**, not only in the working tree.
  **Done** ✅: committed in `a13be71` (`git show HEAD:guidelines/MapRendering.md | grep -c '^## 20'` → 1)
  — the review caught that this task was first closed with the section existing only as an uncommitted
  working-tree change; two of its bullets were then scoped to the backends the code actually reaches ("in
  every backend" → "in the backends that wrap"; the Pango variant wraps at the object width and ignores
  the label-line parameters, and AGG never wraps).
- [x] 4.2 Route it from `AGENTS.md`'s Documentation Map (`| label word-wrapping … | MapRendering.md §20 |`).
  Verify: `bash tools/check-doc-routes.sh`, `git show HEAD:AGENTS.md | grep 'label word-wrapping'`.
  **Done** ✅: `check-doc-routes: all 83 section references resolve`, exit 0, with §20 and the route row in
  HEAD.

## 5. Build the shipped configuration and run the gate

- [x] 5.1 Compile the modified backend in the shipped (Pango-less) Android configuration for one ABI:
  `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`.
  Verify: `BUILD SUCCESSFUL`, `configureCMakeDebug[arm64-v8a]` and `buildCMakeDebug[arm64-v8a]`
  executed, no `error:`/`warning: ` in the log.
  **Done** ✅: `BUILD SUCCESSFUL`, both native tasks executed, no compiler diagnostic in
  `/tmp/nv-native-build2.log`.
- [x] 5.2 Build the remaining shipped ABIs (`armeabi-v7a`, `x86_64`) and both flavors:
  `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`.
  Verify: `BUILD SUCCESSFUL` and the native tasks of all three ABIs in the log.
  **Done** ✅: `BUILD SUCCESSFUL in 21s`, exit 0, no `error:`/`warning: `;
  `configureCMakeDebug[arm64-v8a]`+`buildCMakeDebug[arm64-v8a]`, `[armeabi-v7a]`, `[x86_64]` all
  executed (none `UP-TO-DATE`); both APKs rewritten (07:43:56) and each carries
  `lib/<abi>/libosmscout_map_cairod.so` for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- [x] 5.3 Run the gate: `./gradlew test -PforceTests --no-build-cache`.
  Verify: per-module tallies quoted from the XML, zero failures. This delta has no Kotlin/Java change, so
  the `:app` suites of both flavors are what it owes; a whole-suite `AppNotIdleException` in a Compose
  class outside the delta is attributed, not counted against it (`ki_processing_failures.log`, 2026-10-13;
  `TODO.md` §195).
  **Closed with a recorded deviation** (2026-10-16), in the shape the owner already accepted for
  `archive/2026-10-16-fix-search-cancellation`: a zero-failure aggregate is **not obtainable on this tree**
  for a reason that is not this change (`TODO.md` §195, re-diagnosed and measured on a quiet host by the
  session that owns it: `:app` automotive alone cascades to 38 `AppNotIdleException` in ~20 min, no
  assertion failures, main thread spinning in `android.os.TestLooperManager$…poll` at ~68 % CPU).
  Full record: `evidence/archive-gate-record.md` — what my run produced, what it voids, what it does not
  claim, and what this change proves instead. In one line: no automotive tally exists from my runs (the
  build aborts on the mobile flavour), and the delta has no Kotlin/Java file any `:app` case could exercise
  (the unit tests run against the host JNI stub), so the forced, focused, falsified evidence below is what
  this task rests on.
  **Blocked on 2026-10-15**: the working tree carries three other changes in flight
  (`distinguish-routing-failures`, `nav-status-progress-rings`, the map-manager source selector; ~75
  modified/untracked files, including `openspec/specs/routing-failure-reporting`, which is not in
  `tools/feature-list/specs.json`). Runs 1-4 failed only in those changes' cases — `ShippedSpecClassificationTest`,
  `NavigationDetailsOverlayTest.highlightFollowsProgress` (`AppNotIdleException: Compose did not get idle
  after 2711583 attempts in 60 SECONDS`), `PluralFormatTest`, the search-cancellation cases — plus once in
  the documented `:core` diagnostics-log flake (`TODO.md` ~L2376, passes alone). **No failure named a file
  of this change.**
  ⚠ **Attempt 5 is void, and the collision was mine to own**: `./gradlew test -PforceTests --no-build-cache`
  (pid 1036964) ran at the same time as another session's `--rerun-tasks` gate (pid 1025947) and was
  cancelled — exit 143 (SIGTERM), **no `BUILD …` verdict line**, so its partial output (16 ×
  `MapManagerSourceSelectorComposeTest`, 2 × `BasemapSectionComposeTest`, `ShippedSpecClassificationTest`) is
  recorded as void and is not evidence. My "is anyone building?" check used
  `pgrep -fa "GradleWrapperMain|GradleDaemon"`, the pattern `TODO.md` item 62 records as unable to match
  (the wrapper runs as `java … -jar gradle-wrapper.jar`); logged in `ki_processing_failures.log` (2026-10-15).
  ⚠ **Attempt 6 executed, not green — and its XMLs are void** (2026-10-16 07:51:16 start, tree checked quiet first:
  `pgrep -cf 'gradle-wrapper\.ja[r]'` → 0):
  `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest -PforceTests --no-build-cache`
  → `BUILD FAILED in 41m 38s`, gradle exit 1. The **mobile** flavor ran and reported **40 failures on the
  console**: 39 × `androidx.test.espresso.AppNotIdleException: Compose did not get idle … in 60 SECONDS`
  across `MapManagerSourceSelectorComposeTest` (15), `RouteSummaryStepSelectionTest` (8),
  `MapUpdatesSectionTest` (7), `NextTurnOverlayTest` (6), `BasemapSectionComposeTest` (2),
  `NavigationStateOverlayComposeTest` (1) — all classes of the other in-flight changes, and the redness of
  exactly these classes inside a whole-suite run is `ki_processing_failures.log` 2026-10-13 / `TODO.md` §195,
  where each class passes alone — plus 1 × `ShippedSpecClassificationTest` on a spec that the tree has since
  classified (then `routing-failure-reporting`, then `search-request-lifecycle`; `grep -c` in
  `tools/feature-list/specs.json` is 1 for both now).
  **The automotive flavor never executed** — the build aborted on the mobile task's failure, so
  `:app:testAutomotiveDebugUnitTest` does not appear in the log at all.
  ⚠ And the run collided again: another session ran a focused `:app:testMobileDebugUnitTest --tests
  *ShippedSpecClassificationTest*` while mine was in flight, so `app/build/test-results/testMobileDebugUnitTest/`
  holds **one** XML, timestamped 07:52:56 — one minute into my run. Under `ki_processing_failures.log`
  2026-10-08 (two GradleDaemons in one project dir) those XML tallies are void; only the console failure
  list above is this run's. Attribution evidence for any XML in this tree has to be read, not assumed.
  **Zero failures is not obtainable here**: the structural §195 redness alone forbids it in a whole-suite
  `:app` run, and the delta has no Kotlin/Java file that any of those classes could exercise (the `:app`
  unit tests run against the host JNI stub). What this delta owes and has: both flavors' APKs with all three
  ABIs (5.1/5.2) and the native cases (3.1-3.3, falsified). Left open rather than recorded as green.
  ✅ **Attribution measured, not assumed** (2026-10-16, same tree): `MapUpdatesSectionTest` — 7 failures
  inside the whole-suite run — passes **alone**: `./gradlew :app:testMobileDebugUnitTest -PforceTests
  --no-build-cache --tests "com.naviveylin.ui.mapmanager.MapUpdatesSectionTest"` → `BUILD SUCCESSFUL in 12s`,
  1 XML, `7 tests, 0 failures`. So the whole-suite redness is the `ki_processing_failures.log` 2026-10-13 /
  `TODO.md` §195 phenomenon, not a defect of this delta and not necessarily of the other one either.
  ⚠ For the record, one trap avoided: the same command with the guessed package
  `com.naviveylin.ui.map.MapUpdatesSectionTest` returned `BUILD FAILED in 5s — No tests found for given
  includes`; a green there would have proven nothing.
  **Verdict** ✅: 5.3 closed on that evidence, not on a green aggregate — the deviation, its measurements
  and its citations are in `evidence/archive-gate-record.md`, and the one thing still missing for the
  archive is **not** this task: it is 6.1's on-device measurement.

## 6. Record the on-device measurement

- [x] 6.1 With the Andorra map installed on `emulator-5554`, record per surface that a known multi-word
  place name is drawn on more than one line and a known one-word name on one line — screenshot read by
  the owner, or a temporary coordinate-free diagnostics line carrying the line count of a label
  (numbers only, never coordinates; removed after the measurement) (spec: map-render — "Both surfaces
  receive wrapped labels", design "Device measurement").
  Verify: the recorded measurement, with the numbers, in this task.
  ✅ **Phone canvas measured on device** (2026-10-16, `emulator-5554`, x86_64 debug build, Andorra + basemap +
  north-rhine-westphalia installed, app at its stored Andorra street view): a temporary line in the
  Pango-less `MapPainterCairo::Layout` logged `lines/words/chars/prop/avg/maxc/minc/fit/fontbox/text` for
  every label of at least 10 characters. Instrumentation removed afterwards and the clean build reinstalled
  (`strings` on the packaged `libosmscout_map_cairod.so` → 0 occurrences of the marker).

  **Multi-word labels drawn on more than one line** — all at the shipped parameters (`maxc=15`, `minc=5`,
  `fit=1120`):

  | drawn on device | chars | words | lines | `prop` | `avg` | `fontbox` |
  |---|---|---|---|---|---|---|
  | `Comú d'Andorra la Vella` | 23 | 4 | **2** | 421 | 28 | 65 |
  | `Edifici Xavier Maestre` | 22 | 3 | **2** | 374 | 24 | 58 |
  | `5001 Príncep Benlloch` | 21 | 3 | **2** | 468 | 31 | 72 |
  | `Restaurante El Crosto` | 21 | 3 | **2** | 421 | 28 | 65 |
  | `5002 Plaça Guillemó` | 19 | 3 | **2** | 468 | 31 | 72 |
  | `Transparencia. Przejrzystość.` | 29 | 2 | **2** | 374 | 24 | 58 |
  | `Centre Històric d'Andorra la Vella` | 34 | 5 | **3** | 374 | 24 | 58 |
  | `Antiga presó de la Casa del Quart (segle 18)` | 44 | 9 | **3** | 374 | 24 | 58 |

  **One-word labels kept on one line**: `L'Alternativa` (13 chars), `L'Arrosseria` (12), `Broceliand` (10),
  `Versailles` (10) — every one of them `words=1, lines=1`.
  ⚠ **Discrimination limit, stated rather than papered over**: no single word in view was wider than its own
  bound (13 × ~28 px ≈ 364 px < `prop` 421), so this view does not *prove* the never-break-a-word rule — it
  is consistent with it. That half is discriminated by the host case (`Musterstrassenverzeichnis` wider than
  half the unwrapped width stays on one baseline, tasks 3.1–3.3).

  **The bounds measured on the real font, not assumed**: `avg` 21–83 px, `fontbox` 43–193 px, and
  `prop = max(min(fit, 15·avg), 5·avg)` holds on every logged line — 15·28 = 420 ≈ 421, 15·24 = 360 ≈ 374,
  15·31 = 465 ≈ 468, while the country label's 15·83 = 1245 exceeds `fit`, so there `prop = 1120`. In other
  words: **the 15-character cap is what binds for ordinary labels (321–468 px)** and the pixel bound
  (`fit` = the canvas short side) settles only the largest label. That is the answer to "is the value
  reasonable": yes for point labels, and it is a *soft* bound — an 18-character name still fits one line if
  its glyphs are narrow, which is why "nothing wraps" can be observed at a country view.

  ⚠ **Measured, and worth knowing: street names never wrap, by design.** Every street label logs
  `prop=-1`, i.e. `enableWrapping=false` — `LabelLayouter::RegisterContourLabel` passes it for path labels
  (`MapPainter.cpp:1078-1087`). `Antic Carrer Major` (18 chars) and `Carrer dels Canals` (18) are drawn on one
  line while point labels of the same length wrap. The wrapping contract covers regular point labels; a
  street name that should break is a different change.

  **Method notes (so the next session does not repeat them):** the AVD's auto-recenter (GPS fix,
  `RECENTER … vis=true`) overrides pan and zoom, so `input tap`/`input swipe` gestures and a rewritten
  `files/maps/viewport-andorra.json` did not change the view (the app overrode the stored magnification; only
  the centre took effect). The viewport file was restored to its original content
  (`{"centerLat":42.55299823398143,"centerLon":1.5579369780267456,"magnification":6.0}`) and no instrumented
  build is left on the device. A `uiautomator` UI dump is not available to this session: its `/sdcard` target
  path is refused by the harness path policy and `run-as … uiautomator dump` is killed.

  ✅ **Car surface measured too** (2026-10-16, AAOS AVD `Automotive_Distant_Display_with_Google_Play`,
  product `sdk_gcar_dd_x86_64`, `ro.build.characteristics=automotive`, app + `basemap` + `europe` installed,
  automotive flavour x86_64 debug build, `CarAppActivity` resumed): the same temporary line logged **217
  label lines** from the car's own render — same native entry, same `MapParameter` block.

  | car label | chars | words | lines | `prop` | `avg` | `fontbox` | `fit` |
  |---|---|---|---|---|---|---|---|
  | `Muhrmanns Berg` | 14 | 2 | **2** | 80 | 5 | 12 | 720 |
  | `Halde Deusenberg` | 16 | 2 | **2** | 80 | 5 | 12 | 720 |
  | `View Phoenix-West` | 17 | 2 | **2** | 80 | 5 | 12 | 720 |
  | `Kreuz Dortmund-West` | 19 | 2 | **2** | 89 | 5 | 13 | 720 |
  | `Aplerbecker Mark` | 16 | 2 | **2** | 200 | 13 | 31 | 720 |
  | `Stadtforst Vormholz` | 19 | 2 | **2** | 226 | 15 | 35 | 720 |

  **And the car view carries the discriminating one-word case the phone view lacked**:
  `Dortmund-Eichlinghofen` (22 characters, one word — the hyphens join it, no space) and
  `Dortmund-Dorstfeld` (18) are drawn on **one** line while their ink is wider than their own bound
  (`prop=89`, `avg=5`; 22 characters ≈ 130 px > 89 px). So on device, with real fonts, a word wider than the
  bound is **not** broken — the rule the host case (tasks 3.1–3.3) asserts, now observed. The longer
  hyphenated street names (`Willem-van-Vloten-Straße`, 24; `Schulte-Heuthaus-Straße`, 23) log `prop=-1`, the
  contour path again.

  **Parity, measured**: both surfaces ran `maxc=15`, `minc=5`, and the same `prop = 15 × avg` relationship
  (15×5 ≈ 80/89, 15×13 ≈ 200, 15×15 ≈ 226); they differ only in `fit` (720 car, 1120 phone) and in the
  labels their viewport contains. `fit` never bound in either car or phone case.

  **Cleanup on both devices**: instrumentation reverted to the committed file (submodule `git status`
  clean, 0 markers in both packaged `.so` files), clean mobile and automotive builds installed, the phone
  AVD's `viewport-andorra.json` restored byte-for-byte, and both AVDs' map data untouched.
  **Verdict** ✅: both surfaces recorded, both halves of the scenario carried by an observation — the task is
  complete.

## 7. Land the change

- [x] 7.1 Commit the four submodule files alone (this change's files; the tree also holds another
  session's uncommitted routing work), then `git ls-remote origin naviveylin-local` immediately before
  the push, push, and bump the gitlink in the parent repository (`Design.md` §5: the submodule is
  pinned and kept clean; uncommitted submodule changes are not built by CI or a fresh clone).
  Verify: `git status --short` clean in the submodule, `git diff --stat` in the parent shows the new
  submodule SHA.
  **Done** ✅ (2026-10-15): two submodule commits, pushed as a fast-forward —
  `292fce729 map-cairo: wrap a label at word boundaries without a text stack`,
  `e303556e2 client-java: state the line-wrapping bounds of the map render`.
  The app-side block shares its file with another session's uncommitted routing work, so only this
  change's 13-line hunk was staged (`git diff … > patch` → single `@@ -1544,6 +1592,19 @@` hunk →
  `git apply --cached`), leaving that session's 12 files / 787 insertions unstaged and untouched.
  `git ls-remote origin naviveylin-local` returned the base `3b29204e3` and
  `git merge-base --is-ancestor` confirmed no rewrite before the push; the push reported
  `3b29204e3..e303556e2 naviveylin-local`. The parent gitlink was bumped in
  `7b8c97c Update libosmscout submodule to the label word-wrapping commits` —
  `git ls-tree HEAD app/src/main/cpp/libosmscout` now reads `160000 commit e303556e2…`, equal to the
  submodule's `HEAD` and to the pushed remote tip.
- [x] 7.2 `openspec status --change label-word-wrapping` reports every artifact complete and
  `openspec validate label-word-wrapping` passes.
  Verify: `Progress: 4/4 artifacts complete`; `Change 'label-word-wrapping' is valid` with no warning.
  **Done** ✅: `openspec status --change label-word-wrapping` → `4/4 artifacts complete`
  (proposal/specs/design/tasks all `[x]`); `openspec validate label-word-wrapping` →
  `Change 'label-word-wrapping' is valid`, exit 0, no `[WARNING]` after the two oversized requirement
  texts were split and trimmed; `openspec doctor` → OpenSpec root ok.
- [x] 7.3 Independent review before archiving: a fresh read-only child checks each spec scenario against
  the diff and the gate XMLs (the writer may not be the reviewer), then the change is archived. This
  change closes no numbered `TODO.md` entry: the defect was reported directly, and no backlog item
  describes it (the one `wrap` hit in `TODO.md`, §167, is a font-scale note about a band's label, not
  about map labels) — so there is no entry to move to fixed-by.
  **Review run** ✅ (2026-10-15, fresh read-only child, report `review-label-word-wrapping.md`; reviewed
  parent `04dce74`, submodule `c8e340a87`, both this change's commits ancestry-checked, no repo writes):
  **5 of 7 scenarios PASS**, each with a discriminating case — wrap (case `Cairo wraps a multi-word label
  into the proposed width`), never break a word, below the minimum character count, glyph once per
  character (case `Cairo never breaks a word and keeps a short label on one line`), and the size the label
  reports. Scenario 6 (*both surfaces*) is PASS for its code half — both entry points reach the one
  `MapParameter` block (`OSMScoutClient.cpp:1602-1606`) — and **UNVERIFIED** for its on-device half, held
  by task 6.1. The review also confirmed the wrap is reached in the Pango-less branch the Android build
  compiles and that the pre-change file had no `SetLabelLine` call, and found no contradiction with any
  shipped spec or guideline. It returned **BLOCK** for archiving on one point, now fixed: the three
  tracked deliverables (§20, the `AGENTS.md` row, `TODO.md` §202) were uncommitted — landed in
  `a13be71`. Its three remaining notes are recorded, not defects: the draw path is read rather than
  asserted (task 2.3), the guideline bullets were over-broad (fixed in `a13be71`), and the `#if`-guarded
  cases can vanish in a tree with the polluting features header (`TODO.md` §202, task 3.2).
  **Archive decision** (owner, 2026-10-16): archived on the first review plus the device evidence recorded
  in 6.1. The focused re-review of the device half (task 6.1's measurements against scenario 6) **failed to
  launch** — `Failed to start async run 'd2f308b8-…': async runner did not produce a pid for cwd:
  /home/tim/projects/NaviVeylin` — on a host at load average 32.8 with 489 MB free RAM while the AAOS AVD
  was resident (~3.0 GB); that is a spawn failure, not a review result, and it was reported rather than
  worked around. The gap it left is exactly the scenario the first review marked UNVERIFIED, which 6.1 now
  carries with numbers for both surfaces, so the owner chose to close rather than re-review on the loaded
  host. 5.3 is closed with the recorded deviation in `evidence/archive-gate-record.md`; 6.1 is complete.
  No `TODO.md` entry to move to fixed-by.
