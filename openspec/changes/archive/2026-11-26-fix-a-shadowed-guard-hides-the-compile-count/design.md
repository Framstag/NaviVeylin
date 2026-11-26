# Design — one read of the compile marker

## Root cause

`tools/loop/tests/loop-selftest.sh:442` (base) opened with `  if [ ! -f "$selftest_core_marker" ]; then`, the fast
path for a case that asks for the core when an earlier case has already compiled it, and the claiming block inside it
re-read the same marker at `:449` before compiling. Two reads, one condition: whichever read a mutation rewrites, the
other still decides. Record `48.2` rewrites the inner one, and the asking case that runs *after* the first never
enters the block at all — so the mutation is text that applies (`--check` agrees) and behaviour no case can see
(`not-noticed: 1`).

## The single fix

`core_ensure` claims first, then decides once:

- `while ! mkdir "$selftest_core_claim"` (unchanged: the claim is the mutual exclusion, the wait is bounded at
  ~1200 × 50 ms, the marker breaks the wait, a claimed-then-killed claim is removed and taken again);
- `if [ ! -f "$selftest_core_marker" ]; then` — one read, at the function's top level — compiles into
  `$selftest_classes` and writes the marker, exactly as before;
- the claim is released after the decision, so a case that found the marker still drops the claim it took.

The removed outer read skipped a `mkdir`/`rm -rf` pair per asking case — two syscalls against a `javac` the block
then skips, and the case that needs the compile pays the same claim either way. Record `48.2`'s expression moves from
`^    if \[ ! -f "$selftest_core_marker" \]; then$` to `^  if \[ ! -f "$selftest_core_marker" \]; then$`, and its
comment states why one read is the whole guard.

The new case in `tools/loop/tests/loop-mutations-selftest.sh` counts those reads in the repository's driver
(`sed -n '/^core_ensure() {/,/^}$/p' … | grep -c`) and requires one. It is a structural check on purpose: the
behavioural half is record `48.2`, and the state this item was filed on is the one where the structural half is
missing and the behavioural half therefore says nothing.

## Blast radius

- `core_ensure` is called by `tools/loop/tests/loop-cases.sh` at every place that starts the engine or the Java core,
  so every case that asks for the core passes through this function. What changes for them: the claim is taken and
  released on every ask (was: only when the marker was absent), and the marker is read once instead of twice.
- Nothing else reads `$selftest_core_marker` (`tools/look.sh 'selftest_core_marker'`: four occurrences in the driver —
  the declaration, the wait's break, the guard, the write — and one record). No path, no exit code and no message
  moves; `ISSUES_CORE_CLASSES` is exported exactly as before.
- A concurrent pair of asking cases still compiles once: the second waits for the marker, breaks on it, and compiles
  nothing.
- The harness's self-test gains one case (40 pass, was 39).

## Rollback

Revert the two files: the driver's `core_ensure` back to the fast path plus inner guard, record `48.2` back to the
inner read's indentation, and the self-test's case 11 removed. That restores the unnoticeable record with it, which
is the state the item is about.

## Alternatives the evidence eliminated

- **Point `48.2` at the inner guard** (base's own text): eliminated — the second asking case is turned away at the
  outer read, so no expression inside the block is on any path that case takes. Measured: the base run of `48.2` is
  `not-noticed: 1` with the record pointing exactly there.
- **Drop record `48.2`** (the repair record `12.12` got when its case stopped being able to notice it): eliminated —
  here the case *can* notice the count (it installs a `javac` that records every invocation), it was the shadow that
  hid it; deleting the record would leave the invariant asserted by nothing, which is the state the item names.
- **Have the harness reason about reachability**: eliminated as scope — see `proposal.md`.
- **Assert the count from a second driver run**: eliminated as a slower copy of `48.2`.
