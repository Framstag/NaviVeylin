package com.naviveylin.ui.about

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.naviveylin.test.MainDispatcherRule

/**
 * Tests for the license screen's data path (spec: about-dialog — list reflects
 * the running build, License without distributable text links to its source,
 * works offline).
 *
 * The generated inventory is the input, so the fixtures here mirror the shape
 * `generateLicenseAssets<Variant>` writes: an identifier per component, a text
 * file for distributed license texts, and a link for licenses whose terms stay
 * with their owner.
 */
class LicenseInventoryTest {

    private val fixture = """
        {
          "appVersion": "2026-09-13-1",
          "components": [
            {
              "name": "expat",
              "version": "2.8.2",
              "identifier": "MIT",
              "licenseName": null,
              "licenseUrl": null,
              "scope": "shipped",
              "noticeRequired": true,
              "textFile": "MIT.txt",
              "note": null,
              "unexpectedFutureField": "ignored"
            },
            {
              "name": "play-services-location",
              "group": "com.google.android.gms",
              "version": "21.1.0",
              "identifier": "LicenseRef-AndroidSDK",
              "licenseName": "Android Software Development Kit License",
              "licenseUrl": "https://developer.android.com/studio/terms.html",
              "scope": "shipped",
              "noticeRequired": false,
              "textFile": null,
              "note": null
            },
            {
              "name": "protobuf",
              "version": "6.33.4",
              "identifier": "BSD-3-Clause",
              "scope": "buildTimeOnly",
              "noticeRequired": false,
              "textFile": null,
              "note": null
            }
          ],
          "texts": [ { "identifier": "MIT", "source": "licenses/texts/MIT.txt" } ],
          "links": [
            {
              "identifier": "LicenseRef-AndroidSDK",
              "name": "Android Software Development Kit License",
              "url": "https://developer.android.com/studio/terms.html"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the generated inventory`() {
        val inventory = parseLicenseInventory(fixture)
        assertEquals("2026-09-13-1", inventory.appVersion)
        assertEquals(3, inventory.components.size)
        assertEquals("licenses/texts/MIT.txt", inventory.texts.single().sourceDescription)
        assertEquals(
            "https://developer.android.com/studio/terms.html",
            inventory.links.single().url
        )
    }

    @Test
    fun `unknown fields do not break the reader`() {
        // The fixture above carries "unexpectedFutureField": a newer generator
        // must not make an older reader fail.
        assertEquals(3, parseLicenseInventory(fixture).components.size)
    }

    @Test
    fun `component with a distributed text carries its file`() {
        val expat = parseLicenseInventory(fixture).components.first { it.name == "expat" }
        assertEquals("MIT.txt", expat.textFile)
        assertEquals("MIT", expat.identifier)
        assertTrue(expat.isDistributed)
        assertEquals("expat", expat.qualifiedName)
    }

    @Test
    fun `license without distributable text carries a link instead`() {
        val play = parseLicenseInventory(fixture).components
            .first { it.name == "play-services-location" }
        assertNull(play.textFile)
        assertEquals("LicenseRef-AndroidSDK", play.identifier)
        assertEquals("Android Software Development Kit License", play.licenseName)
        assertEquals("https://developer.android.com/studio/terms.html", play.licenseUrl)
        assertEquals("com.google.android.gms:play-services-location", play.qualifiedName)
    }

    @Test
    fun `build time only components are recognisable`() {
        val protobuf = parseLicenseInventory(fixture).components.first { it.name == "protobuf" }
        assertTrue(!protobuf.isDistributed)
        assertEquals("buildTimeOnly", protobuf.scope)
    }
}

/**
 * Tests for the license screen's state handling: loading, content, error, and
 * the two kinds of detail view (spec: about-dialog — license list reachable,
 * full license text reachable, License without distributable text links to its
 * source, works offline).
 *
 * Robolectric because the diagnostics log goes through `android.util.Log`; the
 * default sandbox is used deliberately (`AGENTS.md` classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class LicensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSource(
        private val inventory: LicenseInventory,
        private val texts: Map<String, String> = emptyMap(),
        private val failInventory: Boolean = false,
        private val failTexts: Boolean = false,
    ) : LicenseInventorySource {
        var textRequests = mutableListOf<String>()
        override suspend fun loadInventory(): LicenseInventory {
            if (failInventory) throw IllegalStateException("no asset")
            return inventory
        }

        override suspend fun loadText(fileName: String): String {
            textRequests += fileName
            if (failTexts) throw IllegalStateException("missing $fileName")
            return texts[fileName] ?: throw IllegalStateException("missing $fileName")
        }
    }

    private val shipped = LicenseComponent(
        name = "expat",
        version = "2.8.2",
        identifier = "MIT",
        textFile = "MIT.txt"
    )
    private val linkOnly = LicenseComponent(
        name = "play-services-location",
        group = "com.google.android.gms",
        version = "21.1.0",
        identifier = "LicenseRef-AndroidSDK",
        licenseName = "Android Software Development Kit License",
        licenseUrl = "https://developer.android.com/studio/terms.html"
    )

    @Test
    fun `inventory is loaded into content state`() = runTest {
        val source = FakeSource(LicenseInventory(appVersion = "1.0", components = listOf(shipped)))
        val viewModel = LicensesViewModel(source)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LicensesUiState.Content)
        assertEquals(listOf(shipped), (state as LicensesUiState.Content).components)
        assertEquals("1.0", state.appVersion)
    }

    @Test
    fun `unavailable inventory becomes an error state carrying the reason`() = runTest {
        val viewModel = LicensesViewModel(
            FakeSource(LicenseInventory(), failInventory = true)
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LicensesUiState.Error)
        assertEquals("no asset", (state as LicensesUiState.Error).message)
    }

    @Test
    fun `selecting a component with a text opens that text`() = runTest {
        val source = FakeSource(
            LicenseInventory(components = listOf(shipped)),
            texts = mapOf("MIT.txt" to "MIT license text")
        )
        val viewModel = LicensesViewModel(source)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.select(shipped)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val selected = (viewModel.uiState.value as LicensesUiState.Content).selected
        assertEquals("MIT license text", selected?.text)
        assertNull(selected?.linkUrl)
        assertEquals(listOf("MIT.txt"), source.textRequests)
    }

    @Test
    fun `selecting a component without a distributed text offers the link`() = runTest {
        val source = FakeSource(LicenseInventory(components = listOf(linkOnly)))
        val viewModel = LicensesViewModel(source)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.select(linkOnly)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val selected = (viewModel.uiState.value as LicensesUiState.Content).selected
        assertEquals("https://developer.android.com/studio/terms.html", selected?.linkUrl)
        assertNull(selected?.text)
        assertTrue(source.textRequests.isEmpty())
    }

    @Test
    fun `unreadable text leaves the detail open without a text pane`() = runTest {
        val source = FakeSource(LicenseInventory(components = listOf(shipped)), failTexts = true)
        val viewModel = LicensesViewModel(source)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.select(shipped)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val selected = (viewModel.uiState.value as LicensesUiState.Content).selected
        assertNull(selected?.text)
        assertNull(selected?.linkUrl)
    }

    @Test
    fun `clearing the selection returns to the list`() = runTest {
        val source = FakeSource(
            LicenseInventory(components = listOf(shipped)),
            texts = mapOf("MIT.txt" to "MIT license text")
        )
        val viewModel = LicensesViewModel(source)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.select(shipped)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.clearSelection()

        val state = viewModel.uiState.value as LicensesUiState.Content
        assertNull(state.selected)
        assertEquals(listOf(shipped), state.components)
    }
}
