package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.framstag.libosmscout.client.PoiCategories
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors

/**
 * POI category picker for Android Auto.
 *
 * Shows the fixed [PoiCategories] list; tapping a category pushes
 * [PoiResultsScreen], which searches around the current GPS position and
 * offers "Navigate here" per result. The native API
 * ([com.framstag.libosmscout.client.OSMScoutClient.searchPOIs]) requires a
 * position, so a GPS fix must be available.
 */
class PoiSearchScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): Template {
        val categories = PoiCategories.getCategoryTypes().keys.toList()

        val listBuilder = ItemList.Builder()
        for (category in categories) {
            val label = carContext.getString(categoryLabelRes(category))
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setOnClickListener {
                        Log.d(TAG, "POI category selected: $category ($label)")
                        carContext.getCarService(ScreenManager::class.java)
                            .push(PoiResultsScreen(carContext, navigationViewModel, category, label))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.points_of_interest)).setStartHeaderAction(Action.BACK).build())
            .setSingleList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "PoiSearchScreen"

        /**
         * Maps a native [PoiCategories] id to its localized resource label,
         * falling back to the hotels label for unknown ids (same contract as
         * the phone's `categoryLabelRes`).
         */
        private fun categoryLabelRes(id: String): Int = when (id) {
            PoiCategories.HOTELS -> R.string.poi_category_hotels
            PoiCategories.RESTAURANTS -> R.string.poi_category_restaurants
            PoiCategories.GROCERY -> R.string.poi_category_grocery
            PoiCategories.VIEWPOINT -> R.string.poi_category_viewpoint
            PoiCategories.MUSEUM -> R.string.poi_category_museum
            PoiCategories.FUEL -> R.string.poi_category_fuel
            PoiCategories.CHARGING_STATION -> R.string.poi_category_charging_station
            PoiCategories.ATM -> R.string.poi_category_atm
            PoiCategories.TOURISM -> R.string.poi_category_tourism
            PoiCategories.PARKING -> R.string.poi_category_parking
            PoiCategories.POLICE -> R.string.poi_category_police
            PoiCategories.HOSPITAL -> R.string.poi_category_hospital
            PoiCategories.DOCTORS -> R.string.poi_category_doctors
            PoiCategories.PUBLIC_TRANSPORT -> R.string.poi_category_public_transport
            else -> R.string.poi_category_hotels
        }
    }
}
