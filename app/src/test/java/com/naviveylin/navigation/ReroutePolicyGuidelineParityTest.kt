package com.naviveylin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds `guidelines/UI.md`'s phone/AA reroute-parity bullet to the policy the single navigation
 * engine applies (spec: `reroute-trigger` — Fast first reroute trigger, Distance-based fast path,
 * Post-reroute cooldown, Noise guards on every surface).
 *
 * The bullet is the one place the guideline writes the policy's numbers down, and it kept the
 * pre-`2026-10-12-tune-reroute-trigger-latency` pair (50 m / 10 s) after that change lowered the
 * constants to [NavigationEngine.FAST_PATH_DISTANCE_M] and
 * [NavigationEngine.MIN_OFF_ROUTE_DURATION_MS] — nothing compared the two artefacts, because the
 * gate's own cases build the gate from the constants and the sibling specs state the new numbers.
 *
 * The numbers are read **from the code**, the way `FavAutoZoomClampRangeTest` reads a clamp range
 * from the fit and compares it to the text that names it, so no policy number is written down twice
 * and a move of either artefact fails here. The guideline file is read relative to the module
 * directory, which is an `:app` unit test's working directory.
 *
 * Each number is matched by the label that follows it rather than by position: the same bullet
 * deliberately keeps the car's retired `25 m / 15 s / 10 s`-interval path as the history of the
 * parity rule, so the bullet carries numbers that are not policy.
 */
class ReroutePolicyGuidelineParityTest {

    private val guidelineFile = File("../guidelines/UI.md")

    @Test
    fun `the reroute parity bullet states the policy the engine applies`() {
        val bullet = statedReroutePolicyBullet()
        val stated = mapOf(
            "fast path" to statedQuantity(bullet, "m", "fast path"),
            "confirmation floor" to statedQuantity(bullet, "s", "confirmation"),
            "cooldown" to statedQuantity(bullet, "s", "cooldown"),
            "accuracy guard" to statedQuantity(bullet, "m", "accuracy guard"),
            "tunnel guard" to statedQuantity(bullet, "s", "tunnel guard")
        )
        val engine = mapOf(
            "fast path" to NavigationEngine.FAST_PATH_DISTANCE_M,
            "confirmation floor" to NavigationEngine.MIN_OFF_ROUTE_DURATION_MS / 1000.0,
            "cooldown" to NavigationEngine.REROUTE_COOLDOWN_MS / 1000.0,
            "accuracy guard" to NavigationEngine.MAX_REROUTE_ACCURACY,
            "tunnel guard" to NavigationEngine.TUNNEL_REROUTE_GUARD_MS / 1000.0
        )
        println("reroutePolicy guideline=$stated engine=$engine")

        assertEquals(
            "the bullet's fast path must be the engine's FAST_PATH_DISTANCE_M",
            engine.getValue("fast path"), stated.getValue("fast path"), 0.0
        )
        assertEquals(
            "the bullet's confirmation floor must be the engine's MIN_OFF_ROUTE_DURATION_MS",
            engine.getValue("confirmation floor"), stated.getValue("confirmation floor"), 0.0
        )
        assertEquals(
            "the bullet's cooldown must be the engine's REROUTE_COOLDOWN_MS",
            engine.getValue("cooldown"), stated.getValue("cooldown"), 0.0
        )
        assertEquals(
            "the bullet's accuracy guard must be the engine's MAX_REROUTE_ACCURACY",
            engine.getValue("accuracy guard"), stated.getValue("accuracy guard"), 0.0
        )
        assertEquals(
            "the bullet's tunnel guard must be the engine's TUNNEL_REROUTE_GUARD_MS",
            engine.getValue("tunnel guard"), stated.getValue("tunnel guard"), 0.0
        )
    }

    /**
     * The bullet's own text: its marker line and the continuation lines under it, up to the next
     * bullet of the same list. The marker is unique in the file — the parity rule has one home in
     * this guideline.
     */
    private fun statedReroutePolicyBullet(): String {
        assertTrue("the guideline must exist at ${guidelineFile.absolutePath}", guidelineFile.isFile)
        val marker = "- Phone/AA reroute parity"
        val text = guidelineFile.readText()
        val start = text.indexOf(marker)
        assertTrue(
            "guidelines/UI.md must carry the bullet \"$marker\": ${guidelineFile.absolutePath}",
            start >= 0
        )
        return text.substring(start).lineSequence()
            .drop(1)
            .takeWhile { !it.startsWith("- ") }
            .joinToString("\n")
    }

    /**
     * The quantity the bullet states before [label], in [unit]. A number that is not there is a
     * planning defect — the bullet must state the policy it is the record of — so the assertion
     * names the label and the bullet rather than comparing against a default.
     */
    private fun statedQuantity(bullet: String, unit: String, label: String): Double {
        val match = Regex("([0-9]+(?:\\.[0-9]+)?) $unit $label\\b").find(bullet)
        assertNotNull("the bullet must state the $label in $unit: \"$bullet\"", match)
        return match!!.groupValues[1].toDouble()
    }
}
