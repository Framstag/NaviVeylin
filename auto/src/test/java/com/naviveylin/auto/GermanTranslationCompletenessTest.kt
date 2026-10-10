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

    private fun keys(xmlFile: File, tag: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName(tag)
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
        val missing = keys(values, "string") - keys(valuesDe, "string")
        assertTrue("Missing German string translations: $missing", missing.isEmpty())
    }

    /**
     * The plural resources are a second file per locale, so a count added to `values/plurals.xml`
     * would otherwise reach a German driver in English (spec: i18n-l10n, German is fully supported).
     */
    @Test
    fun everyEnglishPluralHasGermanTranslation() {
        val values = File("src/main/res/values/plurals.xml")
        val valuesDe = File("src/main/res/values-de/plurals.xml")
        assertTrue("values/plurals.xml missing", values.exists())
        assertTrue("values-de/plurals.xml missing", valuesDe.exists())
        val missing = keys(values, "plurals") - keys(valuesDe, "plurals")
        assertTrue("Missing German plural translations: $missing", missing.isEmpty())
    }
}
