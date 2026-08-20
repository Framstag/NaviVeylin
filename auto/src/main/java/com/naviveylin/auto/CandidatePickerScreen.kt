package com.naviveylin.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.framstag.libosmscout.client.ObjectDescription

/**
 * Candidate picker for Android Auto (spec: auto-map-destination-picker).
 *
 * Shown when a map tap hits several reasonable objects: lists the candidates
 * in native ranking order (name + OSM type). Selecting one invokes
 * [onCandidateSelected] with the full [ObjectDescription] — the caller pushes
 * the details screen with it, so no re-query happens.
 */
class CandidatePickerScreen(
    carContext: CarContext,
    private val candidates: List<ObjectDescription>,
    private val onCandidateSelected: (ObjectDescription) -> Unit
) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        for (candidate in candidates) {
            val name = candidateName(candidate)
            val type = candidateType(candidate)
            val rowBuilder = Row.Builder().setTitle(name)
            if (type != null) {
                rowBuilder.addText(type)
            }
            rowBuilder.setOnClickListener {
                Log.d(TAG, "Candidate selected: $name ($type)")
                onCandidateSelected(candidate)
            }
            listBuilder.addItem(rowBuilder.build())
        }

        return ListTemplate.Builder()
            .setHeader(Header.Builder().setTitle("What's here?").setStartHeaderAction(Action.BACK).build())
            .setSingleList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "CandidatePickerScreen"
    }
}

/**
 * Tap routing decision (spec: auto-map-destination-picker): several
 * candidates → picker screen; exactly one or none → details screen.
 */
internal fun shouldShowCandidatePicker(candidateCount: Int): Boolean = candidateCount > 1

/** Display name of a candidate: the General/Name entry, or "(unnamed)". */
internal fun candidateName(candidate: ObjectDescription): String =
    candidate.entries.firstOrNull {
        it.sectionKey == "General" && it.labelKey == "Name"
    }?.value?.takeIf { it.isNotBlank() } ?: "(unnamed)"

/** OSM type of a candidate (e.g. "building"), or null when unknown. */
internal fun candidateType(candidate: ObjectDescription): String? =
    candidate.objectTypeName?.takeIf { it.isNotBlank() }
