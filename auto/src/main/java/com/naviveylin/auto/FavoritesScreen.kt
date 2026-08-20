package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen for browsing favorite locations using [SectionedItemTemplate].
 * Backed by [AutoFavoritesProvider] via [AutoEntryPoint].
 *
 * Uses [SectionedItemTemplate] (not [PlaceListNavigationTemplate]): the place-list
 * template requires every non-browsable row to carry a distance span and every
 * browsable row to carry a click listener, neither of which applies to favorites
 * browsing — building it would fail with an IllegalArgumentException.
 */
class FavoritesScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()

    private var favoritesData: Map<String, List<com.framstag.libosmscout.client.FavoriteLocation>> = emptyMap()
    private var loaded = false

    init {
        enableBackNavigation()
        scope.launch {
            favoritesData = withContext(Dispatchers.Default) {
                favoritesProvider.favoriteLocations().first()
            }
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): SectionedItemTemplate {
        val sections = mutableListOf<RowSection>()

        if (!loaded) {
            sections.add(
                RowSection.Builder()
                    .addItem(Row.Builder().setTitle("Loading...").build())
                    .build()
            )
        } else if (favoritesData.isEmpty()) {
            sections.add(
                RowSection.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("No favorites saved")
                            .addText("Save favorites from the map to see them here")
                            .build()
                    )
                    .build()
            )
        } else {
            for ((groupName, favorites) in favoritesData) {
                val builder = RowSection.Builder()
                    .setTitle(groupName)

                for (fav in favorites) {
                    // Row tap selects the favorite (rows with a click listener
                    // must not also carry row actions — ROW_CONSTRAINTS_SIMPLE).
                    builder.addItem(
                        Row.Builder()
                            .setTitle(fav.name ?: "Favorite")
                            .addText(fav.attributes?.get("address") ?: "")
                            .setOnClickListener {
                                Log.d(TAG, "Favorite selected: ${fav.name} (${fav.lat}, ${fav.lon})")
                                navigationViewModel.navigateTo(fav.lat, fav.lon)
                            }
                            .build()
                    )
                }

                sections.add(builder.build())
            }
        }

        val builder = SectionedItemTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Favorites")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        sections.forEach { builder.addSection(it) }
        return builder.build()
    }

    companion object {
        private const val TAG = "FavoritesScreen"
    }
}
