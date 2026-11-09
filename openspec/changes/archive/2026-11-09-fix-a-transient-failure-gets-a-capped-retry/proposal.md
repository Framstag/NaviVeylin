# Proposal — a transient failure gets a capped retry, not a stop

## Why

**Root cause.** The arm that reads a child whose answer carried no verdict line and whose tree is still at the run's
base commit (`tools/loop/run.sh:2598`, base `f11595fa`) records the shape once as the run's own stop and ends the
run where it stands. That stop is right about the item — nothing was read, so no question is owed its owner — but it
is read as a statement about the **window**: one transient fault ends the run and every item after it, and the run
has no way to spend a retry because no retry is recorded. Every other stop the run has is the same: `state.sh next`'s
conditions are verdicts about the budget, but the pre-screen's exit (`run.sh:1001`), a start the worktree refuses
(`:2163`), a label that cannot be written (`:2178`) and a state that cannot be named (`:2190`) all end the run on
their first occurrence too.

**Evidence, verified against the tree (2026-11-08, issue #752).** The run the item cites exists and holds exactly
the shape it describes — `.pi/issue-loop/runs/2026-11-01T23:30:01Z.json`:

```
{ "minutes": 360, "start": "2026-11-01T23:30:01Z", "stop": "two consecutive items ended blocked",
  "items": [ { "issue": 740, "result": "blocked" }, { "issue": 739, "result": "blocked" } ] }
```

The window was 360 minutes, the run worked two items and stopped with the answer `two consecutive items ended
blocked`. The item's own count is broader: over the archived runs, 9 ended on two consecutive blocked items (3 of
them on items that failed in 3–19 s), 9 on `worktree is not clean` (4 within 4 minutes of starting), and 3 on `gh
could not read #N`. The run prices an item (#663), a turn (#686) and a token (#662) and prices no retry, so a retry
is not something it can spend.

**The red test.** `run#aChildThatNeverStartedIsRetriedBeforeTheRunStops` and `run#theRetryCapIsSpentAndTheRunStops`,
added to `tools/loop-cases.sh` by this change. On HEAD (base `f11595fa`) both fail — XML `tests=2 failures=2`,
2026-11-08T20:11:45Z–20:11:57Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`):

```
run#aChildThatNeverStartedIsRetriedBeforeTheRunStops|   the retry names the delay it waited: 'waiting 0 s' not found …
run#theRetryCapIsSpentAndTheRunStops|   the run spent the two retries and started no fourth child: expected '3', got '1'
```

The existing `run#childThatNeverStartedIsTheRunsFault` and `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`
are left as they were and pin the *stop*; they now name `LOOP_RETRY_MAX=0`, which is how a project records that it
spends no retry, so the stop they assert is the cap already spent.

**The single fix.** Record the retry beside the clocks in `tools/loop/loop.conf` — `LOOP_RETRY_MAX=3` and
`LOOP_RETRY_DELAYS="30 120 300"` — and spend it in `tools/loop/run.sh` on the one transient class the run recognises
from the item's own evidence: a child that wrote no verdict line and left the tree at the base. The arm re-runs the
item from the queue after the recorded delay, naming each attempt, the delay it waited and the keys it read; when the
cap is spent it stops exactly as it did before, its reason naming the retries. `state.sh`'s own stops stay final.

**The requirement the defect contradicts.** `openspec/specs/agent-issue-loop/spec.md`: "A child that never started is
the run's fault, not the item's" (`:71`) and "A child whose answer arrived empty is the run's fault too" (`:88`). Each
states the stop the shape earns and neither states a retry before it, which is why the delta ADDS one requirement
beside them rather than rewriting either.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md` §7d "Refusals, records and the landing",
routed by `AGENTS.md`'s row for the loop's procedure (`Backlog.md` §7 is the index; the bodies are the recipes).
§7d:783 states what the never-started shape is and what it earns, and it gains the retry sentence.

## Surface

surface: 2 cases added, 2 stop cases realigned (both name `LOOP_RETRY_MAX=0`, the project that records no retry),
2 mutation records added, the engine and its config (`tools/loop/run.sh`, `tools/loop/loop.conf`), the case and
mutation files, and 2 records (guideline §7d, spec delta) — `tools/look.sh 'could not start|LOOP_RETRY|never-started'
--path tools` →
`LOOK: 33 matches in 5 files` (`tools/loop/run.sh:1927`, `:1940-1942`, `:2593`, `:2598`, `tools/loop/loop.conf:96-97`,
`tools/loop/tests/loop-cases.sh` and `tools/loop/tests/loop-mutations.sh`, plus the existing stop cases of
`tools/loop/tests/loop-cases.sh:7312`, `:7452`), and `tools/look.sh 'could not start|never started|LOOP_RETRY' --path guidelines` →
`LOOK: 8 matches in 3 files`, of which §7d is the owner. `tools/look.sh … --path tools/loop/prompts` → `0 matches`:
the rule is engine, not a prompt.

## Impact

Additive; the change touches no compiled file, so no build and no Gradle task is owed.

- `tools/loop/loop.conf` — the two keys and their rationale.
- `tools/loop/run.sh` — the retry budget read beside the clocks, and the arm that spends it on the never-started class.
- `tools/loop/tests/loop-cases.sh` — two cases added and registered; the two stop cases name `LOOP_RETRY_MAX=0` so they keep
  asserting the stop the cap leaves.
- `tools/loop/tests/loop-mutations.sh` — records `264.1` and `264.2`.
- `guidelines/Backlog.recipes.md` §7d — the rule and its measurement.
- `openspec/specs/agent-issue-loop/spec.md` via the delta in `specs/agent-issue-loop/spec.md`.

**Rollback.** Reverting the four files' hunks restores the base byte for byte and returns the two cases to red, which
is the signal the retry is gone. `tools/loop/tests/loop-mutations.sh --check` then reports `264.1` and `264.2` as
`MUTATION-DID-NOT-APPLY`, which names them. Nothing else reads the new keys; nothing compiled is touched.

## Open decisions

none — the item's `ITEM_DECISION` block records `none`, and the direction is the item's own stated one: the retry is
a recorded value beside the clocks, spent on the transient classes once per class per run, and the run's own budget
stops stay final.
