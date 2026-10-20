# fix-loop-gate-and-child-timeout

## Why

Item #126's worker phase ran **83 minutes** on 2026-10-19 and spent the run's clock inside a single wait.
Its own report (`gate-results.txt`) says the configured gate *never reached a suite*:
`./gradlew test -PforceTests --rerun-tasks` sat 30 min in `:app:kspMobileDebugUnitTestKotlin` at 194 MB free
RAM / 18 GB swap, and the retry died inside `:app:testAutomotiveDebugUnitTest`. The same 5680 tests through
`-PforceTests --no-build-cache` finished **BUILD SUCCESSFUL in 3m47s**.

Nothing in the loop bounded that. `tools/loop/run.sh`, `run_child` waited for the child with no time limit,
and the run's deadline is consulted only *between* items (`loop_state next`), so a child that never returns
spends the whole budget where the stop condition cannot look. Two mechanisms, one symptom each:

- **root cause 1 (the gate):** the recorded gate command was the invocation this host cannot complete
  (`tools/loop/loop.conf`, `LOOP_GATE_CMD`, `--rerun-tasks`), so every item paid for a flag choice with
  minutes of swap.
- **root cause 2 (the bound):** a child process had no wall-clock of its own (`run_child` in `run.sh`,
  before this change), so a gate that never returns was waited for indefinitely.

The single fix that follows is: record the invocation the host completes, and give `run_child` the
wall-clock the configuration allows — ending the child *and its descendants* and appending one
machine-readable line to the transcript, so the run's existing "the worker process exited N" path records the
item `blocked` with that transcript. There is no second defensible fix: the alternatives are eliminated in
`design.md`.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario "The run stops on its own authority" promises that a spent time budget stops the
run, and a hung child was the one place it did not.

**Red case:** `run#childOutlastingTheWallClockIsBlocked` — on the tree before this change it fails with
`the run was still waiting after 10 s on a child that outlasted its 2 s cap`
(`evidence/red-on-head.txt`); with the change it passes (`evidence/green-on-fix.txt`).

## Cases and evidence

`run#childOutlastingTheWallClockIsBlocked`, registered in both the `--list` array and the dispatch map of
`tools/loop-cases.sh`. It drives the real run with `LOOP_CHILD_MAX_SECONDS=2` through the `LOOP_CONF` seam
and a stub child that blocks on a fifo, and asserts the four things the bound owes: the item's state is
`blocked`, its note names the cap, the transcript carries `LOOP-TIMEOUT: the child exceeded 2 s`, and nothing
is closed. Mutation 23.1 (`tools/loop-mutations.sh`) removes the cap check and this case is the one that goes
red for it; mutations 9.9 and 12.2 were re-pointed at the rewritten child start so the set still applies.

- `evidence/red-on-head.txt`, `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/openspec.txt`
- `evidence/full-suite-superseded-under-load.txt` — the one whole-suite run made here, kept with the reason it
  is not the accepted evidence: it ran before the rebase onto `0205d83` while a second suite ran on the same
  host, and its two failures (`run#citedEvidenceMustTravelWithTheChange`,
  `run#awaitingDecisionIsNotEscalatedAgain`) name cases that touch no file this change edits. The single
  full-suite run belongs to the orchestrator, on the merged tree.

## What changed

- `tools/loop/loop.conf`: `LOOP_GATE_CMD` is `./gradlew test -PforceTests --no-build-cache` — the invocation
  `guidelines/Build.md` §4's flag table already prescribes for test evidence, and the one the host completes
  in 3m47s. New recorded default `LOOP_CHILD_MAX_SECONDS=2700` (45 min), documented beside the other budget
  defaults, with the measurement that produced it.
- `tools/loop/lib.sh`: `LOOP_CHILD_TIMEOUT=124`, the child status a cap-imposed end reports — distinct from
  any status a child would return by itself.
- `tools/loop/run.sh`: the run header names `child-max-seconds`; `run_child` starts the child as its own
  process group (`set -m` for that one command, undone immediately, stdin closed), polls it against
  `LOOP_CHILD_MAX_SECONDS`, ends it with `TERM` and then `KILL` on the whole group when the cap is passed,
  appends `LOOP-TIMEOUT: the child exceeded N s — the run ended it and everything it started` to the
  transcript and returns 124. The heartbeat (`LOOP_PROGRESS_SECONDS`, commit 1fa8077) and the
  `LOOP-OUTSIDE-WRITE` check are unchanged around it. The blocked reason appends the timeout line when the
  transcript carries one, so "exited 124" is never the whole story.
- `tools/loop/prompts/worker.md`: the gate is run **once**; a gate that does not complete inside the run's
  budget is `blocked` with its log, never retried.
- `tools/loop-cases.sh`: the new case, registered in both the `--list` array and the dispatch map.
- `.pi/skills/fix-loop/SKILL.md`, `guidelines/Backlog.md` (§7 and the §11 configuration table): the bound and
  the run-once rule stated where the loop's rules and its configuration keys live.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/loop/loop.conf` | the loop's recorded configuration (engine seam, `lib.sh` reads it) |
| `tools/loop/lib.sh` | the loop's shared constants and configuration reader |
| `tools/loop/run.sh` | the run's only writer: the item loop and `run_child` |
| `tools/loop/prompts/worker.md` | the worker's normative contract (a prompt skeleton, rendered at run time) |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `tools/loop-mutations.sh` | the shell gate's mutation set (two patterns name the rewritten child start; mutation 23.1 removes the cap) |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's rules |
| `guidelines/Backlog.md` | §7 "The loop", §11 "Where an agent runs" (the configuration-key table) |

No Android module, manifest, resource, native source or JNI bridge is touched: this is not a phone, car or
AAOS change, so the parity rules and the native-build rules do not apply — and no `submodule patch` /
`local override` decision arises.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "One command starts a budgeted run, with no
  dialogue": one scenario added (a child that outlasts the configured wall-clock is ended and the item is
  recorded blocked with its transcript). Nothing else in that requirement is re-worded.

No other capability owns the loop's budget, the gate command or the prompts.

## Guidelines affected

- `guidelines/Backlog.md` §7 (what the loop does when it cannot proceed) and §11 (the configuration keys):
  both are edited in this change.
- `guidelines/Build.md` §4 already states the flag table (`-PforceTests --no-build-cache` for test evidence,
  `--rerun-tasks` only for the whole graph), so the recorded gate command now agrees with it; no edit there.
- `.pi/skills/fix-loop/SKILL.md` is a skill, not a guideline, and is edited in this change.

## Additive or breaking, and rollback

**Additive.** A recorded configuration value with a default (2700) and one new scenario bound the loop
strictly more tightly; the gate command is a value, not a code path. A run started before this change and one
started after differ only in what they refuse to wait for.

Rollback: revert this commit. The loop returns to the previous gate invocation and to an unbounded wait; no
state file, branch, label or archived change is affected, because the change writes none of them. A run in
flight during a revert is stopped first (`tools/loop/state.sh next` and the lock refuse a second run anyway).
