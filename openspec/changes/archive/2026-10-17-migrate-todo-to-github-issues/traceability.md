# Traceability — `backlog-source-of-truth`

One row per scenario of the change's spec delta: the case that exercises it, and the task that owns that
case. The row count equals `grep -c '^#### Scenario:' specs/backlog-source-of-truth/spec.md` — 15, as this
table has.

| # | scenario | the case that exercises it | task |
|---|---|---|---|
| 1 | The tracker is the single live backlog → A workflow instruction names the tracker | `refcheck#instructionLineFails` (the check goes red on an instruction, green over the rewired tree) — plus the per-file read of `.pi/skills/**`, `AGENTS.md` and `openspec/config.yaml` in the change report | 1.1, 4.1–4.4, 5.1–5.5, 4.6 |
| 2 | The tracker is the single live backlog → The old record is removed last | not a case but the recorded order: the import (3.2) → the rewiring (4.x, 5.x) → `tools/check-todo-references.sh` green **with the file present** (4.6) → the deletion (6.1) → the same check green with the file absent (6.2). Both runs are in the change report | 3.2, 4.6, 6.1, 6.2 |
| 3 | The tracker is the single live backlog → History is not rewritten | `refcheck#allowlistedPathsAreNotScanned` (an instruction inside `openspec/changes/archive/**` is not scanned) + `git status --short` showing no archived artifact modified | 1.3, 4.5, 6.3 |
| 4 | Every historical reference resolves → A citation resolves | `list#idResolvesTheMarker`, `refcheck#citationLinePasses`, `refcheck#apiResolvesThroughTheTracker` (through the tracker, not only the recorded table) | 2.1, 1.2, 1.3 |
| 5 | Every historical reference resolves → A live mention without an identifier fails the check | `refcheck#instructionLineFails` (naming the file and the line) | 1.1, 1.3 |
| 6 | Every historical reference resolves → An unresolvable citation fails the check | `refcheck#unresolvedCitationFails`, `refcheck#missingTableRowFails`, `refcheck#apiLeavesAnUnknownNumberRed` | 1.2, 1.3 |
| 7 | Every historical reference resolves → The check's exclusions are visible | `refcheck#allowlistIsPrinted` (the glob and its reason are printed on a passing run) | 1.2, 1.3 |
| 8 | The migrated backlog's shape is recorded → Every item became exactly one issue | the live run's counts (137 issues, the 137 `§N` titles covering every section) recorded in `import-evidence.md`, plus `import#dryRunEmitsOnePreviewPerFinding` and `adapter#emitsOneFindingPerFixtureSection` for the mechanism | 3.1, 3.2, 2.3 |
| 9 | The migrated backlog's shape is recorded → Imported items are not pre-authorised | the run's `agent/eligible: 0` assertion over all 137, and `import#dryRunEmitsOnePreviewPerFinding`'s assertion that no preview carries the reserved label | 3.3 |
| 10 | The migrated backlog's shape is recorded → Imported items keep their recorded state | the run's assertions (137 open, 0 closed, exactly one `class/*`, `category/*`, `status/*`, `source/todo-import` on all 137) against the source's own histogram, recorded in `import-evidence.md` | 3.3, 3.6 |
| 11 | The migrated backlog's shape is recorded → Re-running the migration changes nothing | `import#secondRunIsANoOp` + the live re-run: `exit 4`, `0 preview(s), 137 already present, 0 created` | 3.4 |
| 12 | The backlog is selectable without reading it → A working set is selected by query | `list#filtersByEachCriterion` (each label filter, no body read) and `list#idResolvesTheMarker` | 2.1, 2.2 |
| 13 | The backlog is selectable without reading it → The former index is not maintained by hand | `git ls-files` shows no index file and no regenerator; `tools/check-todo-references.sh` stays green with the file deleted, so nothing depends on a list | 6.3, 8.5 |
| 14 | The tooling keeps working with no automated actor present → A person runs a tracker command directly | `lib#discoversTheCurrentProject` (in a subdirectory and in a linked worktree), `list#idResolvesTheMarker`, `refcheck#apiResolvesThroughTheTracker` — all run by the host with `gh` and nothing else | 2.1, 1.3 |
| 15 | The tooling keeps working with no automated actor present → No command requires an automated actor | `bash tools/issue-tooling-selftest.sh` (offline, no token, 33 cases) and the new CI step running `tools/check-todo-references.sh` with no token; `surface#noDeletePath` for the closure-only contract | 1.4, 8.1 |

Every case above is named exactly as the runner prints it: `tools/check-todo-references-selftest.sh` prints
`PASS  <case> (exit N)`, and `tools/issue-tooling-selftest.sh --list` prints the tooling's own cases.
