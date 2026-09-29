package com.naviveylin.core

/**
 * Recognises a diagnostics line that carries a device position, so the retention pass can remove it
 * (spec: auto-diagnostics — Coordinate-carrying entries do not survive the retention pass; `TODO.md`
 * §88). The rule exists because the coordinate-free rule for *new* writes (the `checkNoCoordinatesInLogs`
 * build gate) cannot reach what an earlier build already wrote: those entries stay in the file for the
 * whole 7-day window, which is the window the exported log and its disclosure call coordinate-free.
 *
 * Two shapes are caught, and both halves are deliberately narrow so the precision-free identity a
 * diagnostic line carries today — magnification, screen pixel, map database or map file name, accuracy,
 * bearing, object label/id — is never mistaken for a position:
 *
 * 1. a delimited coordinate field name (`lat`, `lon`, `lng`, `latitude`, `longitude`, case-insensitive)
 *    together with a number at coordinate precision, i.e. `\d{1,3}\.\d{4,}` — the shape the phone-side
 *    entries used (`LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0`);
 * 2. an unnamed pair of such numbers separated by a comma — the shape the pre-redaction car render
 *    entry used (`render center=51.60987926464756,7.621644390462239 mag=17.0`).
 *
 * Accepted limitations (see the change's `design.md` D3 and its risk list):
 *
 * - The pair half requires the comma, so a legitimate line carrying two high-precision numbers without
 *   a comma between them is kept, and a legitimate line carrying such a pair *with* a comma is dropped.
 *   Nothing the app logs today does either — the current build's file scores 0 hits against this shape
 *   (`TODO.md` §88's recipe) — and every coordinate drop is counted in the retention report, so an
 *   unexpected drop is visible in the log.
 * - The predicate is intentionally narrower than the recipe's bare `[0-9]{1,3}\.[0-9]{4,}` grep: a raw
 *   `Double` magnification can print that shape without carrying any position, and dropping such a line
 *   would cost diagnostics evidence for nothing.
 * - A position written without a coordinate field name and with fewer than four fraction digits is not
 *   recognised; the age bound still removes it, and new writes are the build gate's concern.
 */

internal object LogLineCoordinates {

    /** A number at coordinate precision — the shape the `TODO.md` §88 recipe greps for. */
    private val PRECISE_NUMBER = Regex("""\d{1,3}\.\d{4,}""")

    /**
     * A coordinate field name, delimited so a longer identifier or a prose lookalike is not a match
     * (`latitude_marker`, `relations`, `belongs`, `coordinates`).
     */
    private val COORDINATE_FIELD =
        Regex("""(?i)(?<![A-Za-z0-9_])(?:lat|lon|lng|latitude|longitude)(?![A-Za-z0-9_])""")

    /** Two coordinate-precision numbers written as one pair, e.g. `51.60987926464756,7.621644390462239`. */
    private val COORDINATE_PAIR = Regex("""-?\d{1,3}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,}""")

    /**
     * True when [line] carries a device position in one of the two recognised shapes. Pure and
     * allocation-light: it is called once per line by the retention pass on the logging worker.
     */
    fun carriesPosition(line: String): Boolean {
        if (COORDINATE_PAIR.containsMatchIn(line)) return true
        return COORDINATE_FIELD.containsMatchIn(line) && PRECISE_NUMBER.containsMatchIn(line)
    }
}
