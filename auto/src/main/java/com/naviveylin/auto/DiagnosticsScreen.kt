package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import com.naviveylin.auto.R
import com.naviveylin.core.DiagnosticsLog

/**
 * Android Auto diagnostics screen: shows the most recent log entries
 * (crash traces + session events) newest-first so head-unit failures can be
 * inspected from the car without adb.
 */
class DiagnosticsScreen(carContext: CarContext) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): PaneTemplate {
        val entries = DiagnosticsLog.readEntries().takeLast(MAX_ROWS).asReversed()
        val builder = Pane.Builder()

        if (entries.isEmpty()) {
            builder.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.no_log_entries))
                    .build()
            )
        } else {
            entries.forEach { line ->
                builder.addRow(
                    Row.Builder()
                        .setTitle(line.take(MAX_TITLE_CHARS))
                        .build()
                )
            }
        }

        val refreshAction = Action.Builder()
            .setTitle(carContext.getString(R.string.refresh))
            .setOnClickListener { invalidate() }
            .build()

        return PaneTemplate.Builder(builder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.diagnostics))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(refreshAction)
                    .build()
            )
            .build()
    }

    companion object {
        private const val MAX_ROWS = 20
        private const val MAX_TITLE_CHARS = 200
    }
}
