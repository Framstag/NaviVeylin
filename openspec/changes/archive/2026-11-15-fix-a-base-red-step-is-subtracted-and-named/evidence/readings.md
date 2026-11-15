# Readings — a step red on the run's base is subtracted and named

Every reading below is from the tree at `929b9e8f` plus this change directory, taken 2026-11-15. Nothing was run,
built or mutated for this change; what a run would have had to produce is named as missing at the end.

## The behaviour the delta states

| what the delta requires | where the tree does it |
|---|---|
| the run measures the base once, before it works anything | `tools/loop/run.sh:833` (block 4b), `:843` (`LOOP_BASE_RED` read from the profile), `:845` (`take_base_red`) |
| the switch, and the four reasons a pre-flight is not taken | `tools/loop/loop.conf:403`, `tools/loop/run.sh:868`–`877` (off / no base / not a commit / uncommitted work) |
| the measurement's own mode, and its switch inside itself | `tools/loop/gate.sh:64` (`--base-red`), `:72`–`82` (plan refused, `LOOP_BASE_RED=off` exported), `:550` (announcement) |
| it writes no tally and spends no item's budget | `tools/loop/gate.sh:106` (`tally_root=""`), `:385` (`runs_max` arm skipped in that mode) |
| the whole step set is owed, a delta's is not | `tools/loop/gate.sh:340`–`342` |
| the pairs it prints | `tools/loop/gate.sh:716` (`LOOP-BASERED-STEP:<cmd>|<n>`), `:719` (`LOOP-BASERED-CASE:<cmd>|<case>`) |
| they are recorded as the run's own base-red record | `tools/loop/state.sh:579` (`cmd_base_red`), `:605` (`run.baseRed`, `run.baseRedBase`), dispatch `:1633` |
| a later gate reads that record | `tools/loop/gate.sh:528` (`base_red_had`), `:532` (`base_red_of`), `:544`–`547` (read from `LOOP_STATE_FILE`) |
| the subtraction is by pair, never by step | `tools/loop/gate.sh:712`–`746` (`shared_cases`, `subtracted`, `base_red_taken`) |
| what it subtracted is printed and quoted | `tools/loop/gate.sh:742` (`LOOP-GATE-BASERED:<step>:<n>`), `:790` (`— base-red: …`) beside the verdict |
| and written to the run's record | `tools/loop/gate.sh:794`, `tools/loop/state.sh:610` (`cmd_base_red_used`), `:626` (`run.baseRedUsed`) |

## The case

`gate#aBaseRedStepIsSubtractedAndNamed` — `tools/loop/tests/loop-cases.sh:6237`–`6323`, registered in the case list at
`:187`, dispatched at `:932`, declaring `tools/loop/gate.sh`, `tools/loop/state.sh`, `tools/loop/run.sh`,
`tools/loop/loop.conf`, `tools/pi-child-events.sh` and `tools/tooling.json`. Its assertions, in the order they run:

| assertion | line |
|---|---|
| the pre-flight names the red step and its count: `LOOP-BASERED-STEP:./suite-one\|1` | `:6274` |
| and the case that failed beside it: `LOOP-BASERED-CASE:./suite-two\|notes#aCaseThatWasRed-two` | `:6276` |
| the pre-flight wrote nothing into the tally directory | `:6279` |
| the run's record carries the case: `run.baseRed` | `:6288` |
| a carrier is not a red gate: exit `0`, `LOOP-GATE-BASERED:./suite-one:1` and `./suite-two:1` | `:6293`–`6295` |
| the tally it subtracted from is the green one: `tests=4 failures=0 errors=0` | `:6296` |
| the verdict quotes what it carried: `base-red: ./suite-one:1, ./suite-two:1` | `:6297` |
| the gate's own log carries the line too | `:6298` |
| the run's record says what the gate subtracted: `run.baseRedUsed.steps` | `:6300` |
| a step red for a case the base never had is still red: exit `1`, `— failing: ./suite-two`, `tests=5 failures=1` | `:6314`–`6321` |
| that step's own pair leaves the tally, and the subtraction is not widened: `LOOP-GATE-BASERED:./suite-two:2` never printed | `:6317`–`6320` |

## The mutation

None. `tools/loop/tests/loop-mutations.sh` holds no `921.*` record (`grep -n '921' tools/loop/tests/loop-mutations.sh`
→ no match at `929b9e8f`). The falsification exists only in the commit's message, which states two hand-made
mutations: the case fails on a tree without `--base-red` (`unknown argument --base-red`), and with the subtraction
guard removed a step red for a new case reads green (`expected '1', got '0'`). `tools/loop/tests/loop-mutations.sh
--check` therefore cannot replay this change's falsification.

## The gate verdict that shipped it

`8cd97718`, quoted from its message: `Gate: 3 of 443 cases red, each named` — the change's own
`driver#theSharedNamesAreDeclared` (a local named `count` read as a shared name; re-run green), one identical on the
base tree (`phase#oneCallPrintsThePhaseState`), one a load flake under sibling suites
(`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, green when run alone). That is a gate with named reds, not a
claim of a clean run, and this change directory does not re-run it.

## The owning guideline

`guidelines/BuildEvidence.md:118` §4d "Attributing a red run", the rule at `:137`–`147`: the pre-flight and its
switch, the pair-wise subtraction, the two lines, the two cases that stay red (a step red for a case the base never
had; a step that fails without naming a case where the base named none), and the sentence that a step left with none
of its own red counts green with the red quoted.