# Tasks

## 1. The red cases: a tracker bigger than the window, and a read it overran

- [x] 1.1 Add `case_shaping_anItemOutsideTheNewestPageIsListed` and `case_shaping_aReadThatCannotCoverTheSetIsRefused`
      to `tools/issues/tests/issue-tooling-cases.sh` with one fixture helper (`shaping_label`, `shaping_tracker` — a
      tracker of `<total>` issues, the labelled ones carrying the run's own statement), and register
      `shaping#anItemOutsideTheNewestPageIsListed` and `shaping#aReadThatCannotCoverTheSetIsRefused` in
      `ISSUES_SHELL_CASES` and in `issues_run_shell_case`.
      Verify: `bash tools/issues/tests/issue-tooling-selftest.sh --list` names both.
- [x] 1.2 Run both on the base tree (`git stash` is not available to this child, so the run is taken before the fix
      lands) and record them red:
      `bash tools/issues/tests/issue-tooling-selftest.sh --case 'shaping#anItemOutsideTheNewestPageIsListed'`.
      Verify: exit 1 with `the oldest item a run handed back is listed` failing — the queue read the newest 400 of a
      405-item tracker, so both labelled items (`#1`, `#2`) fell outside it and the answer was
      `no item awaits shaping (agent/needs-shaping)` with `LOOP-SHAPING-COUNT 0`.
- [x] 1.3 Copy the red run's log and its tally into `evidence/` as `red-on-base-commit.log` and
      `red-on-base-commit.xml` (`tests="2" failures="2"`).

## 2. Count the set before the read

- [x] 2.1 `tools/issues/shaping.sh`: drop the `limit=400` default, resolve `$ISSUES_REPO` into owner/name, ask the
      label-scoped `totalCount`, read `count + 1` (or `--limit` when the caller names one), refuse
      `shaping-set-uncovered` on a full read, and print the counted set in both human branches.
      Verify: both cases from 1.1 pass; `tools/look.sh 'limit=400' --path tools/issues/shaping.sh` finds nothing.
- [x] 2.2 Update the header the queue's `--help` prints (`sed -n '2,24p'`): the exit line names the refusal, and the
      counted-set paragraph states the window and the coverage line.
      Verify: `bash tools/issues/shaping.sh --help` prints the corrected lines and stops at the usage line.

## 3. The owning guideline

- [x] 3.1 `guidelines/Backlog.md` §5, the shaping-inbox paragraph (`:309`): the sentence that says `shaping.sh` lists
      every open item carrying the label gains the counted-set clause and the refusal.
      Verify: `tools/look.sh --section '^## 5\.' --path guidelines/Backlog.md` names it, and
      `tools/look.sh 'shaping-set-uncovered' --path guidelines/Backlog.md` finds it.

## 4. Falsification

- [x] 4.1 Restore the pre-fix read (`limit=400`, unscoped) into `shaping.sh` and confirm
      `shaping#anItemOutsideTheNewestPageIsListed` fails on it — the constant window comes back as full as the window
      the queue asked for, so the run is refused as `shaping-set-uncovered` instead of being listed; restore the file
      and confirm green again.
      Verify: `evidence/mutation-window-restored.log` (red: exit 1) and `evidence/green-when-restored.log`.
- [x] 4.2 Drop the `shaping-set-uncovered` refusal and confirm `shaping#aReadThatCannotCoverTheSetIsRefused` exits 0
      instead of 3; restore and confirm green again.
      Verify: `evidence/mutation-refusal-removed.log` (red: exit 1) and `evidence/green-when-restored-refusal.log`.

## 5. Evidence, archive, gate

- [x] 5.1 Copy the green runs and the suite tally into `evidence/` (`green-suite.log`, `green-on-change.xml`,
      `tests="191" failures="0"`), run `bash tools/loop/evidence-check.sh <change dir> --root .`, and fix what it
      reports.
- [x] 5.2 Archive with `openspec archive --yes` and run the gate once.
