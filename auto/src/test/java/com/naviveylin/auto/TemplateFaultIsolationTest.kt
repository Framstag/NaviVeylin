package com.naviveylin.auto

import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoSettingsProvider
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.addressbook.AddressBookContactsProvider
import com.naviveylin.core.addressbook.AddressBookSearchProvider
import com.naviveylin.core.addressbook.ContactAddressBookEntry
import com.naviveylin.core.addressbook.ContactPostalAddress
import dagger.hilt.EntryPoints
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template-build fault isolation for every car screen (spec:
 * car-host-fault-isolation — No fault escapes into the host path).
 *
 * The car-app library dispatches `onGetTemplate` on the app's main thread and
 * rethrows an app exception there, which kills the process — and the templates
 * host dies with it (TODO.md §51). Every car screen therefore builds its template
 * through one of the `car*Template` wrappers in `SafeScreen.kt`; this test fails
 * the build of each screen and asserts the host still receives a usable error
 * template instead of the exception escaping.
 *
 * The fault is injected through the screen's own resources ([CarContext.getString]
 * throws for every resource except the three the error templates need), which is
 * the same class of failure a library validator raises. The address-book screen
 * builds no resource string on its list path, so its fault is injected through a
 * contact whose row text is empty (an empty row text is rejected by the library
 * validator) — see [addressBookScreenServesTheErrorTemplateWhenTheBuildFails].
 */
@RunWith(RobolectricTestRunner::class)
class TemplateFaultIsolationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var carContext: CarContext

    private val entryPoint = mockk<AutoEntryPoint>(relaxed = true)
    private val navigationViewModel = mockk<NavigationViewModel>(relaxed = true)
    private val settingsProvider = mockk<AutoSettingsProvider>(relaxed = true)

    /** Title every error template carries. */
    private val errorTitle: String
        get() = appContext.getString(R.string.error)

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        carContext = faultingCarContext()
        every { carContext.applicationContext } returns appContext
        every { carContext.resources } returns appContext.resources
        every { carContext.filesDir } returns appContext.filesDir
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
    }

    /**
     * A car context whose resource lookups throw — the injected template-build
     * fault. The three resources the error templates themselves need stay usable,
     * so a served error template is distinguishable from a broken fallback.
     */
    private fun faultingCarContext(): CarContext {
        val allowed = setOf(R.string.error, R.string.app_name, R.string.unknown_error)
        val context = testCarContext()
        every { context.getString(any()) } answers {
            val resId = firstArg<Int>()
            if (resId in allowed) appContext.getString(resId) else failWithoutThrow(resId)
        }
        return context
    }

    private fun failWithoutThrow(resId: Int): String =
        throw IllegalStateException("injected template-build failure (resource $resId)")

    // ── the shared wrappers ──

    @Test
    fun carScreenTemplateServesTheErrorTemplateWhenTheBuildThrows() {
        assertErrorPane(carScreenTemplate(carContext) { error("build failed") })
    }

    @Test
    fun carPaneTemplateServesTheErrorTemplateWhenTheBuildThrows() {
        assertErrorPane(carPaneTemplate(carContext) { error("build failed") })
    }

    @Test
    fun carListTemplateServesTheErrorTemplateWhenTheBuildThrows() {
        assertErrorList(carListTemplate(carContext) { error("build failed") })
    }

    @Test
    fun carSearchTemplateServesTheErrorTemplateWhenTheBuildThrows() {
        assertErrorSearch(carSearchTemplate(carContext) { error("build failed") })
    }

    // ── one case per screen ──

    @Test
    fun aboutScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(AboutScreen(carContext).onGetTemplate())
    }

    @Test
    fun diagnosticsScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(DiagnosticsScreen(carContext).onGetTemplate())
    }

    @Test
    fun errorOverlayScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(ErrorOverlayScreen(carContext, "navigation failed").onGetTemplate())
    }

    @Test
    fun errorOverlayScreenShowsTheMessageAndABackActionWhenItBuilds() {
        // The guarded path is covered above; this is the normal one, so a fault-injection test
        // cannot hide a broken overlay.
        val template = ErrorOverlayScreen(testCarContext(), "navigation failed").onGetTemplate()
        val row = template.pane.rows.first()

        assertEquals("navigation failed", row.title.toString())
        assertEquals(1, row.actions.size)
    }

    @Test
    fun candidatePickerScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(CandidatePickerScreen(carContext, emptyList()) { }.onGetTemplate())
    }

    @Test
    fun detailsScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(DetailsScreen(carContext, navigationViewModel, 51.5, 7.4).onGetTemplate())
    }

    @Test
    fun poiResultsScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(
            PoiResultsScreen(carContext, navigationViewModel, "restaurant", "Restaurants")
                .onGetTemplate()
        )
    }

    @Test
    fun poiSearchScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorPane(PoiSearchScreen(carContext, navigationViewModel).onGetTemplate())
    }

    @Test
    fun addressBookAddressPickerScreenServesTheErrorTemplateWhenTheBuildFails() = runTest(mainDispatcherRule.dispatcher) {
        // This screen's list path builds rows from contact data (no resource lookup),
        // so the fault is injected through its real resource path: an address that
        // resolves to nothing sets the no-location-found row, whose resource lookup
        // is the injected failure. The click is delivered the way the host delivers
        // it — through the row's click delegate.
        val contact = ContactAddressBookEntry(1L, "Ada", listOf(ContactPostalAddress(street = "Main 1")))
        every { entryPoint.addressBookSearchProvider() } returns
            mockk<AddressBookSearchProvider>().apply { coEvery { resolveAddress(any()) } returns emptyList() }

        val screen = AddressBookAddressPickerScreen(carContext, navigationViewModel, contact)
        clickFirstRow(screen)

        assertErrorList(awaitTemplate(screen, ::isErrorList)!!)
    }

    @Test
    fun favoritesScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(FavoritesScreen(carContext, navigationViewModel).onGetTemplate())
    }

    @Test
    fun overspeedDeltaPickerScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(
            OverspeedDeltaPickerScreen(carContext, settingsProvider).onGetTemplate()
        )
    }

    @Test
    fun preferencesScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(
            PreferencesScreen(carContext, settingsProvider = settingsProvider).onGetTemplate()
        )
    }

    @Test
    fun rootScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(RootScreen(carContext, navigationViewModel).onGetTemplate())
    }

    @Test
    fun searchHistoryScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(SearchHistoryScreen(carContext, navigationViewModel).onGetTemplate())
    }

    @Test
    fun vehicleAnchorPickerScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorList(
            VehicleAnchorPickerScreen(carContext, settingsProvider = settingsProvider).onGetTemplate()
        )
    }

    @Test
    fun searchScreenServesTheErrorTemplateWhenTheBuildFails() {
        assertErrorSearch(SearchScreen(carContext, navigationViewModel).onGetTemplate())
    }

    @Test
    fun addressBookScreenServesTheErrorTemplateWhenTheBuildFails() = runTest(mainDispatcherRule.dispatcher) {
        // See the picker test: this screen's list path also builds from contact data,
        // so the fault rides its own no-location-found resource path.
        val contactsProvider = mockk<AddressBookContactsProvider>()
        coEvery { contactsProvider.contactsWithAddresses() } returns
            listOf(ContactAddressBookEntry(1L, "Ada", listOf(ContactPostalAddress(street = "Main 1"))))
        every { entryPoint.addressBookContactsProvider() } returns contactsProvider
        every { entryPoint.addressBookSearchProvider() } returns
            mockk<AddressBookSearchProvider>().apply { coEvery { resolveAddress(any()) } returns emptyList() }

        val screen = AddressBookScreen(carContext, navigationViewModel)
        clickFirstRow(screen)

        assertErrorSearch(awaitTemplate(screen, ::isErrorSearch)!!)
    }

    // ── driving a screen into its fault ──

    /**
     * Click the first row of the screen's current template the way the host does:
     * through the row's click delegate. Waits (bounded) for the row to exist, so a
     * screen whose list arrives from a background load is covered too.
     */
    private fun TestScope.clickFirstRow(screen: Screen, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            val row = runCatching { firstRowOf(screen.onGetTemplate()) }.getOrNull()
            if (row != null) {
                row.onClickDelegate?.sendClick(mockk(relaxed = true))
                return
            }
            Thread.sleep(5)
        }
        failTemplate("no clickable row became available within ${timeoutMs}ms")
    }

    /** Bounded wait for the screen to build a template matching [accept]. */
    private fun TestScope.awaitTemplate(
        screen: Screen,
        accept: (Template) -> Boolean,
        timeoutMs: Long = 5_000
    ): Template? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            val template = runCatching { screen.onGetTemplate() }.getOrNull()
            if (template != null && accept(template)) return template
            Thread.sleep(5)
        }
        return null
    }

    /** First row of a list- or search-template, or null when it has none. */
    private fun firstRowOf(template: Template): Row? = when (template) {
        is ListTemplate -> template.singleList?.items?.firstOrNull() as? Row
        is SearchTemplate -> template.itemList?.items?.firstOrNull() as? Row
        else -> null
    }

    private fun isErrorPane(template: Template): Boolean =
        template is PaneTemplate && template.pane.rows.first().title.toString() == errorTitle

    private fun isErrorList(template: Template): Boolean =
        firstRowOf(template)?.title?.toString() == errorTitle && template is ListTemplate

    private fun isErrorSearch(template: Template): Boolean =
        template is SearchTemplate && errorRowOf(template) == errorTitle

    // ── assertions ──

    private fun assertErrorPane(template: Template) {
        val pane = template as? PaneTemplate
            ?: failTemplate("expected the error pane, got ${template.javaClass.simpleName}")
        assertEquals(errorTitle, pane.pane.rows.first().title.toString())
    }

    private fun assertErrorList(template: Template) {
        val list = template as? ListTemplate
            ?: failTemplate("expected the error list, got ${template.javaClass.simpleName}")
        val row = list.singleList?.items?.firstOrNull() as? Row
            ?: failTemplate("expected one error row, got ${list.singleList?.items}")
        assertEquals(errorTitle, row.title.toString())
    }

    private fun assertErrorSearch(template: Template) {
        val search = template as? SearchTemplate
            ?: failTemplate("expected the error search template, got ${template.javaClass.simpleName}")
        assertEquals(errorTitle, errorRowOf(search))
    }

    private fun failTemplate(message: String): Nothing = throw AssertionError(message)

    /** Title of the first row of an error search template, or null when it has none. */
    private fun errorRowOf(template: SearchTemplate): String? =
        (template.itemList?.items?.firstOrNull() as? Row)?.title?.toString()
}
