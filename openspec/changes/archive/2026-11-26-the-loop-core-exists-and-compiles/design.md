# Design — the loop's core, behind an unchanged boundary

## Context

See `proposal.md` — Why. The constraints that decide most of this document:

- **479 shell cases drive the engine by CLI.** They are the only regression net the loop has. Any design that
  makes a case call a library instead of a script re-authors 21 000 lines of test, so the process boundary and
  every exit code stay exactly as they are.
- **The loop repairs its own engine**, about 10 commits a day (304 of 1718 commits to 2026-11-17 touched
  `tools/loop`). A compile step in that path is a real cost, to be measured rather than dismissed.
- **`tools/issues/core` is the precedent**: 10 dependency-free Java files, `javac --release 17` into a digest-keyed
  staging directory, one refusal when no compiler answers, `Json.java` throwing on malformed input rather than
  parsing partially. The loop's driver already compiles it (`tools/loop/tests/loop-selftest.sh:424`, `:434`).
- **`tools/loop/**` may name no project value**, and the portability check already reads `*.java` under
  `tools/loop` (`tools/check-portability.sh:222`) — the check anticipated this core.
- **The supervision half stays shell.** `run_child`'s fork, process group, heartbeat and the kill of everything a
  child started stay where they are: a JVM supervises badly, and a JVM that must die with its group is a new
  failure mode in the one place that kills.

## Goals / Non-Goals

Goals: the core exists and compiles once per source digest; a malformed input is a refusal before anything is
written; the suite runs the core's cases as its own; a rename is caught by the compiler rather than by a full
suite.

Non-goals: the state's schema and the scripts that read it (`the-run-state-is-typed`), a child's verdict
(`the-core-reads-the-verdict`), the bounds, the spend and the budget
(`the-bounds-spend-and-budget-are-typed`), the supervision, the git work, the tracker and the prompts. Also not a
goal: changing what the scripts print or what they exit with.

## Decisions

### D1 — Java by `javac --release 17` into a digest cache, not a Gradle module

Alternatives:

- **A Gradle module** (rejected): the engine must be copyable into another project as files (`PORTING.md`: the copy
  is the distribution), and a module makes the tooling part of the build it judges.
- **A second compiler toolchain** (rejected): a Kotlin or a Scala build in a path that must work offline.
- **Stay in shell** (rejected as the whole answer): a shell function has no type, so the rename risk this change
  exists for stays.

Chosen: the issue tooling's exact shape, because the JDK is already a declared prerequisite and `Json.java` can be
copied by value so the two cores share no dependency.

### D2 — The core sits behind the scripts, one call per phase

Alternatives:

- **A core in front** (one Java entry that runs the whole loop) — rejected: it re-authors the suite and moves the
  supervision into the JVM.
- **A call per value** (`jq`'s current shape, one `java` per read) — rejected: a JVM start is ~200–300 ms, so a
  per-value call would cost a run minutes.

Chosen: one invocation per phase, with the verb doing work in batches — `state counts` answers six counts at once,
which is why the boundary costs one call and not six.

### D3 — The core's cases are shell cases that drive the binary

Alternatives:

- **A JUnit suite** (rejected): it would be a second harness, a second selection and a second summary for a suite
  whose gate is one offline shell script.
- **`Cases.java` in the core** (rejected for the loop's core): the issue tooling's core carries its own runner
  because its cases are pure functions; the loop's cases drive scripts and fixtures and need the suite's `--files`
  and `--case` selection.

Chosen: `core#` cases in `loop-cases.sh` that run `loop_core <verb> …` against fixtures and assert the JSON. One
harness, one selection, one offline gate; `core_ensure` in the driver compiles once for the whole suite, and the
driver pools the core's case list with the shell's so `--case` reaches both.

### D4 — The dispatch refuses an unknown verb, and the next verbs plug into it

Alternatives:

- **One binary per verb** — rejected: four digests, four caches, four copy entries.
- **No dispatch until all verbs exist** — rejected: the state's cases would then wait on the verdict's work, which
  is the coupling the split exists to remove.

Chosen: `LoopCore.java` takes the verb as its first argument and exits 3 naming an unknown one. `state` is the only
verb it answers today; the sibling changes add verbs without moving the boundary.

## Risks / Trade-offs

- **javac in the path of every engine fix** (children fix the engine ~10×/day) → digest cache: the first case of a
  suite compiles once, and a fix that touches one core file recompiles once. Measured in this change (cold and
  warm compile time) so the cost is a number rather than an opinion.
- **A second stringly boundary appears inside the core** (verbs parsed by hand in shell) → the verb's answer is one
  JSON object read by `Json`, and the shell side never splits it; a case asserts one document per verb.
- **Two JSON readers in the tooling** (the issue core's and the loop core's, copied by value) → a divergence is a
  defect worth a finding, not a shared module; `PORTING.md` states the copy is on purpose.
- **The core grows into a second engine** (the failure the `run.sh` split is fighting) → the layout table carries a
  bound per core file too, and the guard census reports the number.
- **A second case source with one summary** → one pool, one selection, one summary; the driver's declaration check
  spans both sources so a case that exists in only one is caught.

## Migration Plan

1. The core's skeleton — `Json.java`, `LoopCore.java`, `State.java` — compiled behind `lib.sh`'s `loop_core`, with
   the shell side still doing its own `jq` work: nothing switched, the cases prove the verb.
2. The suite compiles the core and pools its case list; the portability check and the case-list check read it.
3. The documents name the core. The sibling change switches the scripts onto it.

Rollback: revert the commit. No shipped exit code changes and no state shape changes, so a reverted tree is the
tree before the core.

## Open decisions

none
