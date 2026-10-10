# Apply evidence — `generate-feature-list`

Change report for the apply pass. Each section is filled in by the task that produces it; a task's
evidence is here only once that task is complete.

## 1. Classification data

### 1.1 `tools/feature-list/areas.json`

13 areas, one `carOnly` (`car-only`, heading *In Your Car*), `order` 10…130, surface vocabulary
`["phone","car"]`. Verified: `jq -e '.areas|length'` → `13`; `jq '[.areas[]|select(.carOnly)]|length'`
→ `1`; no duplicate id; `build-tooling` is the only area whose members are all `userVisible: false`,
so it renders no heading (spec `feature-list-generation` — an area holding only internal capabilities
gets no heading).

### 1.2 `tools/feature-list/specs.json`

153 entries, equal to the shipped set. Verified independently of the tool:

```
jq -e '.specs|length' tools/feature-list/specs.json                      -> 153
diff <(jq -r '.specs|keys[]' specs.json | sort) \
     <(openspec list --specs --json | jq -r '.specs[].id' | sort)        -> identical, no diff
jq '[.specs[]|select(.userVisible)]|length'                              -> 99 visible / 54 internal
every area id in specs.json resolves against areas.json                  -> no unresolved id
```

Per-area distribution (specs / user-visible): routing 26/19, map-interaction 21/20, favorites 15/14,
map-appearance 15/3, positioning 12/6, search 12/12, offline-maps 8/4, settings-legal 7/5, device-fit
6/6, car-only 5/2, object-info 5/5, handoff 3/3, build-tooling 18/0.

The `--check-classification` form of this check is the tool's (task 2.4); the numbers above are an
independent check of the same data, produced before the tool existed.

### 1.3 Gray-zone decisions, with the deciding phrase quoted from each spec's `## Purpose`

Every id below had a classification that its spec-id prefix does not decide. The quote is a verbatim
fragment of that spec's Purpose. Ids not listed are decided by prefix (`build-*`, `ci-*`, `auto*`,
`car-*`) or are unambiguously a user-facing feature by name.

**A. Internal although the spec touches something a user can see** — the spec describes a technique,
contract, mechanism or reliability guarantee rather than something the user chooses or gains.

| spec id | area | surfaces | deciding phrase |
|---|---|---|---|
| `app` | build-tooling | — | "Main Android application module with Gradle build, Jetpack components, and NDK/CMake integration." |
| `auto` | build-tooling | — | "Android Auto support module providing turn-by-turn navigation display on car screens" |
| `auto/screen-observation` | build-tooling | — | "Defines the state-observation lifecycle of the Android Auto screens" |
| `auto-startup-hardening` | car-only | car | "Ensures the Android Auto session starts reliably on real head units" |
| `car-host-fault-isolation` | car-only | car | "Keeps the car host alive and responsive while the NaviVeylin car session runs" |
| `cross-variant-ui-parity` | car-only | phone, car | "Requires that similar user-facing elements … are labeled and styled identically" |
| `auto-diagnostics` | settings-legal | phone, car | "Captures crash and Android Auto session diagnostics on-device" |
| `auto-map-renderer` | map-appearance | car | "Render libosmscout map tiles to the Android Auto display using `MapTemplate`" |
| `map-render` | map-appearance | phone | "Render a downloaded libosmscout map onto a Compose Canvas using the JNI render() method" |
| `map-canvas-screen` | map-interaction | phone | "Initial full-screen composable with empty map canvas placeholder" |
| `map-download-infrastructure` | offline-maps | phone | "Android-side wiring for map downloads — Hilt module providing `MapDownloadManager`" |
| `basemap-cancel` | offline-maps | phone, car | "Guarantees that cancelling a basemap download always reports `\"Download cancelled\"`" |
| `basemap-discovery` | offline-maps | phone, car | "Detect whether the map provider hosts a world basemap at the well-known basemap path" |
| `basemap-loading` | offline-maps | phone, car | "Load the world basemap as an overlay database" |
| `canvas-overrun` | map-appearance | phone | "Eliminate unnecessary native re-renders during small pan gestures" |
| `double-buffering` | map-appearance | phone | "Eliminate visual flicker and stale-map artifacts by rendering to an offscreen buffer" |
| `render-performance` | map-appearance | phone, car | "Reduce the native map render cost and the per-frame buffer copy overhead" |
| `tile-cache` | map-appearance | phone, car | "Reduce redundant native render calls by splitting rendered buffers into 256×256 tiles" |
| `gps-render-coalescing` | map-appearance | phone | "Reduce the number of full native map renders triggered by GPS updates" |
| `native-tile-data-cache` | map-appearance | phone, car | "Configure the capacity of libosmscout's per-database tile data caches" |
| `style-load-diagnostics` | map-appearance | phone, car | "Defines how a native map-style load reports the type names it cannot resolve" |
| `path-text-font-caching` | map-appearance | phone | "Ensures path text labels … use correct font size every render" |
| `marker-render-accuracy` | positioning | phone | "Ensure GPS location markers and favorite markers stay visually aligned with map geometry" |
| `daylight-map-palette` | map-appearance | phone, car | "Defines the daylight presentation's map colour contract" |
| `route-appearance` | map-appearance | phone, car | "Defines how the active route polyline must look on the map" |
| `gps-bearing-smoothing` | positioning | phone | "Moves bearing smoothing out of the render layer into the location layer" |
| `gps-provider-selection` | positioning | phone | "Defines which location provider the app uses for GPS fixes" |
| `gps-speed-priority` | positioning | phone | "Uses GPS-reported speed as the primary source for navigation speed display" |
| `speed-spike-filtering` | positioning | phone | "Rejects spuriously high speed values from the navigation engine" |
| `location-updates-lease` | positioning | phone, car | "Defines how several consumers in one app process share the device's location updates" |
| `fav-service` | favorites | phone | "Provides a Kotlin repository layer that wraps the JNI CRUD methods" |
| `settings-persistence` | settings-legal | phone | "Defines how the shared settings file is written when more than one surface can change settings" |
| `navigation-controller` | routing | phone, car | "Manages the turn-by-turn navigation lifecycle … Wraps the JNI `NavigationController`" |
| `navigation-engine` | routing | phone, car | "Defines the process-scoped navigation engine: exactly one native navigation controller per process" |
| `reroute-trigger` | routing | phone, car | "Defines when the system triggers a reroute after the vehicle leaves the planned route" |
| `routing-summary` | routing | phone | "Reusable route summary component" |
| `nav-hints-layout` | routing | phone | "Defines layout constraints for the navigation hints overlay" |
| `navigation-symbols` | routing | phone | "Provides a central Compose-based renderer that draws all navigation pictograms … instead of Unicode text" |
| `roundabout-renderer` | routing | phone | "Renders roundabout navigation symbols as Compose Canvas drawings" |

**B. User-visible although the spec reads like plumbing** — the spec gives the user a choice, an
indication or a guarantee they can perceive.

| spec id | area | surfaces | deciding phrase |
|---|---|---|---|
| `gps-fix-quality` | positioning | phone, car | "the accuracy tiers shown to the user" |
| `download-wake-lock` | offline-maps | phone, car | "Keep screen on and prevent app hibernation or sleep during active map downloads" |
| `search-result-ranking` | search | phone, car | "so that a query naming a place exactly returns that place first" |
| `i18n-l10n` | device-fit | phone, car | "so the phone and Android Auto variants render in the device language" |
| `turn-instruction-localization` | device-fit | phone, car | "Localizes turn-by-turn instruction text for the phone UI and Android Auto" |
| `back-gesture` | map-interaction | phone | "back dismisses the topmost open sheet or dialog instead of exiting the app" |
| `viewport-persist` | map-interaction | phone | "so the map opens at the same location on app restart" |
| `keyboard-shortcuts` | device-fit | phone | "keyboard shortcuts for common map actions so power users can zoom and search" |
| `button-elevation` | device-fit | phone | "SHALL display a Material 3 elevation shadow" |
| `landscape-layout` | device-fit | phone | "rearranges controls for landscape mode" |
| `adaptive-zoom` | map-interaction | phone | "Provide immediate visual feedback during zoom gestures" |
| `smooth-zoom` | map-interaction | phone | "Make zooming visually continuous by easing the map display between the start and end zoom levels" |
| `location-options-ui` | map-interaction | phone | "Provides a location options button on the map screen that opens a full-width Material 3 bottom sheet" |
| `route-analysis` | routing | phone | "Defines how a calculated route is inspected on the map: moving through its steps" |
| `osm-attribution` | settings-legal | phone, car | "Displaying the OpenStreetMap attribution notice on the map" |

**C. Surfaces not derivable from the id** — an `auto*` id is not automatically car-only, and a
non-`auto` id is not automatically phone-only.

| spec id | surfaces | deciding phrase |
|---|---|---|
| `auto-deep-links` | phone, car | "Hand a destination from the phone to the car" |
| `auto-cross-device-sync` | phone, car | "Keep navigation state consistent between phone and car" |
| `share/location-receiving` | phone | "The phone app receives shared geo information … from other applications" (phone-only despite the handoff area) |
| `favorite-search` | phone, car | "on the phone search dialog and the Android Auto search template" |
| `address-book-search` | phone, car | "reachable from new menu entries on the phone app and on Android Auto" |
| `lane-guidance` | phone, car | auto/navigation-view Purpose: "lane guidance" in the host instruction panel |
| `map-styles` | phone, car | "on both the phone and Android Auto surfaces" |
| `zoom-controls` | phone, car | auto-map-layout Purpose: host action strips carry "the map controls (search, settings, zoom)" |
| `current-road-info` | phone, car | auto-map-layout Purpose: surface-drawn street name on the car display |
| `navigation-state-display` | phone, car | auto/navigation-view Purpose: "time/distance to destination" |
| `immediate-turn-instruction` | phone, car | shared navigation instruction pipeline |
| `gps-fix-quality` | phone, car | "the compass, the re-center buttons, the speed widget and the search scoping cannot disagree" |
| `reroute-route-visibility` | phone, car | "the shared navigation state carries the route geometry on both phone and car" |
| `dark-mode` | phone, car | "a dark map style sheet … (system night mode today, car dimming later)" |
| `render-mode-switch` | phone, car | shared native render path |
| `basemap-download`, `basemap-ui` | phone, car | basemap is an app-wide map layer, not a phone-only one |
| `search-free-text`, `search-result-ranking`, `poi-search`, `location-search` | phone, car | search runs in both templates |
| `osm-attribution`, `i18n-l10n`, `turn-instruction-localization` | phone, car | obligation or localisation applies to both variants |

**D. Findings that this change did not create** (to be filed in `TODO.md`, task 7.7)

- Two specs carry an unfilled purpose placeholder, so their capability cannot be classified from their text and was
  classified by name alone:
  - `navigation-ongoing-notification`: "TBD - created by archiving change fix-host-crash-residual-paths. Update
    Purpose after archive."
  - `render-mode-switch`: "TBD - created by archiving change render-mode-switch. Update Purpose after archive."

  Both are classified `userVisible: true`, so the catalogue phrases them from their requirement text rather than
  from a purpose. A `TBD` purpose is a documentation defect of a previous change and is filed for repair rather
  than fixed here.

### 1.4 Artifact corrections made during apply

Spec and design claims that were wrong, each found by a *case* rather than by review, and corrected in the change
before the tool was finished.

| what was wrong | where | corrected to |
|---|---|---|
| "`versionName` sorts chronologically as a string" — false, `N` is unpadded so `-1 < -10 < -2` | `design.md` D6 | the four components are compared numerically; the ordering scenario is the case that caught it |
| "`surfaces: [car]` means car-only, so those capabilities belong in the car-only area" — that is shape (A), which the owner rejected | `feature-list-generation` "Car support is a tag" | *surfaces* (where a capability is available) is separated from *the car-only area* (capabilities whose subject is the car platform); one ADDED requirement, 2 scenarios rewritten, 3 added |
| the cache key covers member spec digests | `design.md` D7 | it covers requirement-text digests only, so a prose-only edit does not dirty an area (matching "A prose-only edit is not a change") |
| the coverage obligation was **per capability key**, so a user-visible spec's plumbing requirement (`auto-destination-picker#PaneTemplate as root screen` was the one that made it visible) had to be mentioned by some bullet — which forces a model to write plumbing into a marketing document | reading the first assembled catalogue | the obligation is **per user-visible spec**: at least one bullet must reach each user-visible spec, and which of its requirements are merged away is an editorial decision. Requirement and its 3 scenarios rewritten |
| the figure gate read "a number, limit, format or named capability detail", of which only the figure half has a mechanism this project can defend | implementing the gate | split into two requirements — "A stated figure appears in a spec the bullet cites" (fails) and "Prose the run cannot verify is reported for review" (reports, never fails) — so no unenforceable `SHALL` remains |
| the source marker was a comma-separated key list | nine requirement names turned out to contain `, ` | one key per marker line, repeated per key; documented in the prompt file and at the top of `gate.awk` |
| the phrasing seam was a single injected command | the owner chose a subagent fanout | `--emit-bundles <dir>` + `--sections <dir>`, with `--phrase-cmd` kept for a one-shot wrapper and mechanical assembly as the degraded mode (`design.md` D3) |

Six failures are logged in `ki_processing_failures.log` (2026-10-08 21:05, 21:20, 21:24, 21:34, 21:38, 21:47).
Five of the six are the same mistake in different clothes: a construct I trusted instead of one a case could
falsify — a `sort`/`tail` idiom, an awk "record before this one", a jq `.` inside a piped argument, a missing `-r`
on `jq -Rrs`, and a verification that ran against a warm cache instead of the input it meant to test.

## 2. The capability index — `tools/gen-feature-list.sh`

### 2.1 CLI

```
$ bash tools/gen-feature-list.sh            # no --release
usage error on stderr, exit 2, nothing written
$ grep -c release-version tools/gen-feature-list.sh
0                                           # the version-state file is never read
```

The version is passed in for every run; `--check-classification` and `--list` are readings of the classification
rather than release runs and need no version.

### 2.2 Enumeration, keys, digests

```
tool:      capability keys: 929
openspec:  openspec list --specs --json | jq '[.specs[].requirementCount]|add'  ->  929
```

A capability key survives other specs changing (verified with the mutated-snapshot experiment in 2.10, where the
927 untouched keys were reported unchanged), and an in-flight change contributes no key because the index reads
`openspec/specs/` only — 20 changes are in flight and none of their capability specs appears in `openspec/specs/`.

### 2.3 Delta at requirement-text level

| mutation on the real tree | changed | unchanged | dirty areas |
|---|---|---|---|
| one sentence of `adaptive-zoom`'s `## Purpose` reworded | **0** | 929 | **0** |
| one requirement's text reworded | **1** (`adaptive-zoom#Zoom placeholder from scaled buffer`) | 928 | **1** (`map-interaction`) |

Both edits were reverted with `git checkout -- openspec/specs/`; `git diff --stat -- openspec/specs/` is empty.

### 2.4 Classification gate

| case | result |
|---|---|
| clean tree | `specs read: 153 / classified: 153 / unclassified: 0 / stale: 0 / car-only areas: 1`, exit 0, empty stderr |
| an entry deleted from `specs.json` | exit 1, names `zoom-controls`, no snapshot written (2.8) |
| an entry added for a non-existent spec id | exit 0, names `zzz-removed-capability` as stale, does not fail |
| a surface set to `tablet` | exit 1, `zoom-controls: tablet` |
| a user-visible spec given no surface | exit 1, `zoom-controls` |
| surfaces per capability | `--list` prints 929 rows; visible car 130, visible phone 311, visible both 141, internal 347; user-visible with no surface: 0 |

### 2.5 Snapshot and baseline selection

Written per version to `tools/feature-list/snapshots/<version>.json` (~177 KB for 929 keys). Baseline resolved by
numeric component comparison; requested versions absent from the snapshot set, which is the normal case:

| requested | baseline | why it matters |
|---|---|---|
| `2026-10-09-1` | `2026-10-08-10` | with `-1`, `-2`, `-4`, `-10` present, the lexicographic order would answer `-4` |
| `2026-10-08-10` | `2026-10-08-4` | `-4` is numerically below `-10` while sorting above it |
| `2026-10-08-3` | `2026-10-08-2` | ordinary predecessor |
| `2026-10-08-2` | `2026-10-08-1` | ordinary predecessor |
| `2026-10-08-1` | none | first ever run; establishes the baseline and owes no entry |

A later run decides every changed key reading only the snapshot and the shipped specs — the snapshot holds a
digest per capability and the area cache keys, and nothing else is read.

### 2.6 Run report

```
# second run, next version, no spec change
added: 0   changed: 0   removed: 0   unchanged: 929   dirty areas: 0
baseline: tools/feature-list/snapshots/2026-10-08-2.json

# after one requirement's text changed
changed: 1   dirty areas: 1
```

The 18-line report is the whole of `spec-feature-index` R6: specs read, keys, added/changed/removed/unchanged,
dirty areas, area count, unclassified and stale ids, prompt files, baseline and version.

### 2.7 Model-free and the time bound

The index path invokes no language model: there is no model call anywhere in the tool, and prose is left to the
`--phrase-cmd` seam (group 3). With no phrasing command configured the run completes and reports — this is the
degraded mode. Wall time for the full 153-spec set, `/usr/bin/time` unavailable so measured by shell `time`:

```
full run (writes a snapshot):  real 0m5.8s
report-only run:               real 0m3.5s
```

Both are inside the 30 s the spec requires; the report-only figure is the index's own cost.

### 2.8–2.10 Revert-checks

Each is one mutation, failure first, then restore and green. The three failures are logged.

| task | mutation | the named case's expected result | observed under mutation | after restore |
|---|---|---|---|---|
| 2.8 classification gate | delete the `zoom-controls` entry from `specs.json` | gate fails naming the id and writes nothing | `exit 1`, `unclassified shipped spec id(s): zoom-controls`, no snapshot for that version written | `--check-classification` exit 0, 153/0/0 |
| 2.9 baseline selection | replace the numeric sort with a lexicographic one | `2026-10-09-1` must select `2026-10-08-10` | selected `2026-10-08-4` | selected `2026-10-08-10` |
| 2.10 delta granularity | store the spec's digest as every one of its capabilities' digests | a prose-only edit must report 0 changed | baseline written with the mutated tool, then the `## Purpose` reworded: `changed: 2` — `adaptive-zoom#Render timing metrics` and `adaptive-zoom#Zoom placeholder from scaled buffer` | prose-only edit reports `changed: 0`, requirement-text edit reports `changed: 1` |

2.9's mutation is the defect that actually occurred, not an invented one. 2.10's mutated snapshots
(`2098-01-01-1`, `2098-01-02-1`) were removed after the check.

The snapshot set written while verifying these checks was removed afterwards: a snapshot records "this version
was generated", and none of those versions was released. Task 7.1 writes the real baseline.

## 3. The catalogue — `FEATURES.md`

### 3.1 Prompt files and the phrasing seam

`tools/feature-list/prompts/area-section.md` (composition and merge rules, the marker format, the fact rule) and
`prompts/tone.md` (register and vocabulary). Both are committed, and the prompt digest covers every file under
`prompts/`, so editing either invalidates every area without anyone bumping a version by hand.

```
prompt files: 2
# after appending one newline to prompts/tone.md, with the cache cleared:
dirty areas: 13        # every area, as "An instruction change dirties every area" requires
```

The seam has three fillings (`design.md` D3): `--sections <dir>` for a subagent fanout, `--phrase-cmd` for a
one-shot wrapper, and mechanical assembly when neither is given. A run with a sections directory that is missing one
area fails naming that area rather than mixing mechanical text into a marketing document.

### 3.2 Cache, dirty detection, whole-file write, no timestamp

| check | result |
|---|---|
| no-op run, next version, nothing changed | `dirty areas: 0`, catalogue byte-identical (`011986d54d63c1f3` both runs) |
| full re-render of all 13 areas from a sections directory holding the same text | assembled document byte-identical to the cached one (`cmp` clean) |
| generation timestamp | `grep -nE '[0-9]{4}-[0-9]{2}-[0-9]{2}\|generated (on\|at)\|[0-9]{2}:[0-9]{2}' FEATURES.md` — no match |
| cache sections written | 12 files, each headed `<!-- cachekey: <64 hex> area: <id> -->` |
| write is atomic | assembled to a temp file, then `mv`; a failing gate writes nothing |

### 3.3 The prose gates

All four failure modes and both positive cases, each a mutation of a sections directory with the cache cleared so
the mutated file is genuinely read:

| mutation | expected | observed |
|---|---|---|
| a bullet's marker line deleted | fail | `bullet carries no source marker (line 851): Ambient light sensor option`, exit 1, no catalogue |
| a key repointed to another spec, leaving its own spec unreached | fail | `user-visible specs no bullet reaches: immediate-turn-instruction`, exit 1, no catalogue |
| an invented figure `77777` appended to a bullet | fail | `figure in none of the cited specs: 77777 \| Ambient light sensor option 77777 m`, exit 1, no catalogue |
| a figure `2` appended to a bullet citing `adaptive-zoom` (whose spec contains `2`) | pass | exit 0, zero failures |
| an unsourced word `quantum` appended, marker untouched | report, stay green | `unsourced word: quantum \| Ambient light sensor option quantum` on stdout under "prose worth a reviewer's eye (not a failure)", exit 0, catalogue written |
| a bullet reworded with its marker unchanged | pass | exit 0 — the check is on facts, not phrasing |

`dark-mode/spec.md` contains no digit at all, which is how the first positive-figure attempt failed and confirmed
the gate is precise rather than lenient.

### 3.4 Surface and car-platform rendering

```
## Routing & Turn-by-Turn      _Phone and car_        ... 12 headings, in areas.json order
## In Your Car                 _Car_
build-tooling: no heading (all 18 of its specs are internal)
bullets 582   markers 582
```

`Search & Destinations` carries the car-available-only `auto-search` under its own area, tagged `_Phone and car_`
through the area — the shape the owner chose — while `In Your Car` holds `android-automotive-os` and
`car-session-presence`, whose subject is the car platform. The five classification states are visible in
`--list`: visible car 130, visible phone 311, visible both 141, internal 347, user-visible with no surface 0.

### 3.5–3.8 Revert-checks

One mutation each, failure first, cache cleared before every run, then restore and green.

| task | mutation | expected | observed under mutation | after restore |
|---|---|---|---|---|
| 3.5 coverage | one spec's keys repointed so its spec is reached by no bullet | the coverage case fails | `user-visible specs no bullet reaches: immediate-turn-instruction`, exit 1, catalogue unchanged | green |
| 3.6 figure gate | a figure no cited spec contains | the invented-number case fails | `figure in none of the cited specs: 77777`, exit 1 | the same bullet with `2` (present in `adaptive-zoom`) passes |
| 3.7 cache key | the member requirement-text digests dropped from the key | the one-area-dirty case fails | after a requirement-text edit: `changed: 1` but `dirty areas: 0` — the area would not be re-rendered | the same edit gives `dirty areas: 1` |
| 3.8 byte-identity | a `Generated: <date>` line emitted in the header | the two-runs-same-file case fails | `e6a9e4b58fd334cd` vs `392a613e6601cc2f`, and the file showed `Generated: Do 8. Okt 21:16:57 CEST 2026` | identical digests, no date in the file |

### 3.9 What is not true of the catalogue yet

The committed `FEATURES.md` is the **mechanical** rendering: 582 bullets that are requirement names, one per
user-visible capability, because task 7.1's baseline run has no phrasing harness. It exercises every gate and the
whole pipeline, but it is not the marketing document the change exists for — that is task 7.2, driven by the
`feature-list` skill's subagent fanout (the owner's choice for the seam). Stating this here so the file on disk is
not mistaken for the finished article.

## 4. The release notes — `RELEASE-NOTES.md`

Selection is arithmetic over the delta and is model-free; only the phrasing is a seam, filled per area with
`<area id>.notes.md` from a sections directory, or by `--phrase-cmd` with `GEN_FEATURE_LIST_ROLE=notes`, or
mechanically. `--emit-bundles` writes `<area id>.notes.json` alongside the catalogue bundles, holding the changed
capabilities of that area and the removals it must mention, so a subagent fanout can fill both roles.

A four-step run on a clean tree, with `openspec/specs/` verified clean afterwards:

| step | report | entry written |
|---|---|---|
| baseline `2026-10-17-1`, no snapshot before it | `note: none owed for 2026-10-17-1; the version established the baseline` | none — the file is not created |
| `2026-10-18-1`, one spec's `## Purpose` reworded (prose only) | `note: none for 2026-10-18-1; nothing user-visible changed` | none |
| `2026-10-19-1`, one requirement's text reworded | `note: 2026-10-19-1 carries 1 changed and 0 removed user-visible capabilities` | one, under `### Map Interaction & Controls` |
| `2026-10-19-1` again | `note: none … nothing user-visible changed` | untouched, byte-identical |

### 4.1–4.4 Individual cases

| case | observed |
|---|---|
| first ever run writes no entry | `RELEASE-NOTES.md` did not exist after the run; verdict said *baseline established*, not *nothing changed* (4.4) |
| the run after the baseline writes an entry | `2026-10-19-1` produced one entry (4.4) |
| an internal-only change writes no entry and reports | `changed: 1`, `note: none …, nothing user-visible changed`, `verdict: nothing user-visible changed in …`, entry count unchanged, exit 0 (4.2) |
| the notes document is not created empty | after the baseline run and after the internal-only run, the file was still absent (4.2) |
| a new version with a change adds exactly one entry, newest first | entries `## 2026-10-12-6` then `## 2026-10-12-2`, and the older entry byte-identical to before (4.1) |
| re-running one version replaces its entry | one entry, byte-identical to the first run (4.1) |
| a capability merged is accounted for / changed capabilities grouped by area | each entry's bullets sit under `### <area heading>`, the heading the catalogue uses |
| a renamed requirement is a change, not a removal | `added: 1, removed: 1`, `1 renames`, note reports *1 changed and 0 removed*, entry names the new requirement under its area and no `No longer available` bullet appears (4.3) |
| a requirement gone from a spec that still exists | `No longer available: <name>` bullet carrying the vanished key (4.3) |
| an internal removal stays out of the entry | the same run's changed set was filtered to user-visible specs before the entry was built (4.2) |
| a spec leaving the shipped set is reported, not published | with `openspec/specs/zoom-controls` moved out: `spec left the shipped set, reported and not published as a change: zoom-controls` for each of its keys, and **no** entry for that version (4.3) |
| every vanished key is accounted for | the loop above iterates `$work/pairs.tsv`, one line per vanished key, and prints an accounting for each: rename, removal, or a departed spec |

### 4.5–4.7 Revert-checks

| task | mutation | expected | observed under mutation | after restore |
|---|---|---|---|---|
| 4.5 empty-release refusal | the empty-selection guard short-circuited (`elif false`), so a version with nothing to report still wrote an entry | the maintenance-only case fails | an internal-only change added a bare `## 2026-10-13-4` entry, 5 entries where there had been 4 | the same change writes no entry and reports the verdict |
| 4.6 idempotency | the writer appends this version's entry instead of replacing it | the re-running-a-version case fails | the same version run twice with a change each time left **two** `## 2026-10-14-3` headings | the same version run twice leaves one |
| 4.7 rename pairing | the pairing condition forced false, so no rename is ever detected | the renamed-requirement case fails | `added: 1, removed: 1, 0 renames` and the entry read `- No longer available: Zoom in button` beside the addition | `1 renames`, the note reports 1 changed and 0 removed, and the entry has no removal bullet |

4.5's first attempt taught something worth writing down: re-running a version whose selection is empty never
reaches the writer at all, so the mutation looked inert. The check only bites when the *same version is generated
twice with a change each time*, which is why 4.6's mutation is stated that way.

### 4.8 State left behind

`RELEASE-NOTES.md` does not exist, and `tools/feature-list/snapshots/` is empty, on purpose: a snapshot records the
state a version was generated from, and no version has been generated for a real release yet. The first real
baseline is written by task 7.1 at the next release; until a release carries a user-visible change, the notes
document is legitimately absent. Every test snapshot (versions `2026-10-08-*`, `2026-10-1[0-9]-*`, `2098-*`) was
removed, and `git status --porcelain -- openspec/specs/` is empty.

## 5. The fixture suite

### 5.1 `tools/gen-feature-list-selftest.sh`

```
$ bash tools/gen-feature-list-selftest.sh
… 87 case lines …
gen-feature-list-selftest: 87 passed, 0 failed          exit 0
```

The fixture is its own scratch tree — six synthetic specs (one of them nested, `probe/nested`), four areas (one
car-only), a classification, two prompt stubs and a hand-written sections directory whose every bullet's words are
drawn from the fixture specs, so that a fully-sourced case exists. It reads no project document, runs the tool as
a subprocess, and asserts on the report's numbers, on the documents' content and on the exit codes.

It found four real defects, three of them in the tool and one in a spec:

| defect | the case that found it |
|---|---|
| a **stale** classification entry for a spec that had left the shipped set made the coverage check demand a bullet for it, failing the run — which contradicts "a removed spec id is reported without failing on it" | `gates: a spec leaving the shipped set is reported in the run report` (coverage is now restricted to shipped specs) |
| the advisory word report printed to stdout, so it *failed* the run instead of reporting | `gates: an unsourced word is reported and the run stays green` |
| `--sections` was only consulted for an area needing a render, so a warm cache silently ignored a harness's prose | `regeneration: nothing changed means no dirty area` |
| my own assertion that a user-visible spec's every *key* must be covered, which forced plumbing into the catalogue (see §1.5) | `catalogue: an account for every user-visible spec` |

### 5.2 The suite can fail

One mutation — `surface_label` returning `Phone and car` for a car-only surface:

```
gen-feature-list-selftest: 86 passed, 1 failed
FAIL catalogue: the car-only area states the car surface
```

Restored, the suite is green again (87/0). A suite that cannot fail is not evidence, so this is the case that
says this one can.

### 5.3 Hermetic

Run from `tools/` rather than the repository root: 87 passed, 0 failed — the suite resolves everything from
`BASH_SOURCE`, so it does not depend on the working directory.

The external commands the tool, the suite and the two helper programs can invoke are, by whole-word audit of the
four files: `awk bash cat comm cp cut find grep head jq mkdir mktemp mv printf rm sed sh sha256sum sort tail wc`.
No `java`, no `gradle`, no `curl`/`wget`, no build and no network; the single occurrence of the string `python3`
in `gen-feature-list.sh` is line 14, the comment that states the project forbids it. `PATH` could not be stripped
for a run — the harness protects that variable — so "works without Gradle" rests on that audit rather than on a
run with Gradle removed. Stated that way deliberately.

## 6. The real runs — group 7

### 7.1 The baseline run, no phrasing command

```
$ bash tools/gen-feature-list.sh --release 2026-10-08-1        # the currently released versionName
version: 2026-10-08-1        specs read: 153        capability keys: 929
added: 929  changed: 0  removed: 0  unchanged: 0
dirty areas: 13             areas: 13              prompt files: 3
unclassified spec ids: 0    stale entries: 0       baseline: none
catalogue: FEATURES.md (582 bullets, 12 areas)
note: none owed for 2026-10-08-1; the version established the baseline
snapshot: tools/feature-list/snapshots/2026-10-08-1.json
exit 0, wall 13 s, stderr empty, RELEASE-NOTES.md absent
```

No phrasing command was configured, so the catalogue is the **mechanical** rendering at this point and the
baseline is recorded. The version label is the last released one: a snapshot records the state a version was
generated from, and any spec change made since that release is therefore inside the baseline — which is correct
here, because nothing has been released since and no note has been published.

### 7.2 The fanout over the 153 specs

Driven by `.pi/skills/feature-list` (the owner's choice of seam), one `worker` child per bundle, 12 children in
parallel:

| | |
|---|---|
| bundles written | 12 (`build-tooling` has no user-visible capability, so no bundle and no section) |
| bundle size | 8.6 KB (car-only) … 177 KB (map-interaction), ~810 KB total — one context window each |
| launches | **12**, equal to the dirty-area count |
| wall time | 440 s, and 470 / 580 / 605 / 632 s for the later fanouts |
| gates | exit 0, **zero** failures |

That fanout produced 239 entries, one per capability, which the owner reviewed as too low-level. The shape was
revised and the catalogue regenerated twice more: **the delivered document is 107 entries covering 582
user-visible capabilities, 5.4 per entry** — see §7 for what changed and why the count moved.

The first fanout was regenerated once, and that is worth recording: the advisory word report came back with 352
lines, and reading them showed a systematic defect — the children wrote British English (`favourite`, `centre`,
`colour`) where the project's specs, ids and UI strings are American (`fav-*`, `license-compliance`). Adding that
rule to `prompts/tone.md` regenerated every area (the prompt digest does that on its own) and the same report
fell to **104 lines**. The two remaining `licence` spellings are in specs that spell it that way
(`osm-attribution`, `about-dialog`), so a child matching its cited spec is right and the rule is not absolute.

`RELEASE-NOTES.md` is still absent, and deliberately: no version has yet carried a user-visible change, so the
tool wrote no entry and said so —
`note: none for 2026-10-08-2; nothing user-visible changed (0 renames, 0 keys whose spec left the shipped set)`.
The task's wording assumed both documents would exist; the notes half of the pipeline is proven instead by the
fixture suite's 26 note cases and by the real-tree sequence in §4, where a requirement edit produced an entry, a
re-run replaced it byte for byte, and an internal-only change wrote none.

### 7.3 The no-op re-run

```
$ bash tools/gen-feature-list.sh --release 2026-10-08-3
added: 0  changed: 0  removed: 0  unchanged: 929  dirty areas: 0
catalogue: FEATURES.md (239 bullets, 12 areas)
note: none for 2026-10-08-3; nothing user-visible changed
FEATURES.md 011986d5… before and after: byte-identical
```

All 12 areas were reproduced from the cache and **no phrasing command was configured for this run**, so the
model invocation count is zero: the whole run is model-free, which is the property the caching exists for.

### 7.4 No Gradle gate is owed

```
$ git status --porcelain
 M AGENTS.md      M README.md      M TODO.md
 M auto/src/test/java/com/naviveylin/auto/AutoMapRendererRenderCadenceTest.kt     <- another session, not this change
?? FEATURES.md   ?? guidelines/FeatureList.md   ?? tools/feature-list/
?? tools/gen-feature-list-selftest.sh   ?? tools/gen-feature-list.sh

$ git status --porcelain -uall | awk '$1=="??"{print $2}'      # 25 paths, every one of them
tools/gen-feature-list.sh  tools/gen-feature-list-selftest.sh  tools/feature-list/{areas.json,specs.json}
tools/feature-list/gate.awk  tools/feature-list/pair.awk  tools/feature-list/prompts/*.md
tools/feature-list/cache/*.md  tools/feature-list/snapshots/2026-10-08-1.json  guidelines/FeatureList.md
FEATURES.md
```

Every path is `*.md`, `*.json`, `*.sh` or `*.awk`. No source, test, build script, manifest, resource, Gradle or
native file, and the change's own directory does not appear at all because `.gitignore` carries
`openspec/changes/*/` — open changes are local, only archived ones are versioned. The one modified file that is
not this change's is the peer's `AutoMapRendererRenderCadenceTest.kt`, which moved between observations
(`core/…DiagnosticsLogWritePathTest.kt` at one point) — another session is editing this tree, which is also what
made 7.6 void.

By that prediction the build is unaffected, so 7.6 exists to falsify it rather than to be assumed.

### 7.5 The checks this change can affect

| command | result |
|---|---|
| `tools/check-doc-routes-selftest.sh` | exit 0 |
| `tools/check-doc-routes.sh` | `all 76 section references resolve` — the new route row and `FeatureList.md` resolve, and no guideline document is unrouted |
| `tools/gen-feature-list-selftest.sh` | `87 passed, 0 failed` |
| `openspec validate generate-feature-list --strict` | `Change 'generate-feature-list' is valid` |
| `openspec doctor` | `OpenSpec root: ok`, no dangling references |

The guideline's own verification: the 20 case names it cites in backticks were extracted and diffed against the
suite's 87 case names — every one exists. A rule citing a case that no longer exists would have shown up here.

### 7.6 The test gate — **void, not failed**

```
> Task :auto:testDebugUnitTest FAILED
> Task :app:testAutomotiveDebugUnitTest FAILED
java.nio.file.NoSuchFileException: …/build/test-results/<task>/binary/in-progress-results-generic.bin
BUILD FAILED in 3m 46s
```

No test reported a failure — the `*.xml` results that exist read `failures="0" errors="0"` throughout — and
`ps -ef` showed **two `GradleDaemon` processes**: another session was building this working tree at the same
time, and one build cleaned the test-results directory the other was writing into. The gate is therefore void as
evidence rather than failed, and it is logged with that diagnosis. Re-running it blindly is exactly what the
second attempt did, and it failed identically.

This task is **not** marked complete: a gate that produced no attributable result has verified nothing. It needs
a quiet tree — one builder per working tree, which is the rule `AGENTS.md` states and `TODO.md` §40 item 4 is
about. The change itself predicts it will pass (§7.4), and if it does not, that prediction was wrong.

### 7.7 Filed in `TODO.md`

| id | what | class |
|---|---|---|
| 154 | the pre-existing duplicate id: `guidelines/MapRendering.md` numbers two sections `## 14.` (renumbered from the collided §153, per the file's own convention) | improvement |
| 155 | nothing runs the generator at release time or on a schedule, and the phrasing seam's unattended harness is undecided | improvement |
| 156 | a release-note entry has no length limit, so it cannot be pasted into a store listing as it stands | improvement |
| 157 | nothing forces a change to classify the capability specs it archives | improvement |
| 158 | two specs still carry a `TBD` purpose placeholder, so their capabilities cannot be described from their own text | bug |
| 159 | the word report is unbounded (352 lines on the first run), so its signal is buried | improvement |

### 7.8 What this change does not owe

- **No on-device, emulator or logcat evidence, and no `pixel-check` measurement.** The change adds no runtime
  code: no UI, rendering, car-surface, template, lifecycle or threading behaviour changes, so there is no frame
  budget, surface or device behaviour to measure. The device is not part of this change's verification surface at
  all, and that is a statement about the change, not a gap in it.
- **No new JVM unit test.** The deliverable is a shell tool, two awk programs and data files. Its tests are the
  87-case fixture suite (§5.1), which is device-free and model-free; a JUnit test would have to shell out to the
  same commands and would prove less.

## 7. Shape revision — the catalogue at feature level

**The owner's review.** The first catalogue was too low-level: 239 one-line entries for 582 capabilities is a list
of requirements with an area heading on top. The requested shape is fewer, higher-level entries, each **one
paragraph covering several capabilities, led by a catchy keyword**.

### 7.1 What changed

| where | change |
|---|---|
| `feature-list-generation` spec | "bullet" became "entry" throughout; two ADDED requirements: *"Every entry leads with a keyword and a paragraph"* (≤ 6 tokens, drawn from the cited specs) and *"The catalogue is written at feature level, not capability level"* (at most one entry per four user-visible capabilities per area, rounding up, floor two — and **failing the run** above it). 27 requirements, 74 scenarios |
| `tools/feature-list/gate.awk` | reads an entry as a `- ` line plus its wrapped paragraph plus its markers; checks the bold keyword's presence, length and vocabulary; keeps figures and words over the whole paragraph |
| `prompts/area-section.md`, `tone.md` | the entry shape, the keyword examples, and the merge target: four to eight capabilities per entry, one to three sentences, split the entry rather than run past three |
| `tools/gen-feature-list.sh` | the density gate; the report line now states the density; the mechanical renderer emits one entry per spec, keyed by the first requirement's own name |
| `guidelines/FeatureList.md` §1, §6 | the shape, the density rule with its cases, and the removal-entry rule |

### 7.2 Three fanouts, and why the count moved

| fanout | entries | capabilities per entry | what it was for |
|---|---|---|---|
| 1 — one line per capability prompt | 239 | 2.4 | the first real build; the owner's review came from this |
| 2 — "three to six entries per area" | 72 | 8.1 | merged hard, but 17 capabilities in one paragraph ran to ~1 000 characters: a list again, with a keyword on the front |
| 3 — "four to eight capabilities per entry, one to three sentences" | **107** | **5.4** | the delivered shape |

Each fanout was one child per area, 12 children, 7–10 minutes, and each was assembled and gated before the next.
The report line makes the density readable every run:

```
catalogue: FEATURES.md (107 entries, 12 areas, 582 user-visible capabilities, 5.4 per entry)
```

The advisory word report tracked the prompt's quality as a side effect: 352 lines on the first fanout, 104 after the
spelling rule, 14 after the "use the words the requirement texts use" rule, and 125 on the delivered shape, whose
paragraphs are longer and so contain more prose. It remains a report, never a gate.

### 7.3 The repair loop the gate prescribes

The third fanout's sections were rejected on first assembly — **five keywords were one word over six**, and one
used a word (`fingers`) no cited spec contains. The gate named each one with its length and its word; the sections
were repaired and the run re-assembled with no new fanout. Two further keywords were still malformed on the second
attempt (`Auto-zoom follows your speed` introduced an unsourced word; `Speed, limit and street on the car` was seven
tokens), and the third assembly passed. That is the loop `guidelines/FeatureList.md` §6 describes working as
intended: a failing gate writes nothing, so each attempt left the previous document intact.

### 7.4 Four defects the revision found

| defect | how it surfaced |
|---|---|
| the keyword's stopword test was **case-sensitive**, so a keyword beginning `**Your …**` was rejected as an invented word | the real 107-entry run; no fixture keyword began with a stopword, and one now does |
| the **degraded** renderer took its keyword from the spec id, and `long-press-details`'s spec text never says "details" | the mechanical mode failed its own gate; the keyword now comes from the first requirement's name |
| the length rule counts **tokens**, not space-separated words — `Turn-by-turn` is three | a renderer that truncated to six words produced a seven-token keyword |
| the **density bound broke the mechanical mode on small areas** (two spec-grouped entries against a budget of one), which silently withheld the baseline snapshot and cascaded into twelve downstream case failures | the fixture suite; fixed with a floor of two entries |

The fixture suite grew from 87 to **101 cases** and all pass. All four are in `ki_processing_failures.log`
(2026-10-08 23:55), and the lesson recorded there is that a gate and the renderer that feeds it must agree on the
unit — a word, a token, an entry, a paragraph — and that every bound needs a case at its boundary and one at its
floor.

### 7.6 The test gate — green on the third attempt

Two earlier attempts failed with
`java.nio.file.NoSuchFileException: …/build/test-results/<task>/binary/in-progress-results-generic.bin` on
`:auto:testDebugUnitTest` and `:app:testAutomotiveDebugUnitTest`. No test failed, and `./gradlew --status` showed a
**BUSY** daemon: another session was building this same working tree, which is the one-builder-per-working-tree rule
`AGENTS.md` states. Both attempts were therefore **void as evidence, not failed**, and neither was counted.

With that daemon `STOPPED`:

```
$ ./gradlew test
BUILD SUCCESSFUL in 3m 7s
188 actionable tasks: 20 executed, 1 from cache, 167 up-to-date
```

| suite | tests | failures | errors |
|---|---|---|---|
| `:app:testMobileDebugUnitTest` | 1 746 | 0 | 0 |
| `:app:testAutomotiveDebugUnitTest` | 1 746 | 0 | 0 |
| `:auto:testDebugUnitTest` | 789 | 0 | 0 |
| `:core:testDebugUnitTest` | 448 | 0 | 0 |
| **total** | **4 729** | **0** | **0** |

That is 7.4's prediction confirmed by measurement: this change touched no build input, and the gate is green
without a single source, test, Gradle or native file in its diff.

## 8. Pitch revision — the catalogue is a selection

**The owner's second review.** Functional, but still not the right level: not enough marketing, not catchy, the
paragraphs too long — and, decisively, *not every feature is a marketing feature*. The list need not be complete;
it must carry what is thrilling rather than basic, and a selling point may appear more than once.

That reframes the contract. Four requirements changed and the machinery followed:

| before | after |
|---|---|
| "The catalogue accounts for every user-visible spec" — a completeness gate, which is the opposite of a pitch | **"The catalogue is a selection, not an inventory"**: a capability appears only if a highlight names its spec, every highlight is reached, and a highlight whose specs have all left the shipped set is reported as stale rather than required. A run with no highlight publishes nothing |
| the density bound (one entry per four capabilities) | **"An entry is one selling point"** — an entry's capabilities must belong to a single highlight, and one highlight may take two entries |
| *(no length rule; the paragraphs ran to 900 characters)* | **"A paragraph stays short"**: at most 320 characters, excluding the keyword, enforced by the run |

**The second curated input.** `tools/feature-list/highlights.json` now holds 19 selling points — `offline`, `plan`,
`turn`, `lane`, `reroute`, `search`, `poi`, `contacts`, `favorites`, `inspect`, `car`, `car-navigation`,
`car-search`, `handoff`, `style`, `smooth`, `driving`, `language`, `privacy` — each naming an area, a suggested
keyword and the specs it is written from. This is the second place a human judgement lives, next to the
classification, and the only place that decides what the document contains. It is a draft: it is meant to be edited.

### The delivered document

```
catalogue: FEATURES.md (19 entries for 19 selling points, 12 areas, about 286 chars per entry)
```

Every entry is one keyword and one or two sentences: *Works without a connection*, *Know which lane to be in*,
*The favorites you keep*, *No account, no tracking*. The basics — pinch to zoom, the back gesture, landscape
layout, the compass widget — are absent by construction, because no highlight names them.

### Five defects, four of them a check that quietly did nothing

| defect | how it surfaced |
|---|---|
| my shortlist check's label was **inverted**: it selected the reached highlights and printed them as unreached, so a correct document reported nineteen failures and a broken one would have passed | testing the jq in isolation with one real input line |
| an edit batch failed atomically and I re-applied only the edit I noticed, so the gate never received `-v entryspecfile` / `-v maxpara` and the two new checks were skipped — the length bound read "more than the 0 allowed" | the bound's own message; **twice in one session**, so the rule now is to re-read every file a batch touched |
| the mechanical renderer produced **zero** entries (`.[]` over an object yields values, not entries) and the run **passed**: with no entry nothing had anything to check, and the shortlist check skipped itself on an empty input file | the fixture case that asserted the mechanical output; a catalogue with no entry now fails outright |
| that renderer crashed on a highlight whose specs had all left the shipped set, and such a highlight failed the whole run — colliding with the release-notes rule that a departure is reported | the fixture's departed-spec case |
| a stray apostrophe in a jq comment closed the single-quoted shell string | `bash -n`, the one guard on this list that worked first time |

All five are in `ki_processing_failures.log` (2026-10-09 00:40). The suite went from 101 to **105 cases**, all
passing, with new cases for each shortlist rule, the length bound at its edge, and a capitalised stopword in a
keyword.

## 9. Archive

Archived to `openspec/changes/archive/2026-10-09-generate-feature-list/` after syncing three new capabilities into
`openspec/specs/`: `spec-feature-index` (6 requirements, 19 scenarios), `feature-list-generation` (12, 38),
`release-notes-generation` (10, 21). The main specs were created from the deltas' ADDED requirements with their
`## Purpose` sections copied verbatim — verified by digesting the requirement blocks on both sides and by checking
that no delta operation header (`## ADDED/MODIFIED/REMOVED/RENAMED Requirements`) survives in a main spec.
`openspec validate --specs` reports 158 passed, 0 failed; all three new specs are clean under `--strict` as well
(the 69 strict failures the repository has are pre-existing long-requirement warnings in specs this change never
touched).

**The archive then proved the change's own rule against itself.** Moving the change into the archive is what ships
its capability specs, and none of the three was classified in `tools/feature-list/specs.json` — so the tool this
change had just delivered refused to run on the repository (`unclassified: 3`, exit 1) until the three lines were
added. `guidelines/FeatureList.md` §4 states that the archiving change owes that line, and nothing enforced it at
the moment it mattered; `TODO.md` §157 predicted exactly this and now carries the instance. Two ids from another
in-flight change (`map-repository-source`, `map-source-selection`, owned by `allow-lan-http-map-repository`) are in
the same state, and the gate names them: it is the gate working, three changes late. The three lines added were
`build-tooling`, `userVisible: false`, no surfaces — the tool describes the build, not the product.
