# Tasks

- [x] 1. Measure the flake's mechanism on HEAD: a probe in the heartbeat logging each reading's moment against
  the stub's own write and removal — `evidence/phase-margin-probe.md` (readings 0.45/0.66/0.76 s after a write
  that lives 2 s, file removed 3 s after it appears).
- [x] 2. Repair the case first, so the red is HEAD's: `tools/loop-cases.sh`, no `LOOP_PROGRESS_SECONDS=1`, writer
  `sleep 10`, write removed after 8 s. Verify it is red on the unchanged engine,
  `bash tools/loop-selftest.sh --case 'run#aWriterTheRunCanPlaceBlocksTheItem'`, three consecutive runs:
  `evidence/red-on-head.log`, `evidence/red-on-head.xml` (`tests=1 failures=1`).
- [x] 3. `tools/loop/run.sh`: the in-time reading gets its own cadence (`read_next`, one reading per whole second),
  taken before the reporting gate; the first line of `outside_write_seen` carries the same opening sentence as the
  exit-time line. Verify: the same case green three times (`evidence/writer-case-fixed.log`,
  `evidence/writer-case-fixed.xml`, `tests=3 failures=0`).
- [x] 4. `tools/loop-mutations.sh`: retire `12.12` with the measurement that retired it (`--only '12.10 12.12'`
  reported `GREEN 12.12 … the case did not notice the mutation`); keep `12.10` and `12.11` and verify both red
  (`evidence/mutation-attribution.log`: `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`), and
  `bash tools/loop-mutations.sh --check` green over all 178 records.
- [x] 5. Run the cases the changed notes and verdicts reach — `evidence/affected-cases.log`, ten cases, all `PASS`.
- [x] 6. `guidelines/Backlog.md`, "The loop": the paragraph that stated "reads the project's tree once per
  `LOOP_PROGRESS_SECONDS`" states the reading's own cadence and the measurement. Structural check, no build owed:
  `tools/check-doc-routes.sh` (the gate's `*.md` path).
- [x] 7. `openspec/specs/agent-issue-loop/spec.md` delta: one ADDED requirement with one scenario naming the case.
  `openspec validate fix-the-read-that-places-a-write-has-its-own-cadence --strict`.
- [x] 8. The project's gate, once, from the change root: `./tools/loop/gate.sh` — quoted in
  `evidence/gate.log` with its tallies in `evidence/gate-tallies/`.
- [x] 9. `bash tools/loop/evidence-check.sh openspec/changes/fix-the-read-that-places-a-write-has-its-own-cadence
  --root .` and archive the change.
- [x] 10. Correction round 1, finding 1: delete the writer case's synchronisation paragraph (the stub waits for
  neither the standing process nor `worker-report.md.outside-write`) and state the mechanism it is red on HEAD for
  in its place — `tools/loop-cases.sh`, no assertion touched. Verify: the case is still green,
  `evidence/writer-case-fixed.log` beside `evidence/affected-cases.log`.
- [x] 11. Correction round 1, finding 2: capture the GREEN run that retired `12.12` — the record restored verbatim
  for one run, `bash tools/loop-mutations.sh --only 12.12`, removed again afterwards:
  `evidence/mutation-12.12.log` (`GREEN  12.12 … the case did not notice the mutation`,
  `not-noticed: 1 · restore-failures: 0`).
- [x] 12. Correction round 1, finding 3: narrow the shipped scenario "A difference is attributed while the writer is
  alive" to the clause `run#aWriterTheRunCanPlaceBlocksTheItem` still holds, and carry the requirement it belongs to
  as a `MODIFIED` block in the delta with every other scenario repeated verbatim. Verify: `openspec validate
  agent-issue-loop --type spec` and `bash tools/check-openspec-hygiene.sh` green, and the delta's block diffed
  against the shipped requirement (identical but for the narrowed THEN).
- [x] 13. Correction round 1's notes: the two summary XMLs state no `time` and say they tally their `.log`; the
  surface count and the `tools/loop/run.sh:1168` pointer in `proposal.md` are the greps' and the file's own; the
  `run.sh` comment describes the first look it actually takes; the guideline attributes the 1-in-3 rate to the
  issue. Verify: `tools/check-doc-routes.sh` green, `bash tools/loop/evidence-check.sh
  openspec/changes/archive/2026-10-26-fix-the-read-that-places-a-write-has-its-own-cadence --root .` PASS, and the
  project's gate once over the corrected tree — `evidence/gate-round2.log`, whose `LOOP-GATE:` line reads 608 cases,
  0 failures and 0 errors over the whole loop set, with the per-suite tallies in `evidence/gate-tallies-round-2/`.
