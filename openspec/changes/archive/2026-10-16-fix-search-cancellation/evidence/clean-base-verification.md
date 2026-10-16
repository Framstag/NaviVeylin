# Clean-base verification of the app half (patch, compile, suites)

What this file proves: the Kotlin/Java half of this change, applied alone to base **`f8f0640`** in a
dedicated worktree, **compiles both flavors from scratch and passes every focused suite this change
owns** — compiled there before it counts, per the rule agreed with the routing session.

## The patch

`build/tmp/fix-search-cancellation-app-half.patch` — 3042 lines, **23 files** (8 new), emitted by
`build/tmp/make_app_half_patch.py` against `base: f8f0640`.

- Every path is named explicitly in the generator and asserted present, so a missed file is an error
  rather than a silent omission. A throwaway index (`GIT_INDEX_FILE`) gives new files proper headers
  without touching the repository's own index.
- `guidelines/UI.md` is the one shared document: **only its 2 hunks that name this change** are taken;
  3 hunks belonging to the roundabout and nav-status-progress-rings workstreams were dropped.
- **Not included:** `openspec/changes/fix-search-cancellation/**`. `.gitignore` line 74 is explicit —
  *"OpenSpec: open changes are local WIP — only archived changes are versioned"* — so the change
  artifacts enter the repository when the archive step moves them to `openspec/changes/archive/`.
- The change artifacts are otherwise complete in the working tree: 34 files under the change
  directory, `openspec validate --strict` green.

## Where it was verified

`.pi/tmp/verify-search` — a worktree of this repository created for the purpose, detached at
`f8f0640`, submodule pinned at `dff960e34`, clean.

Two environment facts a fresh worktree needs, both unrelated to this change:

1. `git worktree add` does not check out submodules, and `git submodule update --init` **clones from
   the remote and fails** — `upload-pack: not our ref dff960e34` — because this submodule's pin is
   one of this project's unpushed commits. The clone lands in a *per-worktree* module store
   (`.git/worktrees/verify-search/modules/…`), so the main tree's submodule is untouched. The fix is
   to fetch the pin from the local submodule instead:
   `git -C <worktree-submodule> fetch <superproject>/app/src/main/cpp/libosmscout naviveylin-local:refs/heads/local-pin && git checkout local-pin`.
2. `:app`'s unit-test task cannot even be *configured* without a vcpkg install
   (`Could not create task ':app:mergeNativeSbom' … vcpkg installed-status file not found`); a symlink
   of the main tree's `vcpkg/installed` into the worktree is enough.

## Results

```
:app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin --rerun-tasks --no-build-cache
    BUILD SUCCESSFUL in 24s — 52 actionable tasks: 52 executed
    (--rerun-tasks --no-build-cache: a cache hit cannot masquerade as a compile; no `e:` lines)

:core:testDebugUnitTest  --tests SearchGateTest                              tests=8  failures=0
:app:testMobileDebugUnitTest  (clean base, 4m 30s)
    MapCanvasViewModelSearchCancellationTest        tests=6  failures=0
    MapCanvasViewModelPoiSearchCancellationTest     tests=3  failures=0
    RoutePanelViewModelSearchCancellationTest       tests=3  failures=0
    FakeOSMScoutClientSearchRequestTest             tests=4  failures=0
    PoiSearchViewModelTest                         tests=14  failures=0
:auto:testDebugUnitTest
    SearchScreenTest                               tests=15  failures=0
    PoiResultsScreenSearchLifecycleTest             tests=3  failures=0
```

## The mirror checks on the two files shared with the routing session

- `RoutePanelViewModel.kt` — **0 routing lines added** by this patch; the typed event pair
  (`MutableSharedFlow<NavigationFailure>` / `SharedFlow<NavigationFailure>`) is present **2 of 2**, and
  it came from `3a1c241` (committed), never re-applied — the patch applies cleanly to that base, which
  is the proof that it neither conflicts with nor duplicates those blocks. My 15 search markers are in
  the file.
- `NavigationEngine.kt` — **untouched**: 0 lines of diff. It keeps exactly one `endCalculation`
  declaration, whose third parameter is defaulted; the roundabout workstream's 17 added lines are not
  part of this change. A hunk for this file would have been a phantom, and the peer's reverse check
  caught it before it was emitted.

## A defect found in the base, belonging to another workstream (not this change)

`f8f0640` is **internally inconsistent for `:auto`'s test sources**: its committed
`NavigationTemplateMapper.kt` declares the parameter `iconForTurn` (lines 138, 170, 178), while its
committed `auto/src/test/java/com/naviveylin/auto/NavigationTripTest.kt:76` passes `iconFor`, so
`:auto:compileDebugUnitTestKotlin` fails on a clean base with *"No parameter with name 'iconFor'
found"*. The main tree compiles because it carries an **uncommitted** rename of that parameter back to
`iconFor` (the main tree's mapper has 0 occurrences of `iconForTurn`); the test file itself is clean,
so this is an in-flight change of the roundabout workstream, not a file this patch touches — this
patch touches neither file.

Consequence for the verification above: the two `:auto` suites were run after a **one-line scratch edit
in the verification worktree only** (`iconFor = { testIcon }` → `iconForTurn = { testIcon }`), disclosed
here because it is a change the patch does not contain.

That scratch edit is no longer needed and is no longer scratch: the same one line is now published as
`f77b814 fix: make :auto's test sources compile at HEAD`, verified on its own clean worktree —
`:auto:compileDebugUnitTestKotlin --rerun-tasks --no-build-cache` 46/46 executed, `:auto:testDebugUnitTest`
**853 tests, 0 failures**. The stale name was the outlier: the mapper declares `iconForTurn` in six places
and `NavigationManagerControllerTripTest` already passed it. Until that commit, no clean checkout of this
repository could compile `:auto`'s tests — the main tree only ever did because of the roundabout
workstream's uncommitted mapper change, which was left untouched.

## Landed and published

- **Commit:** `3fd9b3c fix: fix-search-cancellation — land the app half` on `f8f0640`, 23 files,
  2119 insertions, 224 deletions. Staged by path for 22 files (each proven byte-identical to the
  verified worktree) and **hunk-wise through the index for `guidelines/UI.md`**, whose working copy also
  carries two other workstreams' prose: staging that file by path would have committed their words.
  Their 3 hunks remain uncommitted, as do the 41 dirty entries belonging to the other workstreams.
- **Pushed, on the operator's word, to the upstream project's repositories** (the only configured
  remotes): submodule `naviveylin-local` → `e303556e2..dff960e34` at `github.com/Framstag/libosmscout`
  first, then parent `main` → `541f6dd..3fd9b3c` at `github.com/Framstag/NaviVeylin`, in that order so
  the parent's `dff960e34` gitlink is resolvable from the remote. Both branches are now in sync with
  their remotes and the remote SHAs equal the local ones.
- **Consequence of publishing:** `97fb38a` (the non-compiling commit) is now permanent public history;
  superseding it by squashing would require a force-push to a published branch and is not recommended —
  `3a1c241` repairs it append-only and says so in its message.
- **Follow-up repair, published on the same word:** `f77b814 fix: make :auto's test sources compile at HEAD` —
  `NavigationTripTest.kt:76` passed `iconFor` while the mapper declares `iconForTurn` in six places and
  `NavigationManagerControllerTripTest` already passes `iconForTurn`; that one stale call site made
  `:auto:compileDebugUnitTestKotlin` fail from any clean checkout of the published tree. Verified in a clean
  worktree at `3fd9b3c` with only that line changed: `:auto:compileDebugUnitTestKotlin --rerun-tasks
  --no-build-cache` BUILD SUCCESSFUL 46/46 executed, and `:auto:testDebugUnitTest` **853 tests, 0 failures**.
  The roundabout workstream's in-flight mapper change (parameter `iconForTurn` → `iconFor`, type
  `(TurnType) -> (RouteInstruction)`, uncommitted, 81+/17−) renames this call site again when it lands;
  none of that uncommitted work was touched.

- **Still open:** task 5.2 (forced both-flavor gate) under `TODO.md` §195, and the independent read-only
  review this change owes before archive. The archive step is what versionises this directory: it is
  gitignored while open (`.gitignore` line 74).

The verification worktree `.pi/tmp/verify-search` and the throwaway patch index were removed after the
push. The routing session's worktree `.pi/tmp/verify-routing` is theirs and was left alone.
