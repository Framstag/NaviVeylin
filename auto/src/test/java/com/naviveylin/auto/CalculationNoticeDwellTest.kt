package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

/**
 * The minimum visible dwell of the route-calculation wait notice (spec:
 * `route-calculation-feedback` — A notice the navigation view replaces stays visible for a
 * minimum dwell).
 *
 * The measured defect (§133, issue #84; measured 2026-10-05 on the AAOS AVD): a 2 km town route
 * reported `847 ms`. The notice is pushed once `CALCULATION_NOTICE_DELAY_MS` (400 ms) has
 * elapsed, so the navigation view replaced a screen the driver had seen for **443 ms**. The
 * notice therefore has to stay up for a minimum dwell once it has been pushed, and only then
 * may the navigation view replace it.
 *
 * The rule is reached by reflection because it is a *new* seam: a direct call would only fail
 * the compile on the tree before it exists, and a red case has to fail in a JUnit run there
 * (the loop's Phase A) — a compile error produces no result to compare. A signature change
 * makes these cases fail loudly instead of silently, and after the fix they exercise the real
 * function and assert real numbers.
 */
class CalculationNoticeDwellTest {

    /**
     * The dwell owed for a notice that has been visible for [shownForMs] and is about to be
     * replaced by the navigation view ([replacedByNavigation] false where no navigation view stands
     * below the notice: the driver's own cancellation, or a failure whose error notice is shown over
     * no navigation view — those are answered at once, and a screen the wait owes is not what removes
     * the notice).
     */
    private fun dwellRemainingMs(shownForMs: Long, replacedByNavigation: Boolean): Long {
        val method: Method = facade()
            .declaredMethods
            .singleOrNull { it.name.dwellName() && it.parameterCount == 2 }
            ?: throw AssertionError(
                "the calculation notice's removal consults no minimum visible dwell: " +
                    "NavigationSession keeps no calculationNoticeDwellRemainingMs(shownForMs, " +
                    "replacedByNavigation), so a route that arrives just after " +
                    "CALCULATION_NOTICE_DELAY_MS replaces a notice the driver saw for a few " +
                    "hundred milliseconds (measured: 443 ms)"
            )
        return method.invoke(null, shownForMs, replacedByNavigation) as Long
    }

    private fun facade(): Class<*> = runCatching {
        Class.forName("com.naviveylin.auto.NavigationSessionKt")
    }.getOrElse {
        throw AssertionError("com.naviveylin.auto.NavigationSessionKt is not on the classpath: $it")
    }

    /**
     * An `internal` top-level function may carry a module-name suffix in bytecode, so the name is
     * matched by its stem — and the count below keeps a second, unrelated function from being
     * mistaken for it.
     */
    private fun String.dwellName(): Boolean =
        this == "calculationNoticeDwellRemainingMs" ||
            startsWith("calculationNoticeDwellRemainingMs$")

    @Test
    fun aRouteArrivingInsideTheDwellKeepsTheNoticeUp() {
        assertEquals(
            "a notice the driver has seen for 443 ms (the measured flash) owes the rest of its " +
                "700 ms dwell before the navigation view may replace it",
            257L,
            dwellRemainingMs(shownForMs = 443L, replacedByNavigation = true)
        )
        assertEquals(
            "a notification pushed a moment ago owes its whole dwell",
            700L,
            dwellRemainingMs(shownForMs = 0L, replacedByNavigation = true)
        )
    }

    @Test
    fun theDriversOwnAnswerIsNeverDelayedByTheDwell() {
        assertEquals(
            "a cancellation is the driver's own tap: it is answered at once, not after a dwell",
            0L,
            dwellRemainingMs(shownForMs = 0L, replacedByNavigation = false)
        )
        assertEquals(
            "a failure with no navigation view below the notice is answered by the error notice at " +
                "once, not by a dwell",
            0L,
            dwellRemainingMs(shownForMs = 443L, replacedByNavigation = false)
        )
    }

    @Test
    fun aNoticeThatOutlastedItsDwellIsReplacedAtOnce() {
        assertEquals(
            "one millisecond short of the dwell is still inside it",
            1L,
            dwellRemainingMs(shownForMs = 699L, replacedByNavigation = true)
        )
        assertEquals(
            "the dwell boundary replaces the notice",
            0L,
            dwellRemainingMs(shownForMs = 700L, replacedByNavigation = true)
        )
        assertEquals(
            "a notice that stayed up longer owes nothing, and the answer is never negative",
            0L,
            dwellRemainingMs(shownForMs = 24_600L, replacedByNavigation = true)
        )
    }
}
