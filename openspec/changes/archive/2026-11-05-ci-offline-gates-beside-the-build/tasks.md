# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/ci-gate-layout/spec.md` for the contract.

## 1. The measurement this change rests on

Requirement: "A check that needs no build input runs beside the build", scenarios "The offline checks are not in
the build's job" and "A cheap check still answers first".

- [x] 1.1 Record the base run's step clock as `evidence/step-clock-before.txt`: the command and its output, from
      which job's step the build waits for and which steps precede it. Verify: the file names the 23 min 36 s of
      steps 5–24 and the 10:22:55 start of `Install vcpkg dependencies`.
- [x] 1.2 Classify every step of `build` by its inputs — needs a build input / needs nothing — and record the
      classification with the step's own comment quoted beside it. Verify: twenty steps are classed "needs
      nothing" and each quotation is in the workflow file. The classification is the workflow's own:
      `.github/workflows/build.yml:392-400` states it for the twenty ("Every check below needs a checkout, git, jq
      and a JDK — nothing the build already has, and nothing the build reads: no Android SDK, no NDK, no vcpkg, no
      Gradle, no token") and names the three that stay in `build` with their reason. `evidence/split-check.txt`
      counts the two sets as they stand (`gates: 20 steps`, `build: 29 steps`), and `evidence/step-clock-after.txt`
      shows the three in place between the job start and the install.

## 2. The split

- [x] 2.1 Add the `gates` job to `.github/workflows/build.yml`: `runs-on: ubuntu-24.04`,
      `permissions: contents: read`, no caches, and the twenty steps of task 1.2 in their present order. Verify:
      `yq '.jobs.gates.steps | length' .github/workflows/build.yml` is 20, and
      `bash tools/check-action-runtimes.sh` still exits 0 (the pin is repeated, not referenced).
- [x] 2.2 Remove those steps from `build`, keeping `Check libosmscout Android-free outside Android/`,
      `Check the JVM-only test graph` and `Check the dex gate's producer` where they are. Verify: the steps that
      remain are exactly the ones task 1.2 classed "needs a build input", and no step name appears in both jobs
      (`grep -o 'name: .*' .github/workflows/build.yml | sort | uniq -d` prints nothing).
- [x] 2.3 Record both job names in `guidelines/Build.md` §3, beside the commands table, so the required checks are
      read against the workflow rather than against a memory of it. Verify: the section names `gates` and
      `Build debug APK (all ABIs)`.
- [x] 2.4 Classify the new capability in `tools/feature-list/specs.json` (`area`: `build-tooling`,
      `userVisible`: false, `surfaces`: []), because the classification gate reads an open change's delta spec and
      stops the release run for an unclassified one. Verify: `bash tools/gen-feature-list.sh
      --check-classification` exits 0 and prints `unclassified: 0`.

## 3. The measurement after

- [x] 3.1 Dispatch the workflow and record its step clock as `evidence/step-clock-after.txt` in the same shape as
      task 1.1. Verify: the build job's steps no longer precede `Install vcpkg dependencies`, and the run's
      duration is shorter than the base run's by at least the gate job's own duration. No dispatch was needed: a
      run on main already carries the split (42093153526, head `caf44b92`, read 2026-11-05), and its clock is the
      file. `Install vcpkg dependencies` is step 15 and starts 2 min 46 s into the job, against 26 min 31 s on the
      base run; the five jobs all start in the same second. The evidence file states what that run cannot measure
      — it is red in the gates job, and its vcpkg install was a cache restore, so the two runs' totals are not
      comparable; the pre-install block is.
- [x] 3.2 If the after measurement disagrees with the expectation (the run is not shorter), record it and stop:
      a split that does not move the clock is a cost with no benefit, and the reversal is task 2.2 undone. The
      measurement agrees on the property the split was drawn for, so nothing is reversed; the total-duration
      comparison it could not settle is recorded in `evidence/step-clock-after.txt` rather than claimed.
