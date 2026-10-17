# Tasks

This change is spec-owned by `agent-issue-loop`: `specs/agent-issue-loop/spec.md` defines it, so each task names the
**design decision** it lands and its verification names the case that exercises it; the traceability task in this change maps every scenario to its case. Every task carries its own verification.

Scope guard: the wrapper and the skill are shell and markdown — `tools/loop/**` is outside every Gradle
source set, so **no Gradle build and no Gradle suite is run or required** (`guidelines/Build.md` §2, the
scope block). The change's gate is `tools/loop-selftest.sh`. No device or pixel measurement applies:
the change produces no UI and nothing is read on a screen — say so in the change report rather than
implying device proof.

## 1. Run state and the lock (D5, D12)

- [x] 1.1 Write `tools/loop/state.sh` owning `.pi/issue-loop/run.json` as its only writer: `init --items N --minutes M --review on|off --may-close yes|no --max-decisions N --base <sha>`, `show`, `next` (exit 3 = stop), `step`, `done`, `report`. Verify: case `state#initAndShowRoundTrips` writes and re-reads every field, asserting `base` and `may-close` survive; a hand-edited field outside the script is detected as a drift warning (D5).
- [x] 1.2 Implement the budget authority in `state.sh`: `next` exits 3 when the item cap is reached, the deadline passed, the queue empty, two consecutive iterations ended `blocked`, or the remaining time is under the next item's floor. Verify: case `state#nextHonoursEveryStopCondition` drives all five conditions and asserts exit 3 for each and 0 only when none holds (D5).
- [x] 1.3 Implement the lock in `state.sh` (`.pi/issue-loop/lock`, released by an `EXIT` trap). Verify: case `state#secondRunRefusesWithExit5` starts a second `run.sh` against the stub `pi` while the first holds the lock and asserts exit 5 with nothing written (D12).
- [x] 1.4 Write `tools/loop/loop.conf` holding the recorded defaults (items, minutes, review, may-close, max-decisions) **and the three project-specific keys** `LOOP_AGENT_CMD`, `LOOP_WORKER_SKILLS`, `LOOP_GATE_CMD`, and have `run.sh` echo them in the run header. Verify: case `state#defaultsComeFromLoopConf` asserts every flag omitted on the command line equals its `loop.conf` value in `state.sh show`, and case `state#projectKeysAreReadFromLoopConf` asserts the worker prompt's skill list and the gate command come from `loop.conf`, not from a literal in a prompt skeleton (D2, D16).
- [x] 1.5 Put the child invocation in `loop.conf` as `LOOP_AGENT_CMD` (default `pi --print`) and have `run.sh` start **every** child by expanding it — worker and reviewer alike, never a literal command. Verify: case `state#agentCommandIsTheOnlyIndirection` sets `LOOP_AGENT_CMD` to the fixture stub and asserts both child invocations went through that value, and case `state#agentCommandRefusesAnEmptyValue` asserts a blank value stops the run with a usage exit instead of invoking nothing (D3, D15).

## 2. Pre-screen (D6)

- [x] 2.1 Write `tools/loop/prescreen.sh`: select with `tools/issues/list.sh --eligible-only`, drop `class/feature` and `status/in-flight`, map `status/open-device` to verdict `needs-device`, rank bugs before improvements, and write the queue into the state file. Verify: case `prescreen#ranksBugsFirstAndDropsFeatures` against a 6-issue fixture asserts the queue order and that the two dropped issues are reported with their reason (D6).
- [x] 2.2 Implement the filesystem sensor in `prescreen.sh`: for each candidate, resolve the cited path and, when named, the symbol with `git grep -n`, and give verdict `stale` when the path no longer exists. Verify: case `prescreen#missingPathYieldsStale` uses a fixture issue citing `app/src/main/kotlin/DoesNotExist.kt` and asserts verdict `stale` with the path quoted (D6).
- [x] 2.3 Assert in the pre-screen's own output that gates 2–5 ("one root cause", "exactly one fix", "falsifiable", "one capability") are **not** decided here, and emit them as fields the worker must fill. Verify: case `prescreen#doesNotClaimJudgementGates` asserts the queue's per-item record carries gates 2–5 as `pending` and never as a verdict (D6).
- [x] 2.4 Case `prescreen#refusesReservedLabelInSelection` — an issue carrying `agent/eligible` but authored outside the allowlist is excluded, and the exclusion names `author-not-allowlisted` (change 1, D2; D6).

## 3. Worktree and branch ownership (D4)

- [x] 3.1 Write `tools/loop/worktree.sh` `create`: `git worktree add .pi/worktrees/loop -b loop-<date> <base>`, then seed it — `git submodule update --init app/src/main/cpp/libosmscout`, copy `local.prop*`, export `VCPKG_ROOT`, and copy the gitignored skills (`openspec-*` and the loop skill) into `$W/.pi/skills/`. Verify: case `worktree#seedsAllFourInputs` runs it in the self-test's throwaway repository and asserts the worktree exists, the submodule directory is populated, `local.prop*` is present and `$W/.pi/skills/fix-loop/SKILL.md` exists (D4).
- [x] 3.2 Add `worktree.sh start-item <issue> <slug>`: assert the worktree is clean, then `git switch -c fix/issue-<N>-<slug> <base>`; and `worktree.sh end-item` committing the fix and then the archive with today's message shapes. Verify: case `worktree#oneBranchPerItemOffTheBase` starts two items and asserts two distinct branches both descending from `<base>`, and that the second refuses while the first's tree is dirty (D4).
- [x] 3.3 Implement the stale-base check: refuse to start an item when `main` has advanced past the run's recorded `base`. Verify: case `worktree#refusesWhenMainAdvanced` commits to `main` in the throwaway repository after `state.sh init` and asserts the next `start-item` exits 5 naming both commits (D4).
- [x] 3.4 Implement removal/retention: keep the worktree at run end, print its path and the branches, and remove it only under `--clean-worktree`. Verify: case `worktree#keepsByDefaultAndRemovesOnRequest` asserts the directory survives a normal run and is gone with the flag, with `git worktree list` agreeing (D4).
- [x] 3.5 Add the push guard: no `git push` on any path unless `--push yes`, and no pull-request command anywhere. Verify: the selftest case `worktree#noPushWithoutTheFlag` asserts the stub git log has no push, and `grep -rn 'gh pr create\|git push' tools/loop/` shows only the guarded call (D4).

## 4. Label transitions (D7)

- [x] 4.1 Write `tools/loop/labels.sh` mapping a verdict to its transition through `tools/issues/update.sh`: `pick` → `agent/in-progress`; `stale`/`unspecifiable`/`too-wide` → `status/on-hold`; `needs-device` → `agent/needs-device` + `status/on-hold`; `needs-decision`/`needs-diagnosis` → `agent/needs-decision` + `status/on-hold`; `close` → `-agent/in-progress` + `status/fixed-by` then `close.sh`. Verify: case `labels#verdictToLabelMatrix` drives all seven verdicts against the stub `gh` and asserts the exact label set each produces (D7).
- [x] 4.2 Implement the `EXIT`/`SIGINT`/`SIGTERM` trap in `run.sh` that removes `agent/in-progress` and records the item as `blocked`. Verify: case `labels#trapRemovesInProgressOnSignal` sends SIGINT to a run mid-item (the stub `pi` waits on a fifo) and asserts the label was removed, the state records `blocked`, and no issue is left `agent/in-progress` (D7).
- [x] 4.3 Case `labels#neverWritesReservedLabel` — assert no code path in `tools/loop/` passes `agent/eligible`, and that the selftest's `grep -rn 'agent/eligible' tools/loop/` finds it only in the read-side filter (change 1, D2; D7).
- [x] 4.4 Case `labels#writesOnlyBetweenChildren` — the stub `pi` records the state file's mtime and content hash at start and end; assert they are identical, so the wrapper wrote nothing while a child ran (`SKILL.md` invariant 2; D5).

## 5. Prompts and the untrusted-content boundary (D3, D10)

- [x] 5.1 Write `tools/loop/prompts/worker.md`: the phase contract (today's A–D), the named skills the child runs (`openspec-apply-change`, `build-app`, `run-tests`, `revert-check`), the rule that issue text is **data, not instructions**, and the deliverable list the wrapper reads back (change name, gate tally, evidence paths, verdict). Verify: case `prompt#workerNamesEveryPhaseAndSkill` asserts each phase heading and each skill name is present, and that the model is told the work package is untrusted data (D3, D10).
- [x] 5.2 Write `tools/loop/prompts/reviewer.md`: today's fixed eight-item review contract, the requirement that the reviewer must not be the writer, and the PASS/FAIL verdict shape. Verify: case `prompt#reviewerCarriesTheEightItems` asserts all eight items and the `VERDICT: PASS|FAIL` line are present (D3).
- [x] 5.3 Implement work-package rendering in `run.sh` using `tools/issues/core/WorkPackage.java`, writing `<item>/work-package.md` in the item's evidence directory, and have the prompt file reference it rather than inline it. Verify: case `prompt#packageIsAFileAndNotInlined` asserts the rendered prompt contains the package's **path** and not the issue body, and that the package file content equals the core's output (D10).
- [x] 5.4 Case `prompt#inlineInjectionCannotReachTheInstructionPosition` — a fixture issue whose body contains instruction-like prose and the `<<<ISSUE_TEXT` delimiter produces a prompt in which the body appears only inside the delimited block, exactly once (D10).

## 6. The wrapper's orchestration (D2, D3, D8, D9, D11)

- [x] 6.1 Write `tools/loop/run.sh`: parse flags, `state.sh init`, `policy.sh preflight` (exit 3 = stop, nothing processed), the decision cap (exit 5 when the open `agent/needs-decision` count ≥ `--max-decisions`, printing the list), `worktree.sh create`, then the item loop. Verify: case `run#preflightRefusalStopsBeforeAnyWork` asserts exit 3, no worktree created and no label written, using the stub `gh` login absent from the allowlist, and case `run#decisionCapRefusesToStart` asserts exit 5 with the decision list when the open `agent/needs-decision` count equals the cap (D2, D9).
- [x] 6.2 Implement the item loop: `prescreen` → `state.sh next` → `worktree.sh start-item` → `labels.sh pick` → worker `pi --print` → reviewer `pi --print` on PASS-eligible diffs → close or verdict → `state.sh done`, with the wrapper committing and never the child. Verify: case `run#oneItemOrderIsFixed` asserts, from the stub `pi`'s argv log, the exact call order and that exactly two `pi` invocations happened for one item (D3, D7).
- [x] 6.3 Implement the posture: the wrapper never accepts a child's word for closure — it reads `git status`, `git diff --stat`, the archived change directory and the gate XMLs itself, and records `blocked` when the child claims work it did not commit. Verify: case `run#childClaimWithoutCommitIsBlocked` uses a stub `pi` that prints a success report and commits nothing, and asserts the item is recorded `blocked` (not `closed`) and the issue is not closed (D3, D8).
- [x] 6.4 Implement `--dry-run`: `state.sh init`, `prescreen`, and the full report of what **would** happen — no worktree, no label, no `pi`. Verify: case `run#dryRunTouchesNothing` asserts zero stub-`gh` writes, zero stub-`pi` calls and an existing worktree is neither created nor removed (D2).
- [x] 6.5 Implement review handling: PASS → archive path; FAIL → return to the same change as a new worker task with a hard cap of 2 rounds, then `agent/needs-decision` and `blocked`; and the strictly-subtractive round-2 rule carried over from `SKILL.md` Phase E. Verify: case `run#reviewRoundsCapAtTwo` drives two consecutive FAILs and asserts `blocked` with the `needs-decision` label (D7).
- [x] 6.6 Implement `--may-close no`: the item ends with the closure `skipped`, the issue keeps `status/fixed-by` and the evidence comment is still posted. Verify: case `run#mayCloseNoLeavesTheIssueOpen` asserts the issue is open, the label is `status/fixed-by` and exactly one comment was posted (D8).
- [x] 6.7 Implement the per-item metrics record (wall time, `pi` exit status, review rounds, verdict, gate tally, closed?) into the state file. Verify: case `run#metricsRecordedPerItem` asserts every field is present after a stub run and that wall time is a positive duration (D11).
- [x] 6.8 Implement `--items 0` and an empty queue as an explicit non-error: exit 0, report `nothing eligible`, no worktree. Verify: case `run#emptyQueueIsNotAFailure` asserts exit 0 and the report line (D5).
- [x] 6.9 Treat the reviewer as read-only as a **mode**, not a promise: record the worktree's `git status --porcelain` and `git diff` before the reviewer starts and discard the round when either changed — a reviewer that wrote cannot have been the reviewer. Expose it as `--review-read-only` (default on) so the property is named and switchable. Verify: case `run#reviewerWriteInvalidatesTheRound` uses a stub reviewer that appends a line to a tracked file and asserts the round is discarded with a reason, and case `run#reviewReadOnlyOffOnlyForDiagnosis` asserts the flag is honoured (D3, D15).
- [x] 6.10 Stay sandbox-agnostic, host-only **and portable**: name no agent command, no skill, no gate command and no project value outside `loop.conf`; invoke no device tool (`adb`, `emulator`) anywhere; and require no `sbx` on `PATH`. Verify: `grep -rn 'pi --print' tools/loop/ | grep -v 'loop.conf' | grep -v '^tools/loop/fixtures/'` prints nothing; `grep -rn 'openspec-apply-change\|build-app\|run-tests\|revert-check\|gradlew\|adb\|emulator\|sbx' tools/loop/*.sh tools/loop/prompts/` prints nothing; and the selftest passes with a `PATH` that contains no `sbx` (D15, D16).

## 7. Report (D11)

- [x] 7.1 Write `tools/loop/report.sh` printing the run header (budget, base commit, flags from `loop.conf`) plus the per-item table (issue, class, verdict, change, root cause, red test, gate, review rounds, outcome), the skip list with reasons, the open decision list, the branch list and budget left. Verify: case `report#headerAndTableShape` asserts one table row per recorded item and the header's budget line equals the flags actually used (D2, D11).
- [x] 7.2 Case `report#yieldIsCountedFromState` — closed / skipped / blocked come from the state file, not from the wrapper's own counters, so a resume mid-run still reports the whole run (D5, D11).

## 8. Port the skill (D14)

- [x] 8.1 Rewrite `.pi/skills/fix-loop/SKILL.md` to point at the wrapper: the entry is `tools/loop/run.sh`, the budget comes from flags, the queue comes from `prescreen.sh`, labels are the wrapper's, and Phase F's `TODO.md` bookkeeping becomes `labels.sh` + `close.sh`. Keep every rule, phase and invariant that the port does not change, and state which ones changed. Verify: `grep -n 'TODO.md' .pi/skills/fix-loop/SKILL.md` returns only the historical/measurement references, and each of the three invariants still appears by name (D14).
- [x] 8.2 Replace `.pi/skills/fix-loop/scripts/loop-state.sh` with a thin forwarder to `tools/loop/state.sh` (so an old reference does not break) and update `REFERENCE.md`'s worktree section to state that the wrapper owns the recipe's steps 1–5. Verify: the forwarder exits 3 on `next` for a spent budget, and `REFERENCE.md` no longer instructs a human to create the worktree (D14).
- [x] 8.3 Update the `AGENTS.md` "Backlog maintenance" row and the `fix-loop` path it names so the documented entry matches the wrapper. Verify: the row names `tools/loop/run.sh`, and the path it names exists (`test -e $(grep -o 'tools/loop/[a-z.]*' AGENTS.md | head -1)`), with `tools/check-doc-routes.sh` green (D14).
- [x] 8.4 State in the change report that `.pi/skills/fix-loop/` was ported rather than renamed, and list the three mechanics that changed, so a reader of the old name is not surprised. Verify: the report names entry, state path and Phase F (D14).

## 9. Falsification (revert-checks)

One mutation each, restored after; the named case must fail, then the suite must be green again. The
gate is `tools/loop-selftest.sh`, not Gradle — name the exit code in the report.

- [x] 9.1 Lock: remove the lock acquisition in `state.sh`. `state#secondRunRefusesWithExit5` must fail. Restore; selftest exits 0 (D12).
- [x] 9.2 Stale base: remove the `main`-advanced check in `worktree.sh`. `worktree#refusesWhenMainAdvanced` must fail. Restore; selftest exits 0 (D4).
- [x] 9.3 Ordering: let `run.sh` write the state while a `pi` child runs (write before `wait`). `labels#writesOnlyBetweenChildren` must fail. Restore; selftest exits 0 (D5, invariant 2).
- [x] 9.4 Trap: remove the `agent/in-progress` removal from the signal trap. `labels#trapRemovesInProgressOnSignal` must fail. Restore; selftest exits 0 (D7).
- [x] 9.5 Child's word: make `run.sh` accept the child's success report without checking `git status`/`git diff`. `run#childClaimWithoutCommitIsBlocked` must fail. Restore; selftest exits 0 (D3).
- [x] 9.6 Decision cap: remove the open-decision query in `run.sh`. `run#decisionCapRefusesToStart` must fail. Restore; selftest exits 0 (D9).
- [x] 9.7 Close flag: make `--may-close no` close anyway. `run#mayCloseNoLeavesTheIssueOpen` must fail. Restore; selftest exits 0 (D8).
- [x] 9.8 Branch base: make `start-item` reuse one branch instead of one per item. `worktree#oneBranchPerItemOffTheBase` must fail. Restore; selftest exits 0 (D4).
- [x] 9.9 Agent-command seam: make `run.sh` call the agent literally instead of expanding `LOOP_AGENT_CMD`. `state#agentCommandIsTheOnlyIndirection` must fail. Restore; selftest exits 0 (D3, D15).
- [x] 9.10 Reviewer read-only: drop the before/after worktree comparison. `run#reviewerWriteInvalidatesTheRound` must fail. Restore; selftest exits 0 (D3, D15).
- [x] 9.11 Portability: hard-code this project's gate command or skill list in `prompts/worker.md` instead of reading `loop.conf`. `state#projectKeysAreReadFromLoopConf` must fail. Restore; selftest exits 0 (D16).

## 10. Integration and acceptance

- [x] 10.1 Write `tools/loop-selftest.sh` — creates a throwaway git repository under the gitignored
      `.pi/tmp/`, puts stub `gh` (change 1) and stub `pi` on `PATH`, runs every case in groups 1–7 and
      9, prints `PASS`/`FAIL <case>` and exits non-zero on any failure; add one `Check agent issue loop`
      step to `.github/workflows/build.yml` running it. Verify: the script exits 0 locally in an empty
      environment (`env -i PATH="$PATH" bash tools/loop-selftest.sh`) and non-zero when one case is
      deliberately broken.
- [x] 10.2 Run the wrapper's `--dry-run` against the live tracker and record its report as evidence:
      the eligible queue, the ranking, the skip verdicts, the decision list and the base commit. Verify:
      the recorded file exists and its queue count equals `tools/issues/list.sh --eligible-only --json`
      at the run's timestamp (D6).
- [x] 10.3 **Acceptance**: run one real item — `tools/loop/run.sh --items 1 --minutes 90 --review on`
      — and record the report, the branch name, the archived change directory, the issue's closure
      comment and the gate XMLs. Verify: the report shows one item with its verdict; the branch exists
      in `git branch --list`; the archived change exists under `openspec/changes/archive/`; and the
      issue is closed with its evidence comment. If no candidate is eligible, say so and record that
      instead — the acceptance then moves to `migrate-todo-to-github-issues` and this task names it as
      unproven (proposal, "Verification").
- [x] 10.4 Confirm the exclusions explicitly in the change report: no Gradle build and no test suite was
      run, because `tools/loop/**` and `.pi/skills/**` are outside every Gradle source set and no module,
      manifest, resource or native file was touched. Verify: `git status --short` lists no file under
      `app/`, `auto/`, `core/`, `osmscout-client-java/` or `buildSrc/`, and the report names this
      exclusion (`guidelines/Build.md` §2, scope block).
- [x] 10.5 Record the run's first numbers for the concept doc §6 value gate: yield
      (`closed / skipped / blocked`), per-item wall time, the 11-minute cold-build cost if paid, and the
      review round count. Verify: the numbers are in the change's evidence and the report quotes them
      rather than describing the run.
- [x] 10.6 Record in the change report that the wrapper is **sandbox-partitionable but sandbox-free**:
      the five properties of `design.md` D15 hold, no `sbx` artefact, dependency or configuration file
      exists, and running the children in a Docker Sandbox is the named future change
      `sandbox-agent-execution`. Verify: the report names all five properties, `tools/loop-selftest.sh`
      passes on a machine without `sbx`, and `git status --short` shows no container, kit or sandbox
      configuration file (D15).

- [x] 10.7 Classify `agent-issue-loop` in `tools/feature-list/specs.json` as the capability-classification gate requires, since CI fails on a shipped spec with no line there (the gate is `tools/gen-feature-list.sh --check-classification`). Verify: adding the line makes `bash tools/gen-feature-list.sh --check-classification` exit 0, and removing it makes the same command fail naming `agent-issue-loop`, so the line is load-bearing rather than decorative.
- [x] 10.8 Add the scenario-to-task traceability table for `agent-issue-loop` and verify it is complete: one row per scenario naming the case that exercises it and the task that owns that case, with no scenario unmapped and no task claiming a scenario it does not cover. Verify: the row count equals `grep -c "^#### Scenario:" specs/agent-issue-loop/spec.md` and the change report says so.
- [x] 10.9 Keep `guidelines/Backlog.md` true for this change, and verify the routing table resolves: correct every section this change makes stale — its row in §1, and the §12 rows for the decisions it lands — then run `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` and assert both exit 0. Verify: both exit 0, the §1 row for agent-issue-loop is marked landed rather than planned, and no §12 row it owns still describes an intention the implementation did not take.
## Workflow follow-up

- Archive this change only after task 10.3 has produced a real run's evidence, or after that task has
  recorded explicitly why no eligible candidate existed.
- `migrate-todo-to-github-issues` must be applied first: this wrapper consumes a tracker that already
  holds the backlog.
