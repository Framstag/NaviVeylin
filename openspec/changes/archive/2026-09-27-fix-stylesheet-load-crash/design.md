# Design

See `proposal.md` — Why for the crash and the repository split. The client-side guard is designed in the
libosmscout repository (`openspec/changes/client-style-load-resilience/design.md`); this document covers
the application half: how the failure reaches the user, what "non-blocking on both surfaces" means, and
how the degradation is verified.

## Context

| Fact | Where |
|---|---|
| The style switch and style-flag paths already receive the client's boolean result and only log it | `MapCanvasViewModel.kt:746-775` |
| The persisted style applied at start is loaded during map init; a failure is not reported to the user | `MapCanvasViewModel.kt:~1863` (documented: the call blocks on the native DB thread) |
| The car screens apply the style through `CarStyleApplier` and log a failure | `auto/…/MapScreen.kt:304-310`, `auto/…/NavigationScreen.kt:303-308`, `auto/…/CarStyleApplier.kt` |
| The app's diagnostics entry point exists and is already used for non-fatal client failures | `com.naviveylin.core.DiagnosticsLog` |
| Failed loads now report the outcome and the active style from the client | client change `client-style-load-resilience` (D4) |
| Test doubles: the fake client overrides native methods for JVM tests | `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` (Robolectric, default sandbox — `AGENTS.md`) |

Constraint: navigation must not be interrupted, and Android Auto templates are restricted — the car
message therefore reuses an existing non-blocking surface rather than adding a template slot.

## Goals / Non-Goals

**Goals:**

- A failed stylesheet load is visible to the user on the phone and on the car, with one wording.
- The app keeps the previously active style and keeps running; the degradation is verifiable on device.
- The reporting is testable without a device (fake client) and does not duplicate messages.

**Non-Goals:**

- Validating stylesheets in the app (the client owns that; the packaging-time
  `StylesheetHexColorCaseTest` stays the only app-side pre-check for the one trigger it detects).
- A style-repair UI (a "reset style" action), crash reporting, or a new Android Auto template slot.
- Any native change in this repository: the client patch belongs to the submodule change, and this
  change only bumps the gitlink and keeps the Java override module in sync if the client's Java API
  grows.

## Decisions

### D1 — One reporting seam in the app, fed by the client outcome

**Chosen:** a single app-side helper takes the client load outcome (success/failure, active style) and
performs the two side effects: a `DiagnosticsLog` entry and a user-visible message. Every load path
calls it with the client's result — the interactive switch, the style-flag change, the persisted style
at start and the stylesheet refresh.

- Alternative A — report inline at each call site: four near-identical code paths (phone switch, phone
  startup, two car screens) that drift in wording and duplicate messages.
- Alternative B — derive the failure from a state flow of "active style": the app would have to infer
  failure by comparing the requested and active styles, which cannot distinguish "still loading" from
  "refused", and would double-report when both the request result and the state change are observed.
- Risk: a single seam must be reachable from both the phone (Compose) and the car (Car App Library)
  without leaking platform types → keep it a plain function taking the outcome and returning the message
  text, with the surfaces deciding how to show it.

### D2 — Non-blocking presentation, per surface, same wording

**Chosen:** a shared string shown non-blocking on both surfaces — the phone shows it next to the map
(snackbar-style, the pre-existing `uiState.snackbarMessage` → `SnackbarHost` path), the car posts a
**one-shot, low-importance notification** (`CarStyleLoadNotifier`, channel `map_style`, silent,
auto-cancel). No Android Auto template change.

**Corrected while implementing (2026-09-19):** this decision originally said "the existing `:auto`
overlay/message path used for other non-fatal client failures". That path does not exist — verified in
`:auto`: no snackbar, no toast, no banner, and no generic message surface; the templates expose only
header and action-strip slots (`MapTemplateFactory.kt:38-105`, `NavigationTemplateFactory.kt:39-44`),
and the `CarHintContent` hint (`nav_hint_neutral`) exists only in the navigation template. The
notification is the non-blocking surface that (a) is available on both car form factors (AAOS head
units and Android Auto projection), (b) needs no template or host-contract change, and (c) cannot
interrupt guidance or the surface lifecycle — it is system-rendered, silent, low importance, and the
session state is untouched (owner decision, option A).

- Alternative A — a dialog: blocks the map, and no dialog pattern is available on the car.
- Alternative B — a new template slot (a message row in `MapTemplate`/`NavigationTemplate`): touches
  both car screens and the host template contract, disproportionate for one message; still available
  later without changing the spec.
- Alternative C — the navigation template's hint slot only: visible while navigating, silent on the
  map/free-driving screens, so the spec's "the car session surfaces the same wording" would hold only
  for one screen.
- Alternative D — diagnostics only (`DiagnosticsLog` + the car's DiagnosticsScreen): no transient
  notice, weakest visibility.
- Risk: the message must not be lost when it is produced while no surface is visible (app backgrounded,
  car session not yet active) → the phone keeps the pending report in the app state and shows it when a
  map surface becomes visible (spec scenario "Failure without a visible map"); a notification is
  independent of the surface and is delivered by the system.
- Risk: the notification can be suppressed (POST_NOTIFICATIONS not granted on API 33+) → posting is
  best-effort and the failure is always recorded in `DiagnosticsLog`, so the information is never
  lost; the phone path is unaffected.

### D3 — Report exactly once per attempt

**Chosen:** the report is produced from the load call's outcome only, not additionally from the state
flow or from the diagnostics of the client.

- Alternative — report in both places and de-duplicate by content: brittle, and wording changes would
  break the de-duplication.
- Risk: the client's own error list could be surfaced twice (once by the client's diagnostics, once by
  the app message) → they are different channels with different audiences (logcat vs user), which is
  intended; the app message is emitted once.

### D4 — Sequencing with the client change

**Chosen:** the app-side reporting, the string, the tests and the gitlink bump land in this change, and
the submodule revision bump is a task of this change, executed after `client-style-load-resilience` is
committed in the submodule.

- Alternative — implement the app side first and bump the gitlink later: the app-side code would be
  written against an unreleased client outcome and could not be verified end to end (the fake is not the
  client).
- Risk: the client change could change shape while being implemented → the app side depends only on the
  load outcome and the active style being reported, which the client spec fixes.

## Threading and lifecycle

- No new component, thread or dispatcher. The style load call already runs off the main thread (it
  blocks on the native DB thread — `MapCanvasViewModel.kt:1863`); the outcome is published through the
  existing state/error channel and the message is shown on the main thread.
- The car surfaces receive the outcome through their existing session state (`CarStyleApplier` call
  sites), and the message goes through the existing overlay path.
- A pending report survives until a map surface is visible; nothing else is retained, and no new
  lifecycle observer is needed.

## Risks / Trade-offs

- **Message fatigue**: a stylesheet that fails on every start would repeat the message each launch →
  acceptable (it is a real, persistent defect the user should know about); no suppression, because the
  alternative is a silently empty map.
- **The car surface may have no suitable non-blocking path** → confirm while implementing; if only the
  existing overlay is available, use it; a new template slot is out of scope and would be its own change.
- **`DiagnosticsLog` growth** from repeated failures → throttle at the reporting seam if it proves
  noisy; the user-visible message stays once per attempt.
- **Verifying the degradation needs a deliberately broken stylesheet on device** → use the
  internal-storage stylesheet copy (the refresh path is idempotent and can be restored by the next
  app start), and record the logcat evidence.
- **i18n gates**: a new user-visible string must exist in `values/` and `values-de` and must not trip
  `checkHardcodedStrings` → part of the task list, verified by the existing tests.
- **Rollback** is app-only and independent of the client change.

## Migration Plan

No data, settings or resource migration: the persisted style name and the stylesheets on disk are
unchanged. Order: client guard lands in the submodule → gitlink bump + app reporting (this change) →
on-device verification on phone and car. Rollback: revert the app-side edits; logging-only behaviour
returns while the client keeps its guard.

## Verification

- **Unit (JVM, fake client):** the three load paths on the phone (switch, flag, persisted style at
  start) and the car call sites produce exactly one report, keep the previous style, and name the active
  style. The fake client is extended with the load outcome and active style (Robolectric default sandbox
  per `AGENTS.md`).
- **Resources:** the new string exists in `values/` and `values-de`; `GermanTranslationCompletenessTest`
  and the hardcoded-string gate stay green.
- **Build:** both flavors, all three ABIs (`build-app` skill), no new warnings.
- **Existing tests:** full suite green via the `run-tests` skill with the `test-results` directories
  cleared first (`TODO.md` §17). Class batching was needed when this change ran (`TODO.md` §33 had no
  declared fork heap yet); since `fix-auto-unit-test-heap-overflow` declared the `:app`/`:auto` budgets it
  is a diagnostic fallback only (`guidelines/Build.md` §6).
- **On device (phone, then car):** with a deliberately broken stylesheet, `adb logcat -s NaviVeylin |
  grep -i "style error"` shows the failure, there is no `Fatal signal 11`, the map area degrades, the
  message appears non-blocking, guidance continues (car), and restoring a valid stylesheet recovers
  without a restart.
