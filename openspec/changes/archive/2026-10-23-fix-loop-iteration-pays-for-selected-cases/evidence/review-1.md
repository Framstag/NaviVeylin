# Review round 1 — independent, read-only

Reviewer: a fresh child session (`.pi/skills/fix-loop`), 2026-10-23, read-only over
`openspec/changes/fix-loop-iteration-pays-for-selected-cases` at commit `bd572f53`, base `abeffcad`. It was told
not to edit, stash or run the mutation harness, and it verified the registration, the scenario-to-case mapping, the
gate claim against `evidence/gate.log` and five named risks.

## Scenarios

| # | Case | Verdict |
|---|---|---|
| 1 | `driver#aGroupFilterRunsOnlyItsOwnCases` | **FAIL (partial)** — exit status and the executed set were asserted; the scenario's "the selection is named in the summary line" was not, so the summary could be deleted and the case stayed green |
| 2 | `driver#aFilterThatNamesNoCaseRefuses` | PASS |
| 3 | `driver#theListIsFilteredTheSameWayASuiteRunIs` | PASS |
| 4 | `gate#theSummaryNamesWhatEachSuiteCost` | PASS |

Gate claim: 182 + 102 + 34 = **318** parsed cases, durations 811 s / 1 s / 98 s = 910 s — consistent with the
`LOOP-GATE:` line and with `tasks.md`. 182 `PASS`, 0 `FAIL`. The `--base abeffcad` plan named exactly those three
suites. PASS.

## Risks

1. PASS — the single-literal `--case` still prints one `PASS` line and no summary; the mutation harness reads its
   status only.
2. PASS (behaviour) / note — `--group` with no value exits 2 and does not swallow the next flag; the sweep that is
   supposed to see this arm does not (see the note below).
3. PASS — both refusals name the filter that is wrong, and precede any case run.
4. PASS — the `LOOP-GATE:` prefix and the `failing:` list are unchanged; `ran_entries` only appends ` <n>s` inside
   the parentheses; no script parses that list.
5. PASS — the timing case reads its number out of the run instead of hard-coding seconds.

## Findings, and what round 2 closed

| id | finding | closed by |
|---|---|---|
| P1 | scenario 1's "named in the summary line" unasserted | an assertion on `— selection: --group alpha`, plus mutation `49.4` |
| P2 | "both at once" (the intersection) had no case | the intersection asserted in `driver#aGroupFilterRunsOnlyItsOwnCases` (run mode) and in the list case |
| P2 | `tasks.md` quoted `all 99 cases passed` against a measured 102 | corrected to the measured count |
| P2 | the usage sweep (`usage#noUnguardedValueFlagRemains`) requires `--flag)`, so it could not see the `--case\|--group)` arm; `--group` was in no sweep list | **not** closed by the split into two arms, which round 2 disproved: two lines per arm is outside the sweep's contract. Closed in round 2's tree by folding each arm onto one line and by asserting the behaviour in the two usage cases (`evidence/review-2.md`) |
| P2 | the suite-line duration was asserted by shape only, so a wrong number there stayed green | the suite's own line is read out of the run and compared with the summary's number |

## Verdict

**OK with notes — the P1 gap had to close before archive.** It did, in round 2's tree; the round-2 review is
`evidence/review-2.md`. One of round 1's own closure claims — that splitting `--case|--group)` into two arms made
the sweep see both — was wrong, and round 2 caught it; the row above records the correction rather than the claim.
