# Tasks

## 1. The defect, red before the fix

- [x] 1.1 Record the root cause with its device measurement in `proposal.md` (issue #110): the hop's entry,
      not the publish, is the late step, and the map's first frame holds the CPU. Verification: the run table
      and `.pi/logs/110-attribution-2026-10-31.md`. (spec `auto-favorites`)
- [x] 1.2 Write the case that reproduces the defect —
      `FavoritesScreenObservationsTest.aResolvedStoresStateReachesTheScreenBeforeTheBackgroundHop` — with its
      guard `anUnresolvedProviderPublishesNothingBeforeTheHop`. The case drives the new provider method, so on
      HEAD it does not compile; the red run is the revert-check of 4.1: with the fix's synchronous publish
      removed and the case present, `:auto:testDebugUnitTest --tests '*FavoritesScreenObservationsTest*'` →
      **tests=7 failures=1**, the failing case being the named one. (spec `auto-favorites` — A resolved
      store's state is published without waiting for the hop)

## 2. The provider's snapshot

- [x] 2.1 `AutoFavoritesProvider.snapshotIfResolved(): FavoritesSnapshot?` plus the `FavoritesSnapshot` value,
      with a `null` default so existing implementations and fakes keep today's behaviour. Verification:
      `:core:compileDebugKotlin` and `:auto:compileDebugKotlin`. (spec `auto-favorites` — A resolved store's
      state is published without waiting for the hop)
- [x] 2.2 `AutoFavoritesProviderImpl` answers the snapshot from the resolved store's live flows, tracking its
      own first resolution so the call never builds the native client. Verification:
      `AutoFavoritesProviderImplTest.snapshotIsNullUntilTheProviderHasResolvedTheStore` and
      `snapshotReportsTheResolvedStoresCurrentState` (tests=10 failures=0 on `:app:testAutomotiveDebugUnitTest`).
- [x] 2.3 Update the one `mockk`-based provider fake that a favourites screen builds
      (`ClickPathDefersAndConfinesTest`) to stub the new method as null. Verification: the class's own case
      `favoritesGroupRowsNeverPushOnTheCallbackPath` green.

## 3. The observation publishes before the hop

- [x] 3.1 `FavoritesScreenObservations.start()` publishes `snapshotIfResolved()` through `publishOnce` before
      the `withContext(backgroundDispatcher)`, keeping the hop and its collection. Verification: the two cases
      of 1.2 green, and `favoriteSourcesResolveOffTheHostThread` still green (the provider is still reached off
      the host thread). (spec `auto-favorites` — A resolved store's state is published without waiting for the
      hop)

## 4. Falsification and the suites

- [x] 4.1 Revert-check: remove the synchronous publish, `:auto:testDebugUnitTest --tests
      '*FavoritesScreenObservationsTest*'` must fail the named case (tests=7 failures=1, measured), restore,
      then green forced. (invariant: the resolved store's state reaches the screen without the hop)
- [x] 4.2 The changed suites green: `:auto:testDebugUnitTest` (908 cases) and
      `:app:testAutomotiveDebugUnitTest --tests '*AutoFavoritesProviderImplTest*'` (10 cases), both 0 failures.
- [x] 4.3 The gate green for the changed scope: `./gradlew test -PforceTests --no-build-cache`, plus
      `:app:assembleAutomotiveDebug -Pandroid.injected.build.abi=x86_64` for the device build.

## 5. On-device verification (device-gated)

- [x] 5.1 Re-run the cold-session recipe (`.pi/logs/run-110.sh`, AAOS `emulator-5556`, automotive debug of this
      tree, `files/favorites.json` seeded to one favourite, root row `Alle Favoriten` tapped at `(200,318)`)
      six times at host `load average` 15.8–18.4. Verification: `favorites state published after 1–9 ms` in all
      six runs, including one whose hop entered at 866 ms — against 623–1719 ms for the same recipe on HEAD.
      (spec `auto-favorites` — A slow hop does not delay the first template)
- [x] 5.2 Record the pixels: the loading row is gone within the first two screencap frames after the tap
      (`.pi/logs/110-fixed-series/`, OCR of `s0`/`s1` reads `geladen…`, `s3` reads `Favoriten | Favorites |
      1 Favorit`).

## 6. Bookkeeping

- [x] 6.1 Archive this change (`openspec archive fix-car-favorites-cold-template`), so the requirement lands
      in `openspec/specs/auto-favorites/spec.md`.
- [x] 6.2 Close issue #110 with the root cause, the fix and the device numbers as its reason and evidence
      (`tools/issues/close.sh 110 --reason … --evidence …`), which writes `status/fixed-by`.
- [x] 6.3 Update `guidelines/UI.md` §3a if it restates the loading-row timing; the section's rule is that a
      screen observation resolves its provider off the host thread, which this change keeps.
