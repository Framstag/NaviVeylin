# Design — a verdict needs the same-moment analysed-segment line

## Root cause

`tools/measure-highlight.py` computes a verdict from the pixels of the shot it was handed and from nothing
else. Its row test (`find_casing`, `:68-111`) is colour plus run length, so a casing-coloured glyph that forms a
`MIN_RUN_PX` run (`:62-65`) qualifies a row, and `main` (`:204-227`) then prints `inside`/`clipped` for a frame
whose only claim to being "a frame with an analysed segment" is the caller's word. The issue's frame has no
route on screen: 18 px of parking-glyph colour over two rows pass, and the tool reports `inside`, exit 0
(`scratch/repro-147-HEAD.log`, 2026-11-08T11:34:11Z).

## The single fix

The detector takes the same-moment diagnostics as a required input and refuses to answer without them:

- **Input.** `--state FILE` — the logcat capture of the same moment the shot was taken (`adb logcat -d`).
- **The one thing it must establish.** A `MapCanvasVM: segment focus:` line, which the app prints only from
  `focusCameraOnSegment` (`MapCanvasViewModelRoute.kt:400`, call site `:432`) when an analysed anchor is
  published (`:157-165`), and which carries `band=[0,N]` — the app's own card top for that moment
  (`:460`, `:463-468`).
- **The check.** That band must end above the canvas bottom (`N < image height`, so a card was measured and is
  not the whole screen) and must equal the band the detector measures against (from `--dump` or
  `--band-bottom`, or the canvas height when neither is given). Equality is the same-moment test: the state
  line and the band cannot come from two different frames of the app.
- **The refusal.** `verdict: REFUSED (<reason>)` on the human line, `"highlight": null` and
  `"refused": "<reason>"` in `--json`, exit 2. Reasons, in the order they are decided: `no-state` (no
  `--state`, or the file cannot be read), `no-focus-line` (no `segment focus:` line carrying a `band=[0,N]`),
  `no-card` (the state's band ends at the canvas bottom), `band-disagrees` (the state's band is not the band
  the detector was given). The reasons are decided before the pixels are read, so a refused frame never prints
  a box.
- **Unchanged.** The row rule, `MIN_RUN_PX`, the casings, the tolerance, the bbox arithmetic, and the three
  exit values (0 inside, 1 clipped, 2 no highlight — a refusal is exit 2, whose docstring already reads
  "no highlight was found (e.g. no step analysed)").

## Blast radius

| Surface | What changes |
|---|---|
| `tools/measure-highlight.py` | `--state`, `segment_focus_band` (`:121`), `refused_reason` (`:134`), the refusal arm of `main` (`:216-227`), the docstring's usage and exit contract (`:31-34`) |
| `tools/measure-highlight-selftest.sh` | a `segment focus:` fixture per case, `--state` on the 7 existing cases, 4 refusal cases, a `glyph-pair.png` fixture shaped like issue #90's frame |
| `.pi/skills/pixel-check/selftest.sh` | the same fixture and `--state` on its 3 checks |
| `.pi/skills/pixel-check/SKILL.md` | the capture recipe gains the `adb logcat -d` line and the `--state` argument; the detector section states the refusal |
| `guidelines/Build.md` §1 (`:55`) | the recipe line gains `--state` |
| `guidelines/BuildEvidence.md` section 11 rule 2 (`:292-300`) | the precondition paragraph: the detector refuses it, the caller keeps the `px`/stroke sanity check |
| `openspec/specs/highlight-measurement/spec.md` | one requirement added with four scenarios |

Nothing else calls the tool: `tools/look.sh 'measure-highlight'` names the six live files above and their
archived records, and no script under `tools/` or `.pi/` invokes it besides the two harnesses.

## Rollback

The delta is one script, two harnesses and three documentation sites; reverting the hunks of
`tools/measure-highlight.py` restores the old behaviour exactly (the harness cases that pass `--state` then fail on
the unrecognised argument, which is the red run this change already records: `evidence/revert-check-base-tool.log`).
No production code, no data, no migration.

## The alternatives the evidence eliminated

- **Raising `MIN_RUN_PX`** (`:65`) is forbidden by the capability spec ("The run-length threshold SHALL be
  applied to that run and SHALL NOT be raised") and by the comment at `:58-64`: the threshold must stay low
  enough to see a short analysed leg.
- **An extent floor (total `px` / qualifying rows)** — the alternative the recorded decision priced and did not
  choose. It has no operating point in the tree: the sampled frames separate anywhere in 19-44 px, the smallest
  mark the overlay can draw measures 45 px over 5 rows (`SEGMENT_CASING_WIDTH_DP = 9f`,
  `RouteSegmentHighlightOverlay.kt:50`), and the glyph/label population is unmeasured — so a floor would be a
  guess about a population nobody has sampled.
- **A band-only precondition** (`band_bottom < canvas height`) was eliminated by measurement: the issue's own
  frame with a card measured at 2164 still prints `px=18` / `inside`, exit 0
  (`scratch/repro-147-HEAD.log`), because the overlay draws nothing when no step is analysed
  (`RouteSegmentHighlightOverlay.kt:77`) and so the card cannot speak for the segment.

## What this leaves open

A glyph coincidence on a frame that **does** carry an analysed-segment line still passes the row test and is
reported `inside`: the extent question above is not answered by this change. It is a note on the tracker, not a
fork in this change — the recorded decision chose the state precondition alone.

## Open decisions

none
