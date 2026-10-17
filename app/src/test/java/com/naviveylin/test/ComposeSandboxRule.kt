package com.naviveylin.test

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Repairs the Robolectric sandbox's Compose dispatcher before every test of the class it is
 * installed in ([ComposeSandboxDispatcher] holds the defect and the measurement).
 *
 * Every Compose test class carries this rule **before** its Compose rule
 * (`@get:Rule(order = 0) val composeSandbox = ComposeSandboxRule()`), because the sandbox, not the
 * test, is what leaks: a class without this rule inherits whatever a previously finished test left
 * behind, and its `setContent` can then never idle.
 * `tools/check-compose-sandbox-rule.sh` runs in CI so a new Compose test class cannot skip it, and
 * `ComposeSandboxIsolationTest` proves the rule is what keeps a Compose case idle after a
 * non-Compose case wrote a Compose state.
 *
 * Only Compose test classes need it: `:auto` and `:core` have no Compose test sources, and a
 * non-Compose test does not idle, so the leak cannot fail one.
 */
class ComposeSandboxRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                ComposeSandboxDispatcher.repair()
                base.evaluate()
            }
        }
}
