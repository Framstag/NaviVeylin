# The round-3 finding, and the correction it asked for

The run for issue #126 was refused in review round 3 (`review-failed-round-3.md`, 2026-10-19T20:57:44Z)
with one finding: **the requirement claimed coverage the scan does not have.**

The archived requirement said the scan treats as a text position *any* assignment whose field name ends in a
display word and *the message argument of a report call* — unbounded over lines. `HardcodedStringScanner.scan`
iterates `source.lineSequence()` and `lineFindings(line)` can only see the line it is handed, which
`guidelines/UI.md` §10 already states as the scan's limit ("The scan is line-based: a position and its literal
must share a line, so `Text(` with its sentence on the next line is still invisible"). The change's own
pre-fix witness is the counter-example: `NavigationSession.kt:673-675` at base `5d65687` is

```
navigationViewModel.reportError(
    "No matching location found for \"$query\"",
    SurfaceOrigin.CAR
```

a report call the widened scan cannot see — it is absent from `module-gates.txt`, and the change converted it
by hand. So the code, the gate, the revert-check and `guidelines/UI.md` were all right and the **spec was the
one carrier that did not state the bound**.

The correction is the bound itself, in the requirement, worded as `guidelines/UI.md` §10 words it:
*"The scan is line-based: a position and its literal SHALL share a line, so a sentence that begins on the next
line of an assignment or of a report call is outside the scan's coverage."* The delta here and the merged
`openspec/specs/i18n-l10n/spec.md` carry the identical text from the requirement heading to the end of the
file, and `guidelines/UI.md` §10 keeps the same rule for a reader of the guideline.

What the correction does not do: widen the scan to the next line. That is a behaviour change with its own
red case, and the spec now says what the scan does rather than what it was hoped to do. The `:auto` call the
change converted by hand stays converted.
