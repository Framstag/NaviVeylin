# A role's tally records the model that answered it

## Why

Issue **#823**. The spend the run records per item is attributed to the role that spent it and to nothing else:
`child_usage_json` folds each session's tokens under the role its prompt path names (`tools/loop/state.sh:927`–`:933`),
`done` stores that as `metrics.roles`, and `report.sh`'s `roles:` line prints it (`tools/loop/report.sh:457`–`:489`).
No field names the model that answered, so a run that pays a different model per role — the recorded
`LOOP_AGENT_CMD_REVIEWER` in `tools/loop/loop.conf:483` — leaves a reader holding the price of a decision without its
subject: after the fact nobody can say which model a role's tokens were charged to.

**Verified in the tree at this change's base `de1cb614`:**

- The per-role tally is built from each session's turns and never reads a model:
  `tools/loop/state.sh:927`–`:933` reduces `$ses[]` to `{role, tok}` and nothing else.
- The fact is already on the wire and unread. The item's `turn_end` record carries the assistant message, and that
  message carries `model` (with `responseModel` when the provider reports a concrete one) beside the usage and
  `durationMs` the accounting already reads — measured on `.pi/issue-loop/items/808/events.jsonl:55`
  (`"type":"turn_end","message":{"role":"assistant",…,"api":"openai-completions","provider":"ollama-cloud","model":"mimo-v2.6-flash"…}`).
  The `turn_end` handler at `tools/loop/state.sh:903`–`:915` copies the usage, the duration and the calls out of that
  same `$m` and not the model.
- Nothing records a model today: before this change's reader exists, `tools/look.sh 'roleModels' --files` finds no
  match at all on the base (`evidence/measure-surface.txt` for the change's own count: 5 in `tools/loop/state.sh`, 2
  in `tools/loop/report.sh`, 7 in `tools/loop/tests/loop-cases.sh`, 1 in `guidelines/Backlog.recipes.md`), and
  `tools/look.sh 'metrics\.roles' --files` → 13 matches in 6 files on the base (3 in `tools/loop/report.sh`, 6 in
  `tools/loop/tests/loop-cases.sh`) are all numbers.

**Root cause, in one sentence:** the per-role tally is folded from each session's turn usage and never reads the
`model` the same `turn_end` message already carries, so the state and the report name the role that spent an item's
tokens and never the model that answered.

**The red case** — `report#aRolesTokensRecordTheModelThatAnswered`
(`tools/loop/tests/loop-cases.sh`), read against the base's shape (whose `child_usage_json` records no model at all),
one `bash tools/loop/tests/loop-selftest.sh --case 'report#aRolesTokensRecordTheModelThatAnswered'` run at
2026-11-09T17:46Z:

```
report#aRolesTokensRecordTheModelThatAnswered|   the reviewer's tokens are recorded under the model that answered them: expected '1000000', got 'null'
--- report#aRolesTokensRecordTheModelThatAnswered[0] (exit 1)
FAIL report#aRolesTokensRecordTheModelThatAnswered
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run, the switch case's and the mixed-role case's runs beside it in the same file
(the shell runner writes no JUnit XML of its own). The case's first-line `# files:` declaration is accepted before
that run: `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The report says which phase and which role spent
the run's clock" (`:3275`), whose body already splits the tokens "by the role that spent them (worker, reviewer,
tail)"; the delta adds the model the answering turn names.

**Owning guideline section:** `guidelines/Backlog.recipes.md` §7b "What an item costs, and the bounds on it", the
paragraph at `:533` that names `metrics.roles` and reads the role from the session's prompt path.

surface: 4 cases added, 0 rewritten, 0 fixtures realigned, 1 engine reader and 1 report line in two files
(`state.sh`: `turn_end`'s model and the `roleModels` pass; `report.sh`: the `roles:` line), 3 mutation records, 1
requirement modified (four scenarios added), 1 guideline paragraph — counted before the delta with
`bash tools/loop/tests/loop-selftest.sh --list | wc -l` (**386** on the base, 390 with this change's cases) and
`tools/look.sh 'metrics\.roles' --files` (6 matches in `tools/loop/tests/loop-cases.sh` on the base, 11 with this
change's cases). The record is `evidence/measure-surface.txt`.

## What Changes

1. **`child_usage_json` records the answering model.** The `turn_end` handler keeps `model` beside the usage, duration
   and calls it already reads from the same `$m` — `responseModel` when the provider reported one, else `model` — and a
   new pass folds each role's turns into `roleModels: {worker, reviewer, tail} | null`, a map of model to the tokens
   that model's turns spent. A role no session of the item carried, or one whose turns named no model, stays null, the
   rule the function's header already states for every figure it returns.
2. **The report reads `role (model) tokens`.** `report.sh`'s `roles:` line prints one row per `(role, model)` tally
   when any item recorded a model, in the role order the line already uses, with the role totals as its total. The
   tokens of a role no model explained keep a row of the role alone, so the rows always add up to that total, and a
   state that records no model prints the line it always printed. The `--json` output carries the field through
   `items` unchanged, so no separate JSON path is added.
3. **`tools/loop/tests/loop-cases.sh` gains a case per shape** — a role on its own model recorded in the state and
   printed, a session that switched model recorded as two tallies, a state that records no model keeping its role
   totals, and a role whose turns named no model keeping a row of its own.

## What this does not do

- It claims nothing from the configuration: the recorded model is the one the provider answered with, read from the
  turn's own message, never from `LOOP_AGENT_CMD_*`. A role left on the default needs no label, and a role whose key
  names a model needs no second source of truth (the item's own "Claim nothing from the configuration").
- It does not change `metrics.roles` itself: the per-role totals, their names and every figure the report already
  derives from them stay byte-identical, so a state written before the field existed still prints its role totals and
  names no model.- It does not touch `tools/loop-spend.py`'s per-session split (issue #822) or the run's header `agent-roles=` line;
  both read the command that was configured, and the question the item asks is about an archived item's own record.
