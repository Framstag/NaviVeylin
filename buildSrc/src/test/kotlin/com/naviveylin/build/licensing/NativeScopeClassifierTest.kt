package com.naviveylin.build.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeScopeClassifierTest {

    private val probes = mapOf(
        "cairo" to ComponentProbes(exact = listOf("cairo_create")),
        "expat" to ComponentProbes(),
        "marisa-trie" to ComponentProbes(prefix = listOf("_ZN6marisa"))
    )

    @Test
    fun `component packaged as its own shared object is shipped`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("libosmscout", "libomp"),
            probes = probes,
            packagedObjects = mapOf(
                "libosmscoutd" to setOf("osmscoutThing"),
                "libosmscout_map_cairod" to setOf("paint"),
                "libomp" to setOf("GOMP_alloc")
            ),
            archivesByComponent = emptyMap(),
            linkedArchives = emptySet()
        )
        assertEquals(Scope.SHIPPED, result.getValue("libosmscout").scope)
        assertEquals("packaged as libosmscoutd.so", result.getValue("libosmscout").evidence)
        assertEquals(Scope.SHIPPED, result.getValue("libomp").scope)
        assertFalse(result.getValue("libosmscout").ambiguous)
    }

    @Test
    fun `component found by probe symbol is shipped`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("cairo"),
            probes = probes,
            packagedObjects = mapOf("libosmscout_map_cairod" to setOf("cairo_create", "cairo_paint")),
            archivesByComponent = emptyMap(),
            linkedArchives = emptySet()
        )
        assertEquals(Scope.SHIPPED, result.getValue("cairo").scope)
        assertTrue(result.getValue("cairo").evidence.contains("probe symbol"))
        assertFalse(result.getValue("cairo").ambiguous)
    }

    @Test
    fun `component found by prefix probe is shipped`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("marisa-trie"),
            probes = probes,
            packagedObjects = mapOf("libosmscoutd" to setOf("_ZN6marisa5TrieD1Ev")),
            archivesByComponent = emptyMap(),
            linkedArchives = emptySet()
        )
        assertEquals(Scope.SHIPPED, result.getValue("marisa-trie").scope)
    }

    @Test
    fun `component whose archive is linked but whose symbols are hidden is shipped and ambiguous`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("expat"),
            probes = probes,
            packagedObjects = mapOf("libosmscout_map_cairod" to setOf("cairo_create")),
            archivesByComponent = mapOf("expat" to setOf("libexpat.a")),
            linkedArchives = setOf("libexpat.a")
        )
        val evidence = result.getValue("expat")
        assertEquals(Scope.SHIPPED, evidence.scope)
        assertTrue(evidence.ambiguous)
        assertTrue(evidence.evidence.contains("libexpat.a"))
    }

    @Test
    fun `installed archive that no link line reaches is build time only`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("pango"),
            probes = emptyMap(),
            packagedObjects = mapOf("libosmscoutd" to setOf("osmscoutThing")),
            archivesByComponent = mapOf("pango" to setOf("libpango-1.0.a", "libpangocairo-1.0.a")),
            linkedArchives = setOf("libcairo.a")
        )
        assertEquals(Scope.BUILD_TIME_ONLY, result.getValue("pango").scope)
        assertFalse(result.getValue("pango").ambiguous)
    }

    @Test
    fun `component with nothing packaged is build time only`() {
        val result = NativeScopeClassifier.classify(
            components = listOf("protobuf"),
            probes = emptyMap(),
            packagedObjects = mapOf("libosmscoutd" to setOf("osmscoutThing")),
            archivesByComponent = mapOf("protobuf" to setOf("libprotobuf.a")),
            linkedArchives = emptySet()
        )
        assertEquals(Scope.BUILD_TIME_ONLY, result.getValue("protobuf").scope)
    }

    @Test
    fun `every component receives a scope`() {
        val components = listOf("cairo", "expat", "protobuf")
        val result = NativeScopeClassifier.classify(
            components = components,
            probes = probes,
            packagedObjects = emptyMap(),
            archivesByComponent = emptyMap(),
            linkedArchives = emptySet()
        )
        assertEquals(components.sorted(), result.keys.sorted())
        assertTrue(result.values.all { it.scope == Scope.BUILD_TIME_ONLY })
    }

    @Test
    fun `name matching covers debug and map library suffixes`() {
        assertTrue(NativeScopeClassifier.matches("libosmscout", "libosmscoutd"))
        assertTrue(NativeScopeClassifier.matches("libosmscout", "libosmscout_map_cairod"))
        assertTrue(NativeScopeClassifier.matches("libomp", "libomp"))
        assertTrue(NativeScopeClassifier.matches("libcairo", "libcairod"))
        assertFalse(NativeScopeClassifier.matches("cairo", "libosmscout_map_cairod"))
        assertFalse(NativeScopeClassifier.matches("libpng", "libpng16"))
    }
}
