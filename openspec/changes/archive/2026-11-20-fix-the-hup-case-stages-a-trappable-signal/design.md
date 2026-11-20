# Design — the disposition is staged where the run is started

## Root cause

The gate starts its steps detached, and detachment is spelled `nohup` (`tools/loop/prompts/worker-gate.md`: "the gate
is started detached and polled", its wall and the way through it stated in the prompt the gate child reads). `nohup`
sets `SIGHUP` to `SIG_IGN` **and that disposition is inherited through every `fork` and `exec` below it**. POSIX gives
a shell no way to trap or reset a signal it was started with ignored: bash's `trap 'on_signal 129 HUP' HUP`
(`tools/loop/run.sh:844`) returns success and installs nothing. So in the gate's own tree the run under test has no
HUP trap, `kill -HUP` against it does nothing to it, and the case that sends it waits: its state keeps the
`in-progress` item and records no stop, exactly the `its stop was '' and its item ''` the gate printed at 4800 ticks.
Focused, the same case runs under a shell that does not ignore `SIGHUP`, the trap installs, and it passes.

The measurement (`dispositions.log`, `dispositions-reset.log` in this change's `evidence/`): the same probe shell reads
`SigIgn: 0000000000000005` under `nohup`, and a `kill -HUP` of its own pid changes nothing — no trap runs and the shell
lives on to print its `DONE` line; started as `env --default-signal=HUP` under `nohup` it reads
`SigIgn: 0000000000000004` and its trap runs.

## The alternatives the evidence eliminated

- **A stale `ownerPid`, the item's half (a).** The project is created per case (`new_project`), and `run.ownerPid` is
  written by `loop_state init` (`run.sh:853-854`) after the traps (`run.sh:843-846`). A pid read from a state that
  names a stranger would leave *this* run alive and *that* stranger alive too; here the run whose state the case read
  was the one still alive at its deadline. And the disposition finding explains the same readings with no race at all.
  The measurement the item asked for — the pid the case signalled beside the pid the state names at that instant, and
  the case's `WAIT_STOP_SEEN`/`WAIT_ITEM_SEEN` per tick — was not taken; this elimination rests on the construction
  above and on the red→green differential, not on it.
- **The trap ran and the child it ends is not the one it owns, the item's half (b).** `on_exit` ends the child group
  and the heartbeat *before* it writes the stop and the item, and both endings are bounded (`lib.sh`: 25 × 0.2 s then
  `KILL`, 10 × 0.2 s then `KILL`). A trap that ran therefore records within seconds of the signal, not 480 s later, and
  records something.
- **A deadline that is too small, the repair #928 and #949 already made.** 480 s is 96 times the case's whole focused
  runtime; the item's own measurement faults no figure, and moving it again would hide a trap that cannot exist rather
  than let it land.
- **Changing the gate's detach mechanism** (`setsid` instead of `nohup`, or no detachment). The detached start is what
  lets a gate outlive the call that starts it, it is asserted by
  `prompt#theGatePromptNamesTheWallAndTheWayThrough`, and one case may not re-decide how every item's gate is started.
- **Resetting `SIGHUP` in `run.sh`.** A shell cannot: the disposition is ignored before `run.sh` is entered, and the
  shell it is written in has no lever over it. The reset has to happen in the process that is started.

## The single fix

One rule: **a case that signals a run stages the signal's disposition, as it stages the item's silence and the
child's pid.**

1. `tools/loop/tests/loop-cases.sh:1534-1539` — `run_loop_resetting_hup`, the case runner started through
   `env --default-signal=HUP`, so the process that becomes `run.sh` begins with `SIGHUP` at its default and its own
   trap installs. A variant rather than a change to `run_loop` (169 call sites,
   `evidence/surface-run-loop-callsites.log`): the disposition is a fact about the signal a case sends, and only this
   case sends `SIGHUP`. The lever is a GNU coreutils one — `env --default-signal`, which this host's coreutils 9.12
   has; a host whose `env` lacks the flag cannot start the run this way at all.
2. `tools/loop/tests/loop-cases.sh:16501-16508` — the HUP case starts its run through it, and its premise sentence is
   corrected: the state's owner is written after the traps, which says nothing about the disposition.
3. `tools/loop/tests/loop-cases.sh:16531-16543` — the case asserts the disposition it staged, from the run's own
   `/proc/<pid>/status` `SigIgn` mask (bit 0 is `SIGHUP`), before it signals; a host without `/proc` skips the
   assertion instead of guessing. Losing the staging now reds the case at once, naming the disposition, rather than
   spending the pool's clock.
4. `tools/loop/tests/loop-cases.sh` — the case's `# files:` declaration names `tools/loop/prompts/worker-gate.md`, the
   file its new comment reaches and the one that states the detached start.
5. `guidelines/Backlog.recipes.md:869-914`, section 7b — the paragraph that already owns this case's clock and staging states
   the third stage: the disposition, the measurement, and why the reset lives in the case and not in the engine.

## Blast radius

One case file, one guideline paragraph, one change directory. No engine script, no profile value, no spec behaviour
other than the requirement the case is the evidence for. The other 169 `run_loop` call sites are untouched, and the
`TERM` path is untouched: `nohup` ignores `SIGHUP` only, and `run#interruptedRunEndsTheChildItStarted` passes under it
as it stands (`evidence/check-term-case-nohup.log`).

## Rollback

Revert the three hunks in `tools/loop/tests/loop-cases.sh` and the guideline paragraph; the case returns to waiting on
a HUP that cannot be taken when the suite is detached, and the gate's leg returns to the red this item was filed on.
Nothing the engine reads or writes depends on the staging.

## Falsification

With the reset removed and the assertion left in place, the case reds at its staging assertion under `nohup`:
`FAIL … the run's shell (pid 1196870) ignores SIGHUP, so the trap this case sends its signal to cannot be installed in it
(issue #978)` (`evidence/revert-check-without-the-reset.log`; the log carries no timestamp of its own, and its mtime is
the 2026-11-20T01:32:20Z run). The pair is the proof that the
assertion —
not the deadline — is what now holds the mechanism.
