package com.naviveylin.core

import com.framstag.libosmscout.client.RoadInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The road (ref + name) and the speed a surface resolved at its latest fix
 * (spec: `current-road-info` — One free-driving road/speed status feeds every
 * surface and the notification). Published by the surface that is driving;
 * consumed by the on-screen label, the car label and the ongoing notification,
 * so the two display paths cannot disagree.
 *
 * An empty [roadRef]/[roadName] means a fix was processed but no road was found
 * there ([hasRoad] false); a **null** status means no fix has been processed yet
 * (the notification then shows its neutral title, spec:
 * `navigation-ongoing-notification` — No fix yet means no stale road).
 */
data class FreeDrivingStatus(
    /** Road ref tag (e.g. "B 1"), empty when the road has none. */
    val roadRef: String = "",
    /** Road name, empty when the road has none. */
    val roadName: String = "",
    /** Ground speed in km/h, NaN when unknown. */
    val speedKmH: Double = Double.NaN
) {
    /** True when the status names a road (a ref or a name is present). */
    val hasRoad: Boolean
        get() = roadRef.isNotEmpty() || roadName.isNotEmpty()

    /** Display text "ref name" (only the present parts) or null when no road is known. */
    val roadText: String?
        get() = listOf(roadRef, roadName)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
}

/**
 * Surface-independent publisher of the free-driving road/speed status (spec:
 * `current-road-info` — One free-driving road/speed status feeds every surface and
 * the notification). Keyed per surface with retain-on-death semantics, like
 * [DrivingModeProvider]: the exposed [status] is the most recently published entry,
 * and a surface that dies (screen destroyed) keeps the value it last published —
 * [clear] is an explicit act, never a side effect of a surface going away.
 *
 * One publisher at a time wins (last resolution wins): the notification is
 * process-wide, so the rare both-surfaces case resolves to whichever surface
 * published last. Values are guarded by the same lock discipline as
 * [DrivingModeProvider].
 *
 * The implementation lives in `:core` (bound by the app's DI modules) so both the
 * phone ViewModel and the car screen publish into the same instance, and the car
 * surface reaches it through [AutoEntryPoint].
 */
class FreeDrivingStatusProvider {

    private val _status = MutableStateFlow<FreeDrivingStatus?>(null)

    /** The most recently published status, or null before the first publish/after a clear. */
    val status: StateFlow<FreeDrivingStatus?> = _status.asStateFlow()

    /**
     * Per-surface entries in publish order (the most recent is the last). A
     * `LinkedHashMap` so a cleared surface falls back to the entry published
     * before it. Guarded by [publish]'s lock.
     */
    private val perSurface = LinkedHashMap<String, FreeDrivingStatus>()

    /**
     * Publish the road and speed [surface] resolved at its latest fix. A null
     * [road] is a real publish ("a fix was processed, no road found"), not a
     * clear — the label disappears while the notification keeps the fallback text.
     * The emitted value and the diagnostics line only change when the resulting
     * status differs from the one already shown.
     */
    @Synchronized
    fun publish(surface: String, road: RoadInfo?, speedKmH: Double) {
        val status = FreeDrivingStatus(
            roadRef = road?.ref.orEmpty(),
            roadName = road?.name.orEmpty(),
            speedKmH = speedKmH
        )
        perSurface.remove(surface)
        perSurface[surface] = status
        if (_status.value == status) return
        _status.value = status
        val speed = if (status.speedKmH.isNaN()) {
            "none"
        } else {
            status.speedKmH.roundToInt().toString()
        }
        DiagnosticsLog.log(
            TAG,
            "free-driving status: source=$surface road=${status.roadText ?: "none"} speed=$speed"
        )
    }

    /**
     * Drop [surface]'s entry (mode exit, screen destroy). Falls back to the most
     * recently published entry of another surface, or to no status at all.
     */
    @Synchronized
    fun clear(surface: String) {
        if (perSurface.remove(surface) == null) return
        val next = perSurface.values.lastOrNull()
        if (_status.value == next) return
        _status.value = next
    }

    companion object {
        /** Phone map surface (MapCanvasViewModel). */
        const val SURFACE_PHONE = "phone"

        /** Android Auto free-driving screen. */
        const val SURFACE_CAR = "car"

        /** Diagnostics tag of the published status (spec: auto-diagnostics). */
        private const val TAG = "FREEDRIVE"
    }
}
