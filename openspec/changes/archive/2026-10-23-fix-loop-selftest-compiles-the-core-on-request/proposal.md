# The self-test compiles the issue tooling's Java core only when the case it runs asks for it

## Why

**Verified in the tree at the base commit `abeffcad`.** `tools/loop-selftest.sh:95` compiled
`tools/issues/core/*.java` with `javac --release 17` into the suite's scratch tree and handed the directory to every
case through `ISSUES_CORE_CLASSES` (`tools/loop-selftest.sh:107`) — and it did so *above* the point where the case
list exists: `source "$root/tools/loop-cases.sh"` was `tools/loop-selftest.sh:114`, and the `--case` selection is
only compared against that list at `tools/loop-selftest.sh:139`–`150` (`:155`–`166` after this change). The compile
was therefore paid by every invocation, including a `--case` run whose one case never asks for the core.

**Root cause, one sentence.** The compile sits above the case list, so it runs before the driver knows which cases
the invocation selects, and a selection that asks for no core still pays for one.

**Measured, 2026-10-23T10:03:40Z.** The compile itself is 0.72 s and 0.82 s of wall clock over two runs on this host
(`javac --release 17 -d "$(mktemp -d .pi/tmp/javac-measure.XXXXXX)" tools/issues/core/*.java` → `0,818 total` / `0,724 total`; 486 % and 528 % CPU);
the item's own measurement of a `--case` invocation on 2026-10-22 was 1.0 s. The cost is the driver's, not the
case's: `driver#aCaseThatAsksForNoCoreCompilesNothing` shows that the compile is the *only* thing between a
`--case` run of a shell-only case and its result.

**The red test.** `driver#aCaseThatAsksForNoCoreCompilesNothing` (`tools/loop-cases.sh`, added by this change):
`javac` on `PATH` is a probe that records its invocation and exits 1, and the driver is asked for one case that
only drives `tools/loop/state.sh` (`state#initAndShowRoundTrips`). On HEAD the driver compiles before it reads the
case list, so the run is refused for a compile nothing used. Taken against the driver at
`abeffcad45997a34b96f45e32486b178517c918e`, 2026-10-23T10:00Z:

```
  a case that asks for no core runs without one: javac: this probe does not compile anything
loop-selftest: the issue tooling's Java core does not compile: expected '0', got '1'
FAIL driver#aCaseThatAsksForNoCoreCompilesNothing
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

The change has a second case, `driver#aCaseThatAsksForTheCoreGetsItOrRefuses`, which is red on HEAD for the other
half — a case has no way to ask for the core at all, so it cannot be handed one
(`evidence/red-HEAD-core-request.xml`, `tests="1" failures="1"`). Its refusal half (a `javac` that fails ends the
suite with exit 1 and the driver's own line) is green on HEAD and stays as the guard that the laziness removed
nothing.

**Owning requirement.** `agent-issue-loop`. The spec owns what the loop's own checks do — its driver is the suite
the requirement "A suite ended by a signal removes its scratch tree" and its neighbours are asserted through — and
this delta adds the one requirement that owns what the driver compiles, and when.

**Owning guideline section.** `guidelines/Build.md` §2a "Agent iteration protocol", rule 2 ("Iterate with focused
suites, gate once"): the `--case` run *is* the focused iteration the protocol asks for, and a fixed second of a
compile nothing uses is what that iteration pays. The protocol states the rule and quotes no measurement
(`guidelines/Build.md:103`–`105`), so no guideline sentence asserts the compile and none is rewritten.

## What Changes

- **`tools/loop-selftest.sh`** — the eager compile and the `ISSUES_CORE_CLASSES` export become one function,
  `core_ensure` (`tools/loop-selftest.sh:105`): it compiles once per suite into the suite's `classes` directory,
  records that beside it (`tools/loop-selftest.sh:97`–`98` name both paths for the driver, `readonly`), and exports
  the class directory to the caller. The refusal is unchanged in wording, exit status and audience — `exit 1` and
  `loop-selftest: the issue tooling's Java core does not compile` — it now fires for the case that asked rather
  than for every invocation. The environment checks above it (`javac` present, JDK feature ≥ 17, exit 2 with a
  named message) stay eager: they are the suite's prerequisites, not the core's compile.
- **`tools/loop-cases.sh`** — every place a case starts the engine or the core asks for it first: the `run_loop`
  wrapper (`tools/loop-cases.sh:478`), the five cases that start `run.sh` directly
  (`tools/loop-cases.sh:756`, `:765`, `:792`, `:805`, `:1960`), the one that runs `tools/issues/update.sh`
  (`tools/loop-cases.sh:1994`), and the one that runs `WorkPackage` through
  `java -cp "$ISSUES_CORE_CLASSES"` (`tools/loop-cases.sh:2214`). The file's header
  (`tools/loop-cases.sh:12`) says what the driver now hands a case instead of "already compiled by the driver".
- **`tools/loop-cases.sh`** — the two cases above, added at the end of the case list, plus the driver's own
  function being available to a case is what the second one asserts.
- **`tools/loop-mutations.sh`** — record `48.1`, which restores the eager compile, so the new invariant is
  falsified by a mutation like every other (red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0).
- **the spec `agent-issue-loop`** — one ADDED requirement, "The self-test compiles the Java core when a case asks
  for it", with its two scenarios naming the two cases.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned — `grep -rn "javac --release 17\|ISSUES_CORE_CLASSES" tools/loop-selftest.sh tools/loop-cases.sh tools/loop/ tools/loop/fixtures`

Surface, before the plan was fixed: `grep -rn "javac --release 17\|ISSUES_CORE_CLASSES" tools/loop-selftest.sh
tools/loop-cases.sh tools/loop/ tools/loop/fixtures` → the compile at `tools/loop-selftest.sh:95` and its export at
`tools/loop-selftest.sh:107` (base commit `abeffcad`'s numbering), the cases-file header at
`tools/loop-cases.sh:12` and its one direct consumer at `tools/loop-cases.sh:2207` (now `:2214`); nothing under
`tools/loop/` (engine), `tools/loop/prompts/` or `tools/loop/fixtures/` names either, and no existing case asserts
that the core was compiled — **2 cases added, 0 rewritten, 0 fixtures realigned**.

Five other drivers precompile the same core at the top of their own file
(`tools/issue-tooling-selftest.sh:49`, `tools/sensor-selftest.sh:38`, `tools/resolve-stale-selftest.sh:50`,
`tools/check-portability-selftest.sh:71`, `tools/issues/metadata-mutations.sh:45`) and are **not** changed here:
their case sets are the issue tooling's own — `core` (`tools/issue-tooling-cases.sh:245`), `core_class`
(`tools/sensor-cases.sh:165`), `resolver` (`tools/resolve-stale-cases.sh:192`), the real scripts in
`tools/check-portability-selftest.sh` — so a selection of theirs that asks for no core does not exist, and the
`--case` run this item measured is the loop's driver, whose cases are mostly shell. The delta stays inside the
loop's own driver.
