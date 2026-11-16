# Design — a step's own failing tests are its failures

## Root cause

`finish_step` reads one summary shape and treats every other shape a failing step wrote as an environment fault.
`f` comes from a single substitution that matches `<name>: M of N cases failed` (`tools/loop/gate.sh:597` on the
base), and the error is computed from `f` alone (`:604`). A JVM test task reports its failing cases as
`<class> > <case> FAILED` and never writes that summary, so its step contributes `failures=0`, `errors=1` — a
number that is not the step's, in the record the run's own check reads for a red claim.

## The single fix

Where a non-zero step printed no summary, its own named failing cases are its failures:

- a line of the shape `<class> > <case> FAILED` is one failing case. The task's header is `> Task :app:… FAILED`,
  which begins with `>`, so the pattern that reads a case excludes it by its first character rather than by a list
  of task names.
- nothing else changes: `tests=` stays the count the suite reported (none, here — a tally SHALL NOT state a case the
  suite did not report, which is #268's rule), `errors=` stays the arm it was, and the per-suite tally, the gate's
  line, the failed-suite list and the exit status are unchanged.

The reading is guarded by the two states that are not a test failure: `rc=0` (nothing to read) and a step the bound
ended (`st_overran`), whose verdict is its overrun and whose stream may hold anything the step printed before it was
killed.

## The alternatives the evidence eliminated

1. **Read Gradle's `N tests completed, M failed` console line.** Eliminated: the case's own stream carries no such
   line (that is the shape the item is about), so a reading that depends on it answers nothing for the failure it
   was filed over.
2. **Count every ` FAILED` line, the `> Task … FAILED` header included.** Eliminated: it makes one task into one
   failure per test it ran, and it turns #268's configuration step — `> Task :app:compileMobileDebugKotlin FAILED`,
   which is a build that never reached a test — into a failure, inverting the rule this change extends. That step is
   `gate#aFailingSuiteWithNoSummaryInventsNoTest`, and it passes unchanged.
3. **Declare the step's output grammar in the profile** (`LOOP_GATE_*`). Eliminated: the profile's gate keys declare
   commands, globs and a directory — a command and a path, never the grammar of a stream — and the summary shape the
   gate already reads is an engine literal. A second literal of the same kind keeps the seam where the first one put
   it; a profile key would be a shape no second project is asked to change.
4. **Count `AssertionError` lines only.** Eliminated: the per-test line carries the case, and the count a reader
   needs is failing cases, not error texts — a case can fail on any throwable, and Gradle prints the case line for
   every one of them.

## Blast radius

- `tools/loop/gate.sh`'s `finish_step` and the docstring above it: 15 lines added, 2 rewritten (`git diff
  --numstat`).
- The tallies and the gate line of any step that fails without a summary and names a failing case — the numbers of
  such a step move from `failures=0 errors=1` to `failures=N errors=0`. A step that names no case, an overrun step
  and every step that writes a summary are unchanged.
- `tools/loop/tests/loop-cases.sh`: one case added, its registry entry and its dispatch entry.
- `tools/loop/tests/loop-mutations.sh`: record `916.1`.
- `guidelines/Backlog.recipes.md` section 7c ("The gate and its suite") and the capability's spec, which own the rule the numbers are read by
  (`AGENTS.md`'s Documentation Map routes "the loop's procedure — … the gate and its suite" through `Backlog.md` section 7
  ("The loop") to `Backlog.recipes.md`).

## Rollback

Revert the one committed block in `finish_step`; the case, the record and the two documents describe a rule that
would then be false, so they revert with it. Nothing else reads the added lines, and the numbers of every step that
writes a summary are the same either way.
