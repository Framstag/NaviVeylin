package com.naviveylin.ui.map

import com.naviveylin.core.FollowPrediction
import com.naviveylin.core.OverrunWindow
import java.util.Locale

/**
 * Formats one number of a log or diagnostic line.
 *
 * A number in a log line is evidence, not display text, so it must not follow the device locale:
 * with the default-locale formatter a German device printed `off=3,4,-1,5` and `acc=5,0`, where the
 * decimal comma reads as a field separator in the very grep the on-device recipes use
 * (`guidelines/Regulatory.md` §9, `guidelines/Build.md` §10). Spec `i18n-l10n` — Log and diagnostic
 * numbers are locale-independent (issue #44).
 *
 * The fixed locale is [Locale.US]: it is the constant `:app` and `:core` already use for
 * machine-parseable text (`DiagnosticsLog`'s timestamp pair, `CoordinateFormat`), and it is not the
 * constant the same spec forbids for numeric formatting (`Locale.ROOT`). A reader also meets a number
 * this way in the diagnostics view: the re-center site's line is file-backed as well as logged
 * ([MapCanvasViewModel.logBrowseReCenter]). The Android Auto `follow` entry uses `Locale.ROOT` for the
 * same rule (`auto-diagnostics`); on these patterns the two constants print identical bytes.
 */
internal fun diagnosticNumber(format: String, value: Double): String =
    String.format(Locale.US, format, value)

/**
 * The phone `follow` line — the logcat sibling of the car's `follow` diagnostic entry, with this
 * branch's own field set (`t spd brg avg gps eff savg dec stp off clamped`).
 *
 * Built as one function rather than inline in the frame loop so the line's format is assertable
 * without mounting the screen: no test mounts `MapCanvasScreen`, and the line's numbers are the
 * evidence a follow defect is read from (issue #44).
 *
 * `avg`, `eff` and `savg` carry no `-` placeholder, unlike `spd`, `brg` and `gps`: an unknown value
 * there prints as the formatter renders it (`NaN`). That is the line's recorded behaviour and not this
 * function's subject.
 */
internal fun followDiagnosticLine(
    fixTime: Long,
    speedKmH: Double,
    smoothedBearing: Double,
    dbg: FollowPrediction.DebugState,
    offset: OverrunWindow.DisplayOffset
): String =
    "follow t=" + fixTime +
        " spd=" + (if (speedKmH.isNaN()) "-" else diagnosticNumber("%.1f", speedKmH)) +
        " brg=" + (if (smoothedBearing.isNaN()) "-" else diagnosticNumber("%.0f", smoothedBearing)) +
        " avg=" + diagnosticNumber("%.1f", dbg.avgSpeedMs * 3.6) +
        " gps=" + (if (dbg.gpsSpeedMs.isNaN()) "-" else diagnosticNumber("%.1f", dbg.gpsSpeedMs * 3.6)) +
        " eff=" + diagnosticNumber("%.1f", dbg.effectiveSpeedMs * 3.6) +
        " savg=" + diagnosticNumber("%.1f", dbg.smoothAvgMs * 3.6) +
        " dec=" + dbg.decelerating +
        " stp=" + dbg.stopped +
        " off=" + diagnosticNumber("%.1f", offset.clampedX) +
        "," + diagnosticNumber("%.1f", offset.clampedY) +
        " clamped=" + offset.clamped
