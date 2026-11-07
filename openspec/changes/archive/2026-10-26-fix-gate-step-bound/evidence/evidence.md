# Evidence — fix-gate-step-bound

Base measured against: `abb6a8c4178751122a325a8402a335d454b948c8` (the run's base, and this worktree's `HEAD`).

## The measure block

`./tools/loop/measure.sh --change fix-gate-step-bound`, from the change root, once the change existed
(`measure.log`):

```
LOOP-MEASURE base=6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 paths=99 insertions=5608 deletions=33
LOOP-MEASURE-SUITE cases=234 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=1033 suites=23
LOOP-MEASURE-CHANGE fix-gate-step-bound scenarios=2 cases=2 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 17 gate cases re-run green, 2 mutation records realigned + 1 added, 1 requirement
```

The base the block reports is `origin/main`'s tip, which this worktree is two items ahead of: `paths=99` is the
gate's own view of what it owes (the two landed items' sources included — `insertions=5608`), not this delta. The
delta is the five tracked files of the diff below plus the artifacts of this directory. `LOOP-MEASURE-GATE
seconds=1033 suites=23` is taken **after** the gate ran, and is quoted in "Phase D" below.

## Phase A — the condition, the surface, the red case

- The condition holds on the base: the gate ran a step as `$cmd … &` plus `wait "$step"` (`gate.sh:184-195` on
  `abb6a8c4`) and no key, no case and no requirement bounded it — `surface-head.log`:

```
$ git show abb6a8c4:tools/loop/gate.sh | grep -n 'run_step\|wait "\$step"\|tee "\$stream"'
184:run_step() { # <command> → the command's status
195:  wait "$step"
212:  run_step "$cmd"
$ git show abb6a8c4:tools/loop/loop.conf | grep -n 'LOOP_CHILD_MAX_SECONDS\|LOOP_CASE_MAX_SECONDS\|LOOP_GATE_STEP_MAX_SECONDS'
52:LOOP_CHILD_MAX_SECONDS=2700
99:# worker that ran that gate was then ended by its own `LOOP_CHILD_MAX_SECONDS`, and the item it was working on was
104:LOOP_CASE_MAX_SECONDS=900
```

- Surface on the base (`surface-head.log`): 16 `gate#*` cases (17 after the added one) and 9 mutation records quoting
  the engine, 2 of them naming a line inside `run_step` (`46.1`, `63.1`); 0 places in `guidelines/` or
  `openspec/specs/` naming a per-step bound.
- Red test on the base (`red-on-head.log`, tally `red-on-head.xml`, `tests="1" failures="1"`):

```
$ bash tools/loop-selftest.sh --case gate#aStepIsBoundedAndTheOverrunNamesIt
  the step ran past its bound: still inside the gate 20s into a suite that never returns, with the bound set to 2s
FAIL gate#aStepIsBoundedAndTheOverrunNamesIt
loop-selftest: 1 of 1 cases failed
```

The case is a throwaway project whose suite prints `notes: a case that never returns`, starts `( sleep 30 ) &` and
waits for it for ever, in a profile whose `LOOP_GATE_STEP_MAX_SECONDS` is `2`. On the base the gate never returns, so
the case's own 20 s watchdog ends the wait and reads it as the defect; after the change the gate ends the step at the
bound (2 s), names it, and exits 1 with `errors="1"` and `tests="0"`.

- Green, the same case and the 16 beside it (`green-cases.log`, `--group gate`: 17 of 17):

```
$ bash tools/loop-selftest.sh --group gate
… PASS gate#aStepIsBoundedAndTheOverrunNamesIt …
loop-selftest: all 17 cases passed (JDK 27, offline, no token, no sandbox) — selection: --group gate
```

- The two mutation records that name a line inside `run_step` are realigned, and one record is added for the guard
  (`loop-mutations-check.log`, `loop-mutations-311.log`):

```
$ bash tools/loop-mutations.sh --check
loop-mutations: all 173 cases passed — every selected record applies to the file it names
$ bash tools/loop-mutations.sh --only '46.1 63.1 63.2'
RED    46.1  gate#theSuitesOutputReachesTheConsoleWhileItRuns
RED    63.1  gate#aStepEndsWithItsCommandNotWithItsWatchers
RED    63.2  gate#aStepIsBoundedAndTheOverrunNamesIt
red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0
```

`63.2` is the guard itself (`if [ … -ge "$step_max" ]` → `if false`): with it, the new case reads a gate that is still
inside a suite which never returns — the state the item was filed about.

## Phase D — the gate

One run, `./tools/loop/gate.sh` from the change root, exit 0 (`gate.log`; the tallies it wrote are in this directory,
copied from the profile's `app/build/test-results/loop-gate/`):

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
…
LOOP-GATE: tests=597 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 307s, tools/loop-selftest.sh --from-git 6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 403s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 233s, … tools/issue-tooling-selftest.sh 57s, …)
```

`LOOP-MEASURE-GATE seconds=1033 suites=23` above is the same run's own total. The two steps the change is about are
the two slowest, and neither is near the bound: **403 s** for the whole loop self-test (234 cases) and **307 s** for
the Gradle gate — against `LOOP_GATE_STEP_MAX_SECONDS=1800`, which is what the default is set from.

The tallies this directory carries, quoted from each one's own attributes:

- `tools_loop-selftest.sh_--from-git_6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3.xml` — `tests="234" failures="0"
  errors="0" time="403"`, the declared set (`LOOP-MEASURE-SUITE cases=234`), the new case included.
- `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` — `tests="0" failures="0" errors="0" time="307"`:
  a Gradle invocation writes no `all N cases passed` line, so the gate's tally writer reads its cases from the
  JUnit XMLs beside it, not from this file — the same shape `2026-10-25-fix-retired-loop-skill-still-shipped`
  recorded.
- `bash_tools_loop-mutations.sh_--check.xml` — `tests="173" failures="0"`, every record applies to the file it names,
  `46.1`, `63.1` and `63.2` included; `tools_loop-mutations-selftest.sh.xml` — `tests="34" failures="0" time="233"`.
- `tools_check-portability-selftest.sh.xml` — `tests="29" failures="0" time="22"`; `tools_issue-tooling-selftest.sh.xml`
  — `tests="121" failures="0" time="57"`.
- The remaining tallies (`tools_check-portability.sh.xml`, `tools_check-doc-routes.sh.xml`,
  `tools_check-openspec-hygiene.sh.xml`, `tools_check-todo-references.sh.xml`,
  `tools_check-compose-sandbox-rule.sh.xml`, `bash_tools_gen-feature-list.sh_--check-classification.xml`,
  `tools_mutate-selftest.sh.xml` and the seven `tools_mutate.sh --mutations … --list.xml`) — `tests="0" failures="0"`:
  a check that reports a verdict rather than a case count, or a `--list` that prints a set.
- `gate-summary.xml` — `tests="597" failures="0"`, the aggregate, derived from the gate's own summary line the way
  the gate derives each per-suite tally (no single tally carries the sum).

## The delta, and what was not taken

`tools/loop/gate.sh` (the bound, the named overrun, the announced clock), `tools/loop/loop.conf` (the key and its
measurement), `tools/loop-cases.sh` (the case), `tools/loop-mutations.sh` (`63.2` added, `46.1` and `63.1` realigned),
`guidelines/Backlog.md`, section "The loop" (the paragraph, and the stale `tee`d clause beside it corrected to what issue #315 left).
No compiled file is touched; the Gradle step in the gate is owed because the tree's diff against a stale
`origin/main` still carries two earlier items' sources, not because of this delta.

The item's second candidate repair (raise `LOOP_CHILD_MAX_SECONDS` from a measured baseline) is not taken; the
measurements that eliminate it, and why a bound answers a hang a baseline cannot, are `design.md`'s "Alternatives the
evidence eliminated" — the same gate measured 8m25s, 9m46s and 5m45s on three occasions (`guidelines/Build.md` §2c),
and the same 5 680 tests took 3m47s quiet and 30 min at 194 MB free RAM.

## The one edit after the gate

A comment in `tools/loop/gate.sh` cited the run's own child wait as `run.sh:992`; the line is `run.sh:994`, so the
comment now names the lesson without a line number, and `design.md` carries the number where the check resolves it.
No statement, no assertion and no executed line moved — the edit is inside a `#` block — and the case the change adds
was re-run over the edited file (`evidence/focused-after-comment.log`): `PASS`, exit 0. `evidence/gate.log` and the
tallies in this directory are from the run before that comment was touched.
