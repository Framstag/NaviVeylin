# Tasks — fix-source-size-sensor

## 1. The red case

- [x] 1.1 Add case `sensors#sourceSizeRow` to `tools/sensor-cases.sh` (array and dispatcher): it reads the row's
  bound, comparison, fingerprint and class from the registry, then drives the real script over a fixture
  repository — a 1 600-line `Big.kt` is a violation measured at 1 600, a 10-line `Small.java` is no condition,
  a 5 000-line `.cpp` and a 4 000-line `.md` are outside the row's scope, the script holds no threshold
  constant, and a tree inside the bound prints a clear. Verify: on HEAD the case fails with "the registry
  declares no source-size row" and the suite reports `1 of 45 cases failed`
  (`evidence/red-suite-on-head.log`).

## 2. The measurement and the row

- [x] 2.1 Write `tools/sensors/metrics/source-size.sh`: `git ls-files -z '*.kt' '*.java'` with a per-file line
  count, one violation per file past `SENSORS_THRESHOLD` (fingerprint = path), one clear per recorded open
  condition now inside the bound, one row-level clear `{"path":"none"}` when it printed nothing, and one
  `error` finding when the source list cannot be read. Verify: case `sensors#sourceSizeRow` passes — including
  the assertion that a directory git cannot list reports `error` rather than a clear — and the script parses
  with `bash -n`.
- [x] 2.2 Declare the `source-size` row in `tools/sensors/sensors.json` — `threshold 1500`,
  `comparison greater-than`, `consecutive 2`, `class improvement`, `category build-and-harness`,
  `lifecycle file+close`, fingerprint `["path"]`, `cron "0 8 * * *"`. Verify: case `Registry#everyRowIsComplete`
  passes, the row's minute is covered by the pipeline's `0,30 * * * *` tick (`inputs#tickMinutesCoverTheRegistryCrons`),
  and `tools/build-inputs.sh --check` still accepts a registry whose rows declare no unpublished input.
- [x] 2.3 Add `source-size` to the second row step's `--rows` list in `.github/workflows/sensors.yml` and to
  that step's comment. Verify: case `workflow#aRedRecordRunsNoRecordRow` passes, which asserts the two lists
  together are exactly the registry's ids, selected once each.

## 3. The doctrine

- [x] 3.1 Add the row's paragraph to `guidelines/Backlog.md` §9: what it measures, the unit and the scope, the
  path as the identity, that the number is the owner's recorded decision and retunable by one edit, and that a
  file and not a class is bounded. Verify: `bash tools/check-doc-routes.sh` resolves every section reference.

## 4. Falsification and evidence

- [x] 4.1 Add mutation 8.17 to `tools/sensor-mutations.sh` (the violation comparison inverted). Verify:
  `bash tools/sensor-mutations.sh --only 8.17` reports the case red under the mutation and green after the
  restore.
- [x] 4.2 Run the gate the changed pathspec asks for and copy its result into this change's `evidence/`:
  `./tools/loop/gate.sh` (selects `tools/sensor-selftest.sh` and `tools/check-doc-routes.sh`) —
  `tests=45 failures=0`. Its first invocation was refused outright because `tools/sensor-selftest.sh` is
  committed 100644 and `gate.sh` executes the suite command directly; the one-bit fix (mode 100755, as the
  three sibling suites already are) is part of this change and its refusal is kept as
  `evidence/gate-attempt-1-permission.log`. Also copied: `tools/check-portability.sh` (46 engines, no project
  value), `tools/check-doc-routes.sh`, `tools/build-inputs-selftest.sh` (11 cases, 0 failed) and mutation 8.17.
  A review pass then added the `error` outcome to the script and its case assertion, so the gate was run once
  more on the tree that ships: `tests=45 failures=0` (`evidence/gate.log`), the superseded run kept as
  `evidence/gate-attempt-2-pre-review.log`. No Gradle build and no Kotlin suite: no compiled file is touched
  (`guidelines/Build.md` §2, scope block) — named here as the exclusion, not as a promise for later.
- [ ] 4.3 Owed after the merge, by the owner: the first scheduled run of `sensors.yml` (`0 8 * * *`) files the
  four items the bound produces on today's tree. Not runnable here — the pipeline's guard refuses every ref
  but `main`, so a dispatch from this worktree writes nothing by design.
