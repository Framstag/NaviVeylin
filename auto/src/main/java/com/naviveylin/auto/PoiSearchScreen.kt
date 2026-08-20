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
            val label = CATEGORY_LABELS[category] ?: category.replace('_', ' ').replaceFirstChar { it.uppercase() }
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
            .setHeader(Header.Builder().setTitle("Points of interest").setStartHeaderAction(Action.BACK).build())
            .setSingleList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "PoiSearchScreen"

        /** Display labels for the fixed [PoiCategories] ids. */
        private val CATEGORY_LABELS = mapOf(
            PoiCategories.HOTELS to "Hotels",
            PoiCategories.RESTAURANTS to "Restaurants",
            PoiCategories.GROCERY to "Groceries",
            PoiCategories.VIEWPOINT to "Viewpoints",
            PoiCategories.MUSEUM to "Museums",
            PoiCategories.FUEL to "Fuel stations",
            PoiCategories.CHARGING_STATION to "EV charging",
            PoiCategories.ATM to "ATMs",
            PoiCategories.TOURISM to "Tourism",
            PoiCategories.PARKING to "Parking",
            PoiCategories.POLICE to "Police",
            PoiCategories.HOSPITAL to "Hospitals",
            PoiCategories.DOCTORS to "Doctors",
            PoiCategories.PUBLIC_TRANSPORT to "Public transport"
        )
    }
}
