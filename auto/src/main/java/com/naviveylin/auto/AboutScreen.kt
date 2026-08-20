package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row

/**
 * About screen: app name, description, and installed version.
 */
class AboutScreen(carContext: CarContext) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): PaneTemplate {
        val version = try {
            val info = carContext.packageManager.getPackageInfo(carContext.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("NaviVeylin")
                    .addText("Offline OpenStreetMap navigation")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Version")
                    .addText(version.ifBlank { "unknown" })
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle("About")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
