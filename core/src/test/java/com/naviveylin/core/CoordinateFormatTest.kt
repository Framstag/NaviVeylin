package com.naviveylin.core

import com.naviveylin.core.details.DetailsResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Tests for the shared coordinate string helpers ([formatCoordinatePair],
 * [formatCoordinate], [parseLatitude], [parseLongitude]) used by the phone UI
 * and the car screens.
 *
 * The German-default-locale cases pin the defect this helper was introduced for
 * (`TODO.md` §45 / §31): the app prefilled `51,51391` and could not parse its own
 * prefill back, and a coordinate label stopped matching the details resolver's
 * coordinate-pattern detector.
 */
@RunWith(RobolectricTestRunner::class)
class CoordinateFormatTest {

    private val originalDefaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun pairIsLocaleStableUnderGermanDefaultLocale() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("51.51391, 7.47434", formatCoordinatePair(51.51391, 7.47434))
        assertEquals("51.50000, 7.40000", formatCoordinatePair(51.5, 7.4))
    }

    @Test
    fun pairIsLocaleStableUnderEnglishDefaultLocale() {
        Locale.setDefault(Locale.US)

        assertEquals("51.51391, 7.47434", formatCoordinatePair(51.51391, 7.47434))
    }

    @Test
    fun pairKeepsFiveDecimalsAndNegativeValues() {
        assertEquals("-33.92487, 18.42406", formatCoordinatePair(-33.92487, 18.42406))
        assertEquals("0.00000, 0.00000", formatCoordinatePair(0.0, 0.0))
    }

    @Test
    fun pairUsesTheResourcePatternWhenProvided() {
        // The phone passes the `coordinates_format` resource ("%.5f, %.5f"), so
        // resource and constant must stay interchangeable.
        assertEquals(
            formatCoordinatePair(51.5, 7.4),
            formatCoordinatePair(51.5, 7.4, pattern = "%.5f, %.5f")
        )
    }

    @Test
    fun pairOutputMatchesTheDetailsResolverCoordinateLabelDetector() {
        // Drift guard (design D6): the formatter's output is what
        // DetailsResolver treats as "a coordinate label, not a title".
        val pair = formatCoordinatePair(51.51391, 7.47434)

        assertEquals(pair, pair.takeIf { DetailsResolver.COORDINATE_LABEL_REGEX.matches(it) })
        // A locale-form pair would NOT be recognised — the defect this helper removes.
        assertEquals(
            null,
            "51,51391, 7,47434".takeIf { DetailsResolver.COORDINATE_LABEL_REGEX.matches(it) }
        )
    }

    @Test
    fun singleCoordinateIsLocaleStable() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("51.51391", formatCoordinate(51.51391))
        assertEquals("7.47434", formatCoordinate(7.47434))
    }

    @Test
    fun prefilledCoordinateCanBeParsedBackUnchanged() {
        Locale.setDefault(Locale.GERMANY)

        val latText = formatCoordinate(51.51391)
        val lonText = formatCoordinate(7.47434)

        assertEquals(51.51391, parseLatitude(latText)!!, 0.0)
        assertEquals(7.47434, parseLongitude(lonText)!!, 0.0)
    }

    @Test
    fun parseAcceptsBothDecimalSeparators() {
        assertEquals(51.51391, parseLatitude("51.51391")!!, 0.0)
        assertEquals(51.51391, parseLatitude("51,51391")!!, 0.0)
        assertEquals(7.47434, parseLongitude("7.47434")!!, 0.0)
        assertEquals(7.47434, parseLongitude("7,47434")!!, 0.0)
    }

    @Test
    fun parseAcceptsSignWhitespaceAndWholeNumbers() {
        assertEquals(-33.92487, parseLatitude("-33.92487")!!, 0.0)
        assertEquals(-33.92487, parseLatitude(" -33,92487 ")!!, 0.0)
        assertEquals(51.0, parseLatitude("+51")!!, 0.0)
        assertEquals(51.0, parseLatitude("51")!!, 0.0)
        assertEquals(0.0, parseLongitude("0,0")!!, 0.0)
    }

    @Test
    fun parseRejectsTextThatIsNotOneCoordinateValue() {
        // Two separators ("51,51,391" — the comma-typed grouping the dialog must
        // keep rejecting), a grouping separator, an exponent and a bare separator.
        assertNull(parseLatitude("51,51,391"))
        assertNull(parseLatitude("1,234,567"))
        assertNull(parseLatitude("1e2"))
        assertNull(parseLatitude("51."))
        assertNull(parseLatitude(".5"))
        assertNull(parseLatitude("51abc"))
        assertNull(parseLatitude(""))
        assertNull(parseLatitude("   "))
        assertNull(parseLatitude(null))
    }

    @Test
    fun parseHoldsEachValueToItsOwnRange() {
        assertEquals(90.0, parseLatitude("90")!!, 0.0)
        assertEquals(-90.0, parseLatitude("-90,0")!!, 0.0)
        assertNull(parseLatitude("90.00001"))
        assertNull(parseLatitude("91"))

        assertEquals(180.0, parseLongitude("180")!!, 0.0)
        assertNull(parseLongitude("180.00001"))
        assertNull(parseLongitude("-181"))

        // A latitude that is out of range as a longitude stays valid.
        assertEquals(90.0, parseLongitude("90")!!, 0.0)
    }
}
