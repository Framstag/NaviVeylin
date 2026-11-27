package com.naviveylin.ui.settings

import com.naviveylin.data.AmbientLightSensitivity
import com.naviveylin.data.AppSettings
import com.naviveylin.data.DarkModePreference
import com.naviveylin.data.RenderMode
import com.naviveylin.ui.settings.specs.mapTopicRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Karte" topic's own rows (spec: `settings-taxonomy`, spec: `settings-surface`).
 *
 * Group 3 of `settings-taxonomy-and-surface` landed the style picker and the symbol preference and
 * stopped; the five rows it left are the ones the quick sheet still carries, and #903's dispatch
 * condition names them by key — so the first case here is the condition itself, measured rather than
 * assumed. The rest is what a row has to get right once it exists: its stored value is the key it
 * writes, an unknown stored value falls back instead of failing the write, a phone-only row never
 * reaches the car, and the long-press row offers the range the setting documents.
 */
class MapTopicTest {

    /**
     * The topic holds exactly the rows whose controls the quick sheet is to drop, in the order the
     * dispatch condition of #903 names them. A row that moves out of the sheet with no row here
     * would make its setting unreachable (spec: `settings-surface` — "Every persisted phone setting
     * is editable from the surface").
     */
    @Test
    fun theKarteTopicHoldsEveryRowTheQuickSheetDrops() {
        assertEquals(
            "the Karte topic declares the style picker, the symbol preference and the five rows the sheet drops",
            listOf(
                "map-style",
                "poi-symbols",
                "dark-mode",
                "ambient-sensitivity",
                "render-mode",
                "keep-screen-on",
                "long-press-timeout"
            ),
            mapTopicRows.map { it.key }
        )
    }

    /**
     * A `CHOICE` row's stored value is the option's key, and it offers every value the setting can
     * hold — so a pick persists what the enum names and reads back as the same option.
     */
    @Test
    fun aChoiceRowOffersEveryStoredValueAndReadsTheOneInForce() {
        val rows = mapTopicRows.associateBy { it.key }
        val defaults = AppSettings()

        assertEquals(
            "dark mode offers on, off and automatic, keyed by the names the settings file holds",
            DarkModePreference.entries.map { it.name },
            choicesOf(rows.getValue("dark-mode"))
        )
        assertEquals(
            "the row reads the value in force",
            DarkModePreference.AUTOMATIC.name,
            rows.getValue("dark-mode").read(defaults)
        )
        assertEquals(
            "ambient sensitivity offers off and the three sensor settings",
            AmbientLightSensitivity.entries.map { it.name },
            choicesOf(rows.getValue("ambient-sensitivity"))
        )
        assertEquals(
            "rendering offers the tile cache and the direct mode",
            listOf(RenderMode.TILES.name, RenderMode.DIRECT.name),
            choicesOf(rows.getValue("render-mode"))
        )

        // Round trip: a pick writes the option's key and the row reads that key back.
        val dark = rows.getValue("dark-mode")
        val written = dark.write!!.invoke(defaults, DarkModePreference.OFF.name)
        assertEquals(DarkModePreference.OFF, written.darkMode)
        assertEquals(DarkModePreference.OFF.name, dark.read(written))

        val render = rows.getValue("render-mode")
        val direct = render.write!!.invoke(defaults, RenderMode.DIRECT.name)
        assertEquals(RenderMode.DIRECT, direct.renderMode)
        assertEquals(RenderMode.DIRECT.name, render.read(direct))

        val ambient = rows.getValue("ambient-sensitivity")
        val low = ambient.write!!.invoke(defaults, AmbientLightSensitivity.LOW.name)
        assertEquals(AmbientLightSensitivity.LOW, low.ambientLightSensitivity)
    }

    /**
     * A hand-edited or restored settings file can hold a value the row does not offer; the write
     * falls back to the setting's default rather than throwing away the document the user is editing.
     */
    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        val rows = mapTopicRows.associateBy { it.key }
        val defaults = AppSettings()

        assertEquals(
            DarkModePreference.AUTOMATIC,
            rows.getValue("dark-mode").write!!.invoke(defaults, "NotAValue").darkMode
        )
        assertEquals(
            AmbientLightSensitivity.OFF,
            rows.getValue("ambient-sensitivity").write!!.invoke(defaults, "NotAValue").ambientLightSensitivity
        )
        assertEquals(
            RenderMode.TILES,
            rows.getValue("render-mode").write!!.invoke(defaults, "NotAValue").renderMode
        )
    }

    /**
     * Every row written once changes exactly its own field of the document (spec:
     * `settings-taxonomy` — "A setting appears once").
     */
    @Test
    fun eachRowWritesOnlyItsOwnValue() {
        val before = AppSettings()
        val rows = mapTopicRows.associateBy { it.key }

        val after = rows.getValue("keep-screen-on").write!!.invoke(before, "off")
        assertEquals("the switch turns the screen timeout off", false, after.keepScreenOn)
        assertEquals("and changes nothing else", before.copy(keepScreenOn = false), after)

        val slow = rows.getValue("long-press-timeout").write!!.invoke(before, "900")
        assertEquals("the slider writes the duration", 900, slow.longPressTimeoutMs)
    }

    /**
     * The long-press row offers the documented range in its documented steps, and shows the duration
     * the gesture really uses rather than an out-of-range value a hand-edited file carries (spec:
     * `map-long-press-timeout` — "A value outside the offered range is clamped").
     */
    @Test
    fun theLongPressRowOffersTheDocumentedRange() {
        val row = mapTopicRows.single { it.key == "long-press-timeout" }
        val slider = row.payload as SettingControlPayload.Slider

        assertEquals(AppSettings.LONG_PRESS_TIMEOUT_MIN_MS, slider.min)
        assertEquals(AppSettings.LONG_PRESS_TIMEOUT_MAX_MS, slider.max)
        assertEquals(AppSettings.LONG_PRESS_TIMEOUT_STEP_MS, slider.step)
        assertEquals(AppSettings.LONG_PRESS_TIMEOUT_DEFAULT_MS, AppSettings().longPressTimeoutMs)

        assertEquals(
            "a file carrying 40 ms is shown as the shortest duration the row offers",
            AppSettings.LONG_PRESS_TIMEOUT_MIN_MS.toString(),
            row.read(AppSettings(longPressTimeoutMs = 40))
        )
        assertEquals(
            "a file carrying 4000 ms is shown as the longest",
            AppSettings.LONG_PRESS_TIMEOUT_MAX_MS.toString(),
            row.read(AppSettings(longPressTimeoutMs = 4_000))
        )
    }

    /**
     * The car reads dark mode and rendering from the same stored values, so those two rows are
     * shared; keep-screen-on, the long-press delay and the light-sensor sensitivity describe the
     * phone, so the car is never offered them (spec: `settings-taxonomy` — "A phone-only row is
     * absent from the car").
     */
    @Test
    fun thePhoneRowsNeverReachTheCar() {
        val car = SettingsRegistry.rowsFor(SettingScope.CAR, mapTopicRows).map { it.key }
        val phoneOnly = listOf("ambient-sensitivity", "keep-screen-on", "long-press-timeout")

        assertTrue(
            "the car is offered neither the sensor, the screen timeout nor the long-press delay: $car",
            car.none { it in phoneOnly }
        )
        assertEquals(
            "the car is offered the two values both surfaces store, plus the style row; the symbol preference is a phone control whose stored value alone reaches the car",
            listOf("map-style", "dark-mode", "render-mode"),
            car
        )
        mapTopicRows.filter { it.scope == SettingScope.SHARED }.forEach { row ->
            assertNull("a shared row carries no surface mark: ${row.key}", row.surfaceContentDescriptionRes)
        }
        mapTopicRows.filter { it.scope == SettingScope.PHONE }.forEach { row ->
            assertEquals(
                "a phone-only row names the phone: ${row.key}",
                com.naviveylin.R.string.settings_scope_phone,
                row.surfaceContentDescriptionRes
            )
        }
    }

    private fun choicesOf(row: SettingSpec): List<String> =
        (row.payload as SettingControlPayload.Choice).choices.map { it.key }
}