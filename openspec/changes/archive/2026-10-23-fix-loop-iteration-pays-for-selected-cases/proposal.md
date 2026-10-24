# An iteration pays for the cases it selected, and the gate records what each suite cost

**Verified in the tree at the base commit `abeffcad`.** `tools/loop-selftest.sh:13`–`19` parses three arguments —
`--list`, `--case <group#name>`, and nothing — and `--case` compares its value against the declared set with
`grep -qxF` (`:139`–`:150` before this change): one name, exactly, no pattern and no group. Everything else runs
every declared case in one strict sequence (`tools/loop-selftest.sh:155`–`166`), and `tools/loop/gate.sh:131`–`168`
runs each owed suite the same way while printing only its command, never its duration.

**Measured, 2026-10-23.** `tools/loop-selftest.sh` is 178 cases, and the recorded cost of 163 of them was **723 s
(12 min), strictly one after another** (issue #260, measured 2026-10-22; the 66 `run#*` cases are 481 s of it).
`tools/loop-mutations-selftest.sh` is **82 s** for its 34 cases and `tools/loop-mutations.sh --check` **1 s** for
its 99 records, measured today. A delta under `tools/loop/**` therefore owes about **13.5 min** of gate, of which
89 % is the self-test — and the gate cannot say so: it names the suites it ran and nothing about what they cost, so
"the self-test costs twelve minutes" had to be established with a stopwatch and a `ps` on a running process
(`guidelines/Build.md` §2c records the timing of the gate as a maintained number, and the loop's gate was the one
place that did not produce it).

**What that cost in practice.** Item #261 (two files, `tools/loop-selftest.sh` and `tools/loop-cases.sh`) paid
**eight** gate runs in one item — 7 red, then green — and its 30-minute phase A had 9 min 49 s in one of them when
it was first inspected. With `--group driver` (3 cases, ~7 s) and `--group usage` (4 cases, ~6 s), six of those
eight runs could have cost seconds: the work was in one group of the driver, not in all 178 cases.

**The structural fixes stay where they are filed.** Parallelising the cases is issue #260 and lowering the
one-second poll inside `run_child` is issue #259; both are bigger than this change and neither is a prerequisite
for the selection. This change makes an iteration *narrow* and its cost *visible*, which is what lets #259 and #260
be measured afterwards.

## Why

- **Root cause.** The driver's selection is exact-match on one name. A worker that knows which group it broke —
  the driver's own `driver#` cases, the gate's `gate#` cases, the engine's `run#` cases — has no way to say so,
  so iterating on any of them costs the whole suite.
- **Second root cause.** `gate.sh` measures nothing it runs. The suite that costs 723 s and the one that costs 1 s
  are indistinguishable in its output, in the tally it writes, and in the gate claim the run records, so an
  optimisation of the suite has no baseline and no feedback inside the loop that would pay for it.
- **Evidence.** The red-on-HEAD runs are in `evidence/red-HEAD.log`: `driver#aGroupFilterRunsOnlyItsOwnCases`,
  `driver#aFilterThatNamesNoCaseRefuses`, `driver#theListIsFilteredTheSameWayASuiteRunIs` and
  `gate#theSummaryNamesWhatEachSuiteCost` all FAIL against the driver and gate of `abeffcad`, each with the reason
  the missing behaviour gives.
- **The single fix.** `--group <group>` and a glob in `--case` filter the declared set before anything runs, a
  filter that names no case is refused with exit 2, `--list` applies the same selection, and `gate.sh` records each
  suite's wall clock in its per-suite line, its summary and its tally.

**Owning guideline section.** `guidelines/Build.md` §2a "Agent iteration protocol", rule 2 — *iterate with
focused suites, gate once*. The protocol asks for the focused iteration; the driver had exactly one focused
selection (one exact case name) and no group. §2c owns the timing record. Neither section states a number that
this change alters, so no guideline sentence is rewritten.

## What Changes

- `tools/loop-selftest.sh`: `--group <group>`; `--case` accepts a pattern beside a name; both filters may be
  combined; each filter is checked against the whole declared set before anything runs and refused with exit 2 when
  it names nothing; `--list` prints the selection; a filtered run names its selection in its summary line; the
  single-literal-`--case` invocation keeps its exact output (one `PASS` line, no summary) because the mutation
  harness and the leftovers cases read precisely that shape.
- `tools/loop/gate.sh`: each suite's wall clock (`$SECONDS` delta) is printed on the suite's own line, carried in
  the `LOOP-GATE:` summary beside the command that spent it, and written as the tally's `time` attribute.
- `tools/loop-cases.sh`: four cases (`driver#aGroupFilterRunsOnlyItsOwnCases`,
  `driver#aFilterThatNamesNoCaseRefuses`, `driver#theListIsFilteredTheSameWayASuiteRunIs`,
  `gate#theSummaryNamesWhatEachSuiteCost`) and the `driver_selection_probe` helper they share.
- `tools/loop-mutations.sh`: records `49.1`, `49.2`, `49.3` — one per new invariant.
- `openspec/changes/fix-loop-iteration-pays-for-selected-cases/**`: this change.

## Impact

- Affected files: `tools/loop-selftest.sh`, `tools/loop/gate.sh`, `tools/loop-cases.sh`,
  `tools/loop-mutations.sh`. No compiled file, no Android module, no manifest, no resource, no native code.
- Modules: none. This is the loop's own engine under `tools/loop/**` and its gate.
- Previous specifications touched: `openspec/specs/agent-issue-loop/spec.md` — one **ADDED** requirement. No
  shipped requirement is modified or removed.
- Guidelines referenced: `guidelines/Build.md` §2a (iterate with focused suites, gate once), §2c (gate timing
  record), §4a (the verdict and the warnings); `guidelines/Backlog.md` §9 (checks). None is contradicted.
- **Additive, not breaking.** Every existing invocation keeps its meaning: the bare run is the whole suite, an
  exact `--case` is one case with the same output, `--list` is the declared set. Rollback: revert the four files;
  no state, no data and no tracker item depends on the new flags.
- Scope: the agent-issue-loop tooling only. It does not affect the phone UI, Android Auto or the Automotive build.

## Follow-ups (not this change)

- The file→case mapping that would let a change select the cases its own touched files own — the shape
  `tools/loop-mutations.sh` already uses per record (`label@fifo@file@case@…`), filed as its own item.
- Issue #260 (a bounded pool over the cases) and #259 (sub-second polling in `run_child`).
