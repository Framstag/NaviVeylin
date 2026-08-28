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
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto address picker for a contact with multiple postal addresses
 * (spec: address-book-search — selecting a person with multiple addresses asks
 * for the address). Picking one resolves it and opens the existing
 * [DetailsScreen]; unresolvable addresses show a message row.
 */
class AddressBookAddressPickerScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val contact: ContactAddressBookEntry
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null
    private var notFound = false

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val searchProvider: AddressBookSearchProvider =
        entryPoint.addressBookSearchProvider()

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): ListTemplate {
        val itemList = ItemList.Builder()
        if (notFound) {
            itemList.addItem(
                Row.Builder().setTitle("No location found for this address").build()
            )
        } else {
            for (address in contact.addresses) {
                itemList.addItem(
                    Row.Builder()
                        .setTitle(address.displayText)
                        .setOnClickListener { onAddressSelected(address) }
                        .build()
                )
            }
        }
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(contact.name.ifBlank { "Contact" })
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun onAddressSelected(address: com.naviveylin.core.addressbook.ContactPostalAddress) {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val results = withContext(Dispatchers.Default) {
                try {
                    searchProvider.resolveAddress(address)
                } catch (e: Exception) {
                    Log.e(TAG, "resolveAddress failed", e)
                    emptyList()
                }
            }
            val best = results.firstOrNull()
            if (best != null) {
                Log.d(TAG, "Resolved '${address.displayText}' -> ${best.label}")
                carContext.getCarService(ScreenManager::class.java).push(
                    DetailsScreen(
                        carContext, navigationViewModel, best.lat, best.lon,
                        nameHint = best.label
                    )
                )
            } else {
                notFound = true
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AddressBookAddressPickerScreen"
    }
}
