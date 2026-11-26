# Tasks

- [x] 1. **Red case first.** `run#theReworkPromptIsTheFileTheChildIsHanded` is declared in
  `tools/loop/tests/loop-cases.sh` with its `# files:` line naming `tools/loop/run.sh`, `tools/loop/evidence.sh`,
  `tools/loop/child.sh`, `tools/loop/loop.conf`, `tools/loop/tests/fixtures/scenarios/`, `tools/pi-child-events.sh`
  and `tools/tooling.json`.
  Verified: `bash tools/loop/tests/loop-selftest.sh --case 'run#theReworkPromptIsTheFileTheChildIsHanded' --jobs 1`
  exits 1 on HEAD with `'the worker-round2 prompt is 34269 B' not found in: …` (log
  `evidence/red-on-HEAD-theReworkPromptIsTheFileTheChildIsHanded.log`).

- [x] 2. **One reading of the `BLOCKING:` line.** `blocking_finding_lines` in `tools/loop/evidence.sh` prints the
  lines; `blocking_findings` counts them through it.
  Verified: `bash -n tools/loop/evidence.sh`, and `run#aCorrectiveRefusalEarnsOneMoreRound` — the only case that
  reads a report with `BLOCKING:` lines through the round after it — still passes: the count is the same number
  through one more call.

- [x] 3. **The append carries the findings and the path, and the check runs after it.** `tools/loop/run.sh:2510`
  takes the text from `blocking_finding_lines`; `tools/loop/run.sh:2523` appends the findings and
  `$reviewer_report`; `tools/loop/run.sh:2525` weighs the handed file.
  Verified: the case at 1 passes, its three assertions (`the worker-round2 prompt is <handed> B` in the run's
  output, the `BLOCKING:` line and the report's path in the file, `PROSE-NOT-A-FINDING` absent) all hold (log
  `evidence/green-theReworkPromptIsTheFileTheChildIsHanded.log`).

- [x] 4. **The new name is declared.** `tools/loop/layout.tsv`'s `evidence.sh` row owns it and `tools/loop/shared.tsv`
  carries the row `blocking_finding_lines evidence.sh run.sh`.
  Verified: `driver#anOwnedFunctionIsInItsOwnersFile`, `driver#theSharedNamesAreDeclared` and
  `driver#noEngineFileExceedsItsBound` pass (both files stay under their recorded bounds: 873 ≤ 962, 2973 ≤ 3497).

- [x] 5. **The owning rule states the weighed file.** `guidelines/BuildGate.md` §2a's prompt-size rule names the
  handed file, the append's content and the case.
  Verified: `LOOP-ROUTE: guidelines/BuildGate.md §2a` resolves in the change's markdown (C6 of
  `tools/loop/evidence-check.sh`).

- [x] 6. **Evidence, then the archive, then the gate.** The two logs above and the gate's XML are copied into
  `evidence/`; `bash tools/loop/evidence-check.sh <this change> --root .` passes; `openspec archive --yes` merges the
  delta; `./tools/loop/gate.sh` runs once on the archived tree.
  Verified: `LOOP-GATE:` line in the item's `gate.log`.
