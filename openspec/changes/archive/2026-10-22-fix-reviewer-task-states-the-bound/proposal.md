# fix-reviewer-task-states-the-bound

## Why

Issue #236. Verified on the run's base commit `80df3a3` on 2026-10-21, in the loop's worktree.

**The claim holds, at one line and in one absence.** `tools/loop/prompts/reviewer.md:8` states the bound as
one sentence — "Read-only. Do not edit a file, do not commit, do not run version control, do not touch the
issue tracker." — and names neither the gate nor a test suite. The eight items the round is asked to judge
then *invite* the expensive move: item 3 (`reviewer.md:26`) asks "is `tests=<n> failures=0` a real execution
*for this change*", and nothing in the task says that question is answered by reading the change's own
evidence rather than by running the suite again. The task also states no wall-clock bound and no model
choice, so a reviewer a session starts itself has nothing but its own judgement to stop on.

The measured consequence is the item's own observation: on 2026-10-21 the independent reviewer for the #232
change held one `ctx_shell` call open for **4 min 1 s** (11 turns, 77 k tokens, one tool outstanding) and the
parent had to steer it — "do not run the full selftest or the mutation harness — the evidence already records
both" — before the review finished in 8 min. Total cost 20 min of a 4 h 40 m change, ~7 %.

**Root cause, one sentence:** the reviewer's task text bounds the eight things a round must *judge* and
nothing about what it may *run*, so the cheapest-looking way to answer item 3 is to reproduce the gate — a
question the evidence already answers.

**Red tests on HEAD, before the fix.** Two cases in `tools/loop-cases.sh`, run one at a time with
`bash tools/loop-selftest.sh --case '<case>'`; both fail on HEAD because the text they assert is absent:

```
$ bash tools/loop-selftest.sh --case 'prompt#reviewerIsBoundedToReading'
  the reviewer is told what it is given: 'Your inputs are' not found in: # Reviewer — the fixed contract
FAIL prompt#reviewerIsBoundedToReading
EXIT=1                                    # evidence/red-reviewer-bound.log, 2026-10-21T22:07:10Z

$ bash tools/loop-selftest.sh --case 'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'
  the iteration protocol does not name the skeleton a session-spawned reviewer is handed
FAIL prompt#iterationProtocolHandsTheReviewerTheSameSkeleton
EXIT=1                                    # evidence/red-iteration-protocol.log, 2026-10-21T22:07:21Z
```

The first case also asserts the four clauses that follow the inputs — the gate, the suite, the network, the
wall-clock bound — and each of them is absent on HEAD for the same reason; the case reports the first one.
Both green halves of the same two commands are beside them (`green-reviewer-bound.log`,
`green-iteration-protocol.log`, `EXIT=0`).

**The project's gates produce no JUnit XML for this delta.** `tools/loop-selftest.sh` is a shell harness that
prints one `PASS`/`FAIL` line per case and exits 0/1; there is no Gradle task behind it. The gate the change
owes is `./tools/loop/gate.sh`, which writes its own JUnit-shaped tally per suite it ran
(`app/build/test-results/loop-gate/`), so `LOOP-GATE` carries that file's numbers and the red and green halves
are quoted as the harness prints them, with their exit status and timestamp.

**Owning capability:** `agent-issue-loop` — requirement "Each item is worked by a fresh process, reviewed by
a different one" (`openspec/specs/agent-issue-loop/spec.md:372`), which this delta extends with the task text a
round is given. **Owning guideline section:** `guidelines/Build.md` §2 "Agent iteration protocol"
(`Build.md:98`), which states rules 1–4 of the loop and no rule about the reviewer a session starts itself —
routed by `AGENTS.md`'s Documentation Map ("build, run, force a rerun, read a gate result → `Build.md` §2 …")
and by `AGENTS.md`'s own sentence "an independent read-only reviewer before archive".

## What changes

- `tools/loop/prompts/reviewer.md` — a new opening section, **"Your inputs, and what you may run"**, placed
  before the eight items and after the identity paragraph: the four inputs (the diff, the project's rules, the
  evidence by path, this task), the read-only list extended with **the gate, the test suite and the network**,
  the statement that a green gate is *quoted from the evidence* and never reproduced, the wall-clock bound the
  run enforces (`{{REVIEW_WALL_CLOCK}}`), and that a diff review is a **reading** task — the model choice.
  Item 3's own text is narrowed to the same rule, because that line is what invited the suite run.
- `tools/loop/run.sh` — `render_reviewer_prompt` passes `REVIEW_WALL_CLOCK=$child_max`, the value the run
  already bounds the child by (`run.sh:94`). Nothing else in the engine changes; the number stays a recorded
  configuration value and no project fact enters the engine.
- `guidelines/Build.md` §2 — a fifth rule in the iteration protocol, "A reviewer is a reader, not a runner",
  which names `tools/loop/prompts/reviewer.md` as the task text a session hands its own reviewer, so the
  bound reaches a review the loop did not start.
- `tools/loop-cases.sh` — the two cases above.
- `tools/loop-mutations.sh` — mutation `37.1`: delete the gate from the read-only list, and
  `prompt#reviewerIsBoundedToReading` must fail.

The change is **additive and documentation-shaped**: it constrains a task text and a guideline rule, and
changes no production code path. Rollback is the reverse patch of five files, and mutation `37.1` *is* that
rollback for the invariant itself: with the clause removed the case goes red again.

## Impact

- `tools/loop/prompts/reviewer.md` (one new section, item 3's line narrowed)
- `tools/loop/run.sh` (one render argument)
- `guidelines/Build.md` (one rule in §2)
- `tools/loop-cases.sh` (two cases, two registration lines)
- `tools/loop-mutations.sh` (one record)
- Verification: the two cases, mutation `37.1`, and `./tools/loop/gate.sh` run once — `tests=193 failures=0`
  over four suites (`evidence/gate.log`, `evidence/gate-*.xml`). No compiled *source* file changes
  (`guidelines/Build.md` §2, "no compiled file changed"), but the run's gate still ran the build's own gate:
  the diff it measures is taken from the run's base `80df3a3`, which carries three commits of an earlier item,
  and one of the paths it matched is an archived `*.xml`. The build was green; it was not owed. Named here
  rather than silently dropped.
