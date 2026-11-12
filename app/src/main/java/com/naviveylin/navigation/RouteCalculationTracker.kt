package com.naviveylin.navigation

import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.NavigationState
import com.naviveylin.core.RouteCalculation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The route calculation in flight: the token a superseded callback is told apart by, the count of the
 * progress reports it made, and the `ROUTE` measurement entry it is closed with (spec:
 * `route-calculation-feedback`).
 *
 * This is the part of `NavigationEngine` that owns nothing but the calculation's own identity, which is why
 * it can leave: it carries no session state, starts no dispatch of its own, and every method stays
 * main-thread only, called from the site that already publishes navigation state. The engine hands it the
 * state flow it writes and the clock the entry is timed with.
 *
 * It was a member block of `NavigationEngine` until the change `fix-navigation-engine-file-size` (item #852)
 * moved it here: the `source-size` row counts lines per tracked file and the engine stood at 1 512 lines,
 * past the row's bound of 1 500 (`tools/sensors/metrics/source-size.sh`). The declarations, their names, their
 * order and the entry's shape are the ones the engine carried; the line itself is [routeCalculationDoneLine]'s.
 */
internal class RouteCalculationTracker(
    private val _state: MutableStateFlow<NavigationState>,
    private val timeSource: EngineTimeSource
) {

    /**
     * Token of the newest route calculation request. It is published in
     * [com.naviveylin.core.RouteCalculation] and increases with every request, so a
     * callback of a calculation that a newer request superseded can be told apart
     * from the live one (spec: `route-calculation-feedback` — A superseded calculation
     * never clears the live calculation's state).
     */
    private var calculationToken = 0L

    /** Progress values published for the live calculation (measurement, see [endCalculation]). */
    private var calculationProgressCount = 0

    /** Wall clock at which the live calculation started (measurement, see [endCalculation]). */
    private var calculationStartedAtMillis = 0L

    /** Coordinate-free identity of what asked for the live calculation (measurement). */
    private var calculationTrigger = "acquisition"

    /**
     * Publish [destLat]/[destLon] as the calculation in flight and return its token.
     * The destination is part of the state so a surface can name what is being
     * calculated for (spec: `route-calculation-feedback` — In-flight route calculation
     * is part of the shared navigation state). [trigger] is the coordinate-free identity
     * of what asked for the calculation, for the measurement entry only
     * (spec: `auto-diagnostics`). Main-thread only.
     */
    internal fun beginCalculation(destLat: Double, destLon: Double, trigger: String): Long {
        calculationToken += 1
        calculationProgressCount = 0
        calculationStartedAtMillis = timeSource.nowMillis()
        calculationTrigger = trigger
        _state.update { it.copy(
            calculation = RouteCalculation(
                token = calculationToken,
                destLat = destLat,
                destLon = destLon,
                percent = null
            )
        ) }
        return calculationToken
    }

    /**
     * Publish a progress value of the live calculation, if [token] still owns it.
     * Main-thread only; see the counting and dedupe rules inside
     * (spec: `route-calculation-feedback` — Route calculation progress is exposed as a
     * percentage).
     */
    internal fun publishCalculationProgress(token: Long, percent: Int) {
        val current = _state.value.calculation
        if (current == null || current.token != token) return
        // Every report the live calculation makes is counted — the measurement asks how
        // much the routing thread paid (spec: `route-calculation-feedback` — Route
        // calculation is measured without coordinates) — but only a changed value is
        // written: the native side already rate-limits (`JavaRoutingProgress`), and a
        // new value costs a recomposition on the phone and a host template push on the
        // car.
        calculationProgressCount += 1
        val capped = percent.coerceIn(0, 99)
        if (current.percent == capped) return
        _state.update { it.copy(calculation = current.copy(percent = capped)) }
    }

    /**
     * Clear the calculation [token] owns and record its measurement entry.
     *
     * Returns whether [token] was still the live one: a calculation a newer request
     * superseded ends without touching the live state and without an entry — its start
     * time is gone with the state, so a duration it could report would be a lie
     * (spec: `route-calculation-feedback` — A superseded calculation never clears the
     * live calculation's state). Main-thread only.
     */
    internal fun endCalculation(token: Long,
                                outcome: CalculationOutcome,
                                failure: NavigationFailure? = null): Boolean {
        val current = _state.value.calculation
        if (current == null || current.token != token) return false
        _state.update { it.copy(calculation = null) }
        // Coordinate-free by construction (spec: `auto-diagnostics` — no coordinates in diagnostics); the
        // line's own shape, and the reason it names, are asserted in NavigationEngineCalculationDiagnosticsTest.
        DiagnosticsLog.log(
            NavigationEngine.ROUTE_TAG,
            routeCalculationDoneLine(
                source = calculationTrigger,
                durationMillis = timeSource.nowMillis() - calculationStartedAtMillis,
                progressCount = calculationProgressCount,
                outcome = outcome.entryValue,
                reachedPercent = current.percent,
                failure = failure
            )
        )
        return true
    }
}

/** How a route calculation ended — the measurement entry's outcome field. */
internal enum class CalculationOutcome(val entryValue: String) {
    OK("ok"),
    ERROR("error"),
    CANCELLED("cancelled")
}
