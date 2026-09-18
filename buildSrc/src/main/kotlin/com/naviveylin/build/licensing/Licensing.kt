package com.naviveylin.build.licensing

/**
 * Whether a component's code is distributed in the application artifact or is
 * only used while building it.
 *
 * License obligations follow distribution, so the same license can be acceptable
 * for a build-time tool and unacceptable for a shipped library. The policy file
 * keeps two permitted lists and this enum selects between them.
 */
enum class Scope(val id: String) {
    SHIPPED("shipped"),
    BUILD_TIME_ONLY("buildTimeOnly");
}

/**
 * A license that has no SPDX identifier, declared in the policy file under a
 * `LicenseRef-` identifier (the SPDX mechanism for licenses outside its list).
 *
 * @param id the `LicenseRef-` identifier reported for the component
 * @param name the license name as the component declares it
 * @param source canonical address of the license text
 * @param textDistributed whether the application distributes the text itself
 */
data class LicenseRefDeclaration(
    val id: String,
    val name: String,
    val source: String,
    val textDistributed: Boolean,
)

/**
 * The license a component is actually relied upon, where its declared license
 * offers a choice (for example `LGPL-2.1-only OR MPL-1.1`).
 *
 * @param from the declared expression, kept for validation that the election was
 *   actually offered
 * @param to the expression relied upon, containing no alternatives
 */
data class Election(val from: String, val to: String)

/**
 * One `reviewRequired` policy entry: a license whose identifiers still need the
 * application license decision before distribution, optionally scoped to named
 * components.
 *
 * @param license the identifier the entry is about
 * @param components the components the entry applies to; empty means every
 *   component carrying the license (the unscoped form)
 */
data class ReviewRequiredEntry(
    val license: String,
    val components: Set<String> = emptySet(),
)

/**
 * The declared license policy, loaded from `licenses/license-policy.json`.
 *
 * @param permittedShipped licenses permitted for distributed components
 * @param permittedBuildTimeOnly licenses permitted for build-time-only components
 * @param elections elections by component name
 * @param licenseRefs `LicenseRef-` declarations by identifier
 * @param reviewRequired identifiers that need the application license decision;
 *   reported as warnings, never treated as clearance. Entries honour their
 *   `components` scoping (empty = all carriers).
 * @param noticeRequired identifiers whose licenses require the notice text to
 *   accompany redistribution
 */
data class LicensePolicy(
    val permittedShipped: Set<String>,
    val permittedBuildTimeOnly: Set<String>,
    val elections: Map<String, Election> = emptyMap(),
    val licenseRefs: Map<String, LicenseRefDeclaration> = emptyMap(),
    val reviewRequired: List<ReviewRequiredEntry> = emptyList(),
    val noticeRequired: Set<String> = emptySet(),
)

/**
 * A component's license claim, as prepared by the build for evaluation.
 *
 * @param component stable component key, used for election lookup and messages
 * @param declared the license value the component declares
 * @param declaredIsSpdx whether [declared] is an SPDX identifier or expression
 *   rather than a bare license name
 * @param scope whether the component is distributed
 * @param caveat an unresolved doubt about the claim, reported rather than hidden
 */
data class ComponentLicense(
    val component: String,
    val declared: String?,
    val declaredIsSpdx: Boolean,
    val scope: Scope,
    val caveat: String? = null,
)

/** Outcome of resolving one component's license. */
sealed interface LicenseResolution {
    /** The component resolves; [ids] are the identifiers its license consists of. */
    data class Resolved(val identifier: String, val ids: List<String>) : LicenseResolution

    /** The declared license offers alternatives and no election is recorded. */
    data class NeedsElection(val declared: String, val offered: List<String>) : LicenseResolution

    /** Nothing could be resolved from the declared value. */
    data class Unresolved(val reason: String) : LicenseResolution
}

/** Result of evaluating a component set against the policy. */
data class GateResult(
    val violations: List<String>,
    val warnings: List<String>,
) {
    val passed: Boolean get() = violations.isEmpty()
}

/**
 * Splitting and normalising of SPDX-style license expressions.
 *
 * Only the operators this project's dependency set actually uses are handled:
 * `AND` for conjunctions and `OR` for alternatives, with optional parentheses.
 * `WITH` is treated as part of an identifier (`Apache-2.0 WITH LLVM-exception`)
 * because the policy lists such forms verbatim.
 */
object LicenseExpressions {

    /** Splits [expression] on top-level occurrences of [operator]. */
    fun splitTopLevel(expression: String, operator: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        val tokens = expression.split(" ").filter { it.isNotEmpty() }
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token == operator && depth == 0 -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> {
                    depth += token.count { it == '(' } - token.count { it == ')' }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(token)
                }
            }
            index++
        }
        parts += current.toString()
        return parts.map { stripParentheses(it) }.filter { it.isNotEmpty() }
    }

    /** The conjunction operands of [expression]. */
    fun conjuncts(expression: String): List<String> = splitTopLevel(expression, "AND")

    /** The alternative operands of [expression]. */
    fun disjuncts(expression: String): List<String> = splitTopLevel(expression, "OR")

    /** Removes whitespace and redundant parentheses around [expression]. */
    fun stripParentheses(expression: String): String {
        var result = expression.trim()
        while (result.startsWith("(") && result.endsWith(")") && enclosesWhole(result)) {
            result = result.substring(1, result.length - 1).trim()
        }
        return result
    }

    private fun enclosesWhole(expression: String): Boolean {
        var depth = 0
        expression.forEachIndexed { index, character ->
            if (character == '(') depth++
            if (character == ')') {
                depth--
                if (depth == 0 && index != expression.length - 1) return false
            }
        }
        return depth == 0
    }
}

/**
 * Resolves a component's license claim into concrete identifiers, applying a
 * recorded election where the declared expression offers a choice.
 */
class LicenseResolver(private val policy: LicensePolicy) {

    fun resolve(component: ComponentLicense): LicenseResolution {
        val declared = component.declared?.trim().orEmpty()
        if (declared.isEmpty()) {
            return LicenseResolution.Unresolved("no license value declared")
        }

        policy.elections[component.component]?.let { election ->
            return if (offers(election.from, election.to)) {
                LicenseResolution.Resolved(election.to, LicenseExpressions.conjuncts(election.to))
            } else {
                LicenseResolution.Unresolved(
                    "recorded election '${election.to}' is not an alternative offered by " +
                        "'${election.from}'"
                )
            }
        }

        if (declared.startsWith(LicenseGate.LICENSE_REF_PREFIX)) {
            return if (policy.licenseRefs.containsKey(declared)) {
                LicenseResolution.Resolved(declared, listOf(declared))
            } else {
                LicenseResolution.Unresolved(
                    "LicenseRef identifier '$declared' is not declared in licenses/license-policy.json"
                )
            }
        }

        if (component.declaredIsSpdx) {
            val alternatives = LicenseExpressions.disjuncts(declared)
            return if (alternatives.size > 1) {
                LicenseResolution.NeedsElection(declared, alternatives)
            } else {
                LicenseResolution.Resolved(declared, LicenseExpressions.conjuncts(declared))
            }
        }

        val declaredRef = policy.licenseRefs.values
            .firstOrNull { it.name.equals(declared, ignoreCase = true) }
        return if (declaredRef == null) {
            LicenseResolution.Unresolved(
                "license '$declared' has no SPDX identifier and no LicenseRef declaration"
            )
        } else {
            LicenseResolution.Resolved(declaredRef.id, listOf(declaredRef.id))
        }
    }

    /** True when every identifier in [elected] appears in [declared]. */
    private fun offers(declared: String, elected: String): Boolean {
        val electedIds = LicenseExpressions.conjuncts(elected)
        if (electedIds.isEmpty()) return false
        return electedIds.all { declared.contains(it) }
    }
}

/**
 * Evaluates components against the policy: a component fails when its license
 * cannot be resolved, when it has a choice and no election, or when a resolved
 * identifier is not permitted for the component's scope.
 */
class LicenseGate(policy: LicensePolicy) {

    private val resolver = LicenseResolver(policy)
    private val permittedShipped = policy.permittedShipped
    private val permittedBuildTimeOnly = policy.permittedBuildTimeOnly
    private val licenseRefs = policy.licenseRefs
    private val reviewRequired = policy.reviewRequired

    /** True when [entry]'s scoping covers [component] (empty scope = all). */
    private fun appliesTo(entry: ReviewRequiredEntry, component: ComponentLicense): Boolean {
        if (entry.components.isEmpty()) return true
        val qualified = component.component
        val plain = component.component.substringAfter(':', component.component)
        return entry.components.any { it == qualified || it == plain }
    }

    fun evaluate(components: List<ComponentLicense>): GateResult {
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        components.forEach { component ->
            when (val resolution = resolver.resolve(component)) {
                is LicenseResolution.Unresolved ->
                    violations += "${component.component}: ${resolution.reason}"

                is LicenseResolution.NeedsElection ->
                    violations += "${component.component}: license '${resolution.declared}' " +
                        "offers alternatives (${resolution.offered.joinToString(", ")}) and no " +
                        "election is recorded in licenses/license-policy.json"

                is LicenseResolution.Resolved -> {
                    val permitted = when (component.scope) {
                        Scope.SHIPPED -> permittedShipped
                        Scope.BUILD_TIME_ONLY -> permittedBuildTimeOnly
                    }
                    resolution.ids.forEach { id ->
                        if (id.startsWith(LICENSE_REF_PREFIX) && !licenseRefs.containsKey(id)) {
                            violations += "${component.component}: identifier '$id' is not declared " +
                                "in licenses/license-policy.json licenseRefs"
                        } else if (id !in permitted) {
                            violations += "${component.component}: license '$id' is not permitted " +
                                "for scope '${component.scope.id}'"
                        }
                        if (reviewRequired.any { it.license == id && appliesTo(it, component) }) {
                            warnings += "${component.component}: license '$id' needs the " +
                                "application license decision before distribution"
                        }
                    }
                }
            }

            component.caveat?.let { warnings += "${component.component}: $it" }
        }

        return GateResult(violations, warnings)
    }

    companion object {
        const val LICENSE_REF_PREFIX = "LicenseRef-"
    }
}
