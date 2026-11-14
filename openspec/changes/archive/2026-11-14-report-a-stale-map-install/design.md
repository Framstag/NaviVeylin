# Design

## Context

Evidence and motivation: `proposal.md` — Why (2026-11-13: 225 of 638 declared names absent from the stale set,
`amenity.oss: 103` per load, `Search results: 0 for 'Mensa'`; the fresh set at 2 by-design names and
`candidates=60`).

What shapes the approach, read from the tree:

- **The declared names ship.** `app/build.gradle.kts:626-630` copies `src/main/cpp/libosmscout/stylesheets` into
  the APK's `assets/stylesheets`, and `AssetCopier.ensureStylesheets()` mirrors it to internal storage; the APK
  carries `assets/stylesheets/map.ost` (362 KB). The declared set is therefore readable on device with no new
  packaging and no network.
- **A database's names are in its `types.dat`.** `TypeConfig::LoadFromDataFile`
  (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/TypeConfig.cpp:1470`) reads `uint32` format version,
  then `featureCount` features, then `typeCount` types, each a `TypeInfo::Read` record followed by its feature
  names. Names are length-prefixed strings; the counts use libosmscout's number encoding.
- **The app already reads `types.dat`, but only its header.** `BasemapDataFormat.readFormatVersion` takes the
  first `uint32` and compares it with `MapDownloadManager.DATABASE_FORMAT_VERSION` — the admission pattern this
  change reuses, together with its refusal shape (`BasemapAdmission.Rejected`).
- **The update check cannot see this.** `map-updates` compares the published `typeConfigVersion` with the
  installed one; both are 27 for the stale and the fresh set, so the check answered *No updates available*
  (measured 2026-11-13 in the map manager).
- **The native unresolved-name set is not reachable from Kotlin.** No bridge or client method exposes the
  findings `Parser::NoteUnresolvedType` keeps (`grep` over `app/` and `osmscout-client-java/` finds no
  accessor); they reach the app only as the condensed log line.

## Decisions

### D1 — The detector reads `types.dat` and the bundled `map.ost`, in `:core`

Chosen: a reader that mirrors `TypeConfig::LoadFromDataFile` far enough to collect the type-name set
(`uint32` format version gate, `featureCount` skip, `typeCount` records with each `TypeInfo` skipped), and a
pure comparison against the `TYPE <name>` lines of the bundled `map.ost`. Both halves are pure file work, so
they live in `:core` beside `DatabaseMetadata` and are unit-testable against a golden file without a device.

Alternatives and why they are not chosen:

- **Ask the native layer for the last style load's unresolved names** (the library keeps them per reference,
  `libosmscout-map/src/osmscoutmap/oss/Parser.cpp`). It is the exact truth, and it is excluded: the accessor
  does not exist, so the fix would land in the submodule plus the JNI bridge — and an item whose fix lands in
  the submodule is a person's patch followed by a gitlink bump, which the loop's pre-screen refuses
  (`create-issue`, "The fix lands in the submodule"). A change that cannot be worked unattended is the wrong
  container for this requirement.
- **Scrape the condensed log line** (`Unknown types in '<file>': N`). Excluded on principle: a log line is
  diagnostics, not data (`guidelines/Logging.md` §1); reading it would make the condensation format a contract
  and would still need the mapping from style *file* to *database*.
- **Compare only the format version** (what `BasemapDataFormat` does). Excluded as insufficient, and measured:
  the stale set and the fresh set both carry version **27**. The version says which reader can open the file,
  not which types it holds.

Risk of the chosen path: it is coupled to the on-disk layout, so a `FILE_FORMAT_VERSION` bump breaks the
reader. That is why the version gate comes first and answers *unknown* rather than a shortfall, and why the
golden-fixture case (`StaleInstallTest#theGoldenTypesDataFileYieldsItsNameSet`) fails loudly on a bump instead
of silently reporting every name missing.

### D2 — The comparison is computed on read, not stored, and not part of the daily repository check

Chosen: the map manager computes the answer when it builds its installed-map list, off the main thread (the
existing `withContext(ioDispatcher)` section), and nothing is persisted. A re-download therefore changes the
answer with no cache to invalidate, and a rolled-back or deleted map cannot leave a stale verdict behind.

Alternatives and why they are not chosen:

- **Persist the verdict beside the installation** (in `db.json` or a marker file): a second source of truth for
  a fact derived from a file that is right there, and it needs invalidation on every install, delete and
  source switch — the failure modes `map-updates`' own installed-metadata handling already has to defend
  against.
- **Fold it into the daily update check** (`map-updates`, once per 24 h): the check's contract is "is a newer
  version published", and this answer is not an update. Folding them would either make the check report a
  non-update as an update, or make the notice up to a day stale, and the map manager is exactly where a user
  looks after the fact.

Cost bound: the read is one 40–80 KB file per installed database plus one 362 KB file parsed once per list
build; both are files already being opened on that path (`DatabaseMetadata.read`), and the parse is a name-set
build, so it is done once per map-manager open and never on a render path.

### D3 — The surface is the installed-map row, with the existing download action

Chosen: the notice is part of the installed map's row in `MapManagerScreen` — the place that already owns
"installed", "download", "delete" and the basemap's own format-mismatch text (`Installed data format 26, this
build reads 27 — delete the basemap and download it again`, read on device 2026-11-13). Reusing that row keeps
one vocabulary for "your installed data and the app disagree" and needs no new screen or dialog.

Alternatives and why they are not chosen:

- **A snackbar or a one-time startup dialog**: it interrupts at a moment the user cannot act (no map open, no
  list in front of them) and is unobservable in a UI dump unless it is still on screen — the degradation the
  stylesheet-failure path already has to work around.
- **The car surface**: there is no map manager on the car, and a template row for it would be a new screen for
  a maintenance action the user performs on the phone. Out of scope per the proposal's platform constraint.

Threading: the detector is a suspend function on the map manager's existing `ioDispatcher`; the view model
holds the verdict in its immutable UI state and the composable only renders it. Nothing native runs on the main
thread, and no new lifecycle owner is introduced (`guidelines/Design.md` §4).

## Open decisions

none

## Verification

- Unit: the detector over a golden `types.dat` (a real installation's file, cut small) and a fixture `map.ost`;
  the three scenarios as named cases, plus one revert-check mutation — see `tasks.md`.
- Unit: the map manager's installed-row state for a stale verdict and a current one.
- Structural: the introduction of the notice changes no render path, so no rendering guideline owes an edit
  (`guidelines/MapStyling.md` owns the stylesheet's type vocabulary, not a map-manager row state), and the
  statement is checked by the build that packages `map.ost`.
- On device (gated): the phone recipe of `guidelines/BuildNative.recipes.md` §10e, run twice — once with the
  stale set installed (the notice appears, `N` names missing) and once after the re-download (no notice). This
  is the measurement that settles the row's wording, not an eyeball: the UI dump carries the text.
