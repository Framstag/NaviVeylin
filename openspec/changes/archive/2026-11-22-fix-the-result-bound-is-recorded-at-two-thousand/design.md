# Design — the bound is 2 000 because the tail is the volume

## The root cause

`tools/loop/loop.conf:493` on the base records `LOOP_TOOL_RESULT_MAX_CHARS=4000`, and that line is the whole of the
decision: every reader takes the number from the file (the adapter at `tools/pi-child-events.sh:88-90`, the trimmer
at `tools/pi-trim-result.mjs:415` and `:441`, and `tools/look.sh:94-96`, `tools/loop/phase.sh:81`,
`tools/gate-attribute.sh:66`, `tools/loop/state.sh:282`), so the bound is applied correctly and in one place. What
is wrong is the number and only the number: the bound is a rule about the tail, most results are far under any
number this tree has recorded, and the value therefore decides nothing except **how much of the long tail** a child
is handed and re-reads on every later turn.

## The single fix

One value: `tools/loop/loop.conf:493` → `:505` after the delta's own comment paragraph, `LOOP_TOOL_RESULT_MAX_CHARS=2000`.
No code moves: the trimmer already cuts at whatever the file records, and `0` still turns the rule off.

The number is not a preference, and the tree already works at it:

- `tools/loop/tests/loop-cases.sh:21021` pins a case's own bound at `pinned=2000` — `phase#theIdentitySurvivesTheBoundOnAnyRoot`
  — because the phase answer is the largest structured thing the loop hands a child and 2 000 is "loose enough that a
  root several times deeper than any this suite creates still leaves the identity inside it". A bound at that case's
  own pinned number is a number the loop's own largest answer already has to fit.
- `tools/gate-attribute.sh:74` cuts each line of its block to `max_chars / 9 - 4`, a ninth of the bound less the
  marker, over at most nine lines, so the block stays inside the bound arithmetically rather than by being truncated.
  That arithmetic still holds at 2 000 (`gate#theBlockStaysInsideTheRecordedBound`, run green against the new value).
- `tools/pi-trim-result.mjs:139` already cuts a **verification** read at `VERIFICATION_READ_BOUND = 2000`. The
  recorded bound meets the number a narrower rule was already shipped at instead of crossing it — which is also why
  that constant's own comment had to stop calling itself "a quarter of the recorded result bound".

And the class the diagnosis depends on does not move: `tools/pi-trim-result.mjs:186` raises the bound to
`GATE_EVIDENCE_BOUND` (8 000) for a result that is gate evidence by text or by path, so a gate log and a suite XML
keep their 8 000 whatever this key records (`guidelines/BuildEvidence.md` §4d).

## Why 2 000 and not 1 000

The bound is a rule about the tail **and the tail is what a diagnosis reads**: a failing test's JUnit line with its
`file:line`, the stack frame that attributes it, a `git diff` of a small hunk. 2 000 holds those; 1 000 starts
cutting them, and a child that has to re-read the range to see the failure has paid the turn the bound was meant to
save. The gate/evidence exemption is not the argument for stopping at 2 000 — it is held at 8 000 by the extension
either way — the ordinary `ctx_shell` result is what this key is about.

## Blast radius

Seven existing cases read the recorded value rather than naming their own, and the table's last row is this change's
own new case; each was run against the new value:

| case | what it asserts at 2 000 |
|---|---|
| `trim#aResultUnderTheBoundIsHandedOverWhole` (`:13848`) | rewritten by this change to read the file's number; 1 000-character fixture and the exact-length boundary both hold |
| `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst` (`:2299`) | the run's state carries the file's number; it follows the file by construction |
| `adapter#theChildIsStartedWithTheResultBound` (`:13950`) | the adapter hands the child the file's number |
| `look#aResultOverTheBoundIsCutAndNamed` (`:20410`) | the tree answer stays inside the bound and its count line survives |
| `gate#theBlockStaysInsideTheRecordedBound` (`:20731`) | nine facts, one per line, still inside a bound of a ninth each |
| `phase#aResultOverTheBoundIsCutAndNamed` (`:20951`) | the phase state, the tree and the package's head still reach the child at the recorded bound |
| `phase#theIdentitySurvivesTheBoundOnAnyRoot` (`:21016`) | the identity prints at the recorded bound on a deep root |
| `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` (`:13891`) | the red case: a 3 003-character result is cut and named |

Cases that pass a **literal** bound to the trimmer's CLI (`trim#aResultAboveTheBoundIsTrimmedAndNamed`'s 8 000,
`adapter#theResultBoundExemptsGateEvidence`'s 4 000) are untouched by the value: they assert the mechanism at a
bound of their own choosing, which is the arm the two cases above exist to complement. `--from-git` over a
`tools/loop/**` delta still selects most of the suite — 126 cases declare `tools/loop/loop.conf` in the tree this
change leaves, 124 on the base, and the difference is the two rows above (`grep -c
'# files:.*tools/loop/loop.conf' tools/loop/tests/loop-cases.sh`, `evidence/structural.log`) — and that full
selection is the gate's step, deferred to the gate child by this item's run.

## Rollback

One line back to `4000`. Nothing else needs to move: the requirement names no value, the prose cites the key rather
than the number, and the only case that would go red is
`trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut` — which is the point of it, because a raise is a decision
that has to be measured rather than a line edited back.

## Alternatives the evidence eliminated

- **1 000, or 500.** Cut the JUnit line and the stack frame `guidelines/BuildEvidence.md` §4d attributes a red run
  with. The exemption class keeps its 8 000, so what is lost is exactly the failing-test output of an ordinary
  `ctx_shell` read — the thing a diagnosis is done from.
- **Keep 4 000 and correct only the prose.** Leaves the item's measured tail on the table and leaves the number
  unowned by any measurement; the prose is what this change corrects *because* the number moves, not instead of it.
- **Lower `GATE_EVIDENCE_BOUND` with it.** §4d reads a gate log and the suite XMLs whole — the failing case's
  `file:line`, the JUnit `timestamp` order a failing set is bisected in. A bound about the ordinary result must not
  tighten the class a red run is attributed from.
- **Keep a head and a tail instead of a prefix.** A change to the mechanism to do a number's job: the notice already
  names the `sed -n` range that reads the rest, and the repeat-read rule answers a second read with the range
  rather than the file.
- **Add a 2 000 row to `tools/loop-spend.py`'s cap sweep (`:293`).** A change to the reader, owed by no case here:
  the item's model is the measurement behind the number and is quoted at `evidence/issue-1004-model.md`, not
  re-taken.

## What this change does not claim

- **Not a fresh model.** The gain is the item's (#1004), quoted as its model; this change takes no new measurement of
  spend and states none.
- **Not a save on its own.** Every cut result is a fact the child may have to fetch again, and #791 is the item that
  measured a bound's own ceiling: it cut the trims it counted and the item still ended with a context far above what
  that cut could buy. A lower bound claims its share and not a bound cut.
