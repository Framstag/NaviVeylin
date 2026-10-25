# Tasks — fix-loop-selftest-case-pool

## 1. Red cases (fail on HEAD)

- [x] `driver#theSuiteRunsItsCasesInAPool` — a nested suite of four stub cases that record `start`/`stop` around a
      one-second sleep; the largest running count read from the log's order. Verified on HEAD `a2489ca7` with
      `git show HEAD:tools/loop-selftest.sh` driven against this tree's cases: `the suite ran its cases one after
      another (at most 1 was running at any moment)` → `FAIL`, `1 of 1 cases failed`
      (`evidence/red-on-head-pool.log`)
- [x] `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` — the probe case leaves a `sleep` in a group of its own at
      the scratch root the driver gives it, and the second case polls for it. On HEAD the driver hands no per-case
      root at all: `FAIL`, `1 of 1 cases failed` (`evidence/red-on-head-sweep.log`)

## 2. The pool

- [x] `--jobs <n>` (a guarded value flag, a positive integer, refused otherwise), else `LOOP_SELFTEST_JOBS` from the
      profile, else `nproc`/`getconf _NPROCESSORS_ONLN`/one — `selftest_workers()`
      — verified: `bash tools/loop-selftest.sh --jobs 1 …` runs one case at a time, `--jobs 0` and `--jobs x` exit 2
      with the value named
- [x] `wait -n` as the reaper, with a bash 4.3 refusal beside the JDK and `pgrep` refusals
      — verified: `bash tools/loop-selftest.sh --case 'driver#theSuiteRunsItsCasesInAPool'` → `PASS`
- [x] `start_case`/`finish_case`: a slot per case, its own process group (`set -m`), its own root (`LOOP_CASE_TMP`),
      its own output, its status recorded by the subshell that ran it
      — verified: `bash tools/loop-selftest.sh --group state` → 18 cases, all `PASS`
- [x] Per-case cleanup: the case's group first, then the processes standing in its own root
      — verified: `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` → `PASS`
- [x] `on_exit` ends every running slot's group before the suite-level sweep and the lease release
      — verified by the shipped cases that assert the interrupted suite's behaviour, green in the pooled run:
      `clean#interruptedSuiteRemovesItsScratchTree`, `clean#interruptedSuiteEndsItsProcessGroup`,
      `clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft` (`evidence/full-suite-pooled.log`)
- [x] One compile per suite under the pool: the claim `mkdir` around `core_ensure`'s compile
      — verified: `driver#theCoreIsCompiledOncePerSuite` (two asking cases, `javac` invoked once) → `PASS`

## 3. The cases around it

- [x] `scratch()` creates a case's trees under `LOOP_CASE_TMP` when the driver sets it, and under `LOOP_TMP` when it
      does not — verified by the whole suite, which creates its trees through it (`evidence/full-suite-pooled.log`)
- [x] Three assertions that compared the order cases ran in now compare the set: `aFileFilter…` (`sort`),
      `aGroupFilter…` (`sort`, twice), `aSecondSuite…` (`sort` of the last two lines)
      — verified: `bash tools/loop-selftest.sh --case 'driver#aGroupFilterRunsOnlyItsOwnCases'` → `PASS`
- [x] Whole suite, pooled: `221 cases`, `4:24,11`, `439 % CPU` (`evidence/full-suite-pooled.log`)
- [x] One group both ways, for the size of the win: `state` 1:00,12 → 20,86 s, `worktree` 1:07,79 → 20,06 s

## 4. The mutation set

- [x] `59.1` — the pool's refill line bounded to one case at a time → `driver#theSuiteRunsItsCasesInAPool` `RED`,
      restore green (`evidence/mutation-run-new-records.log`)
- [x] `59.2` — the per-case sweep left out → `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` `RED`, restore green
      (`evidence/mutation-run-new-records.log`)
- [x] `48.2` repaired to the marker check it now names → `driver#theCoreIsCompiledOncePerSuite` `RED`, restore green
      (`evidence/mutation-run-new-records.log`)
- [x] `bash tools/loop-mutations.sh --check` → `all 152 cases passed`

## 5. The rule where a person reads it

- [x] `guidelines/Backlog.md`, its section "The loop" — the suite's cost paragraph now states the pool, the per-case root and sweep, the
      measured pooled number and the `--jobs`/profile bound
- [x] `tools/loop-selftest.sh` header — the usage and exit lines name `--jobs`, the per-case root and the `wait -n`
      requirement
- [x] `tools/loop/loop.conf` — `LOOP_SELFTEST_JOBS`, with the measurement that motivates it

## 6. Gate, evidence, archive

- [x] `./tools/loop/gate.sh` run once — `LOOP-GATE: tests=534 failures=0 errors=0`; its tallies copied into
      `evidence/gate/` (`tools_loop-selftest.sh_--from-git_db888dd8….xml`: `tests="221" failures="0"`, 283 s) and
      the summary line quoted (`evidence/gate.log`, `evidence/gate/loop-gate-summary.xml`). The artifacts edited after
      it — this change's own `proposal.md`, `design.md`, `tasks.md` — were re-read by the structural checks the gate
      owes for a markdown or `openspec/**` path, not by a second gate: `openspec validate … --strict` (valid),
      `tools/check-openspec-hygiene.sh` (consistent), `tools/check-doc-routes.sh` (153 + 4 references resolve),
      `tools/check-todo-references.sh` (63 resolve), `tools/gen-feature-list.sh --check-classification` (stale 3)
- [x] every cited XML and log copied into `evidence/`, and the pointers checked mechanically:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-selftest-case-pool --root .` →
      `evidence-check: PASS` (C1-C5 ok)
- [x] the change archived, the delta merged into `openspec/specs/agent-issue-loop/spec.md`
