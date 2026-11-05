package com.naviveylin.core.details

import com.framstag.libosmscout.client.DescriptionEntry

/**
 * Kind of target a contact description value resolves to (spec:
 * enhanced-details-sheet — Contact values are actionable).
 */
enum class LinkKind { WEB, PHONE }

/**
 * One actionable part of a contact description entry.
 *
 * [display] is the tag's own text, verbatim — it is what the row renders.
 * [target] is what the intent carries (an `http(s)` URL for [LinkKind.WEB], a
 * `tel:` URI for [LinkKind.PHONE]). The shared module owns no wording
 * (spec: `i18n-l10n`), so no label or hint string lives here.
 */
data class ContactLink(val kind: LinkKind, val display: String, val target: String)

/**
 * Classifies the two contact description entries the native description service
 * emits — `Contact/Phone` and `Contact/Website` (`ContactDescriptionProcessor`,
 * `LABEL_KEY_CONTACT_PHONE` / `LABEL_KEY_CONTACT_WEBSIZE`; the value only ever
 * crosses the bridge, the constant name stays native).
 *
 * Pure string work on purpose: the rules are unit-testable without Robolectric
 * (design D1).
 *
 * - A value carrying several `;`-separated parts is split, each part resolved
 *   independently (design D7 — OSM's `website`/`phone` tags are multi-valued).
 * - Website targets are an allow-list: `http`/`https`, with `https://` prefixed
 *   to a bare host; every other scheme is refused (design D2 — the values are
 *   OSM tag data, i.e. untrusted input).
 * - Phone targets are `tel:` plus the value's digits and one leading `+`; a
 *   value under three digits is refused (design D3).
 */
object ContactLinks {

    /** Description section the two label keys belong to. */
    const val SECTION_CONTACT = "Contact"

    /** Label key of the phone entry (`LABEL_KEY_CONTACT_PHONE`'s value). */
    const val LABEL_PHONE = "Phone"

    /** Label key of the website entry (`LABEL_KEY_CONTACT_WEBSIZE`'s value). */
    const val LABEL_WEBSITE = "Website"

    /** A phone value below this many digits is not dialable. */
    private const val MIN_PHONE_DIGITS = 3

    /** `scheme:` prefix — any value that carries one is judged by scheme only. */
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** The two schemes a website value may carry. */
    private val WEB_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    /**
     * Resolves one description entry to its targets, in the entry's own order.
     * Empty for every entry outside the `Contact` section and for every label
     * that is neither [LABEL_PHONE] nor [LABEL_WEBSITE].
     */
    fun resolve(entry: DescriptionEntry): List<ContactLink> {
        if (entry.sectionKey?.trim() != SECTION_CONTACT) return emptyList()
        val kind = when (entry.labelKey?.trim()) {
            LABEL_PHONE -> LinkKind.PHONE
            LABEL_WEBSITE -> LinkKind.WEB
            else -> return emptyList()
        }
        return parts(entry.value).mapNotNull { part -> resolvePart(kind, part) }
    }

    /**
     * The `;`-separated parts of a contact value, trimmed and without blanks —
     * the split the row renders on and the classifier resolves on, so a part
     * the classifier refuses is shown as text rather than dropped (design D7).
     */
    fun parts(value: String?): List<String> =
        value.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun resolvePart(kind: LinkKind, part: String): ContactLink? {
        val target = when (kind) {
            LinkKind.WEB -> webTarget(part)
            LinkKind.PHONE -> phoneTarget(part)
        } ?: return null
        return ContactLink(kind, part, target)
    }

    /**
     * `http`/`https` only, else a bare host gets `https://`. Refused: a
     * non-web scheme (`javascript:`, `file:`, `data:`, `intent:`, `content:`),
     * a value with whitespace, and anything that is not host-like.
     */
    private fun webTarget(part: String): String? {
        if (part.isEmpty() || part.any { it.isWhitespace() }) return null
        if (SCHEME_PREFIX.containsMatchIn(part)) {
            return part.takeIf { WEB_SCHEME.containsMatchIn(it) }
        }
        val host = part.substringBefore('/')
        if (host.isEmpty() || !host.contains('.') || host.any { it == '@' || it == ':' }) return null
        return "https://$part"
    }

    /** `tel:` + digits and one leading `+`; refused below [MIN_PHONE_DIGITS]. */
    private fun phoneTarget(part: String): String? {
        val digits = part.filter { it.isDigit() }
        if (digits.length < MIN_PHONE_DIGITS) return null
        val prefix = if (part.trimStart().startsWith("+")) "+" else ""
        return "tel:$prefix$digits"
    }
}
