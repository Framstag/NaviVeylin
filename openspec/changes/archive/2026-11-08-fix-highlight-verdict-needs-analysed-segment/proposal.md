# A highlight verdict needs the same-moment analysed-segment line

## Why

`tools/measure-highlight.py` finds the analysed-segment highlight by its casing colour and prints one of three
verdicts — `inside` (exit 0), `CLIPPED` (exit 1) or no highlight (exit 2) (`tools/measure-highlight.py:31-34`).
Nothing in it establishes that an analysed segment was on screen at the moment the frame was captured, so a
casing-coloured coincidence on a frame that shows **no route at all** is reported as a measurement of one.

**Root cause, in one sentence:** the detector's row test (`find_casing`, `tools/measure-highlight.py:68-111`) is a
test of colour and run length alone, so any casing-coloured glyph that happens to form a `MIN_RUN_PX` run
(`:62-65`) qualifies as a highlight row — and the tool then prints a verdict for a frame whose state it never
checked.

**The red case, reproduced device-free on the base `b9ff0171`** (2026-11-08T11:34:11Z, `scratch/repro-147.sh` of
issue #90): the observed shape drawn in the dark casing colour `#E0F7FA` on a 1080×2400 canvas, two rows 321 px
apart, 18 px in total —

```
highlight (dark): bbox=[619, 628, 413, 734] px=18
canvas=1080x2400 band=[0,2400] margin=126
verdict: inside
   exit=0
```

— the issue's `px=18` / `verdict: inside` / exit 0 reproduced, with `band=[0,2400]` as the printed tell: the
frame is not in the state the detector assumes. The same frame with a **measured** card
(`--band-bottom 2164`) still prints `px=18` / `band=[0,2164]` / `verdict: inside`, exit 0, so a band-only
precondition does not decide the state either (`scratch/repro-147-HEAD.log`).

Two facts of the tree make the precondition available rather than invented:

- `MapCanvasViewModel.logSegmentFit` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelRoute.kt:432`,
  the line at `:463-468`) logs `MapCanvasVM: segment focus: range=… band=[0,…] margin=… bboxPx=[…] inside=…`,
  and it is reached only from `focusCameraOnSegment` (`:400`) on a **non-null** analysed anchor
  (`:157-165`) — so the line's presence is the same-moment statement that an analysed segment was on screen,
  and it carries the band the app's own card measured.
- The owning guideline already routes on both halves: `guidelines/BuildEvidence.md:289-291` (section 11 rule 1) names
  that exact line as the diagnostic, and section 11 rule 2, at the base `:297-300`, stated "Screenshot and dump must
  come from the same moment. A positive verdict also needs its precondition: `band` must end above the canvas
  bottom and the match must be a stroke" — the sentence this change turns into the detector's refusal.

**The recorded decision, applied here:** "D — the same-moment precondition, with the detector refusing instead
of reporting a verdict it cannot establish" (issue #90, 2026-11-08T08:41:03Z). The detector therefore takes the
same-moment diagnostics as an input, and refuses — it does not print a verdict — when they do not establish an
analysed segment on screen.

**Round-2 review correction:** the first implementation matched any diagnostics line containing
`segment focus:`, so a line under another tag could establish the state. The wrong-tag red case reports
`"inside": true`, exit 0 before the correction (`evidence/red-on-round2.log`,
`evidence/red-on-round2.xml`: tests=12, failures=1, 2026-11-08T12:02:36Z). The detector now requires the literal
`MapCanvasVM: segment focus:` line and refuses that input as `no-focus-line`.

**Owning requirement:** `openspec/specs/highlight-measurement/spec.md` — one requirement added ("A verdict
requires the same-moment frame state"); the six scenarios that state the row rule are unchanged.

**Owning guideline section:** `guidelines/BuildEvidence.md` section 11 "Measuring a phone UI finding", rule 2
(`:292-300`) — the paragraph that puts the precondition on the caller; it now states that the detector refuses
instead, and the recipe in `guidelines/Build.md` §1 (`:55`) and the `pixel-check` skill carry the input.

surface: 12 harness cases in total, 5 refusal cases added, 3 skill-harness checks gain the state input,
1 requirement added with 5 scenarios, 1 detector, 2 guideline sections and 1 skill section realigned — counted
with `bash tools/measure-highlight-selftest.sh` and `tools/look.sh 'measure-highlight'`.

## What Changes

1. **`tools/measure-highlight.py` takes the same-moment diagnostics and refuses without them.** A new required
   `--state FILE` (the logcat capture of the same moment, as `adb logcat -d` writes it) must carry a
   `MapCanvasVM: segment focus:` line; the band in that line must end above the canvas bottom and must equal the
   band the detector measures against. Any other answer prints `verdict: REFUSED (<reason>)` with
   `"highlight": null` and `"refused": "<reason>"` in `--json`, and exits 2. The exit-code contract stays at
   three values: a refusal is the tool's existing "no highlight was found" answer, and now also its answer when
   the frame's state is not established.
2. **The two device-free harnesses state the input instead of describing it.** `tools/measure-highlight-selftest.sh`
   writes a `MapCanvasVM: segment focus:` fixture per case and adds five refusal cases — `no-state`,
   `no-focus-line`, `wrong-tag`, `no-card`, `band-disagrees`; `.pi/skills/pixel-check/selftest.sh` gains the same
   fixture for its three checks.
3. **The recipes that call the tool pass the state**, so a documented command cannot come back refused:
   `.pi/skills/pixel-check/SKILL.md` (`:80`), `guidelines/Build.md` §1 (`:55`) and
   `guidelines/BuildEvidence.md` section 11 rule 2.

## What this does not do

- It does not add an extent floor, and it does not raise `MIN_RUN_PX` (the capability spec forbids raising it):
  a glyph coincidence on a frame that **does** carry an analysed-segment line still passes the row test. That is
  the operating point the recorded decision left open — the smallest real mark measures 45 px over 5 rows and
  the glyph/label population is unmeasured — and it is named in `design.md` rather than guessed here.
- It does not add a fourth exit code or a fourth verdict word to the contract: a refusal is exit 2, the value
  whose own docstring already reads "no highlight was found (e.g. no step analysed)".
- It does not change the row rule, the casing colours, the tolerance, the band arithmetic or the bbox the tool
  reports.
