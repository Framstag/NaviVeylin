# Tasks — fix-mutation-harness-keeps-the-tree

- [x] 1.1 Scratch root defaults to `<project>/.pi/tmp`, `TMPDIR` overrides it, and the resolved path is printed
      once on stderr.
- [x] 1.2 `backup_file` stops the harness (exit 2, "nothing was mutated") when the backup is empty.
- [x] 1.3 `restore_file` refuses an empty or missing backup, copies, and compares; used by the not-applied path,
      the normal path and the `EXIT` trap.
- [x] 1.4 `--probe empty-backup` and `LOOP_MUTATIONS_PROBE_VICTIM` so the guard is asserted against a file a test
      can inspect afterwards.
- [x] 2.1 Two cases in `tools/loop-mutations-selftest.sh`: the scratch root (default and `TMPDIR`, removed after
      the run) and the refused empty backup (exit 2, protected file intact).
- [x] 2.2 Evidence: `evidence/harness-selftest.txt` (exit 0), `evidence/mutations.txt` (67 red-when-mutated,
      0 not-noticed, 0 restore-failures), `evidence/suite.txt` (131/131 as the control).
- [x] 2.3 Spec delta (one requirement added), archived with the evidence, and issue #234 closed with the commit.
