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
      `evidence/gate-round-1/`, green, with the loop's suite (`tools/loop-cases.sh|tools/loop-selftest.sh`) among the
      suites that ran. The first run is red on a defect this change introduced (`core_ensure` read `$tmp`, which
      five cases shadow with a `local`); it is kept in `evidence/gate-first-red/` and attributed in
      `evidence/gate-attribution.md`, and the run whose verdict counts is the one after the fix. Verify: the round-1
      record's own line, three suites and 314 cases, none failed
      (`evidence/gate-round-1/gate.log`) and `evidence/gate-round-1/tools_loop-selftest.sh.xml`
      (`tests="180" failures="0"`). The round-1 files were moved into that subdirectory when round 2's gate run
      took the top-level names — the same run, kept under the name that says which round it belongs to.
- [x] 4.2 Copy every cited transcript and XML into `evidence/` and check the change's pointers:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh
      openspec/changes/archive/2026-10-23-fix-loop-selftest-compiles-the-core-on-request --root .`
      → PASS (C1–C5; C3 reports the measurement's own `mktemp -d .pi/tmp/…` command as a note, which is the
      re-runnable form of it). Written up in `evidence/verification.md`.
- [x] 4.3 Archive the change and confirm `openspec/changes/archive/…-fix-loop-selftest-compiles-the-core-on-request/`
      exists and that the requirement is in `openspec/specs/agent-issue-loop/spec.md`.

## 5. Round 2 — the review's three blocking findings and its notes

- [x] 5.1 **"at most once per suite" had no case.** Add `driver#theCoreIsCompiledOncePerSuite` to
      `tools/loop-cases.sh`: a nested driver whose two stub cases both ask for the core, with a `javac` probe on
      `PATH` that records every invocation and writes the class file they look for, asserting exactly one
      invocation. Add record `48.2` to `tools/loop-mutations.sh` (the marker guard dropped to `if true`; it was
      numbered `49.1` before #273's merge took that label). Verify:
      the case PASSes on this tree (`evidence/green-correction.log`), and `bash tools/loop-mutations.sh --only 48.2`
      → red-when-mutated 1 · not-noticed 0 · restore-failures 0 (`evidence/revert-check-49.1.log`).
- [x] 5.2 **The gate record covered three suites, not the ones the pathspec owes.** `tools/loop/loop.conf` owes
      `bash tools/gen-feature-list.sh --check-classification` and `tools/check-doc-routes.sh` for the `openspec/`
      and `*.md` paths this change adds, and `gate.log` named three suites. Re-run the gate once on the archived
      tree, after every artifact including this file. Verify: `evidence/gate-round-2.log` — the suites it names, the
      summary line, and the tallies in `evidence/gate-tallies-round-2/`. Round 3 found this run's own tree stale (the last
      write of `tools/loop-cases.sh` was 96 s after `gate.log` closed); `evidence/gate-final-tree.log` and
      `evidence/gate-tallies-final/` are the run of the tree this change leaves — see 6.1.
- [x] 5.3 **The sibling-driver sentence was false.** `tools/check-portability-selftest.sh:71` precompiles the core
      for `tools/portability-cases.sh`'s 27 shell cases, and `tools/resolve-stale-selftest.sh:50` does the same for
      `tools/resolve-stale-cases.sh`'s 27 shell cases (`resolver#…` 17 of them, the rest 7 `classifier#…`, 1
      `prescreen#…`, 2 `pipeline#…`), so a `--case` run of theirs does pay the same second.
      Correct `proposal.md` and `design.md`, "What is not changed" to say exactly that, with the command that shows
      it: `grep -c 'java' tools/portability-cases.sh` → `1` (a `grep` target at `:354`).
- [x] 5.4 **A note in the evidence: the revert-check log states no reason.** `evidence/revert-check-48.1.log`
      carries the harness's `RED`/`restore` lines and neither a timestamp nor the case's own output
      (`tools/loop-mutations.sh:550` prints the record and the case name). Say in `evidence/verification.md` that the
      "right reason" is read from the case's own assertions, not quoted from that log.
- [x] 5.5 **A note in `verification.md`:** its "six cases … `evidence/green-fix.log`" cited a file that holds the
      two new cases only. The six are in `gate-round-1/gate.log` (`:6`, `:7`, `:148`, `:149`, `:278`, `:589`); the row now says
      so, and `evidence/green-correction.log` carries the focused re-run of six affected cases.
- [x] 5.6 **A note in the case:** `driver#aCaseThatAsksForTheCoreGetsItOrRefuses` rewrote its nested loop
      configuration into the file `sed` was reading, so the second half ran on an empty one and its isolation held
      by `lib.sh`'s fallback. The broken configuration is now `$d/loop-broken.conf`. Verify:
      `bash tools/loop-selftest.sh --case 'driver#aCaseThatAsksForTheCoreGetsItOrRefuses'` → PASS.
- [x] 5.7 **A note in the spec's wording:** "SHALL end the suite with exit 1" overstates a mid-suite ask (the case
      fails, the remaining cases still run, the run exits 1). Reworded to "SHALL fail with exit 1 … and the run
      SHALL end with exit 1", in the delta and in the shipped requirement.
- [x] 5.8 **A note in the cases file's header:** it described `ISSUES_CORE_CLASSES` and not the convention a case
      author must follow — call `core_ensure` where you use the tooling. Added to the header of
      `tools/loop-cases.sh`.

## 6. Round 3 — the decision the review left, worked as option (a)

- [x] 6.1 **The gate record predated the tree (round 2's one blocking finding).** Re-ran the gate on the tree this
      change leaves, after the merge with `main` at `2284a998`: the gate's own line reports 328 cases, none failed — five
      suites, the shell ones 187 + 107 + 34 = 328, and no compiled step (since #273 a compiled path is read by the
      tree it sits in, and this delta holds no compiled path). Verify: `evidence/gate-final-tree.log`,
      `evidence/gate-tallies-final/`.
- [x] 6.2 **The four stale pointers the review named.** `gate-attribution.md` and the focused-cases row now name
      `gate-round-1/`; `green-fix.xml`'s sentence quoting a tally that had moved is gone; the sibling-driver
      sentence says 27 shell cases of which 17 are `resolver#…`. The round-2 record is named `gate-round-2.log` /
      `gate-tallies-round-2/`, so the top-level name no longer means two runs.
- [x] 6.3 **The merge with `main`, and the label it moved.** #273 and #275 landed while this change waited on a
      decision, and #275 took `49.1`–`49.4`; the marker record is now `48.2` (one label, one invariant). Re-ran both
      records on the merged tree: `evidence/revert-check-48.1.log`, `evidence/revert-check-48.2.log` —
      red-when-mutated 1 · not-noticed 0 · restore-failures 0 each. The pre-merge run is kept as
      `evidence/revert-check-49.1-before-the-merge.log`.
- [x] 6.4 **The merged tree still holds both changes.** The driver carries `core_ensure` beside #275's selection
      filter, `--case` runs still print the `all N cases passed` line the cases assert, and the spec carries both
      requirements. Verify: `tools/loop-selftest.sh --list | wc -l` → 187, and the gate above.
