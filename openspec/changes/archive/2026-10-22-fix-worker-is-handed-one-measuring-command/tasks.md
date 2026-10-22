# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run.

- [x] 1. Root cause and the red test (Phase A). Verified the entry against the tree: 73 to 244 shell calls and
  22 to 79 reads per item from `.pi/issue-loop/items/<n>/events.jsonl`, the worker task carrying no reading
  command (`grep -o '{{[A-Z_]*}}' tools/loop/prompts/worker.md`), and `gate.sh:74-88` holding the pathspec →
  checks mapping. Three cases written first and run against the base commit: all three `FAIL`, `EXIT=1`
  (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`).
   Verification: `bash tools/loop-selftest.sh --case 'measure#namesTheOwedChecksInOneBlock'` on the base → the
   measuring command does not exist (`expected '0', got '127'`).

- [x] 2. `tools/loop/measure.sh` — one block, prefixed `LOOP-MEASURE`, from the same base and pathspec the gate
   resolves; the owed checks asked of `gate.sh --plan`; the tallies read from the gate's directory and the
   change's `evidence/`; the delta's scenarios counted against their cases; the proposal's `surface:` quoted.
   Verification: `measure#namesTheOwedChecksInOneBlock` and `measure#printsTheNumbersTheReviewerAsksFor` green,
   and the first case's canary file absent — no check was executed.

- [x] 3. `tools/loop/loop.conf` — `LOOP_MEASURE_CMD`, the project's value, with the measurement that motivates
   it; `tools/loop/run.sh` renders it into the task at both of the render's call sites.
   Verification: `state#projectKeysAreReadFromLoopConf` — the case substitutes the key and asserts the
   rendered task carries the substituted value.

- [x] 4. `tools/loop/prompts/worker.md` — the section that hands the block over and states its two rules, and
   the phase-D sentence that the block is not the gate.
   Verification: `prompt#workerIsHandedTheMeasureBlock` green.

- [x] 5. `tools/loop/gate.sh` — the suite map printed with a trailing newline, so the profile's last pair is
   consulted. Verification: `measure#namesTheOwedChecksInOneBlock` asserts the second pair by name (it fails
   while the last line is dropped), and `gate#theDeltaSelectsTheSuitesItTouches` still green.

- [x] 6. `tools/loop-mutations.sh` — mutation `39.1` deletes the loop that names the owed checks; the case must
   go red and the restore green.
   Verification: `bash tools/loop-mutations.sh --only 39.1 --out …/mutation-39.log` →
   `RED 39.1 measure#namesTheOwedChecksInOneBlock`, `restore … green again`,
   `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.

- [x] 7. The project's gate, once, exactly as the run configures it.
   Verification: `./tools/loop/gate.sh` → `LOOP-GATE: tests=198 failures=1 (./gradlew test -PforceTests
   --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh,
   tools/check-doc-routes.sh) — failing: tools/check-action-runtimes-selftest.sh` (`evidence/gate.log`, the
   tallies in `evidence/tools_*.xml` and `evidence/gradle-suite-tally.xml`). The suites this change's pathspec
   owes are green — `tools/loop-selftest.sh` 153/153, `tools/sensor-selftest.sh` 45/45, `tools/check-doc-routes.sh`
   exit 0, `./gradlew test` exit 0. The one failure is not this change's: the run's base (`80df3a3`) also carries
   the landed commits #240 and #196, whose `.github/workflows/*` paths make the profile owe
   `tools/check-action-runtimes-selftest.sh`, and that file is mode `100644` in the repository — it exits 126 when
   the profile's command names it directly and passes 10/10 when a shell runs it
   (`evidence/gate-unrunnable-suite.txt`). The gate is not retried; the item blocks on the repair.

- [x] 8. Archive: the change sits under
   `openspec/changes/archive/<date>-fix-worker-is-handed-one-measuring-command/`, the delta merged into
   `openspec/specs/agent-issue-loop/spec.md`; every cited log and XML is in its `evidence/`.
   Verification: `openspec validate agent-issue-loop --type spec`, `openspec doctor`, and
   `tools/check-doc-routes.sh` after the archive (`evidence/`).

- [ ] 9. **Open, and not this item's to fix:** the profile's suite map names
   `tools/check-action-runtimes-selftest.sh` as a command, and the repository marks that file `100644`, so the
   gate cannot run it. Repairing it is a change of its own — either the mode bit or the profile's command. Until
   it is repaired, every item whose base touches `.github/workflows/*` blocks on the same exit 126.
