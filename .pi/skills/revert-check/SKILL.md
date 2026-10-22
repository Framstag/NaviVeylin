---
name: revert-check
description: Proves a new guard test can actually fail — applies one deliberate mutation to the production invariant, confirms the named test case fails for the right reason, restores, and records both runs as evidence. Use when a task requires a revert-check or a falsification step, when a change adds a test that guards an invariant, before claiming a test covers new behaviour, or when an OpenSpec tasks.md item says "revert-check" / "one mutation".
---

# Revert-check (falsification of a new guard)

A green test proves nothing until it has been shown **capable of failing**. This skill performs that
falsification once per invariant, in a way that leaves quotable evidence and a clean tree.

It exists because the repo has been burned by the alternative: origin changes shipped without a
falsification step, every later fix change had to add one (`TODO.md` §113), and a geometry test that
handed its own inputs in stayed green while the production call site passed the wrong value for weeks
(`TODO.md` §111). `TODO.md` §40.12 is the load-bearing rule: **one mutation per check.**

## When to use

- An `openspec/changes/<change>/tasks.md` item says "revert-check", "one mutation", "the case that must
  fail", or "verified by removing the guard" (the `spec-driven` tasks convention this repo follows)
- A change adds a test for a new invariant, a new predicate, a new bound, or a new refusal path
- Before quoting "the test covers this behaviour" — especially when coverage numbers are unusable
  (Kover/Robolectric attribution, `TODO.md` §15)
- Before archiving a change whose `specs/` delta added a scenario with no device-verifiable path

## When NOT to use

- Pure docs/spec/tooling changes with no production invariant
- The behaviour is device-gated and no device exists: do not fake a mutation run — record the blocker,
  like the change's device task does
- The "guard" is a library or framework contract you cannot mutate (say so; see Pitfalls)

## Contract

| Element | Rule |
|---|---|
| Mutations | **exactly one** per run — combining two makes the assertion vacuous (`TODO.md` §40.12) |
| Target | production code only — mutating the test proves nothing |
| Scope of the run | **every** case the guard protects, not just the closest one |
| Failure reason | the expected assertion, quoted — a compile error or an unrelated premise is not a falsification |
| Restore | exact reversal, then `grep -c 'REVERT-CHECK MUTATION'` → `0` |
| Green re-run | **forced** (`--rerun-tasks` / `--rerun`), and the XML `timestamp` must be *after* the restore |
| Record | mutation, failing case + message, both runs' counts and executed-task count, in the task |

## When the gate is a shell self-test, not Gradle

Some invariants live in shell — the issue tooling (`tools/issues/**`), the agent loop (`tools/loop/**`) —
and their gate is a self-test script rather than a suite (`tools/issue-tooling-selftest.sh`,
`tools/loop-selftest.sh`). The rule is unchanged (**one mutation per invariant, the named case must fail for
the right reason, restore, it must pass again**); three mechanics differ:

* there is no build cache to defeat — a shell case always executes, so the "forced re-run" step is the same
  command as the red run, and the evidence is the script's `PASS`/`FAIL <case>` line plus its exit code
  (0 pass, 1 fail), not a JUnit XML;
* the mutation is a `sed` on one line of the script or the skeleton (a lock, a refusal, an ordering, a
  configured value) — and the two ways such a mutation silently lies are **the pattern no longer matches the
  line** and **the case does not assert the invariant**;
* the case names are the self-test's case ids (`state#secondRunRefusesWithExit5`), so the task item quotes
  one id rather than an `--tests` filter.

The mechanical form for the loop is **`tools/loop-mutations.sh`**: one command per mutation, red asserted,
restore asserted, a `MUTATION-DID-NOT-APPLY` guard, and an EXIT trap that restores what it touched if the run
is killed. `--list` prints the set; `--only <labels>` runs one; `--engine <copy>` runs against a copy (which
is what its own self-test, `tools/loop-mutations-selftest.sh`, does to prove both guards and the restore);
`--files <path> ...` and `--from-git <base>` bound the pass to the records whose file the change touched, and
the count line (`selected 7 of 81 records`) says so.
Quote its transcript in the task item, exactly as the Gradle form quotes a tally.

**The generic form for any shell gate is `tools/mutate.sh`** (`.pi/skills/mutation-runner`): the mutations
live in a versioned JSON set (`tools/portability-mutations.json` is a worked one), so the pass is
reproducible years later instead of living in a session's scratch directory, and the restore is verified
byte-for-byte *and* by permission bit:

```bash
tools/mutate.sh --mutations tools/portability-mutations.json --list              # labels, cases, reasons
tools/mutate.sh --mutations tools/portability-mutations.json --only 5.1          # one, while iterating
tools/mutate.sh --mutations tools/portability-mutations.json --out evidence/revert-checks.log
tools/mutate-selftest.sh                                                          # the runner's own guards
```

It reports the two silent lies as failures — `MUTATION-DID-NOT-APPLY` (the search no longer matches the line
the change wrote: a stale pattern, not a strong test) and `STAYS-GREEN` (the mutation landed and the case did
not notice) — alongside `RESTORE-FAILED` and `RESTORE-STAYS-RED`. A set's `--list` belongs in the CI step
that owns the engine, so a set that no longer parses fails the pipeline instead of rotting.

```bash
tools/loop-mutations.sh --list            # what the invariants are, and which case owns each
tools/loop-mutations.sh --only 9.1        # one mutation, while iterating
tools/loop-mutations.sh --files tools/loop/worktree.sh   # the records whose file this change touched
tools/loop-mutations.sh --from-git <base> # the same set, derived from `git diff --name-only <base>`
tools/loop-mutations.sh --out <file>      # the whole set, with the transcript recorded
```

A filtered run prints its selection against the declared set (`selected 7 of 81 records`) — the line that says
the pass was a subset — and a filter that names no record exits 2, because a mutation run that mutates nothing
must never report success. Run the file set while iterating and the whole set before a change that owns
`tools/loop/**` is archived.

## Procedure

1. **Name the triple before touching code** and write it into the task item: the invariant, the single
   mutation, and the case that must fail. A revert-check invented after seeing the result is not a check.
2. **Baseline**: run the case set on the current tree and quote `tests/failures/errors` plus the XML
   `timestamp`. It must be green before the mutation, or you are measuring something else.
3. **Baseline the suite too when the invariant is cross-class**: `run-tests` (see that skill) — a
   mutation is only meaningful against a known-green state.
4. **Apply the single mutation** to the production line, with an unmistakable marker comment:
   ```kotlin
   // REVERT-CHECK MUTATION (task 3.3): <what this removes>; restore after the run.
   ```
   The marker is what makes a forgotten mutation detectable — always grep it after restoring.
5. **Run every case the guard protects**, by `--tests` filters:
   ```bash
   ./gradlew :app:testMobileDebugUnitTest --tests "<FQCN>" --tests "<FQCN.otherCase>"
   ```
   A mutation that fails only *one* of them is a measurement of the tests, not of the guard — then the
   other case's assertion does not target what the guard checks (see Pitfalls).
6. **Confirm the failure reason**: open the JUnit XML and quote the assertion message.
   The mutation must fail **at the assertion that describes the invariant**. If the case fails earlier
   (a premise assertion, a compile error, an NPE in the fixture), the run is not evidence — fix the case
   or the mutation and repeat.
7. **Restore** the mutation exactly (reverse the same edit; remove the marker).
8. **Re-run GREEN FORCED — never trust the restored tree's first result.** Restoring returns the source
   to the state whose successful result is already cached, so the test task comes back `UP-TO-DATE` (or
   `FROM-CACHE`) in seconds and the XML still carries the *previous* run's `timestamp`. Force it:
   ```bash
   ./gradlew :app:testMobileDebugUnitTest --tests "<FQCN>" --rerun-tasks
   ```
   Then verify all three: `actionable tasks: N executed`, a fresh XML `timestamp`, `failures="0"`.
   When only the tests have to re-execute, `-PforceTests --no-build-cache` is cheaper and equally forced
   (change `speed-up-build-test-gate`): the test tasks execute while the compiles stay `UP-TO-DATE`. Keep
   `--rerun-tasks` when the mutation changed anything the compiles see.
9. **Record the evidence in the task item** in one block, then mark the checkbox:
   ```
   — ran <date>: with <mutation>, <case> failed (<message>, XML tests="1" failures="1",
   BUILD FAILED in 52s); restored, re-ran with --rerun-tasks → tests="5" failures="0",
   110 actionable tasks: 110 executed, fresh timestamp.
   ```
10. **If it stays green, that is a finding — not a failure.** Some guards are structural (the mutated
    path is unreachable through the public API today). Report it in the task in exactly those words, name
    the discriminating revert-check that *does* fail, and let the reader judge. Precedent: the honest
    correction recorded in `openspec/changes/archive/2026-09-27-fix-phone-gesture-pan-tracking/tasks.md`
    item 7.6.

## Pitfalls

1. **The restored run is answered from the cache.** After a mutation → restore cycle the tree hash equals
   the state that already produced a green XML: `BUILD SUCCESSFUL in 4s`, old `timestamp`. A 4-second
   "green" is not the second half of a revert-check — `--rerun-tasks` it (`guidelines/Build.md` §4,
   `TODO.md` §17).
2. **The case asserts something adjacent to the guard.** The embedded-map fit's position case asserted the
   two endpoints while the fit verifies its padded extent — it passed with the verification removed.
   Run all protected cases and assert exactly what the guard computes.
3. **Failure for the wrong reason.** A mutation that breaks compilation, or that trips the case's own
   "premise" assertion, tells you nothing about the guard. Quote the message, always.
4. **Mutating the test.** Deleting an assertion or a `--tests` filter proves nothing about production.
   Mutate the invariant: remove the lock, the bound, the check, the call, or the guard branch.
5. **Two mutations at once.** Each can mask the other, and the remaining assertion becomes vacuous
   (`TODO.md` §40.12).
6. **Leaving the mutation in the tree.** `grep -rn 'REVERT-CHECK MUTATION'` must return nothing before the
   change is committed or archived; a leftover mutation is worse than a missed check.
7. **Forgetting the full gate.** A revert-check verifies the new guard, not the change: the `:app` suites
   (both flavors) still have to run before "done" — a behaviour change can move an expectation pinned in
   an unrelated class (`grep` the suite for the constant/value your change moves *before* the gate).
8. **Claiming a device-gated invariant was falsified.** If the only reachable evidence is a device, the
   honest record is the blocker, in the task, like any other device-gated item.

## References

- `guidelines/Build.md` §4 — result evaluation: tallies from the XML, "a cached run is not a run",
  attribution ritual
- `.pi/skills/run-tests/SKILL.md` — how to run the cases (and how to force a real execution)
- `tools/loop-mutations.sh`, `tools/loop-mutations-selftest.sh` — the mechanical form when the gate is a
  shell self-test (`tools/loop-selftest.sh`); `openspec/changes/agent-issue-loop/evidence/revert-checks.md`
  is a worked example (11 mutations, 11 red, 11 restores green)
- `tools/mutate.sh`, `tools/mutate-selftest.sh`, `tools/portability-mutations.json` and
  `.pi/skills/mutation-runner/SKILL.md` — the generic form for any shell gate, with the mutations versioned
  in a JSON set; `openspec/changes/archive/2026-10-18-tooling-portability/evidence/revert-checks.md` is the
  worked example
- `.pi/skills/openspec-apply-change/SKILL.md` — the apply loop this skill plugs into
- `TODO.md` §113 (origin changes without falsification), §111 (a case that proved nothing about the call
  site), §40.12 (one mutation per check), §17 (cache-masked verdicts)
