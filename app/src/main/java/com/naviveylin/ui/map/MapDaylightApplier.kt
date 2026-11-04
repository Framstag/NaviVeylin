package com.naviveylin.ui.map

import android.content.Context
import android.util.Log
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.BundledMapStyles.DEFAULT_STYLE_NAME
import com.naviveylin.core.MapStyleLoadReporter
import com.naviveylin.core.stringResolver
import java.util.Collections
import java.util.WeakHashMap

/** Outcome of one [MapDaylightApplier.push]. */
internal enum class MapDaylightPush {
    /** The native side took the value; it was recorded as the applied one. */
    APPLIED,

    /** The client already carried the value — no native call, so no stylesheet reload. */
    UNCHANGED,

    /** The native side kept the previous variant (the reload was rejected); nothing recorded, so a retry pushes. */
    FAILED,

    /** The call itself threw; nothing recorded. */
    ERROR,
}

/**
 * The phone's app-side state for the stylesheet `daylight` flag, keyed on the client and the value
 * (`guidelines/MapStyling.md` §15 — "Load budget and dedupe key").
 *
 * The record holds the **dark presentation** the client was last given, the same polarity
 * `MapCanvasViewModel.resolvedDaylight()` and `CarDaylightApplier` use: a dark push unsets the
 * `daylight` flag.
 *
 * Every writer on the phone goes through here: `MapCanvasViewModel.ensureMapStyle` (the main map's
 * style/flag pair) and the mini map widget. Both are load paths for the same native client, so a
 * per-writer record would make each of them reload the whole stylesheet set for a value the client
 * already carries — measured as one extra `LoadStyleInternal` per mini-map mount. One record per
 * client and per value means the push happens once per *changed* value, whichever writer sees the
 * change first, and a later writer with the same value pays nothing.
 *
 * The record is written only after the native call returned and reported success
 * (`wasLastStyleLoadSuccessful()`), so a rejected reload is retried by the next writer instead of
 * being remembered as applied. A [force] push bypasses the record for the reasons
 * [MapStyleApplyReason] names: a freshly opened map database carries no style config of its own.
 *
 * Threading: the widget pushes from a composition effect and the view model from the main thread;
 * the record is guarded. The native call itself reloads the stylesheet on the DB thread.
 */
internal object MapDaylightApplier {

    private const val TAG = "MapDaylightApplier"

    /** One entry per client: the dark presentation that client carries; weak so a closed client is
     * not held by its record. */
    private val applied: MutableMap<OSMScoutClient, Boolean> =
        // `synchronizedMap` and not the raw map: `push` reads the record outside the native call
        // and writes it after, and a second writer may interleave.
        Collections.synchronizedMap(WeakHashMap<OSMScoutClient, Boolean>())

    /** Whether [push] with these arguments would touch the native side. */
    fun needsPush(client: OSMScoutClient, dark: Boolean): Boolean = applied[client] != dark

    /**
     * Applies the presentation to [client] unless the client already carries it.
     *
     * @param dark the resolved presentation: true selects the dark stylesheet variant
     * @param force push even when the recorded value equals [dark] (a database open may have
     *   dropped earlier work — [MapStyleApplyReason.STARTUP_DB_READY])
     */
    fun push(client: OSMScoutClient, dark: Boolean, force: Boolean = false): MapDaylightPush {
        if (!force && applied[client] == dark) return MapDaylightPush.UNCHANGED
        val accepted = try {
            client.setStyleSheetFlag(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT, !dark)
            // A flag change reloads the active stylesheet; when that load is rejected the
            // previously active style stays in effect (spec: map-styles — "Style flag change
            // fails").
            client.wasLastStyleLoadSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "setStyleSheetFlag failed", e)
            return MapDaylightPush.ERROR
        }
        if (!accepted) {
            // Not recorded: the client carries the other variant, so the next writer retries.
            applied.remove(client)
            return MapDaylightPush.FAILED
        }
        applied[client] = dark
        return MapDaylightPush.APPLIED
    }

    /**
     * Records the presentation a stylesheet load installed, without pushing: `loadStyleSheet` hands
     * the load the enabled flags (§15), so the load carries the variant itself.
     */
    fun record(client: OSMScoutClient, dark: Boolean) {
        applied[client] = dark
    }

    /** Drops the record for [client], so the next writer re-pushes. */
    fun forget(client: OSMScoutClient) {
        applied.remove(client)
    }

    /** Test seam: forgets every client. */
    fun reset() {
        applied.clear()
    }
}

/**
 * Reports one rejected variant reload through the shared seam
 * (`guidelines/MapStyling.md` §15a — every load path reports) and returns the message to show, or
 * `null` when the client says the reload was accepted.
 *
 * `requestedStyle` is the stylesheet that was reloaded — the active one, since a flag push
 * reloads whatever is installed — and [activeStyle] the style that stayed in effect.
 */
internal fun rejectedVariantMessage(context: Context, client: OSMScoutClient): String? {
    val active = try {
        client.getActiveStyleSheet()
    } catch (e: Exception) {
        Log.e("MapDaylightApplier", "getActiveStyleSheet failed", e)
        null
    }
    val accepted = try {
        client.wasLastStyleLoadSuccessful()
    } catch (e: Exception) {
        Log.e("MapDaylightApplier", "wasLastStyleLoadSuccessful failed", e)
        true
    }
    return MapStyleLoadReporter.reportFailure(
        resolver = context.stringResolver(),
        requestedStyle = active ?: DEFAULT_STYLE_NAME,
        activeStyle = active,
        loadSucceeded = accepted,
    )
}
