# Tasks — fix-gate-tallies-belong-to-one-run

## 1. The red case (do first)

- [x] 1.1 `gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes` (`tools/loop/tests/loop-cases.sh`), registered in
      `LOOP_SHELL_CASES` and the dispatcher: a throwaway profile owing one stub suite, a carrier an earlier run
      wrote already in the tally directory, asserting the gate's line carries the stub's numbers, the directory
      holds one carrier, and neither reads the planted failure. Verify: red on the base
      (`db1971d1577ec39004d14c7d27ea9c5b94d6c87b`) — `evidence/red-on-head.log`, `evidence/red-on-head.xml`
      (`tests=1 failures=1`, 2026-11-11T11:56:47Z); green with 2.2 — `evidence/green-carrier.log`.
- [x] 1.2 `gate#aSecondGateRefusesWhileOneRuns`: the tally directory's lock is held by a live process, so the gate
      refuses (exit 4) before a step, names the lock and its owner, leaves the lock, and prints no `LOOP-GATE:`
      line. Verify: red on the base — `evidence/red-on-head-refusal.log`, `evidence/red-on-head-refusal.xml`
      (`tests=1 failures=1`, `expected '4', got '0'`); green with 2.1 — `evidence/green-refusal.log`.
- [x] 1.3 Both cases declare `# files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh
      tools/tooling.json`, and the declaration case ran first: `PASS driver#everyCaseDeclaresTheFilesItCanReach`
      (the gate's plan puts it before everything else for a delta that writes a case, issue #792).

## 2. The fix (`agent-issue-loop` — "A gate's tallies belong to the run that wrote them")

- [x] 2.1 `tools/loop/gate.sh` claims `$tally_root/.gate.lock` for the whole run (owner = pid and UTC start time,
      live-owner refusal `LOOP-GATE-REFUSED:` with exit 4, dead-owner takeover with a line naming whose lock it
      was), releases it on `EXIT` beside the step-output directory, and documents exit 4 in the usage header.
      Verify: task 1.2 green.
- [x] 2.2 `tools/loop/gate.sh` clears every `*.xml` the directory still holds once the lock is held and before the
      first step, carries `run="<pid>"` on each tally it writes, and names the directory and the run on the console
      (`LOOP-GATE-TALLY:`). Verify: task 1.1 green, and the `gate#` group unchanged —
      `evidence/gate-cases.log`, `all 35 cases passed` (180 s, 2026-11-11).
- [x] 2.3 `tools/loop/tests/loop-mutations.sh`: `848.1` (the sweep removed) and `848.2` (the refusal made
      unconditional-false). Verify: `--check` applies every record (`all 332 cases passed`), and each run is red on
      its own case and green again after the restore — `RED 848.1 gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes`,
      `RED 848.2 gate#aSecondGateRefusesWhileOneRuns`, `restore … green again` (2026-11-11T11:57:46Z / 11:57:51Z),
      both transcripts in `evidence/mutations-848.log`.

## 3. The rule beside its measurement

- [x] 3.1 `guidelines/Backlog.recipes.md`, the section "The gate and its suite": one paragraph stating that the
      tally directory is one run's, the two halves that make it so, and the measurement behind it (2026-11-10, item
      #830). Verify: `tools/check-doc-routes.sh` (`all 330 section references resolve`) and
      `tools/tests/check-doc-routes-selftest.sh` (`all 34 cases passed`).

## 4. The measurement the change owes

- [x] 4.1 `./tools/loop/measure.sh --change fix-gate-tallies-belong-to-one-run` over the final change, quoted in
      `evidence/measure-block.log`: the case count (`cases=407`), the checks this pathspec owes, and the gate's
      timing line (`seconds=-`, the gate of this tree has not run — the gate child runs it).
