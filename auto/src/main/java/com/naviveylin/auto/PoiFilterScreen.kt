package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.naviveylin.auto.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * The car's POI name filter (spec: `auto-search` — A car POI result list can be narrowed by name;
 * change `poi-name-filter`, design D5).
 *
 * A search-template screen prefilled with the results screen's current term. Submitting applies the
 * term to that same results screen and pops back to it, so one refinement costs no extra back-stack
 * depth — the pattern [OverspeedDeltaPickerScreen] and [VehicleAnchorPickerScreen] already use.
 * Leaving without submitting changes nothing, and an empty submit clears the filter.
 */
class PoiFilterScreen(
    carContext: CarContext,
    /** The results screen's current term, prefilled into the field. */
    internal val currentTerm: String,
    /** Applies the submitted term to the results screen behind this one; never a push. */
    private val onApply: (String) -> Unit,
    /** Production uses the main dispatcher; the tests inject their own, as the other car screens do. */
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : Screen(carContext) {

    private val scope = carScreenScope("PoiFilterScreen", mainDispatcher)

    init {
        enableBackNavigation()
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template = carSearchTemplate(carContext, ::buildTemplate)

    /** Template body; guarded by [carSearchTemplate] (spec: car-host-fault-isolation). */
    private fun buildTemplate(): SearchTemplate {
        val builder = SearchTemplate.Builder(SearchCallbackImpl())
            .setHeaderAction(Action.BACK)
        if (currentTerm.isNotBlank()) {
            builder.setInitialSearchText(currentTerm)
        }
        return builder.build()
    }

    /**
     * Apply [term] to the results screen behind this one, then pop: the term is applied before the
     * pop, so dismissing the filter can never drop it. An empty term clears the filter. The pop goes
     * through [guardedHostCall], so a refusing host cannot fault the car path (spec: `auto-search` —
     * The pop goes through the guarded host call; `guidelines/UICar.md` §3c).
     */
    internal fun submit(term: String) {
        onApply(term.trim())
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        guardedHostCall("pop (poi filter)") { screenManager.pop() }
    }

    /** Typing applies nothing: the term reaches the results only on submit (design D5). */
    internal inner class SearchCallbackImpl : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) = Unit

        override fun onSearchSubmitted(searchText: String) = submit(searchText)
    }
}
