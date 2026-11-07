package com.naviveylin.test

import java.util.Locale
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that makes one locale the JVM's default for the duration of a case.
 *
 * A case that asserts a locale-independent format has to *be* on a decimal-comma locale to mean
 * anything — with the default-locale formatter a German install prints `acc=5,0` (spec `i18n-l10n` —
 * Log and diagnostic numbers are locale-independent). Doing that with an inline
 * `Locale.setDefault(…)` / `try` / `finally` pair in every case makes the restore the case's own
 * responsibility, and a case that fails between the two leaves the locale behind for the cases after
 * it: the rule restores it whatever the outcome, on the same thread the case ran on.
 *
 * A case that needs a second locale for one call uses [withLocale], which restores the rule's own
 * locale when it returns.
 */
class DefaultLocaleRule(private val locale: Locale) : TestWatcher() {

    private var previous: Locale = Locale.getDefault()

    override fun starting(description: Description) {
        previous = Locale.getDefault()
        Locale.setDefault(locale)
    }

    override fun finished(description: Description) {
        Locale.setDefault(previous)
    }

    companion object {

        /** Run [body] under [locale] and put the previous default back, whatever happens. */
        fun <T> withLocale(locale: Locale, body: () -> T): T {
            val previous = Locale.getDefault()
            return try {
                Locale.setDefault(locale)
                body()
            } finally {
                Locale.setDefault(previous)
            }
        }
    }
}
