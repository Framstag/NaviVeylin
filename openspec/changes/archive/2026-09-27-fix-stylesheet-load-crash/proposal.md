# Proposal

## Why

Stylesheet loading in the client library can leave a database without a usable style configuration, and the app then renders through it and dies natively:

```
Style error:243,8 Error: Cannot load module '.../stylesheets/include/route.oss'
Failed to load stylesheet .../standard.oss
Fatal signal 11 (SIGSEGV) in osmscout::StyleConfig::HasNodeTextStyles
  called from MapPainter::PrepareNode
```

The **client-side guard** (never adopt a configuration from a failed parse, keep the previously active style, use a safe configuration when nothing loaded successfully yet, never hand a rejected configuration to the painter, report the per-database outcome and the active style on every load path) is specified and implemented in the libosmscout repository — see `openspec/changes/client-style-load-resilience` there (this repository consumes that client through the `libosmscout` submodule on branch `naviveylin-local`).

What is missing **in this repository** is the application half: today a failed stylesheet load is only logged (`MapCanvasViewModel.kt:746-775`, `auto/…/MapScreen.kt:304-310`, `NavigationScreen.kt:303-308` — `Log.e`/`Log.w`), so a user sees an empty or degraded map with no explanation, and the app has no verified path that turns a rejected stylesheet into a message instead of a crash. Why now: the client guard is being written right now, the crash was hit on a real device run (2026-09-19), and the stylesheets ship from the submodule through `syncSubmoduleStylesheets` plus the on-device `AssetCopier` refresh — so a bad bundled stylesheet reaches every install, and the app must survive it visibly.

## What Changes

- **A stylesheet load failure reaches the user.** When the client reports that a stylesheet could not be loaded, the app SHALL show a non-blocking message and write a `DiagnosticsLog` entry, instead of only logging to logcat.
- **The previously active style stays in effect, visibly.** The app reports which style is active after the failed attempt (the client reports it; the app resolves and shows it), and the map keeps rendering with it.
- **The message is the same on the phone and on the car surfaces** (same wording, parity per `guidelines/UI.md`), non-blocking on both — navigation guidance must not be interrupted, and no Android Auto template change is introduced.
- **The map degrades instead of crashing.** After the client guard, the visible outcome of a rejected stylesheet is a map area with no content for the affected database plus the message — the app SHALL NOT crash, and other content (other databases, overlays, guidance) SHALL keep working. Verified on device.
- **All load paths report**: the interactive style switch, the persisted style applied at start, a style-flag change (daylight/night) and the on-device stylesheet refresh.
- **The app is built against the guarded client**: the submodule revision carrying `client-style-load-resilience` is bumped in this change's gitlink.
- Unchanged: the style picker, the persisted style name, the strings shown for successful selection, and every rendering behaviour with a valid stylesheet.

Not **BREAKING**: no API, preference, resource or persistence change; only the failure path gains a message, and crash becomes degradation. Rollback: revert the message/reporting edits (the client guard stays, which is desirable on its own).

## Capabilities

### New Capabilities

None — the app-visible behaviour belongs to the existing map style and render contracts.

### Modified Capabilities

- `map-styles`: the "Failed style switch keeps previous style" requirement is generalized from the interactive switch to every load path **as seen by the app** (the style in effect stays the previous one, the failure is reported once per attempt, and the user sees which style is active), and a requirement is added for the failure message on both surfaces (same wording, non-blocking, deferred when no map is visible).
- `map-render`: a requirement is added for the app-visible degradation — a database whose stylesheet could not be loaded produces an empty map area rather than a crash, and the remaining content still renders.

The client-side contract (safe configuration, never painting a rejected configuration, per-database outcome) is specified in the libosmscout change, not repeated here.

## Impact

**Sequencing dependency:** the client guard lands first in `app/src/main/cpp/libosmscout` (`client-style-load-resilience`); this change bumps the submodule gitlink and adds the app-side reporting and its verification. Until the guard is committed, the app-side reporting can be written and unit-tested against the fake client, but the crash-free degradation cannot be verified on a device.

**App / modules:**

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (~:746-775 style switch and style-flag paths, ~:1863 startup/persisted style) — report the load outcome and the active style on the app's error channel; keep the previous style.
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` (~:304-310), `NavigationScreen.kt` (~:303-308), `CarStyleApplier.kt` — carry the outcome into the car session state and surface the message.
- `core/src/main/java/com/naviveylin/core/…` (`DiagnosticsLog`) plus the app-side message channel used next to the map; car-side non-blocking overlay/message path in `:auto` (no Car App Library template change).
- Resources: one shared failure string in `values/` and `values-de` (i18n gate: `GermanTranslationCompletenessTest`, `checkHardcodedStrings`).
- Tests: `MapCanvasViewModel` style tests, `CarStyleApplier`/car screen tests, plus the extended `FakeOSMScoutClient` for the new client outcome; `StylesheetHexColorCaseTest` stays as the packaging-time guard for the one trigger it detects.
- Native: **no patch in this repository** — the submodule patch is owned by `client-style-load-resilience`; this change only bumps the gitlink (and keeps any Java override in `:osmscout-client-java` in sync if the client's Java API surface changes).

**Specs changed:** `openspec/specs/map-styles/spec.md` (requirement "Failed style switch keeps previous style" modified; one requirement added), `openspec/specs/map-render/spec.md` (one requirement added).

**Guidelines:** `guidelines/UI.md` (phone↔car parity of the failure message, and the message is non-blocking); `guidelines/MapRendering.md` (the app-visible degradation: empty map area plus message, never a crash); `guidelines/Design.md` §5 (the failure arrives as client state on the existing channel; no new thread).

**Scope:** general — phone, Android Auto and Android Automotive OS, with the same wording; no manifest, template or Gradle change.

**Rollback:** revert the Kotlin/resource edits; the client guard (separate repository) is unaffected, and the app falls back to logging-only behaviour.
