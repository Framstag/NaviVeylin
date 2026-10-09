---
name: pixel-check
description: Verify a *visual* claim (something is clipped, off-centre, missing, the wrong colour) by looking at a device screenshot with `view_image` and then measuring it with tools/measure-highlight.py — instead of asking the owner to describe what they see. Use whenever a finding is about pixels rather than state, and before changing projection/fit/layout code.
---

# pixel-check — look at the screenshot, then measure it

The agent *can* see a screenshot: `view_image` takes a local PNG path and returns the
image to the model, so say what is on screen before reaching for a script. That look is
triage. Reasoning about "the segment looks cut off" from projection code is still what
cost rounds 5–9 of `route-planning-session` (`ki_processing_failures.log`) — so measure
as well, and let the measurement decide.

```
view_image("/tmp/shot.png")               <- pixels reach the model; triage, not evidence
  -> what is on screen, in words
python3 tools/measure-highlight.py ...    <- the verdict: bbox / band / verdict / exit code
  -> the numbers that go into tasks.md
```

Two authorities, and they are not equal:

- **`view_image`** answers questions a script cannot express — which control has focus, is a
  label truncated, is the expected panel state on screen, is a list scrolled. Its answer is an
  *impression*: not reproducible, sometimes wrong.
- **`tools/measure-highlight.py`** answers where something is, reproducibly, with an exit code and
  a self-tested detector. Only its numbers and exit code are **evidence**.

A vision description never replaces the script's verdict. When the two disagree, the script wins
and the disagreement is the finding (that is how the follow-offset shift was found).

**But check the precondition before you trust a positive verdict.** The script measures whatever it is
handed, and its verdict is only meaningful when the frame is in the state it assumes — a route with an
analysed segment on screen and a card top inside the canvas. Two tells, both printed:

- `band` must end **above** the canvas bottom (a measured card top). `band=[0,<canvas height>]` means
  `--dump` found no card, so the frame is not in the expected state.
- the match must be a **stroke**: `px=` in the hundreds or thousands over many rows. A couple of dozen
  pixels spread over a wide box is a colour coincidence — on a phone map the light-blue `P` parking
  glyph lands on the dark casing colour `#E0F7FA`.

Recorded example of the failure (2026-10-06, a phone frame with no route, `.pi/logs/view-image-check/`):
`highlight (dark): bbox=[619, 641, 413, 734] px=18` / `canvas=1080x2400 band=[0,2400]` / `verdict:
inside`, exit 0. A crop of that box is ordinary base map; the look caught it. The script's own gap —
refusing instead of reporting `inside` when no plausible highlight exists — is `TODO.md` §147.
**The look establishes the precondition; the script measures given it.** A verdict that fails these
checks is a false positive, never a finding.

`view_image` is also the **sanctioned exception** to the rule that this session uses `ctx_*` tools
exclusively: `ctx_read` returns only a placeholder for an image file (`[Image: shot.png (1391
KB, image/png)]`, even with `mode=raw`), so image files go through `view_image` and everything else
through `ctx_*`.

If the call fails with `unable to locate image at <path>`, the file is gone — capture again rather
than describing a screenshot you did not read. Capture into a dedicated directory: a shared scratch
path (`.tmp-verify/`) was deleted by a peer session mid-investigation and turned a working call into
that error.

## The measurement tool

`tools/measure-highlight.py` finds the route's analysed-segment highlight by its
**casing colour** (`RouteSegmentHighlightOverlay`: day `#00454F`, dark `#E0F7FA`),
which is opaque and unique to the highlight, and reports its pixel bounding box
against the free map band:

```bash
D=.pi/logs/view-image-check        # dedicated dir inside the repo — never a shared scratch path
mkdir -p "$D"
# dump first, then the screenshot: the two must come from the same moment
adb shell 'uiautomator dump $EXTERNAL_STORAGE/ui.xml; cat $EXTERNAL_STORAGE/ui.xml' 2>&1 \
  | sed -n '/<?xml/,$p' > "$D/ui.xml"
adb exec-out screencap -p > "$D/shot.png"
# look first (view_image on "$D/shot.png"), then measure
python3 tools/measure-highlight.py "$D/shot.png" --dump "$D/ui.xml" --margin 126
```

Why the recipe is shaped like that (each line cost a round, 2026-10-06):

- **Do the device-side work inside one `adb shell '…'` string.** A literal `/sdcard/...` or `/dev/tty`
  in the *host* command is rejected by the path policy before anything runs
  (`Denied by policy: 'external_directory' … (rule '*')`), and `adb pull` cannot expand the device's
  `$EXTERNAL_STORAGE` — only the *device* shell can. Let the device expand it and filter host-side.
- **Keep `sed -n '/<?xml/,$p'`.** `uiautomator dump` prints `UI hierchary dumped to: …` before the XML,
  and `measure-highlight.py` parses the dump with `ElementTree`, so the header breaks the parse.
- **Capture into a dedicated directory inside the repo** (`.pi/logs/<what-you-are-checking>/`). `/tmp`
  and shared scratch dirs are not yours: `.tmp-verify/` was emptied by a peer session mid-investigation
  and the next `view_image` answered `unable to locate image at …`.
- **`magick` is not in the shell allowlist here; `convert` is** (ImageMagick 7 only warns that it is
  deprecated). Keep new image work inside a script as rule 5 says, or call `convert`.

Output (`--json` for one machine-readable line):

```
highlight (day): bbox=[300, 360, 900, 1100] px=…
canvas=1080x2400 band=[0,2164] margin=126
verdict: inside            # exit 0; 1 = clipped (names the edges); 2 = no highlight
```

- `--dump` derives the card top from the bottom-most full-width view with real
  height, so the band is the *measured* card edge, not an assumption.
- The detector requires a **dense run** of matching pixels per row
  (`MIN_RUN_PX`) — the row's *longest* run, wherever it sits in the row, so a
  casing-coloured glyph or label run right of the highlight cannot drop the row
  (`TODO.md` §169) — because in dark mode the light casing colour also appears on map
  labels — a colour match alone is not evidence. It is not sufficient either: on a
  frame with no route, 18 pixels of light-blue `P` parking glyph passed it and the
  script reported `verdict: inside` (`TODO.md` §147). Check the precondition and the
  `px`/`band` sanity before trusting a positive verdict.
- `bash tools/measure-highlight-selftest.sh` verifies the detector against synthetic
  images it builds itself (python3 stdlib, no ImageMagick, no device) — the row rule,
  the bbox extent, the floor and the band verdict. `bash .pi/skills/pixel-check/selftest.sh`
  covers inside / behind the card / no highlight with ImageMagick. Run one after touching
  the script.

## Rules

1. **Screenshot and dump must be the same moment** — take the dump, then the
   screenshot (or vice versa) within a second, otherwise the band and the pixels
   disagree and the verdict is noise.
2. **Report the numbers, not an impression**: bbox, band, margin, verdict. That is
   what goes into `tasks.md` as evidence.
3. **Compare with the model's own diagnostics.** The map logs what the fit
   *computed* (`MapCanvasVM: segment focus: … inside=…`, coordinate-free). If the
   model says `inside=true` and the measurement says `CLIPPED`, the disagreement is
   between model and pixels — the actual bug (that is how the follow-offset shift
   was found).
4. **Never claim a fix for a pixel symptom without either a measurement or a
   measurement that could not be taken** (e.g. a stationary emulator cannot produce
   follow drift). Say which of the two it is. A `view_image` description is neither:
   it is triage, and it is never the record.
5. Keep new colour detectors in the script, not in a throwaway shell line: the
   colour constants live in one place and the selftest covers them.

## The three-way comparison

The strongest evidence is a three-way agreement on the *same* leg/label:

| view | source |
|---|---|
| the fit's own numbers | `adb logcat -d -s MapCanvasVM` → `segment focus: range=… bboxPx=… inside=…` |
| what the overlay actually draws | `adb logcat -d -s RouteHighlight` → `range=… canvas=… bboxPx=…` |
| the pixels on screen | `tools/measure-highlight.py SHOT.png --dump XML` → `bbox=…`, `verdict` |

Two of them agreeing while the third differs locates the bug immediately: fit ≠
overlay means two consumers read different state (that is how the published-range
race was found: fit `range=9..28` vs overlay `range=28..32`), and overlay ≠ measured
pixels means the drawn frame does not match the projection used to place it.

A **short** analysed leg produces a short highlight: `MIN_RUN_PX` in the script must
stay low enough to see one, while still rejecting isolated label pixels.
