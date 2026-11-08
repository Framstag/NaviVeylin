# Proposal

## Why

`NavigationSession.onStart` publishes `carSessionPresence().setActive(true)` and `onDestroy` clears it
with `setActive(false)`. Neither publish is reached by any test: no `:auto` test constructs
`NavigationSession`, because it needs a host-provided `CarContext` and cannot be built under Robolectric.
The state transitions are covered through the seam (`CarSessionPresenceImplTest` 5/0,
`CarSessionIndicatorComposeTest` 2/0), but the *direction each lifecycle callback publishes* —
active on start, cleared on end — was asserted nowhere. The change `shared-resource-arbitration`
accepted the gap explicitly (archive task 4.2, "Verification gap, stated", 2026-10-01); this change
closes it.

**Root cause, one sentence:** both presence publishes are inline `carSessionPresence().setActive(...)`
calls inside `NavigationSession`'s lifecycle callbacks, and `NavigationSession` cannot be constructed
under Robolectric, so the direction each callback publishes is reachable only on a device.

**The red case.** `auto/src/test/java/com/naviveylin/auto/SessionPresencePublishTest.kt` drives the
publish seam a host-free case needs. On the base `b114dc619347dac664a7480f5e4874772fb4ef92` it fails to
compile — `Unresolved reference 'publishCarSessionPresence'` at the four call lines
(`evidence/red-on-HEAD.log`, 2026-11-08T15:14Z). Once the seam exists it is green; the JUnit red half
is the revert-check — the seam's publish line mutated to a no-op, XML `tests=4 failures=4`
(2026-11-08T15:14:38Z), restored to `tests=4 failures=0` (2026-11-08T15:14:46Z).

## What Changes

One top-level seam in `NavigationSession.kt`:

```kotlin
internal fun publishCarSessionPresence(
    presence: () -> CarSessionPresence,
    live: Boolean
): Boolean = guardedHostCall(
    what = if (live) "publish car session presence" else "clear car session presence",
    tag = SESSION_DIAG_TAG
) {
    presence().setActive(live)
}
```

Both call sites go through it (`onStart`: `live = true`; `onDestroy`: `live = false`), replacing the
inline `guardedHostCall("…") { carSessionPresence().setActive(…) }` blocks. `presence` is a provider so
the lazy resolution (`entryPoint.carSessionPresence()`) stays inside the confined block, exactly as the
inline step had it. The seam is `internal`, so the unit case in the same module reaches it without a
`CarContext` — the same pattern as `endNavigationAfterArrival`, `SessionHostGate` and `carSessionScope`,
whose lifecycle steps are likewise seams.

**Additive, not breaking.** The observable publish, its lazy resolution, its confinement and its two
diagnostics names are byte-for-byte what the inline steps did; only the call site's shape changes.
**Rollback**: inline the two calls again and delete the seam and its case; the publishes are again
device-only, which is the state this change repairs.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

`car-session-presence`: one ADDED requirement — the presence publish is one host-free seam whose
direction a unit case asserts and whose fault is confined. The existing scenarios ("Session start is
published", "Session end is published") state the behaviour; the new requirement states that the
publish is drivable without a car host, which is the gap the item records.

## Impact

**Files**

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`: one `internal` seam added (36 insertions,
  9 deletions with the two call sites), `CarSessionPresence` imported.
- `auto/src/test/java/com/naviveylin/auto/SessionPresencePublishTest.kt`: new, 4 cases
  (`sessionStartPublishesLive`, `sessionEndClearsTheSignal`, `aFaultingPublishIsConfinedAndNamed`,
  `clearingAFaultingPublishIsNamedForTheClear`).
- `openspec/changes/fix-car-session-presence-publish-reaches-no-test/**`: this change and its evidence.

**Guidelines affected**: `guidelines/DesignCrossVariant.md` §8a owns the rule this change obeys
("every host mutation goes through the guarded seam", "no fault escapes a host callback") — the publish
stays on `guardedHostCall` and no host, native or DI code is touched. **No guideline edit is owed**: the
extraction moves an existing step onto the seam the section already mandates.

**Rollback**: see above.

surface: 1 seam added, 2 call sites rewritten, 4 cases added, 1 requirement added, 0 fixtures — counted by `bash tools/look.sh 'publishCarSessionPresence'` (3 in `NavigationSession.kt`, 4 in the new case file) and `bash tools/look.sh 'setActive' --path auto` (1 production call site, inside the seam).
