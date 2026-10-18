---
name: mutation-runner
description: Runs a versioned set of shell-gate mutations — one per invariant, red asserted, the file restored byte-for-byte, green again — with a guard against a mutation that never landed and one against a case that does not notice. Use when a change's falsification pass is owed for a shell self-test (tools/issues, tools/sensors, tools/loop, tools/check-portability.sh), when a task says "revert-check", "one mutation" or "the case that must fail" and the gate is not Gradle, or when a mutation set in the repository has to be run, listed or repaired.
---

# Mutation runner (shell gates)

`.pi/skills/revert-check` owns the **rule**: one mutation per invariant, the mutation must actually land, the
named case must fail for the right reason, restore, green again. This skill owns the **mechanism** for the
gates that are shell self-tests rather than Gradle suites — `tools/issue-tooling-selftest.sh`,
`tools/sensor-selftest.sh`, `tools/loop-selftest.sh`, `tools/check-portability-selftest.sh` — where there is
no build cache to force and no JUnit XML to quote.

```bash
tools/mutate.sh --mutations tools/portability-mutations.json              # the whole set
tools/mutate.sh --mutations <file> --only 5.1 5.2                         # while iterating
tools/mutate.sh --mutations <file> --list                                 # labels, cases, reasons
tools/mutate.sh --mutations <file> --out evidence/revert-checks.log       # transcript to a file as well
tools/mutate-selftest.sh                                                  # the runner's own guards
```

Exit `0` every selected mutation went red and its restore went green; `1` a problem (the four below); `2`
usage, a missing prerequisite or a malformed set.

## Why a runner and not a session's scratch script

The pass was hand-rolled once per change — three bespoke `*-mutations.sh` harnesses and, in
`tooling-portability`, a throwaway `.pi/tmp/revert-check.sh` — and every hand-rolled version has the same two
silent lies:

| silent lie | what it looks like | what it means |
|---|---|---|
| **the mutation never landed** | the case is green, so the transcript reads as "the case cannot be falsified" | the search no longer matches the line the change wrote (an edit, a reflow, a rename) — a stale pattern, not a strong test |
| **the case does not notice** | the mutation landed and the case is green anyway | the assertion is adjacent to the invariant instead of on it (`revert-check` pitfall 2) |

The runner reports both as failures of the run (`MUTATION-DID-NOT-APPLY`, `STAYS-GREEN`) and never as a pass.
It adds two guards on the restore, `RESTORE-FAILED` and `RESTORE-STAYS-RED`, because an inexact restore is
the third way the pass lies: a byte difference or a dropped permission bit means the "green again" run was on
a tree the gate was never green on. Content **and** mode are compared, the file is restored even if the
runner is killed (EXIT trap), and a set lives in the repository, so it is versioned and reproducible instead
of a session's scratch directory.

## The set

```json
{
  "comment": "what this set proves, and where its evidence is recorded",
  "case-cmd": "bash tools/check-portability-selftest.sh --case {case}",
  "mutations": [
    { "label": "5.7", "case": "porting#everyCommandInTheRecipeRuns",
      "file": "PORTING.md",
      "search": "bash tools/check-portability.sh --quiet",
      "replace": "bash tools/check-portability.sh --nope",
      "why": "every command in the porting recipe runs" }
  ]
}
```

- **`case-cmd`** runs in the tree root with `{case}` replaced by the record's case id (an exact-token
  substitution — a command that needs a quote writes it itself). It must exit 0 on a clean tree.
- **`search`** is a **literal, single-line** string: not a regular expression, no escaping, no globbing (`?`,
  `*`, `[` are common in shell and would be misinterpreted by a `${var/…}`). Every occurrence on a line is
  replaced and the count is printed, so a pattern that now hits two lines is visible rather than silent.
- **`replace`** removes the invariant. Mutate the *engine*, never the case: deleting an assertion proves
  nothing about production.

Write the `search` as **the exact text the change wrote**, long enough to be unambiguous — a `grep -c` of the
search before adding the record tells you whether it is unique.

## Writing the set for a new change

1. Take the triple from each task item that asks for a revert-check: invariant, mutation, case id — before
   touching code (`revert-check` procedure step 1).
2. Add one record per invariant. One mutation per invariant; a record that changes two things makes the
   assertion vacuous.
3. `bash tools/mutate.sh --mutations <file> --only <label>` while iterating: it prints red/restore/green in
   three lines.
4. Then run the whole set and quote its transcript in the task item or the change's `evidence/`.
5. If a mutation comes back `STAYS-GREEN`: the case is the finding, not the runner. Either the assertion must
   be moved onto the invariant (usually the right answer — say so in the task) or the mutation is not the one
   that removes the invariant.
6. Print `--list` in the CI step that owns the engine, the way the loop and sensor steps do: a set that no
   longer parses then fails the pipeline instead of rotting.

## Pitfalls

1. **A search with a trailing newline.** The values are read with `jq -r` into shell variables, so a trailing
   newline is stripped. Keep searches single-line; the runner reports `MUTATION-DID-NOT-APPLY` rather than
   guessing.
2. **Mutating a file the suite copies at test time.** That is fine — and usually the point: the portability
   cases copy the engines into throwaway trees *after* the mutation, which is why the mutating run sees it.
   But a mutation in a file the case reads from the repository (a document, a fixture overlaid later) has to
   land before the case starts; the runner does that by construction (mutate → run → restore).
3. **A timeout too small.** `--timeout` defaults to 600 s per case; a case that builds a throwaway tree with
   a JDK compile is slower than it looks. A `timeout` kill counts as a red run — check the exit code in the
   transcript rather than trusting "not zero".
4. **Running the set on a dirty tree.** The restore is exact, but a peer's edit to the same line makes the
   search ambiguous or absent. One writer per working tree (`guidelines/Build.md` §2, rule 4).
5. **Leaving the runner's job to memory.** A mutation set that is not in the repository is a claim, not a
   proof; `tools/mutate.sh` exists so the `tooling-portability` revert-checks can be re-run years later in one
   command.

## References

- `.pi/skills/revert-check/SKILL.md` — the rule this runner implements, including the Gradle form
- `tools/mutate.sh`, `tools/mutate-selftest.sh`, `tools/fixtures/mutate/` — the runner and its own guards
- `tools/portability-mutations.json` — a worked set (eight records: the seven invariants of that change, one of them naming two cases)
- `openspec/changes/archive/2026-10-18-tooling-portability/evidence/revert-checks.md` — the transcript and the reasoning
- `guidelines/Build.md` §4 — result evaluation; "a cached run is not a run"
