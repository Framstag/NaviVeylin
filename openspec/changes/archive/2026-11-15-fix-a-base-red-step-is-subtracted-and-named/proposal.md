# A step red on the run's base is subtracted from the item's tally and named

## Why

Issue **#921**. The loop gate charges an item for every red its own tally carries, whatever tree caused it. Five
items of one run were charged for a step no change of theirs touched, each owed a gate re-run it could not earn, and
the run's green rate read as their fault. `guidelines/BuildEvidence.md` §4d already states the rule the record needs
— "a case that also fails at the run's base commit … is a pre-existing red rather than a finding about the delta"
(`guidelines/BuildEvidence.md:125`) — but the engine only made that red *readable* after the fact
(`metrics.gateAttribution: base`, item #853). A red the base carries was still counted against the item.

The shipped repair is the rule turned into arithmetic: the run measures the base once, before it works anything
(`tools/loop/run.sh:833`, block 4b), records the step-and-case pairs as `run.baseRed`
(`tools/loop/state.sh:579`, `cmd_base_red`), and every later gate subtracts exactly those pairs from its tally
(`tools/loop/gate.sh:712`–`746`), prints `LOOP-GATE-BASERED:<step>:<n>` on the console and in its own log
(`tools/loop/gate.sh:742`) and quotes the red beside the verdict (`:790`, `— base-red: <step>:<n>`). What the gate
subtracted is written to the run's record as `run.baseRedUsed` (`tools/loop/gate.sh:794`, `tools/loop/state.sh:610`).

Shipped on the tree as commit `8cd97718` (merged by `3eb63c95`).

**Root cause, in one sentence.** The gate's verdict was the sum of its steps' numbers with no term for the red the
base already carried, so the only way a pre-existing red could reach the record was as a block on the item.

**The owning capability is `agent-issue-loop`, not `build-test-gate`.** Read, not assumed: the loop gate's verdict,
its step set and the run's record are stated in `openspec/specs/agent-issue-loop/spec.md` — "A gate step is bounded,
and the step that overran is named" (`:2732`), "The gate measures the diff from a base this tree resolves" (`:4884`),
"An item's record keeps what the run measured to decide it" (`:4160`, the `metrics.gateAttribution` field this rule
sits beside). `openspec/specs/build-test-gate/spec.md` owns the *project's* gate — the flavors and suites a run must
cover, the verdict's warnings and failures, the artifacts and coverage it demands — and names no loop tally, no
`LOOP_*` line and no run state. The change is therefore named for the behaviour, under `agent-issue-loop`, and not
`fix-issue-921-…` as the dispatch would have named it had `build-test-gate` owned it.

**ADDED, not MODIFIED, and why.** No shipped requirement states that a red the base carries is subtracted. The
nearest, "An item's record keeps what the run measured to decide it" (`:4160`), owns the field that makes such a red
readable *after* it has blocked the item (`metrics.gateAttribution: base`) and the shipped scenarios
`run#aRedGateIsAttributedToTheTreeThatFailsIt` / `state#aCloseOverARedGateWithoutAWaiverIsRefused` remain the
behaviour of a gate that is still red. #921 changes what the tally *is*, and #853's field keeps its meaning for the
reds subtraction does not take: the two are one rule with two halves, and the half that is new has no shipped
requirement to modify.

**Surface**, counted on this tree (`grep -rn` over `tools/` and `guidelines/`, 2026-11-15): `baseRed` 17 matches in 6
files (`tools/loop/run.sh`, `tools/loop/gate.sh`, `tools/loop/state.sh`, `tools/loop/loop.conf`,
`tools/loop/tests/loop-cases.sh`, `guidelines/BuildEvidence.md`), `LOOP_BASE_RED` 7 in 4 files,
`LOOP-GATE-BASERED` 10 in 4 files. The change adds one requirement and four scenarios to a new delta, touches no code.

**The case** is `gate#aBaseRedStepIsSubtractedAndNamed` (`tools/loop/tests/loop-cases.sh:6237`, registered at `:187`,
dispatched at `:932`). It builds a throwaway project whose two steps are both red at the base and asserts, in order:
the pre-flight names each red step and its failing case (`LOOP-BASERED-STEP:./suite-one|1`,
`LOOP-BASERED-CASE:./suite-two|notes#aCaseThatWasRed-two`) and writes nothing into the tally directory; `state.sh
base-red` records the pairs as `run.baseRed`; the gate over a change that touches neither script exits **0** with the
two `LOOP-GATE-BASERED:` lines, a green tally (`tests=4 failures=0 errors=0`), the `— base-red: …` note beside the
verdict, the line in the gate's own log and the pairs in `run.baseRedUsed`; and a step red for a case the base never
had is still red (`tests=5 failures=1`, `— failing: ./suite-two`), with that step's own base pair subtracted and no
wider subtraction (`LOOP-GATE-BASERED:./suite-two:2` never printed).

**What is not established.** The change carries **no mutation record**: `tools/loop/tests/loop-mutations.sh` holds no
`921.*` entry (`tools/mutate.sh`-style refusal is why `--check` cannot name it), so the falsification is the commit's
own prose (`8cd97718`: "the case fails on the tree without the new `--base-red` argument (`unknown argument
--base-red`), and with the subtraction guard removed a step with a new failing case reads green (`expected '1', got
'0'`)"), and it is not a record `tools/loop/tests/loop-mutations.sh --check` can replay.

**The gate verdict that shipped it** was not clean and said so (`8cd97718`): `3 of 443 cases red, each named` — one
the change's own (`driver#theSharedNamesAreDeclared`, a local named `count` read as a shared name; re-run green), one
identical on the base tree (`phase#oneCallPrintsThePhaseState`, the 4 000-character phase cut landing on the `PACKAGE`
line), one a load flake under sibling suites (`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, green alone).

surface: 1 requirement and 4 scenarios added to 1 delta file, 0 shipped requirement bodies rewritten, 0 source files
changed by this change directory — `grep -c '^#### Scenario:'
openspec/changes/fix-a-base-red-step-is-subtracted-and-named/specs/agent-issue-loop/spec.md` → 4; the behaviour it
states is read back with `grep -rn 'LOOP-GATE-BASERED' tools/ | wc -l` → 9 in 3 files.