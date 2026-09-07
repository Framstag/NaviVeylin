package com.naviveylin.auto

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * German translation completeness for the :auto module (spec: German is fully
 * supported, phone/Auto label parity): every translatable string in the
 * default `values/` must exist in `values-de/`. Plain JUnit — Gradle runs unit
 * tests with the module dir as working directory.
 */
class GermanTranslationCompletenessTest {

    private fun stringKeys(xmlFile: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("translatable") == "false") continue
            keys += el.getAttribute("name")
        }
        return keys
    }

    @Test
    fun everyEnglishStringHasGermanTranslation() {
        val values = File("src/main/res/values/strings.xml")
        val valuesDe = File("src/main/res/values-de/strings.xml")
        assertTrue("values/strings.xml missing", values.exists())
        assertTrue("values-de/strings.xml missing", valuesDe.exists())
        val missing = stringKeys(values) - stringKeys(valuesDe)
        assertTrue("Missing German string translations: $missing", missing.isEmpty())
    }
}
