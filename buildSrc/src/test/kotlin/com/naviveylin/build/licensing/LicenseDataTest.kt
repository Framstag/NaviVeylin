package com.naviveylin.build.licensing

import groovy.json.JsonSlurper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LicenseDataTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var textsDir: File
    private lateinit var vcpkgRoot: File

    private fun writePolicy(
        permittedShipped: List<String> = listOf("MIT", "Apache-2.0", "LicenseRef-AndroidSDK"),
        textSources: String = """{ "MIT": { "kind": "file", "path": "MIT.txt" } }""",
    ): File {
        val policy = temp.newFile("license-policy.json")
        policy.writeText(
            """
            {
              "version": 1,
              "permitted": {
                "shipped": [${permittedShipped.joinToString(", ") { "\"$it\"" }}],
                "buildTimeOnly": ["MIT", "BSD-3-Clause"]
              },
              "licenseRefs": {
                "LicenseRef-AndroidSDK": {
                  "name": "Android Software Development Kit License",
                  "source": "https://developer.android.com/studio/terms.html",
                  "textDistributed": false
                }
              },
              "textSources": $textSources,
              "noticeRequired": ["MIT", "Apache-2.0"]
            }
            """.trimIndent()
        )
        return policy
    }

    /** A component as the SBOM carries it after license enrichment. */
    private fun component(
        name: String,
        licenseEntry: String,
        scope: String,
        noticeRequired: Boolean = false,
        group: String? = null,
        note: String? = null,
    ): String {
        val groupField = group?.let { "\"group\": \"$it\"," } ?: ""
        val noteField = note?.let { "{ \"name\": \"naviveylin:license:caveat\", \"value\": \"$it\" }," } ?: ""
        return """
            {
              $groupField
              "name": "$name",
              "version": "1.0",
              "licenses": [ $licenseEntry ],
              "properties": [
                { "name": "naviveylin:license:scope", "value": "$scope" },
                { "name": "naviveylin:license:notice", "value": "${if (noticeRequired) "required" else "not-required"}" },
                $noteField
                { "name": "naviveylin:license:scope-evidence", "value": "test" }
              ]
            }
        """.trimIndent()
    }

    private fun writeBom(vararg components: String): File {
        val bom = temp.newFile("bom.json")
        val joined = components.joinToString(",\n")
        bom.writeText(
            """
            {
              "specVersion": "1.7",
              "metadata": { "component": { "name": "NaviVeylin", "version": "2026-09-13-1" } },
              "components": [ $joined ]
            }
            """.trimIndent()
        )
        return bom
    }

    private fun setUpDirs() {
        textsDir = temp.newFolder("texts")
        File(textsDir, "MIT.txt").writeText("MIT license text")
        vcpkgRoot = temp.newFolder("vcpkg")
    }

    @Test
    fun `writes inventory, deduplicated texts and link-only entries`() {
        setUpDirs()
        val policyFile = writePolicy()
        val bom = writeBom(
            component("expat", """{ "license": { "id": "MIT" } }""", "shipped", noticeRequired = true),
            component("brotli", """{ "license": { "id": "MIT" } }""", "shipped", noticeRequired = true),
            // As the enriched SBOM carries it: a policy-declared LicenseRef
            // identifier, with the display name and link coming from the policy.
            component(
                "com.google.android.gms:play-services-location",
                """{ "expression": "LicenseRef-AndroidSDK" }""",
                "shipped"
            ),
            component("protobuf", """{ "license": { "id": "BSD-3-Clause" } }""", "buildTimeOnly"),
            component("NaviVeylin", """{ "license": { "id": "Apache-2.0" } }""", "shipped")
        )

        val problems = LicenseData.writeAssets(
            bomFile = bom,
            outputDir = temp.newFolder("out"),
            policy = LicenseData.policy(policyFile),
            textSources = LicenseData.textSources(policyFile),
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkgRoot,
            triplets = listOf("arm64-android"),
            ndkNotice = null
        )

        // Apache-2.0 has no declared text source and is distributed → a problem.
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("NaviVeylin"))
        assertTrue(problems.single().contains("Apache-2.0"))

        val json = JsonSlurper().parse(File(temp.root, "out/dependencies.json")) as Map<*, *>
        assertEquals("2026-09-13-1", json["appVersion"])

        @Suppress("UNCHECKED_CAST")
        val components = json["components"] as List<Map<String, Any?>>
        assertEquals(5, components.size)

        // Distributed first, then by name.
        assertEquals(
            listOf("NaviVeylin", "brotli", "com.google.android.gms:play-services-location", "expat", "protobuf"),
            components.map { it["name"] }
        )

        // MIT is used by two shipped components and shipped once.
        @Suppress("UNCHECKED_CAST")
        val texts = json["texts"] as List<Map<String, Any?>>
        assertEquals(listOf("MIT"), texts.map { it["identifier"] })
        assertEquals("licenses/texts/MIT.txt", texts.single()["source"])
        assertEquals(
            "MIT license text",
            File(temp.root, "out/texts/MIT.txt").readText()
        )

        val expat = components.first { it["name"] == "expat" }
        assertEquals("MIT.txt", expat["textFile"])
        assertEquals("shipped", expat["scope"])
        assertEquals(true, expat["noticeRequired"])

        // A LicenseRef license is link-only: the terms are not redistributed.
        val playServices = components.first { it["name"] == "com.google.android.gms:play-services-location" }
        assertNull(playServices["textFile"])
        assertEquals("LicenseRef-AndroidSDK", playServices["identifier"])
        assertEquals("Android Software Development Kit License", playServices["licenseName"])
        assertEquals("https://developer.android.com/studio/terms.html", playServices["licenseUrl"])

        // Build-time-only components carry no text: their license is not shipped.
        val protobuf = components.first { it["name"] == "protobuf" }
        assertEquals("buildTimeOnly", protobuf["scope"])
        assertNull(protobuf["textFile"])

        @Suppress("UNCHECKED_CAST")
        val links = json["links"] as List<Map<String, Any?>>
        assertEquals("LicenseRef-AndroidSDK", links.single()["identifier"])
    }

    @Test
    fun `text reference of every row resolves to a written text or a link`() {
        setUpDirs()
        val policyFile = writePolicy(permittedShipped = listOf("MIT", "LicenseRef-AndroidSDK"))
        val bom = writeBom(
            component("expat", """{ "license": { "id": "MIT" } }""", "shipped", noticeRequired = true),
            component("x", """{ "license": { "id": "LicenseRef-AndroidSDK" } }""", "shipped")
        )
        LicenseData.writeAssets(
            bomFile = bom,
            outputDir = temp.newFolder("out2"),
            policy = LicenseData.policy(policyFile),
            textSources = LicenseData.textSources(policyFile),
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkgRoot,
            triplets = listOf("arm64-android"),
            ndkNotice = null
        )

        val json = JsonSlurper().parse(File(temp.root, "out2/dependencies.json")) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val components = json["components"] as List<Map<String, Any?>>
        components.forEach { row ->
            val textFile = row["textFile"] as? String
            if (textFile == null) {
                assertTrue("row without text must be link-only: $row", row["licenseUrl"] != null)
            } else {
                assertTrue(
                    "text file must exist: $textFile",
                    File(temp.root, "out2/texts/$textFile").isFile
                )
            }
        }
    }

    @Test
    fun `caveat becomes the row note`() {
        setUpDirs()
        val policyFile = writePolicy(permittedShipped = listOf("MIT"))
        val bom = writeBom(
            component(
                "libosmscout",
                """{ "license": { "id": "MIT" } }""",
                "shipped",
                noticeRequired = true,
                note = "README says LGPL, LICENSE file carries GPLv2 text"
            )
        )
        LicenseData.writeAssets(
            bomFile = bom,
            outputDir = temp.newFolder("out3"),
            policy = LicenseData.policy(policyFile),
            textSources = LicenseData.textSources(policyFile),
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkgRoot,
            triplets = listOf("arm64-android"),
            ndkNotice = null
        )
        val json = JsonSlurper().parse(File(temp.root, "out3/dependencies.json")) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val components = json["components"] as List<Map<String, Any?>>
        assertEquals(
            "README says LGPL, LICENSE file carries GPLv2 text",
            components.single()["note"]
        )
    }

    @Test
    fun `vcpkg port text source reads the port copyright`() {
        setUpDirs()
        val port = File(vcpkgRoot, "installed/arm64-android/share/expat")
        port.mkdirs()
        File(port, "copyright").writeText("expat copyright text")
        val policyFile = writePolicy(
            permittedShipped = listOf("MIT"),
            textSources = """{ "MIT": { "kind": "vcpkgPort", "port": "expat" } }"""
        )
        val bom = writeBom(component("expat", """{ "license": { "id": "MIT" } }""", "shipped", noticeRequired = true))

        val resolved = LicenseData.licenseText(
            identifier = "MIT",
            textSources = LicenseData.textSources(policyFile),
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkgRoot,
            triplets = listOf("arm64-android"),
            ndkNotice = null
        )
        assertEquals("expat copyright text", resolved?.text)
        assertEquals("vcpkg expat:arm64-android copyright", resolved?.source)
    }

    @Test
    fun `notice lists notice-requiring components and their texts`() {
        setUpDirs()
        val policyFile = writePolicy(permittedShipped = listOf("MIT"), textSources = """{ "MIT": { "kind": "file", "path": "MIT.txt" } }""")
        val bom = writeBom(
            component("expat", """{ "license": { "id": "MIT" } }""", "shipped", noticeRequired = true, group = "org.expat"),
            component("protobuf", """{ "license": { "id": "MIT" } }""", "buildTimeOnly")
        )
        val notice = LicenseData.notice(
            bomFile = bom,
            policy = LicenseData.policy(policyFile),
            textSources = LicenseData.textSources(policyFile),
            vendoredTexts = textsDir,
            vcpkgRoot = vcpkgRoot,
            triplets = listOf("arm64-android"),
            ndkNotice = null
        )
        assertTrue(notice.contains("org.expat:expat — MIT (distributed)"))
        assertTrue(notice.contains("MIT license text"))
        assertTrue(notice.contains("source: licenses/texts/MIT.txt"))
        // A build-time-only component needs no notice.
        assertTrue(!notice.contains("protobuf"))
    }
}
