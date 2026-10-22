# Design — fix-calculation-notice-minimum-dwell

## D1. The dwell is a decision the session asks, not a timer the screen owns

`NavigationSession` already delegates its notice decisions to pure seams beside it
(`needsNoticeArm`, `noticeIsStale`, `noticeIsStillArmedFor`, `noticeNeedsUpdate`), because the session
needs a host-provided `CarContext` and cannot be constructed in Robolectric. The dwell gets the same
shape:

```kotlin
internal const val CALCULATION_NOTICE_MIN_DWELL_MS = 700L

internal fun calculationNoticeDwellRemainingMs(
    shownForMs: Long,
    replacedByNavigation: Boolean
): Long = if (!replacedByNavigation) 0L
         else (CALCULATION_NOTICE_MIN_DWELL_MS - shownForMs).coerceAtLeast(0L)
```

The session measures `shownForMs` from `SystemClock.elapsedRealtime()` captured when the push landed
(monotonic: a wall-clock jump would otherwise stretch or shorten the dwell), arms one
`calculation-notice-dwell` job for the answer, and removes the notice by identity when it fires.

*Alternatives eliminated:* a `dismissErrorNotice`-style suspend seam with one lambda per branch (rejected —
the branch it needs to decide is *whether* to wait, which would be decided inside the lambda and therefore
untested); a timer in `RouteCalculatingScreen` (rejected — the screen owns no session state and the removal
is the session's mutation); a `Handler.postDelayed` (rejected — the session's work is coroutine-scoped and
`stopObserving()` has to be able to cancel it).

## D2. `700 ms`, and why that number

The measured flash is 443 ms (an 847 ms route; the notice is pushed at 400 ms, the navigation view
replaced it at 443 ms later). The dwell must be longer than the band it removes, and the observed band is
400–800 ms; 700 ms covers every sample the archived change recorded (847 ms, 24.6 s, 28.7 s) and keeps the
worst case a driver waits — a route that arrives one millisecond after the delay — at 700 ms of notice,
1.1 s from the request. The number is asserted by the case (a 443 ms notice owes 257 ms), so a later
measurement that changes it changes a red case, not a comment.

*Alternatives eliminated:* raising `CALCULATION_NOTICE_DELAY_MS` to ~900 ms instead (rejected — it makes
the notice disappear for the whole 400–900 ms band, i.e. the driver waits a second in silence instead of
seeing that something is happening, and the archived change's own decision kept 400 ms because no
successful route below it was observed); a fixed dwell of 443 ms (rejected — it would be the measured
sample rounded down to the boundary, and a route arriving at 443 ms would flash for zero milliseconds only
by luck).

## D3. The dwell never delays the driver's own answer

The removal has two shapes and only one of them is a screen the wait owes:

- **the route arrived** — the navigation view is pushed on the `isNavigating` edge, i.e. *under* the
  notice, and the removal is what reveals it. This is the shape the dwell protects.
- **a cancellation or a failure** — the driver's tap, and the error notice. Delaying either would tax the
  one interaction the notice exists to offer, and the shipped scenarios ("Calculation cancelled from the
  notice", "Calculation fails") state they are answered at once.

`replacedByNavigation` is therefore an input of the decision (it is
`navigationViewModel.state.value.isNavigating` at the moment the calculation state cleared), not of the
timer.

## Blast radius

One production file (`NavigationSession.kt`): the constant, the seam, two fields
(`calculationNoticeShownAtMs`, `calculationNoticeDwellJob`), `scheduleCalculationNoticeRemoval()`,
`cancelNoticeDwell()`, the record set in `pushCalculationNotice()`, the cancel of a dwell a live
calculation supersedes, and the clear in `removeCalculationNotice()` / `showRootScreen()` /
`stopObserving()`. The `showRootScreen()` clear is a guard the dwell needs and a stale record it fixes: a
landed `popToRoot()` takes every notice with it, so the session must not keep believing in one (the same
rule `SessionScreenStack.onRootPopped()` follows).

One new case file (`CalculationNoticeDwellTest`, 3 cases), one guideline bullet (`UI.md` §3c), one
capability delta. No dependency, no manifest, no native code, no resource, no build script. The other car
screens, the phone surface and the routing engine are untouched.

## Rollback

Delete the constant, the seam, the two fields and the dwell job; restore `removeCalculationNotice()` as
the body of the `calculation == null` branch of `applyCalculationNotice()`; drop the timestamp record.
The notice leaves on the state change again and no other file changes.

## Risks

- **A dwell job that outlives its notice** → every path that ends the notice cancels the job
  (`calculationNotice` identity check at the fire site, `cancelNoticeDwell()` on removal, on a superseding
  calculation, on the root pop and in `stopObserving()`), so the job can only ever remove the screen it
  was armed for.
- **The dwell and the deferred-dismissal path** → a removal the host gate refuses is still owed and is
  re-applied by the started sync; the dwell job itself performs no host call while the session is not
  started, and the owed path removes without a second wait (the session was not visible, so nothing was
  seen to flash).
- **A longer-lived notice over a live navigation view (a reroute)** → the notice already sits over the
  navigation view during a reroute; the dwell changes only how long it stays after the route arrives, and
  the driver keeps the instruction panel underneath. The archived change's risk "Reroute notice over the
  navigation view could hide the instruction panel" is unchanged in kind and shortened in duration.
