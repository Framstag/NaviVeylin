# Design: fix-the-gate-evidence-bound-is-four-thousand

## Why 4 000

The class exists for a diagnosis, not for a budget: `guidelines/BuildEvidence.md` §4d reads a gate log and the suite
XMLs for the failing case's `file:line`, the JUnit `timestamp` order a failing set is bisected in, the failed step and
the flake line. Two widths are defensible from that reading, and only one of them is:

- **2 000, the recorded bound.** Rejected: the JUnit line and the stack frame a red run is attributed with can sit
  past a 2 000-character head, which is the measurement that kept the recorded bound from descending to 1 000
  (issue #1004). Widening only the *tail* made the class the same width as the ordinary bound, which would have made
  the exemption a shape and not a width.
- **4 000.** Chosen: it is the midpoint — twice the bound beside it, half the 8 000 the class shipped with — and the
  issue's own estimate prices the 338 recorded results at 338 × 6 000 characters saved besides the ordinary bound.

The issue says the honest answer if the first red run loses its attribution is the head-and-tail cut, not a return to
8 000, so the width and the shape are one decision and both land here.

## Head and tail, not head alone

The head-only cut keeps the verdict line a gate log opens with and drops the failed step it closes on — and §4d reads
both ends: the verdict for the tally, the tail for the step that failed. So the exempt class's cut is its first half
plus its last half, and `trimNotice` names both widths. The ordinary class keeps the head-only cut, because nothing
in its diagnosis reads a tail.

`boundedHead` and the new `boundedTail` each refuse to halve a surrogate pair at their seam, so a cut never hands over
a broken character.

## Where the width lives

The constant is the only home of the width: `trimResult` raises the recorded bound to `Math.max(bound,
GATE_EVIDENCE_BOUND)` for the class, and `isGateEvidence` is the only predicate that selects it. The case does not
carry the number as a literal where it can read it from the constant's effect — its assertions compare the class's
whole vs. cut sizes — so a later width moves the arms with it. `tools/loop/loop.conf` records only the *ordinary*
bound; the comment there names the class's width without owning it.

## Rollback

One constant. A tree that records a wider `LOOP_TOOL_RESULT_MAX_CHARS` than 4 000 still raises the class to it, so
the exemption never tightens a project's own wider bound — only the default moves.

## Open decisions

none
