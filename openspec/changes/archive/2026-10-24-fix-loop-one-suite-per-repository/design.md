# Design

## The fix, and the bound it holds

A lease on the scratch root the tree resolves, which is the unit two suites of one tree share. The driver takes it
after the traps are installed — so a signal releases it the way a failure does — and before the first case runs; a
suite that is refused leaves nothing behind because `on_exit` removes the scratch tree it made a moment earlier
(`tools/loop-selftest.sh:84`) and releases the lock last, after that tree is gone. The lock holds the pid that took
it and the moment it did, and the refusal repeats that record rather than reformatting it, which is what makes the
message name the suite instead of naming a lock. An owner that is not running any more is taken over and named: the
lease `state.sh lock` already is (`tools/loop/state.sh:575`), read for a suite instead of for a run.

The bound is stated here because a lock can be read as more than it is. What is refused is a second **suite in one
tree**, and the refusal is the artifact a reader needs to attribute a red run to the neighbour rather than to the
change under test. Two things are *not* refused, and each is named rather than left to the reader: (a) a suite in
another worktree of the same repository — a lease stands in the scratch root the tree resolves, so two worktrees
hold two locks, and the item's own measured pair (a change tree and a detached control worktree) is exactly that
pair. What such a neighbour costs is host load, which is #259/#260's ground, and the lease must not pretend to own
it. (b) A single named case still runs, because that is the shape the mutation harness uses
(`tools/loop-mutations.sh:604`) and the shape the one case that starts a suite of *this* tree on purpose uses
(`tools/loop-cases.sh:6241`), beside the listing `--case-files` (`tools/loop-cases.sh:6707`); the nested copies at
`tools/loop-cases.sh:6392` and its like resolve a scratch root of their own and would not be refused either way. A
*loop run* in the same tree is not refused at all, because a run's own gate must be able to run a suite while that
run holds the repository: a rule about the run's lock is impossible, and the load a run puts on the host stays the
host's business.

## Alternatives, and why the evidence eliminates them

**A per-invocation ref namespace (the item's other option).** The item suspected that two suites share
`loop-<timestamp>` branches (`tools/loop/run.sh:499`) and git's worktree and index locks in the repository they run
in. They do not share them: every case builds a throwaway repository of its own (`tools/loop-cases.sh:549`, from the
scratch tree of that invocation, `tools/loop-cases.sh:542`), the run those cases start creates its own worktree
*inside that throwaway repository*, and the branch is made there. Measured in this change's own full run: the
repository the suite ran in carried six `loop-<timestamp>` refs before the run and the same six after it, all six
from loop runs of 2026-10-21 and 2026-10-23, and the run added no worktree to it (`evidence/refs-of-the-suite.log`).
A namespace over refs that are never shared would have changed nothing.

**Refusing every second invocation, without the one-named-case exemption.** Eliminated by what a refusal would cost
the harnesses that run beside a suite. `tools/loop-mutations.sh` runs one case per record and reads the driver's
status as its answer: `run_case` hands back whatever the driver exited with (`tools/loop-mutations.sh:598`), and its
caller prints `GREEN … the case did not notice the mutation` for a zero status and `RED` for any other
(`tools/loop-mutations.sh:656`), so the refusal's exit 2 would be reported as a mutation that was noticed — a false
claim about a mutation, the one answer that harness must not give. The one case that starts a suite of this tree on
purpose would be refused inside its own case, and it is the case that proves the exemption does what the requirement
says (`tools/loop-cases.sh:6241`).

**Making the cases independent of the host's load.** That is a different item's work (#259, #260 — the waits
between polls and the suite's serial execution of cases that own a repository each), and it leaves the repository
unowned: two suites would still be started in one tree, on one host, with no statement anywhere that a red run
belongs to both. This change is what makes the *fact* of the neighbour visible; the cost of a neighbour is the
other items'.

**Reading a snapshot of the engine instead of the tree the suite runs in.** A snapshot would let the cases read a
stable engine while a peer writes this tree, which is the second interference the item's evidence shows (a live
loop run fast-forwards the base in the project tree). It also stops the suite from testing the tree under test:
the declaration check `driver#everyCaseDeclaresTheFilesItCanReach` and the gate's own `--from-git` selection are
both built on the suite reading the tree it was started in, and the gate would then measure a copy rather than the
delta.
