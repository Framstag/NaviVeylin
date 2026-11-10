# Tasks

The change's own line numbers are the tree's as this change leaves it; the base's are `c0ddb97c`. The red run's own
tally is `items/831/red-on-HEAD.xml` (`tests=1 failures=1`, 2026-11-10T01:08:29Z) and its output
`items/831/red-on-HEAD.log`; the probe that took it is `items/831/red-on-HEAD-probe.sh`.

## 1. The red case

- [x] 1.1 Added `report#theRepeatRuleNamesWhatItWithheld` to `tools/loop/tests/loop-cases.sh` (after
      `case_report_anItemRecordsItsRepeatReads`), with its first-line `# files:` declaration, its name in
      `LOOP_SHELL_CASES` and its arm in the dispatcher. Verify: against the base's three production files
      (`red-on-HEAD-probe.sh`), `bash tools/loop/tests/loop-selftest.sh --case
      'report#theRepeatRuleNamesWhatItWithheld'` → `FAIL … the record names the tool, the path, the characters the rule
      withheld and the line that entered instead: 'LOOP-TOOL-RESULT-REPEAT: … chars=40 kept=' not found in:` (exit 1,
      `items/831/red-on-HEAD.log`, `items/831/red-on-HEAD.xml` — `tests=1 failures=1`, 2026-11-10T01:11:45Z).
- [x] 1.2 The declaration's own check passes before the case's run is read:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS` (it refused
      the first draft for `tools/pi-child-events.sh` and `tools/tooling.json`, which `new_project` reaches; both are
      declared).
- [x] 1.3 The case is green on the tree this change leaves:
      `bash tools/loop/tests/loop-selftest.sh --case 'report#theRepeatRuleNamesWhatItWithheld'` → `PASS`.

## 2. The record — `tools/pi-trim-result.mjs`

- [x] 2.1 `repeatRecordLine` (`:70`) writes one line: the tool, the path, the characters the rule withheld and the
      characters of the line that entered. Verify: the case reads
      `LOOP-TOOL-RESULT-REPEAT: ctx_read path=… chars=40 kept=` and the `kept` it records equals the character count of
      the line the child was handed (`wc -m` of the CLI's output).
- [x] 2.2 `repeatResult` appends it through `read.record` (`:132`–`:140`), in the same try/catch discipline as the
      bound, and both entry points carry `record` and `tool` on the ledger invocation (`:215`, `:268`). Verify: the
      case's first read leaves the record empty, its repeat leaves exactly one line, and its read after an edit — a
      sha the ledger does not hold — leaves the count at one.
- [x] 2.3 The sibling rules are unchanged: `bash tools/loop/tests/loop-selftest.sh --case 'repeat#*'` → 4 of 4 `PASS`,
      `--case 'trim#*'` → 3 of 3 `PASS`.

## 3. The state — `tools/loop/state.sh`

- [x] 3.1 `child_usage_json` reads `<the stream's directory>/tool-result-trims.log` and sums the two fields
      (`:931`–`:940`), `null` where the item kept no record. Verify: the case reads `.metrics.repWithheld` = `40` and
      `.metrics.repKept` = `97` from a record holding one line, and `.metrics.dupChars` = `40` beside them.
- [x] 3.2 The fields reach the record: `child_usage_json` returns them (`:1087`), its fallback names them `null`
      (`:1100`), and `cmd_done` writes them into `metrics` (`:774`). Verify: the same case reads them from the state
      file, not from the report.
- [x] 3.3 MUTATION `96.3` (`tools/loop/tests/loop-mutations.sh`) empties `rep_record` and the case goes red —
      `expected '40', got 'null'` — then green again: `bash tools/loop/tests/loop-mutations.sh --only 96.3` →
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (2026-11-10T01:08:55Z).

## 4. The report — `tools/loop/report.sh`

- [x] 4.1 The `reads:` line prints what the rule withheld and what entered as a one-line pointer, over the items whose
      record carried a record line, and keeps the raw volume as the denominator (`:417`–`:448`). Verify: the case reads
      `1 of 2 file reads were a second read of a path the session already had (50 %) · 40 of the 40 chars those repeats
      returned were withheld by the repeat rule, 97 entered as a one-line pointer · worst #61 1 of 2`, and asserts the
      line carries no `re-injected`.
- [x] 4.2 An item with no trim record prints the raw volume with its own dash:
      `report#anItemRecordsItsRepeatReads` now reads `3 of 7 file reads … (43 %) · 90 chars those repeats returned — no
      trim record was read for them, so what the repeat rule withheld is unrecorded · worst #21 2 of 5` → `PASS`.
- [x] 4.3 MUTATION `96.2` re-anchored on `were withheld by the repeat rule` → `were re-injected`, and the case goes red
      on the wording (`bash tools/loop/tests/loop-mutations.sh --only 96.2` → `red-when-mutated: 1 · not-noticed: 0`).
      The records whose files this change touches still apply: `--check --files 'tools/loop/report.sh
      tools/loop/state.sh'` → `all 53 cases passed`.

## 5. The rule as stated

- [x] 5.1 `guidelines/Backlog.recipes.md` section 7a (`:172`–`:190`) states the two figures, the record they are read from,
      the warning they remain and the dash an unrecorded item earns, and names both cases and mutation records `96.1`,
      `96.2`, `96.3`; section 7e (`:1226`–`:1228`) names the figure in the trend. Verify:
      `tools/look.sh 'repWithheld' --files` → `9 matches in 5 files`, `Backlog.recipes.md` alone among the guidelines.
- [x] 5.2 `openspec/specs/agent-issue-loop/spec.md:3951` states the pair, its source and the prohibition on calling the
      raw volume a re-injection, with the new scenario at `:3976` and its case.
- [x] 5.3 The change's own hygiene: `bash tools/check-openspec-hygiene.sh` and `bash tools/check-doc-routes.sh` → see
      the gate child; this change adds a change directory under `openspec/changes/` and edits two documents whose
      routes `AGENTS.md` already owns, so it adds no route.
