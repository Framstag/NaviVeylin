# Proposal

## Why

`NavigationSession.onStart` publishes `carSessionPresence().setActive(true)` and `onDestroy` clears it
with `setActive(false)`. Neither publish was reached by a test: no `:auto` test constructs
`NavigationSession`, because it needs a host-provided `CarContext` and cannot be built under Robolectric.
The state transitions are covered through the presence implementation, but the wiring from each
lifecycle callback to its publish direction was asserted nowhere. The change
`shared-resource-arbitration` accepted the gap explicitly (archive task 4.2, "Verification gap,
stated", 2026-10-01).

**Root cause, one sentence:** both presence publishes were inline
`carSessionPresence().setActive(...)` calls inside `NavigationSession`'s lifecycle callbacks, and
`NavigationSession` cannot be constructed under Robolectric, so the callback-to-direction wiring was
reachable only on a device.

**The red case.** `SessionPresencePublishTest#sessionCallbacksSelectMatchingPublishDirection` reads
the production session source and requires `onStart` and `onDestroy` to select different,
lifecycle-specific publish seams. On the pre-correction tree it failed with `the onStart callback
selects the live publish`; JUnit XML `tests=1 failures=1 errors=0`, timestamp
`2026-11-08T15:37:28.251Z` (`evidence/review-round-2-red.xml` and
`evidence/review-round-2-red.log`). The red case failed for the missing production wiring, not for a
compile error.

**Owning requirement and rule.** The shipped capability is `car-session-presence`. The owning
guideline section is `guidelines/DesignCrossVariant.md` §8a: every host mutation goes through the
guarded seam and no fault escapes a host callback. This delta changes no screen, host API, native
path or DI graph.

## What Changes

Two lifecycle-specific host-free seams own the directions:

```kotlin
internal fun publishCarSessionPresenceOnStart(presence: () -> CarSessionPresence): Boolean
internal fun publishCarSessionPresenceOnDestroy(presence: () -> CarSessionPresence): Boolean
```

The production `onStart` callback selects the first seam and `onDestroy` selects the second. Both
delegate to one private publish helper, which keeps the lazy presence resolution inside
`guardedHostCall` and preserves the existing "publish car session presence" / "clear car session
presence" rejection names.

`SessionPresencePublishTest` now has five cases: four drive the start/destroy seams and their fault
confinement; one source-level case reads `NavigationSession.kt` and requires `onStart` to select only
the start seam and `onDestroy` only the destroy seam. The source-level half exists because the class
itself cannot be constructed in a JVM test; swapping the two production callback calls now fails the
named case.

**Additive, not breaking.** The observable publish points, lazy resolution, confinement and
diagnostics names stay in place. Only the call-site shape and test coverage change.

**Rollback:** restore the two inline guarded publishes, remove the two lifecycle-specific seams and
return the test to its prior four cases. Presence publishing returns to device-only coverage.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

`car-session-presence`: one ADDED requirement. The presence publish has lifecycle-specific host-free
seams, their directions are unit-asserted, the production lifecycle callbacks are bound to the
matching seams, and a rejected publish remains a confined logged no-op.

## Impact

**Files**

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`: two lifecycle-specific seams and
  the two production callback call sites.
- `auto/src/test/java/com/naviveylin/auto/SessionPresencePublishTest.kt`: four seam cases plus one
  production-callback wiring case.
- `openspec/changes/archive/2026-11-08-fix-car-session-presence-publish-reaches-no-test/**`: this
  change and its evidence.
- `openspec/specs/car-session-presence/spec.md`: the shipped capability delta.

**Guidelines affected:** `guidelines/DesignCrossVariant.md` §8a owns the rule this change obeys. No
guideline edit is owed: §8a already requires the guarded seam and fault confinement this delta keeps.

surface: 2 lifecycle-specific production seams, 2 production callback call sites, 1 production
`setActive` call and 5 unit cases — command output in `evidence/surface.txt`.
