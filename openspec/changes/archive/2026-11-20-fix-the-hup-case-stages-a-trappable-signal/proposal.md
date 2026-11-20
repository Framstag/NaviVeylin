# The HUP case stages the signal it sends

## Why

`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` is red in every whole-suite leg of
`tools/loop/tests/loop-selftest.sh --from-git <base>` and green in every focused run of the same tree. The gate that
recorded it is green in every step but this one: `LOOP-GATE: tests=1259 failures=1 errors=0 … failing (that run's XML is not retained —
it is that change's evidence): tools/loop/tests/loop-selftest.sh --from-git 6b0e01a7`, and the case
printed `a run that took HUP did not end inside 4800 ticks (30 s for each of the 16 case(s) the pool starts beside
this one; status 124) — its stop was '' and its item ''`. The same step in a clean detached worktree at the base is the same single red
(`…/a-containers-child-carries-a-type/evidence/base-red-6b0e01a7-loop-selftest-from-git.log:1224`), so no delta is
charged it, and #928 and #949 already moved the only figure the message names.

**Root cause** — **the gate starts its own steps detached with `nohup`** (`tools/loop/prompts/worker-gate.md:118`:
"the gate is started detached and polled"), `nohup` sets `SIGHUP` to `SIG_IGN`, the disposition is inherited across
`fork` and `exec`, and a shell cannot install a trap for a signal it inherited ignored: `trap … HUP`
(`tools/loop/run.sh:844`) succeeds, prints nothing, and installs nothing. So in the gate's tree the run under test
never had a HUP trap, and the signal this case sends is a signal no process of that run can answer.

Measured on this host (2026-11-20, `evidence/dispositions.log` and `evidence/dispositions-reset.log` of this
change, taken by the probe `evidence/hup-probe.sh`):

- a shell started by `nohup` reads `SigIgn: 0000000000000005` (bit 0 is `SIGHUP`), and a `kill -HUP` of its own pid
  changes nothing — no `TRAPPED` line is printed and the shell lives on to print its `DONE` line;
- the same probe started with `env --default-signal=HUP` after `nohup` reads `SigIgn: 0000000000000004` and prints
  `TRAPPED` and `DONE`: the reset restores the disposition, and only in the process that is started.

The case's premise was the opposite, in a sentence this change corrects
(`tools/loop/tests/loop-cases.sh:16514-16516` before it): "`run.ownerPid` is written after the traps are installed,
so a state that names an owner names a run that would take the signal". The write order is right and says nothing
about the disposition.

**Neither half the item offered survives the tree, and neither is settled by the measurement the item asked for —
that measurement was not taken.** (a) a *stale* `ownerPid`
signalling a stranger: the project is fresh per case and `run.ownerPid` is written once, by `loop_state init` after
the traps (`run.sh:843-846`, `:853-854`); and a stale pid would leave a run that is not this one alive, not one whose
state is empty at its own deadline. (b) the trap ran and recorded nothing because the child it ends is not the child
it owns: `on_exit` writes the stop and the item *after* ending the child and the heartbeat, and both are bounded
(`lib.sh`'s `loop_end_group` 5 s, `loop_end_pid` 2 s), so a trap that ran records within seconds of the signal. At
4800 ticks — 480 s — the case read an empty stop and an empty item. The measurement the item asked for — the pid the
case sent the signal to beside the pid the state names at that instant, and the case's `WAIT_STOP_SEEN`/`WAIT_ITEM_SEEN`
per tick — was not taken; the two halves are eliminated by the construction above and by the red→green differential.

**Red test** — `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, HEAD's case, under the disposition the gate hands it
(`evidence/hup-ignored-suite.sh --case 'run#aHupEndedRunRecordsItsStopAndUnpicksItsItem' --jobs 1` — this session may
not start `nohup`, and `trap '' HUP` before the `exec` hands every process below it the same ignored disposition),
2026-11-20T02:27:01Z: `tests=1 failures=1 errors=0`, 36 s elapsed (`evidence/red-on-HEAD-nohup-case-timed.txt` — the
clock around that one command, whose log is byte-identical to the one beside it), transcribed as
`evidence/red-on-HEAD-nohup-case.xml`; `FAIL … a run that took HUP did not end inside 300 ticks (30 s for
each of the 1 case(s) the pool starts beside this one; status 124) — its stop was '' and its item ''`, exit 1
(`evidence/red-on-HEAD-nohup-case.log`, the case body HEAD ships). It is the gate's message with the gate's figure
removed, at a thirtieth of the time —
30 s instead of 480 s — because the pool is one case wide.

**The falsification that tells the disposition from the wait** — with the reset removed and the assertion left in
place, the same case reds **at its staging assertion**: the deadline's own message is absent from the log and the
disposition's is in it — `FAIL … the run's shell (pid 1196870) ignores SIGHUP, so the trap this case sends its signal
to cannot be installed in it (issue #978)`, exit 1 (`evidence/revert-check-without-the-reset.log`; the log carries no
timestamp of its own, and its mtime is the 2026-11-20T01:32:20Z run `design.md` cites).

**Owning capability**: `agent-issue-loop` — "An interrupted run ends the child it started"
(`openspec/specs/agent-issue-loop/spec.md:1187`), whose scenario "A run a vanished terminal ended takes the same path
and leaves no item looking in progress" names this case (`spec.md:1203-1209`).

**Owning guideline section**: `guidelines/Backlog.recipes.md` section 7b, "What an item costs, and the bounds on it" — the
paragraph "The clock a case keeps is the pool's width, not a duration the host measured"
(`guidelines/Backlog.recipes.md:869`), which is where this case's staging, its clock and its two earlier repairs are
stated.

**Surface** — the live places that assert this case's behaviour, each with the command that counted it and the output
archived beside this change: `tools/look.sh 'aHupEndedRunRecordsItsStopAndUnpicksItsItem' --path tools --files` → 5
matches in 2 files (`evidence/surface-case-name-tools.log`: 3 in `tools/loop/tests/loop-cases.sh`, 2 in
`tools/loop/tests/loop-mutations.sh`); the same call `--path guidelines --files` → 3 matches in 2 files
(`evidence/surface-case-name-guidelines.log`: 1 in `guidelines/BuildGate.md`, 2 in `guidelines/Backlog.recipes.md`);
`--path openspec/specs --files` → 2 matches in 1 file (`evidence/surface-case-name-specs.log`:
`openspec/specs/agent-issue-loop/spec.md`) — 10 matches in 5 files. The tree's other matches are in archived changes'
evidence, which this change does not touch. `tools/look.sh 'run_loop "\$p"' --path tools/loop/tests/loop-cases.sh
--files` finds 169 call sites of the helper this change adds a variant to (`evidence/surface-run-loop-callsites.log`)
— none of them is touched, because only `SIGHUP`'s disposition is inherited as ignored.

surface: 1 case rewritten (run#aHupEndedRunRecordsItsStopAndUnpicksItsItem), 1 helper added to the case file
(run_loop_resetting_hup), 1 case declaration widened (tools/loop/prompts/worker-gate.md, which is what states the
detached start), 1 guideline paragraph extended (section 7b) — `tools/look.sh 'aHupEndedRunRecordsItsStopAndUnpicksItsItem'`;
`tools/look.sh 'run_loop "\$p"' --path tools/loop/tests/loop-cases.sh`

## What changes

1. `tools/loop/tests/loop-cases.sh` — `run_loop_resetting_hup`, a second form of the case runner that starts
   `run.sh` through `env --default-signal=HUP` (a GNU coreutils flag — this host runs coreutils 9.12, and a host
   whose `env` has no such flag cannot start the run that way at all), and the HUP case starts its run through it:
   the signal the case sends is one the run's own shell can trap.
2. `tools/loop/tests/loop-cases.sh` — the case asserts the disposition it staged, from the run's own
   `/proc/<pid>/status`, before it signals, so a tree that loses the reset reds at once naming `SIGHUP` rather than
   spending the deadline on a run no signal can end. The case's premise sentence is corrected, and its `# files:`
   declaration names `tools/loop/prompts/worker-gate.md`, the prompt that says the gate is started detached.
3. `guidelines/Backlog.recipes.md` section 7b — the paragraph that owns this case's clock states the third thing it stages:
   the disposition.

## What does not change

- `tools/loop/run.sh` is not touched: a run started under `nohup` is a run whose `SIGHUP` the operator asked to
  ignore, and the engine has no lever over a disposition it inherited — the reset has to happen where the process is
  started, which is the case.
- The gate's detached start is not touched: `nohup` is what lets the gate outlive the call that starts it
  (`tools/loop/prompts/worker-gate.md`, and the case `prompt#theGatePromptNamesTheWallAndTheWayThrough` asserts the
  prompt names it), and one case may not re-decide it.
- No deadline moves: `stop_wait_deadline` and its pool-width derivation (issues #928, #949) are what this case prints
  and what the item's own measurement did not fault.
- The other 169 `run_loop` call sites are untouched: `SIGHUP` is the only signal a detached suite's children inherit
  as ignored, and the `TERM` case (`run#interruptedRunEndsTheChildItStarted`) passes under `nohup` as it stands.
