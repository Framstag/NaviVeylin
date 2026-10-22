# Design — fix-updates-queue-failure-signal

## The root cause

`updateOneItemRunsOnlyThatOne` (`MapManagerUpdatesQueueTest.kt:191-210` on HEAD) drove one update and then
asserted the installed `types.dat`, the other installation's `types.dat` and the request log. It never read
`uiState.updateTransfers`. Three states therefore reach its first assertion with the same message,
`expected:<[new types]> but was:<[installed]>`:

1. the update **failed** — a refused write on this host (`EDQUOT`), a failed verification, or a swap that could
   not be made — in which case `runUpdate` has already published `UpdateTransferState.Failed`
   (`MapManagerViewModel.kt:329-333`) and the installation is untouched by design;
2. the update **did not finish** — the state the item's own diagnosis assumed;
3. the update **never started** — `runUpdate` returns before any row when the active source is not a repository
   (`MapManagerViewModel.kt:311-312`), which the earlier worker measured and filed as a second, separate
   mechanism (`.pi/issue-loop/items/217/evidence.md` §3).

The case could not name which, and the swap path had no case at all: `RepositoryFailure.InstallFailed` is
translated to `R.string.update_install_failed` at `MapManagerViewModel.kt:411-412` and is named in no test.

## The fix, and why it is the single one the decision allows

Two assertions, one file, no product change:

- the one-item case reads its row first (`assertFalse(… row is UpdateTransferState.Failed)`, message prints the
  row) so a recurrence is attributed by the assertion that failed, not by a rerun;
- `aFailedSwapIsReportedAndKeepsTheInstallation` makes the swap fail deterministically and pins the row, its text
  and the untouched installation.

The swap is made to fail at `SlotInstall.replace`'s **first** move: `replace` deletes the previous attempt's
backup and then moves the installation aside (`SlotInstall.kt:103-110`), so a backup that cannot be deleted —
its body carries the read-only permission, which is what a directory refused by the filesystem looks like —
leaves `Files.move(target, backup, REPLACE_EXISTING)` failing with `DirectoryNotEmptyException`. The exception is
raised outside `replace`'s try, reaches `RepositoryDatabaseInstaller.install`'s catch (`:91-94`) and becomes
`DownloadOutcome.Failed(RepositoryFailure.InstallFailed)`, with the installation never touched. Measured before
the case was written, as a standalone JVM probe: the move fails `DirectoryNotEmptyException: bak`, and the
target directory is still present.

## Blast radius

One test file, and the class runs in both `:app` flavours. The case owns the maps root between its own calls: the
read-only body is what stops `SlotInstall.sweep` from removing the backup at the start of the update, so the
`finally` restores the permission and sweeps the staging directories the attempt leaves (`.hamburg.incoming`,
which this failure mode does not clean up) — otherwise the next case in the class would start against a
non-removable backup and its own swap would fail.

`guidelines/Build.md` §6 gains one bullet, so the rule survives the change: a case whose only assertion is a
post-condition of a queue also asserts the queue's own row state.

## Alternatives the evidence eliminated

- **Re-run the case N times per configuration and bisect neighbours (the item's own option 2).** Eliminated by
  measurement: 20/20 isolated green, and the full-suite runs that carried the item's configuration failed 69/184/5
  cases, all disk-write failures on a host whose `/tmp` was 5.7 GB of 7.7 GB full with 4 627 stale basemap
  directories. The host cannot produce an attributable signal, so the runs would buy nothing.
- **Close the item as mis-diagnosed on that measurement (option 3).** Not eliminated — deferred: the owner's
  decision is "option 1, then option 3", and the decision belongs to the run's reporting after this change, not to
  this delta.
- **Assert on `isUpdating` instead of the row.** Eliminated: `isUpdating` is a queue-wide flag and is false for
  both a failed item and a finished one; it cannot distinguish the states the item confuses.
- **Assert that no staging data survives the failed swap.** Eliminated as out of scope: the first-move failure
  leaves `.hamburg.incoming` behind (`SlotInstall.replace`'s catch, `:119-125`, is the branch that cleans it up),
  which is a product finding of its own and would make this delta two mechanisms. The case sweeps it in its
  `finally` instead of asserting it away.
- **Make the swap fail by a `FakeRepositoryServer` failure injection.** Eliminated: the server's failures reach
  `DownloadOutcome`, not `SlotInstall.replace` — a transfer failure is exactly the state the case must not
  produce, because a transfer failure and a swap failure have different text and different cleanup.

## Rollback

Delete the new case and the hardened assertion, and the guideline bullet; the class returns to its five cases and
no other file moves.
