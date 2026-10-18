# Tasks

## 1. Red first

- [x] 1.1 Case `tools/loop-cases.sh` → `prescreen#refusalLeavesATraceInTheTracker`: the stale fixture's
      verdict writes `agent/excluded-stale`, leaves `agent/eligible` and every `status/*` alone, and a
      `--dry-run` pre-screen writes nothing.
- [x] 1.2 Run it against `HEAD` and keep the output: `evidence/red-on-head.log` (`FAIL`, `agent/excluded-stale`
      not found in the recorded `gh` write log).

## 2. The fix

- [x] 2.1 `tools/loop/prescreen.sh`: collect the refusal writes and apply them once after the queue, through
      `labels.sh`; add `--dry-run` (writes nothing).
- [x] 2.2 `tools/loop/labels.sh`: `exclude-stale`/`exclude-feature`/`exclude-settled` add the refusal label;
      `clear-*` removes it. No status write, no authorisation write.
- [x] 2.3 `tools/tooling.json`: the three `agent` values and the three `roles` keys the verdicts are read from.
- [x] 2.4 `tools/loop/run.sh`: pass `--dry-run` to the pre-screen on a dry run.
- [x] 2.5 The pre-screen cases that merged stderr into the JSON pipe read stderr from a file instead
      (a successful label write is a stderr line now).

## 3. Verification

- [x] 3.1 `bash tools/loop-selftest.sh --case 'prescreen#…'` (every prescreen case + `labels#verdictToLabelMatrix`)
      green.
- [x] 3.2 Mutation `15.1` — `tools/loop-mutations.sh --only '15.1' --timeout 120`: red-when-mutated 1,
      not-noticed 0, restore-failures 0.
- [x] 3.3 `bash tools/loop-selftest.sh` whole: all 64 cases passed (offline, no token).
- [x] 3.4 `bash tools/check-portability.sh`: OK — 40 engines and 2 templates name no project value.

## 4. Record

- [x] 4.1 `openspec validate fix-loop-prescreen-refusal-trace --type change` exits 0.
- [ ] 4.2 OWED TO THE OWNER — the three labels (`agent/excluded-stale`, `agent/excluded-feature`,
      `agent/excluded-settled`) must exist on the tracker; `gh issue edit --add-label` fails on a label the
      repository does not have, and the run reports that failure and continues (design D5).
