package com.naviveylin.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Verifies that history timestamps are formatted per the device locale:
 * German produces "15.08.2026", English produces "Aug 15, 2026".
 */
@RunWith(RobolectricTestRunner::class)
class SearchHistorySheetTest {

    // 2026-08-15T12:00:00Z — noon keeps the date stable across time zones.
    private val timestamp = 1786795200000L

    @Test
    fun formatsInGermanLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val formatted = formatHistoryDate(timestamp)
            assertTrue("expected German date in '$formatted'", formatted.contains("15.08.2026"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatsInEnglishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val formatted = formatHistoryDate(timestamp)
            assertTrue("expected English date in '$formatted'", formatted.contains("Aug 15, 2026"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
