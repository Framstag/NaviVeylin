# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case

Requirement: "A case's files select it, and the gate hands a suite the base it measured", scenarios "A file set
that names no case is refused" and "An empty intersection is stated when the caller allows it".

- [x] 1.1 Add `driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt` to `tools/loop-cases.sh`: one file set no case
      declares, run once with `--allow-empty` and once without it. Verify: on base `3986724e` the case fails —
      `--allow-empty` is an unknown argument and the driver exits 2 — and after task 2.1 it passes with the two
      statuses and the line (`evidence/red-on-HEAD.log`).
- [x] 1.2 Add the case's `# files:` declaration for the driver and the case file, and add the mutation record
      beside 53.8 in `tools/loop-mutations.sh` that removes the `--allow-empty` branch. Verify:
      `bash tools/mutate.sh --mutations tools/loop-mutations.json --list` prints the record and
      `driver#everyCaseDeclaresTheFilesItCanReach` still passes.

## 2. The flag

- [x] 2.1 Add `--allow-empty` to the driver's argument loop in `tools/loop-selftest.sh`, and make the refusal
      branch at the empty-intersection test print `LOOP-SELFTEST: no case declares the delta` with the file set and
      the declared count, then exit 0 — without the flag the branch is unchanged. Verify: the two invocations of
      task 1.1 give exit 0 and exit 2, and
      `bash tools/loop-selftest.sh --list --files app/src/main/java/com/naviveylin/MainActivity.kt` is the one that
      exits 2.
- [x] 2.2 State the flag in the driver's header comment beside the other selection arguments, including the one
      thing it does not do: a path the tree has and no case declares is still a refusal without it. Verify: the
      header names `--allow-empty` and the sentence.

## 3. The caller in CI

- [x] 3.1 In `.github/workflows/build.yml`, hand the step its delta and the flag:
      `bash tools/loop-selftest.sh --allow-empty --from-git "${{ github.event.pull_request.base.sha || github.event.before }}"`,
      and the whole set when that value is the all-zeros SHA. Verify: a pull request whose delta touches
      `app/**` runs the step green and its log carries `LOOP-SELFTEST: no case declares the delta` with the file
      set; a delta to `tools/loop/lib.sh` runs all 314 cases.
- [x] 3.2 Record the measurement the change rests on as `evidence/selection-by-delta.txt`: the six rows of
      `proposal.md`'s table with the command that produced each. Verify: `guidelines/BuildGate.md` reads 2 and
      `tools/loop/lib.sh` reads 314.
- [x] 3.3 Add the third outcome to `guidelines/Backlog.md` §7c, where the suite's cost and its file-set selection
      are stated, and keep the rule that the whole set is owed before a change that owns `tools/loop/**` is
      archived. Verify: the section names `--allow-empty` and repeats the archive rule. The §7c body itself lives in
      `guidelines/Backlog.recipes.md`, which is where the edit lands.

## 4. The bound

- [x] 4.1 Run the whole set once by hand before archive (`bash tools/loop-selftest.sh`, 314 cases at the time this
      task was written; 317 on the tree it ran against, because two merges into `main` added cases) and record its
      summary as `evidence/full-set.log`. Verify: the summary names every declared case and the suite is green;
      this is the archive's evidence, not the CI step's. Two earlier whole runs were contended by other worktrees on
      the same host and reported only the wall-clock `run#` cases; `evidence/red-attribution.txt` records them, the
      isolated re-runs and the identical failure on the base tree, and `evidence/full-set.log` is the run alone —
      `loop-selftest: all 317 cases passed ...` and `exit=0`.
