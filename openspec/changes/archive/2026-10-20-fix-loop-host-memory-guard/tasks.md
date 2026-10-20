# Tasks — fix-loop-host-memory-guard

Parent spec for every task below: `openspec/specs/agent-issue-loop/spec.md`, requirement **"One command starts a
budgeted run, with no dialogue"**. The delta is
`openspec/changes/fix-loop-host-memory-guard/specs/agent-issue-loop/spec.md` (three scenarios added: "The host a
run works on is read and named", "A host below the floor is reported instead of spent", "A host that publishes
no reading is not refused").

## 1. Reproduce the defect

- [x] 1.1 Add `run#hostBelowTheFloorBlocksTheItemAndStops` to `tools/loop-cases.sh`, registered in **both** the
  `--list` array and the dispatch map: drive the real `run.sh` against the stub `gh`/`pi` with a `LOOP_CONF`
  copy whose appended `LOOP_MIN_FREE_MIB=99999999` no host reaches, then assert that no agent process was
  started, no `worker-report.md` and no `fix/issue-*` branch exist, the item is recorded `blocked` with
  `the host has <n> MiB available` and `below the recorded floor of 99999999 MiB`, and the run's recorded stop
  reason names that floor. **Parent:** the delta scenario "A host below the floor is reported instead of
  spent". **Verification:** on HEAD `fb713c1` the case fails with
  `no agent process is started on a host below the floor: expected '', got 'invocation … prompt:
  worker-prompt.md …'` — `evidence/red/hostBelowTheFloorBlocksTheItemAndStops.txt`.
- [x] 1.2 Add `run#hostAboveTheFloorRunsTheItem` the same way, with `LOOP_MIN_FREE_MIB=0`: the header names
  `min-free-mib=0` and a numeric `free-mib=<n>`, one child starts, the item is recorded `done`/`closed`.
  **Parent:** the delta scenario "The host a run works on is read and named". **Verification:** on HEAD it
  fails on the header line (`'min-free-mib=0' not found in: agent-issue-loop: items=1 …`) —
  `evidence/red/hostAboveTheFloorRunsTheItem.txt`.
- [x] 1.3 Add `run#hostWithoutTheReadingPasses`: `LOOP_MEMINFO_PATH` points at a file that does not exist and the
  unreachable floor of 1.1 is set, so only the missing reading can let the item run; it must run, close, and
  the header must say `free-mib=unknown`. **Parent:** the delta scenario "A host that publishes no reading is
  not refused". **Verification:** on HEAD it fails on the header (`'free-mib=unknown' not found in:
  agent-issue-loop: items=1 …`) — `evidence/red/hostWithoutTheReadingPasses.txt`.

## 2. The fix

- [x] 2.1 `tools/loop/run.sh`: add `host_free_mib` (reads `MemAvailable` from the file the recorded key
  `LOOP_MEMINFO_PATH` names, MiB, nothing when the file or the field is absent), read the floor from
  `LOOP_MIN_FREE_MIB` with the recorded default 1024, and print `min-free-mib=` and `free-mib=` in the run
  header beside the budget. **Parent:** the delta scenario "The host a run works on is read and named".
  **Verification:** `run#hostAboveTheFloorRunsTheItem` and `run#hostWithoutTheReadingPasses` go green —
  `evidence/green/`.
- [x] 2.2 `tools/loop/run.sh`: in the item loop, after `state.sh next` returns an issue and before `start-item`,
  read the host again and, below the floor, record the item `blocked` with the measurement and the floor, print
  it and stop the run with that as its stop reason. `run_child`, its progress lines and
  `LOOP_CHILD_MAX_SECONDS` stay as they are. **Parent:** the delta scenario "A host below the floor is reported
  instead of spent". **Verification:** `run#hostBelowTheFloorBlocksTheItemAndStops` goes green, and
  `run#childOutlastingTheWallClockIsBlocked` still passes — `evidence/green/`.
- [x] 2.3 `tools/loop/loop.conf`: record `LOOP_MIN_FREE_MIB=1024` and `LOOP_MEMINFO_PATH="/proc/meminfo"` beside
  the other recorded values, with the 2026-10-19 measurement (194 MB / 18 GB swap / 30 min against 3m47s, and
  467 MB free / 2 499 MB available during a run) and the rule "an absent file passes". **Parent:** both
  scenarios that name a recorded value. **Verification:** `grep -n -A6 'LOOP_MIN_FREE_MIB' tools/loop/loop.conf`
  names the default and the measurement, and the cases read the keys through `loop_conf_value`.
- [x] 2.4 `tools/loop-mutations.sh`: record `24.1`, the guard's `if` replaced by `if false`, naming
  `run#hostBelowTheFloorBlocksTheItemAndStops`. **Parent:** the delta scenario "A host below the floor is
  reported instead of spent". **Verification:** `bash tools/loop-mutations.sh --only 24.1` reports
  `RED 24.1 …` and `restore 24.1 … green again` — `evidence/green/mutation-24.1.txt`.

## 3. The documents agree with the code

- [x] 3.1 `.pi/skills/fix-loop/SKILL.md`: the flag table and the "Budget authority" section name the host read;
  the stop-condition list gains the memory floor with the reason it writes and the `free-mib=` header field.
  **Parent:** all three delta scenarios. **Verification:** read back against `run.sh` — the quoted reason is
  the string the case asserts.
- [x] 3.2 `.pi/skills/fix-loop/REFERENCE.md`: "Measured costs" gains the host measurement and the floor, and
  says a starved host is reported rather than paid for. **Parent:** the delta scenario "A host below the floor
  is reported instead of spent". **Verification:** `grep -n 'MemAvailable' .pi/skills/fix-loop/REFERENCE.md
  .pi/skills/fix-loop/SKILL.md tools/loop/loop.conf` names the same reading in all three.
- [x] 3.3 `guidelines/Backlog.md` and `guidelines/Build.md` need no edit: the loop's recorded defaults are
  described there by name only, and neither `LOOP_MIN_FREE_MIB` nor `LOOP_MEMINFO_PATH` appears
  (`grep -n 'MIN_FREE\|MEMINFO' guidelines/*.md` — no match before or after this change). Named here so the
  absence is deliberate. **Parent:** "The run's mechanics are the project's configuration, not its code".
  **Verification:** the grep above.

## 4. Verification (offline; this change compiles nothing)

- [x] 4.1 Structural check instead of a build: no Kotlin, Java, C++, Gradle, manifest or resource file is
  touched, so `guidelines/Build.md` §2's scope block applies and the check is `bash -n` on the two edited shell
  files plus the self-test gate for `tools/loop/**`. **Verification:**
  `bash -n tools/loop/run.sh tools/loop-cases.sh`. **Exclusion:** no Gradle build and no JVM suite is run,
  because nothing they compile is touched.
- [x] 4.2 The three new cases, green on the fix: `bash tools/loop-selftest.sh --case
  'run#hostBelowTheFloorBlocksTheItemAndStops'`, `--case 'run#hostAboveTheFloorRunsTheItem'`, `--case
  'run#hostWithoutTheReadingPasses'` — `evidence/green/`.
- [x] 4.3 The two existing cases that own the surfaces this touches stay green:
  `run#childOutlastingTheWallClockIsBlocked` (the child cap `run_child` must keep) and
  `run#stopReasonIsPrintedAndReported` (the stop the run records is the stop it prints) —
  `evidence/green/existing-surface-cases.txt`. **Exclusion:** the whole offline suite is **not** run here; the
  orchestrator runs it once on the merged tree.
- [x] 4.4 Falsification: restoring HEAD's `tools/loop/run.sh` and `tools/loop/loop.conf` (the guard and the two
  keys removed) turns all three cases red, which is exactly `evidence/red/`. The mechanical form of that
  mutation is `24.1` in `tools/loop-mutations.sh` — the guard's own `if` replaced by `if false` — and
  `bash tools/loop-mutations.sh --only 24.1` reports `RED 24.1 …` and `restore 24.1 … green again`
  (`evidence/green/mutation-24.1.txt`). **Verification:** `evidence/red/` against `evidence/green/` on the same
  host, and the mutation run.
- [x] 4.5 `bash tools/check-portability.sh --quiet`, `openspec validate fix-loop-host-memory-guard --strict`
  and `openspec doctor`, all clean. **Verification:** their output in `evidence/green/validation.txt`.
