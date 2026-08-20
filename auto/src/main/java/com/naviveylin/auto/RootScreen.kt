package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import com.naviveylin.core.NavigationViewModel

/**
 * Root Android Auto screen displayed when not navigating.
 * Shows shortcuts to Search, Favorites and Diagnostics via [SectionedItemTemplate].
 *
 * Uses [SectionedItemTemplate] (not [PaneTemplate]): PaneTemplate rows do not
 * support click listeners (car-app constraint `ROW_CONSTRAINTS_PANE`), and a
 * PaneTemplate with clickable rows fails template build with
 * `IllegalArgumentException: A click listener is not allowed on the row`.
 * List-based templates allow row click listeners.
 */
class RootScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    override fun onGetTemplate(): SectionedItemTemplate {
        val section = RowSection.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Map")
                    .addText("Browse the map")
                    .setOnClickListener { onMap() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Search")
                    .addText("Find a destination")
                    .setOnClickListener { onSearch() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Points of interest")
                    .addText("Hotels, restaurants, fuel, ATMs nearby")
                    .setOnClickListener { onPoiSearch() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Favorites")
                    .addText("Browse saved locations")
                    .setOnClickListener { onFavorites() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Preferences")
                    .addText("Adjust navigation settings")
                    .setOnClickListener { onPreferences() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Diagnostics")
                    .addText("View crash and session logs")
                    .setOnClickListener { onDiagnostics() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("About")
                    .addText("App information")
                    .setOnClickListener { onAbout() }
                    .build()
            )
            .build()

        return SectionedItemTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("NaviVeylin")
                    .build()
            )
            .addSection(section)
            .build()
    }

    private fun onMap() {
        Log.d(TAG, "Opening map screen")
        screenManager.push(MapScreen(carContext, navigationViewModel))
    }

    private fun onSearch() {
        Log.d(TAG, "Opening search screen")
        screenManager.push(SearchScreen(carContext, navigationViewModel))
    }

    private fun onPoiSearch() {
        Log.d(TAG, "Opening POI search screen")
        screenManager.push(PoiSearchScreen(carContext, navigationViewModel))
    }

    private fun onFavorites() {
        Log.d(TAG, "Opening favorites screen")
        screenManager.push(FavoritesScreen(carContext, navigationViewModel))
    }

    private fun onPreferences() {
        Log.d(TAG, "Opening preferences screen")
        screenManager.push(PreferencesScreen(carContext))
    }

    private fun onDiagnostics() {
        Log.d(TAG, "Opening diagnostics screen")
        screenManager.push(DiagnosticsScreen(carContext))
    }

    private fun onAbout() {
        Log.d(TAG, "Opening about screen")
        screenManager.push(AboutScreen(carContext))
    }

    companion object {
        private const val TAG = "RootScreen"
    }
}
