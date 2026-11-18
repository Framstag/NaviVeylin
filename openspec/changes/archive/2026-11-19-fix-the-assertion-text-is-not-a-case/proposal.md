# The throwable a step printed is not a case it named

## Why

`finish_step` in `tools/loop/gate.sh` reads a failing step's failures from the `<class> > <case> FAILED` lines the
step itself named (base `7aea9b5d`, `gate.sh:786`–`796`). It carried a **second arm** beside that reading:

```
[ "$f" -gt 0 ] || f=$(printf '%s\n' "$out" | grep -c '^[[:space:]]*\(java\.lang\.\)\?AssertionError\(:\|[[:space:]]\)')
```

Any `AssertionError` text in the step's stream was counted as a failing case. So a step that named no case at all —
a test task that printed its own `> Task :… FAILED` header and the throwable under it, and no per-test line — was
tallied `failures=1 errors=0`: a case the step never reported, in the record the run's own check reads for a red
claim, and the `errors` count that a step with no case is supposed to carry is lost. That is the `#268` rule
inverted, which is the opposite of what the arm was written for.

**Root cause, in one sentence:** the fallback arm treated the throwable a case failed on as a case in its own right,
so a step that named nothing was tallied one failure instead of the one error the shipped requirement gives it.

**The red test:** `gate#aLoneAssertionErrorIsNotACase` fails on HEAD — XML `tests=1 failures=1 errors=0`
(`2026-11-18T22:58:00Z`). Run with the engine at the base (`git archive 7aea9b5d7999f4a8c1689d39aa66247386dc3728
tools/loop tools/issues` into the item's `scratch/base-engine-916`, handed to the suite as `LOOP_ENGINE`), the case's
gate printed `LOOP-GATE: tests=0 failures=1 errors=0` where it asserts `tests=0 failures=0 errors=1`
(`evidence/red-on-HEAD-lone-assertion.log`, `evidence/red-on-HEAD-lone-assertion.xml`).

**The owning requirement:** `agent-issue-loop` — "A suite that reported no summary invents no number"
(`openspec/specs/agent-issue-loop/spec.md:2064`): a suite that fails without a summary "SHALL have the failing cases
it named itself counted as its failures … and SHALL be recorded as an error instead where it named none".
**The owning guideline section:** section 7c of `guidelines/Backlog.recipes.md` ("The gate and its suite"), the
paragraph that follows #268's (`Backlog.recipes.md:957`–965 at the base).

This is the second round of item #916. The first (`2026-11-14-fix-a-failing-test-is-a-failure`, archived) landed the
per-test reading and left this arm standing as its fallback; the item's recorded decision is that the elimination is
settled and this change lands it.

surface: 4 files (`git diff --numstat` → `6 3 guidelines/Backlog.recipes.md`, `5 4 tools/loop/gate.sh`,
`44 0 tools/loop/tests/loop-cases.sh`, `5 0 tools/loop/tests/loop-mutations.sh` — **60 insertions, 7 deletions**), 1
case added — `bash tools/loop/tests/loop-selftest.sh --list | wc -l` → **489** declared cases (488 at the base), and
1 mutation record added — `bash tools/loop/tests/loop-mutations.sh --list | wc -l` → **424** records (423 at the
base); 1 spec delta with 1 requirement and 4 scenarios (1 scenario added, the requirement body re-cut).

## What Changes

1. **The fallback arm is deleted.** `finish_step` reads a non-zero step's failures from the per-test `FAILED` lines
   only; a step that named none keeps `#268`'s error — `tests` stays the count the suite reported (none), `failures`
   stays 0, `errors` is 1. A step the bound ended keeps the error as before.
2. **A case holds the shape** (`gate#aLoneAssertionErrorIsNotACase`): a task header, the `AssertionError` under it, no
   per-test line — the tally and the gate's line must read `tests=0 failures=0 errors=1` and the step must still be
   named as the failing one.
3. **A mutation record keeps it** (`916.2`): the eliminated reading is put back in place of the per-test one, and the
   case reads `failures=1 errors=0` again.
4. **The guideline states the deletion** (section 7c, the paragraph after #268's) and the capability delta restates
the
   requirement with the throwable named as not-a-case, beside the scenario the case holds.

What is deliberately **not** touched: the per-test reading itself (`916.1` and its case unchanged), `n`'s derivation
from the two summary shapes, the bound's error, the gate's line and tally shapes, the compiled step, and the archived
first-round change.
