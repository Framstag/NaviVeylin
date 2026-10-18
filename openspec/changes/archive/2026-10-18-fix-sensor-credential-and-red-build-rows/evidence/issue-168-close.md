# Issue #168 — closure reason and evidence

**Archived change:** `openspec/changes/archive/2026-10-18-fix-sensor-credential-and-red-build-rows/`

## Reason

A red build no longer reddens the checks' pipeline. The fetcher reports the conclusion the build's run ended
with, and the pipeline runs the rows that measure the build only for a `success` conclusion while the
condition and repository rows still run — so "the build is red" stays exactly one row's verdict
(`broken-main`) and no row reports a failure a person would have to read as "the build was red, the check was
fine".

**Root cause (one).** `tools/fetch-build-inputs.sh` asked for a *completed* run, not a *successful* one, and
reported nothing about how the run ended, while `.github/workflows/sensors.yml` issued one `run.sh --due` that
attempted every due row. A red build cannot produce the coverage summary (Kover's report task is skipped once
its tests fail), the package (if it fails before the assemble step) or the timing record, and
`tools/build-inputs.sh --write` publishes the result record alone in that case (by design,
`wire-sensor-inputs` D10) — so on `0 3`, `0 4` and `0 6` every row that reads one of those outputs reported
the third outcome, wrote nothing and made the run exit 8: four red ticks a day whose redness said nothing
about the checks.

**The single resulting fix (mechanism (a) of the issue).** `tools/fetch-build-inputs.sh --conclusion-file
<path>` reports the run's conclusion beside the record (exit contract unchanged: 0 fetched, 3 not ready, 1
cannot tell); `sensors.yml` captures it and runs two row steps — `build-duration,package-size,coverage`
gated on `ready == 'true' && conclusion == 'success'`, and
`broken-main,pr-conflicts,hotspot,stale-citations` gated on `ready` alone; `tools/sensors/run.sh` learned
`--rows <id,id>` (comma-separated, repeatable) so a step runs one family of rows while each row's own cadence
still decides whether it runs, with an id the registry does not declare refused before any row runs. The
registry, its bounds and its cadences are untouched.

**Cases.** `workflow#aRedRecordRunsNoRecordRow` (the two steps' gates and lists out of the workflow, the lists
proven to be exactly the registry once each, no `--due` invocation left without `--rows`, and the pipeline's
own command lines driven against a red build's record in a throwaway tree: the other rows exit 0 with
`broken-main`'s verdict, the record rows — which the pipeline no longer issues — exit 8, kept as the reason
the gate exists), `inputs#reportsTheRunsConclusion`, `run#rowsSelectsSeveralRows`,
`run#rowsRefusesAnUndeclaredId`. Falsified by mutations `8.10`, `8.11` and `5.13`.

**The requirement the defect contradicts.** `backlog-sensors` said a run reads the record "so its verdict is
the measurement rather than a failed check" and its red-build cost was accepted in `wire-sensor-inputs` D10;
the shipped spec now states the red-build rule in three requirements ("A red build is not a failed check", "A
red build still runs the condition rows", "The fetcher reports the run's conclusion"), added rather than
rewritten, each scenario naming its case.

## Evidence

Red on HEAD (`../../evidence/red-on-head.log`, HEAD `bf6f5d7`, 2026-10-18) — the issue's own measured command,
a tree carrying the record a red build publishes (`app/build/gate-result.json` alone):

```
$ bash tools/sensors/run.sh --due --now 2026-10-18T00:00:00Z
broken-main: fingerprint=4c0fc623… verdict=violation measurement=failure issue=- action=filed      exit=0
$ bash tools/sensors/run.sh --due --now 2026-10-18T03:00:00Z
build-duration: fingerprint=- verdict=error measurement=error-no-timing-record issue=- action=posted nothing, the run fails
sensors: 1 finding(s) reported an error — nothing was written for them and the run fails           exit=8
$ bash tools/sensors/run.sh --due --now 2026-10-18T04:00:00Z
package-size: fingerprint=- verdict=error measurement=error-no-package-artifact issue=- action=posted nothing, the run fails
exit=8
$ bash tools/sensors/run.sh --due --now 2026-10-18T06:00:00Z
coverage: fingerprint=- verdict=error measurement=error-no-coverage-report issue=- action=posted nothing, the run fails
exit=8
```

Green (`../../evidence/green.log`):

```
$ bash tools/sensor-selftest.sh
sensor-selftest: all 40 cases passed (JDK 27, offline, no token)          # was 4 of 40 red on HEAD
$ bash tools/build-inputs-selftest.sh
11 cases, 0 failed                                                        # was 1 of 11 red on HEAD
$ bash tools/sensor-mutations.sh
sensor-mutations: 11 behaved, 0 did not
$ bash tools/build-inputs-mutations.sh
build-inputs-mutations: 13 behaved, 0 did not
$ yq -r '.jobs.sensors.steps[] | select(.run != null) | .name + " :: " + (.if // "-")' .github/workflows/sensors.yml
Run the rows that measure the build :: steps.record.outputs.ready == 'true' && steps.record.outputs.conclusion == 'success'
Run the condition rows and the repository rows :: steps.record.outputs.ready == 'true'
```

## What this closure does not cover

One `workflow_dispatch` of `sensors.yml` on `main` with `apply: false` is owed after the merge: its guard
refuses every ref but the project's main branch (`tools/sensors/guard.sh`, `guard#refusesOffMain`), so the
end-to-end record of the two steps over a live `sensor-inputs` artifact is the first scheduled or dispatched
run on `main` after this lands, not something this branch can produce. The offline substitute is
`workflow#aRedRecordRunsNoRecordRow` plus `inputs#reportsTheRunsConclusion`, both quoted above. No other
residue: the registry, the bounds and the cadences are unchanged, and nothing was left open as a known
defect.

## The closure run

```
$ tools/issues/update.sh 168 --status fixed-by
https://github.com/Framstag/NaviVeylin/issues/168
exit=0

$ tools/issues/close.sh 168 --reason ... --evidence ...
missing-eligibility-label: agent/eligible absent
close: refusing to close #168 — the gate did not pass, so nothing was posted
exit=3

$ tools/issues/update.sh 168 --comment-file <this file's reason, quoted as a comment>
update: #168 already carries  — no label write
exit=0
```

The comment is posted on the item (author `Framstag`, 2026-10-18T17:41:46Z); the item carries
`status/fixed-by` and stays **open**, because the `agent/eligible` opt-in is a person's write — every write
path refuses the label — and the gate refuses a closure without it. The decision to close is therefore the
owner's, and the change is archived either way.
