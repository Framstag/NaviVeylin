# Task 2.2 — left open, with its finding recorded

Recorded by the parent session during apply, 2026-10-31. The task is **not** marked done.

## The task

> Verify every rule that a superseded decision touches keeps a reference to the companion rather than an operator the
> reader must resolve. Verify: each such rule names `Design.why.md §13` …

## What the worker found

It reported that the rules a superseded decision touches do not carry an inline `Design.why.md §13` reference; only
`Design.md`'s maintenance line names the companion at all.

## What I measured as the extension, because the premise needed it

`Design.why.md §13` records **five** reversals, and each has a rule that exists *because* of it:

| §13 records the reversal of | the rule born from it lives in |
|---|---|
| live guidance built instruction prose in native, parsed back in the app | `DesignCrossVariant.md` / the route-step model (spec `osmscout-jni`) |
| dual-mode single APK (phone + AAOS in one artifact) | `Regulatory.md` / the two-flavor split (spec `android-automotive-os`) |
| GPS markers baked into the native render | `MapRendering.md §8` (display-layer overlays, marker-free cached buffers) |
| AA instruction hints surface-drawn | `UI.md` (host-rendered panel, one source of instruction UI) |
| bearing smoothing in the ViewModel | `MapRendering.md §7` (location layer) |

So the premise is real and the extension is **not** empty — but **four of the five rules live in documents owned by
changes that have already landed** (`guideline-maprendering-layers`, `guideline-ui-layers`,
`guideline-small-docs-layers` for `Regulatory.md`). Adding an inline pointer means editing those documents, i.e.
writing into a finished change's files from this one. That is why this task is left open rather than closed.

## The options, for the independent review

1. **Accept and record.** `§13` is reachable: the routing table's row for `Design.md §13` now names
   `Design.why.md`, so a future change re-proposing a rejected shape can find the record one hop from the rule's
   document. Cost: nothing. Residual: a rule does not point at its own reversal.
2. **Add the five pointers in a follow-up `docs-` change** that owns all four documents — one line each
   ("its reversal, and why, is `Design.why.md §13`"). Strictly additive, no new claim, no case, and it is the
   shape the project's own reviewer calls *subtractive/additive-with-no-new-claim*.
3. **Make it a rule**: "a rule that exists because a decision was reversed names the record" — a new requirement,
   a check that cannot be written (which rule was born from a reversal is not decidable from text), and therefore a
   reviewer-owned judgement rather than a guard. Not recommended: it would be the fifth rule in this set that no
   case can assert, and the project's own position is that judgement stays with the reviewer.

The parent's recommendation: **2** at the next convenient point, **1** if it never comes up, and the review should
grade this as a recorded residual rather than as an unfinished task.
