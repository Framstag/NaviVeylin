package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import com.naviveylin.core.DiagnosticsLog

/**
 * How a car list screen learns the row capacity the connected host displays (spec:
 * `car-list-row-limit` — A car list never presents more rows than the host displays; design D2).
 *
 * The host ignores the items over its limit with no signal — the Car App Library's own contract for
 * `ListTemplate` ("The host will ignore any items over that limit", `ConstraintManager`), and the
 * AAOS templates host answers 6 rows there while this screen used to send every group and every
 * favourite in one list.
 *
 * The answer comes from the host ([ConstraintManager.getContentLimit]); nothing here hard-codes a
 * host's number. Two paths cannot ask the host and both take the Car App Library's own default for a
 * list instead of an unbounded list:
 *
 * - a host below car API 2, where `ConstraintManager` is `@RequiresCarApi(2)` while the manifest
 *   declares `minCarApiLevel = 1` (`app/src/main/AndroidManifest.xml`), so the query is not legal;
 * - a query that fails (`getCarAppApiLevel` before the handshake throws `IllegalStateException`; a
 *   dead host surfaces as `HostException`/`IllegalStateException` out of the service lookup).
 *
 * [LIBRARY_DEFAULT] is `androidx.car.app.R.integer.content_limit_list` as packaged in car-app 1.7.0
 * (read out of the AAR's `res/values/values.xml`); `ConstraintManager.getContentLimit` itself falls
 * back to that resource when its remote call fails, so the screen and the library agree on the floor
 * the library documents.
 *
 * The reader is pure with respect to the screen and logs its own coordinate-free measurement line
 * (numbers and the host's API level, never a coordinate — `guidelines/Logging.md` §3), which is what a
 * device run reads to record the capacity a host really reports (spec: auto-diagnostics).
 */
internal object CarListCapacityReader {

    /**
     * The Car App Library's default capacity for `CONTENT_LIMIT_TYPE_LIST`
     * (`androidx.car.app.R.integer.content_limit_list`, car-app 1.7.0).
     */
    const val LIBRARY_DEFAULT = 6

    /** The car API level from which `ConstraintManager` may be used (`@RequiresCarApi(2)`). */
    private const val QUERY_API_LEVEL = 2

    /**
     * The row capacity for a car list screen, asking the host when it allows the query.
     *
     * Never throws: a screen that cannot learn its capacity still renders a bounded list.
     */
    fun read(carContext: CarContext): Int {
        val apiLevel = try {
            carContext.carAppApiLevel
        } catch (t: Throwable) {
            return report(apiLevel = null, capacity = LIBRARY_DEFAULT, why = "no car API level yet: $t")
        }
        if (apiLevel < QUERY_API_LEVEL) {
            return report(apiLevel, LIBRARY_DEFAULT, "below car API $QUERY_API_LEVEL")
        }
        return try {
            val limit = carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            if (limit >= 1) {
                report(apiLevel, limit, why = null)
            } else {
                // A host that answers 0 would show no row at all; the page row could not even be
                // reached, so the library default keeps every item reachable.
                report(apiLevel, LIBRARY_DEFAULT, "host reported $limit")
            }
        } catch (t: Throwable) {
            report(apiLevel, LIBRARY_DEFAULT, "query failed: $t${firstFrameSuffix(t)}")
        }
    }

    /** One measurement line per screen start: the capacity used, its source and the host's API level. */
    private fun report(apiLevel: Int?, capacity: Int, why: String?): Int {
        DiagnosticsLog.log(
            CAR_SCREEN_DIAG_TAG,
            "list capacity $capacity rows " +
                (if (why == null) "(host, car API level $apiLevel)" else "(library default, $why)")
        )
        return capacity
    }
}
