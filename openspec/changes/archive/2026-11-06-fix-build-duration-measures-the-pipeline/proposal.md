# The build-duration row measures the pipeline, not one Gradle invocation

## Why

Issue #778. Measured `2026-10-29 → 2026-11-04` (`gh api repos/Framstag/NaviVeylin/actions/runs/<id>/jobs`, the
item's own figures): the `Build debug APK` job went from **46m50s–50m10s** to **1h49m–2h06m**, and the whole
increase sat in one step the row cannot see — `Install vcpkg dependencies`, 102–109 s (runs 41303297916,
41182133057) against 3430 s / 3758 s / 4304 s (41810844107, 41585715604, 41687813264). Every Gradle side was
flat: `Build debug APK` 623 s → 622 s, `Run unit tests` 419 s → 453 s, `Generate coverage reports` 344 s → 371 s.
The row filed nothing, and it cannot: its measurements on record (issue #316: 410781, 336814, 444645, 380585 ms,
the last at 2026-11-03T10:43) are all clear, because they are the wall time of one Gradle invocation.

Root cause, one mechanism: `tools/sensors/metrics/build-duration.sh:30` reads `.durationMs` of
`build/gate-timings.json`, a record only a Gradle run carrying `-I tools/gate-timings.init.gradle.kts` writes
(`.github/workflows/build.yml:298`), and the published set carries no job's or step's wall time at all
(`tools/build-inputs.sh:104-126`, `.github/workflows/build.yml:355-365`), so every second a job spends outside
that one invocation is invisible to every declared row.

The owner recorded the closure on 2026-11-05T20:53:07Z (`<<<ITEM_DECISION` of the work package): **option 1, the
general net — with the producer settled as the fetcher, not a new pipeline, and the bound as an absolute
ceiling.** This change is that answer and nothing else.

Owning capability: `backlog-sensors`, "A row's declared input is published, checked against its producer, and
read for the commit under test" (`openspec/specs/backlog-sensors/spec.md:357`) — a row's measurement is only as
good as the record the pipeline publishes for it. Owning guideline: `guidelines/BacklogTooling.md` §9 "Checks"
(a check is a declared row over an input its pipeline publishes) and `guidelines/BacklogTooling.recipes.md` §9,
which states what the build's published record carries.

### The red case, on the run's base `bea00bf5da8d8c81304d9271e66715c40b13b714`

The nine paths this change touches are byte-identical between that base and the one the killed run measured
against: `git diff --stat 0fb7a802 bea00bf5 -- <the nine> 2>/dev/null` prints nothing, which is why the earlier
evidence's mechanism stands unchanged on this base — the red case below was re-taken on it, not quoted.

`evidence/red-on-HEAD.log`, tally `evidence/red-on-HEAD.xml` — `tests=2 failures=2`, `2026-11-06T14:06:45Z`:

```
$ bash tools/sensor-selftest.sh --case 'sensors#buildDurationCoversThePipeline'
  the run succeeds: build-duration: fingerprint=- verdict=error measurement=error-the-timing-record-proves-nothing issue=- action=posted nothing, the run fails
sensors: 1 finding(s) reported an error — nothing was written for them and the run fails: expected '0', got '8'
FAIL sensors#buildDurationCoversThePipeline
$ bash tools/build-inputs-selftest.sh --case 'inputs#theFetcherPublishesTheJobTimings'
  the fetch exited 2: fetch-build-inputs: unknown argument --jobs-record
FAIL inputs#theFetcherPublishesTheJobTimings
```

The first case is the item's own: a record whose every Gradle step is the healthy one's while
`Install vcpkg dependencies` is an hour longer fails to file, because no such record exists to be read. The
second is the producer's half of the same absence: the fetcher publishes no job or step wall time.

surface: 2 cases added, 2 rewritten, 1 fixture realigned, 1 registry row, 1 workflow step — counted, not estimated: `git grep -l 'build-duration' HEAD -- tools .github guidelines openspec/specs | wc -l` = 7 paths on the base, `git grep -l 'build-duration' -- tools .github guidelines openspec/specs | wc -l` = 8 on the changed tree (the recipe §9 this change edits carries the row's name), `git show HEAD:tools/sensor-cases.sh | grep -c 'build-duration'` = 8 before and after (`git grep -c 'build-duration' -- tools/sensor-cases.sh` = 8, the two realigned cases keep the row's name once each), `git show HEAD:tools/build-inputs-cases.sh | grep -c "^  '"` = 13 on the base against 14 on the changed tree, the producer's case added.

### This change's own numbers

`evidence/measure.txt`; the lines are the block's own output from `bash tools/loop/measure.sh --change
fix-build-duration-measures-the-pipeline`, kept rather than re-derived. Its `LOOP-MEASURE base=` line compares
this whole worktree — nine open changes deep — against `b114e2d0`, so it is the tree's size and **not this
change's**; this change's size is the nine tracked files above.

```
LOOP-MEASURE-CHANGE fix-build-duration-measures-the-pipeline scenarios=8 cases=8 unnamed=0
LOOP-MEASURE-SUITE cases=330 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-TALLY openspec/changes/fix-build-duration-measures-the-pipeline/evidence/sensor-selftest.xml tests=85 failures=0 time=0s
LOOP-MEASURE-TALLY openspec/changes/fix-build-duration-measures-the-pipeline/evidence/build-inputs-selftest.xml tests=14 failures=0 time=0s
LOOP-MEASURE-TALLY openspec/changes/fix-build-duration-measures-the-pipeline/evidence/red-on-HEAD.xml tests=2 failures=2 time=1s
LOOP-MEASURE-TALLY openspec/changes/fix-build-duration-measures-the-pipeline/evidence/structural-checks.xml tests=29 failures=0 time=0s
```

Eight scenarios in the delta and eight cases, no unnamed one: the two the added requirement states, and the six
the `## MODIFIED Requirements` block carries forward from the shipped requirement (one of them repointed to the
case task 4.2 moves, the rest unchanged and already named by a case). No compiled path is touched, so no Gradle
gate is owed and none was run: the 29 structural checks the change does owe — the three suites it edits or extends,
the `--list` declaration checks the gate's plan names, the metadata/action-runtime/doc-route/OpenSpec-hygiene/
portability/citation/sonar/vcpkg checks, `build-inputs.sh --check` and the readiness verdict — are
`evidence/structural-checks.log` and its tally `evidence/structural-checks.xml`.

## What Changes

- `tools/fetch-build-inputs.sh` — `--jobs-record <path>`: after the run of the commit under test is resolved and
  its artifact restored, read `repos/<repo>/actions/runs/<id>/jobs` and write every job's and every step's wall
  time, derived from the API's own `started_at`/`completed_at`, with the run id and commit beside them. A caller
  that does not pass the flag writes nothing, so the fetcher's second call (the scan's artifact) cannot answer
  with another pipeline's jobs.
- `tools/build-inputs.sh` — `build/job-timings.json` in `foreign_paths()`, so `--check` pairs the row with the
  producer that writes it and a registry row cannot name a record nothing publishes.
- `tools/sensors/metrics/build-duration.sh` — the measurement is the pipeline's: the longest job of the record,
  its identity the job's own name, and its claim names that job's longest step, so a step outside Gradle that
  grew is visible without the row reading step names of its own. The bound is the row's `threshold` read
  absolutely (milliseconds, `comparison: greater-than`); no recorded baseline is needed for the comparison.
- `tools/sensors/sensors.json` — the `build-duration` row: `input: build/job-timings.json`, `comparison:
  greater-than`, `threshold: 3600000` (one hour), `consecutive: 2` unchanged.
- `.github/workflows/sensors.yml` — the build's fetch passes `--jobs-record build/job-timings.json`; the rows
  that measure the build, and the `success` conclusion they are gated on, are unchanged.
- `tools/issues/fixtures/gh` — the jobs endpoint, so the producer is testable offline with no token and no
  network.
- `tools/sensor-cases.sh`, `tools/build-inputs-cases.sh` — the two cases above, and the two cases whose fixtures
  assert the old measurement (`sensors#buildDurationRow`, `run#aFilingSetsTheBaseline`) realigned to the record
  the row now reads.
- `guidelines/BacklogTooling.recipes.md` §9 — what the build's published record now carries and what the row
  measures.
- `openspec/changes/fix-build-duration-measures-the-pipeline/specs/backlog-sensors/spec.md` — the delta.

## Impact

- Affected files: `tools/fetch-build-inputs.sh`, `tools/build-inputs.sh`, `tools/sensors/metrics/build-duration.sh`,
  `tools/sensors/sensors.json`, `.github/workflows/sensors.yml`, `tools/issues/fixtures/gh`,
  `tools/sensor-cases.sh`, `tools/build-inputs-cases.sh`, `guidelines/BacklogTooling.recipes.md` and this
  change's own directory. No compiled file, no Gradle module, no native source, no profile key.
- The row's number changes unit of subject, not of unit: milliseconds before and after, but one Gradle
  invocation's wall time becomes the pipeline's longest job. The record issue's history (#316) carries the old
  numbers; the change's design says what that means for a reader and for the trend line.
- Rollback: revert the registry row and the four production hunks (`fetch-build-inputs.sh`,
  `build-inputs.sh`, `build-duration.sh`, `sensors.yml`); `foreign_paths()` loses the path and `--check` refuses
  a registry that still declares it, so the revert and the row's declaration move together. No fixture moves
  except the stub's jobs endpoint, which nothing else reads.
- Affected guidelines: `guidelines/BacklogTooling.recipes.md` §9 only.
