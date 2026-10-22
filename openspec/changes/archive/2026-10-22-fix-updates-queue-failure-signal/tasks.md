# Tasks — fix-updates-queue-failure-signal

The delta is `map-updates`'s *An update that cannot be installed is reported for its own item*
(`specs/map-updates/spec.md`). No compiled **product** file changes, so the delta is the test-only case of
`guidelines/Build.md` §2's scope block: the changed cases run through their module's `--tests` filter, and the
gate runs once because the pathspec includes `*.kt`.

## 1. The two cases

- [x] 1.1 `updateOneItemRunsOnlyThatOne` reads its row before it reads the installed file and asserts the row is
      not an `UpdateTransferState.Failed`, with the row printed in the message. Reference: the delta's second
      scenario.
      **Verification:** the class alone on HEAD is `tests="5" failures="0"` (`evidence/head-focused.log`,
      `2026-10-22T00:45:17.203Z`); with the change it is `tests="6" failures="0"`
      (`evidence/after-focused.log`, `2026-10-22T00:45:49.857Z`).
- [x] 1.2 New case `aFailedSwapIsReportedAndKeepsTheInstallation`: a backup of the previous attempt that cannot be
      removed makes `SlotInstall.replace`'s first move fail after the transfer completed and verified; the case
      asserts the `Failed` row, its `R.string.update_install_failed` text, the unchanged installation and no
      failure on the other item's row. Reference: the delta's first scenario.
      **Verification:** green in the class run above; falsified by mutating the text mapping
      (`MapManagerViewModel.kt:411-412` → `R.string.map_download_failed`), which fails this case alone with
      `ComparisonFailure: with the text a failed installation gets, not a failed transfer's …`,
      `tests="6" failures="1"` (`evidence/mutation-focused.log`, `evidence/mutation-TEST-…xml`,
      `2026-10-22T00:46:10.460Z`). The production file was then restored — `git status` names only the test file.
- [x] 1.3 The case restores what it made unremovable in a `finally` and sweeps the staging directories the
      attempt leaves, so it cannot fail the next case in the class.
      **Verification:** all six cases green in one run of the class, and again in the gate, which runs the class
      in both `:app` flavours.

## 2. The record

- [x] 2.1 The delta spec under `specs/map-updates/spec.md`, one added requirement, each scenario naming its case.
      **Verification:** `openspec validate fix-updates-queue-failure-signal --strict` → `Change
      'fix-updates-queue-failure-signal' is valid`.
- [x] 2.2 `guidelines/Build.md` §6 gains the rule this item produced.
      **Verification:** `tools/check-doc-routes.sh` green in the gate.

## 3. The gate

- [x] 3.1 `./tools/loop/gate.sh` once, from the change root, after the mutation was restored.
      **Verification:** the numbers and the per-file tally in `evidence/gate.log` and
      `evidence/gate-tally.txt`, quoted in `evidence.md` and in the worker's report.
