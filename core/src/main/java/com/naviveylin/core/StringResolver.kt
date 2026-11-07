package com.naviveylin.core

import android.content.Context

/**
 * Resolves a string resource with optional format arguments.
 * Implemented by callers against their own [Context] (Compose
 * `LocalContext`, Car App Library `carContext`, or a test fake).
 *
 * This is the seam every localized label goes through: `:core` builds the wording
 * ([ManoeuvreLabels]) without owning a `Context`, and each surface supplies one.
 */
fun interface StringResolver {
    fun get(resId: Int, vararg args: Any): String
}

/** [StringResolver] backed by [Context.getString]. */
fun Context.stringResolver(): StringResolver =
    StringResolver { resId, args -> getString(resId, *args) }
