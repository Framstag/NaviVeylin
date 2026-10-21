# Design — fix-source-size-sensor

## The root cause

`tools/sensors/sensors.json` declares seven checks and none of them weighs a source unit. The nearest,
`hotspot`, computes `size × changes` per path from `git log --numstat`
(`tools/sensors/hotspot-report.sh:21-29`), and multiplying by a change count is precisely what zeroes a large
file nobody edits: the measurement's own factor removes the signal. Every other row reads a build artifact, the
tracker, or the API. So "this file is 5 094 lines" is not expressible as a condition by any row that exists —
one mechanism, and it is the absence of a size metric rather than a fault in one of the rows.

## The single fix

Add the size row:

- `tools/sensors/metrics/source-size.sh` measures lines per tracked `*.kt`/`*.java` file with
  `git ls-files -z | xargs -0 … awk`, so a path with a space survives and the tree costs a handful of
  processes; the line count is `awk`'s `FNR`-based record number rather than `wc -l`'s, whose total line is a
  translated word and would make the parser locale-dependent.
- `sensors.json` declares the row: `threshold 1500`, `comparison greater-than`, `consecutive 2`,
  fingerprint `["path"]`, `class improvement`, `category build-and-harness`, `lifecycle file+close`,
  `cron "0 8 * * *"` — a minute no other row owns, covered by the pipeline's `0,30 * * * *` tick.
- `.github/workflows/sensors.yml` runs it in the second step, the half that runs for a record a build did not
  complete successfully, because it reads the working tree and not a build output. The two steps' `--rows`
  lists still cover the registry exactly once.
- The script emits four outcomes: a violation per file over the bound, a clear per recorded open condition
  that is now inside it (the fingerprint read back from the issue's own record, so a closure names the identity
  the issue carries), one row-level clear keyed `{"path":"none"}` when it has printed nothing at all — as
  `pr-conflicts` does, because the runner's "the command printed no finding" would otherwise turn a clean tree
  into a failed check (`run.sh:456-458`, `backlog-sensors`, the three outcomes) — and one `error` finding when
  `git ls-files` itself fails, because a check that cannot read its own source list has determined nothing and
  must not read as cleared.

Scope, unit and bound are the owner's recorded decision of 2026-10-21: 1 500 lines, Kotlin and Java, tests
included, file level, C++ out. The bound is the row's (`sensors.json`, `threshold`); the unit (lines per file)
and the language scope (`*.kt`, `*.java`) are constants of the measurement itself, named in its header with the
decision they come from. Retuning is therefore one edit in the registry, while changing the unit or the scope is
changing the decision, not the row.

## Blast radius

- One new script under `tools/sensors/metrics/`; one row; one list entry in the workflow; one paragraph in
  `guidelines/Backlog.md` §9; one case and one mutation; and one mode bit on `tools/sensor-selftest.sh`
  (100755), because `tools/loop/loop.conf` maps it as a gate *command* and `gate.sh` executes it directly — the
  gate's first run here was refused by the file system, not by a case, with `tests=0`
  (`evidence/gate-attempt-1-permission.log`).
- Nothing compiled: no Kotlin, Java, C++, Gradle, manifest or resource. No `:app` behaviour changes.
- The row's first scheduled run files one item per file over the bound — four on today's tree — and each closes
  when that file drops under 1 500 lines. Filing is not authorising: the items carry no `agent/eligible`.
- `check-portability.sh` is the one structural risk: the new script is an engine and must name no project value.
  It names none (no module name, no path, no repository, no label); the measured languages are `*.kt` and
  `*.java`, which are not project values. Verified: `check-portability: OK — 46 engines`.

## Rollback

Revert the commit: the script, the row, the workflow entry, the §9 paragraph and the spec requirement go
together. The tracker keeps the items the row filed; they stay valid findings and simply stop being measured,
exactly as before the change.

## The alternatives the evidence eliminated

- **Reuse `hotspot` by lowering its bound.** Impossible: its score is `size × changes`, so the files this row
  is for — large and static — score zero however low the bound goes. The metric is not a bound away from the
  answer; it is a different quantity, and no bound repairs a factor of zero.
- **Measure per class rather than per file.** Eliminated by the recorded decision, not by preference: it needs
  a Kotlin/Java brace-matching counter that exists nowhere under `tools/`, which is a second mechanism and a
  second change. The item names it as the later refinement; the row is file level until then.
- **Measure a ratchet (never grow past the current value) instead of a fixed cut.** Eliminated by the same
  decision: the fixed cut is retunable by one edit in `sensors.json`, a ratchet needs a recorded per-file
  baseline somewhere other than the issue, which the checks' own rule forbids ("no measurement is stored in
  the repository").
- **Include the native sources.** Eliminated by measurement: the whole C++ tree
  (`app/src/main/cpp`, `app/src/test/cpp`) maxes at 140 lines, so the scope would add conditions only if the
  bound fell by an order of magnitude below the one the decision recorded.
- **Count declarations instead of lines.** Eliminated by the decision's unit ("lines per file") and, secondarily,
  by cost: a declaration count needs a per-language parser, the same second mechanism the per-class cut needs.
  Lines are countable with `awk` on the path set the row already has.
- **Read a metrics file instead of measuring the tree (an `input` row).** Impossible without a second producer:
  no pipeline publishes a per-file line count, and `tools/build-inputs.sh --check` refuses a row that declares an
  input no producer writes. A repository row needs no input, as `hotspot` already shows.

## Verification

- The red case `sensors#sourceSizeRow` fails on HEAD (suite: 1 of 45 cases failed) and passes after the change.
- The case drives all four outcomes: the violation, the row-level `{"path":"none"}` clear, the clear for a
  recorded open condition now inside the bound (naming that path and its line count), and the `error`.
- `bash tools/sensor-selftest.sh` — the sensor tooling's gate, 45 cases.
- `bash tools/sensor-mutations.sh --only 8.17` — the comparison inverted must redden the case, restore, green;
  re-run on the shipping bytes after the review round (`evidence/mutation-8.17-round2.log`).
- `bash tools/check-portability.sh`, `bash tools/check-doc-routes.sh`, `bash tools/build-inputs-selftest.sh`.
- No Gradle gate: no compiled file is touched (`guidelines/Build.md` §2, scope block). The loop's own
  `./tools/loop/gate.sh` selects `tools/sensor-selftest.sh` and `tools/check-doc-routes.sh` from the changed
  pathspec and reports `tests=45 failures=0` (`evidence/gate.log`, tallies
  `evidence/sensor-selftest-junit.xml` and `evidence/doc-routes-junit.xml`).
- The mode bit is part of the rollback: reverting the commit returns the suite to 100644 and the loop's gate to
  a `tests=0` failure for every change that reaches the sensor tooling.
- The gate was run a second time on the final tree, because a review pass added the `error` outcome above (and
  its case assertion) after the first green run: a gate result that no longer measures the tree shipped is not
  evidence. `evidence/gate-attempt-2-pre-review.log` keeps the superseded run, so the sequence is reproducible
  rather than retold as one attempt.
