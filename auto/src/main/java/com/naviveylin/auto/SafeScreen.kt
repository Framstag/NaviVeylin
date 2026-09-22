package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SearchTemplate.SearchCallback
import com.naviveylin.auto.R
import androidx.car.app.model.Template
import com.naviveylin.core.DiagnosticsLog

/**
 * Screen wrapper that converts any [onGetTemplate] exception into an error
 * [PaneTemplate] instead of letting it kill the process.
 *
 * Use for simple/stateless screens (loading, error, diagnostics fallback).
 * Stateful screens that register lifecycle observers in `init` must not be
 * wrapped — their observers would never start — they should instead build their
 * template through one of the `car*Template` wrappers in this file
 * (`carScreenTemplate`, `carPaneTemplate`, `carListTemplate`,
 * `carSearchTemplate`), which serve the matching error template on failure.
 */
class SafeScreen(
    carContext: CarContext,
    private val templateBuilder: () -> Template
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return try {
            templateBuilder()
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "SafeScreen template build failed", e)
            errorTemplate(carContext, e.message)
        }
    }

    companion object {
        /** Build an error [PaneTemplate]; safe to call without a live context. */
        fun errorTemplate(carContext: CarContext, message: String?): PaneTemplate {
            val pane = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.error))
                        .addText(errorText(carContext, message))
                        .build()
                )
                .build()
            return PaneTemplate.Builder(pane)
                .setHeader(Header.Builder().setTitle(carContext.getString(R.string.app_name)).build())
                .build()
        }

        /**
         * Error template for a screen whose [Screen.onGetTemplate] returns a
         * [ListTemplate]: same shape as [errorTemplate], expressed as a list.
         */
        internal fun errorListTemplate(carContext: CarContext, message: String?): ListTemplate =
            ListTemplate.Builder()
                .setHeader(
                    Header.Builder()
                        .setTitle(carContext.getString(R.string.app_name))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setSingleList(
                    ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.error))
                                .addText(errorText(carContext, message))
                                .build()
                        )
                        .build()
                )
                .build()

        /**
         * Error template for a screen whose [Screen.onGetTemplate] returns a
         * [SearchTemplate] (search screens cannot serve a pane): the search box
         * stays usable and its hint carries the failure.
         */
        internal fun errorSearchTemplate(carContext: CarContext, message: String?): SearchTemplate =
            SearchTemplate.Builder(object : SearchCallback {
                override fun onSearchTextChanged(searchText: String) = Unit
                override fun onSearchSubmitted(searchText: String) = Unit
            })
                .setSearchHint(errorText(carContext, message))
                .setHeaderAction(Action.BACK)
                .setItemList(
                    ItemList.Builder()
                        .addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.error))
                                .addText(errorText(carContext, message))
                                .build()
                        )
                        .build()
                )
                .build()

        /** Non-empty error text for the error templates (a row title/text must not be empty). */
        private fun errorText(carContext: CarContext, message: String?): String =
            message?.takeIf { it.isNotBlank() } ?: carContext.getString(R.string.unknown_error)
    }
}

/**
 * The template diagnostics tag shared by the wrapper and [SafeScreen].
 */
private const val TEMPLATE_TAG = "TEMPLATE"

/**
 * Build a car template, confining a fault to the build (spec: car-host-fault-isolation —
 * No fault escapes into the host path).
 *
 * The car-app library dispatches `onGetTemplate` on the app's main thread and rethrows an
 * app exception there (`RemoteUtils.dispatchCallFromHost`), which kills the process — and the
 * templates host then dies with it (TODO.md §51). Every car screen therefore builds its
 * template through one of the wrappers below, which serve an error template instead of
 * dying. [fallback] must return the same template type the screen declares.
 */
internal inline fun <T : Template> carTemplate(
    carContext: CarContext,
    fallback: (String?) -> T,
    build: () -> T
): T = try {
    build()
} catch (t: Throwable) {
    DiagnosticsLog.logThrowable(TEMPLATE_TAG, "template build failed — error template served", t)
    fallback(t.message)
}

/** Template build for a screen that returns a [Template] (no narrower type). */
internal inline fun carScreenTemplate(carContext: CarContext, build: () -> Template): Template =
    carTemplate(carContext, { SafeScreen.errorTemplate(carContext, it) }, build)

/** Template build for a screen that returns a [PaneTemplate]. */
internal inline fun carPaneTemplate(carContext: CarContext, build: () -> PaneTemplate): PaneTemplate =
    carTemplate(carContext, { SafeScreen.errorTemplate(carContext, it) }, build)

/** Template build for a screen that returns a [ListTemplate]. */
internal inline fun carListTemplate(carContext: CarContext, build: () -> ListTemplate): ListTemplate =
    carTemplate(carContext, { SafeScreen.errorListTemplate(carContext, it) }, build)

/** Template build for a screen that returns a [SearchTemplate]. */
internal inline fun carSearchTemplate(carContext: CarContext, build: () -> SearchTemplate): SearchTemplate =
    carTemplate(carContext, { SafeScreen.errorSearchTemplate(carContext, it) }, build)

/**
 * Lightweight loading template ("Loading map data…").
 *
 * NOT used as a stack root: androidx.car.app [ScreenManager] cannot pop the
 * root screen, so a transient loading root would be re-revealed by every
 * later popToRoot() and wedge the session on it. [NavigationSession] instead
 * serves the real root screen immediately and preloads the native client in
 * the background. This screen remains available for temporary/overlay use.
 */
class LoadingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): PaneTemplate {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.loading_map_data))
                    .addText(carContext.getString(R.string.preparing_navigation))
                    .build()
            )
            .build()
        return PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.app_name)).build())
            .build()
    }
}

/**
 * Fallback screen shown when Android Auto session startup fails.
 * Keeps the process alive and offers a Retry action.
 */
class ErrorScreen(
    carContext: CarContext,
    private val message: String,
    private val onRetry: () -> Unit
) : Screen(carContext) {

    init {
        enableBackNavigation()
    }

    override fun onGetTemplate(): PaneTemplate {
        val retryAction = Action.Builder()
            .setTitle(carContext.getString(R.string.retry))
            .setOnClickListener { onRetry() }
            .build()

        val backAction = Action.Builder()
            .setTitle(carContext.getString(R.string.back))
            .setOnClickListener { guardedHostCall("pop (error screen back)") { screenManager.pop() } }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.startup_failed))
                    .addText(message)
                    .addAction(retryAction)
                    .addAction(backAction)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.app_name)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
