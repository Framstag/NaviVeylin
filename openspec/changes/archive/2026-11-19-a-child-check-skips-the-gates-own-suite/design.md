# Design: a-child-check-skips-the-gates-own-suite

## Context

`child_check_calls` (`tools/loop/lib.sh`) reads a child's stream and counts the calls that run a programme
`gate_check_programs` lists, minus the calls `is_gate` answers for. `run.sh`'s watch loop (`run_child`) adds that
count to `checks_seen` once a second and ends the child at `LOOP_CHILD_CHECKS_MAX`. The distinction the count is
built on — a call that runs *a check* versus a call that runs *the gate* — is the same distinction the report's
`gateRepeat` column is read from, so one rule serves both readings.

The measured population says the rule is drawn one programme too narrowly. A child that runs
`tools/loop/tests/loop-selftest.sh --from-git <base>` has run the gate's whole suite: it spends the gate's 15–19
minutes, produces the gate's tally, and the item-level gate runs it again immediately after. Counting it as one of
40 checks prices the gate's most expensive work at the price of a focused case.

## Decision 1 — the exclusion is `is_gate`'s, extended to the suite's full-run form

The change does not add a second classification. It extends the one `is_gate` already owns: a call whose command
runs a programme on the check list **without** narrowing it — no `--case`, no `--tests`, no `--from-git` with a
case name — is the gate's own work. A narrowed call stays a check, because that is what §2a hands a child and what
the bound exists to price.

The alternative — a separate `is_full_suite` predicate, read only by `child_check_calls` — was rejected: the report
classifies the same calls (`metrics.gateRepeat`) and a second predicate would let the two readings disagree about
one call, which is the defect class issue #882 was filed for.

## Decision 2 — the figure the bound is set from is restated, not moved

`LOOP_CHILD_CHECKS_MAX=40` stays. The comment beside it justifies 40 by a p90 of 38 over 17 children and 265
checks; that population included whole-suite runs, so after this change the same 40 buys more focused cases than it
did. The comment says so and quotes the run that motivated it (253 checks, 1.9 h, 4 of 11 items ended at the bound,
0 of 18 in the archive). No new key.

## Decision 3 — what is out of scope

- **The bound's value.** Raising 40 to 80 would have bought the same four items and made every child of a *new*
  kind of work wait twice as long before the run intervenes. The defect is what a check is, not how many are allowed.
- **The gate's own repeat.** `LOOP_GATE_RUNS_MAX` bounds the gate's runs; this change does not touch it, and a child
  that runs the suite twice now spends no check on either.
- **The item's wall clock.** #894 was ended by the run's deadline, not by the check bound, and the change does not
  claim it.

## The falsification

The new case is red on the tree before the change: with `is_gate` unextended, a stream whose only check call is
`tools/loop/tests/loop-selftest.sh --from-git <base>` answers `1` where the case expects `0`, and the second case's
focused call answers `0` under a rule that excluded everything. Both are asserted on the helper, not through a run,
so the mutation is one function and the case names it.

## Open decisions

none

The exclusion's rule, its shared home and the bound's value are settled above; nothing here is left for a person
to choose.

## Risk

A child that runs the full suite five times now spends no check and can spend the item's whole wall clock doing it.
The item's own clock (`LOOP_ITEM_WALL_MAX`) and the run's deadline both still end it, and the report's `gate 1.9 h`
figure would name the spend on the next run — which is why the change keeps that figure in the proposal rather than
in a comment only.
