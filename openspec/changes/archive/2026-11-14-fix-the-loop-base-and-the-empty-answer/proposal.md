## Why

Issue **#882**. One run of 2026-11-14 lost 14.5 Mtok to two readings that each asked the wrong question, and both
questions were answered from evidence the run already held.

The gate measured from a remote it never fetches: `tools/loop/gate.sh` resolved its base as
`git merge-base HEAD origin/main`, and the tree the loop works on only lands. Measured on that run, `origin/main`
stood 7 commits behind the local base branch, so every gate measured `HEAD..<stale sha>` — a diff that carries
every landing the branch already had. The self-test leg the gate runs built its selection from that diff and took
all 427 declared cases, 841 s a gate, 56 % of the item's wall clock.

An empty answer over work was evidence of nothing: the retry budget's arm for a child that wrote no verdict fired
only while HEAD was still the base. Measured on that run, a continuation child exited 0 with a 0-byte report over a
tree that already carried the item's work, no arm read the empty answer, the item was recorded `blocked`, and the
14.5 Mtok of its work were left behind for a person to find.

## What Changes

The base is the first of five sources that is a commit this tree knows **and** an ancestor of HEAD: the `--base`
argument, the base the run recorded, the merge-base with the run's base branch, the merge-base with `origin/main`,
`HEAD~1`. The base branch's current tip beats a recorded base, because landings fast-forward that branch and a
recorded sha cannot move. The gate prints `LOOP-GATE-BASE: <sha> (from <source>)` before its first step, so a reader
of a gate log sees which commit was measured and why.

A final report that exists, is 0 bytes and carries no verdict, `LOOP-TIMEOUT` or `LOOP-DEADLINE` spends one retry
of the same recorded budget (`LOOP_RETRY_MAX`, `LOOP_RETRY_DELAYS` by position) and re-runs the **same** child with
the **same** prompt, wherever HEAD stands. It is spent in place and not by re-picking the item from the queue: the
branch carries what the attempt kept, and a second pick would read that work as an unfinished attempt instead of
asking the question again. The class is named `answer-empty` in the run's printed line and in the item's step; a
second empty answer stops the run with both attempts and their byte counts in the note, and a report that carries a
verdict of its own is never re-run.

The cases are `gate#theBaseIsReadFromTheRunNotFromTheRemote` and
`run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop`; `tools/loop/tests/loop-mutations.sh` 882.1 and 882.2 are the
records that keep each guard, and each was run to watch its own case go red and green again.

What this does not do: it fetches nothing — a landings-only tree has no reason to reach the remote, and the
resolution is asked of commits this tree already knows; and it does not widen the retry: the budget stays one entry
of the same cap, with the class flag beside the counter bounding the empty answer to a single retry.