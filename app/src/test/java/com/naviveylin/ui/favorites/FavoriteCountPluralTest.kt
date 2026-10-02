package com.naviveylin.ui.favorites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/**
 * The favorites group-card subtitle is a plural resource, not an inline English
 * string (spec: i18n-l10n — Favorites group card shows a localized count). The
 * card renders `pluralStringResource(R.plurals.favorite_count, count, count)`;
 * this pins both locales' forms, including that the count reaches the string as
 * a format argument.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteCountPluralTest {

    private fun quantity(count: Int): String =
        ApplicationProvider.getApplicationContext<Context>()
            .resources.getQuantityString(R.plurals.favorite_count, count, count)

    @Test
    fun englishSingularAndPlural() {
        RuntimeEnvironment.setQualifiers("en")
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("1 favorite", quantity(1))
        assertEquals("3 favorites", quantity(3))
    }

    @Test
    fun germanSingularAndPlural() {
        // A German device must not read "2 favorites" / "3 favorites".
        RuntimeEnvironment.setQualifiers("de")
        Locale.setDefault(Locale.GERMANY)

        assertEquals("1 Favorit", quantity(1))
        assertEquals("3 Favoriten", quantity(3))
    }
}
