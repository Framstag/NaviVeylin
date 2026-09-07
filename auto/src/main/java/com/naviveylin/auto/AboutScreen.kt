package com.naviveylin.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import com.naviveylin.auto.R
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
                    .setTitle(carContext.getString(R.string.app_name))
                    .addText(carContext.getString(R.string.offline_nav_tagline))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.version))
                    .addText(version.ifBlank { carContext.getString(R.string.version_unknown) })
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.map_data))
                    .addText(carContext.getString(R.string.osm_licence_statement))
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.osm_licence_link))
                    .setOnClickListener { openCopyrightPage() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.about))
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
