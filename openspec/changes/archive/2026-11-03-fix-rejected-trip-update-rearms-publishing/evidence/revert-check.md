# Revert-check for the reset case (task 6.2)

The invariant `aDeliveredTripResetsTheRejectionBudget` pins is the production line
`rejectedUpdates = 0` that follows the guarded `updateTrip` call in `NavigationManagerController.publishTrip`
(`auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt:142` at the time of this check): an
accepted send ends a run of refusals, so the next run counts from zero.

**The mutation.** That one line was replaced by a marker comment, leaving the rest of the method — the
`onFailure` counter, the budget comparison and the log lines — in place:

```
                return
            }
        // REVERT-CHECK MUTATION: the counter no longer resets on a delivered send.
```

**It failed for the right reason.** Only the new case was selected
(`--tests 'com.naviveylin.auto.NavigationManagerControllerTest.aDeliveredTripResetsTheRejectionBudget'`):

```
NavigationManagerControllerTest > aDeliveredTripResetsTheRejectionBudget FAILED
    java.lang.AssertionError: Verification failed: call 1 of 1: NavigationManager(#38).updateTrip(any())). 4 matching calls found, but needs at least 5 and at most 5 calls
```

— the missing send, not a throttle or a build error; the tally is `revert-check-red-reset-budget.xml`
(`tests="1" skipped="0" failures="1" errors="0" timestamp="2026-11-03T19:52:37.881Z"`).

**It restored clean.** The marker was removed and `git diff` over the production file was empty before the next
run; the class then read `tests=12 skipped="0" failures="0" errors="0" timestamp="2026-11-03T19:52:51.464Z"`
(`green-reset-budget-controller-cases.xml`) and `NavigationManagerControllerTripTest` `tests=9 failures=0`
(`green-reset-budget-controller-trip-cases.xml`), and a later full gate over the final tree was green
(`gate-final-gradlew-test.xml`).
