# Design — fix-gate-step-ends-with-its-command

## Root cause

`tools/loop/gate.sh:166` on the base run each step as `$cmd 2>&1 | tee "$stream"`, and `:170` took the step's wall
clock after that pipeline. A pipeline's last command is `tee`, which reads until EOF; EOF arrives when every writer
holding the write end of the pipe is gone. A suite's own exit therefore did not end the step — the last process the
suite had started did. Measured 2026-10-26 (the item's evidence, `.pi/issue-loop/items/255/gate-round2.log`): a
`tools/loop-selftest.sh` step printed `loop-selftest: all 232 cases passed` 565 s in and its own line read
`— 1237s` against a `LOOP_CASE_MAX_SECONDS` of 900 (`tools/loop/loop.conf:104`), with 100+ reparented `sleep 900`
watchers alive in that window.

## The single fix

The step's output is a file the command writes, not a pipe it fills:

- `$cmd >"$stream" 2>&1 &` — the command owns the file's write end; the step waits for that pid and reads its status
  (`wait "$step"; rc=$?`), which is what `PIPESTATUS[0]` used to answer.
- `follow_stream "$stream" "$follow_stop"` — the console's half. It prints the bytes the file gained since its last
  pass (`tail -c +N`) and ends when the gate writes `<stop>` *and* the file has been drained, so the last line of a
  suite's output reaches the console before the next step starts. Byte-exact, so a partially written line is shown as
  it is written and not line-buffered into a later line.
- A writer that outlives the command keeps the *file* open, which blocks nothing. The stop file is removed after
  each step; both files are removed by the one `EXIT` trap.

The console and the tally still read one observation (`out=$(cat "$stream")`), so *The console and the tally are one
observation* is untouched; the follower is what keeps *A suite's output reaches the console before the suite exits*
true, and `gate#theSuitesOutputReachesTheConsoleWhileItRuns` is the case that says so.

## The one fork, and the alternatives the evidence eliminated

The item names three candidate repairs, and the evidence removes two of them:

1. *"end the step when the command's own process tree is empty"* — the gate cannot see that tree. The suite is one
   process the gate started; the writers that outlive it are its own descendants, reparented by the time the gate
   could look, and this project has already paid for inferring a process tree (`tools/loop/run.sh`'s attribution,
   issue #288). A poll over `/proc` per step would also be a new failure mode on a host without procps.
2. *"give the capture a pipe only the command's process group can hold"* — a pipe has one write end per file
   descriptor and no group scoping; a writer that inherits the descriptor holds it wherever it goes.
3. *"make the case bound's watcher belong to nothing that inherits the suite's stdout"* — already done: the watcher
   sets its own stdio to `/dev/null` (`tools/loop-selftest.sh:498`) and the comment there records that it was needed
   for exactly this reason (three of the driver's cases capture a nested suite in `$( … )`). The item says the same:
   the watchers were the observed concurrent population, not a proven cause. So the leak is some other writer a case
   started, and no shape of pipe is safe against a writer the gate cannot name.

The chosen fix needs none of that: it removes the pipe, so there is nothing for a leftover writer to hold.

## Blast radius

- `tools/loop/gate.sh` — one step became `run_step` plus `follow_stream`, and the two helpers sit above the loop
  (`:65`, `:180`). Nothing else in the gate reads the stream's shape: `out=$(cat "$stream")`, the summary parse, the
  tally writer and both exit paths are unchanged.
- Every case that runs the gate: `--group gate` re-run green (16 cases), including the three that assert the old
  observable (`gate#theSuitesOutputReachesTheConsoleWhileItRuns`, `gate#aFailingSuiteIsReadFromTheSameStream`,
  `gate#theSummaryNamesWhatEachSuiteCost`). `--quiet` keeps its meaning: the file is written, the console is not.
- `tools/loop-mutations.sh` — `46.1` quoted the pipe and would have gone stale (`MUTATION-DID-NOT-APPLY`); it quotes
  the follower now. `63.1` is new and is the record of the new invariant. Its replacement spells the restored
  pipeline's three ampersands escaped (`2>\&1`, `\|`, `\&`), because the harness applies a record with `sed -i
  "$expr"` and an unescaped `&` there expands to the whole match: round 1's record therefore rewrote the step line
  into a corrupted one rather than restoring `$cmd 2>&1 | tee "$stream" &`, and the `RED` it produced was not the
  invariant's (its round-1 review). Every record that needs a literal ampersand in its replacement writes it `\&`
  (`9.3`, `9.9`, `12.2`, `34.4`, `43.3`); `63.1` follows them now.
- The loop's gate on a machine without `wc`/`tail` — both are POSIX and already required elsewhere in the tooling
  (`tools/gen-feature-list.sh` uses `tail -n +2`); no new prerequisite is added.

## Rollback

Revert the branch. The tree carries no state the fix wrote: the stream and its stop file are `mktemp` files removed by
the `EXIT` trap, a tally written by an older gate is read unchanged, and the spec delta disappears with the change.

## What a reader loses

Nothing on the console: the follower prints the same bytes `tee` printed, from byte 0 of the same file. What changes
is the *order* of two facts in the gate's transcript when a step's command ends in the same poll as its last line:
the follower drains the file before it ends, so the line is printed before `gate: … — Ns`, which is the order a
reader already expects.
