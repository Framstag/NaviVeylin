# Tasks

## 1. The red case

- [x] 1.1 Add `surface#theStubRefusesAnEditWithNoField` to `tools/issue-tooling-cases.sh` (its entry in
      `ISSUES_SHELL_CASES` and in the dispatch): a bare `gh issue edit 21` under `GH_STUB_RECORD` exits
      non-zero, names `--add-label` and `--body-file`, records nothing; `gh issue edit 21 --add-label
      class/bug` is still recorded unchanged.
- [x] 1.2 Verify red on HEAD: `bash tools/issue-tooling-selftest.sh --case
      surface#theStubRefusesAnEditWithNoField` → `FAIL … the stub recorded a fieldless …` (log in
      `evidence/red-on-head.log`).

## 2. The fixture's refusal

- [x] 2.1 Add `check_edit_field` to `tools/issues/fixtures/gh` beside `check_close_reason`, returning on the
      first of `--add-label`, `--remove-label`, `--body-file` and otherwise printing one `stub gh:` line and
      exiting 1.
- [x] 2.2 Call it from the `edit` arm before `record_write` (`[ "$sub" = "edit" ] && check_edit_field "$@"`), and
      record the refusal in the header's write list.
- [x] 2.3 Structural check of the file it edits — this change compiles nothing, so there is no build to run:
      `bash -n tools/issues/fixtures/gh` parses, and `grep -n 'check_edit_field' tools/issues/fixtures/gh`
      shows the function and its one call site.
- [x] 2.4 Green: `bash tools/issue-tooling-selftest.sh --case surface#theStubRefusesAnEditWithNoField` →
      `PASS`.

## 3. The writers still record what they recorded

- [x] 3.1 `bash tools/issue-tooling-selftest.sh --case update#statusReplacesTheOldStatus` (single edit carrying
      the state) → PASS.
- [x] 3.2 `--case update#commentOnlyReportsNoStatusWrite` (the #165 tool-side anchor) → PASS.
- [x] 3.3 `--case close#writesTheSettledStatus` → PASS.
- [x] 3.4 `--case duplicates#youngerDuplicateIsClosedWithAPointer` → PASS.
- [x] 3.5 `--case surface#reservationIsNotBypassedByTheOtherLabelFlags` → PASS.
- [x] 3.6 `bash tools/resolve-stale-selftest.sh` (the `--body-file` writer, another driver of this stub) → all
      36 cases passed.

## 4. The gate and the evidence

- [x] 4.1 Run `./tools/loop/gate.sh` once, from the change root; quote its `LOOP-GATE:` line.
- [x] 4.2 Copy the gate's XMLs and the red log into `evidence/`, and the item's copy into
      `.pi/issue-loop/items/680`.
- [x] 4.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-gh-stub-refuses-a-fieldless-edit --root .` →
      no dangling pointer.
- [x] 4.4 Archive the change and confirm the delta merged into `openspec/specs/issue-backlog/spec.md`.
