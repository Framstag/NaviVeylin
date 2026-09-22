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
    message: String
) : Screen(carContext) {

    /**
     * The notice's text. Mutable so a second error while this notice is up updates it
     * instead of stacking a second overlay (spec: car-host-fault-isolation — Host
     * screen-stack mutations are balanced; design D4): the session invalidates the
     * screen after [updateMessage]. Main-thread only, like every screen-state write.
     */
    private var message: String = message

    init {
        enableBackNavigation()
    }

    /** Replace the shown message; the caller requests the template refresh. */
    fun updateMessage(newMessage: String) {
        message = newMessage
    }

    override fun onGetTemplate(): PaneTemplate = carPaneTemplate(carContext) {
        val backAction = Action.Builder()
            .setTitle(carContext.getString(R.string.back))
            .setOnClickListener { guardedHostCall("pop (error notice)") { screenManager.pop() } }
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
