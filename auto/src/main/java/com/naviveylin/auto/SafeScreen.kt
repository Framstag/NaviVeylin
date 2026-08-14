package com.naviveylin.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.naviveylin.core.DiagnosticsLog

/**
 * Screen wrapper that converts any [onGetTemplate] exception into an error
 * [PaneTemplate] instead of letting it kill the process.
 *
 * Use for simple/stateless screens (loading, error, diagnostics fallback).
 * Stateful screens that register lifecycle observers in `init` must not be
 * wrapped — their observers would never start — they should instead wrap
 * their template-building code in try/catch and return
 * [SafeScreen.errorTemplate] on failure (see [NavigationScreen]).
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
            errorTemplate(e.message)
        }
    }

    companion object {
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val ERROR_TITLE = "Error"

        /** Build an error [PaneTemplate]; safe to call without a live context. */
        fun errorTemplate(message: String?): PaneTemplate {
            val pane = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle(ERROR_TITLE)
                        .addText(message ?: "Unknown error")
                        .build()
                )
                .build()
            return PaneTemplate.Builder(pane)
                .setTitle("NaviVeylin")
                .build()
        }
    }
}

/**
 * Lightweight screen shown while the Hilt/native client warmup (see
 * [NavigationSession]) is still running. Returned synchronously so the host
 * gets its first template without waiting on heavy initialization.
 */
class LoadingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): PaneTemplate {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Loading map data…")
                    .addText("Preparing navigation")
                    .build()
            )
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle("NaviVeylin")
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

    override fun onGetTemplate(): PaneTemplate {
        val retryAction = Action.Builder()
            .setTitle("Retry")
            .setOnClickListener { onRetry() }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Startup failed")
                    .addText(message)
                    .addAction(retryAction)
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("NaviVeylin")
            .setActionStrip(ActionStrip.Builder().addAction(retryAction).build())
            .build()
    }
}
