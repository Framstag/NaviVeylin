package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SearchTemplate.SearchCallback
import com.naviveylin.core.R

/**
 * The POI name filter, as a car search screen (spec: `auto-search` — A car POI result list can be
 * narrowed by name; change `poi-name-filter`, design D5).
 *
 * The driver opens it from the results screen's filter row, which prefills it with the term already
 * active; submitting applies the term to *that same* results screen and pops back to it, so one
 * refinement costs no extra screen on the back stack — the pattern `OverspeedDeltaPickerScreen` and
 * `VehicleAnchorPickerScreen` use for a picked value. Submitting an empty field clears the filter.
 * Leaving without submitting changes nothing: the term is only handed over on submit.
 */
class PoiFilterScreen(
    carContext: CarContext,
    /** The term the results screen already carries; the field opens with it. */
    private val initialTerm: String = "",
    /**
     * Applies the submitted term to the results screen underneath. The screen applies it to itself
     * rather than a new results screen being pushed (design D5).
     */
    private val onApply: (String) -> Unit = {}
) : Screen(carContext) {

    /** The text the host last reported; the submit callback's own copy of the field. */
    private var term: String = initialTerm

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): SearchTemplate = carSearchTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carSearchTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): SearchTemplate {
        val builder = SearchTemplate.Builder(SearchCallbackImpl())
            .setShowKeyboardByDefault(true)
            // Explicit back — emulated hosts may not render their own.
            .setHeaderAction(Action.BACK)

        if (initialTerm.isNotBlank()) {
            builder.setInitialSearchText(initialTerm)
        }
        return builder.build()
    }

    /**
     * Submit: hand the term to the results screen below, then pop back to it through the guarded
     * host call so a refusing host cannot fault the car path (spec: `auto-search` — The pop goes
     * through the guarded host call).
     */
    internal fun apply(filter: String) {
        term = filter
        onApply(filter.trim())
        guardedHostCall("pop (poi filter)") { screenManager.pop() }
    }

    private inner class SearchCallbackImpl : SearchCallback {
        override fun onSearchSubmitted(searchText: String) = apply(searchText)

        override fun onSearchTextChanged(searchText: String) {
            term = searchText
        }
    }

    /** The term the screen would submit right now; the template's initial text is [initialTerm]. */
    internal fun currentTerm(): String = term

    /** The label the filter field carries, from the string resource both variants share. */
    internal fun filterLabel(): String = carContext.getString(R.string.poi_filter_label)
}
