package com.naviveylin.core.details

import com.framstag.libosmscout.client.DescriptionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure classifier tests (spec: enhanced-details-sheet — Contact values are
 * actionable). One case per scenario of the delta; the Compose cases in
 * `LocationDetailsDialogComposeTest` prove the same classifier reaches the
 * dialog's rows.
 *
 * No Robolectric: [ContactLinks] takes a plain `DescriptionEntry` and returns
 * plain data, so the rules are provable on a bare JVM.
 */
class ContactLinksTest {

    private fun entry(section: String, label: String, value: String): DescriptionEntry =
        DescriptionEntry().apply {
            sectionKey = section
            labelKey = label
            this.value = value
        }

    private fun contact(label: String, value: String): DescriptionEntry =
        entry("Contact", label, value)

    @Test
    fun aBareHostGetsHttpsAndIsLinked() {
        // Spec: a website value without a scheme is linked over https.
        val links = ContactLinks.resolve(contact("Website", "www.cafe.de"))

        assertEquals(1, links.size)
        assertEquals(LinkKind.WEB, links[0].kind)
        assertEquals("www.cafe.de", links[0].display)
        assertEquals("https://www.cafe.de", links[0].target)
    }

    @Test
    fun anHttpWebsiteValueKeepsItsOwnScheme() {
        assertEquals(
            "http://cafe.de",
            ContactLinks.resolve(contact("Website", "http://cafe.de")).single().target
        )
        assertEquals(
            "https://cafe.de/karte",
            ContactLinks.resolve(contact("Website", "https://cafe.de/karte")).single().target
        )
    }

    @Test
    fun aNonWebSchemeResolvesToNoTarget() {
        // Spec: a non-web scheme is refused. These strings are OSM tag data —
        // a hostile tag must not reach the resolver (design D2).
        for (value in listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,x",
            "intent://x",
            "content://x"
        )) {
            assertTrue(
                "$value must resolve to no target",
                ContactLinks.resolve(contact("Website", value)).isEmpty()
            )
        }
    }

    @Test
    fun aPhoneValueBelowThreeDigitsResolvesToNoTarget() {
        // Spec: a phone value too short to dial is refused.
        assertTrue(ContactLinks.resolve(contact("Phone", "12")).isEmpty())
        assertTrue(ContactLinks.resolve(contact("Phone", "")).isEmpty())
    }

    @Test
    fun aMultiValuedTagSplitsIntoOneTargetPerPart() {
        // Spec: a multi-valued tag splits into one target per part.
        val websites = ContactLinks.resolve(contact("Website", "https://a.de; https://b.de"))
        assertEquals(listOf("https://a.de", "https://b.de"), websites.map { it.target })
        assertTrue(websites.all { it.kind == LinkKind.WEB })

        val phones = ContactLinks.resolve(contact("Phone", "030 / 12 34 56; +49 30 7654321"))
        assertEquals(listOf("tel:030123456", "tel:+49307654321"), phones.map { it.target })
        assertTrue(phones.all { it.kind == LinkKind.PHONE })
    }

    @Test
    fun aPhoneValueIsNormalisedToDigitsAndOnePlus() {
        assertEquals(
            "tel:+49301234567",
            ContactLinks.resolve(contact("Phone", "+49 30 1234567")).single().target
        )
        assertEquals(
            "tel:0231123456",
            ContactLinks.resolve(contact("Phone", "(0231) 12 34 56")).single().target
        )
    }

    @Test
    fun aNonContactEntryYieldsNoTarget() {
        // The classifier's own guard: a URL in any other section is not a link.
        assertTrue(ContactLinks.resolve(entry("General", "Name", "https://cafe.de")).isEmpty())
        assertTrue(ContactLinks.resolve(entry("Location", "Website", "https://cafe.de")).isEmpty())
        // …and an unknown label inside Contact is not a link either.
        assertTrue(ContactLinks.resolve(contact("Email", "https://cafe.de")).isEmpty())
    }
}
