package com.naviveylin.core

/**
 * The clock a component's timing decisions read.
 *
 * A component SHALL NOT read the system clock at the site of a timing decision: the decision reads
 * this seam, so a test moves time instead of waiting for it (spec: `navigation-engine` — Engine
 * lifecycle and threading; spec: `unit-test-suite-runtime` — Injected time instead of a real clock).
 */
fun interface EngineTimeSource {

    /** Milliseconds since the Unix epoch, the unit the engine's timestamps already use. */
    fun nowMillis(): Long

    companion object {
        /**
         * The production source: the JVM wall clock.
         *
         * The call is deliberately fully qualified — the property shadows the class name inside this
         * companion, so an unqualified `System.currentTimeMillis()` here would recurse into the
         * property.
         */
        val System: EngineTimeSource = EngineTimeSource(java.lang.System::currentTimeMillis)
    }
}
