# Issue #166 — closure reason and evidence

**Archived change:** `openspec/changes/archive/2026-10-18-fix-sensor-late-tick-window/`

## Reason

A scheduled tick GitHub fires late now measures the rows it was scheduled for. The selection is a window of
minutes that ends at the tick's own minute, and the pipeline declares that window as wide as its own cadence,
so a late run is the same tick rather than a green no-op.

**Root cause (one).** `tools/sensors/lib.sh`'s `sensors_cron_due` matched a row's five-field cron against the
*one* minute the run started in, and `tools/sensors/run.sh`'s selection loop asked that question per row and
nothing else. With the schedule `0,30 * * * *` and the registry's rows on minutes 0 and 30, a tick that starts
in minute 07 is a minute no row declares, so the selection was empty: `no rows selected at
2026-10-18T00:07:00Z`, exit 0 — green, nothing measured, and a repeatedly delayed tick indistinguishable from
a run with nothing due.

**The single resulting fix.**

- `tools/sensors/lib.sh` — `sensors_cron_due <cron> <epoch> [<minutes>]` is due when the expression matches
  any of the `<minutes>` minutes ending at the tick's own minute (the per-minute matcher keeps its body as
  `sensors_cron_minute`); the default of one minute keeps the punctual two-argument call unchanged.
- `tools/sensors/run.sh` — `--due --within <minutes>` declares the window, and a value that is not a whole
  number of minutes, or is 0, is refused with exit 2 before anything is read rather than silently narrowed to
  one minute (the defect's own silence).
- `.github/workflows/sensors.yml` — both row steps pass `--within 30`, the tick cadence the schedule declares.
  A window as wide as the cadence makes consecutive ticks' windows tile the hour (`00:00` covers
  23:31–00:00, `00:30` covers 00:01–00:30), so a row's minute belongs to exactly one tick's window; a tick
  delayed beyond its cadence can only repeat a measurement the row's `consecutive` hysteresis absorbs.

The registry, its rows, their bounds and their cadences are untouched.

**The requirement the defect contradicts.** `backlog-sensors`'s scenario *The schedule fires without a person*
said "the rows whose declared cadence is due to run are selected and measured" — read as the tick's own
minute, that is what the shipped `sensors_cron_due` implemented and what a late tick contradicted. The
requirement is modified, not rewritten: the shipped description and nine of its ten scenarios are unchanged,
*The schedule fires without a person* now holds for a late tick and names the window it selects over, and two
scenarios were added beside it (*The window measures a row once*, *The pipeline declares the window its ticks
select over*).

## Evidence

Red on HEAD
(`openspec/changes/archive/2026-10-18-fix-sensor-late-tick-window/evidence/red-on-head.log`, HEAD
`4bd0504`, 2026-10-18) — the issue's own two measured invocations, and the change's cases before the fix:

```
$ bash tools/sensors/run.sh --due --dry-run --now 2026-10-18T00:00:00Z --json
{"row":"broken-main","fingerprint":"83bb457d…","verdict":"clear","measurement":"success",…}   exit=0
$ bash tools/sensors/run.sh --due --dry-run --now 2026-10-18T00:07:00Z --json
no rows selected at 2026-10-18T00:07:00Z                                                      exit=0
$ bash tools/sensor-selftest.sh
FAIL run#aDelayedTickSelectsTheRowsItWasScheduledFor      (run: unknown argument --within)
FAIL run#theWindowDoesNotSelectThePreviousTicksRow        (run: unknown argument --within)
FAIL workflow#theTickDeclaresItsWindow                    (expected '2', got '0')
sensor-selftest: 3 of 43 cases failed                       # 40 pre-existing cases green
```

Green (`openspec/changes/archive/2026-10-18-fix-sensor-late-tick-window/evidence/green.log`):

```
$ bash tools/sensors/run.sh --due --dry-run --within 30 --now 2026-10-18T00:07:00Z --json
{"row":"broken-main","fingerprint":"83bb457d…","verdict":"clear","measurement":"success",…}   exit=0
$ bash tools/sensor-selftest.sh
sensor-selftest: all 43 cases passed (JDK 27, offline, no token)
$ bash tools/sensor-mutations.sh
MUTATION 8.12: run#aDelayedTickSelectsTheRowsItWasScheduledFor red under the mutation, green after the restore
MUTATION 8.13: workflow#theTickDeclaresItsWindow red under the mutation, green after the restore
MUTATION 8.14: run#theWindowDoesNotSelectThePreviousTicksRow red under the mutation, green after the restore
sensor-mutations: 14 behaved, 0 did not
$ bash tools/build-inputs-selftest.sh           → 11 cases, 0 failed
$ bash tools/check-portability.sh               → OK — 42 engines and 2 templates name no project value
$ bash tools/check-doc-routes.sh                → all 89 section references resolve
$ bash tools/build-inputs.sh --check            → every declared input has a producer (4 rows checked)
$ bash tools/declared-cases-selftest.sh         → 12 passed, 0 failed
$ openspec validate fix-sensor-late-tick-window --type change   → valid, exit=0
```

No Gradle build and no test suite: the change touches no compiled file (no Kotlin, no C++, no Gradle logic, no
resource) — `guidelines/Build.md` §2, scope block.

## What this closure does not cover

A live late tick cannot be produced on demand — that is why the cases drive a fixture clock — and
`tools/sensors/guard.sh` refuses every ref but `main`, so a `workflow_dispatch` from a branch is not the
probe. The end-to-end observation is therefore the first scheduled run of `sensors.yml` on `main` after this
lands: its selection line names the rows the tick declared its window over. Nothing else is owed: the
schedule, the registry and the rows' bounds are unchanged, and no defect was left open as known.
