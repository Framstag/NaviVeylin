# Design — the log arm reads a suite, and the state names what it accepted

## Root cause

`verification_record_green`'s `.log` arm (`tools/loop/worktree.sh:437`) tests `^PASS +<group>#<case>` for a *match*
and `^FAIL ` for absence. A focused run's log — the record a blocked attempt leaves beside its red gate — satisfies
both, so `carried_verification` finds a green record on a branch whose own gate read `failures=1`, `branch_state_of`
answers `adoptable`, and `adopt-item` merges it and closes the item on it. The second half is the same reader's print:
it emits `$files`, the directory's whole `ls-tree` listing, so the closure's `evidence checked:` names the red proofs
as verification.

## The single fix

1. **The arm asks for a suite.** The `.log` arm counts the distinct `^PASS <group>#<case>` lines and returns 1 (not
   green) unless there are at least two, keeping the `^FAIL ` refusal as it stands. A suite's own console log carries
   one line per case, so the shape the #997 widening was made for — a `tools/**` change's gate is the shell suites —
   is untouched; the shape it accidentally admitted, one case's own log, is refused.
2. **The state names what it accepted.** `carried_verification` accumulates the names it accepted and prints those,
   comma-separated, instead of the directory's file list. Empty when nothing was accepted, which is what the
   `archived-not-green` column already means.

Both halves sit in the function the two readers share (`carry_gate_record` over the working tree, the branch reader
over git objects): fixing the predicate once keeps the readers agreeing, which is the reason the function exists.

## Why not the alternatives

- **Scope the arm to the suites the change touches**, as the item's wording suggests first. The record carries no
  mapping from a log to a path, and inventing one — a `SUITE <path>` header line — changes the shape the gate's own
  suites print, which is a bigger delta than the defect and would have to be written by every existing caller still
  in flight. Eliminated on blast radius, not on principle.
- **Keep the one-line acceptance and refuse the branch only when `gate-red.log` is absent.** That is what #1035
  already does, and it does not reach this item: the branch #1006 left behind carried its red proof as
  `gate-red-policy-case.log` — a *focused* log with `FAIL` lines and no `LOOP-GATE:` line — so `carried_gate_red` reads
  nothing and the verification arm decides alone. Eliminated by the measurement.
- **Tighten only `carried_verification` and leave the shared predicate.** The branch reader and `carry_gate_record`
  would then disagree about what a green record is, and a change the run wrote itself would read back as unverified —
  the drift issue #979 put the predicate in one function to prevent. Eliminated by that requirement.

## Blast radius

`carried_verification`'s callers are `cmd_branch_state` (`tools/loop/worktree.sh:686`) — whose third column is read by
`adopt-item`'s adoption line and by the closure's `evidence checked:` field in `tools/loop/leftovers.sh:46` — and
nothing else in `tools/`. `verification_record_green` is called by `carry_gate_record` (`:491`) and by
`carried_verification` (`:573`). Three cases exercise them: `worktree#theGatesTallyTravelsWithTheChangeItMeasured`,
`worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable` and `worktree#aShellSuiteLogIsAVerificationRecord`; the first two
put an XML or a red `LOOP-GATE:` line in front of the arm, and the third is re-scoped here to a suite's worth of
cases. No production path other than these four functions reads an evidence record.

## Rollback

One commit, four hunks in two shell files plus one guideline paragraph. Reverting it restores the one-line acceptance
and the whole-listing print; the new case goes red with the revert (it is the mutation the tasks' falsification step
runs), so the revert is self-checking.

## The guideline edit this delta owes

The "Refusals, records and the landing" section of `guidelines/Backlog.recipes.md` states the adoption's refusals — "no
green verification record" among them — and never said what a green record is. It gets one paragraph: the suite's worth
of `PASS` lines and no `FAIL` line, one case's own log is not the change's gate (issue #1036), and the state names only
the records it accepted.
