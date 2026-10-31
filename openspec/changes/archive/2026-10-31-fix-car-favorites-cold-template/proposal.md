# Proposal

## Why

On a cold car session the favourites screen can sit on its loading row for 0.6–1.7 s although the store has
been ready for seconds. Issue #110 (`§176`, `agent/eligible`) filed it as unattributed; the device
measurement of 2026-10-31 attributed it and corrected the earlier guess.

**Root cause (measured, AAOS `emulator-5556`, tree `7110e6a8`, automotive debug):** the wait is **not** the
main dispatcher and **not** the publish path. In every slow run the late line is
`favorites resolution entered after Nms` — the `withContext(backgroundDispatcher)` that resolves the provider
was queued behind the map's first frame (stylesheet and type config parse, tile retrieval, bitmap render),
which the render loop runs on `Dispatchers.Default`
(`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt:102`). `favorites observation start` and the
resumption (`resolution done` → `state published`, 2–3 ms apart) both logged on main with no gap there.

| run | tap after surface adopt | `resolution entered` | `state published` |
|---|---|---|---|
| cold2 | ~0.8 s | 1715 ms | 1719 ms |
| cold5 | ~0.8 s | 1052 ms | 1061 ms |
| cold6 | ~0.8 s | 623 ms | 632 ms |
| cold3 / cold4 / cold7 | ~0.8 s | 3 / 4 / 13 ms | 20 / 6 / 29 ms |
| warm1 | after the map settled | 4 ms | 11 ms |

The fast cold runs start the observation *before* the first-frame work; the slow ones start it while that
work is in flight. Host `load average` was 15.8–18.4 during the slow runs, so the magnitude is CPU pressure —
which is why the earlier instrumented runs (7–20 ms) missed it. The user-visible symptom is confirmed by OCR:
`tap+0.png` in cold2/cold5 reads `Favoriten | geladen…`, the settled frame `Favoriten | Favorites | 1 Favorit`.
Evidence: `.pi/logs/110-attribution-2026-10-31.md`, `.pi/logs/110-cold{2..7}/`, `.pi/logs/110-warm1/`.

**The defect contradicts** `openspec/specs/auto-favorites/spec.md` — "The favourites list distinguishes a
loading store from an empty one": the row is meant to stand only *while* the store is uninitialised or its
first state has not been published, and here the store is ready and its state is knowable. The requirement is
met in spirit and missed in practice because the first publish depends on a hop that an unrelated first frame
delays.

**Root cause proven by:** the case `FavoritesScreenObservationsTest.aResolvedStoresStateReachesTheScreenBeforeTheBackgroundHop`.
On HEAD it cannot compile — the provider method it drives does not exist — so the falsification is the
revert-check: with the fix's synchronous publish removed and the case present,
`:auto:testDebugUnitTest --tests '*FavoritesScreenObservationsTest*'` gives **tests=7 failures=1**, the
failing case being the named one. The single resulting fix: **the observation publishes an already-resolved
store's current state at the start of the period, before the hop.** No second fix is defensible — see design.

## What Changes

- `com.naviveylin.core.AutoFavoritesProvider` gains `snapshotIfResolved(): FavoritesSnapshot?`, returning the
  four observed sources as one value when the provider has already resolved the store, and `null` otherwise.
  The default implementation returns `null`, so no existing fake changes.
- `com.naviveylin.di.AutoFavoritesProviderImpl` tracks that its lazy repository has been resolved and answers
  the snapshot from the resolved store's live `StateFlow.value`s — plain field reads, no list production, no
  database open, no native-client build.
- `FavoritesScreenObservations` publishes that snapshot before the `withContext(backgroundDispatcher)` and
  keeps the hop, so the collection is still established off the host thread; `publishOnce` drops the hop's
  repetition of the equal state.
- The car screen's provider-resolution contract does not change: the hop still resolves the provider off the
  host thread, and a provider that has not resolved the store hands out nothing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-favorites` — one ADDED requirement, "A resolved store's state is published without waiting for the
  hop". The existing loading-row requirement is unchanged and still holds; the new one bounds *when* its
  first publish happens.

## Impact

- `core/src/main/java/com/naviveylin/core/AutoFavoritesProvider.kt` (interface + `FavoritesSnapshot`)
- `app/src/main/java/com/naviveylin/di/AutoFavoritesProviderImpl.kt`
- `auto/src/main/java/com/naviveylin/auto/FavoritesScreenObservations.kt`
- tests: `auto/src/test/java/com/naviveylin/auto/FavoritesScreenObservationsTest.kt`,
  `app/src/test/java/com/naviveylin/di/AutoFavoritesProviderImplTest.kt`
- No native, no JNI, no manifest, no dependency, no persisted format. The phone path is untouched —
  `FavoritesScreenObservations` is a car screen and `snapshotIfResolved` has no phone caller.
- Additive; the interface method has a `null` default. Rollback: revert the change's commits — no state,
  format or stored data is involved.
