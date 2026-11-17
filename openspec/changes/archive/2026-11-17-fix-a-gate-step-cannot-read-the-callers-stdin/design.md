# Design: fix-a-gate-step-cannot-read-the-callers-stdin

## Root cause

`start_step` (`tools/loop/gate.sh:557-585`) starts a step as `( … ) >"$stream" 2>&1 &` with `set -m` around it. The
redirections cover the step's stdout and stderr and leave fd 0 alone, so the step inherits the gate's stdin. When the
gate was started from a shell, that stdin is the terminal; the process group `set -m` gives the step is then a
*background* group of that terminal, and a `read` from a background group raises `SIGTTIN`. Measured 2026-11-16
(issue #947, whose Observed and Evidence lines carry the figures): one base-red step was found in state `T` with its
whole subtree, and neither `kill -CONT` twice nor a third `SIGCONT` after `stty -tostop` resumed it — the read stands,
so the stop is renewed. The gate printed nothing about it and the step was ended by its bound 30 minutes later.

The engine's other child launch is the counter-example in the same repository: `tools/loop/run.sh:2125` ends its line
with `2>&1 < /dev/null &`. The gate's start line is the one place the redirect is missing.

## The fix

One redirect, on the line the step is started from:

```bash
( $cmd; printf '%s\n' "$?" > "$status_file" ) >"$stream" 2>&1 < /dev/null &
```

A step's command, and everything it starts, reads end-of-file. Nothing else changes: the stream, the status file, the
process group and the bound are the ones `315`, `311` and `801` put there.

## Alternatives the evidence eliminated

1. **Resume the stopped child** — `SIGCONT`, repeated, with or without `stty -tostop`. Eliminated by measurement, not
   by argument: on 2026-11-16 `kill -CONT` twice left the subtree in `do_signal_stop` within two seconds, and a third
   after `stty -F /dev/pts/3 -tostop` did the same. A background group's read raises the stop again as soon as it is
   resumed; nothing outside the reading process can undo a stop the terminal keeps applying.
2. **Make the step's group the terminal's foreground group** — the read then succeeds. Impossible: the bound ends a
   step with `loop_end_group` on that group (`tools/loop/gate.sh:623`, the handle issue #311 added), and a process group
   that owns the terminal cannot be signalled from a background shell without taking the terminal from the operator;
   a step ended mid-write would also leave a foreground job the operator's shell no longer owns. It would trade a stop
   for a bound that cannot be enforced, which is the regression `63.2` exists to catch.
3. **Detect the stop and report it** — poll `/proc/<pid>/stat` for the `T` state in the wave, and name the step as
   stopped. Available, and it would have turned 75 minutes into a readable line, but it answers the symptom after the
   fact: the step still spends its whole bound, and the item's wall clock still pays it. It also adds a per-look read of
   every step's subtree to a poll that runs twice a second, and a diagnostic that exists only to describe a state the
   redirect removes.

The three are the ones the root cause leaves; the fix is the only one that removes the state instead of describing or
surviving it, so gate 3 is answered.

## Blast radius

- One line and its comment in `tools/loop/gate.sh`.
- One case, its `# files:` declaration and its dispatch row in `tools/loop/tests/loop-cases.sh`.
- One mutation record (`947.1`) in `tools/loop/tests/loop-mutations.sh`, and `63.1` moved onto the changed line, since a
  record that quotes a line is orphaned by an edit to it (issue #264).
- No Kotlin, Java, C++, Gradle, resource or manifest file: no build, no Android suite.

**Who could notice the difference.** A step whose command reads stdin now reads end-of-file. Every step in the profile
(`tools/loop/loop.conf`) is a suite, a Gradle invocation, a `tools/*.sh` check or the mutation harness; none reads stdin,
and `gate.sh` itself takes its arguments and its profile from outside stdin (`--plan` included). A caller that ever pipes
something into `gate.sh` would keep it on the gate's own fd 0, which the fix leaves untouched — that is the reason the
redirect sits on the step's line and not in a `exec 0</dev/null` at the top of the engine.

## Verification

- `gate#aStepCannotReadTheCallersStdin` — red on the tree without the redirect (the step waits on the fifo until its
  ten-second bound: `evidence/red-on-head.log`), green with it (`evidence/green-case.log`).
- `947.1` removes the redirect and the case must go red; `63.1` still ends the step with the pipe it removed from the
  start line and its own case must go red; both restore green (`evidence/mutation-947-1.log`).
- The declaration and fifo checks the new case reaches: `driver#everyCaseDeclaresTheFilesItCanReach`,
  `driver#noFixtureFifoIsOpenedWriteOnly` (`evidence/driver-cases.log`).
- The full self-test suite of `tools/loop/**`, once, on the tree that lands.
- No on-device measurement applies: this is the loop's own harness, not the app.
