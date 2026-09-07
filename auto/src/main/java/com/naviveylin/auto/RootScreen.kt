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
import com.naviveylin.auto.R
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
                    .setTitle(carContext.getString(R.string.map))
                    .addText(carContext.getString(R.string.browse_map))
                    .setOnClickListener { onMap() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.search))
                    .addText(carContext.getString(R.string.find_destination))
                    .setOnClickListener { onSearch() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.points_of_interest))
                    .addText(carContext.getString(R.string.poi_subtitle))
                    .setOnClickListener { onPoiSearch() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.favorites))
                    .addText(carContext.getString(R.string.browse_saved_locations))
                    .setOnClickListener { onFavorites() }
                    .build()
            )

        // Address book — only while READ_CONTACTS is granted
        // (spec: address-book-permission — permission state drives visibility).
        if (hasAddressBookPermission()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.address_book))
                    .addText(carContext.getString(R.string.search_contacts))
                    .setOnClickListener { onAddressBook() }
                    .build()
            )
        }

        listBuilder
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.preferences))
                    .addText(carContext.getString(R.string.adjust_nav_settings))
                    .setOnClickListener { onPreferences() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.diagnostics))
                    .addText(carContext.getString(R.string.view_crash_logs))
                    .setOnClickListener { onDiagnostics() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.about))
                    .addText(carContext.getString(R.string.app_information))
                    .setOnClickListener { onAbout() }
                    .build()
            )

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.app_name))
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
