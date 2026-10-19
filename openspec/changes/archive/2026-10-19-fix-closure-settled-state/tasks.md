# fix-closure-settled-state — tasks

- [x] 1.1 Reproduce the defect: a case that closes an escalated item and reads what state it carries. Red
      on HEAD (`the closure wrote no status label — the item is closed and still carries status/on-hold`).
- [x] 1.2 `close.sh` writes `closeStatus`/`retiredStatus` through `update.sh --status` before
      `gh issue close`; the role is resolved before the first write.
- [x] 1.3 The fail-closed branch: a refused write leaves the item open and exits with that write's code;
      a closure that fails after the write names the residue.
- [x] 2.1 The independent review found `update.sh` swallowing a rejected `gh issue edit`; the write now
      lands before the report and its failure is a failure. Case
      `close#keepsTheItemOpenWhenTheStateCannotBeWritten` is red without it.
- [x] 3.1 `roles.retiredStatus` declared in the project's profile and in the foreign fixture; the roles
      comment names `tools/issues/close.sh` as a reader.
- [x] 3.2 Cases for the not-planned sibling, the loop's already-settled no-op, and a profile that names no
      settled state.
- [x] 4.1 Mutations A1.1–A1.4, each red with the mutation in place and green after the restore.
- [x] 4.2 `check-portability` green (97 values, 40 engines) and its 26 self-test cases.
- [x] 5.1 Documented the three-step write order in `.pi/skills/close-issue/SKILL.md`.
- [x] 5.2 Measured on the live tracker: #188 closed and read `status/fixed-by` in one call.
