# Proposal — the compile count is asserted by the record whose line a case reaches

## Why

**Root cause.** `core_ensure` in `tools/loop/tests/loop-selftest.sh` read the compile marker in **two** places: a
fast path at `:442` (`  if [ ! -f "$selftest_core_marker" ]; then`) wrapping the claiming block, and the guard inside
that block at `:449` (`    if [ ! -f "$selftest_core_marker" ]; then`). Record `48.2`
(`tools/loop/tests/loop-mutations.sh:456`) quoted the **inner** read, so on the base it rewrote
`    if [ ! -f … ]; then` to `    if true; then` — a line the *second* asking case never arrives at: its first
`core_ensure` finds the marker at the outer fast path and returns without entering the block. The mutation landed,
changed nothing any case could see, and the invariant "`javac` runs once per suite" (issue #261, case
`driver#theCoreIsCompiledOncePerSuite`) was therefore asserted by no passing record — exactly the state
`loop-mutations.sh`'s header names as issue #264.

**The arm the item was filed on** (`evidence/red-on-head.log`, taken on the run's base
`42ddc5469a3d2cc69bf039f542795db250a1450e`):

```
GREEN  48.2  driver#theCoreIsCompiledOncePerSuite   <-- the case did not notice the mutation
restore 48.2 driver#theCoreIsCompiledOncePerSuite green again

red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0
```

while `--check` reported the record still applying to the line it quotes
(`evidence/mutations-48.2-check.log`): a guard that stopped guarding, and no reading that could say so.

**The fix.** The marker is read in one place — the claiming block, at the function's top level:
`mkdir` the claim first, then the single `if [ ! -f "$selftest_core_marker" ]; then` compiles and writes it. The
outer read was a fast path and nothing else: it skipped a `mkdir` the claim block takes anyway, and its removal leaves
every asking case passing through the one read the record quotes, so the record now mutates a line the case reaches.
Record `48.2`'s expression moves to the surviving read's indentation and says why in its own comment.

**Red on HEAD** (`evidence/red-on-head-case.log`): the case this change adds to the harness's own self-test reads the
driver and counts that read — with the driver restored from the base it fails, the only failure of 40 cases:

```
FAIL the compile's marker is read in one place, so the line record 48.2 mutates is one a case reaches: expected [1] got [2]
loop-mutations-selftest: 1 of 40 cases failed — the mutation harness does not hold its own guards
```

On the change's tree the same suite passes 40 of 40 (`evidence/mutations-selftest.log`), the record is red with the
assertion it now reaches (`evidence/mutations-48.2.log`):

```
RED    48.2  driver#theCoreIsCompiledOncePerSuite
       2026-11-26T03:43:35Z — case log line 1: the core is compiled once for the suite, not once per asking case
       (javac ran 2 times …): expected '1', got '2'
restore 48.2 driver#theCoreIsCompiledOncePerSuite green again
red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
```

and `48.1`, the record that puts the compile back above the case list, is still red on the same tree
(`evidence/mutations-48.1.log`: `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`), so one read did not
cost the other half of the invariant its falsification.

**The owning rule.** `guidelines/BuildEvidence.md` §4e "Falsification, flakes and scheduling traps" — *a new
regression test must fail against the unfixed revision first; one that passes against the bug is worse than none* —
and *the assertion must be what the guard produces … not the absence of growth*. The section already states the rule,
so this change adds no guideline text: what it adds is the tree in which the rule holds, and the self-test case that
keeps a second read of the marker from coming back.

surface: 3 files edited, 1 case added (`loop-mutations-selftest.sh`), 2 records re-verified (`48.1`, `48.2`, the
latter rewritten), 1 requirement delta (1 modified requirement, 1 scenario added) — `tools/look.sh
'selftest_core_marker'` → `LOOK: 11 matches in 6 files`, of which `loop-selftest.sh` 4, `loop-mutations.sh` 1,
`loop-mutations-selftest.sh` 1 (the new case) and three are this change's own `openspec/changes/**` text. No case
list moves: `tools/loop/tests/loop-cases.sh` declares no case of its own here, so nothing is rewritten there and no
fixture is realigned.

## What is deliberately not here

- **Moving record `48.2` onto the inner guard** — the item's first option — cannot work: with the outer fast path in
  place the second asking case is turned away at `:442` and reaches neither guard's body, so the record stays
  unnoticeable wherever inside the block it points. Only removing the shadow makes the count observable.
- **A harness-level refusal of a record whose line a case cannot reach** would ask the harness to model the control
  flow of the case's own script. The harness knows a record applies (text) and whether the case noticed (run); the
  shape in between is the driver's, and it is guarded by a case rather than by the harness.
- **A second assertion of the count** (a case that runs the suite twice and counts `javac` itself) is what record
  `48.2` already is; adding a slower copy of it buys a second reading of the same number.
