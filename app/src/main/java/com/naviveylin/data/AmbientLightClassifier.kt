package com.naviveylin.data

import kotlinx.serialization.Serializable

/** Lux below which the surroundings classify as dark. */
const val DARK_LUX = 10f

/** Lux above which the surroundings classify as light. */
const val LIGHT_LUX = 50f

/** A classification must hold this long before the signal flips. */
const val DEBOUNCE_MS = 10_000L

/**
 * Ambient light sensor sensitivity: how readily the sensor triggers dark
 * presentation in **Automatic** mode. [OFF] disables the sensor (system night
 * mode remains the environment signal); [HIGH] is the current behavior and
 * enters dark least reluctantly; [MEDIUM] and [LOW] require progressively
 * darker surroundings to enter dark and progressively brighter surroundings to
 * leave it (wider hysteresis band). Persisted on [AppSettings].
 */
@Serializable
enum class AmbientLightSensitivity(
    /** Lux below which the surroundings classify as dark; null = sensor disabled. */
    val darkLux: Float?,
    /** Lux above which the surroundings classify as light; null = sensor disabled. */
    val lightLux: Float?
) {
    OFF(null, null),
    HIGH(DARK_LUX, LIGHT_LUX),
    MEDIUM(5f, 100f),
    LOW(2f, 200f)
}

/**
 * Hysteresis classifier for the ambient light sensor: enters dark below the
 * sensitivity's dark threshold, leaves above its light threshold, holds the
 * previous state between them so the presentation does not flap near the
 * boundary. [OFF] reports no classification ([previousDark] is returned).
 */
fun classifyLux(
    previousDark: Boolean,
    lux: Float,
    sensitivity: AmbientLightSensitivity = AmbientLightSensitivity.HIGH
): Boolean {
    val darkLux = sensitivity.darkLux ?: return previousDark
    val lightLux = sensitivity.lightLux ?: return previousDark
    return when {
        lux < darkLux -> true
        lux > lightLux -> false
        else -> previousDark
    }
}

/**
 * Debounces a boolean signal: a flip is only reported after the new value has
 * held for [debounceMs]. Filters sensor noise and transient shadows; the
 * hysteresis band already prevents threshold flapping.
 *
 * Pure — the caller supplies the wall clock ([nowMs]) so tests drive time
 * deterministically.
 */
class DebouncedSignal(
    private val debounceMs: Long = DEBOUNCE_MS,
    initial: Boolean = false
) {
    private var current = initial
    private var pending: Boolean? = null
    private var pendingSinceMs = 0L

    /** The current stable value. */
    val value: Boolean get() = current

    /** Feed a raw value; returns the stable (debounced) value. */
    fun update(raw: Boolean, nowMs: Long): Boolean {
        if (raw == current) {
            pending = null
            return current
        }
        if (pending != raw) {
            pending = raw
            pendingSinceMs = nowMs
        }
        if (nowMs - pendingSinceMs >= debounceMs) {
            current = raw
            pending = null
        }
        return current
    }
}
