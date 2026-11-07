# Design

## Problem

`NavigationManagerController.publishTrip` treats one rejected `NavigationManager.updateTrip` as the end of the
navigation period (`NavigationManagerController.kt:107-111` on base `4367cb4d`): it logs and clears
`navigating`, the flag `:101` guards on, and `navigating = true` is written only by `onNavigationStarted`
(`:69`). `NavigationSession` calls that from the `isNavigating` false→true edge (`NavigationSession.kt:700-701`,
`:712`) and from the restore path (`:855`), so within one navigation period there is no second chance: the
cluster and the heads-up display keep the last trip the host accepted, silently, until the driver ends and
restarts navigation. The trip collector is not the limit — `tripSendAllowed(navState.isNavigating,
hostGate.started)` (`NavigationSession.kt:1373`) keeps letting the call through; the controller's latch stops
it.

The owner's recorded decision on item #736 is **Option 1**: re-arm on the next changed trip content, bounded, so
a healthy host resumes guidance while a dead host is not polled at the position rate. That is the change; the
fork is settled and is not re-derived here.

## D1 — The single fix

**A: count the rejected update as delivered and bound the rejections (chosen).** In the `onFailure` of
`updateTrip` the controller (1) writes `lastPublishedState = state`, so `NavigationTemplateMapper.hasTripChanged`
sees content the host has already refused as published and the next send waits for an actual change, (2)
increments `rejectedUpdates`, and (3) sets `navigating = false` only once `rejectedUpdates` reaches
`MAX_REJECTED_TRIP_UPDATES` (3) — the behaviour the shipped code has on the first rejection. A successful
`updateTrip` resets the counter, `onNavigationStarted` and `onDestroy` reset it, so the budget is per
navigation period.

**B: re-arm only on the next `onNavigationStarted` (today's behaviour, restated).** That is item candidate (c)
and the decision's option 3, which the owner did not record. Rejected on that ground alone.

**C: latch with no bound and keep resending on every changed content.** An unbounded re-arm polls a dead host
at the trip-change rate (the cadence requirement's own reason not to: `auto-navigation-hints` — "Trip
publishing cadence"). Rejected: the item asks for a bound explicitly.

**D: drop the latch and resend on every position fix.** Contradicts the shipped cadence requirement and
floods the host on a reconnect. Rejected.

## D2 — What bounds it: an attempt budget, not a clock

**A: a per-period rejection budget (chosen).** Three refused sends are enough to tell a host that came back
from a host that is gone: the transient cases the item names (a rebind, a surface teardown, a reconnecting
projection session) either accept the next changed trip or refuse it, and a dead host is polled at most three
times in a period. The bound needs no clock, so the case is deterministic — no injected time source, no
`SystemClock` under Robolectric, and nothing for `guidelines/Build.md` §6e ("wall-clock waits") to object to.

**B: one attempt per N seconds (rejected).** The item allows it, but it makes the controller read a clock,
puts a time source into a constructor that tests build directly, and leaves a dead host polled for the whole
trip at the interval rate — 2 sends a minute for an hour versus 3 per period. Its only advantage, faster
recovery for a host that comes back after a long outage, is bounded by the position-update throttle anyway.

**C: both bounds together (rejected).** Two knobs, and a reader cannot tell which one is the rule.

## Blast radius

One production file (`NavigationManagerController.kt`): two fields (one new), `onNavigationStarted`,
`onDestroy`, `publishTrip` and its KDoc, plus the new companion constant. No caller changes
(`NavigationSession.kt:733` untouched), no API, no state, no threading change — all methods stay main-thread.
Tests: `NavigationManagerControllerTest` (9 → 11 cases; `aRejectedTripUpdateEndsTripPublishing` rewritten into
the budget case). `NavigationManagerControllerTripTest` is untouched and its 9 cases stay green; in particular
`hostFailureIsSwallowed` still passes because the first rejection is swallowed exactly as before.

Spec: `car-host-fault-isolation`, one `ADDED` requirement (3 scenarios, 3 cases) and one `MODIFIED`
requirement whose text and 11 scenarios are otherwise repeated verbatim; the 10 scenarios this change does not
touch carry `**Case**: none — …`, which is the readiness checker's own form for "a claim, not an omission".

## Rollback

Restore the `onFailure` body to `Log.w(…, "trip update rejected — treating the host session as ended", it);
navigating = false; return`, delete `rejectedUpdates` and `MAX_REJECTED_TRIP_UPDATES`, and restore
`aRejectedTripUpdateEndsTripPublishing`. The spec delta is removed with the change; nothing else reads the
counter.

## Risks

| risk | mitigation |
|---|---|
| A host that refuses the first trip of a period and then accepts a *changed* trip gets one send the shipped code would not have made | That is the decision's intent (Option 1); the shipped scenario is narrowed in the same change, so the code and the spec agree |
| The budget is spent early in a long trip, so a late rebind is not retried | Three consecutive refusals (a successful send resets the counter) mean the host refused everything after the change, which is the "session is gone" case the shipped stance covers; the alternative is polling a dead host for an hour |
| `lastPublishedState = state` on failure hides a real content change from the throttle | The refused state is the state the host was *sent*; a later emission differs from it exactly as it would have differed from an accepted publish |
| A reviewer reads the 10 `**Case**: none` lines as plan debt | The proposal's surface line states the count and the reason; the readiness rule accepts `none — <reason>` |

## Not taken: the AAOS measurement the decision recommended

The item asks for an AAOS AVD run counting `Diag/HOST trip update` lines against `rejected` lines
(`guidelines/Build.md` §10c/§10d against the §10e baseline) to show a rejection can outlive a live host
session — the premise Option 1 rests on. It was **not** taken: this session has `adb` but no `emulator`
binary on `PATH` and no device attached (`adb devices` → empty), and driving a mid-navigation host rebind is
not deterministic on a stationary emulator even when one is booted. The premise therefore remains unmeasured,
and this change records the re-arm as the owner's decision rather than as a measured repair.

## Open decisions

none
