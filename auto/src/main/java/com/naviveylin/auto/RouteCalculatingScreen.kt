package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row

/**
 * Percentage step the notice displays. A value inside the same step cannot change what the
 * driver sees, so it must not cost a host template push — the same rule
 * `NavigationTemplateMapper.displayedDistanceBucket` applies to distances
 * (spec: `car-host-fault-isolation` — Bounded host-facing traffic).
 */
internal const val CALCULATION_PERCENT_STEP = 5

/**
 * The percentage the notice displays for a reported [percent], or null while the
 * calculation has not reported one.
 */
internal fun displayedCalculationPercent(percent: Int?): Int? =
    percent?.let { (it / CALCULATION_PERCENT_STEP) * CALCULATION_PERCENT_STEP }

/**
 * Transient wait notice pushed by the car session while a route calculation is in flight
 * (spec: `route-calculation-feedback` — Car wait notice while a route is being calculated).
 *
 * Built as a mirror of [ErrorOverlayScreen]: it is pushed by the session (never a stack
 * root), its template goes through the guarded [carPaneTemplate] wrapper, it is removed by
 * identity and it is *updated* in place rather than pushed a second time.
 *
 * The Cancel action is the notice's **only** exit, and it is only offered while navigation is
 * **not** active: a reroute happens with guidance still running, and neither cancelling it nor
 * losing the instruction panel is what a driver off route needs (spec:
 * `auto/navigation-view` — Reroute keeps the navigation view live under the notice). Whether the
 * notice is cancellable is therefore fixed for its whole life; it is decided when the session
 * pushes it.
 *
 * **No back affordance on purpose** (deviation from the exploration's C1, forced by the platform):
 * car-app 1.7's [Screen] exposes no back callback (`onBackPressed` does not exist; back is a
 * host-driven stack pop), so a back affordance could only *pop* the notice and leave the routing
 * work running with nothing on screen to stop it. The notice therefore does not enable back
 * navigation and carries no header back action; the row's Cancel is the exit (owner decision C1's
 * intent — back must not leave a calculation running — with the only mechanism the API offers).
 */
class RouteCalculatingScreen(
    carContext: CarContext,
    destinationName: String?,
    percent: Int?,
    cancellable: Boolean,
    private val onCancel: () -> Unit = {}
) : Screen(carContext) {

    private var destinationName: String? = destinationName
    private var percent: Int? = percent
    private val cancellable: Boolean = cancellable

    /**
     * Replace the progress the notice shows (a reported percentage, displayed in its
     * [CALCULATION_PERCENT_STEP] step); the caller requests the template refresh
     * (`invalidate()`), so the host sees the new percentage.
     */
    fun update(destinationName: String?, percent: Int?) {
        this.destinationName = destinationName
        this.percent = percent
    }

    override fun onGetTemplate(): PaneTemplate = carPaneTemplate(carContext) {
        val row = Row.Builder()
            .setTitle(
                destinationName?.takeIf { it.isNotBlank() }
                    ?: carContext.getString(R.string.route_calculation_title)
            )
        displayedCalculationPercent(percent)?.let {
            row.addText(carContext.getString(R.string.route_calculation_percent, it))
        }
        if (cancellable) {
            row.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener { onCancel() }
                    .build()
            )
        }
        val header = Header.Builder()
            .setTitle(carContext.getString(R.string.route_calculation_title))
            .build()
        PaneTemplate.Builder(Pane.Builder().addRow(row.build()).build())
            .setHeader(header)
            .build()
    }
}
