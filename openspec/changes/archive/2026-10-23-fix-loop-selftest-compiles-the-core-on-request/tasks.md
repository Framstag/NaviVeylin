# tasks — fix-loop-selftest-compiles-the-core-on-request

## 1. The red cases (done before the driver changed)

- [x] 1.1 Add `driver#aCaseThatAsksForNoCoreCompilesNothing` to `tools/loop-cases.sh`: a `javac` shim on `PATH` that
      records its invocation and exits 1, and a `--case` run of `state#initAndShowRoundTrips`. Verify: red on HEAD
      — `evidence/red-HEAD.xml` (`tests="1" failures="1"`), transcript `evidence/red-HEAD.log`.
- [x] 1.2 Add `driver#aCaseThatAsksForTheCoreGetsItOrRefuses`: a nested driver whose case asks for the core with the
      real `javac` (it must be handed a directory that carries `Cases.class`), and the same run with a failing
      `javac` (exit 1 and the driver's own line). Verify: red on HEAD —
      `evidence/red-HEAD-core-request.xml` (`tests="1" failures="1"`), transcript
      `evidence/red-HEAD-core-request.log`.

## 2. The driver

- [x] 2.1 Replace `javac …` and `export ISSUES_CORE_CLASSES` at `tools/loop-selftest.sh:95`–`107` with
      `core_ensure` (`tools/loop-selftest.sh:105`), which compiles once (marker `selftest_core_marker`), exports the
      class directory, and keeps the refusal line and its exit 1. Verify: `bash -n tools/loop-selftest.sh`, then
      task 1.1's case is green and its shim log is empty — `evidence/green-fix.xml` (`tests="2" failures="0"`).
- [x] 2.2 Call `core_ensure` where a case uses the tooling: `run_loop` (`tools/loop-cases.sh:478`), the five direct
      `run.sh` starts (`:756`, `:765`, `:792`, `:805`, `:1960`), `tools/issues/update.sh` (`:1994`) and the
      `WorkPackage` run (`:2214`); say in the cases file's header what the driver now hands a case. Verify: task
      1.2 is green, and `bash tools/loop-selftest.sh --case 'run#oneItemOrderIsFixed'` still passes (a case that
      asks through the wrapper).

## 3. Falsification

- [x] 3.1 Add record `48.1` to `tools/loop-mutations.sh`, restoring the eager compile, with the case
      `driver#aCaseThatAsksForNoCoreCompilesNothing`. Verify: `bash tools/loop-mutations.sh --only 48.1` →
      red-when-mutated 1, not-noticed 0, restore-failures 0, in `evidence/revert-check-48.1.log`; and
      `bash tools/loop-mutations.sh --check` still applies every record.

## 4. Gate and evidence

- [x] 4.1 Run the project's gate once: `./tools/loop/gate.sh` → `gate.log` and `gate-tallies/` in
      `evidence/`, green, with the loop's suite (`tools/loop-cases.sh|tools/loop-selftest.sh`) among the suites that
      ran. The first run is red on a defect this change introduced (`core_ensure` read `$tmp`, which five cases
      shadow with a `local`); it is kept in `evidence/gate-first-red/` and attributed in
      `evidence/gate-attribution.md`, and the run whose verdict counts is the one after the fix. Verify:
      `evidence/gate.log` (the gate's summary line, three suites, 314 cases, none failed) and
      `evidence/gate-tallies/tools_loop-selftest.sh.xml` (`tests="180" failures="0"`).
- [x] 4.2 Copy every cited transcript and XML into `evidence/` and check the change's pointers:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-selftest-compiles-the-core-on-request --root .`
      → PASS (C1–C5; C3 reports the measurement's own `mktemp -d .pi/tmp/…` command as a note, which is the
      re-runnable form of it). Written up in `evidence/verification.md`.
- [x] 4.3 Archive the change and confirm `openspec/changes/archive/…-fix-loop-selftest-compiles-the-core-on-request/`
      exists and that the requirement is in `openspec/specs/agent-issue-loop/spec.md`.
