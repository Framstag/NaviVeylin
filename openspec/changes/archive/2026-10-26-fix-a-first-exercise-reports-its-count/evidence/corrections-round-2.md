# Round 2 — the reviewer's corrections, applied

Review round 1 (`evidence/review-round-1-requested.md`) refused the change on one BLOCKING finding and four
NOTEs, with `LOOP-CORRECTIONS-ONLY: yes`. Every correction below is subtractive or a pointer repair: no new case,
no new assertion, no production behaviour changed. The fix itself, the two cases, the two mutation records and
the gate run are untouched.

## 1. BLOCKING — the SHALL and its second scenario were wider than the code and the cases

The requirement's body read *"A run a person started SHALL record that first measurement"* and scenario 2's WHEN
named *"a dispatch that asked to write, and equally a command run by hand, which names no trigger"*. Neither
holds: `tools/sensors/run.sh:88` makes a dry run the default (`SENSORS_DRY_RUN="${SENSORS_DRY_RUN:-1}"`), and
`.github/workflows/sensors.yml:57` gives a dispatch with `inputs.apply` false `--dry-run` as well, so only a run
that asked to write records anything. Narrowed to the run the cases and the mutation actually exercise:

| place | before | after |
|---|---|---|
| `openspec/specs/backlog-sensors/spec.md:563`, delta `specs/backlog-sensors/spec.md:5` | "A run a person started SHALL record that first measurement" | "A run a person started **that asked to write** SHALL record that first measurement" |
| `openspec/specs/backlog-sensors/spec.md:573`, delta `specs/backlog-sensors/spec.md:15` | WHEN "a run a person started — a dispatch that asked to write, and equally a command run by hand, which names no trigger —" | WHEN "a dispatch that asked to write" |
| `tools/sensors/run.sh:29-35` (header comment) | "Every other run — a dispatch, and a command run by hand, which names no trigger at all — is a run a person started, and its first exercise records the measurement" | a dispatch that asked to write (`inputs.apply=true`) records it; a hand run names no trigger and, its default mode being `--dry-run`, records nothing |
| `tools/sensors/run.sh:800-801` (the gate's comment) | "a run a person started is the acknowledgement that records the first measurement" | "a dispatch that asked to write (`inputs.apply=true`) is the acknowledgement that records the first measurement" |
| `tools/sensors/run.sh:90` (`SENSORS_TRIGGER` default) | "an empty value is a run a person started" | "… — which records a measurement only if it asked to write" |
| `guidelines/Backlog.md:773-778` | "a command run by hand, which names none, behaves as a dispatched one; the scheduled tick is the only run that reports and does not write" | "only the scheduled tick prints that count line: a run a person started records a measurement only when it asked to write, and a hand run — whose mode defaults to `--dry-run` — writes nothing and so leaves the row's first exercise unspent" |
| `tools/sensor-cases.sh:1431` and `:1471` | "spent by a run a person started" / assertion message "no row is held back by a run a person started" | "spent by a dispatch that asked to write" / "no row is held back by a dispatch that asked to write" |
| `design.md:21` | "An absent value is the run a person started: every hand run and every case names none" | "An absent value is the run a person started; it records a measurement only when it asked to write (`--apply`)…" |

The narrowed body is 322 characters, under the validator's 500.

## 2. NOTE — task 8's tally pointer named files that do not exist

`tasks.md` task 8 cited `evidence/gate-*.xml`; the tallies the gate writes are per check,
`evidence/tools_*.xml` (`tools_sensor-selftest.sh.xml`, `tools_issue-tooling-selftest.sh.xml`, …). Pointer
corrected; no claim changed.

## 3. NOTE — task 7's verification command is not runnable after the archive

`openspec validate fix-a-first-exercise-reports-its-count --strict` no longer resolves once the change is under
`openspec/changes/archive/`. Task 7 now cites the check that does run on the shipped capability —
`openspec validate backlog-sensors --type spec` → `Specification 'backlog-sensors' is valid` — and task 10
re-measures the body.

## 4. NOTE — two prose claims the gate does not support

- `proposal.md` "Why" said *"a new or retuned bound's blast radius"*; the gate keys on an empty baseline only
  (`tools/sensors/run.sh:802`), so a retuned row that has measured before files as it always did. "or retuned"
  removed.
- `guidelines/Backlog.md` said "the scheduled tick is the only run that reports and does not write": a hand dry
  run writes nothing either, and prints `dry-run: would file …` rather than the count line. Reworded to the count
  line, which is the narrower true statement.

## 5. NOTE — numbers that lived only in prose

- `proposal.md` "Why" now says the 337 items, `40287372367` and `08:23:27Z` are *quoted from the item's text and
  not re-measured here*; they are `work-package.md` lines 8-9, not a command's output.
- `evidence.md` §3 quoted the `MUTATION 8.29:`/`8.30` form, which belongs to `tools/sensor-mutations.sh`; the
  archived transcript is `tools/mutate.sh`'s, so `evidence.md` now quotes `evidence/mutations.log` verbatim and
  names the command behind the other form.
- `evidence.md` §5's merge-base path count was a stale figure (163); the tree's own block prints
  `LOOP-MEASURE base=4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46 paths=193`, and `evidence.md` quotes that line.
- `evidence.md` §7's `evidence-check: PASS (…)` line is now a pasted run of the same command, and §6's
  `openspec validate` output likewise.

## 6. Verification after the corrections

`evidence/corrections-round-2.log`, one run, 2026-10-26T22:32:12Z:

```
PASS run#aFirstExerciseOnATickReportsInsteadOfFiling
PASS run#aDispatchedFirstExerciseRecordsItsMeasurement
check-doc-routes: all 154 section references resolve, all 4 skill references resolve
OpenSpec artifacts are consistent: 1 open change(s) carry task markers, the operation guidance is quoted, and every capability spec states its Purpose.
Specification 'backlog-sensors' is valid
322                                  # the narrowed requirement's body, characters
```

Appended to the same log, after the `tasks.md` round-2 task landed: `bash tools/check-todo-references.sh` →
`63 citation(s) resolve through the recorded table`, exit 0, and `bash tools/check-openspec-hygiene.sh` re-run →
`OpenSpec artifacts are consistent: 1 open change(s) carry task markers, the operation guidance is quoted, and
every capability spec states its Purpose.`, exit 0.

The gate is **not** re-run: no correction changes a behaviour or an assertion (two comment blocks, two case
messages, one guideline paragraph, one requirement's wording, pointers), and re-running a gate to refresh its
evidence is what destroys the evidence. The gate's own record stands as `evidence/gate.log` (its aggregate
`LOOP-GATE` line), and its per-suite tally XMLs stand beside it — `evidence/tools_sensor-selftest.sh.xml` carries
`tests="64" failures="0" errors="0" time="164"`, `evidence/tools_issue-tooling-selftest.sh.xml` `tests="124"`, and
no compiled file is in this pathspec, so no Gradle suite is owed (`--base` was the run's base `a859c0bb`).

Pointer check after the corrections, from the change root:

```
bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-26-fix-a-first-exercise-reports-its-count --root .
ok   C1 §references: 8 checked, 1 exempted as archived pointers
ok   C2 test tallies: 2 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift
ok   C3 /tmp pointers: none cited
ok   C5 file:line: 371 resolved and in range, 0 unresolved or ambiguous
evidence-check: PASS
```
