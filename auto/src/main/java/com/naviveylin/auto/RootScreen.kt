package com.naviveylin.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.core.content.ContextCompat
import com.naviveylin.core.NavigationViewModel

/**
 * Root Android Auto screen displayed when not navigating.
 * Shows shortcuts to Search, Favorites and Diagnostics via [ListTemplate].
 *
 * Uses [ListTemplate] (not [PaneTemplate]): PaneTemplate rows do not support
 * click listeners (car-app constraint `ROW_CONSTRAINTS_PANE`). ListTemplate is
 * also the most broadly supported list template across hosts (Android Auto
 * projection and Android Automotive OS, incl. driving mode).
 */
class RootScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): ListTemplate {
        val listBuilder = ItemList.Builder()
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

        // Address book — only while READ_CONTACTS is granted
        // (spec: address-book-permission — permission state drives visibility).
        if (hasAddressBookPermission()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Address book")
                    .addText("Search contacts with addresses")
                    .setOnClickListener { onAddressBook() }
                    .build()
            )
        }

        listBuilder
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

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("NaviVeylin")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
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

    private fun onAddressBook() {
        Log.d(TAG, "Opening address book screen")
        screenManager.push(AddressBookScreen(carContext, navigationViewModel))
    }

    /** Address-book entry visibility follows the READ_CONTACTS permission. */
    private fun hasAddressBookPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

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
