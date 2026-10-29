# Tasks — fix-a-bound-is-clamped-by-the-callers-ceiling

## 1. The red cases (do first)

- [x] 1.1 Add `driver#aSuiteEndsInsideTheCeilingItsCallerNamed` to `tools/loop-cases.sh`: four 6 s stub cases, one
      worker and a caller ceiling of 3 s. Verify: red on the base —
      `evidence/red-on-base-suite-ceiling.xml` (`tests=1 failures=1`, 2026-10-28T18:45:56Z): the suite ran all 24 s and
      read `all 4 cases passed`.
- [x] 1.2 Add `gate#theStepBoundIsClampedByTheCeilingTheCallerNamed`: a real `tools/loop/gate.sh` over a throwaway
      repository whose suite sleeps 30 s, a recorded step bound of 600 s and a caller ceiling of 2 s. Verify: red on
      the base — `evidence/red-on-base-gate-step.xml` (`tests=1 failures=1`): the console read
      `LOOP-GATE-STEP-BOUND: 600s per step` and the step passed at 30 s.
- [x] 1.3 Add `driver#aCeilingTheSuiteFitsInsideChangesNothing` as the guard that a ceiling is a bound and not an
      end for everything. Verify: green on the base, by construction (nothing read the key there).

## 2. The key and its one reader (`agent-issue-loop` — "A bound is clamped by the ceiling the caller named")

- [x] 2.1 `tools/loop/loop.conf`: record `LOOP_CALL_CEILING_SECONDS` with the rationale, a fill-if-unset assignment
      so a caller's environment wins, and an empty default. Verify:
      `LOOP_CALL_CEILING_SECONDS=30 bash tools/loop-selftest.sh --case driver#aCeilingTheSuiteFitsInsideChangesNothing`
      prints nothing different from the run without it.
- [x] 2.2 `tools/loop/lib.sh`: `loop_ceiling` and `loop_bound_under_ceiling`, validating the value like the profile's
      other bounds. Verify: `tools/check-portability.sh` passes (the key is configuration, not a literal in an
      engine).

## 3. The two invocations that apply a bound

- [x] 3.1 `tools/loop-selftest.sh`: one watcher for the ceiling beside the per-case watcher, a `LOOP-SUITE-CEILING:`
      handler that names the counts and exits non-zero through `on_exit`, and the watcher ended on the normal path
      too. Verify: `driver#aSuiteEndsInsideTheCeilingItsCallerNamed` and
      `driver#aCeilingTheSuiteFitsInsideChangesNothing` pass; `driver#aCaseThatBlocksIsEndedByItsOwnBound`,
      `driver#aCaseInsideItsBoundKeepsItsOwnVerdict` and `driver#aRunningCasePrintsItsOwnLines` still pass (the
      per-case watcher is untouched).
- [x] 3.2 `tools/loop/gate.sh`: clamp `step_max` with `loop_bound_under_ceiling` and name the clock in force on the
      `LOOP-GATE-STEP-BOUND:` line. Verify: `gate#theStepBoundIsClampedByTheCeilingTheCallerNamed` passes, and
      `gate#theBlockStaysInsideTheRecordedBound`, `gate#theSuitesOutputReachesTheConsoleWhileItRuns` and
      `gate#aStepThatNamesNoCaseIsAttributedByItsStep` still pass.
- [x] 3.3 `tools/loop-mutations.sh`: three records (the suite's ceiling watcher, the ceiling being a clock rather
      than an end for every suite that names one, and the gate's clamp) pointed at the new cases. Verify: `bash tools/loop-mutations.sh --check` applies all three, and each record
      turns its case red and its restore green.

## 4. The guideline (`guidelines/Build.md` §2a rule 2)

- [x] 4.1 Name the input beside the rule that already says a caller's ceiling can sit below this project's bounds:
      a caller starting a suite or a gate states its ceiling and the invocation ends itself inside it. Verify:
      `bash tools/check-doc-routes.sh` and `bash tools/check-openspec-hygiene.sh` pass.

## 5. Evidence and archive

- [x] 5.1 Copy every cited XML into `openspec/changes/<change>/evidence/` and into the item directory, and verify the
      pointers: `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .`.
- [x] 5.2 Run the project's gate once: `./tools/loop/gate.sh`. Record its result with the block
      `./tools/loop/measure.sh` printed.
- [x] 5.3 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.

## 6. Round 1's corrections

- [x] 6.1 The two `driver#` cases count what their THEN promises instead of inferring it from the elapsed time:
      after the run, no process at the suite's own scratch root (`loop_pids_under "$d"`) and no entry left under
      `$d/tmp`. Verified: green — `driver#aSuiteEndsInsideTheCeilingItsCallerNamed` and
      `driver#aCeilingTheSuiteFitsInsideChangesNothing` (2026-10-29), and red under `71.4`, `71.5` and `71.6`
      (`evidence/mutations-round-2.log`, which prints the 1 and 2 pids the mutation leaves standing).
- [x] 6.2 `tools/loop-selftest.sh`: every bound watcher is started under `set -m` and ended by its **group**
      (`loop_end_group`) in `finish_case` and `on_exit`; ending the subshell left the `sleep` it had forked
      standing, which is the process the clause "every watcher it started" is about. Verified by 6.1's three
      mutations.
- [x] 6.3 Record the two checks 4.1 and 5.1 name: `tools/check-openspec-hygiene.sh` and its selftest ran in this
      round's gate (20 suites, `evidence/gate-round-2.log`), and
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-28-fix-a-bound-is-clamped-by-the-callers-ceiling --root .`
      passes on the final tree — `evidence/evidence-check-round-2.log`, `evidence-check: PASS`.
- [x] 6.4 Re-measure the gate over the final tree, with the openspec paths in its pathspec:
      `LOOP-GATE: tests=685 failures=0 errors=0` over 20 suites (`evidence/gate-round-2.log`,
      `evidence/gate-tallies-round-2/`, `evidence/gate-summary-round-2.xml`), against round 1's
      `tests=665 failures=2`. The one failure this change
      had caused (`driver#everyCaseDeclaresTheFilesItCanReach`) is green here, and the two `postfix-*` logs its
      attribution cited are now in this evidence directory.
