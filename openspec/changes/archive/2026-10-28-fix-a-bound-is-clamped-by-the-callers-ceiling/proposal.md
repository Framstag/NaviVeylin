# Proposal — fix-a-bound-is-clamped-by-the-callers-ceiling

## Why

**Root cause.** Every bound the loop starts a call under is a recorded project value (`LOOP_CASE_MAX_SECONDS=900`
at `tools/loop/loop.conf:174`, `LOOP_GATE_STEP_MAX_SECONDS=1800` at `tools/loop/loop.conf:187`) and nothing reads
the ceiling of the process that started the call, so a caller whose own per-call ceiling is smaller kills the call
and the child above it mid-work: there is no key, and no reader, for that ceiling (`grep -rn 'CEILING'
tools/loop/*.sh tools/loop-selftest.sh` → no hit).

**The red cases on the base (`c2969186`), 2026-10-28T18:45:56Z** (JUnit-shaped tallies in `evidence/`):

| case | XML | what it reads |
|---|---|---|
| `driver#aSuiteEndsInsideTheCeilingItsCallerNamed` | `tests=1 failures=1` | a caller's ceiling of 3 s named beside four 6 s cases leaves the suite green — `loop-selftest: all 4 cases passed … expected '1', got '0'` — after running the whole 24 s |
| `gate#theStepBoundIsClampedByTheCeilingTheCallerNamed` | `tests=1 failures=1` | the bound in force is `LOOP-GATE-STEP-BOUND: 600s per step` under a caller ceiling of 2 s, and the step the caller would have killed reads green at 30 s |

**Measured on the tracker (issue #692, verified in the tree).** Two of three children started on 2026-10-28 died
with `Error: Tool 'ctx_shell' exceeded its timeout of 900000ms.`, both mid-suite and both with their work
uncommitted; the orphaned suite of the second kept running for 1 383 s after the child was gone. The work was
recovered by hand. `tools/loop/loop.conf` records 900, 1 800, 2 700 and 3 900 s — every one of them at or above that
900 s ceiling, and no project file names the ceiling at all.

**What the item asks for, and what is already there.** The item names three things the project can own. Its (c) —
the rule that a `--files` selection naming `tools/*-cases.sh` is the gate and not the inner loop — is on the base
already (`guidelines/Build.md:144-149`, which cites this very measurement). Its (b) — a way to split a full run into
calls that finish — exists as the suite's own `--case`/`--group` filters (`tools/loop-selftest.sh:12`), which §2a's
"the filter is the inner loop" already routes to. Its (a) — the ceiling named as an input and honoured — is absent,
and it is the one thing a filter cannot supply: one invocation, however narrow, still has to end inside the ceiling
of the call that started it, and the gate's own step is such an invocation.

**Owning requirement.** `agent-issue-loop` — "A self-test case is held to a bound of its own", "A gate step is
bounded, and the step that overran is named", and "A child never outlives the run's deadline" (the run's own
`child_cap` is the one place a ceiling *is* derived rather than assumed).

**Owning guideline.** `guidelines/Build.md` §2a "Agent iteration protocol", rule 2 — "the filter is the inner loop;
a selection naming the case file is the gate", which already states that a caller's ceiling can sit below this
project's own bounds and names no way to honour it.

surface: 2 cases added, 1 guard case added, 3 files changed in the engine, 1 guideline section touched —
`grep -l 'LOOP_CASE_MAX_SECONDS\|LOOP_GATE_STEP_MAX_SECONDS' tools/loop-cases.sh tools/loop/*.sh
tools/loop-selftest.sh tools/*-cases.sh` names three files — `tools/loop-cases.sh`, `tools/loop/gate.sh` and
`tools/loop-selftest.sh` — and those three are the whole of the mechanism this change re-points. The declared case
count is `LOOP-MEASURE-SUITE cases=276` (`tools/loop-selftest.sh --list`, after this change's three cases).

## What changes

1. **`tools/loop/loop.conf` — the ceiling is a key, named by the caller.** `LOOP_CALL_CEILING_SECONDS` records the
   last second the invocation that reads it may still be running, named at the call site through the environment
   (a fill-if-unset assignment, the shape `LOOP_BASE_REF` already uses, so an inherited value wins and an unset one
   takes the recorded empty default). Empty is no ceiling: every bound keeps its recorded value, which is the shape
   this project ran in until now.
2. **`tools/loop/lib.sh` — one reader.** `loop_ceiling` returns the named ceiling in whole seconds or nothing, and
   `loop_bound_under_ceiling <bound>` returns the smaller of a recorded bound and the ceiling. One home, because a
   second copy of the rule is a second rule (D16).
3. **`tools/loop-selftest.sh` — the suite ends itself at the ceiling.** When a ceiling is named, the suite starts a
   watcher for it beside the per-case bound it already starts, and at the ceiling it prints
   `LOOP-SUITE-CEILING: …` naming the ceiling, how many cases started and how many never started, prints its own
   partial summary, ends the cases still running, and exits non-zero. A cut suite is never a green
   suite, and the caller is never the process that ends it.
4. **`tools/loop/gate.sh` — a step's clock is the smaller of the two.** `step_max` is clamped by the ceiling, and
   the `LOOP-GATE-STEP-BOUND:` line on the console says which clock is in force, so the gate ends the step it was
   in and names it in its tally instead of being killed mid-step with nothing said.
5. **`guidelines/Build.md` §2a rule 2** names the input: a caller that starts a suite or a gate states its ceiling,
   and the invocation ends itself inside it.
6. **`tools/loop-cases.sh`** gains the two red cases and the guard that a ceiling the selection fits inside changes
   nothing; `tools/loop-mutations.sh` gains three records pointing at the new assertions.

## What the gate measured

The gate ran once on this change, with `LOOP_CALL_CEILING_SECONDS=1500` named so that the gate would end a step
itself instead of being killed mid-step:

```
LOOP-MEASURE-GATE seconds=1256 suites=17
LOOP-GATE: tests=665 failures=2 errors=0 (tools/loop-selftest.sh --from-git c2969186… 691s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 462s, … tools/issue-tooling-selftest.sh 71s, …)
```

`loop-selftest: 2 of 276 cases failed`, and both are accounted for in `evidence/gate-failures-attributed.md`: one was
this change's own case declaration (repaired, and re-verified green by that case), the other —
`gate#theMapCoversEveryPipelineStep` — is red on the base `c296918` for a pipeline step (`build.yml:394`) whose
command the base's suite map never carried, and is left to a delta of its own. The change's three cases are green
(`evidence/green-suite-ceiling.xml`, `green-suite-ceiling-guard.xml`, `green-gate-step.xml`) and its three mutation
records are red-then-green (`evidence/mutations-70.log`).

## Impact

- Affected files: `tools/loop/loop.conf`, `tools/loop/lib.sh`, `tools/loop-selftest.sh`, `tools/loop/gate.sh`,
  `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `guidelines/Build.md`, `openspec/specs/agent-issue-loop/spec.md`.
- No compiled file, no Android module, no device: the gate this delta owes is the shell suite and the mutation
  check, which is what `tools/loop/gate.sh` runs for a `tools/loop/**` path.
- Additive to the configuration: a profile that records no `LOOP_CALL_CEILING_SECONDS` behaves exactly as before,
  which is why the default is empty rather than a value copied from another host.
- Rollback: revert this change. The suite's behaviour without a named ceiling is byte-for-byte the old one — no
  watcher is started and no line is printed — so nothing a later change reads depends on it.
