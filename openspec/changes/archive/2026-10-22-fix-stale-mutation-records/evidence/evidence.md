# Evidence — fix-stale-mutation-records

Base commit `9f107493f4e64e7cdd62e60705951a50ece3c03a`. Every log named here is in this directory; the copies in
`.pi/issue-loop/items/264/` are the run's, and the change's are the versioned half.

## The defect, as the tree carried it

`mutations-baseline.log` — records `9.9` and `12.2` against the engine at HEAD:

```
$ bash tools/loop-mutations.sh --only '9.9 12.2'
MUTATION-DID-NOT-APPLY 9.9  …/tools/loop/run.sh (the pattern no longer matches — fix the mutation, not the case)
MUTATION-DID-NOT-APPLY 12.2  …/tools/loop/run.sh (the pattern no longer matches — fix the mutation, not the case)
red-when-mutated: 0 · not-noticed: 2 · restore-failures: 0        exit=1
```

The line they quoted and the line that exists: `tools/loop/run.sh:668`, quoted in the log's header.

## The red case on HEAD

`red-on-head.log` — the three cases this change adds, run against HEAD before the check existed:

```
FAIL mutation#everyDeclaredRecordApplies      … loop-mutations: unknown argument --check: expected '0', got '2'
FAIL mutation#aStaleRecordIsNamed             … expected '1', got '2'
FAIL gate#anEnginePathOwesTheRecordCheck      … 'loop-mutations.sh --check' not found in: tools/loop-selftest.sh
```

Three cases, three failures, no prior green.

## The check finds this very defect

`check-names-the-stale-records.log` — `--check` implemented, the two records **not** yet repaired:

```
MUTATION-PATTERN-STALE 9.9  tools/loop/run.sh (an expression of this record names no line of this file …)
MUTATION-PATTERN-STALE 12.2  tools/loop/run.sh (…)
loop-mutations: 2 of 84 checked records no longer apply — the set is stale        exit=1
```

Two records named out of 84, and both are the item's.

## The two records falsify again

`mutations-9.9-12.2.log` — the item's own evidence line, reproduced:

```
RED    9.9  state#agentCommandIsTheOnlyIndirection     restore 9.9 … green again
RED    12.2  run#childStartsInTheProjectRoot           restore 12.2 … green again
red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0        exit=0
```

## The new cases are falsifiable

`mutations-41.log` — one record per new case:

```
RED    41.1  mutation#aStaleRecordIsNamed         restore 41.1 … green again
RED    41.2  gate#anEnginePathOwesTheRecordCheck  restore 41.2 … green again
RED    41.3  mutation#everyDeclaredRecordApplies  restore 41.3 … green again
red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0        exit=0
```

## The check passes over the whole declared set

`check-green.log` — `loop-mutations: all 87 cases passed — every selected record applies to the file it names`,
exit 0.

## The gate

`gate.log`, run once:

```
# 2026-10-22T20:18:39Z
gate: tools/loop-selftest.sh
gate: tools/loop-mutations-selftest.sh
gate: tools/check-doc-routes.sh
exit=0
# 2026-10-22T20:32:43Z
```

The gate's aggregate was 200 cases and no failures — 166 cases from `tools/loop-selftest.sh` plus 34 from
`tools/loop-mutations-selftest.sh`, and `tools/check-doc-routes.sh`, which reports section references rather than
cases. The per-suite tallies are `gate-tallies/*.xml`, and each of these pairs is one of them:
`tests="166" failures="0"` (`tools/loop-selftest.sh`, the suite the two new `mutation#…` cases and
`gate#anEnginePathOwesTheRecordCheck` ran in), `tests="34" failures="0"` (`tools/loop-mutations-selftest.sh`) and
`tests="0" failures="0"` (`tools/check-doc-routes.sh`).

**One caveat, stated rather than glossed.** The gate's environment inherits `LOOP_CONF` from the run, and that value
names the project tree's `loop.conf`, not this change's: the owed list above is therefore the profile *before* the
two pairs this change adds. `gate-plan.log` is the same call with the change's own profile —
`LOOP_CONF=tools/loop/loop.conf` — and names the fourth check the change owes:

```
tools/loop-selftest.sh
bash tools/loop-mutations.sh --check
tools/loop-mutations-selftest.sh
tools/check-doc-routes.sh
```

That fourth command is not re-run as gate evidence: it is a reading command (`check-green.log`, exit 0 over 87
records), and the assertion that the gate owes it is `gate#anEnginePathOwesTheRecordCheck`, which ran inside the
gate's `tools/loop-selftest.sh` tally (166 of the 200). The gate was not run a second time.

## The archive-only checks, run before the move

The archive adds `openspec/**` paths to any diff, and two of the profile's checks are only owed for those:

```
$ tools/check-doc-routes.sh
check-doc-routes: all 152 section references resolve, all 4 skill references resolve
$ bash tools/gen-feature-list.sh --check-classification
unclassified: 0
delta spec ids: 1
stale: 0
car-only areas: 1
highlights: 19
```

## Delta and validation

`openspec validate fix-stale-mutation-records --strict` → `Change 'fix-stale-mutation-records' is valid`. The delta
adds one requirement with three scenarios, each naming one of the three cases above; the requirement body is 473
characters, under the validator's 500-character warning line.

## Archive

```
$ openspec archive fix-stale-mutation-records --yes
Applying changes to openspec/specs/agent-issue-loop/spec.md:
  + 1 added
Specs updated successfully.
Change 'fix-stale-mutation-records' archived as '2026-10-22-fix-stale-mutation-records'.
```

The merged requirement is `openspec/specs/agent-issue-loop/spec.md:1454`, "A declared mutation record is checked
against the file it names", with its three scenarios. `openspec validate agent-issue-loop --type spec --strict`
reports the spec's six pre-existing "Requirement text is very long" warnings and no error; this requirement is not
among them.

The archive put `openspec/**` paths into the diff, so the two profile checks those paths own were run again after
the move — the same two named above, both green:

```
$ tools/check-doc-routes.sh
check-doc-routes: all 152 section references resolve, all 4 skill references resolve
$ bash tools/gen-feature-list.sh --check-classification
classified: 177
unclassified: 0
delta spec ids: 0
stale: 0
```

## One cleanup after the gate, and why it is inert

Reviewing the diff found a helper (`engine_path`) left behind by a reverted refactor: dead code with no caller. It
was removed *after* the gate, and the removal is inert by construction — `grep -c engine_path tools/loop-mutations.sh`
is 0, so nothing can reach it — and it was re-verified on the paths that own the harness:
`tools/loop-mutations-selftest.sh` → `all 34 cases passed`; `tools/loop-mutations.sh --check` → `all 87 cases
passed`; the three records `41.1`–`41.3` → three `RED` and three `green again`, `not-noticed: 0`
(`post-gate-cleanup.log`). The gate was not run a second time; `tools/loop-selftest.sh`, the only suite the removal
could conceivably touch, exercises `run.sh` and the engine, not an uncalled function of the harness.
