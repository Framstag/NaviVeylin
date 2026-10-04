#!/usr/bin/env python3
"""Measure a claimed on-screen invariant in a device screenshot.

Why this exists: an agent cannot look at a screenshot, and "the segment is not
completely visible" cannot be settled by reasoning about projection code. This
script turns the screenshot into numbers, so a visual claim is verified (or
refuted) with evidence instead of another round trip with the owner.

It finds the route-segment highlight by its *casing* colour, which is opaque and
unique to the highlight (`RouteSegmentHighlightOverlay`: day `#00454F`, dark
`#E0F7FA`), and reports the highlight's bounding box inside the free map band
(the canvas minus the overlay card).

Usage:
  measure-highlight.py SHOT.png [--dump XML] [--band-bottom N] [--margin N] [--json]

  --dump XML        uiautomator dump of the same moment; the card top is derived
                    from the bottom-most node that spans the screen width
  --band-bottom N   card top in px (overrides --dump)
  --margin N        pixels the highlight must stay clear of the band's edges
  --json            machine-readable single line

Exit code 0 when the highlight is inside the band, 1 when it is clipped, 2 when
no highlight was found (e.g. no step analysed).
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET

# Casing colours of the analysed-segment highlight (`RouteSegmentHighlightOverlay`).
# The day casing is a dark teal, the dark-mode casing a light cyan — both opaque and
# *unique to the highlight*, but only per theme: in dark mode a light colour also
# appears on map labels, so a colour match alone is not evidence. The detector
# therefore requires a **dense run** of matching pixels per row, which random label
# pixels never form (a stroke of the analysed segment is ~8 px wide).
CASINGS = {
    "day": (0x00, 0x45, 0x4F),
    "dark": (0xE0, 0xF7, 0xFA),
}
TOLERANCE = 4
# A short analysed leg is a short highlight: the threshold must stay low enough to see one
# (a leg crossing the band is hundreds of px, a 40 m leg at city zoom is a handful), while
# still rejecting the isolated label pixels that carry the same colour in dark mode.
MIN_RUN_PX = 8


def find_casing(pixels, width, height, target):
    """Bounding box of rows that hold a dense horizontal run of [target] pixels."""
    x0 = y0 = 10 ** 9
    x1 = y1 = -1
    count = 0
    for y in range(height):
        run = 0
        first = -1
        last = -1
        for x in range(width):
            r, g, b = pixels[x, y]
            if (abs(r - target[0]) <= TOLERANCE
                    and abs(g - target[1]) <= TOLERANCE
                    and abs(b - target[2]) <= TOLERANCE):
                if run == 0:
                    first = x
                run += 1
                last = x
            else:
                run = 0
        if run >= MIN_RUN_PX or (last - first + 1) >= MIN_RUN_PX:
            count += last - first + 1
            if first < x0:
                x0 = first
            if last > x1:
                x1 = last
            if y < y0:
                y0 = y
            if y > y1:
                y1 = y
    if count == 0:
        return None
    return {"bbox": [x0, x1, y0, y1], "count": count}


def card_top_from_dump(path):
    """Top of the bottom-most full-width view with real height = the overlay card."""
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return None
    best = None
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        x0, y0, x1, y1 = (int(value) for value in match.groups())
        width = x1 - x0
        height = y1 - y0
        # Bottom-docked, full width, and too tall to be a button row.
        if y1 >= 2000 and width >= 1000 and height > 120:
            if best is None or y0 > best:
                best = y0
    return best


def measure(image_path, band_bottom=None):
    from PIL import Image

    image = Image.open(image_path).convert("RGB")
    width, height = image.size
    pixels = image.load()
    if band_bottom is None or band_bottom <= 0:
        band_bottom = height

    boxes = {}
    for name, target in CASINGS.items():
        box = find_casing(pixels, width, height, target)
        if box:
            boxes[name] = box
    return width, height, band_bottom, boxes


def verdict(width, height, band_bottom, box, margin):
    x0, x1, y0, y1 = box["bbox"]
    clipped = []
    if x0 < margin:
        clipped.append("left")
    if x1 > width - margin:
        clipped.append("right")
    if y0 < margin:
        clipped.append("top")
    if y1 > band_bottom - margin:
        clipped.append("bottom")
    return clipped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--dump")
    parser.add_argument("--band-bottom", type=int, default=0)
    parser.add_argument("--margin", type=int, default=0)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    band_bottom = args.band_bottom or (card_top_from_dump(args.dump) if args.dump else 0)
    width, height, band_bottom, boxes = measure(args.image, band_bottom)
    if not boxes:
        print(json.dumps({"image": args.image, "highlight": None,
                          "image_size": [width, height], "band_bottom": band_bottom}))
        return 2

    name, box = max(boxes.items(), key=lambda item: item[1]["count"])
    clipped = verdict(width, height, band_bottom, box, args.margin)
    result = {
        "image": args.image,
        "presentation": name,
        "bbox": box["bbox"],
        "highlight_px": box["count"],
        "image_size": [width, height],
        "band": [0, band_bottom],
        "margin_px": args.margin,
        "clipped": clipped,
        "inside": not clipped,
    }
    if args.json:
        print(json.dumps(result))
    else:
        print("highlight (%s): bbox=%s px=%d" % (name, box["bbox"], box["count"]))
        print("canvas=%dx%d band=[0,%d] margin=%d" % (width, height, band_bottom, args.margin))
        print("verdict: %s%s" % ("inside" if not clipped else "CLIPPED",
                                 "" if not clipped else " at " + ",".join(clipped)))
    return 0 if not clipped else 1


if __name__ == "__main__":
    sys.exit(main())
