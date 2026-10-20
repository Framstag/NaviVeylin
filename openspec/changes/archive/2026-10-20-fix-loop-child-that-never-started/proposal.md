# fix-loop-child-that-never-started

## Why

The loop's run treats every non-zero child exit as a fault of the **item**. On 2026-10-20 the configured child
died 3.9 s and 2.9 s after it started, in both cases with the same 35-byte answer (`Error: Unknown option:
--mode json` — an adapter defect, fixed since in `tools/pi-child-events.sh`). The run recorded both items
`blocked` with `piExit 1` and the note *the worker process exited 1*, escalated each with
`agent/needs-decision` + `status/on-hold`, and stopped on *two consecutive items ended blocked* 3 min into an
8 h budget. Nothing about issue #190 or #198 was examined, and both then carried an escalation no reviewer or
worker produced (issue #212).

**Root cause, verified in the tree at the base commit `648d2bc`.** One line of `tools/loop/run.sh` decides
every item outcome from the child's exit status alone (line numbers are that commit's):

- `tools/loop/run.sh:475-546` `run_child` — `wait "$child"; rc=$?; …; return "$rc"`: the caller is handed the
  child's status **and nothing else**. Whether the child refused its own argv, crashed, or worked the item and
  failed is not in that number.
- `tools/loop/run.sh:700-701` — `if [ "$worker_exit" -ne 0 ]; then outcome="blocked"; blocked_reason="the
  worker process exited $worker_exit"`: the first branch of the outcome chain, so a child that never started is
  classified before any of the evidence the run reads itself (the tree, the archive, the verdict line) is
  consulted.
- `tools/loop/run.sh:869-896` — the `*)` arm escalates every blocked item (`loop_labels escalate`, decision
  label + hold + comment) and `loop_state done --result blocked` (`tools/loop/run.sh:892`), which is the record
  `tools/loop/state.sh:238` counts for the "two consecutive items ended blocked" stop.

**Red test, on the base commit `648d2bc`** (`evidence/red/red-on-head.txt`):

```
  the item is not recorded as a blocked one: expected '0', got '1'
FAIL run#childThatNeverStartedIsTheRunsFault
```

The case `run#childThatNeverStartedIsTheRunsFault` (`tools/loop-cases.sh`) drives the real `run.sh` against the
stub child, which prints one line of its own and exits 1 — the shape the incident left behind. On HEAD the item
is recorded `blocked`; the run then escalates it and the tracker carries a question nobody asked. The offline
shell gate reports it as one case, so there is no JUnit XML for it: `bash tools/loop-selftest.sh --case
'run#childThatNeverStartedIsTheRunsFault'` → `1 case, 1 failing` (2026-10-20, base `648d2bc`).

**Owning requirement.** `agent-issue-loop`, "One command starts a budgeted run, with no dialogue" — its
scenario "A child that outlasts the configured wall-clock is ended and recorded" already establishes that a
child the *run* ended is recorded with a status that is not the child's own and stays a `blocked` item. What the
contract lacked is the sibling case: a child the *environment* refused never read the item, so it is the run's
own fault and the item is not touched.

**Owning guideline section.** `guidelines/Backlog.md` §7 "The loop" — "What bounds it" (the child wall-clock)
and "What it does when it cannot proceed" (escalation). It states what a run does with a child that runs too
long and with an item that needs a decision, and said nothing about a child that never started.

## What Changes

- `tools/loop/run.sh`: after the item's commits are collected, a worker that ended non-zero, wrote no
  `LOOP-VERDICT`, was not ended by the run's own wall-clock, and left the tree at the base commit is the run's
  own fault — the in-progress mark comes off, **no item record is written, nothing is escalated**, and the run
  stops with one recorded reason naming the child's own last line. A child that was ended by the cap (issue
  #203) and a child that left work behind (whose branch a later pass escalates, issue #176) keep today's
  reading: they are blocked items.
- `tools/loop/run.sh`: one small helper, `last_line`, which reads the child's own last non-blank line out of
  its transcript — that line is what the run has to name, and no engine text may name another program's prose.
- `tools/loop/fixtures/pi`: `PI_STUB_SAY`, the file the stub prints as the child's own words before any report,
  so a case can reproduce a child that answers with a complaint instead of a verdict.
- `tools/loop-cases.sh`: `run#childThatNeverStartedIsTheRunsFault`, registered in the `--list` array and in the
  dispatch map.
- `tools/loop-mutations.sh`: mutation `27.1`, which removes the classification and makes the red case fail
  again (revert-check for the new invariant).
- `guidelines/Backlog.md` §7: states what a run does with a child that never started, with the measurement.
- `openspec/specs/agent-issue-loop/spec.md`: the new scenario, inside the requirement that already owns the
  child's end.

## Impact

| file | module / area |
|---|---|
| `tools/loop/run.sh` | the run's only writer: the item loop's outcome chain and one new helper |
| `tools/loop/fixtures/pi` | the stub child the offline suite starts |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `tools/loop-mutations.sh` | the shell gate's mutation set (`revert-check`) |
| `guidelines/Backlog.md` | §7 "The loop" |
| `openspec/specs/agent-issue-loop/spec.md` | the loop's contract |

No compiled file is touched — no Kotlin, Java, C++, manifest, resource or Gradle file — so the phone/car parity
rules, the native-build rules and the test constraints of `guidelines/Build.md` §6 do not arise. The scope is
the offline harness: nothing here runs on a device or under Gradle.

**Additive.** No recorded configuration value, exit code, argument or state field changes its name or meaning;
the run gains one stop reason and loses one way of mis-recording an item. Rollback is `git revert` of the one
`run.sh` branch (the helper it uses then has no caller).

**Spec delta.** `agent-issue-loop` — **MODIFIED** "One command starts a budgeted run, with no dialogue": one
scenario added ("A child that never started is the run's fault, not the item's"), every other scenario and the
requirement's statement restated verbatim.
