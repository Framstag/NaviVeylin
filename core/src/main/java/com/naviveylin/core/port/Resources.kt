package com.naviveylin.core.port

/**
 * The string table a feature reads its user-facing text from — the platform's resource system abstracted.
 *
 * A `port` holds abstraction only (spec `package-structure` — port holds abstraction only): no Android
 * type and no package of ours appears here, so a feature composes and formats its text without a
 * `Context` and stays Android-free (spec `package-structure` — a feature is Android-free).
 *
 * [key] is a string table entry name — the name the platform's table gives the entry, not an id and not a
 * translated value — so the same text is reachable from every surface without one of them owning the
 * vocabulary. The platform side implements this interface over that table.
 */
fun interface Resources {

    /**
     * The text stored under [key] for the device's current locale, with [args] substituted into its
     * placeholders in order.
     */
    fun text(key: String, vararg args: Any): String
}
