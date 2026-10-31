#!/usr/bin/env python3
# One-off cut of guidelines/MapRendering.md into four normative documents and their companions.
# Original line numbers are 1-based and inclusive. Reads guidelines/MapRendering.md; writes the four base
# documents and the companions. No source line is reworded except a purity token (moved to a companion) and
# the `**Rule: <name>**` naming the mechanism requires.
import re, os

SRC = "openspec/changes/guideline-maprendering-layers/evidence/MapRendering.pre-cut.md"
L = open(SRC, encoding="utf-8").read().split("\n")  # L[i-1] == line i

def K(a, b):  # inclusive 1-based
    return L[a-1:b]

def join(lines):
    return "\n".join(lines).rstrip("\n") + "\n"

# ---- purity tokens: each is moved to a companion and replaced by a pointer (documentation-ownership,
#      "A guideline document carries one kind of text") ----
PURITY = [
    ("(issue #18, 2026-09-27)",
     "(the dated record is `MapRendering.why.md` §1)"),
    ("fails it (measured 2026-10-21: `callSiteRawReads=2`, mutated run red).",
     "fails it (`MapRendering.why.md` §1 carries the measurement)."),
    ("(measured 2026-10-13 on the phone:",
     "(the dated measurement is in `MapFollow.why.md` §11:"),
    ("issue #25 is what the",
     "the documented silence (`MapCar.why.md` §14) is what the"),
    ("(measured 2026-10-26)",
     "(`MapRendering.recipes.md` §20)"),
]

def scrub(text):
    for a, b in PURITY:
        text = text.replace(a, b)
    return text

# ---- the §1 split: [new number, heading text, source lines] ----
SEC1 = [
    ("1a", "Follow framing — the anchor applies to the RENDER TARGET (read before touching follow mode)", (66, 129)),
    ("1b", "The render modes and the path selection", (15, 49)),
    ("1c", "The overlay stage, the mode switch, the low-zoom risk and the removed helpers", (50, 63)),
    ("1d", "Marker projection and the overlay viewport", (130, 237)),
    ("1e", "The follow display contract on phone and Android Auto", (238, 260)),
]

SEC1_INDEX = """## 1. Render Pipeline Architecture

This section is an index by question; the rules are stated under the sub-sections, and the history and the
measurements behind them are in `guidelines/MapRendering.why.md` §1.

- **§1a** — follow framing: the vehicle anchor is applied in exactly one place, the render target.
- **§1b** — the two render modes (`TILES`, `DIRECT`) and how `executeRender` selects between them.
- **§1c** — the overlay stage, the mode switch, the low-zoom risk and the removed helpers.
- **§1d** — marker projection: which frame the Compose and car overlays project against.
- **§1e** — the follow display contract shared by the phone and Android Auto.

"""

MOVED_INDEX = """

## Sections that moved out of this document

The numbers below are carried by the document named, and this document no longer states their rules:

- §3, §6, §7, §8, §9, §10, §11 — `guidelines/MapFollow.md`.
- §14 (the Android Auto renderer), §18 — `guidelines/MapCar.md`.
- §15, §15a, §16, §16a, §16b, §17 — `guidelines/MapStyling.md`.

The regression checklist is `guidelines/MapRendering.why.md` "Known Pitfalls"; the parameter table is
`guidelines/MapRendering.recipes.md` "Parameter Overview".
"""

# ---- the four base documents ----
def build_maprendering():
    out = ["# Map Rendering — Knowledge Base & Rules\n"]
    out.append("\n")
    out.append("This document owns the render pipeline and its data rules — the two render modes, the bitmap\n")
    out.append("lifecycle, coalescing, epochs, the front buffer, the overrun window, the rotation-gesture handoff,\n")
    out.append("route data and label wrapping. The follow path is `guidelines/MapFollow.md`, the car renderer\n")
    out.append("`guidelines/MapCar.md`, and the stylesheet and icon contract `guidelines/MapStyling.md`. The history,\n")
    out.append("the measurements and the rejected alternatives are in `guidelines/MapRendering.why.md`; the commands\n")
    out.append("and number tables are in `guidelines/MapRendering.recipes.md`.\n")
    out.append("\n---\n\n")
    out.append(SEC1_INDEX)
    for num, text, (a, b) in SEC1:
        out.append("\n")
        out.append(f"## {num}. {text}\n\n")
        out.append(join(K(a, b)))
    for (a, b) in [(262, 295), (306, 324), (325, 340), (529, 551), (552, 630), (631, 653), (1112, 1149), (1150, 1185)]:
        out.append(join(K(a, b)))
    out.append(MOVED_INDEX)
    return scrub("".join(out))

def build_follow():
    out = ["# Map Follow — location, follow and camera\n"]
    out.append("\n")
    out.append("This document owns the follow path: GPS deduplication, angle handling, bearing smoothing, the marker\n")
    out.append("rules, follow-mode rotation and camera center, and auto-zoom. The pipeline it rides is\n")
    out.append("`guidelines/MapRendering.md`; the rationale and the measurements behind these rules are in\n")
    out.append("`guidelines/MapFollow.why.md`.\n")
    out.append("\n---\n\n")
    for rng in [(296, 305), (341, 366), (367, 413), (414, 446), (447, 457), (458, 463), (464, 528)]:
        out.append(join(K(*rng)))
    return scrub("".join(out))

def build_car():
    out = ["# Map Car — the Android Auto renderer and the phone canvas\n"]
    out.append("\n")
    out.append("This document owns the car's own render path — the Android Auto smooth-follow renderer (overrun,\n")
    out.append("blit and extrapolation) — and what happens to the phone canvas while a car session is active. The\n")
    out.append("pipeline they share is `guidelines/MapRendering.md`; the history is in `guidelines/MapCar.why.md`.\n")
    out.append("\n---\n\n")
    for rng in [(708, 846), (1058, 1111)]:
        out.append(join(K(*rng)))
    return scrub("".join(out))

def build_styling():
    out = ["# Map Styling — stylesheets, icons and the tile cache\n"]
    out.append("\n")
    out.append("This document owns the stylesheet contract (day/night, basemap, load failure, source and on-device\n")
    out.append("copy), the POI icons and where they come from, and the native tile-data cache. The history and the\n")
    out.append("packaging failures behind these rules are in `guidelines/MapStyling.why.md`.\n")
    out.append("\n---\n\n")
    for rng in [(847, 907), (908, 945), (946, 973), (974, 1004), (1005, 1017), (1018, 1057)]:
        out.append(join(K(*rng)))
    return scrub("".join(out))

# ---- companions ----
def build_rendering_why():
    out = ["# MapRendering — the rationale\n"]
    out.append("\n")
    out.append("This is the rationale layer of `guidelines/MapRendering.md`: the history, the derivation, the dated\n")
    out.append("measurements and the rejected alternatives behind the rules the base document states, read against the\n")
    out.append("numbered section the base document carries. An anchor here names a section number the base document\n")
    out.append("carries.\n\n")
    out.append("## §1 Render Pipeline Architecture — the anchor's history and measurements\n\n")
    out.append("The blit seam's history, excised from §1a:\n\n")
    out.append("- The screen's previous inline `OverrunWindow.shiftPx(…, activeFollowAnchor.fx,\n")
    out.append("  activeFollowAnchor.fy)` call site was invisible to every case and needed two fixes in one day\n")
    out.append("  (issue #18, 2026-09-27).\n")
    out.append("- A caller that rebuilds the state around the preset is refused; the guard reads the code around the\n")
    out.append("  seam call, so `offsetFor(ui.copy(resolvedAnchor = ResolvedAnchor(ui.activeFollowAnchor.fx,\n")
    out.append("  ui.activeFollowAnchor.fy)), …)` fails it (measured 2026-10-21: `callSiteRawReads=2`, mutated run red).\n\n")
    out.append("## §13 Overrun Display Window (pan and follow) — the regression checklist\n\n")
    out.append(join(K(654, 707)))
    out.append("\n")
    return "".join(out)

def build_rendering_recipes():
    out = ["# MapRendering — the recipes\n"]
    out.append("\n")
    out.append("This is the procedure layer of `guidelines/MapRendering.md`: the number tables and the measurement\n")
    out.append("recipes, read against the section the base document states the rule in.\n\n")
    out.append("## §20 Label wrapping — the parameter table\n\n")
    out.append(join(K(1186, 1202)))
    out.append("\n## §20 Label wrapping — the pango measurement\n\n")
    out.append("- `pkg-config --modversion pangocairo` answers `1.58.0` when pointed at one of the three Android\n")
    out.append("  vcpkg triplets that install pango, harfbuzz and fribidi (measured 2026-10-26); the module's own\n")
    out.append("  detection is what does not reach them.\n")
    return "".join(out)

def build_follow_why():
    out = ["# MapFollow — the rationale\n"]
    out.append("\n")
    out.append("This is the rationale layer of `guidelines/MapFollow.md`. An anchor here names a section number the\n")
    out.append("base document carries.\n\n")
    out.append("## §7 Bearing Smoothing (location layer) — why a bearing is smoothed\n\n")
    out.append("A raw fix bearing is noisy, so the displayed heading is smoothed before it drives the map rotation;\n")
    out.append("the bound the smoothing holds is stated in `guidelines/MapFollow.md` §7.\n\n")
    out.append("## §8 Marker Rules — why a marker is deduplicated\n\n")
    out.append("A marker is deduplicated so the drawn position does not jump when two fixes describe one stop; the\n")
    out.append("rule is stated in `guidelines/MapFollow.md` §8.\n\n")
    out.append("## §11 Auto-Zoom — the measurement behind the pin\n\n")
    out.append("The walk's pin on the render magnification lives exactly as long as the walk. Every magnification\n")
    out.append("committed outside the walk (`fitViewportToRoute`, the follow-engage preset, the mode presets, a\n")
    out.append("re-center) is silently discarded (measured 2026-10-13 on the phone: the route overview fit committed\n")
    out.append("mag 10.0 and the frame was rendered at the landed auto-zoom walk's 17.756, so the fitted route lay\n")
    out.append("outside the frame until an unrelated render put the camera back on the vehicle).\n")
    return "".join(out)

def build_styling_why():
    out = ["# MapStyling — the rationale\n"]
    out.append("\n")
    out.append("This is the rationale layer of `guidelines/MapStyling.md`: the failed-load history and the packaging\n")
    out.append("failures behind the stylesheet and icon rules. An anchor here names a section number the base document\n")
    out.append("carries.\n\n")
    out.append("## §15a Stylesheet load failure (app side) — why a rejected style is never rendered\n\n")
    out.append("A stylesheet that fails to load once is not retried on every frame; the app refuses the style and\n")
    out.append("keeps the previous one, because a render against a half-loaded stylesheet crashed the app in the\n")
    out.append("rounds that produced this rule. The rule is stated in `guidelines/MapStyling.md` §15a.\n\n")
    out.append("## §16a POI icons — the packaging failures behind the fallback\n\n")
    out.append("Icons that the configured directory does not carry fall back to a symbol; the packaging failures that\n")
    out.append("produced the fallback are the reason the rule exists. The rule is stated in `guidelines/MapStyling.md`\n")
    out.append("§16a.\n")
    return "".join(out)

def build_car_why():
    out = ["# MapCar — the rationale\n"]
    out.append("\n")
    out.append("This is the rationale layer of `guidelines/MapCar.md`. An anchor here names a section number the base\n")
    out.append("document carries.\n\n")
    out.append("## §14 Android Auto renderer — the driver-visible silence behind the wait bound\n\n")
    out.append("A requested car frame that never lands is re-requested. The silence that motivated the bound looked\n")
    out.append("to a driver like a black surface with host chrome and no message (issue #25). The rule is stated in\n")
    out.append("`guidelines/MapCar.md` §14.\n")
    return "".join(out)

# ---- the rule-naming transform (mechanism D2): every `- **<lead>**` bullet becomes `**Rule: <lead>**` ----
def name_rules(text):
    lines = text.split("\n")
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(\s*)- \*\*(?!Rule: )(.*)$", line)
        if not m:
            out.append(line); i += 1; continue
        indent, rest = m.group(1), m.group(2)
        if "**" in rest:
            lead, tail = rest.split("**", 1)
        else:
            # multi-line bold span: accumulate until the closing **
            buf = rest
            j = i + 1
            while j < len(lines) and "**" not in lines[j]:
                buf += "\n" + lines[j]; j += 1
            if j >= len(lines):
                out.append(line); i += 1; continue
            head, tail2 = lines[j].split("**", 1)
            buf += "\n" + head
            lead, tail = buf, tail2
            i = j
            # collapse the accumulated newlines inside the name
        name = re.sub(r"\s+", " ", lead).strip()
        if name.startswith("§"):
            out.append(line); i += 1; continue
        name = name.rstrip(":")
        out.append(f"{indent}- **Rule: {name}**{tail}")
        i += 1
    return "\n".join(out)

DOCS = {
    "guidelines/MapRendering.md": build_maprendering,
    "guidelines/MapFollow.md": build_follow,
    "guidelines/MapCar.md": build_car,
    "guidelines/MapStyling.md": build_styling,
}
COMPANIONS = {
    "guidelines/MapRendering.why.md": build_rendering_why,
    "guidelines/MapRendering.recipes.md": build_rendering_recipes,
    "guidelines/MapFollow.why.md": build_follow_why,
    "guidelines/MapStyling.why.md": build_styling_why,
    "guidelines/MapCar.why.md": build_car_why,
}

if __name__ == "__main__":
    for path, fn in DOCS.items():
        text = name_rules(fn())
        open(path, "w", encoding="utf-8").write(text)
        print(f"{path}: {len(text.encode())} bytes")
    for path, fn in COMPANIONS.items():
        text = fn()
        open(path, "w", encoding="utf-8").write(text)
        print(f"{path}: {len(text.encode())} bytes")
