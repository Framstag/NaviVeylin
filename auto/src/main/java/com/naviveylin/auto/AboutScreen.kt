package com.naviveylin.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row

/**
 * About screen: app name, description, installed version, and the
 * OpenStreetMap data licence (spec: osm-attribution — "Licence info
 * reachable in car app"). PaneTemplate rows are not actionable (UI.md §3),
 * so the licence link is a pane-level action.
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
            .addRow(
                Row.Builder()
                    .setTitle("Map data")
                    .addText("© OpenStreetMap contributors, available under the Open Database License (ODbL).")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("openstreetmap.org/copyright")
                    .setOnClickListener { openCopyrightPage() }
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

    private fun openCopyrightPage() {
        try {
            carContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(OSM_COPYRIGHT_URL))
            )
        } catch (_: Exception) {
            // No browser available on the host
        }
    }

    private companion object {
        const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
    }
}
