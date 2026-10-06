package com.naviveylin.build.testing

/**
 * Reads the build's boolean switches (change `speed-up-build-test-gate`, spec
 * `build-test-gate` / `test-coverage`).
 *
 * `-P<name>` (empty) and `-P<name>=true` switch the flag on; `-P<name>=false` or leaving it out
 * switches it off. Any other value is refused with an actionable message: a typo that silently
 * means "off" produces a run that looks as asked for but did something else — a "forced" run that
 * executed nothing, or a coverage run with no instrumentation data.
 */
object BuildFlags {

    fun isSet(name: String, value: String?, onLabel: String): Boolean =
        when (val normalized = value?.trim()?.lowercase()) {
            null, "false" -> false
            "", "true" -> true
            else -> throw IllegalArgumentException(
                "-P$name=$normalized is not a value this build understands. Use -P$name " +
                    "(or -P$name=true) $onLabel, or leave the property out (-P$name=false). " +
                    "Guessing would let a typo produce a build that looks as asked for but is not."
            )
        }
}
