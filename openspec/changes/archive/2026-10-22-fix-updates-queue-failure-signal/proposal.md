# fix-updates-queue-failure-signal

## Why

Issue #217 is a single failure of `MapManagerUpdatesQueueTest.updateOneItemRunsOnlyThatOne` in the mobile unit-test
suite on 2026-10-20 (`expected:<[new types]> but was:<[installed]>`, gate run of 2026-10-20T13:21Z, `2068 tests
completed, 1 failed`), green on every rerun since. The item's own measurement was taken by the earlier worker of
this run — `.pi/issue-loop/items/217/evidence.md`, base `2e732f0`:

- 20 sequential forced runs of the class alone (`:app:testMobileDebugUnitTest -PforceTests --no-build-cache
  --tests com.naviveylin.ui.mapmanager.MapManagerUpdatesQueueTest`): **20 / 20 BUILD SUCCESSFUL**, the class
  `tests="5" failures="0"` in each.
- three runs of the configuration the item blames (the full mobile suite): `tests="5" failures="0"` for this
  class in all three, while the suite as a whole failed 69 / 184 / 5 cases and the failing stacks carried
  `java.io.IOException: Der zugewiesene Plattenplatz (Quota) ist überschritten` (`EDQUOT`) — the host's own
  writes failing, 92 such writes in the second run.
- no rate per configuration and no neighbour bisect: with two runs per configuration "one flaky case" is an
  attribution, not a proof, and on this host the suite's failure set is decided by disk writes.

**Owner decision, recorded on the item 2026-10-21T16:45:52Z** (`<<<ITEM_DECISION` in the work package), option 1
of three, "then (3)": harden the case first — assert the one-item update's row state, and add the missing
failed-swap case — and treat a single failure as host-resource noise until the host can support the bisect. That
decision is implemented here and is not re-derived.

**Root cause, one sentence:** `updateOneItemRunsOnlyThatOne` asserted only the installed file's content after
`update(item)` + `advanceUntilIdle()`, so the two states that produce that message — a `Failed` row for an update
that did not complete (a refused write, a failed verification, a swap that could not start) and a transfer that
had not finished when the assertion ran — are indistinguishable in its output, and no case anywhere exercised the
swap-failure path at all (`RepositoryFailure.InstallFailed` is named in five production files —
`MapManagerViewModel.kt:411`, `RepositoryDatabaseInstaller.kt:93`, `RepositoryBasemap.kt:101`,
`RepositoryFailure.kt:65` and the two `strings.xml` — and in **no** test:
`grep -rln "InstallFailed\|update_install_failed" app/src/test core/src/test auto/src/test` is empty).

**This is a measurement change, red-free on HEAD, by that decision.** The case it adds passes on HEAD because
HEAD already reports a failed swap correctly; nothing in the product is wrong that this change fixes. What is
measured instead is the delta's own absence: `updateTransfers` holds no `Failed` row for a finished one-item
update, and a swap that cannot complete is reported with the installation-failed text on that item's row alone.
Both are asserted by cases and both were shown to fail when the behaviour they pin is removed (§ Falsification).

- HEAD, the class alone (`evidence/head-focused.log`): `tests="5" failures="0"`, timestamp
  `2026-10-22T00:45:17.203Z`.
- the same run after the change (`evidence/after-focused.log`): `tests="6" failures="0"`, timestamp
  `2026-10-22T00:45:49.857Z`.
- falsification (`evidence/mutation-focused.log`, `evidence/mutation-TEST-…xml`): the installation-failed text is
  mutated to the download-failed one, and `aFailedSwapIsReportedAndKeepsTheInstallation` fails alone —
  `tests="6" failures="1"`, `ComparisonFailure: with the text a failed installation gets, not a failed
  transfer's expected:<The [update could not be installed — the map you had is unchang]ed> but was:<The [map
  could not be download]ed>`, timestamp `2026-10-22T00:46:10.460Z`.

**Owning capability:** `map-updates` — the requirement this change adds is "An update that cannot be installed is
reported for its own item", the failure half of "An update replaces the installation only when it is complete".
**Owning guideline section:** `guidelines/Build.md` §6 "Test constraints" — the section the Documentation Map
routes "writing or fixing a test" to — and, for the data path the case drives, `guidelines/Design.md` §9 "Data,
persistence & search".

**Surface, counted before the plan was fixed:** `updateTransfers` is asserted by exactly one case file
(`grep -rln updateTransfers app/src/test core/src/test auto/src/test` → `MapManagerUpdatesQueueTest.kt`);
`InstallFailed`/`update_install_failed` by none. So: **1 case added, 1 case hardened, 0 fixtures realigned, one
missing-case family** (`InstallFailed`: 5 production files, 0 cases). No product file, no resource, no build
script.

## What changes

- `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerUpdatesQueueTest.kt`:
  - `updateOneItemRunsOnlyThatOne` asserts the item's own row carries no `UpdateTransferState.Failed` before it
    asserts the installed file, and the assertion message prints the row — so a recurrence names whether the
    update failed (and with which text) instead of reading as an unfinished transfer.
  - new case `aFailedSwapIsReportedAndKeepsTheInstallation`: a one-item update whose completed, verified transfer
    cannot be swapped in — the previous attempt's backup cannot be removed, so the swap's first move is refused —
    reports `UpdateTransferState.Failed` carrying `R.string.update_install_failed`, keeps the installation the
    app had, and leaves no other listed item's row carrying a failure. The case restores the maps tree it made
    unremovable, so it cannot fail the next case in the class.
- `guidelines/Build.md` §6: one bullet recording the rule the item produced — a case whose only assertion is a
  post-condition of a queue also asserts the queue's own row state, with this measurement as its reason.
- `openspec/specs/map-updates/spec.md` (delta): the added requirement and its two scenarios, each naming its case.

No production code, no resource, no dependency, no build script. **Rollback:** delete the new case and the
hardened assertion; the class returns to five cases, and nothing else moves.

## Out of scope, and why

The item's own neighbour bisect (option 2) and the closure as mis-diagnosed (option 3) are the other two branches
of the recorded decision; option 3 follows this change in the run's own reporting, not in this delta. The
`Failed`-row gap the earlier worker found at `MapManagerViewModel.kt:311-312` (a non-repository source returns
from `runUpdate` silently, with no row and no message) is a **second** mechanism producing the same silence and is
not part of this delta — the item's eligibility is one root cause, and the case for that path would need its own
item.
