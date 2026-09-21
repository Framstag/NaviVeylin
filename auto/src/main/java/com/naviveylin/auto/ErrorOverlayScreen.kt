package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row

/**
 * Transient error overlay pushed by the car session on an error state
 * (spec: car-host-fault-isolation — No fault escapes into the host path).
 *
 * Extracted from [NavigationSession.showError] so its template build is guarded
 * like every other car screen's and so the overlay is constructible in a unit
 * test: the session itself needs a host-provided [CarContext] and cannot be
 * built in Robolectric.
 */
class ErrorOverlayScreen(
    carContext: CarContext,
    private val message: String
) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): PaneTemplate = carPaneTemplate(carContext) {
        val backAction = Action.Builder()
            .setTitle(carContext.getString(R.string.back))
            .setOnClickListener { screenManager.pop() }
            .build()
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(message)
                    .addAction(backAction)
                    .build()
            )
            .build()
        PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.error))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
