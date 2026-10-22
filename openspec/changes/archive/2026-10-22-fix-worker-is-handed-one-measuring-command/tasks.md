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
   Verification: `prompt#workerIsHandedTheMeasureBlock` green — `evidence/focused-cases-green.log`, the
   `PASS` line and `EXIT=0` written 2026-10-22T00:23:26Z (`evidence/focused-cases-green-round1-mislabeled.log`
   is the round-1 artifact this replaces: it recorded the pre-fix run's `FAIL`).

- [x] 5. `tools/loop/gate.sh` — the suite map printed with a trailing newline, so the profile's last pair is
   consulted. Verification: `measure#namesTheOwedChecksInOneBlock` asserts the second pair by name (it fails
   while the last line is dropped), and `gate#theDeltaSelectsTheSuitesItTouches` still green.

- [x] 6. `tools/loop-mutations.sh` — mutation `39.1` deletes the loop that names the owed checks; the case must
   go red and the restore green.
   Verification: `bash tools/loop-mutations.sh --only 39.1 --out …/mutation-39.log` →
   `RED 39.1 measure#namesTheOwedChecksInOneBlock`, `restore … green again`,
   `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.

- [x] 7. The project's gate, once, exactly as the run configures it — **re-run after the round-2 corrections**,
   because the mode repair and the block's two corrections changed the tree the first run measured.
   Verification: `./tools/loop/gate.sh` → `LOOP-GATE: tests=210 failures=0 (./gradlew test -PforceTests
   --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh,
   tools/check-doc-routes.sh)`, `EXIT=0` taken from the gate itself, not from a pipeline
   (`evidence/gate.log`, started 2026-10-22T00:25:06Z, finished 00:38:47Z; tallies in `evidence/tools_*.xml`,
   `evidence/gradle-suite-tally.xml`, and `evidence/gate-tally.txt` for how 210 is reached). The 210 is the tool
   half: the JVM half's `tests=0` is a zero it executed — 168 tasks up-to-date, no `TEST-*.xml` written after the
   gate started — which is what the `*.xml` pattern of this change's evidence pulled in, not a compiled delta.
   The round-1 gate was red (`tests=198 failures=1`, `evidence/gate-round1.log`) because
   `tools/check-action-runtimes-selftest.sh` had no execute bit; task 9 repairs it.

- [x] 8. Archive: the change sits under
   `openspec/changes/archive/<date>-fix-worker-is-handed-one-measuring-command/`, the delta merged into
   `openspec/specs/agent-issue-loop/spec.md`; every cited log and XML is in its `evidence/`.
   Verification: `openspec validate agent-issue-loop --type spec`, `openspec doctor`, and
   `tools/check-doc-routes.sh` after the archive (`evidence/`).

- [x] 9. The suite the profile names could not be run: `tools/check-action-runtimes-selftest.sh` was mode
   `100644` while every other suite the profile names is `100755`, so the profile's direct invocation (the way
   `gate.sh` runs a suite) exited 126, and a shell — the way CI invokes it, `.github/workflows/build.yml:297` —
   passed 10/10. Repaired in review round 2 with the one bit: `chmod +x`.
   Verification: `evidence/suite-mode-probe.txt` — `git ls-files -s` before (`100644`) and the direct invocation's
   `EXIT=126`; after the repair the same invocation is 10/10 at `EXIT=0`, and the gate's own run of that suite is
   10/10 (`evidence/tools_check-action-runtimes-selftest.sh.xml`). The profile is unchanged: it names a command,
   and every sibling it names is executable.

- [x] 10. *(round 2)* The block measures the change it is told to measure, and never chooses between two:
   a `--change` name resolves under `openspec/changes/` and then under the archive, two open changes print
   `LOOP-MEASURE-CHANGE ambiguous` naming both and measure neither, and a name that resolves nowhere is named
   `not-found` instead of reading as "no change".
   Verification: `measure#refusesToGuessWhichChangeItMeasures` — red first (`evidence/red-round2.log`, the old
   block printing `LOOP-MEASURE-CHANGE fix-two scenarios=2 cases=1 unnamed=1` and `--change fix-one` printing
   `none`), green after (`evidence/focused-cases-green.log`).

- [x] 11. *(round 2)* The tally list's per-directory cap says what it withheld, so a capped list cannot read as
   the whole tree.
   Verification: `measure#saysHowManyTalliesItWithheld` (25 tallies in, 20 out, `5 of 25` named) — red first
   (`evidence/red-round2.log`), green after (`evidence/focused-cases-green.log`).

- [x] 12. *(round 2)* `tools/loop/prompts/worker.md` names the two answers the block cannot guess: `--change` for
   two open changes, and where the tallies come from (a `clean` empties the gate's directory and that is not a
   green number).
   Verification: `prompt#workerIsHandedTheMeasureBlock` still green — its assertions are a subset of what the
   section now says (`evidence/focused-cases-green.log`).

- [x] 13. *(round 2)* `tools/loop-mutations.sh` — `39.2` (the count that makes two open changes ambiguous) and
   `39.3` (the line that says how many tallies were withheld), beside `39.1`.
   Verification: `bash tools/loop-mutations.sh --only "39.1 39.2 39.3"` → three `RED` lines, three `restore …
   green again`, `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`, and `measure.sh` byte-identical
   before and after (`evidence/mutation-39-round2.log`).

- [x] 14. *(round 2)* The capability delta and the merged requirement carry the two new scenarios, and the
   sentence about the tallies now states the cap and the withheld count rather than promising "the tallies the
   tree already carries".
   Verification: `openspec validate agent-issue-loop --type spec`, `openspec doctor`,
   `tools/check-doc-routes.sh` (`evidence/post-archive.txt`).
