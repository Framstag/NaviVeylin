# Design — fix-loop-process-group-lifetime

The root cause is established and one mechanism explains all three symptoms: a process is started in a group
somebody *can* end, and nobody ends it. The suite's traps know only the suite's pid, the run's traps know only
the run's pid, and the leftover report reads a directory's age — so the case subshell, the child the run was
waiting for, and the process standing in a removed tree all survive the thing that started them. The
alternatives below are the ones the evidence eliminates, not a menu (`.pi/skills/fix-loop`,
`openspec/config.yaml`, `rules.design`).

## The alternatives the evidence eliminated

**1. Let the suite's traps kill every descendant by walking `/proc` at exit.**
Eliminated because it is the wrong tree and it cannot be bounded: a descendant walk has no way to know which
processes to leave (the child's own grandchildren are the ones the cap needs to reach) and it races a child that
is forking while the walk runs. A process group is the kernel's answer to exactly this question, and the child
already has one — `run_child` puts it there with `set -m` so the cap can end it and its descendants (#203). The
defect is that only the cap used it.

**2. `setsid` (a fresh session) instead of `set -m` (a fresh process group).**
Eliminated because the run's trap must be able to reach the child, and a session leader leaves the run's own
group: `kill -TERM -<pgid>` from the run would no longer reach it, which is the opposite of what is needed. A
process group is also what `run_child` already uses, and this change must not introduce a second convention for
the same thing.

**3. Have the driver kill the case's pid only, and accept a case that outlives its suite.**
Eliminated by the measurement in the proposal: bash defers a trap until the foreground command returns, so with
the case in the foreground a `TERM`ed suite honours the signal only after its case has finished anyway — a
5-minute case makes a 5-minute interrupt (`real 5m01,505s` for the case as it was first written). The case that
reproduces the leak therefore fails on HEAD for the delay alone, and the group kill is what makes the interrupt
immediate.

**4. Report the leftovers and let a person end them.**
Eliminated because the report exists precisely because nobody looks: the five processes in the issue ran for
55 to 100 minutes and were ended by hand. `--apply` already means "do what you reported"; ending a process that
is polling a directory that no longer exists is the same action as removing the directory.

**5. Detect leftovers by argument line (`pgrep -f 'tools/loop/run.sh'`).**
Eliminated because an argument line is not evidence about *which* run a process belongs to: the self-test runs
the real `run.sh` in scratch repositories, a developer may have a real run in another worktree, and a stale
binary of the same shape would be indistinguishable. The working directory is the fact this command already
acts on — it removes the tree, so a process standing inside that tree is the one the removal strands.

**6. Signal every process the report can see, including the ones in kept trees.**
Eliminated by the age rule #210 already paid for: a fresh tree may belong to a suite that is running right now,
and its processes are that suite's. The report's own case asserts the boundary in both directions (the stale
tree's process is ended, the fresh tree's is not).

**7. Give the child a session of its own and make the driver responsible for the run's children too.**
Eliminated because it inverts ownership: the run is the thing that knows which child it started and when the
child's phase ended, and the suite is the thing that knows which case it started. Each layer ends what it
started (the rule this change states), rather than each layer reaching two levels down.

## The chosen shape, and why it is safe

| layer | who owns it now | what it ends |
|---|---|---|
| `run_case` in `tools/loop-selftest.sh` | the suite | the case's subshell and everything the case started |
| `run_child`'s `active_child` in `tools/loop/run.sh` | the run | the child it is waiting for, and its descendants |
| `leftover_pids` in `tools/loop/loop-clean.sh` | the leftovers | one pid per stale tree, with `--apply` only |

The ending itself is implemented **twice at most, once per kind**: `loop_end_group` (a group) and
`loop_end_pid` (one process), both in `lib.sh`, so the suite and the run cannot drift apart on *how* they end
something (D16: a rule re-implemented per script is a rule that drifts). Both send `SIGTERM` first, because the
members have cleanup of their own — the run writes the item's outcome and releases its lock from its `EXIT`
trap — then a bounded grace (5 s for a group, 2 s for a pid) and `SIGKILL` for what ignored the first signal.

**Why the group id is safe to signal.** A process group id cannot be recycled while a member is unreaped, and
every place that signals one holds the leader unreaped: the suite's `wait "$case_pgid"` was interrupted, so the
case is still the suite's child; the run's `wait "$child"` was interrupted the same way. Both clear the variable
immediately after a *normal* wait, so a normal end never signals a group id that a later process may have been
given.

**Why the case's stdin is closed.** That is `run_child`'s own reason, copied: a member of a background process
group that reads a terminal is *stopped*, not run, and an unattended suite has nobody to type at it. No case
reads stdin (checked), so the redirect changes nothing a case asserts.

**What the change deliberately does not touch.** The cap (`LOOP_CHILD_MAX_SECONDS`), the poll, the heartbeat,
the report, the state, the floor, the label transitions, the closure and the prompt skeletons are untouched.
`loop-clean`'s path count, byte total, age gate and idempotency are untouched: the process count joins the
summary only when the report ended or would end one, so every existing assertion on that line still holds.

## Risks

| risk | mitigation |
|---|---|
| a group id is signalled after its group is gone | signalled only while the leader is unreaped, and cleared after every normal `wait` |
| the suite ends a case that was about to pass | the group is signalled only on the interrupt path; a case that finishes is waited for and its group id is cleared first |
| the run ends a child that was finishing its work | `SIGTERM` first, so the child's own cleanup runs; the run is exiting, and the item is recorded `blocked` either way |
| the report ends a process that is not a leftover | only a process whose working directory is inside a tree it removes, only a stale tree (the #210 age gate), and never its own caller (`$$`, `$PPID`) |
| the process scan is unavailable (no procfs) | the report says `no /proc — the processes inside <tree> cannot be listed` and does the tree work anyway; the check never guesses |
| a report line other cases parse changes shape | the `N path(s), B B` prefix is unchanged; the process clause is appended only when the count is non-zero |

## Threading, lifecycle, and the native boundary

None arises: every file here is a bash script run by the developer or by CI — no JVM, no Compose, no dispatch,
no JNI call. The loop's existing rules hold unchanged: the run writes the state between children only, and this
change ends the child *before* those writes on the interrupt path, which is the ordering the rule asks for.

## Verification

- **Red on HEAD `648d2bc`**, `evidence/red/` (three cases, exit 1 each, messages quoted in the proposal).
- **Green on the fix**, `evidence/green/`: the same three cases plus the five cases on these two surfaces that
  already existed.
- **Revert-check**, `tools/loop-mutations.sh --only 27`: 27.1 removes the suite's group kill, 27.2 removes the
  report's process reader, 27.3 removes the run's child kill — each must go red in its named case, restore, and
  go green again.
- **Structural checks**: `bash -n` on every edited script, `openspec validate fix-loop-process-group-lifetime
  --strict`, `openspec doctor`, `tools/check-portability.sh`.
- **The gate**: the whole offline suite, `tools/loop-selftest.sh`, once.
- **Not run here**: no Gradle run belongs to this change — no compiled file of the application is touched, so
  `guidelines/Build.md` §2's scope block replaces the project's Gradle gate with the surface's own gate.
