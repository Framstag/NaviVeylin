package com.naviveylin.data

/** Lux below which the surroundings classify as dark. */
const val DARK_LUX = 10f

/** Lux above which the surroundings classify as light. */
const val LIGHT_LUX = 50f

/** A classification must hold this long before the signal flips. */
const val DEBOUNCE_MS = 10_000L

/**
 * Hysteresis classifier for the ambient light sensor: enters dark below
 * [DARK_LUX], leaves above [LIGHT_LUX], holds the previous state between the
 * thresholds so the presentation does not flap near the boundary.
 */
fun classifyLux(previousDark: Boolean, lux: Float): Boolean = when {
    lux < DARK_LUX -> true
    lux > LIGHT_LUX -> false
    else -> previousDark
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
