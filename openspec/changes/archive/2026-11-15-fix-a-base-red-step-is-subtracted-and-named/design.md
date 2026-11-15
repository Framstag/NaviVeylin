# Design — a step red on the run's base is subtracted and named

## The fault

A run starts from a commit, and that commit can already be red. Five items of one run went red at steps no change of
theirs touched, each was owed a gate re-run it could not earn, and the run's green rate read as their fault
(`tools/loop/run.sh:833`–`838`). Nothing in the engine asked *which tree* a red belonged to: `state.sh` recorded the
item's tally, `report.sh` totalled the run's gate lines, and the tree the run started from was not measured at all.

## The single measurement

One measurement, taken once, recorded once, read by the gate that subtracts from it.

1. **`tools/loop/run.sh` measures the base before it works anything.** Block 4b (`:833`–`878`) runs
   `gate.sh --base-red --base "$base"` into `<state dir>/base-red.log`, and `state.sh base-red --from <log> --base
   <commit>` records the pairs as `run.baseRed` (`tools/loop/state.sh:579`, `:605`). It runs after the checkout guard —
   a run started on the wrong branch is refused before it spends the pre-flight's minutes — and only where it can read
   a base: `LOOP_BASE_RED=off` (`tools/loop/loop.conf:403`), no base, a base this tree cannot read, or a tree with
   uncommitted work each print the reason it was not taken, and each records nothing. A status other than `0|1` is the
   pre-flight failing to run, and a set it could not measure must not be read as a base that was green (`:855`–`858`).
2. **`tools/loop/gate.sh --base-red` is the gate asked one other question** (`:64`, `:72`–`83`). It owes the profile's
   whole step set, not a delta's, because at the run's start no delta is known (`:340`–`342`); it writes no tally and
   holds no lock (`:106`), because it is not the item's measurement and must not spend the runs the profile records for
   the child; it refuses `--plan` beside itself (one call is one of the two); and it runs its own steps with
   `LOOP_BASE_RED=off` exported, since a suite of this tree starts runs and a run started inside one would take a second
   pre-flight of its own (`:82`). Its pairs are `LOOP-BASERED-STEP:<cmd>|<n>` and `LOOP-BASERED-CASE:<cmd>|<case>`
   (`:716`, `:719`), and its own verdict line is `LOOP-BASERED:` (`:798`).
3. **A later gate subtracts by pair, never by step** (`:712`–`746`). A case the step failed on the base and fails again
   leaves the tally; a case it fails only now stays; the step is a carrier — green — only when nothing of its red is
   left. A step that fails naming no case is the base's own only when the base named none there either. What left is
   printed as `LOOP-GATE-BASERED:<step>:<n>` (`:742`), appended to the verdict as `— base-red: …` (`:790`), and written
   into the run's record through `state.sh base-red-used` → `run.baseRedUsed` (`:794`, `:610`, `:626`) — so a report
   reads which reds were carried instead of charging the item and re-reading a log.
4. **`guidelines/BuildEvidence.md:118` §4d** ("Attributing a red run", `:137`–`147`) states the rule.

## Why the requirement is ADDED, not MODIFIED

No shipped requirement in `agent-issue-loop` states that a base red is subtracted. The nearest shipped text,
`openspec/specs/agent-issue-loop/spec.md` on `metrics.gateAttribution`, records *which tree fails the case a red gate
names*, and that field keeps its exact meaning for the reds the subtraction does not take — a step failing for a case
the base never had, and a step failing with no case name where the base named none. Rewriting it to absorb the
subtraction would make one field answer two questions and would drop a scenario it still owes. The capability that owns
it is `agent-issue-loop` and not `build-test-gate`: the verdict, the step set and the run record are stated there
(`:2732`, `:4884`), while `build-test-gate` states the project's gate — its flavors, its evidence, its compiled
predicate — and names no run tally.

## Alternatives the evidence eliminated

- **Charge the base red and read it afterwards** — what the engine did: the item pays a re-run for a red it did not
  introduce, and no field tells the reviewer which reds were whose.
- **Take the pre-flight on every gate instead of once per run** — thirteen minutes of the profile's whole step set per
  gate, and the answer cannot change while the run works; the run takes it once, and `LOOP_BASE_RED=off` is the switch
  a project pays nothing with.
- **Subtract per step** — a step red on the base for one case and red now for another would read green, and a delta
  that breaks a new case in a step the base already reddened would be recorded clean. The case set is the pair, and
  the case set is what leaves.
- **Read the base from the tree's working copy** — a tree with uncommitted work is not the commit the run starts from,
  so the pre-flight would subtract reds the base does not carry; the run refuses to take it there and says why.
- **A second derivation of the failing cases in `run.sh`** — the gate already parses its own output; the pre-flight
  writes its pairs and the run records them, so one parse serves both.

## Blast radius

- `tools/loop/gate.sh`: one argument, one mode, one subtraction loop, two `LOOP-BASERED-*` lines, one verdict note, one
  `state.sh` call. The owed list, the waves, the bounds, the tally from a delta's gate and the mode's meaning are
  unchanged; a `--base-red` call answers exit `1` for a base that carries a red step and `0` for one that carries none —
  both a measurement — and any other status is the pre-flight failing to run (`tools/loop/run.sh:853`–`858`).
- `tools/loop/run.sh`: one block, one function, four refusal reasons, one recorded field. Nothing else in the run reads
  `run.baseRed` except the gate that subtracts from it.
- `tools/loop/state.sh`: two commands, `base-red` and `base-red-used`, and two fields, `run.baseRed` and
  `run.baseRedUsed`.
- `tools/loop/tests/loop-cases.sh`: one case,
  `gate#aBaseRedStepIsSubtractedAndNamed` (`:6237`–`6323`), whose assertions cover the pre-flight's two lines, the tally
  it must not write, the record it must write, the carrier's exit `0` and verdict, and the two reds that stay.
- The guidelines: §4d of `BuildEvidence.md` gains the rule.

## Rollback

`git revert` of `8cd97718`: `run.sh` takes no pre-flight, `gate.sh` refuses `--base-red` as an unknown argument, and a
state written with `run.baseRed`/`run.baseRedUsed` reads on that tree exactly as it did before — both fields are
written and read only inside the feature, and `report.sh` prints neither.

## The measurement, and its limits

The pre-flight costs the profile's whole step set on the base tree, which is the run's own cold cost at its start
(`tools/loop/run.sh:840` names the order of minutes); nothing here measures a warm one, because the run starts on a
tree the previous run left warm only when it keeps one (D4).

The falsification is not a mutation record: `tools/loop/tests/loop-mutations.sh` holds no `921.*` entry, and the two
hand-made mutations that produced the case live only in `8cd97718`'s message — without `--base-red` the gate answers
`unknown argument --base-red`, and with the subtraction guard removed a step red for a new case reads green
(`expected '1', got '0'`). The gate that shipped the case read `3 of 443 cases red, each named` — its own
`driver#theSharedNamesAreDeclared`, one identical on the base tree, and one load flake under sibling suites — so the
change's evidence is a named red gate and a case that has run green alone, not a clean gate.

## Open decisions

none