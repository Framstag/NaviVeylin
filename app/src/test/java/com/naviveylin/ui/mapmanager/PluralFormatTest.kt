package com.naviveylin.ui.mapmanager

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class PluralFormatTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun getQuantityStringFormatsGerman() {
        RuntimeEnvironment.setQualifiers("de")
        Locale.setDefault(Locale.GERMAN)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val s = ctx.resources.getQuantityString(R.plurals.active_downloads, 1, 1)
        assertEquals("1 aktiver Download", s)
    }

    @Test
    fun pluralStringResourceFormatsWithExplicitCountArg() {
        // Regression: compose-ui 1.7.6 pluralStringResource(id, count) calls
        // getQuantityString(id, count) WITHOUT format args, so %d stays literal.
        // The vararg overload (id, count, count) formats — the app must pass the
        // count explicitly as a format argument.
        RuntimeEnvironment.setQualifiers("de")
        Locale.setDefault(Locale.GERMAN)
        composeRule.setContent {
            Text(text = pluralStringResource(R.plurals.active_downloads, 1, 1))
        }
        composeRule.onNodeWithText("1 aktiver Download").assertExists()
    }
}
