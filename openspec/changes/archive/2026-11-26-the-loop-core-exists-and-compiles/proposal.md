# Proposal — the loop's core exists, and the boundary it sits behind does not move

## Why

The loop's engine is 67 shell functions in one namespace and 172 `jq` call sites, and a rename is proven by the
whole suite or not at all. Measured 2026-11-17 on the committed tree:

- **172 `jq` call sites** in the five protocol scripts (`state.sh` 48, `report.sh` 55, `prescreen.sh` 34,
  `run.sh` 24, `lib.sh` 11), each a string expression over an untyped document, none of them unit-tested on its
  own;
- **the protocol's vocabulary is a set of magic strings** — `result == "cut"`, `resume-at <epoch>`, `verdict:`,
  `mechanism=bound` — declared nowhere and checked nowhere: 47 spellings across six engine scripts;
- **a rename is caught by the suite or by nothing**: 479 shell cases drive the engine by CLI, so a shell function
  has no unit test and a changed name is a full-suite run.

This change is the first half of the item `loop-protocol-core-in-java` was filed as, re-cut on 2026-11-23 into
#1037 (this change) and #1038 (`the-run-state-is-typed`), which switches the scripts onto the core this change
builds. The item was refused as `too-wide` because one delta carried all five values — 6 requirements, 17
scenarios and six Java files over four scripts rewritten in one pass. This change carries the core's existence and
its compile path; the sibling carries the state and the scripts that read it. Together the two carry exactly the
original's 6 requirements and 17 scenarios.

## What Changes

- **A typed core**: `tools/loop/core/*.java`, dependency-free, compiled once with `javac --release 17` and cached
  by source digest — the pattern `tools/issues/core` already uses, and `tools/loop/tests/loop-selftest.sh:424`
  already compiles *that* core for the loop's suite (`:434`), so this is one more subject for a mechanism that
  exists rather than a new one.
- **One verb in this change: `state`.** It reads the run's document under a declared schema and answers the six
  count queries in one call. The dispatch refuses an unknown verb by name, which is what the sibling changes plug
  into.
- **The boundary does not move.** `state.sh` and `report.sh` keep their command lines, their exit codes and their
  human output; the core is called once per phase. That is the constraint the 479-case suite depends on: its cases
  drive scripts by CLI, so a core behind a script is invisible to them.
- **The suite gains a second case source.** `core#` cases live in the shell case file and are pooled, selected and
  summarised with the shell's, so a core case is never a case that exists and does not run.
- **One JSON document per answer, and a refusal instead of a partial parse.** A malformed input exits 3 with the
  field named — the rule `Json.java` already states, and the one a 0-byte answer needs.
- **Scope**: `tools/loop/**` only. No app code, no native code, no Gradle change, no new dependency; a JDK ≥ 17 is
  already a declared prerequisite (`PORTING.md`, "Prerequisites").
- **Not this change**: the state's schema and the scripts that read it (`the-run-state-is-typed`), a child's
  verdict (`the-core-reads-the-verdict`), the bounds, the spend and the budget
  (`the-bounds-spend-and-budget-are-typed`), and the supervision — `run_child`'s fork, process group, heartbeat and
  pid tree stay shell, because a JVM supervises badly and the children must be killable as a group.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `agent-issue-loop` gains four requirements, all new. The shipped `The self-test compiles the Java core when a
case asks for it` is **not** modified: it describes the issue tooling's core, and this change's core follows the
same rule under its own digest — the section gains a second subject rather than a rewritten sentence.

## Impact

Code:

- `tools/loop/core/` (new) — `Json.java` (the issue tooling's reader, copied by value so the two cores depend on
  nothing shared), `LoopCore.java` (the verb dispatch and its unknown-verb refusal), `State.java` (the declared
  fields, the accessors, the writer that refuses an undeclared field, and the six-count query).
- `tools/loop/lib.sh` — `loop_core` (compile-on-demand by digest, one refusal when no compiler is present) and the
  verb wrapper.
- `tools/loop/tests/loop-cases.sh` — the `core#` cases.
- `tools/loop/tests/loop-selftest.sh` — `core_ensure` covers the loop's core beside the issue tooling's, and the
  driver pools a second case source.
- `tools/loop/layout.tsv` — a row per core file once `run-sh-is-split-along-the-stream` has landed the table it
  reads; until then the bound the table carries for `lib.sh` covers it.

Guidelines and documents:

LOOP-ROUTE: guidelines/Backlog.md §7

- `guidelines/Backlog.md`, "## 7. The loop" — names the core as the protocol's one owner.
- `guidelines/Design.md`, "## 12. General engineering principles" — a typed value over a stringly one, with the
  engine's protocol as the worked example. Its native-boundary section is **not** the pattern here, and no line
  above cites it as one.
- `PORTING.md` — "What to copy" gains `tools/loop/core/`; "Prerequisites" keeps its JDK row and states that two
  cores now need it.

Rollback: revert the commit. The scripts' CLIs and exit codes are unchanged, and a reverted tree has no core and
compiles nothing.

Costs to prove by measurement rather than assume:

- **the compile cost** on a cold cache and on a warm one, from the suite's own run (`core_ensure`'s line), because
  the loop repairs its own engine ~10 times a day and `javac` would then be in that path;
- **the invocation count per run**, from the run's own record: the claim is one call per phase, and a run that
  calls the core 50 times is a design error this measurement makes visible.

surface: 4 requirements and 11 scenarios added to 1 delta file, 2 core files plus 1 copied JSON reader, 0 shipped
exit codes changed, 0 `jq` sites retired here (the sibling retires them).
