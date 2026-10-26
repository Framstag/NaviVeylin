# fix-source-size-skips-the-change-tree

## Why

The row is right about the number and wrong about what the file is. Issue #670 reports

```
openspec/changes/archive/2026-10-25-fix-auto-map-renderer-file-size/evidence/AutoMapRenderer-head.kt is 2307 lines, past the row's bound of 1500
```

and that file is the *frozen head snapshot* the change `2026-10-25-fix-auto-map-renderer-file-size` kept as
review evidence: the 2 307-line file the change split, copied so a reviewer can compare the two states. No build
compiles it — `AutoMapRenderer.kt` itself is under the bound after the change, which is why the change archived.

**Root cause, one sentence:** `tools/sensors/metrics/source-size.sh:28` builds its file list with
`git ls-files -z '*.kt' '*.java'` and no pathscope, so every tracked Kotlin or Java file in the repository is a
candidate — the change tree's frozen evidence copies included — and the row files an item about a copy of a file
rather than about a file the project builds.

**Measured on HEAD** (`evidence/sensor-condition-on-head.log`, 2026-10-26T21:20:07Z, the real tree, the row's own
threshold):

```
$ SENSORS_ID=source-size SENSORS_ROOT=$PWD SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[]' \
    SENSORS_PROFILE=$PWD/tools/tooling.json bash tools/sensors/metrics/source-size.sh
source-size: lines=2307 path=openspec/changes/archive/2026-10-25-fix-auto-map-renderer-file-size/evidence/AutoMapRenderer-head.kt
{"path":"openspec/changes/archive/2026-10-25-fix-auto-map-renderer-file-size/evidence/AutoMapRenderer-head.kt",
 "fingerprint":"9ba4b081217dbf428415345330a46525a398211110918b615a5ac553142a5d9f","measurement":"2307",
 "verdict":"violation","baseline":"","title":"the source file is past the size bound",
 "claim":"openspec/changes/archive/2026-10-25-fix-auto-map-renderer-file-size/evidence/AutoMapRenderer-head.kt is 2307 lines, past the row's bound of 1500"}
```

One finding, and it is the evidence copy — the fingerprint is the item's own `<!-- sensor: source-size … -->`
marker, so this is the condition #670 carries and not a lookalike. The row cannot clear it by the mechanism its
own requirement names ("an item closes when that path drops under the bound"): the snapshot is frozen at 2 307
lines and stays there for as long as the archive holds its evidence.

**Red test on HEAD, before the fix** (`evidence/red-on-head.log`,
`evidence/red-on-head-sensors_sourceSizeSkipsTheChangeTree.xml`, `tests="1" failures="1"`, 2026-10-26T21:20:33Z):

```
$ bash tools/sensor-selftest.sh --case sensors#sourceSizeSkipsTheChangeTree
… 'Head.kt' unexpectedly present in: {"path":"openspec/changes/archive/a-change/evidence/Head.kt",…}
… the same for {"path":"openspec/changes/an-open-change/evidence/Case.kt",…}
FAIL sensors#sourceSizeSkipsTheChangeTree
```

The case builds a fixture repository with three files past the bound: a live `app/src/main/java/Big.kt`, an
archived change's `openspec/changes/archive/a-change/evidence/Head.kt`, and an open change's
`openspec/changes/an-open-change/evidence/Case.kt`. On HEAD all three are conditions; the case requires the live
file to stay one and the two frozen copies to be none.

**Owning capability and guideline.** `openspec/specs/backlog-sensors/spec.md`, requirement "A source file's size
is a declared check" (its scope scenario: "the unit and the language scope are the measurement's own, fixed by
the owner's recorded decision"); `guidelines/Backlog.md` §9 "Checks" — the paragraph that states the row's unit
and scope ("`source-size` measures **lines per tracked file** over `*.kt` and `*.java`, tests included and the
native sources out").

**Surface**, counted before the plan was fixed
(`grep -rl 'source-size' tools/*-cases.sh tools/sensors .github/workflows/sensors.yml guidelines/Backlog.md` →
`tools/sensor-cases.sh`, `tools/sensors/sensors.json`, `tools/sensors/metrics/source-size.sh`,
`.github/workflows/sensors.yml`, `guidelines/Backlog.md`): 1 case added, 0 cases rewritten, 0 fixtures realigned —
the fixture repository is built inside the new case (`scratch`), so no shared fixture and no registry row moves.
The registry (`sensors.json`) keeps its bound, its comparison and its fingerprint: only the measurement's own
file list changes, which is the half its header declares as the measurement's.

## What Changes

- `tools/sensors/metrics/source-size.sh` — the file list excludes the change tree
  (`:(exclude)openspec/changes/`), and the header states the exclusion as part of the scope the file owns.
- `tools/sensor-cases.sh` — one case, `sensors#sourceSizeSkipsTheChangeTree`, registered in the list and the
  dispatch table.
- `tools/sensor-mutations.sh` — mutation `8.28`: drop the exclusion, and the new case must go red.
- `openspec/specs/backlog-sensors/spec.md` — one sentence on the requirement and one scenario naming the case.
- `guidelines/Backlog.md` §9 — the sentence that states the row's unit and scope names the change tree as out.

## Impact

- No compiled file, no Kotlin and no native source: the gate's suites are the sensor tooling's own
  (`tools/sensor-selftest.sh` for the case, `tools/sensor-mutations.sh --list` for the registry's listing), and
  `tools/check-doc-routes.sh` for the guideline sentence. No build and no Gradle suite is run "for completeness".
- The archive keeps its evidence untouched: the fix moves the measurement's scope, never an archived change's
  file. Renaming the snapshot was the alternative and is eliminated in `design.md`.
- Rollback: revert the exclusion line in `source-size.sh` (the case goes red again) and the case with it; the
  registry, the fixtures and the archive are untouched either way.
