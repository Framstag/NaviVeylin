package com.naviveylin.architecture

/**
 * Every rule the check evaluates — the import rules of [LayerRules] and the declaration rules of
 * [DeclarationRules] — in one list, because three places count them and a second count would drift: the green
 * run reports the figure, and [Baseline.claims] honours an exemption only for a rule that is evaluated.
 */
object StructureRules {

    val ALL: List<StructureRule> = LayerRules.ALL + DeclarationRules.ALL
}