# Proposal — fix-sensor-late-tick-window

## Why

One defect, measured on `main` (`4bd0504`, 2026-10-18), issue #166: **a scheduled tick GitHub fires late
selects no row**, so the run is green, nothing is measured, and a repeatedly delayed tick is
indistinguishable from a run with nothing due.

**Root cause (one).** `tools/sensors/lib.sh:241`'s `sensors_cron_due` matched a row's five-field cron against
the *one* minute the run started in, and `tools/sensors/run.sh:100`'s selection loop asked that question per
row and nothing else. With the schedule restored as `0,30 * * * *` and the registry's rows on minutes 0 and
30, a tick that starts in minute 07 is a minute no row declares, so the selection is empty: `no rows selected
at 2026-10-18T00:07:00Z`, exit 0. GitHub documents that a `schedule` trigger may fire late under load, and the
archived `wire-sensor-inputs` report had already recorded this measurement (its issue #166 line).

**Evidence (the defect's own measured commands, saved in `evidence/red-on-head.log` §1):**

```
$ bash tools/sensors/run.sh --due --dry-run --now 2026-10-18T00:00:00Z --json
{"row":"broken-main","fingerprint":"83bb457d…","verdict":"clear","measurement":"success",…}   exit=0
$ bash tools/sensors/run.sh --due --dry-run --now 2026-10-18T00:07:00Z --json
no rows selected at 2026-10-18T00:07:00Z                                                      exit=0
```

The change's cases reproduce it: `bash tools/sensor-selftest.sh` on HEAD → **3 of 43 cases failed**
(`run#aDelayedTickSelectsTheRowsItWasScheduledFor`, `run#theWindowDoesNotSelectThePreviousTicksRow`,
`workflow#theTickDeclaresItsWindow`), every one of the 40 pre-existing cases green; the three debug lines are
quoted in `evidence/red-on-head.log` §2.

**The single resulting fix.** The selection is a **window** rather than a single minute.
`sensors_cron_due <cron> <epoch> [<minutes>]` is due when the expression matches any minute in a window
`<minutes>` wide that ends at the tick's own minute — the tick's minute and the `<minutes> - 1` before it —
with the default of one minute keeping the punctual single-minute call shape unchanged.
`run.sh --due --within <minutes>` declares it and refuses a value that is not a count of minutes rather than
silently narrowing it, and `.github/workflows/sensors.yml` passes `--within 30`: the tick's own cadence, so a
tick delayed by up to 29 minutes still measures the rows it was scheduled for while two consecutive ticks'
windows tile the hour (00:00 covers 23:31–00:00, 00:30 covers 00:01–00:30) and select no row twice.

**The requirement the defect contradicts.** `backlog-sensors`'s scenario *The schedule fires without a
person* states that "the rows whose declared cadence is due to run are selected and measured" — read as the
tick's own minute, that sentence is what the shipped `sensors_cron_due` implemented, and a late tick
contradicts it. The requirement is **modified**, not rewritten: its description and every other scenario stay
byte-identical, the scenario above gains the late tick and the window, and two scenarios are added beside it
(*The window measures a row once*, *The pipeline declares the window its ticks select over*), each naming the
case that exercises it.

**Not double-measuring.** A window as wide as the cadence makes adjacent windows disjoint, so a row's minute
belongs to exactly one tick's window. A tick delayed beyond its own cadence can reach into the next tick's
window; the repeat it causes is a second measurement of the same condition and stays inside the registry's
`consecutive` hysteresis — the row is not closed on a repeat, and no new issue is filed for a condition the
previous tick already reported (`design.md`, D2).

## What Changes

- **`tools/sensors/lib.sh`** — the per-minute matcher splits out as `sensors_cron_minute` (unchanged body,
  unchanged contract) and `sensors_cron_due <cron> <epoch> [<minutes>]` becomes the window predicate: it walks
  the `<minutes>` minutes now and back, returning 0 on the first match, 2 for a malformed expression or a
  window that is not a count of minutes. The two-argument call keeps its meaning (one minute).
- **`tools/sensors/run.sh`** — `--within <minutes>` (default 1) is the window `--due` selects over; a value
  that is not a whole number of minutes, or is zero, is refused with exit 2 before anything is read, because a
  silently narrowed window is the defect. The usage block and the flag list state the window.
- **`.github/workflows/sensors.yml`** — both row steps pass `--within 30` (the tick cadence the schedule
  `0,30 * * * *` declares), and the header's schedule paragraph names the value and why.
- **`tools/sensor-cases.sh`** — three cases: the delayed tick that still selects the row it was scheduled for
  (and the unwindowed late tick kept as the defect's own measurement), the window's far edge and the tick
  that does **not** re-select the previous tick's row (plus the refusal of a window that is not a count of
  minutes), and the workflow's declared window proven to be its schedule's own cadence. One pre-existing
  case's row-step pattern is widened (`--due .*--rows`) because the step's invocation now names its window
  before its rows; the assertion it makes is unchanged.
- **`tools/sensor-mutations.sh`** — mutations `8.12` (the window is ignored), `8.13` (the pipeline drops its
  declared window) and `8.14` (the refusal is disabled), one per new bound and refusal.
- **`openspec/specs/backlog-sensors/spec.md`** — one requirement modified when this change archives (`The
  checks run in a pipeline of their own, pinned and least-privileged`): its shipped text and nine scenarios
  untouched, *The schedule fires without a person* widened to the window, two scenarios added.

**Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
resource — the tools, one workflow and one spec. The registry, its rows, their bounds and their cadences are
untouched. A punctual tick behaves exactly as before (`--within` omitted is the same one-minute question), so
the only behaviour that changes is the one that was broken: a late tick measures instead of reporting nothing.
Rollback path: revert this change's commit — the window, the flag and the workflow's `--within 30` disappear,
`sensors_cron_due` asks about one minute again, and §1 of `evidence/red-on-head.log` reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at all,
so no Gradle build and no test suite is owed (`guidelines/Build.md` §2, scope block); the verification is the
sensor tooling's own offline suite, its mutation harness and the structural checks named in `tasks.md`.

**Guidelines.** `guidelines/Backlog.md` §9 owns the checks' doctrine and was read for this change: it states
the trigger cap, the credential, the identity and the record, and it names a cadence only as one of the
registry's columns — nothing there contradicts the window, and no guideline section states how a due row is
selected, so no guideline is edited (`Design.md` §12: a rule goes where the measurement lives, and this
measurement is a case in `tools/sensor-cases.sh`).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: one requirement modified — `The checks run in a pipeline of their own, pinned and
  least-privileged`. Its shipped description is unchanged, nine of its scenarios are copied whole, *The
  schedule fires without a person* now holds for a late tick, and two scenarios are added. The delta lives at
  `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine and its pipeline)
- `tools/sensors/lib.sh` — `sensors_cron_minute` (split out), `sensors_cron_due` (the window)
- `tools/sensors/run.sh` — `--within <minutes>`, its refusal, the selection that passes it
- `.github/workflows/sensors.yml` — `--within 30` on both row steps, the schedule paragraph

Cases and their harness
- `tools/sensor-cases.sh` — three cases, and the row-step pattern of `workflow#aRedRecordRunsNoRecordRow`
- `tools/sensor-mutations.sh` — mutations `8.12`, `8.13`, `8.14`

Docs and specs
- `openspec/specs/backlog-sensors/spec.md` — gains the modified requirement when this change archives (the
  delta lives at `specs/backlog-sensors/spec.md`)

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's own gate (43 cases)
- `bash tools/sensor-mutations.sh` — the change-time falsification of every bound it adds (14 mutations)
- `bash tools/build-inputs-selftest.sh` (the workflow's tick cron is asserted there too),
  `bash tools/check-portability.sh`, `bash tools/check-doc-routes.sh`, `bash tools/build-inputs.sh --check`,
  `yq -e '.' .github/workflows/sensors.yml`, the CI hygiene step's replication
- Not run, deliberately: no Gradle build or test suite (no compiled file is touched; `guidelines/Build.md` §2,
  scope block), and no live `workflow_dispatch` of `sensors.yml` (its guard refuses every ref but `main`, so
  the end-to-end record of a late tick is the first scheduled run on `main` after this lands — recorded in
  `tasks.md` and the closure reason)
