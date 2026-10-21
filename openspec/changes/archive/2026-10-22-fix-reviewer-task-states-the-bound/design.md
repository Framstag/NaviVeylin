# Design — the reviewer's task states the bound it runs under

## Root cause

The reviewer's task text bounds the eight things a round must **judge** and nothing about what it may **run**.
Its read-only sentence (`tools/loop/prompts/reviewer.md:8`) prohibits editing, committing, version control and
the tracker — the acts that would break the "a reviewer that wrote cannot have been the reviewer" invariant —
and stops there, while item 3 of the eight (`reviewer.md:26`) asks whether `tests=<n> failures=0` is a real
execution. The cheapest way to answer that question, for a process that has a shell, is to run the suite; the
task never says the evidence already answers it. The session-spawned reviewer has it worse: outside the loop
the task is written by hand, and no guideline states a bound at all.

## The single fix

State the bound in the one place a reviewer is handed its task, in the order it needs it:

1. `tools/loop/prompts/reviewer.md` — a section **"Your inputs, and what you may run"** immediately after the
   identity paragraph and before the eight items: the four inputs, the read-only list extended with the gate,
   the test suite and the network, "the gate's result is an input, not something you reproduce", the
   wall-clock bound, and that a diff review is a **reading** task. Item 3's line is narrowed to the same rule,
   so the sentence that invited the run no longer does.
2. `tools/loop/run.sh` — `render_reviewer_prompt` adds `REVIEW_WALL_CLOCK=$child_max`. The number is the one the
   run already bounds the child by (`run.sh:94`, `LOOP_CHILD_MAX_SECONDS`), so the text cannot drift from the
   enforcement, and no project fact enters the engine (D16).
3. `guidelines/Build.md` §2 — rule 5, "A reviewer is a reader, not a runner": a review edits nothing, runs no
   gate and no suite, quotes a green result from the evidence, and a session hands its own reviewer
   `tools/loop/prompts/reviewer.md`. One home for the text; the guideline states the rule and names the path.

## The alternatives the evidence eliminated

- **Bound the reviewer in the wrapper instead of in the text.** Impossible as the fix: the wrapper already
  bounds the child by `LOOP_CHILD_MAX_SECONDS` (2700 s), and the measured stall happened *inside* that bound —
  4 min 1 s held open, then 8 min to finish. A cap does not stop an idle wait, and the wrapper cannot tell a
  reviewer's suite run from a reviewer's reading, because the reading tree is the same tree.
- **Let the round re-run the gate and compare with the evidence.** Impossible: the reviewer's reading surface
  is the diff, which excludes the generated evidence trees by pathspec (`LOOP_REVIEW_EXCLUDE_PATHSPEC`); a
  second execution is a second measurement of the same tree, and cannot be more authoritative than the run's
  own tally, which is the artifact the round is being asked to check. Two executions is exactly the cost the
  item records.
- **Put the rule only in the guideline.** Impossible: the loop's reviewer is a fresh child started from
  `prompts/reviewer.md` alone — the guideline is not rendered into its task, and `prompt#reviewerIsBoundedToReading`
  reads the rendered prompt, not the guideline. A rule only in `Build.md` would leave the loop's own child
  unbounded, which is where the 4 min were spent.
- **Put the rule only in the prompt.** Impossible: the reviewer that stalled was the one a *session* spawned
  from a free-form task; the loop's own reviewer is already bounded by the wrapper's read-only comparison. The
  protocol is the only text a session reads before it writes such a task.
- **Choose the model at render time.** Not available: the child's model is the adapter's business
  (`LOOP_AGENT_CMD`), and the engine may name no agent command or mode flag (issue #211). The skeleton can
  state the *kind* of task it is; it names no model, and the guideline states no selection act either.

## Blast radius

Five files, no compiled file, no production code path: a prompt template, one render argument, one guideline
rule, two cases and one mutation record. The loop's own behaviour is unchanged — the reviewer is still one
fresh read-only child, still compared before and after, still bounded by the same wall clock.

## Rollback

The reverse patch of the five files. For the invariant itself, mutation `37.1` *is* the rollback: it deletes
"the gate" from the read-only list and `prompt#reviewerIsBoundedToReading` must fail, so the regression is
visible rather than silent.

## Verification

- `bash tools/loop-selftest.sh --case 'prompt#reviewerIsBoundedToReading'` — red on HEAD
  (`evidence/red-reviewer-bound.log`), green after the fix (`evidence/green-reviewer-bound.log`).
- `bash tools/loop-selftest.sh --case 'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'` — red on HEAD
  (`evidence/red-iteration-protocol.log`), green after the fix (`evidence/green-iteration-protocol.log`).
- `bash tools/loop-selftest.sh` — the whole suite runs inside the gate: `tools/loop-selftest.sh` reported
  `all 148 cases passed` (`evidence/gate-loop-selftest.xml`).
- `bash tools/loop-mutations.sh --only 37.1` — the mutation lands, the named case goes red, the restore goes
  green (`evidence/mutation-37.1.log`).
- `./tools/loop/gate.sh` — run **once** (`evidence/gate.log`, 2026-10-21T22:07:57Z → 22:21:49Z):
  `LOOP-GATE: tests=193 failures=0` over `./gradlew test -PforceTests --no-build-cache`,
  `tools/loop-selftest.sh`, `tools/sensor-selftest.sh` and `tools/check-doc-routes.sh`. No compiled *source*
  file changes (`guidelines/Build.md` §2, "no compiled file changed"); the build's own gate was selected
  anyway, because the gate measures the diff from the run's base `80df3a3` and one path it matches is an
  archived `*.xml`. The per-suite tallies are `evidence/gate-*.xml` — including `evidence/gate-gradle.xml`,
  the build's own tally, which carries `tests="0"` and passes by exit status, so 193 is 148 + 45 + 0.

### Round 2, after the first review

The review's one blocking finding was a scenario clause no case read; the round-2 correction adds that
assertion and corrects the pointers the review graded as notes. The gate is **not** re-run: a second run would
replace the one measurement the change owns rather than extend it, and the round-2 edits touch no compiled
file.

- `bash tools/loop-selftest.sh --case 'prompt#reviewerIsBoundedToReading'` — green with the item-3 assertion
  and with the wall clock set to `8100` in the case's own configuration (`evidence/round2-reviewer-bound.log`).
- `bash tools/loop-selftest.sh --case 'prompt#iterationProtocolHandsTheReviewerTheSameSkeleton'` and
  `--case 'prompt#reviewerCarriesTheEightItems'` — green (`evidence/round2-iterationProtocolHandsTheReviewerTheSameSkeleton.log`,
  `evidence/round2-reviewerCarriesTheEightItems.log`).
- `bash tools/loop-mutations.sh --only '37.1 37.2'` — `red-when-mutated: 2 · not-noticed: 0 ·
  restore-failures: 0` (`evidence/mutation-37.log`); `37.2` restores item 3's invitation and the case notices.
- `bash tools/loop-selftest.sh` — `all 148 cases passed` (`evidence/round2-loop-selftest.log`).
- `bash tools/check-doc-routes.sh` — `all 120 section references resolve`
  (`evidence/round2-doc-routes.log`).
