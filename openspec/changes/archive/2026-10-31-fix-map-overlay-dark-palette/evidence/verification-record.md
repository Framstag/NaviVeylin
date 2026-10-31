# Verification record — fix-map-overlay-dark-palette

Every number below is a command's output, kept beside the XML it came from (this directory). Times are UTC,
2026-10-31.

## 1. The red cases on HEAD (before any production change)

| case | XML | tally |
|---|---|---|
| `auto: AutoMapRendererMarkerDrawCacheTest` "theAccuracyRingBranchesOnTheDarkPresentation" | `red-head-auto-accuracy-ring.xml` | `tests=5 failures=1`, 09:29:07Z — "the accuracy ring's fill must follow the presentation. Actual: 438408947" (`0x1A2196F3`) |
| `app: LocationMarkerOverlayPaletteTest` "theDrawnAccuracyRingChangesWithThePresentation" | `red-head-app-accuracy-ring.xml` | `tests=1 failures=1`, 09:30:57Z — "0 of 24 sampled ring pixels differ (light=(64, 91, 120), dark=(64, 91, 120))" |

Both ran without any new API, so the defect is reproduced before the fix exists (the loop's Phase A rule).

## 2. Green, per task

| what | XML / directory | tally |
|---|---|---|
| `core: GpsMarkerPaletteTest` (4 cases) | `green-core-palette.xml` | `tests=4 failures=0` — ring hue 210.63° → 207.97° (Δ2.66°), luminance 0.2641 → 0.1420; dark dot luminance 0.3499 vs its halo over land 0.0210 |
| `app: LocationMarkerOverlayPaletteTest` (4 raster cases) | `green-app-accuracy-ring.xml` | `tests=4 failures=0` — 24/24 ring pixels differ between presentations; drawn radius 60.0 px and stroke thickness 2 px identical in both; light tone `(64,92,121)` against the shipped composite `(64,92,122)` |
| the mini map parameter's call sites (11 classes) | `app-plumbing/` | 84 tests, 0 failures |
| `app: MiniMapCurrentPositionPaletteTest` (3 cases) | `green-app-minimap-dot.xml` | `tests=2` for the drawn cases + 1 mapping case; drawing: light frame 70 px of the light dot / 0 px of the dark, dark frame 70 px of the dark dot / 0 px of the light, light frame 0 px of `#1A73E8` |
| `auto: AutoMapRendererMarkerDrawCacheTest` (6 cases) | `green-auto-accuracy-ring.xml` | `tests=6 failures=0` |
| focused set: 4 classes (`:core`) + 6 (`:app`) + 1 (`:auto`) | `focused-6.1/` | 11 XMLs, 63 tests, 0 failures |

## 3. The revert-check (task 6.3)

Mutation: `GpsMarkerPalette.accuracyFill(dark)` returns the light value for both presentations, marked
`// REVERT-CHECK MUTATION (task 6.3)`.

| step | XML | tally |
|---|---|---|
| mutated | `revert-check-6.3-red.xml` | `tests=6 failures=1`, 09:50:52Z — the named case `theAccuracyRingBranchesOnTheDarkPresentation` fails (`AutoMapRendererMarkerDrawCacheTest.kt:133`) |
| restored, `-PforceTests --no-build-cache` | `revert-check-6.3-green.xml` | `tests=6 failures=0`, 09:51:19Z |

No marker survives: `grep -rn 'REVERT-CHECK MUTATION' app/src core/src auto/src` prints nothing, which is what
`tools/loop/evidence-check.sh`'s C4 reads.

## 4. The JVM suite (the scoped gate this session ran)

`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` — **BUILD SUCCESSFUL in 4m 53s**, both `:app`
flavors, `:core` and `:auto`:

| module / variant | files | tests | failures |
|---|---|---|---|
| `:app` mobileDebug | 311 | 2219 | 0 |
| `:app` automotiveDebug | 311 | 2219 | 0 |
| `:auto` debug | 93 | 910 | 0 |
| `:core` debug | 66 | 653 | 0 |
| **total** | **781** | **6001** | **0** |

The per-file counts are `find app core auto -path '*/build/test-results/*' -name 'TEST-*.xml' | sed 's|/TEST-[^/]*$||' | sort | uniq -c`
and the tallies `grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' <dir>/*.xml | awk -F'"' '{t+=$2; f+=$6} END …'`,
both run on the run's own build directories. **That 3.3 MB / 781-file XML set is deliberately not versioned
here** — the tallies above are its record, and the classes this change touches keep their own XMLs in this
directory (`green-*.xml`, `red-head-*.xml`, `revert-check-*.xml`, `focused-6.1/`, `app-plumbing/`).

**Exclusion, named:** `./tools/loop/gate.sh` itself was not run here. This tree carries a second, unfinished
change (`git status --porcelain`: `AGENTS.md`, `.github/workflows/{build,sensors}.yml`, `guidelines/Backlog.md`,
`guidelines/{Backlog.recipes,Backlog.why,BuildGate,BuildGate.recipes,BuildGate.why}.md`, `tools/check-rule-routes*`,
`tools/doc-routes.json`, `tools/loop/{loop.conf,run.sh,prompts/reviewer.md}`, `tools/{sensor-cases,sensor-mutations}.sh`,
`tools/sensors/**`, `tools/tooling.json`), and `gate.sh --plan` derives its pathspec from the whole tree — 34
suites, including that change's new ones. A verdict from that run would be about two changes at once, so the full
gate stays owed by the loop's wrapper, which cuts its worktree from the base and sees only this change.

The checks this change's own paths do owe were run and pass: `tools/check-compose-sandbox-rule.sh` (73 Compose
test classes carry the sandbox rule), `tools/check-openspec-hygiene.sh` (28 open changes consistent),
`bash tools/gen-feature-list.sh --check-classification` (`unclassified: 0`).

`tools/check-doc-routes.sh` reports `guidelines/BuildGate.md` unrouted, and
`tools/check-doc-routes-selftest.sh` reports 2 of 32 cases failing — both from the *other* change's in-flight
document split (that document, its recipes/why layers and the checker's own edits are that change's paths; this
change edits only the text of `guidelines/UI.md`, and the checker reports `0 of 169 references do not resolve`).

## 5. Two red runs this session, and their attribution

1. **`JniStubSandboxRuleTest.noTestClassThatLoadsTheStubPinsTheSandbox` — mine, and fixed.** The first version of
   `MiniMapCurrentPositionPaletteTest` composed `MiniMap` (which loads the host JNI stub) while pinning
   `@GraphicsMode(NATIVE)`, which `guidelines/Build.md` §6 forbids: a stub-loading class must run in the default
   Robolectric sandbox. Fix: the widget's dot drawing and its palette mapping are extracted
   (`drawCurrentPositionMarker`, `miniMapMarkerColors`), the raster case draws those directly (stub-free, so the
   graphics pin is allowed), and the widget's mapping is asserted without a renderer
   (`theWidgetsMarkerColoursComeFromThePalette`). Re-run: `MiniMapCurrentPositionPaletteTest` +
   `JniStubSandboxRuleTest` + `MiniMapComposeTest` — BUILD SUCCESSFUL.
2. **`auto: AddressBookScreenTest.theAddressBookListFitsTheHostsCapacity` — not this change's, a flake under
   load.** It failed in the first (non-forced) full-suite run at 09:54:33Z — "one capacity query per screen
   start expected:<1> but was:<0>" (`AddressBookScreenTest.kt:189`), in a two-case class. The red XML was
   overwritten by the green rerun of the same task before it was copied here, so its tally is reported as the
   run's words rather than as a quoted `tests=`/`failures=` pair; the green rerun superseded it.
   Attribution per
   `guidelines/Build.md` §4d: (1) alone — green; (2) together with the classes this change added
   (`AutoMapRendererMarkerDrawCacheTest`) — green; (3) it did not reappear in the forced full run (4m53s, 6001
   tests, 0 failures), which is the rerun evidence. Mechanism ruled out: nothing this change touches is on that
   screen's path (the car change here is two accuracy-ring paint colours; the address book queries the host's
   list capacity). This tree keeps no flake list (`guidelines/Build.md` §4d), so the red run and the green rerun
   are recorded here together rather than retried silently.

## 6. Not run in this session

- **The full `tools/loop/gate.sh`** (section 4's exclusion).
- **`tools/loop/evidence-check.sh`'s final pass and the archive** (tasks 6.6) — after the device tasks.
