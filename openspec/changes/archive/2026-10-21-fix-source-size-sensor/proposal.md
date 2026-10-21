# Proposal — fix-source-size-sensor

## Why

**Observed (#196, verified in the tree today).** The registry declares seven rows and none of them measures a
source unit's size (`tools/sensors/sensors.json`, `rows[]`). The closest row is `hotspot`
(`tools/sensors/hotspot-report.sh:21-29`): it scores `size × change count` from `git log --numstat`, so a
5 094-line class that nobody touches scores zero and files nothing, while a 200-line file edited every week
files. Nothing under `tools/` measures lines or declarations per file or per class — `tools/sensors/metrics/`
holds `build-duration.sh`, `coverage.sh`, `package-size.sh` and `stale-citations.sh`, none of which reads a
source file.

**Consequence.** The only thing that notices a class too large to hold in one head is a human reading it, so
the refactor such a class needs is never a scheduled finding: it stays invisible until it lands in a change's
scope by accident.

**Root cause (one).** The registry's metric set has no size metric — every declared measurement reads the
build's outputs, the tracker, or the repository's *history*, and a size multiplied by a change count is zero
for exactly the file the metric is wanted for. So a large, static source file is not a condition any check can
report.

**The recorded decision is settled (2026-10-21, `ITEM_DECISION`).** The new row ships at **1 500 lines per
file**, over tracked `*.kt` and `*.java`, tests included, **file level**, **C++ out**; no ratchet, and a
per-class cut is the later refinement the item already names. Measured on HEAD
(`80df3a3cadec0c6c4961ef08220d08177f23a9b1`): `git ls-files '*.kt' '*.java' | xargs wc -l | sort -rn` gives
5 094 (`MapCanvasViewModel.kt`), 3 384 (`MapCanvasScreen.kt`), 2 295 (`AutoMapRenderer.kt`) and 2 086
(`AutoMapRendererTest.kt`) over the bound, and the whole native tree (`app/src/main/cpp`, `app/src/test/cpp`)
maxes at 140 lines — so the scope costs four conditions and excludes nothing that would fire.

**The red test.** Case `sensors#sourceSizeRow` in `tools/sensor-cases.sh`, run on HEAD with only the case in the
tree (2026-10-21T22:46+02:00):

```
$ bash tools/sensor-selftest.sh --case 'sensors#sourceSizeRow'
  the registry declares no source-size row
FAIL sensors#sourceSizeRow                                                   exit=1
$ bash tools/sensor-selftest.sh
sensor-selftest: 1 of 45 cases failed                                        exit=1
```

The suite's own summary line is the tally the loop's gate derives its JUnit record from, so the red case is
`tests=45 failures=1` (evidence: `evidence/red-suite-on-head.log`, copied beside the run's own
`red-on-head.log`). The case asserts the row's bound, comparison, fingerprint and class, then drives the real
script over a fixture repository: a 1 600-line `Big.kt` is a violation measured at 1 600, a 10-line
`Small.java` is no condition, a 5 000-line `.cpp` and a 4 000-line `.md` are outside the row's scope, the script
holds no threshold constant, and a tree inside the bound still prints a clear instead of nothing.

**The owning requirement and its guideline section.** Capability `backlog-sensors` ("A check is a declared
row, not a script"; "A check reports one of three outcomes, and only clearing closes"); guideline
`guidelines/Backlog.md` §9 "Checks", which states that a check is a declared row and already names "a size that
grew past its bound" as one of the checks' verdicts.

## What Changes

- **`tools/sensors/metrics/source-size.sh`** (new) — the row's measurement: lines per tracked `*.kt`/`*.java`
  file from `git ls-files`, one finding per file past `SENSORS_THRESHOLD`, one clear per recorded open
  condition that is now inside it, one row-level clear (`{"path":"none"}`) when the tree has no condition at
  all — because the runner reads a row that printed nothing as a failed check — and one `error` finding when
  git cannot list the tree, because an unreadable source list must not read as a clean one.
- **`tools/sensors/sensors.json`** — the `source-size` row: `cron "0 8 * * *"`, `threshold 1500`,
  `comparison greater-than`, `consecutive 2`, `class improvement`, `category build-and-harness`,
  `lifecycle file+close`, fingerprint `["path"]`. The bound and the unit live in the row, never in the script
  (design D9/D10), so retuning is one edit here.
- **`.github/workflows/sensors.yml`** — `source-size` joins the second row step's `--rows` list (the half that
  runs for any record, because it reads the repository and not a build output); the two lists still cover the
  registry exactly once, which `workflow#aRedRecordRunsNoRecordRow` proves.
- **`guidelines/Backlog.md` §9** — one paragraph: what the row measures, in which unit and scope, that the path
  is the identity, that the number is the owner's recorded decision, and that a file and not a class is the
  bound.
- **`tools/sensor-selftest.sh`** — the executable bit only (100644 → 100755). `tools/loop/loop.conf` maps the
  sensor pathspec at that script as a *command*, and `gate.sh` executes it directly, so a mode that only
  worked under `bash …` made the gate refuse to run the suite at all (`tests=0`, "keine Berechtigung"). Its
  three sibling suites already carry 100755; this is the same one-bit correction, found by this change's first
  gate run and recorded as `evidence/gate-attempt-1-permission.log`.
- **`backlog-sensors`** — one ADDED requirement with three scenarios, each naming
  `sensors#sourceSizeRow`.
- **Cases and falsification** — `tools/sensor-cases.sh` gains `sensors#sourceSizeRow` (registered in the array
  and the dispatcher); `tools/sensor-mutations.sh` gains mutation 8.17 (the comparison inverted must redden
  that case).
- **Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
  resource. The change adds one check; it reverses no owner decision — it implements the one recorded on the
  item. Rollback path: revert this change's commit — the row, the script, the workflow entry, the §9 paragraph
  and the spec requirement disappear together, and the tracker keeps the four items the row filed.
- **Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at
  all, so no Gradle build and no test suite is owed (`guidelines/Build.md` §2, scope block); verification is
  the sensor tooling's offline suite, its mutation harness, and the structural checks named in `tasks.md`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: one ADDED requirement ("A source unit's size is a declared check") with three scenarios,
  no shipped text or scenario removed. The delta lives at `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine and its pipeline)
- `tools/sensors/metrics/source-size.sh` — the new measurement (new file)
- `tools/sensors/sensors.json` — the `source-size` row
- `.github/workflows/sensors.yml` — the row's place in the second row step's list

Cases and their harness
- `tools/sensor-cases.sh` — the case `sensors#sourceSizeRow` and its registration
- `tools/sensor-mutations.sh` — mutation 8.17
- `tools/sensor-selftest.sh` — mode 100755, so the loop's gate can execute it

Docs and specs
- `guidelines/Backlog.md` — §9, the size row's paragraph
- `openspec/specs/backlog-sensors/spec.md` — gains the ADDED requirement when this change archives

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's gate (45 cases)
- `bash tools/sensor-mutations.sh --only 8.17` — the new bound's falsification
- `bash tools/check-portability.sh` — the new script names no project value (46 engines)
- `bash tools/check-doc-routes.sh` — the edited guideline's section references still resolve
- `bash tools/build-inputs-selftest.sh` — the registry the publisher checks is still one whose inputs it
  publishes (11 cases)
- Not run, deliberately: no Gradle build or test suite (no compiled file is touched;
  `guidelines/Build.md` §2, scope block), and no live `workflow_dispatch` of `sensors.yml` (its guard refuses
  every ref but `main`), so the first scheduled run of the new row is owed after the merge and recorded in
  `tasks.md`
