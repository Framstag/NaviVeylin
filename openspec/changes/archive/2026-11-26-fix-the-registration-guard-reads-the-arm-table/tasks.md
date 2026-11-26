# Tasks — the registration guard reads the arm table beside the bodies

- [x] 1. The red case first, on the base commit `37b3b83f` as it stands: `driver#anArmTableInASecondFileIsCompared` in
      `tools/loop/tests/loop-cases.sh` — one row in `LOOP_SHELL_CASES`, one arm in `loop_run_shell_case`, one body that
      builds a nested suite whose two bodies and two rows sit in its cases file and whose arm table sits in the second
      file beside it. Leg 1 (complete arm table) is green before and after; leg 2 (one arm removed) asserts exit 1 and
      reads exit 0 on the base. Verified by
      `bash tools/loop/tests/loop-selftest.sh --case driver#anArmTableInASecondFileIsCompared`, whose `FAIL` line and
      tally are in `evidence/red-on-the-base-commit.log` (a shell self-test carries no JUnit XML, so its tally is
      carried in the log beside the `FAIL` line).

- [x] 2. The scan takes the files (`tools/loop/tests/loop-selftest.sh`): the cases file always, and
      `tools/loop/tests/loop-case-index.sh` when the tree has it, handed as one argument list to the same awk; the
      comment block at `:513-524` states "the files that were just sourced". Verified by task 1's case green
      (`evidence/green-on-the-change.log`), `driver#aCaseWithNoSelectionRowIsRefused` still refusing with its own text
      unchanged (`evidence/green-a-case-with-no-selection-row.log`), and
      `bash tools/loop/tests/loop-selftest.sh --list` printing 579 cases where the base prints 578
      (`evidence/list-579-cases.txt`).

- [x] 3. The revert-check: one mutation — the second path removed from the scan (`:526`'s `if [ -f … ]` made
      `if false`), so the index file is handed to no awk — returns task 1's case to red for its own reason
      (`a lost arm in the second file is refused, never skipped: PASS stub#kept: expected '1', got '0'`), and the
      restore returns it to green. Verified by the three runs, each copied into `evidence/`:
      `red-when-the-second-path-is-removed.log`, `green-when-restored.log`, with the file restored byte for byte
      (`cmp` clean against the copy the mutation was taken from) and green again in
      `evidence/green-on-the-change.log`.

- [x] 4. The change completed before the gate: the base's red log, the green run, the mutation's red run, its restore
      and the two further focused runs, plus the `--list` output, all copied into this change's `evidence/` at the
      moment they were produced; `bash tools/loop/evidence-check.sh
      openspec/changes/fix-the-registration-guard-reads-the-arm-table --root .` clean; the change archived with
      `openspec archive --yes`; and then `./tools/loop/gate.sh` run once, its `LOOP-GATE:` line quoted. Verified by the
      evidence check's own `ok` lines and the gate's tally.
