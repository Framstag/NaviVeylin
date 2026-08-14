package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Place
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
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
 * Android Auto screen for browsing favorite locations using [PlaceListNavigationTemplate].
 * Backed by [AutoFavoritesProvider] via [AutoEntryPoint].
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
        scope.launch {
            favoritesData = withContext(Dispatchers.Default) {
                favoritesProvider.favoriteLocations().first()
            }
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (!loaded) {
            return PlaceListNavigationTemplate.Builder()
                .setTitle("Favorites")
                .setItemList(
                    ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle("Loading...")
                                .build()
                        )
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()

        if (favoritesData.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("No favorites saved")
                    .addText("Save favorites from the map to see them here")
                    .build()
            )
        } else {
            for ((groupName, favorites) in favoritesData) {
                // Group header
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(groupName)
                        .setBrowsable(true)
                        .build()
                )

                for (fav in favorites) {
                    val navigateAction = Action.Builder()
                        .setTitle("Navigate here")
                        .setOnClickListener {
                            Log.d(TAG, "Navigate to favorite: ${fav.name} (${fav.lat}, ${fav.lon})")
                            navigationViewModel.navigateTo(fav.lat, fav.lon)
                        }
                        .build()

                    val place = Place.Builder(
                        CarLocation.create(fav.lat, fav.lon)
                    ).build()

                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(fav.name)
                            .addText(fav.attributes["address"] ?: "")
                            .addAction(navigateAction)
                            .build()
                    )
                }
            }
        }

        return PlaceListNavigationTemplate.Builder()
            .setTitle("Favorites")
            .setItemList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "FavoritesScreen"
    }
}
