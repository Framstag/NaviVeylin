# Proposal — fix-a-first-exercise-reports-its-count

## Why

One defect, verified in this tree, issue #667: **a row with no measurement on record files its very first
exercise straight to the tracker, so a new bound's blast radius reaches the tracker before any count of it has
been printed.**

**Root cause (one).** `sensors_run_row` reads the row's baseline — the newest measurement marker its own issues
carry — and uses it as a *value to hand on*, never as a *predicate* on how much the run may write:
`tools/sensors/run.sh:792-794` (`baseline=$(sensors_last_marker "$issues")` →
`SENSORS_BASELINE="$baseline"`, and `tools/sensors/run.sh:237` in the issue body as its text default
`none recorded`). Nothing between that read and the write decides anything by it, so a row that has never
measured reaches the filing branch `tools/sensors/run.sh:472` (`if [ -z "$issue" ]; then`) exactly like a settled
one — once per finding its command printed (`tools/sensors/run.sh:811`, the `while IFS= read -r line` loop). The
report already knows the state the run path does not: `tools/sensors/report.sh:69` prints
`stale row=<id> no measurement on record — the row has never run or never filed`.

**Verified, not quoted from the tracker.** In this tree, on the run's base `a859c0bb`:

```
$ git show a859c0bb:tools/sensors/run.sh | grep -c 'SENSORS_TRIGGER\|FIRST_EXERCISE'
0
```

No branch in the runner is gated on the trigger the pipeline names, so a scheduled tick and a dispatched run
behave identically; `SENSORS_MODE` (`.github/workflows/sensors.yml:57`) encodes only *write or decide*, and a tick
always writes. The item's own field evidence, quoted from its text and not re-measured here — 337 items
(#321–#657) from one `hotspot` row, whose report line read
`row=hotspot last=never verdict=- measurement=- issue=-` before *and after* the tick of 2026-10-26
(`08:23:27Z`, run `40287372367`) — is the tracker's record of that mechanism firing; this change measures the
mechanism, which is what a host test settles.

**The red test** — one case that fails on the base, plus the case that pins the half the fix must not break.
Log `evidence/red-on-head.log`, tally beside it, run 2026-10-26T22:14:39Z:

- `run#aFirstExerciseOnATickReportsInsteadOfFiling` —
  `the tick files no condition on a row with no measurement on record: expected '0', got '3'`,
  `tests="1" failures="1"`: `evidence/red-on-head-aFirstExerciseOnATickReportsInsteadOfFiling.xml`
- `run#aDispatchedFirstExerciseRecordsItsMeasurement` — green on the base by construction: it asserts that a
  dispatch that asked to write still files, which is exactly what a gate keyed on the baseline alone would break. Its
  evidence is the mutation, not the base: `8.29` drops the trigger from the gate and reddens it.

**Owning capability and guideline section.** The behaviour is a new requirement of `backlog-sensors`, beside
*A row's filings in one run are bounded* — the requirement that owns what one run of one check may write
(`openspec/specs/backlog-sensors/spec.md:512`). The prose that owns a check's declaration and its bounds is
`guidelines/Backlog.md` §9 "Checks", whose paragraph on the filing bound (`guidelines/Backlog.md:760`) is where
the first exercise is stated; it gains one paragraph, and no existing sentence is rewritten.

**The recorded decision is implemented, not re-derived.** The item's `ITEM_DECISION` of 2026-10-26T16:21:51Z
records **option 2**: *the gate applies to the scheduled tick only, and a dispatch with `apply=true` is the
acknowledgement that bootstraps the row*. So the gate is a mode check on the trigger the pipeline names, not a
registry value and not a new tracker state, and a first exercise is spent by a dispatch that asked to write; a
hand run names no trigger and, its mode defaulting to `--dry-run`, records nothing.

surface: 2 cases added, 0 rewritten, 2 mutation records added (`8.29`, `8.30`), 1 requirement added with 2
scenarios, 1 workflow env line, 1 guideline paragraph — the counts are
`bash tools/sensor-selftest.sh --list | wc -l` (64 declared cases, 62 on the base),
`grep -c "^  '8\." tools/sensor-mutations.sh` (30 records, 28 on the base) and
`grep -rn 'SENSORS_TRIGGER' tools/ .github/ guidelines/ | wc -l` (0 on the base). The rule's other 38 `--apply`
call sites across 26 cases (`grep -c -- '--apply' tools/sensor-cases.sh`, 40 after the two added) assert the
settled path and need no seeded measurement: the gate reads the trigger, and none of them names one.

## What changes

- `tools/sensors/run.sh` — `SENSORS_TRIGGER` is read from the environment (one global beside `SENSORS_DRY_RUN`,
  absent when the caller names none), and `sensors_run_row` sets `SENSORS_FIRST_EXERCISE` when the trigger is
  `schedule` **and** the baseline is empty. Such a row reports each finding as
  `not filed (first exercise: no measurement on record)` and ends with one line naming the count —
  `first exercise: row=<id> findings=<n>` — and writes nothing: no condition item, no record issue, no summary.
- `.github/workflows/sensors.yml` — `SENSORS_TRIGGER: ${{ github.event_name }}` beside `SENSORS_MODE`, so the
  engine can tell the unattended tick from a run a person started. The trigger cap and the mode expression are
  unchanged.
- `tools/sensor-cases.sh` — the two cases above, in the declared set and the dispatch table.
- `tools/sensor-mutations.sh` — `8.29` (the gate ignores the trigger) and `8.30` (no gate at all), one per half
  of the new invariant.
- `guidelines/Backlog.md` §9 — the paragraph that names the first exercise, its count line and the acknowledgement
  that spends it.
- `openspec/specs/backlog-sensors/spec.md` — one added requirement with two scenarios.

## Capabilities

### Modified Capabilities

- `backlog-sensors`: adds *A row's first exercise is reported, not spent* — the count the unattended tick prints
  for a row with no measurement on record, the silence it keeps, and the dispatch that asked to write, which
  records the first one.

## Impact

`tools/sensors/run.sh`, `.github/workflows/sensors.yml`, `tools/sensor-cases.sh`, `tools/sensor-mutations.sh`,
`guidelines/Backlog.md` §9 and the spec delta. No compiled file, so no Gradle suite is owed by these paths: the
gate's plan for this pathspec is the shell suites and the doc checks, and it ran them once (`evidence/gate.log`,
one tally per suite beside it). Additive and reversible: a run whose caller names no trigger — every hand run and
every existing case — behaves exactly as it does today, so rollback is reverting the branch.

## Spec

`ADDED` **A row's first exercise is reported, not spent** in `backlog-sensors`. Body: 302 characters against the
validator's 500, measured by
`awk '/^### Requirement: A row.s first exercise/{f=1;next} f&&/^####/{exit} f&&NF{printf "%s ", $0}' <delta> | wc -c`.
Scenarios: *The unattended tick reports a row's first exercise*
(`run#aFirstExerciseOnATickReportsInsteadOfFiling`) and *A dispatched run records the first exercise*
(`run#aDispatchedFirstExerciseRecordsItsMeasurement`).

No existing requirement changes: *A row's filings in one run are bounded* keeps its body and every scenario —
the bound still governs what a run that may write files; the new requirement states when a run may not write at
all.

## Open

The item's other candidate repairs are eliminated by the evidence rather than ranked, and the recorded decision
settles the choice between them: *a row with no measurement on record runs `--dry-run` whatever the tick's mode
says* starves the row, because the gate's own predicate is the marker comment a dry run never posts — the row
would report its first exercise for the life of the bound; *the first run files at most the filing cap* needs a
bound in the registry, which #666 already put on the row as `maxFilings` and which governs a run that writes, not
one that must not; *nothing is ever written until a person records a measurement by hand* needs a documented
bootstrap or leaves a bound that silently never files, the failure #257 fixed by the opposite means.
