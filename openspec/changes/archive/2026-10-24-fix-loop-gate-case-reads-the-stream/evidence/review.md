# The independent review — a fresh, read-only process that did not write the change, 2026-10-24
#
# Run: a `delegate` child of the session that made the change, told to follow `tools/loop/prompts/reviewer.md`
# and nothing else, with the diff (`git diff 0f366176 -- tools/loop-cases.sh`, 40 lines), the change artifacts,
# the shipped requirement and this evidence directory as its inputs. Read-only: no file written, no git command,
# no gate, no suite, no network, no tracker. Verdict: LOOP-BLOCKING 0, VERDICT PASS.
#
# The tree was untouched by the round: `git status --porcelain` was ` M tools/loop-cases.sh` both before and
# after it, and the file's hash is the one this change's gate measured.
#
# Its findings are all NOTES, and this change answers them where they were subtractive: the two UNPROVEN notes
# (the sibling case's red, and a host-load figure no artifact carried) are narrowed to what the shipped evidence
# states, the mutation record's log now quotes the assertion it fires for and its own wall clock
# (`mutation-46.1.log`), and the replay half's missing mutation record is named as an exclusion
# (`evidence.md`). The two remaining notes are the archive and this file, which are the run's mechanical last
# steps and are done after this round.
#
# One quote inside the review is stale as it stands, and is kept as the reviewer wrote it rather than edited:
# item 6 quotes `87–289 ms` as carried by `timing.log`. That was the reading of the batch the reviewer read; the
# batch was re-taken afterwards on the same host in two parts — 43–45 ms quiet and 115–141 ms while 20 spinning
# shells held it (`timing.log`) — and the change's own prose now quotes the artifact's 43–141 ms. The reviewer's
# point stands either way: the number it was checking was carried by the artifact it named, and the artifact now
# carries a later reading of the same measurement.

# Review — item #296, change `fix-loop-gate-case-reads-the-stream`

Read: `.pi/tmp/review-296.diff` (40-line diff), `openspec/changes/fix-loop-gate-case-reads-the-stream/{proposal,design,tasks}.md`, its `specs/agent-issue-loop/spec.md` delta, every file in its `evidence/`, the shipped `openspec/specs/agent-issue-loop/spec.md` (req. line 1633), the archived `2026-10-23-fix-loop-label-write-failure-is-recorded/evidence/baseline-main-red.log`, the archived `2026-10-23-fix-loop-gate-output-streams-while-a-suite-runs/evidence/revert-check-46.1.log`, `tools/loop/run.sh`, `tools/loop/prompts/worker.md`, `guidelines/Build.md` §6e, `.gitignore:75`. No file written; no gate, suite or VCS command run.

## 1. Scenario coverage

- **Added scenario — "A suite's line is read from the stream, not at an instant"** — **PASS**. Asserting case `gate#theSuitesOutputReachesTheConsoleWhileItRuns`. Its assertion is the new body: `for _ in $(seq 600); do out=$(cat "$log"); case "$out" in *'case: a note is read'*) break ;; esac; sleep 0.05; done`, followed by the unchanged `assert_not_contains "$out" "notes: all 1 cases passed"`. Timestamps: `evidence/gate.log` line 301 `PASS gate#theSuitesOutputReachesTheConsoleWhileItRuns` (gate header: `finished 2026-10-24T10:50:32Z; started at 10:31:10Z`, suite `1111s`); `green-after.log` PASS (12:27), `green-repeat.log` three PASS (10:27:51Z–10:28:10Z), `green-contended.log` PASS under the same 24-shell contention that made the base FAIL (`red-before.log`) — the wait is exercised on the host shape the item names. The "does not soften the guard" half is asserted by that `assert_not_contains` and demonstrated by `host-bound-probe.log` run 3 (`the replayed gate: RED — the fixed reading still refuses a replay`) and `mutation-46.1.log`.
- **Shipped scenarios repeated verbatim** — **PASS**. "A suite's output reaches the console before the suite exits" (same case, `gate.log:301`), "The console and the tally are one observation" (`gate.log:302 PASS gate#aFailingSuiteIsReadFromTheSameStream`); the delta changes neither. Verified programmatically: requirement body identical (436 chars), both shipped scenarios byte-identical between shipped spec and delta.

NOTE: the wait's own bound is not asserted; `evidence.md` says so itself ("The 30 s bound has no assertion of its own"), and the failure path is kept honest by record `46.1` — that is the guard half, not a missing assertion.

## 2. Cause or gap scope

**PASS.** The hunk is `case_gate_theSuitesOutputReachesTheConsoleWhileItRuns` (diff header names the preceding function, `case_gate_aPipelineCheckIsOwedByItsPaths`). It replaces `sleep 1.5 / out=$(cat "$log")` with the bounded wait and adds the comment block; nothing else. No engine script, mutation record, profile, Gradle or native path is touched. The diff is only `tools/loop-cases.sh`.

## 3. Gate reality

**PASS, with the one red attributed.** `evidence/gate.log`: `loop-selftest: selected 209 of 209 cases (files: tools/loop-cases.sh)`; `LOOP-GATE: tests=209 failures=1 errors=0 (tools/loop-selftest.sh --from-git 0f366176 1111s) — failing: tools/loop-selftest.sh --from-git 0f366176`; `GATE-EXIT=1`. A real, executed run (209 executed, 1111 s) taken after the fix — the source `tools/loop-cases.sh` mtime `2026-10-24 12:18:09 +0200` precedes the gate's start `10:31:10Z`; no cache concept for a shell suite. Coverage is right: a one-file shell delta owes the loop suite, not Gradle (`guidelines/Build.md` §2 scope).
The single red is `run#aWriterTheRunCanPlaceBlocksTheItem` (`gate.log:392`), message `a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'`. `base-red-attribution.log` shows the same case alone on `.pi/worktrees/i296-base` (detached at `0f366176`) with the byte-identical message, and cites the existing archived `baseline-main-red.log`, which carries the same three lines under `## run#aWriterTheRunCanPlaceBlocksTheItem — red on the base too, not this change`. The delta reaches neither `tools/loop/run.sh` nor that case. **The attribution is honest and supported.**

## 4. Tasks claiming done

- 4.1, 4.2 — satisfied (`gate.log`, `base-red-attribution.log`).
- **NOTE:** 4.3 is `[x]` and names `evidence/post-archive-checks.log` — that file does not exist; 4.5 is `[x]` ("Archive the change and confirm the scenario merged") but the change is still under `openspec/changes/fix-loop-gate-case-reads-the-stream/` (not in `openspec/changes/archive/`, and `openspec/specs/agent-issue-loop/spec.md` does not contain the added scenario — the only hits for its text are the open change's delta and `tasks.md`). Closure depends on the archive (`agent-issue-loop` spec: "An item SHALL be closed only after its review passed and its change was archived"), so these two marks are premature. I grade this a note, not a block: the change's content does not depend on the archive, which is the run's mechanical last step.
- **NOTE:** 4.4 is `[x]` and names `evidence/review.md`; that artefact is produced by this round, so it cannot be present before it.

## 5. Revert-check

**PASS with one gap.** Record `46.1` targets the production invariant — `tools/loop-mutations.sh` line 325 replaces the gate's `| tee "$stream"` with `out=$($cmd 2>&1); printf %s "$out" > "$stream"` (the console goes silent). `evidence/mutation-46.1.log`: `RED 46.1 gate#theSuitesOutputReachesTheConsoleWhileItRuns`, `restore 46.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`. With the suite's console silenced the only falsifiable assertion is the first-line one (`gate: bash` is printed by the gate independently), so the failure is for the right reason. **NOTE:** the log does not quote that assertion's message or a precise timestamp (`Taken 2026-10-24T10:3xZ`), so the reason is inferred rather than quoted; this matches the archived precedent `revert-check-46.1.log`, which also prints only the summary line.

## 6. Evidence pointers

**PASS, two dangling.** Every cited log/harness resolves: `timing.log`, `timing-probe.sh`, `red-before.log`, `contend.sh`, `green-after.log`, `green-repeat.log`, `green-contended.log`, `host-bound-probe.log`+`.sh`, `mutation-46.1.log`, `structural-checks.log`, `gate.log`, `base-red-attribution.log`, and the archived `baseline-main-red.log`. The tallies are carried by the artefact each names (`tests=209 failures=1 errors=0`, `selected 209 of 209`, `87–289 ms`, `1111 s`). Missing: the `evidence/post-archive-checks.log` and `evidence/review.md` cited by tasks 4.3/4.4 (see item 4).
**NOTE:** `evidence.md` states "`mutation-46.1.log`, 39 s for the record's two case runs" — that log carries no `39 s` (or any wall time); the number is unbacked by the named artefact.

## 7. Spec and guideline honesty

**PASS.** `MODIFIED` is the right kind: it adds one scenario to an existing requirement, repeats the body and both shipped scenarios verbatim (checked), and leaves the requirement text at 436 chars (< 500). The added scenario states a contract (wait within a bound; the replayed output still fails), not an implementation — no `seq`/`sleep`/30 s in the text. No guideline edit is owed: `grep` finds no guideline naming any loop case, and `checkNoWallClockWaits` is registered only in `app/auto/core` Gradle modules (`grep`: `./auto/build.gradle.kts:138`, `./core/build.gradle.kts:125`, `./app/build.gradle.kts:488`), so it binds no shell case — `structural-checks.log` confirms `openspec validate … is valid`, `check-doc-routes: all 153 section references resolve`, `evidence-check: PASS`.

## 8. New overreach

UNPROVEN: the proposal's and design's claim that the sibling `run#prescreenAnnouncesItselfBeforeItReturns` "had exactly this shape and went red on a host whose start-up outlasted the sample" — the #287 branch is not on this tree and no log carried here measures it.

UNPROVEN: the proposal's "(a load average of 9–41 on 16 CPUs, three other loop suites live in the repository)" — `timing.log` carries one host line, `16 cpus, load 41.76 31.47 19.44`, and no count of other suites.

## Notes that are not blocks

NOTE: the replay half of the added scenario's guard is asserted by the case's `assert_not_contains` and demonstrated by the hand probe (`host-bound-probe.sh` run 3), but has no mutation record of its own; only the silent-stream half is in the shipped mutation set.

---

The change's substance is sound: the root cause (a fixed-instant reading bounded by the host) is replaced by a wait on the gate's own observable output, the guard is not softened (record `46.1` still reddens; a replayed gate still fails), the delta is the right kind and verbatim where required, and the gate's one red is honestly the base's (#288, on record). The only open items are the archive and its post-archive evidence, which `tasks.md` marks done too early — a process step the closing run owes, recorded here so it is not lost.

```
LOOP-BLOCKING: 0
VERDICT: PASS
```
