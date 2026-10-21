# Design — the harness must not be able to destroy the tree it proves

## D1 — The backups live beside the tree, not on the host's tmpfs

The scratch tree holds a copy of every file a mutation touches, and the restore source of last resort. `/tmp`
is a 7.7 G tmpfs on the measured host and was 88 % full; `.pi/tmp` is gitignored, lies on the same filesystem as
the tree, and is where the loop's own scratch already lives (`LOOP_TMP_DIR`). `TMPDIR` still wins, because a
caller may have a better place — and the resolved path is printed once, so nobody has to guess.

## D2 — A backup that did not land stops the harness before it mutates

`cp` failing is the only moment at which the harness can still be harmless: nothing has been written yet. So
`backup_file` checks the copy is non-empty and exits 2 with "nothing was mutated" rather than proceeding with a
zero-length backup. This is the guard that would have turned the 2026-10-21 incident into one line of output.

## D3 — A restore never writes a backup it could not take, and verifies what it wrote

`restore_file` refuses an empty or missing backup, copies, and then compares. Three call sites use it: the
not-applied path, the normal path, and the `EXIT` trap. The trap cannot exit (it is the exit), so it reports
instead — but it never copies an empty backup either.

## D4 — The guard is asserted by a probe, not by a comment

The harness already carries probes for its two other guards (`no-apply`, `stays-green`); the empty backup is the
same kind of fact. `--probe empty-backup` builds the exact state the full tmpfs produced and must exit 2, with
the protected file untouched — a file the caller names, so a test can look at it after the harness has exited.

## Rejected

- **Checking free space at start** instead of guarding each write: free space can vanish between the check and
  the copy (it did, under a parallel build), and the copy's own result is the fact that matters.
- **Writing the backup atomically (`.part` + `mv`)**: it fixes visibility, not emptiness — a zero-length `.part`
  would be moved just as happily. The size check states the requirement directly.
