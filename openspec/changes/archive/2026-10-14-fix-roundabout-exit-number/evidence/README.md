# Evidence — fix-roundabout-exit-number

Captured 2026-10-14 on `emulator-5554` (Pixel_8 AVD, API 37, `sdk_gphone64_x86_64`) and in this working tree.

## jvm-focused-suites/

JUnit XML copied from `<module>/build/test-results/**/TEST-*.xml` after focused runs.

| file | tally |
|---|---|
| `TEST-com.naviveylin.auto.NavigationTemplateMapperTest.xml` | 66 tests, 0 failures — the class 7.5's and 7.6's revert-check mutations were run against |
| `TEST-com.naviveylin.core.ManeuverSymbolsTest.xml` | 11 tests, 0 failures |
| `TEST-com.naviveylin.core.TurnInstructionLocalizerTest.xml` | 31 tests, 0 failures |
| `TEST-com.framstag.libosmscout.client.RouteInstructionFieldsTest.xml` | 7 tests, 0 failures |

**Two files of this set are missing, and why.** `TEST-…ManeuverGlyphsTest.xml` (8 tests) and
`TEST-…RouteDescriptionScreenTest.xml` (2 tests) were green in the runs the tallies come from, but both were
deleted from `auto/build/test-results/` by two `:auto:testDebugUnitTest` invocations that failed with
`java.nio.file.NoSuchFileException: …/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin` —
a second Gradle build in this same working tree, which is the collision `TODO.md` §40 item 4 names. Their
tallies are recorded verbatim in `../tasks.md` (7.1, 7.5, 7.6). No fresh run was attempted after them: while
another builder holds the tree, re-running is what produced the collision (`guidelines/Build.md` §2, "one
builder per working tree").

## device/

Raw `adb shell am instrument -w -e class …` output and the app's own logcat for the two device classes. Every
install was `adb install -r -t`, so the AVD's map data survived each run (`files/maps`: `andorra`, `basemap`).

| file | what it is |
|---|---|
| `device-roundabout-instructions.txt` | `…RouteInstructionRoundaboutDeviceTest` → `OK (2 tests)` |
| `device-roundabout-instructions-logcat.txt` | tag `RouteDeviceTest`: `roundabout probe: routed=5 candidates=5`, `roundabout exits: total=42 numbered=42`, ordinals 1–4, `clockwise=[false ×42]` |
| `device-glyph-legibility.txt` | `…RoundaboutExitGlyphDeviceTest` → `OK (1 test)` |
| `device-glyph-legibility-logcat.txt` | tag `RoundaboutGlyphTest`: digit 10×13 / 11×13 / 14×13 px inside a 16 px ring at 48 px; 13×32 / 20×33 / 44×32 px inside 57 px at 168 px; ordinal 112 at 0.250 (48 px) and 0.143 (168 px) of the edge |

The 42 counter-clockwise roundabout exits are the device proof for the direction half of the fix: Andorra drives
on the right, so counter-clockwise is correct — and it is what the pre-change unconditional clockwise mapping got
wrong (7.2).

## Not captured here

- **The revert-check mutation outputs** (7.5, 7.6, 7.9) are recorded verbatim in `../tasks.md`: failure
  messages, case names and tallies. 7.5 and 7.6 mutated the JVM class whose XML is above; 7.9 mutated the native
  descriptor and its red was a device run (2 failures, "the route was calculated but the bridge delivered no
  instruction list"), which also found and fixed the unreachable JNI exception-handling path (submodule commit
  `3b29204e3`, pinned by main `6720e4e`).
- **The original red-on-HEAD case** for the first symptom (`expected:<3> but was:<0>`) — that XML was overwritten
  by later green runs in an earlier session; the tally is in `../tasks.md` task 1.1.
- **7.3's DHU observation** was not taken: `desktop-head-unit` is refused by the shell allowlist (exit 126). It is
  recorded in `../tasks.md` and filed as `TODO.md` §201.
