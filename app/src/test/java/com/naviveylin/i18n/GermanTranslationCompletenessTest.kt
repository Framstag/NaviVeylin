package com.naviveylin.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * German translation completeness (spec: German is fully supported): every
 * translatable string/plural key in the default `values/` must exist in
 * `values-de/`. Plain JUnit — Gradle runs unit tests with the module dir as
 * working directory, so the resource paths resolve relative to it.
 */
class GermanTranslationCompletenessTest {

    private fun keys(xmlFile: File, tag: String, translatableAttr: String?): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName(tag)
        val keys = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (translatableAttr != null && el.getAttribute(translatableAttr) == "false") continue
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
        val missing = keys(values, "string", "translatable") - keys(valuesDe, "string", null)
        assertTrue("Missing German string translations: $missing", missing.isEmpty())
    }

    @Test
    fun everyEnglishPluralHasGermanTranslation() {
        val values = File("src/main/res/values/plurals.xml")
        val valuesDe = File("src/main/res/values-de/plurals.xml")
        assertTrue("values/plurals.xml missing", values.exists())
        assertTrue("values-de/plurals.xml missing", valuesDe.exists())
        val missing = keys(values, "plurals", null) - keys(valuesDe, "plurals", null)
        assertTrue("Missing German plural translations: $missing", missing.isEmpty())
    }
}
