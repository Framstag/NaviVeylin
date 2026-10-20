# Tasks — fix-loop-child-usage-in-report

## 1. The red cases first

- [x] 1.1 Write `run#childUsageIsRecordedPerItem` in `tools/loop-cases.sh`, registered in the `--list` array and
      in the dispatch map (spec: "One command starts a budgeted run, with no dialogue" — scenario "An item's
      spend is read from the child's own stream"); verify by running it against HEAD's engine
      (`LOOP_ENGINE`/`LOOP_CONF` pointed at `git archive HEAD tools`): it fails with `the tool counts are
      recorded by name: expected '2', got 'null'`, recorded in
      `evidence/red/run_childUsageIsRecordedPerItem.txt`.
- [x] 1.2 Write `report#zeroUsagePrintsDashNotZero` (spec: scenario "Nothing reported is printed as zero") and
      `run#plainTextChildStillReports` (the same scenario's third case: no stream at all), registered in both
      places; verify each fails on HEAD with `loop: done: unknown argument --events` / `no table row for #21`
      and `'| - | - | - | done |' not found in: | #21 | … | 4s | done | - |` — `evidence/red/`.
- [x] 1.3 Teach the stub child to print an event fixture (`PI_STUB_EVENTS` in `tools/loop/fixtures/pi`) and the
      throwaway project to copy the adapter; verify `bash tools/loop-selftest.sh --list` names all three cases.

## 2. The adapter, and the configuration that names it

- [x] 2.1 `tools/pi-child-events.sh`: start the command in its JSON event mode, append every event line to
      `$LOOP_CHILD_EVENTS`, pass a non-JSON line through, print the last assistant message's text, exit with the
      child's status (spec: scenario "An item's spend is read from the child's own stream"). Verify with
      `run#childUsageIsRecordedPerItem`, whose worker prints no plain text at all and can only close if the
      adapter printed the answer out of `message_end`.
- [x] 2.2 `tools/loop/loop.conf`: `LOOP_AGENT_CMD="bash tools/pi-child-events.sh pi"`. Verify the engine still
      names no agent command: `bash tools/loop-selftest.sh --case 'run#engineNamesNoProjectFact'`, and that the
      seam still substitutes one: `--case 'state#agentCommandIsTheOnlyIndirection'`.

## 3. The run exports the stream, the state records the sums

- [x] 3.1 `tools/loop/run.sh`: export `LOOP_CHILD_EVENTS` per item (`items/<n>/events.jsonl`, removed before the
      item's first child) and pass it to the three `state.sh done` call sites; nothing else about the child, its
      cap or its progress lines changes (spec: scenario "An item's spend is read from the child's own stream").
      Verify with `--case 'run#childReportsWhileItWaits'` (the wait still reports) and
      `--case 'run#childOutlastingTheWallClockIsBlocked'`-shaped behaviour through `--case
      'run#metricsRecordedPerItem'`.
- [x] 3.2 `tools/loop/state.sh` `done --events FILE`: record `metrics.tools`, `metrics.tokens`, `metrics.cost`
      and `metrics.context` beside `metrics.wallSeconds`, a turn counted at its last usage record and the
      context as a maximum (spec: scenario "An item's spend is read from the child's own stream"); verify with
      `--case 'run#childUsageIsRecordedPerItem'` (2000 input tokens, not the 3000 a per-update sum would give).
- [x] 3.3 `tools/loop/state.sh` `report`: print the `tools`, `tokens` and `context` columns after `wall`, `-` for
      a value nothing reported (spec: scenarios "A run is recorded", "Nothing reported is printed as zero");
      verify with `--case 'report#zeroUsagePrintsDashNotZero'`, `--case 'run#plainTextChildStillReports'` and
      `--case 'report#wallTimeAndReasonInTheTable'` (fourteen columns, fifteen separators, every earlier column
      in its place).
- [x] 3.4 Revert-check the two new invariants: mutate the `tokens` cell to print `$t.input` unconditionally
      (`report#zeroUsagePrintsDashNotZero` must fail), and move the events-file removal into `run_child`
      (`run#childUsageIsRecordedPerItem` must report `bash=2`, not `4`); restore and re-run both green —
      `evidence/green/`.

## 4. The documents that quote the report

- [x] 4.1 `.pi/skills/fix-loop/SKILL.md`: the report template carries the three columns, the wrapper table names
      the adapter, and a new subsection states what a run measures and why `-` is not `0` (spec: scenarios "A
      run is recorded", "Nothing reported is printed as zero"); verify by reading the template against the
      output of `--case 'report#headerAndTableShape'`.
- [x] 4.2 `guidelines/Backlog.md` §7 "The loop": "what each one cost" names the spend read from the child's own
      event stream (spec: scenario "A run is recorded"); verify with
      `grep -n "the child's own event stream" guidelines/Backlog.md`.
- [x] 4.3 `openspec/specs/agent-issue-loop/spec.md`, "A run reports the wait it cannot get from the child": the
      reason clause named print mode, which the configuration no longer starts; restate it as "a child's answer
      arrives when it exits" and carry every scenario over verbatim (spec: that requirement's three scenarios);
      verify with `openspec validate fix-loop-child-usage-in-report --strict` and
      `--case 'run#childReportsWhileItWaits'`.

## 5. Structural checks — the scope block (no compiled file is touched)

- [x] 5.1 `bash -n` on `tools/pi-child-events.sh`, `tools/loop/run.sh`, `tools/loop/state.sh`,
      `tools/loop-cases.sh` and `tools/loop/fixtures/pi`: no syntax error; `bash tools/check-portability.sh
      --quiet`: 42 engines name no project value; `openspec validate fix-loop-child-usage-in-report --strict` and
      `openspec doctor` clean — all recorded in `evidence/green/validation.txt`.
- [x] 5.2 No Gradle and no device: the change touches no Kotlin, Java, C++, manifest or resource file, so
      `guidelines/Build.md` §2's scope block names the structural checks instead of the gate. Exclusion: the
      full offline suite (`tools/loop-selftest.sh`) is not run here — the orchestrator runs it once on the
      merged tree.
- [x] 5.3 Evidence: the three cases red on HEAD (`evidence/red/`) and green on the fix, together with the cases
      on this surface (`evidence/green/`).
