# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The measurement and the selection

Requirement: "The suite runs as legs on separate runners, and a leg is its own verdict", scenario "A leg runs the
cases of its own selection".

- [x] 1.1 Record the per-group case counts and the base step clock as `evidence/legs-baseline.txt`:
      `bash tools/loop-selftest.sh --list | cut -d'#' -f1 | sort | uniq -c` and the step clock of the loop's step
      in run 41810844107. Verify: `run` reads 114 and the step reads 17 min 15 s. **Recorded: 114 / 138 / 62, and the
      step 09:59:51 → 10:17:06 = 1035 s.**
- [x] 1.2 Verify the driver's group filter already states its selection, so a leg cannot silently cover less:
      `bash tools/loop-selftest.sh --list --group run | grep -c .` is 114 and a real `--group run` run's summary
      names the selection. Verify: the summary line carries `--group run` (issue #276's shape).

## 2. The legs

- [x] 2.1 Add the matrix job for the loop's self-test to `.github/workflows/build.yml`: `runs-on: ubuntu-24.04`,
      `permissions: contents: read`, `fail-fast: false`, and one entry per leg (`run`; the six tooling groups; the
      rest). Verify: `yq -r '.jobs["loop-suite"].steps[].name'` prints the leg step, and
      `bash tools/check-action-runtimes.sh` still exits 0 for the repeated pin.
- [x] 2.2 Decide whether the second leg is one invocation per group in one step or a repeatable `--group`, and
      record which in `design.md`'s table. Verify: the leg's log carries one summary per invocation, each naming
      its own selection. **Taken: one invocation per group in one step**, because the driver's `--group` is a single
      value (the last wins) and a repeatable flag is an engine change the legs do not need.
- [x] 2.3 Keep the mutation harness's step in the leg that owns the driver, so a mutation case and the driver it
      mutates are read in one log. Verify: `tools/loop-mutations-selftest.sh` runs in exactly one leg.
- [x] 2.4 Teach the suite map's pin that a leg's `--group <selection>` is the driver's own entry:
      `gate#theMapCoversEveryPipelineStep` strips it from both sides before comparing, record 54.11 falsifies the
      strip, and 49.2 is anchored to `count_matching`. Verify: the `tooling` leg of run 42090111006 failed on the
      narrowed command, and its leg of run 42094414229 passed — `evidence/legs-after.txt`, `evidence/revert-check.txt`.

## 3. The measurement after

- [x] 3.1 Dispatch the workflow and record each leg's duration as `evidence/legs-after.txt`. Verify: no leg
      exceeds half the base step's duration, and the slowest leg is named. **Recorded: `run` 361 s, `tooling`
      416 s (the slowest), `rest` 107 s, against the base step's 1035 s.**
- [x] 3.2 State the split beside the suite's cost — `guidelines/Backlog.recipes.md` §7c, where the section's body
      lives, with `guidelines/Backlog.md` §7c naming it — including what a leg owes and that the whole set is still
      owed before a change that owns `tools/loop/**` is archived. Verify: the section names the legs and repeats the
      archive rule.
