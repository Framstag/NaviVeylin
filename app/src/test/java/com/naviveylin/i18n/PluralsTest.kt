package com.naviveylin.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plurals selection for English and German (spec: count-dependent strings use
 * plurals). Does not touch OSMScoutClient, so @Config qualifiers are safe
 * (see AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class PluralsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "en")
    fun englishSelectsSingularAndPlural() {
        assertEquals("1 file", context.resources.getQuantityString(R.plurals.file_count, 1, 1))
        assertEquals("3 files", context.resources.getQuantityString(R.plurals.file_count, 3, 3))
        assertEquals("1 active download", context.resources.getQuantityString(R.plurals.active_downloads, 1, 1))
        assertEquals("2 active downloads", context.resources.getQuantityString(R.plurals.active_downloads, 2, 2))
    }

    @Test
    @Config(qualifiers = "de")
    fun germanSelectsSingularAndPlural() {
        assertEquals("1 Datei", context.resources.getQuantityString(R.plurals.file_count, 1, 1))
        assertEquals("3 Dateien", context.resources.getQuantityString(R.plurals.file_count, 3, 3))
        assertEquals("1 aktiver Download", context.resources.getQuantityString(R.plurals.active_downloads, 1, 1))
        assertEquals("2 aktive Downloads", context.resources.getQuantityString(R.plurals.active_downloads, 2, 2))
    }
}
