# Design — the checks a child runs by hand are bounded while it runs

## D1 The lever: the run reads the child's stream, not the gate the child calls

Three levers move the same work, and the two rejected ones are rejected by what the work *is*:

1. **Refuse a hand-run check inside `gate.sh`.** The gate is a process the child starts; a call the child makes
   directly never enters it, and a check the child runs as `bash tools/loop/tests/loop-selftest.sh --case …` is not
   the gate's call at all. The refusal `LOOP_GATE_RUNS_MAX` already gives cannot see it — that is why issue #857 had
   to price these calls out of the child's stream in the first place.
2. **Price it and report it** — what #857 did, and what the run of 2026-11-13T05:47:11Z measured the cost of: 226
   calls, 1.3 h of the run's 3.1 h gate bucket, and nothing ended. A number a report prints after the fact is a
   measurement, not a bound.
3. **A wrapper the child must call** — a guard script named in the prompt, through which every check is routed. A
   child's tool call is arbitrary text: nothing in the process can make it an argument of a script it is not told to
   pass, and a wrapper the prompt asks for is a rule the child may forget, which is the class of failure the run's
   own bounds exist to remove.
4. **The run reads the count while the child runs**, from the lines its event stream has gained, and ends it past the
   recorded number. **Chosen.** The seam already exists and is proven three times over — turns
   (`LOOP_CHILD_TURNS_MAX<ROLE>`), context (`LOOP_ITEM_CONTEXT_MAX<ROLE>`) and the checkpoint split all read that
   stream each second — and the run already has the means to end a child's process group and to record the cut.

**Cost, stated:** the run pays one `wc` and one `jq` over the *new* lines per second per child while the bound is on,
on a stream that reaches megabytes; `child_check_calls` reads the new lines only, exactly as `child_context` does, so
the cost is the child's own writing rate and not the file's size.

## D2 What is counted: the programs the gate's own checks start, less the gate

A check the gate would not re-run is nobody's to bound. The map is the one `gate.sh --plan` reads
(`LOOP_GATE_SUITES`, `LOOP_GATE_FIRST_STEPS`, `LOOP_GATE_COMPILED_CMD`, each entry `<glob>|<command>`), so nothing here
names a project path and a project that records a different map is bounded by its own checks (D16). Three exclusions,
each with its own reason:

- **A call that only names a path** — `sed -n 1,60p tools/loop/gate.sh`, `git show HEAD:tools/loop/gate.sh`, a `grep`
  over it. These are readings; count them and the child that reads the gate to answer a refusal pays the gate's clock
  for reading a file. The rule is the one issue #830 established for the report's split, and the case pins both halves
  in one transcript: two reads, two checks, a recorded bound of 2, and the line reading `after 2 check(s)`.
- **A `--plan` call** — it runs no step; it is the answer a refused run was handed, and refusing it twice would punish
  the child for asking.
- **The gate's own run** — `LOOP_GATE_RUNS_MAX` owns it, with a refusal the child can read. A second rule over the same
  call would fire first and end the child on a count the gate's own refusal exists to explain.

A focused case (`loop-selftest.sh --case …`) is not in the map either, so `guidelines/BuildGate.md` §2a's iteration —
the red case, the affected group before and after the fix, the mutation set, the delta's structural checks — keeps its
room inside the bound, which is what sets its value.

## D3 Why the number is 10

`10`, from two measurements that bound it from either side:

- The iteration §2a owes a child is **eight calls**: three focused attempts at the red case (a fourth being `blocked`),
  the affected group before and after the fix, the mutation set of the files the change touches, and the structural
  checks the delta owes. Ten leaves two spare, so a child that obeys §2a never meets the bound.
- The state this tree keeps carries 11 items with a `metrics.gateRepeat`: **265 calls, 1.4 h** over them, min 3,
  median 23, p90 38, max 43 **per item**. A child is one phase of one item, so a child that has spent ten is a child
  that has done a whole item's worth of the gate by hand.

`0`, and a profile that records no key, bound nothing: the same rule `LOOP_GATE_RUNS_MAX`, the turn bounds and the
context bounds follow, so a project that wants a wider bound records a number rather than editing a phase.

## D4 Why the definitions moved to `lib.sh`

Two readers ask the same question — *does this command run one of the gate's checks?* — and a second copy is a second
rule: the report would price one set of calls and the run would bound another, which is exactly the class of defect
#830 and #857 were filed as. So `gate_program` moved from `state.sh`, and `gate_check_programs`, `gate_command_jq` and
`child_check_calls` were written once, in `lib.sh`, where both `state.sh` and `run.sh` already source their helpers.

The move has a cost this change pays: four mutation records (`272.1`–`272.3`, `855.1`) quoted text that is now in
`lib.sh`, and a record whose pattern no longer matches its file mutates nothing and reports itself stale
(`loop-mutations: 4 of 357 checked records no longer apply`). They were re-pointed at `lib.sh` and each one re-run
against its own case, so the four invariants are still falsified rather than merely declared.

## D5 The item's result

The child the bound ends is the ordinary bound case: `LOOP-TIMEOUT` in the transcript with the wall clock's own exit
status, the item recorded `cut` with `bound` as its stop reason, no decision label and no comment, and the work the
child left where it left it. The reason this needs no new arm is that the run already ends a child by killing its
process group and writing `ended_by`; the check bound is one more value that variable takes, and the transcript line
names the key, the bound and the count that reached it, as the turn bound's line does — a person reading the item must
not have to re-derive from an exit code which of three bounds fired.

## D6 What is not done

- **No new key per role.** What a child iterates on is the case its own diagnosis named, and that count is the same
  work in every phase, so the bound is one number for every role rather than a role map.
- **No bound on a check the gate would not re-run.** The focused case is the diagnostic iteration §2a asks for; bounding
  it would make the loop's own protocol the thing that ends a child.
- **No retry of a stream that says nothing.** That is issue #882's sibling finding, worked elsewhere; this change does
  not touch the answer path.