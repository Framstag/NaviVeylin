# Round-2 corrections — what the review named, and what changed

Review round 1 returned `VERDICT: FAIL` with one BLOCKING finding and five NOTEs. The change was refused on the
blocking one; the NOTEs that named a false record were corrected in the same pass, because a changed change owes
true pointers. Nothing outside this change and its sibling files was touched.

## 1. The blocking finding — the gate named by the change was never run

Round 1 carried `loop-selftest.xml`, a **focused 10-case pass** of the prompt group, as the change's tally, and
`tasks.md` 5.4 marked done a whole-suite run that no artifact carried. `guidelines/Build.md:137` says in the
section this change edits: *"A declared-cases pass is never gate evidence."* The review was right and the task's
`[x]` was not.

Correction:

- `./tools/loop/gate.sh` was run **once**, whole, after the corrections, 2026-10-22T00:51:54+02:00 →
  01:05:33+02:00 (`gate.log`):

  ```
  LOOP-GATE: tests=195 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-doc-routes.sh)
  EXIT=0
  ```

- Per-suite tallies, copied from the gate's own tally directory
  (`app/build/test-results/loop-gate/`) at the moment they were produced: `gate-loop-selftest.xml` (150 cases),
  `gate-sensor-selftest.xml` (45), `gate-doc-routes.xml`, `gate-gradle.xml` (`BUILD SUCCESSFUL in 3m 28s`).
- The focused 10-case `loop-selftest.xml` stays as the record of task 5.2 and is no longer called gate evidence.
- `tasks.md` 5.4 now quotes the gate run; the proposal's gate paragraph was rewritten from *"none is owed"* to
  the run's own numbers.

Two things the run showed that the plan had not:

- The gate also ran the Gradle suite. Not because this delta changes a compiled file — it does not — but because
  the compiled pattern set includes `*.xml` and the archived JUnit evidence of this and neighbouring changes
  matches it. It passed (`BUILD SUCCESSFUL in 3m 28s`); its tally records `tests="0"` because `gate.sh` derives
  numbers only from a suite's shell summary line, so the Gradle result is legible in `system-out`, not in counts.
- `tools/sensor-selftest.sh` was owed by a sibling change's paths in the same worktree diff
  (`tools/sensors/*`, `.github/workflows/sensors.yml`), not by this delta.

## 2. The scope conflict the review asked the change to resolve

`guidelines/Build.md:109` says a delta that compiles nothing runs no build and no suite; `tools/loop/gate.sh`
maps `tools/**` and `*.md` at the shell suites. The proposal now states which governs where, in one paragraph:
`Build.md:109` is the build's gate (`./gradlew`, the module suites); inside a run the gate is the project's
`LOOP_GATE_CMD`, and its whole-suite run — not a focused case pass — is the evidence. `tasks.md` 5.4 carries the
same sentence.

## 3. The record errors the review listed

| review finding | correction |
|---|---|
| proposal's two timestamps (`00:42:41`, `00:43:12`) no artifact carries | replaced with the artifacts' own `00:44:20` (red) and `00:44:35` (green) |
| *"the two neighbouring prompt cases"* — the log carries 10 cases | *"the eight other cases of the prompt group"* |
| the surface record's second command does not find the third site, and its `3` counts two lines of one document | the record now carries the command that finds the two documents (`grep -rn 'rules 1-3\|rule 4' …` → `AGENTS.md:185`, `:186`, `.pi/skills/mutation-runner/SKILL.md:98`) and the separate command that finds the open change (`grep -rn 'rule 2' openspec/changes/fix-mutation-harness-file-scope/` → `proposal.md:29`, `:60`, `tasks.md:40`), restated as **two documents plus one open change** |
| *"inserting a new number before rule 5 would falsify the two live 'rule N' citations"* — nothing cites rule 5 | corrected to the mechanism that is true: rule 1 already governs measuring before code changes, a new number ahead of the cited rules would renumber them, and a rule appended after rule 5 would put a plan-time rule behind the reviewer rule |
| *"the count sets the budget, the review scope and the loop's floor"* names an effect nothing computes | replaced in `guidelines/Build.md`, `tools/loop/prompts/worker.md`, the merged spec, this delta's spec, `design.md`, `tasks.md` 2.1 and the proposal: *the count, not the first file the plan names, is what the plan's estimate is set against* — and the mutation `38.1` label now says the same |
| `tasks.md` 1.3 claims a `diff -q` byte-identical restore that the item dir does not carry | 1.3 now points at what exists: the red and green logs, `git show 80df3a3:<path>` for the pre-fix text, and the change's diff as the observable restore |
| `tasks.md` 3.1 names a change directory that only exists under `archive/` | 3.1 now records the validate result as taken while the change was open and names both delta paths |
| `tasks.md` 5.4's `[x]` claimed an unexecuted run | rewritten around the gate run above |

## 4. What was re-run, and why it is not gate evidence

- the two new prompt cases after the text corrections: `cases-after-corrections.log`, both `PASS` (2026-10-22T01:06:11+02:00);
- mutation `38.1` after its label change: `mutation-38.1.log`, `red-when-mutated: 1 · not-noticed: 0 ·
  restore-failures: 0`, `EXIT=0` (2026-10-22T01:06:40+02:00);
- the gate: once, above.

The red case is unchanged from round 1 (`red-before-fix.log`, `red-before-fix.xml`, `tests=2 failures=2`): the
corrections touch wording and records, not the invariant, so no new red run was owed and none was manufactured.
