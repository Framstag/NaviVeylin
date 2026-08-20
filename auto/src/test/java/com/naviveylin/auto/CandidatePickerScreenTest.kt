package com.naviveylin.auto

import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.ObjectDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the AA candidate picker row mapping (spec:
 * auto-map-destination-picker): candidates render name + OSM type, unnamed
 * objects fall back to a placeholder, and the tap-routing decision
 * (multi → picker, single/zero → details) is derived from the candidate count.
 */
class CandidatePickerScreenTest {

    private fun candidate(name: String, type: String, offset: Long): ObjectDescription {
        val entries = mutableListOf<DescriptionEntry>()
        if (name.isNotEmpty()) {
            entries.add(DescriptionEntry().apply {
                sectionKey = "General"
                labelKey = "Name"
                value = name
            })
        }
        entries.add(DescriptionEntry().apply {
            sectionKey = "General"
            labelKey = "Type"
            value = type
        })
        return ObjectDescription(entries, Double.NaN, Double.NaN, "area", type, offset)
    }

    @Test
    fun candidateNameFromGeneralNameEntry() {
        assertEquals("Hotel Central", candidateName(candidate("Hotel Central", "tourism_hotel", 1L)))
    }

    @Test
    fun candidateNameFallsBackToUnnamed() {
        assertEquals("(unnamed)", candidateName(candidate("", "building", 2L)))
    }

    @Test
    fun candidateTypeFromObjectTypeName() {
        assertEquals("tourism_hotel", candidateType(candidate("Hotel Central", "tourism_hotel", 1L)))
    }

    @Test
    fun candidateTypeNullWhenUnknown() {
        val desc = ObjectDescription(emptyList(), Double.NaN, Double.NaN, null, null, 0L)
        assertNull(candidateType(desc))
    }

    @Test
    fun tapRoutingMultipleCandidatesUsesPicker() {
        assertEquals(true, shouldShowCandidatePicker(2))
    }

    @Test
    fun tapRoutingSingleCandidateUsesDetails() {
        assertEquals(false, shouldShowCandidatePicker(1))
    }

    @Test
    fun tapRoutingNoCandidatesUsesDetails() {
        assertEquals(false, shouldShowCandidatePicker(0))
    }
}
