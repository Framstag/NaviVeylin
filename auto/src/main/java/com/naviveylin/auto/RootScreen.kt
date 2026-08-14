package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import com.naviveylin.core.NavigationViewModel

/**
 * Root Android Auto screen displayed when not navigating.
 * Shows shortcuts to Search and Favorites via [PaneTemplate].
 */
class RootScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    override fun onGetTemplate(): PaneTemplate {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Map")
                    .addText("Browse the map")
                    .setOnClickListener { onMap() }
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Search")
                    .addText("Find a destination")
                    .setOnClickListener { onSearch() }
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Favorites")
                    .addText("Browse saved locations")
                    .setOnClickListener { onFavorites() }
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Diagnostics")
                    .addText("View crash and session logs")
                    .setOnClickListener { onDiagnostics() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("NaviVeylin")
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

    private fun onFavorites() {
        Log.d(TAG, "Opening favorites screen")
        screenManager.push(FavoritesScreen(carContext, navigationViewModel))
    }

    private fun onDiagnostics() {
        Log.d(TAG, "Opening diagnostics screen")
        screenManager.push(DiagnosticsScreen(carContext))
    }

    companion object {
        private const val TAG = "RootScreen"
    }
}
