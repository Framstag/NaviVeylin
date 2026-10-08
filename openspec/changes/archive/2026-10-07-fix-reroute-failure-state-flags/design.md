# Design

## Context

See `proposal.md` — Why. The constraints that shape the approach:

- `NavigationEngine` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`) owns one
  process-scoped state (`core/NavigationState.kt:61-62` carries `isRerouting`/`isOffRoute`) and
  publishes it on the main dispatcher. `confirmReroute` (`:908`) sets both flags and starts the
  attempt through `calculateAndStart(..., fromReroute = true)`.
- One attempt at a time is tracked by a token: `beginCalculation` publishes it,
  `endCalculation(token, outcome)` (`:623`) clears the `calculation` field and is **token-scoped**
  — it returns `false` (and the caller returns early) when a newer attempt has replaced the token.
  The three terminal paths are: `onSuccess` (→ `startInternal`), `onError` (`:488`) plus the
  synchronous `catch` (`:514`), and `onCancel` / `cancelAcquisition` (`:550`).
- Only `onRouteInstructions` (`:803-804`) clears the two flags today; `stopNavigation` (`:653`)
  resets the whole state. Both are correct and stay untouched.
- The consumer that makes the defect visible is the car template: `NavigationTemplateMapper:288`
  returns `loadingTrip()` (a `Trip` with `setLoading(true)` and no step) whenever `isRerouting` is
  true, and `hasTripChanged` (`:247`, `:364`) compares the flag. The phone keys its tint on
  `isOffRoute` (`NavigationStateOverlay.kt:153`).

## Goals / Non-Goals

**Goals:**
- `isRerouting` is true exactly while a reroute attempt is in flight, from every surface's view.
- One rule the reader can grep: the attempt that set the flag clears it on its own terminal
  outcome.
- The change stays inside the engine's Kotlin state handling: no new state field, no new
  dispatcher, no native call, no consumer change.

**Non-Goals:**
- Changing what `isOffRoute` means or when its red tint disappears (owner decision, see D3).
- Changing the car template, its wait notice or its refresh policy.
- Re-offering or auto-retrying a reroute after a failure — the driver keeps the running guidance
  and the published error (deferred, see Open Questions).
- Device verification: the car symptom is a pure function of the state (`tripFromState`), so it is
  asserted at the mapper instead of on a head unit. No device run is owed (an optional check is
  listed in tasks.md).

## Decisions

### D1 — The clear lives at each terminal site, through one private helper
`endRerouteAttempt()` — a single `_state.update { it.copy(isRerouting = false) }` — is called from
the two failure handlers and from the cancel path, each *after* its `endCalculation` guard.

- **Alt A (chosen).** Explicit, greppable, and each call site already knows it belongs to an
  attempt that set the flag.
- **Alt B — derive it: `isRerouting = (state.calculation != null)`.** Rejected: `calculation` is
  also non-null for a surface-less acquisition (`navigateTo` from the route panel), so the car
  would show its loading trip for every destination entry.
- **Alt C — clear inside `endCalculation` for the `ERROR`/`CANCELLED` outcomes.** Rejected: that
  function is token-scoped bookkeeping shared by non-reroute attempts; a flag write there gives
  every existing and future caller a reroute semantic it does not have, and makes the cancel path
  implicit.

The guard order matters: the clear must follow `if (!endCalculation(token, …)) return@launch`.
A superseded attempt that fails late must not end the reroute the newer attempt is still running.

### D2 — The failure publishes the flag and the error in one state update
The failure handlers write `isRerouting = false` together with `errorMessage`/`errorOrigin`, so no
emission shows "not rerouting, no error" in between.

- **Alt: two updates.** Rejected: `NavigationTemplateMapper.hasTripChanged` compares `isRerouting`,
  so an intermediate emission rebuilds the car template twice and can replace the loading trip with
  guidance for one frame before the error is known.

### D3 — `isOffRoute` is not touched on failure (owner decision, 2026-10-07)
The vehicle is genuinely still off route, so the tint is the honest cue; the flag keeps its two
existing clearers (`onRouteInstructions`, `stopNavigation`), which is what the spec delta states.

- **Alt: clear both.** Rejected: a visible tint flicker, and the driver loses the off-route cue
  while guidance still runs from the stale route.
- **Alt: re-derive `isOffRoute` from the last native position state.** Rejected for now: it needs a
  queryable position state (today it is only written per estimate) for a rare path. If a device pass
  ever shows the tint lingering unhelpfully, that is its own change — not this one.

### D4 — The cancel path clears the flag only when the cancelled attempt was a reroute
`cancelAcquisition` clears the flag when `isRerouting` is true, and leaves it alone otherwise, so
the route panel cancelling its own surface-less acquisition never writes a reroute flag it did not
set.

- **Alt: track the attempt's kind in a token map.** Rejected as bookkeeping the state already
  carries: `isRerouting` is true iff the attempt in flight is a reroute (`confirmReroute` is only
  reachable while navigating).

### D5 — Threading and state ownership are unchanged
Every write stays inside the existing `scope.launch(Dispatchers.Main)` blocks (`guidelines/Design.md`
§3, §4); no write moves to a background dispatcher, and the failure handlers keep their current
shape (they already publish on Main).

### D6 — Verification is engine-level plus mapper-level, no device
The state assertions live in `NavigationEngineRerouteTest` / `NavigationEngineCalculationCancelTest`
(scheduler-driven, real engine, `FakeOSMScoutClient`); the car-visible consequence is a pure-function
assertion in `NavigationTemplateMapperTest`. That covers both named surfaces without a head unit —
which matters because `adb devices` is empty (2026-10-07).

## Risks / Trade-offs

- **A late failure of a superseded attempt clears the live reroute's flag** → the clear sits behind
  the `endCalculation` token guard and is covered by a case that fails an *old* token while a newer
  attempt is in flight (the existing `cooldownBlocksARerouteCascade` shape shows how reroutes are
  serialised).
- **The cancel path now writes state the acquisition path never set** → guarded by the `isRerouting`
  check plus a boundary case asserting a cancelled surface-less acquisition leaves the flag false.
- **Silent regression to the old behaviour** (someone re-adding a flag set without a clearing path)
  → the revert-check tasks name the mutation that must break the two new cases, and the rule is
  recorded next to the lease rule in `guidelines/Design.md` §4.
- **Trade-off accepted:** after a failed reroute the driver sees the error and the old guidance with
  no automatic retry; a retry design is deferred deliberately (Open Questions).

## Migration Plan

None: the flags are in-memory state with no persistence, no native handle and no wire format.
Deployment is the normal app update. Rollback is a revert of the change's commit
(`NavigationEngine.kt` + the two test files + the spec delta); no stored state would be left
inconsistent, and the spec delta archives with the change.

## Open Questions

- Whether a failed reroute should offer an explicit retry (or a shorter cooldown) is deferrable: it
  needs a UX decision and, if pursued, an engine seam to re-run the gate. It does not change this
  change's specs, approach or tasks. If a device pass later shows the off-route tint lingering
  unhelpfully after a failure, that is D3's deferred alternative and belongs to its own change.
