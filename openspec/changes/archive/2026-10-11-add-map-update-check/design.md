# Design

## Context

See `proposal.md` — Why. The state this design works from:

- The upstream client contract (`Documentation/MapRepository.md` §6) says a client needs exactly two files:
  the region index `names.json` and *its own* version slot's `db.json`, and that absence of that slot means
  "unavailable; keep local state".
- The app already stores each installation's `db.json` inside the installed directory
  (`DatabaseDownloader.writeMetadata`), so the local baseline exists on disk, already parsed by
  `DatabaseMetadataParser` (`generatedAt` is read but never compared).
- `RepositorySource.metadataOf(idPath, version)` already reads one leaf's metadata, and
  `MapManagerViewModel.probeLeaf` already caches that per leaf for the tree view.
- `RepositoryBasemap.install` already installs a basemap slot non-destructively: download into
  `<dir>.incoming`, verify, `swap()` with a `.backup` and a restore on failure. It is the template for a
  regional database update.
- The running-client seams exist: `OSMScoutClient.openDatabases(dirs)` closes and reopens the whole
  database set as one coordinated change (spec `native-database-open`), `MapDownloadManager.registerMapDirectory`
  registers one directory, and `BasemapRegistrar.apply` is the worked example of "change the data a live
  client renders and tell the renderers": register/reload natively, then `BasemapReloadNotifier.bump()`,
  which the phone map and the car renderer already collect.
- `BasemapViewModel.refreshFromRepository` sets `updateAvailable = false` on purpose (spec
  `basemap-discovery`'s carve-out) — that is the one-line gap on the basemap side.
- Constraints: no new Gradle dependency (a shipped one also needs an entry through the licence policy gate,
  `guidelines/Build.md` §9), no native/JNI change, `guidelines/Design.md` §4 threading rules,
  `guidelines/Logging.md` §3 (identity only, never a coordinate).

## Goals / Non-Goals

**Goals** — the behavior of `specs/map-updates/spec.md`: a once-a-day and an on-demand comparison of the
active repository source with what is installed, one state per compared item, an updates section that lists
what can be updated, a queue behind one action, and a non-destructive replacement that a running app renders
without a restart.

**Non-Goals**

- Comparing anything the built-in provider installed: it records no comparable baseline, so the provider
  keeps today's "install or delete" model (`TODO.md` §160 stays open for it).
- Any native, JNI or submodule change.
- Transferring an update without the user asking for it (the daily check only compares), and any
  notification on discovery.
- A car surface for the updates list: the car has no map-manager screen and keeps rendering what is
  installed (`cross-variant-ui-parity` — an accepted deviation, recorded in `map-updates` and `UI.md` §1).
- Background execution while the app is not running: no `WorkManager`, no alarm, no headless process.

## Decisions

### D1 — The check hangs off the app start, throttled by a persisted timestamp

Alternatives: **(a)** a process-scoped checker launched from `NaviVeylinApp.onCreate`, running at most once
per 24 h against a persisted `lastCheckAt`; **(b)** `WorkManager` periodic work; **(c)** a check whenever the
map manager screen opens; **(d)** an `AlarmManager` trigger.

**Chosen: (a).** It matches the request ("if started at a day"), costs no dependency and no licence
inventory entry, needs no headless process, no notification and no background-execution exemption, and it
reuses the app-start hook that already exists (`NaviVeylinApp.onCreate` eagerly activates other
process-scoped work the same way). (b) was rejected on cost and on shape: it still needs the same comparison
code, plus a Hilt worker, plus a headless run whose result nothing can display until the app opens anyway.
(c) fails the specification outright — the check must run without a screen being opened. (d) wakes the
process for a request whose result is only displayable in the app.

### D2 — An installed directory is matched to its leaf by walking the region index

Alternatives: **(a)** fetch `names.json`, compute `RepositoryUrlPlanner.databaseDirectoryName(idPath)` for
every leaf, and match the installed repository directories by name; **(b)** store the leaf's identifier path
in the installed `.source.json` marker and probe directly; **(c)** reverse a directory name by splitting on
`-`.

**Chosen: (a), as the single mechanism.** (c) is impossible: identifiers contain `-`
(`north-rhine-westphalia` is one segment), which is exactly why `databaseDirectoryName` joins with `-` in
the first place. (b) looks cheaper per check but does not remove (a): the maps already on a device carry no
identifier path, so (b) would need (a) as its fallback and the check would carry two mechanisms for one job.
(a) also yields the localized name the section shows, and it detects the leaf a repository no longer
publishes. The cost is one extra request per check, on top of one per installed map.

A directory name that matches no leaf, or matches more than one leaf, is reported as **unknown** — never
probed at a guessed path. Two leaves colliding on one directory name is a pre-existing ambiguity of
`databaseDirectoryName` (the second install would already overwrite the first); the check reports it rather
than acting on it.

### D3 — The comparison base is the metadata the installation already carries

Alternatives: **(a)** the installed `db.json` against the server's `db.json` for the client's version slot;
**(b)** an app-side record of the installed version and timestamp; **(c)** file sizes and checksums.

**Chosen: (a).** It is on disk, self-describing, travels with a copied directory, needs no new persistence,
and is the comparison the upstream contract names. (b) duplicates state that exists and can drift from the
data it describes. (c) answers "is it corrupt", not "is there a newer one".

For the basemap the base is the availability manifest: the installed slot's metadata against the manifest
entry for the version the client reads (`BasemapManifestParser` already selects it), which is the second
half of the same contract.

### D4 — The result is persisted in the settings file, not in memory

Alternatives: **(a)** two new `AppSettings` fields (`mapUpdateCheckAt`, the update records) written through
`SettingsStorage.update {}`; **(b)** a `@Singleton` holding it in memory only; **(c)** its own JSON file
beside the maps.

**Chosen: (a).** The specification requires the list to be the same after a cold start and the next start
not to probe again; (b) fails both. `SettingsStorage` already serializes every read-modify-write under one
lock (spec `settings-persistence`) and reads with `ignoreUnknownKeys`, so the fields are additive and a
rollback is inert. (c) adds a third persistence mechanism with its own writer discipline and its own
lifetime problem.

The stored result is **replaced** by each completed check, and reconciled against the installed set when it
is read, so a map the user deleted in the meantime cannot leave a stale row.

### D5 — An update is installed through `.incoming` + swap, then the live client is re-pointed

Alternatives for the install: **(a)** download into a sibling `<dir>.incoming`, verify, then swap as
`RepositoryBasemap.install` does; **(b)** download straight into the installed directory, as the plain
download path does; **(c)** install a new version-suffixed directory and re-register that.

**Chosen: (a).** (b) is forbidden by the specification: `DatabaseDownloader` deletes its target directory
first, so the map is unusable during the transfer and lost if the transfer is interrupted. (c) changes the
map's identity — the directory name comes from the leaf path and keys the saved viewport and
`lastMapPath` — and would need garbage collection for the directory it replaced.

Alternatives for making a running process render the replacement: **(i)** after the swap, re-open the
installed database set (`openDatabases`) and bump `BasemapReloadNotifier`; **(ii)** refuse the update while a
navigation session owns that database; **(iii)** defer the swap to the next app start.

**Chosen: (i), to be settled by measurement** (see Verification). (ii) is the documented fallback, not the
default: it would have to be invented before the evidence exists, and it removes an action the user
explicitly asked for. (iii) makes a tap do nothing visible, which is indistinguishable from a broken update.
(i) is the precedent the codebase already follows for the same class of change: `nativeDeleteMap` unlinks
the files of an open database and re-runs the lookup, `openDatabases` exists precisely to close and reopen
the whole set in one coordinated change, and the basemap swap already runs against a live client and
re-points it through `BasemapRegistrar`.

If the measurement shows an active navigation session cannot survive the reopen, the change adds the guard
of (ii) — and that is a **spec change**, not an implementation detail: it needs a new scenario in
`map-updates` and a user-visible explanation, and the report must say so instead of quietly shipping it.

**Staging data is recognised, not hidden.** The transfer's `.incoming` directory and the swap's `.backup`
directory live beside their target *inside* the maps tree, which is the tree the installed-map scan walks —
and a `.backup` holds a complete database, so it carries the `types.dat` that scan looks for. The tempting
fix, making the scan ignore dot-directories, was rejected: a directory name is not a claim about content, a
blanket prefix rule would also hide an installation whose identifier happens to start with a dot, and the
failure would be silent. Instead `SlotInstall.stagingTargetOf` names what a staging directory stages for,
so every consumer of the maps tree can tell an installation from what an attempt left behind:
`MapUpdateChecker` skips them and reports them as leftovers (`MapUpdateCheckResult.Compared.leftoverStaging`),
and the app-start path removes them. Removal happens there and **not** on the check-on-demand path: a manual
check can run beside a transfer, and deleting the `.<name>.incoming` a transfer is writing into would fail
that transfer, while losing a `.<name>.backup` in the middle of a swap would lose the installation it
exists to protect. On the app-start path no transfer of this process can be in flight yet, which is the
whole safety argument for sweeping there. The installer still sweeps its own target before every attempt, so
a leftover is removed at the latest by the next update of that map.

### D6 — "Update all" is a queue of one transfer at a time

Alternatives: **(a)** sequential, one item per iteration on the IO dispatcher, each with its own
cancellation flag; **(b)** all transfers in parallel, which the existing download lifecycle can already
host; **(c)** hand the list to a native downloader.

**Chosen: (a).** Parallel regional downloads multiply peak storage, memory and bandwidth for no gain on a
phone, and the platform caps `dataSync` foreground services at 6 h per 24 h
(`guidelines/Regulatory.md` §6) — a queue that cannot exhaust that cap is the safer shape. (b) is not less
code: the queue is a loop over the same call. (c) does not exist for repository slots and would be a native
change, which the proposal excludes.

Each item's transfer reuses the existing lifecycle: `MapDownloadService.start`/`update`/`stop`, the shared
wake lock, progress published into the active-downloads section, and a per-item cancellation flag in the
shape of `MapManagerViewModel.cancelledRepositoryDownloads`. A failure is reported per item and the loop
continues; a cancelled item's partial data is removed by `DatabaseDownloader` itself.

### D7 — The check control and the last-checked line live in the updates section

Alternatives: **(a)** inside the updates section; **(b)** in the provider row beside `[Refresh]`; **(c)** in
the installed-maps header.

**Chosen: (a).** The upstream comparison exists only for a repository source, so its control belongs to the
surface that only exists for that source; the provider row is always visible (chips, `[Refresh]`, and for a
repository the URL field and its test action) and `[Refresh]` already means "re-read the map listing", which
a check is not. (c) collides with the installed section being hidden while searching. The section is
therefore present for a repository source whether or not anything is updatable — which is also what makes
"am I up to date?" answerable, and is why `map-updates` states the section's presence as a rule of its own
rather than as "omit when empty".

### D8 — The check is sequential, suspend, and off-main

Alternatives: **(a)** one probe at a time on the IO dispatcher; **(b)** probes in parallel behind a small
semaphore.

**Chosen: (a).** The number of probes is bounded by the installed maps (single digits) and each response is
a small JSON document, while a repository on a local network can be slow: sequential keeps the result order
stable, makes cancellation trivial, and bounds peak memory. (b) buys latency nobody is waiting for.

### D9 — The rendered-data change reuses the existing reload signal

Alternatives: **(a)** `BasemapReloadNotifier` plus a re-open of the database set; **(b)** a second notifier
for regional data; **(c)** restart the activity.

**Chosen: (a).** The signal already means "the set of loaded map data changed — drop cached tiles and
re-render", and its two consumers (phone `MapCanvasViewModel`, car `MapScreenObservations`) are exactly the
ones that must react; (b) would be a second name for the same fact, wired into the same two consumers;
(c) is what the specification forbids.

`guidelines/MapRendering.md` §5 currently says this contract is for a *basemap* download/update/delete; the
wording widens to a replaced regional database (same change).

## Threading and lifecycle

- **`MapUpdates` (`:app`, `@Singleton`)** owns the check state, the daily throttle and the persisted result.
  It carries a process-lifetime scope (`SupervisorJob` + `Dispatchers.IO`, never cancelled — the Application
  is its owner, mirroring the engine's process-lifetime rule in `Design.md` §4) with its own fault handler,
  so a failed check can never reach the main thread's uncaught handler and kill the process.
- **`NaviVeylinApp.onCreate`** only launches; it blocks on nothing and touches no native code. The check body
  runs on the IO dispatcher.
- **The probes** are suspend functions over `HttpUrlFetcher`; no native call, no file write on the caller's
  thread. Publication is a `StateFlow` write, which the map manager screen collects on the main dispatcher.
- **The install (swap)** does file I/O on the IO dispatcher. **The re-open** (`MapDataRegistrar.apply`) calls
  `openDatabases` — a native call — never on the main thread and never from a car host callback, from the
  same IO context the queue runs in, wrapped so any throwable is confined and reported (`Design.md` §4).
- **Lifecycle:** a check is cancelled with the process, and a cancelled check is not recorded as the day's
  check, so the next start retries. A queued update's transfer is protected by the existing foreground
  service; if the platform ends that service, the transfer stays incomplete, the installed map is untouched
  and the leftover `.incoming` directory is removed before the next attempt.
- **One writer per tree:** the change edits `SettingsStorage` alongside the concurrent fix-loop changes, so
  it is one session's work and one builder at a time (`guidelines/Build.md` §2).

## Risks / Trade-offs

- **A swap under an active navigation session breaks the session** → the measurement in Verification decides
  it; the fallback (refuse while navigating) is named in D5 and needs a spec delta rather than a silent
  guard.
- **A repository reachable only occasionally (a LAN source, a phone hotspot) makes the daily check fail
  often** → a failed check is not recorded, the state is "unknown", the section says the check did not
  complete and why, and nothing in the UI claims an update. No retry storm: the check runs once per app
  start at most.
- **A check on a metered connection costs one small GET per installed map** → bounded and small; no
  transfer happens without a tap. If it ever matters, the request is a plain GET that the user's repository
  serves cheaply.
- **Two leaves colliding on one directory name** → reported as unknown, never probed at a guess (D2). The
  pre-existing collision in the *download* path is out of this change's scope and is named in the report if
  found.
- **A persisted result that outlives its data** (a map deleted, a source switched) → the result is replaced
  by each completed check and reconciled against the installed set when read (D4).
- **The updates section adds a permanent line to the map manager for repository sources** → accepted in D7;
  it is what makes the manual check reachable when nothing is updatable.
- **A region update changes rendered pixels for every cached tile of that region** → the reload signal
  bumps the epoch and clears the tile cache (`MapRendering.md` §5), so the first frame after the swap is
  fully re-rendered.

## Verification

**Unit tests (`:core`, pure JVM, no device):**

- `MapUpdateCheckTest` — the decision matrix cell by cell: newer `generatedAt` (update), equal or older
  (up to date), slot absent (no longer offered, install kept), installed metadata unreadable (unknown),
  a newer version slot published (no update), a directory matching no leaf or two (unknown), a
  provider-installed directory (no state, no request), one failing item not hiding the others.
- `RepositoryDatabaseInstallerTest` — installed data untouched while the transfer runs and after a failure,
  verification failure installing nothing, `.incoming` removed, a successful swap replacing the directory
  contents and keeping the directory name.
- `MapUpdatesTest` (`:app`) — the first start of the day runs the check, a second start within 24 h does
  not, an unfinished check is retried, a running check is not started twice, a manual check counts as the
  day's check, the stored result is replaced and not merged, the state survives a simulated restart.
- `MapManagerUpdatesQueueTest` (`:app`) — one transfer at a time, per-item cancellation, a failure not
  stopping the queue, the queue's items leaving the update-available state as they complete.
- Compose: `MapUpdatesSectionTest` — the section for a repository source and its absence for the provider,
  the nothing-to-update line, the no-check-yet line, the count, `[Update]` per row and `[Update all]`; plus a
  `compose-geometry` assertion for its position in the section order (below active downloads, above the
  basemap section).

**Revert-checks** (one mutation each, restore, then a forced green run with
`-PforceTests --no-build-cache`):

| Mutation | The case that must fail |
|---|---|
| Drop the 24 h throttle | `MapUpdatesTest#aSecondStartInTheSameDayRunsNoCheck` |
| Record the check as completed even when it failed | `MapUpdatesTest#anUnfinishedCheckIsRetriedAtTheNextStart` |
| Swap before verifying the transferred files | `RepositoryDatabaseInstallerTest#aFailedVerificationLeavesTheInstalledMapIntact` |
| Read the installed state as update-available when the slot is absent | `MapUpdateCheckTest#aSlotThatIsNoLongerPublishedIsNotAnUpdate` |
| Let the queue start every transfer at once | `MapManagerUpdatesQueueTest#theQueueRunsOneTransferAtATime` |
| Merge a new result into the stored one instead of replacing it | `MapUpdatesTest#aNewCheckReplacesTheStoredResult` |

**On-device (the recipes are `guidelines/Build.md` §10/§11, the `device-check` skill):**

1. **The comparison against a real repository.** Serve a mapgen repository locally (its layout and its own
   decision-matrix test script are `Documentation/MapRepository.md` §6 and
   `scripts/mapgen/client-check-test.sh`), install a region, then regenerate that region's slot and let the
   check run. Measure: one line per compared item naming the item and its state, plus one line per
   completed check, read with `adb logcat -s NaviVeylin` and from the in-app diagnostics — identity only, no
   coordinates (`Logging.md` §3).
2. **The rendered data changed without a restart** (the spec's observable). Measured as a screenshot pair of
   the same viewport before and after the swap, compared with the `pixel-check` skill, together with the
   diagnostics lines of the swap and the database re-open. **If the harness cannot produce a genuinely
   regenerated slot for that region, the pixel half is unmeasured** and the report says so, giving the
   metadata change plus the re-open lines as the evidence instead.
3. **The navigation-survival question of D5** (the reason this design needs a device run). With a route
   navigating over a repository-installed map, trigger the update and observe: guidance continues, the
   instruction and road lines keep updating, the map keeps rendering, and the diagnostics show the re-open.
   The failure signature to look for is the route graph or a road lookup failing right after the re-open. If
   it fails, the outcome is the D5 fallback plus a spec delta — recorded, not silently patched.
4. **Not measurable on a stationary emulator:** the platform ending the `dataSync` service mid-update (it
   needs the 6 h cap) — covered by the existing `basemap-cancel` behavior plus the unit case, and stated as
   such in the report.

## Migration Plan

Nothing to migrate: no installed data changes shape, no setting is removed, and the two new settings fields
are written only by this change and ignored by the previous build. The check itself modifies nothing on
disk. Steps: land the code, run the daily check once by opening the app, confirm the section renders for a
repository source and stays absent for the provider.

**Rollback:** revert the change. The check, the section, the queue and the manifest comparison are gone; the
stored fields are ignored; a leftover `<dir>.incoming` or `<dir>.backup` directory is inert to the installed
map and is swept by the next install attempt (and by a new sweep at check time).

## Open Questions

Deferrable, none of which changes the specs, the approach or the task breakdown:

- Whether an installed database reported as *no longer offered* should also be surfaced in the UI (it is not
  actionable, so today it only appears in the diagnostics). If it should, that is a new scenario in
  `map-updates`, not a task here.
- Whether the maps-tree *discovery* consumers — the phone map canvas, the car session, the map manager's
  installed list — should each recognise staging data with `SlotInstall.stagingTargetOf` as the check does.
  They can, and it would close the window between the app start and the sweep, in which a leftover from an
  earlier crashed update can still be discovered and opened as a map. It is not in this change's scope
  (which owns the check and the app-start cleanup), and it is recorded as its own backlog entry.
