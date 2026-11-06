# Design — the build-duration row measures the pipeline

## Root cause

The row's only input is `build/gate-timings.json`, whose `.durationMs` is one Gradle invocation's wall time
(`tools/gate-timings.init.gradle.kts:105-108`), and no artifact the build publishes carries a job's or a step's
wall time (`tools/build-inputs.sh:104-126`). A job therefore grows an hour in a step outside that invocation and
every declared row keeps reporting the flat number it measures.

## The fix

Four edits and one declaration, each the smallest shape the recorded decision allows.

| file | edit |
|---|---|
| `tools/fetch-build-inputs.sh` | `--jobs-record <path>`: read `repos/<repo>/actions/runs/<id>/jobs`, write each job's and step's `durationMs` from the API's own instants, with `runId` and `commit` beside them |
| `tools/build-inputs.sh` | `build/job-timings.json` in `foreign_paths()` — published by the fetcher, paired with the row by `--check` |
| `tools/sensors/metrics/build-duration.sh` | measure the longest job of the record; bound = `SENSORS_THRESHOLD` read absolutely |
| `tools/sensors/sensors.json` | `input`, `comparison: greater-than`, `threshold: 3600000` |
| `.github/workflows/sensors.yml` | the build's fetch passes the flag |

**The producer is the fetcher, as recorded.** A running job cannot read its own step durations: the jobs API is a
post-run read, and the fetcher already resolves the build run of the commit under test and holds the read-only
token (`actions: read`) that answers it. Nothing new is scheduled, no second workflow exists, and the record is
paired with the commit by construction — the fetcher writes it only for the run it just restored the artifact of.

**The measurement is the longest job.** An absolute ceiling applied to every job is the same condition as one
applied to the longest one, so one finding carries it: the measurement is that job's wall time, its identity the
job's own name from the record (the row's existing `job` fingerprint field), and its claim names that job's
longest step with that step's number. A reader sees which step grew; the row reads no step name of its own.

**The bound is absolute, as recorded.** `threshold: 3600000` — one hour, over a largest measured healthy job of
50m10s (3010 s, the item's figure for run 41303297916) and a regression that reached 6540–7560 s. Arithmetic on
the item's own figures, both operands named: 3600000 / 3010000 = 1.196, so the healthy ceiling sits 19.6 % under
the bound, and 3600000 / 6540000 = 0.55, so the regression's first day ran 81.7 % past the bound. That is a bound in
project terms, not a multiple of the previous measurement. The engine files a violation on the first run past the bound; `consecutive:
2` keeps its existing meaning, which is the hysteresis of the *closing* (`tools/sensors/run.sh:589-600`), so the
first tick that sees a job over an hour files, and a fixed job closes after two consecutive runs inside it.

## Blast radius

- The record is a new path in the checks' tree only (`build/job-timings.json`, gitignored with `build/`); the
  build pipeline uploads nothing new and no artifact changes shape.
- The row's number keeps its unit and changes its subject: from one Gradle invocation's wall time to the
  pipeline's longest job. The record issue (#316) carries the old numbers as comments; the report's trend line
  compares markers of one condition, and the condition's identity changes with this change (`job=gate` →
  `job=<the longest job's name>`), so no old marker is compared against a new number as if they measured the
  same thing.
- `--check` is the bound on the declaration: dropping the registry row without dropping the fetcher's flag leaves
  an unpublished declaration refused, and the reverse leaves the row reading a path nothing writes — both are
  visible in the gate's own `bash tools/build-inputs.sh --check` and in the two cases.

## Rollback

Revert the registry row, `build-inputs.sh`'s `foreign_paths()` line, the fetcher's flag, the metric script and
the workflow's flag together; the shipped rows then read `build/gate-timings.json` again, which the build still
writes. Nothing else in the tree depends on the record, and no case besides the two this change adds, and the two
it realigns to the new record (`sensors#buildDurationRow`, `run#aFilingSetsTheBaseline`), reads it.

## Alternatives the evidence eliminated

- **A new post-run pipeline that uploads the record.** The fetcher is already that pipeline (it runs post-run,
  per commit, with `actions: read`), so a second workflow would be a second credential and a second ready-ness
  for one read — the recorded decision names the fetcher as the producer for exactly this reason.
- **`baseline-multiple` on the job's wall time.** The item measures why it fails: it needs one run to be 50 %
  worse than the run before, and drift is never 50 % worse than the day before. The regression was 118 % worse
  than its baseline — which a multiple would have caught on the second day and a ceiling catches on the first.
- **A row per step.** A step name is not a project condition, identities would be unbounded, and a renamed step
  would silently become a new condition; the one fact the pipeline owes is that no job takes over an hour.
- **A row per job (one finding each).** The same pipeline condition would post one comment per job per tick on
  one record issue, and any job crossing the ceiling would be the longest one anyway; the longest job is the
  condition, so one finding carries it.
- **The probe's outcome as a published input plus a `boolean` row** (the item's narrow net). The recorded
  decision chose the general net: it closes only the one failure mode #761 already fixed, and the next
  non-Gradle regression would still run unseen.

## Open decisions

none
