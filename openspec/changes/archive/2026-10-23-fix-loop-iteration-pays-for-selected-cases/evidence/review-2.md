# Review round 2 — independent, read-only

Reviewer: a fresh child session, 2026-10-23, read-only over the branch at commit `5e76181f`, base `abeffcad`. It
was asked to confirm that round 1's findings were closed, then to re-run the four scenarios end to end.

## Scenarios — 4 of 4 PASS

| # | Case | Registration |
|---|---|---|
| 1 | `driver#aGroupFilterRunsOnlyItsOwnCases` | `LOOP_SHELL_CASES` + dispatch |
| 2 | `driver#aFilterThatNamesNoCaseRefuses` | `LOOP_SHELL_CASES` + dispatch |
| 3 | `driver#theListIsFilteredTheSameWayASuiteRunIs` | `LOOP_SHELL_CASES` + dispatch |
| 4 | `gate#theSummaryNamesWhatEachSuiteCost` | `LOOP_SHELL_CASES` + dispatch |

## Round 1 closures

| finding | verdict |
|---|---|
| P1 — the summary naming the selection | **PASS** — asserted, and mutation `49.4` now targets the assigning line rather than the `selection=""` initialiser a later assignment overwrote |
| P2 — "both at once" | **PASS** — the intersection is asserted in run mode; the list case asserts `--list --group` only (round 1's wording said otherwise and was corrected) |
| P2 — the usage sweep's blindness to `--case\|--group)` | **FAIL** — the arms were still two lines long, so the sweep (which needs `--flag)` and its bound check on one line) saw neither, and round 1's claim about it was false |
| P2 — the suite-line duration asserted by shape only | **PASS** |
| P2 — quoted numbers against `evidence/gate.log` | **PASS** |

## Findings, and what closed them

| id | finding | closed by |
|---|---|---|
| P2 | neither arm visible to `usage#noUnguardedValueFlagRemains`; a bare `--group` had no test at all; `tasks.md` claimed the sweep proved it | both arms folded to one line each (the sweep's own contract, "one line per value flag"), so the arms are selected at the sweep's stage 3 (`tools/loop-selftest.sh:25`, `:26`) and cleared at its stages 4/5 as guarded — round 3 proved those stages non-vacuous by replaying them over a guard-less arm of the same shape, which was flagged; `bash tools/loop-selftest.sh --group` added to `usage#bareValueFlagInASingleShotHarnessIsAUsageError` and `--group --list` to `usage#bareValueFlagDoesNotSwallowTheNextFlag`; the two `tasks.md` sentences corrected |
| P2 | `line_seconds` not declared local in `case_gate_theSummaryNamesWhatEachSuiteCost` | added to the function's `local` list |

## Verdict

**OK with notes — no behavioural gap in the four scenarios.** Both notes were P2 and are closed in the tree that
round 3 reads (`evidence/review-3.md`).
