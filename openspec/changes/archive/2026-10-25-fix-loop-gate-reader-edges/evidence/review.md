# Independent review — `fix-loop-gate-reader-edges` (issue #289)

Read-only review by a separate agent of the uncommitted diff, the archived change, both shipped specs and the
item's own measurement artifacts (`.pi/issue-loop/items/289/`). Verdict: **OK with notes** — the map, the
precondition and the falsifications are sound; one P1 and six P2s were raised and all were resolved in a second
round before the commit.

## Per claim

| # | claim | verdict | evidence |
|---|---|---|---|
| 1 | every measured reader edge owes the whole reading step's command set; no pair reaches another step | PASS | `tools/loop/loop.conf`, computed plan for the six paths |
| 2 | the command side did not regress; `gate#aPipelineCheckIsOwedByItsPaths`'s `assert_not_contains` still holds | PASS | `evidence/green-case.log` |
| 3 | the new case fails on the base and passes after; its `# files:` declaration is complete | PASS | `evidence/red-on-HEAD.log`, `evidence/green-case.log` |
| 4 | the `.pi/` precondition refuses a non-checkout and accepts a linked worktree; the four cases pass | PASS (6.4/6.5 had no archived run; re-run this round) | `evidence/full-suite.log`, `evidence/worktree-run.txt`, `evidence/revert-check.log` |
| 5 | `54.5`/`54.6` go red and restore green | PASS | `evidence/revert-check.log` |
| 6 | the shipped requirement bodies are ≤500 chars and carry every scenario plus the new one | PASS | `openspec validate --specs` → 177/177 |
| 7 | the evidence is not self-contradictory | FAIL at the time; **fixed** | see below |
| 8 | no statement overclaims what the code does | FAIL at the time; **fixed** | see below |

## Findings and how they were resolved

1. **P1 — a second ungated reader edge.** `Check issue tooling` runs `tools/issue-tooling-selftest.sh`, whose case
   `lib#onlyTheSeamCallsTheRealCli` greps `tools/loop/*.sh` and `tools/sensors/*.sh` and whose taxonomy case reads
   `tools/sensors/sensors.json`, so a `command gh` added to `tools/loop/worktree.sh` reddens that step while the
   map owed none of it — the same 2026-10-21 shape, contradicting the broader SHALL this diff writes.
   **Resolution (owner's answer, option A):** map the whole step (all six commands) for `tools/loop/*`,
   `tools/sensors/*` and `tools/sensors/sensors.json`; the case gains the engine-glob delta and the two config
   deltas, with records `54.7`–`54.10`.
2. **P2 — the case and mutations exercised 2 of the edges the scenario names.** **Resolution:** the case now
   drives five deltas (`tools/loop/loop.conf`, `tools/tooling.json`, `tools/sensors/sensors.json`,
   `tools/loop/worktree.sh`, `tools/issues/create.sh`) and records `54.5`–`54.10` falsify each.
3. **P2 — a stale comment above the pin case** still called the path side "a residual the map cannot close".
   **Resolution:** rewritten in `tools/loop-cases.sh`.
4. **P2 — `guidelines/Build.md` §3 miscounted and misattributed the measured edges.** **Resolution:** the
   paragraph now names the two steps' edges separately and drops the count.
5. **P2 — evidence gaps** (empty `rc=`, a truncated `--list`, no `openspec validate` line, and a gate run from
   the main checkout rather than the change root). **Resolution:** every evidence file below was regenerated with
   full output and real exit codes; the gate was re-run from a linked working tree at the commit.
6. **P2 — the precondition widening accepted a `.pi/` worktree top that is not this project's checkout.**
   **Resolution:** the `.pi/` branch now also requires the fixture (`tools/fixtures/foreign-project`), the
   refusal case covers a `.pi/` top without it, and record `6.7` falsifies the new clause.
