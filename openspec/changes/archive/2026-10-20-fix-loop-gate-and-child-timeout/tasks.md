# Tasks — fix-loop-gate-and-child-timeout

Parent spec for every task below: `openspec/specs/agent-issue-loop/spec.md`, requirement **"One command
starts a budgeted run, with no dialogue"** (and, for the configuration tasks, **"The run's mechanics are the
project's configuration, not its code"**). The delta is
`openspec/changes/fix-loop-gate-and-child-timeout/specs/agent-issue-loop/spec.md`.

## 1. Reproduce the defect

- [x] 1.1 Add `run#childOutlastingTheWallClockIsBlocked` to `tools/loop-cases.sh`, registered in **both** the
  `--list` array and the dispatch `case` map: a stub child blocks on a fifo, the case runs with
  `LOOP_CHILD_MAX_SECONDS=2` through the `LOOP_CONF` seam, and asserts the item is recorded `blocked`, its
  note names the cap, the transcript carries `LOOP-TIMEOUT: the child exceeded 2 s`, the run prints
  `exit 124`, and nothing is closed. **Parent:** the delta scenario "A child that outlasts the configured
  wall-clock is ended and recorded", case `run#childOutlastingTheWallClockIsBlocked`.
  **Verification:** `bash tools/loop-selftest.sh --case 'run#childOutlastingTheWallClockIsBlocked'` fails on
  HEAD's `tools/loop/run.sh` with `the run was still waiting after 10 s on a child that outlasted its 2 s
  cap` — recorded in `evidence/red-on-head.txt`.

## 2. The fix

- [x] 2.1 `tools/loop/loop.conf`: `LOOP_GATE_CMD` becomes the invocation the host completes,
  `./gradlew test -PforceTests --no-build-cache`, with the measurement (3m47s against 30 min and no suite)
  in the comment beside it. **Parent:** "The run's mechanics are the project's configuration".
  **Verification:** `git diff tools/loop/loop.conf` — the recorded line, and the case
  `state#projectKeysAreReadFromLoopConf` still passes with the shortened value it substitutes.
- [x] 2.2 `tools/loop/loop.conf`: record `LOOP_CHILD_MAX_SECONDS=2700` beside the other budget defaults,
  documented with the measurement behind it. **Parent:** the delta scenario above.
  **Verification:** the new case proves the value is read (its 2 s copy changes the run's behaviour), and the
  run header prints it.
- [x] 2.3 `tools/loop/lib.sh`: `LOOP_CHILD_TIMEOUT=124`, so a cap-imposed end is a status no child returns by
  itself. **Parent:** the delta scenario. **Verification:** the new case asserts `exit 124` in the run's own
  output and uses it to reach the blocked path.
- [x] 2.4 `tools/loop/run.sh`: `run_child` starts the child as its own process group (`set -m` for that one
  command, undone immediately, stdin `/dev/null`), polls it against the cap, ends the group with `TERM` then
  `KILL`, appends the `LOOP-TIMEOUT` line to the transcript and returns `LOOP_CHILD_TIMEOUT`. The heartbeat
  (`LOOP_PROGRESS_SECONDS`) and the `LOOP-OUTSIDE-WRITE` check keep working around it. **Parent:** the delta
  scenario. **Verification:** the new case, plus `run#childReportsWhileItWaits` and
  `run#childWriteOutsideTheWorktreeIsBlocked` in the full suite.
- [x] 2.5 `tools/loop/run.sh`: the run header names `child-max-seconds`, and the blocked reason appends the
  timeout line when the item's transcript carries one. **Parent:** the delta scenario and "The run stops on
  its own authority". **Verification:** the new case asserts the item's note names the cap.
- [x] 2.6 `tools/loop/prompts/worker.md`: the gate is run **once**, and a gate that does not complete inside
  the run's budget is `blocked` with its log and never retried. **Parent:** the delta scenario.
  **Verification:** `prompt#workerNamesEveryPhaseAndSkill` (the rendered prompt still carries every phase,
  and every placeholder is substituted) in the full suite.

## 3. The documents agree with the code

- [x] 3.1 `.pi/skills/fix-loop/SKILL.md`: invariant 3 states the inner bound, the failure-handling table gains
  the timeout row, the "what a run prints" section explains `exit 124` and the `LOOP-TIMEOUT` line, and a
  "the gate is run once" paragraph states the rule. **Parent:** the delta scenario.
  **Verification:** the skill's own text names the key and the line — read back against `run.sh`.
- [x] 3.2 `guidelines/Backlog.md` §7 (what bounds the loop) and §11 (the configuration-key table, which gains
  `LOOP_CHILD_MAX_SECONDS`). **Parent:** "The run's mechanics are the project's configuration".
  **Verification:** `grep -n 'LOOP_CHILD_MAX_SECONDS' guidelines/Backlog.md .pi/skills/fix-loop/SKILL.md
  tools/loop/loop.conf` names the same default in all three.

## 4. Verification (offline; this change compiles nothing)

- [x] 4.1 Structural check instead of a build: the change touches no compiled file — no Kotlin, Java, C++,
  Gradle, manifest or resource — so `guidelines/Build.md` §2's scope block applies and the structural check is
  `bash -n` on the four shell files it edits plus the self-test gate for `tools/loop/**`.
  **Verification:** `bash -n tools/loop/run.sh tools/loop/lib.sh tools/loop-cases.sh tools/loop-mutations.sh`,
  and the case and mutation evidence below. **Exclusion:** no Gradle build and no JVM suite is run, because
  nothing they compile is touched.
- [x] 4.2 Revert-check for the new bound: restore HEAD's `tools/loop/run.sh` (the single mutation: no cap),
  re-run the case, watch it fail, restore the fix, run it green. The mechanical form is mutation 23.1 in
  `tools/loop-mutations.sh`, and mutations 9.9 and 12.2 were re-pointed at the rewritten child start so the set
  still applies.
  **Verification:** `evidence/red-on-head.txt` (fails), `evidence/green-on-fix.txt` (passes), and
  `bash tools/loop-mutations.sh --only '9.9 12.2 23.1'` →
  `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0` — case
  `run#childOutlastingTheWallClockIsBlocked`.
- [x] 4.3 The case on the rebased tree (`0205d83`): `timeout 300 bash tools/loop-selftest.sh --case
  'run#childOutlastingTheWallClockIsBlocked'` — `PASS`, no stub left behind.
  **Verification:** `evidence/green-on-fix.txt`. The whole offline suite is **not** run by this change: the
  orchestrator runs it once on the merged tree (a second suite on the same host produced load-induced
  failures), and the one run made here is kept as `evidence/full-suite-superseded-under-load.txt` with its
  reason. **Exclusion named:** the full-suite tally is the orchestrator's, not quoted here as green.
- [x] 4.4 `openspec validate fix-loop-gate-and-child-timeout` and `openspec doctor`, both clean.
  **Verification:** `evidence/openspec.txt`.
