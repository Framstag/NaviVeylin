## Why

On a real device under Android Auto (projection and AAOS), the Preferences screen sometimes opens and shows only "Loading..." with no content — indefinitely. The screen renders a single placeholder row until an async `settingsProvider.load()` completes, and the content swap depends on exactly one `invalidate()` call. `invalidate()` is a remote host call (`IAppHost.invalidate()`, contract `@throws HostException`): on real hardware the request can fail or be dropped while the host template pipeline is busy (cold-start contention from native-client init on the car-app main thread is documented in the in-flight change `fix-aa-renderer-init-off-main`). There is no timeout, no retry, no re-invalidate on re-visibility, and no error state — so a lost `invalidate()` wedges the screen on "Loading..." forever with zero corrective action. Emulator and Robolectric never exercise real host invalidation, which is why this only reproduces on a device.

## What Changes

- **Watchdog + retry in the settings screens**: the async load gets a short `withTimeoutOrNull`-style watchdog; if the screen still shows a loading placeholder when it fires, the screen re-invalidates instead of trusting the first (possibly dropped) request.
- **HostException-guarded `invalidate()`**: every `invalidate()` in the settings screens is wrapped so a failed remote call cannot kill the load coroutine and leave the screen permanently on the placeholder.
- **Re-invalidate on re-visibility**: a lifecycle `onStart`/`onResume` observer re-invalidates so a screen re-entered after a lost render (back/forward navigation, host reconnect) repaints from current state.
- **Load-failure fallback**: when `settingsProvider.load()` fails (or the watchdog times out repeatedly), the screen shows an explicit error row ("Settings unavailable") instead of an inert infinite spinner; back still works.
- **Covered screens**: `PreferencesScreen` plus the picker sub-screens it pushes — `VehicleAnchorPickerScreen`, `OverspeedDeltaPickerScreen` — which share the identical single-shot loading pattern. Sibling screens (`FavoritesScreen`, `SearchHistoryScreen`, `AddressBookScreen`, `SearchScreen`) stay unchanged in this change; hardening them via a shared settings flow is tracked as a follow-up (scope note below).
- Additive, non-breaking; car-app UI in `:auto` only. No native/JNI changes; the submodule is untouched. Rollback: revert the change; behavior returns to today's single-shot loading.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto/preferences`: add a robustness requirement — the preferences screen and the picker screens opened from it SHALL NOT remain stuck on a loading placeholder: content appears once loaded, a dropped/failed template refresh is recovered (watchdog/re-invalidate), a load failure shows an error row with retry instead of an infinite spinner, and the screen repaints on re-visibility.

## Impact

- `auto/src/main/java/com/naviveylin/auto/PreferencesScreen.kt` — watchdog + guarded invalidate + lifecycle re-invalidate + error row.
- `auto/src/main/java/com/naviveylin/auto/VehicleAnchorPickerScreen.kt` — same loading-recovery treatment (row key `KEY_ROUTING_ANCHOR` / `KEY_FREE_DRIVING_ANCHOR` pushes).
- `auto/src/main/java/com/naviveylin/auto/OverspeedDeltaPickerScreen.kt` — same treatment (row key `KEY_OVERSPEED_DELTA` push).
- Possibly a small shared helper (e.g. `LoadingGuard` / re-invalidate-on-resume observer) if the three screens converge on one pattern.
- `auto/src/test/java/com/naviveylin/auto/PreferencesScreenTest.kt` — tests for watchdog, failed invalidate, error row; existing toggle tests stay.
- New/updated unit tests for the picker screens' loading-recovery paths (pure Kotlin/Robolectric, no host).
- Specs: `openspec/specs/auto/preferences/spec.md` (delta in this change).
- Guidelines: `guidelines/UI.md` (Android Auto section) — note the loading-placeholder rule for car screens ("settings/loading screens must self-recover; never trust a single invalidate").
- **Follow-up (not in this change)**: a shared `AutoSettings` flow on `NavigationSession` that all car screens collect — then "Loading..." becomes unreachable (see analyzed Layer 2). Out of scope here to keep the fix reviewable.
- Scope: Android Auto / AAOS only (`:auto`); the phone app has no equivalent settings dialog.
