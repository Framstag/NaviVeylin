# A rejected trip update re-arms publishing

## Why

One rejected `NavigationManager.updateTrip` silences the vehicle cluster and the heads-up display for the rest
of the navigation period, while the navigation screen stays on the stack.

**Verification on base `4367cb4d`.** Every claim of the item holds in the tree:

- `auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt:107-111` — the `onFailure` of
  `navigationManager.updateTrip` logs and sets `navigating = false`; nothing re-arms it.
- `NavigationManagerController.kt:69` is the only writer of `navigating = true`, inside
  `onNavigationStarted`, which `NavigationSession.kt:700-701` reaches only on the `isNavigating`
  false→true edge (`.map { it.isNavigating }.distinctUntilChanged()`, the call at `:712`, and the restore
  path at `:855`).
- `NavigationSession.kt:728-733` keeps calling `publishTrip` — the collector's gate is
  `tripSendAllowed(navState.isNavigating, hostGate.started)` (`:1373`), which stays true — so the send is
  suppressed by the controller's latch, not by the collector.

**Root cause, in one sentence.** The controller conflates a rejected `updateTrip` with the end of the
navigation period: the rejection clears `navigating` (`:107-111`), the only re-arm is the next
`onNavigationStarted` (`:69`), and that call happens only on the `isNavigating` false→true edge — so a
transient rejection (a host rebind, an AAOS surface teardown, a reconnecting projection session) leaves
`publishTrip` early-returning (`:101`) for the rest of the trip, with one `Log.w` (`:108`) and no `Diag/HOST
trip update` line afterwards as the only trace.

**The recorded decision this implements.** The item's decision block records *Option 1*: re-arm on the next
changed trip content, bounded, so a healthy host resumes guidance while a dead host is not polled at the
position rate. This change implements it as a small attempt budget rather than a clock (design D2), and does
not re-open the fork.

**The red test.** `NavigationManagerControllerTest.aTransientTripRejectionRearmsPublishing`,
`…aRejectedTripIsNotResentUntilItsContentChanges` and
`…aRepeatedlyRejectedHostStopsPublishingAfterTheRetryBudget` (the last rewritten from
`aRejectedTripUpdateEndsTripPublishing`, which asserted the latch: `verify(exactly = 1)`). On base `4367cb4d`,
with the controller unchanged, the case class reads JUnit `tests=11 failures=3 errors=0` at
`2026-11-03T12:21:34Z` (`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/736/red-on-HEAD.xml`): the
first rejection is never followed by a send, so two
of the cases find one `updateTrip` call where they need two, and the third finds one where it needs three.

**Owning capability and guideline section.** `car-host-fault-isolation` — the shipped scenario *Host
navigation call rejected*
(`openspec/specs/car-host-fault-isolation/spec.md:85-88`), whose stance this change narrows, and the new
requirement *A rejected trip update re-arms publishing within a retry budget*; the car path's rule that a
host-facing send is guarded and that the driver's guidance keeps flowing is `guidelines/DesignCrossVariant.md`
§8a (reached from `guidelines/Design.md` §8) and `guidelines/UICar.md` §3 "Car-host template constraints".

**Surface.** The rule has one enforcement site, one caller, one case asserting it, one log line and one
shipped scenario; the change adds two cases, rewrites one, and repeats the modified requirement's eleven
scenarios because a `MODIFIED` block replaces the whole requirement:

surface: 2 cases added, 1 rewritten, 0 fixtures realigned — `git grep -n 'aRejectedTripUpdateEndsTripPublishing'
HEAD -- auto/` (1 case), `git grep -n 'trip update rejected' HEAD -- auto/src/main` (1 log line),
`git grep -c 'publishTrip' HEAD -- 'auto/src/test/java/com/naviveylin/auto/*.kt'` (6/13/2), `sed -n '62,137p'
openspec/specs/car-host-fault-isolation/spec.md | grep -c '^#### Scenario:'` (11)

```
git grep -n 'aRejectedTripUpdateEndsTripPublishing' HEAD -- auto/  → 1 (NavigationManagerControllerTest.kt:144)
git grep -n 'trip update rejected' HEAD -- auto/src/main          → 1 (NavigationManagerController.kt:108)
git grep -rn 'navigationManager.updateTrip|\.publishTrip(' auto/src/main → 2 sites:
    NavigationManagerController.kt:106 (the send) and NavigationSession.kt:733 (its only caller)
git grep -c '@Test' HEAD -- .../NavigationManagerControllerTest.kt → 9 (the file the rewrite lands in)
```

so the surface is: **1 enforcement site edited** (`NavigationManagerController.publishTrip`), **2 cases added,
1 case rewritten** in `NavigationManagerControllerTest` (9 → 11 cases), **0 fixtures realigned**,
**1 shipped scenario modified plus 1 requirement added** in `car-host-fault-isolation` (the `MODIFIED` block
repeats 11 shipped scenarios; 10 carry `**Case**: none — …` because this change does not re-case them), and
**no production caller changed** — `NavigationSession.kt:733` and `tripSendAllowed` are untouched.

## What Changes

- **A rejected update no longer ends the period by itself.** `publishTrip` counts the rejected state as
  delivered for the change throttle (`lastPublishedState = state`) and increments a rejection counter, so the
  next *changed* trip content is sent again — the re-arm — while content the host already refused is not
  resent.
- **The re-arm is bounded by an attempt budget, not a clock.** `MAX_REJECTED_TRIP_UPDATES = 3` per navigation
  period: the fourth rejection is not attempted, and from the third the controller does what it does today —
  logs and sets `navigating = false` until the next `onNavigationStarted`. A dead host therefore receives at
  most three refused sends per period, not one per position fix, and `onNavigationStarted` resets the budget.
- **The log line says which half fired**: "re-arming for the next changed trip" below the budget, "the budget
  is spent, treating the host session as ended" at it. Neither carries a coordinate (spec `auto-diagnostics`).
- **The stance in the shipped spec is narrowed, not reversed.** *Host navigation call rejected* keeps "the app
  logs it and keeps running" and now points at the bounded re-arm instead of claiming the session is always
  treated as ended on the first refusal.
- **Both KDocs state it**: the class KDoc and the `publishTrip` KDoc name the re-arm and the budget instead of
  the old claim that a rejection ends publishing.
- **Additive and reversible**: one production file, no API, no state, storage or threading change; rollback is
  the old `onFailure` body (log, `navigating = false`, return) and the old case.
