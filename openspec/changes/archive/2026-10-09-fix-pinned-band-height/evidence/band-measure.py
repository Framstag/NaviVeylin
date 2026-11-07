#!/usr/bin/env python3
"""Report the pinned band's boxes and the card's top from a uiautomator dump.

Usage: band.py <dump.xml> [--json]

Prints, in dp (density 420 dpi -> 2.625 px/dp):
  * every clickable node in the bottom 320 px with its label (first non-empty text or content-desc descendant)
  * the card's top edge: the minimum top of the nodes that sit in the card region below the map
  * the band: the lowest clickable row's box, and its share of the card
No coordinate leaves this script's output as a location: the numbers are pixel/box values, which the
project's logging rule allows (pixels, not coordinates).
"""
import sys
import xml.etree.ElementTree as ET

DENSITY = 420.0
PX_PER_DP = DENSITY / 160.0


def bounds(node):
    raw = node.get("bounds", "")
    try:
        left_top, right_bottom = raw.split("][")
        left, top = left_top.lstrip("[").split(",")
        right, bottom = right_bottom.rstrip("]").split(",")
        return int(left), int(top), int(right), int(bottom)
    except ValueError:
        return None


def label(node, out=None):
    """First non-empty text/content-desc under the node."""
    if out is None:
        out = []
    for attr in ("text", "content-desc"):
        value = (node.get(attr) or "").strip()
        if value:
            out.append(value)
    for child in node:
        label(child, out)
    return out[0] if out else ""


def dp(px):
    return round(px / PX_PER_DP, 2)


def main():
    path = sys.argv[1]
    root = ET.parse(path).getroot()
    rows = []
    for node in root.iter("node"):
        box = bounds(node)
        if box is None:
            continue
        rows.append((node, box))
    card_region_top = None
    clickables = []
    for node, (l, t, r, b) in rows:
        if node.get("clickable") == "true":
            clickables.append((label(node), l, t, r, b))
            if t > 900 and (card_region_top is None or t < card_region_top):
                card_region_top = t
    print(f"dump: {path}")
    print("clickables (label, box px, box dp, height dp):")
    for name, l, t, r, b in sorted(clickables, key=lambda x: x[2]):
        if t < 900:
            continue
        print(f"  {name[:44]:44s} [{l},{t}][{r},{b}]  "
              f"dp=[{dp(l)},{dp(t)}][{dp(r)},{dp(b)}]  h={dp(b - t)}dp w={dp(r - l)}dp")
    # the band: the clickables whose top is the greatest (bottom-most row)
    if clickables:
        band = [c for c in clickables if c[2] > 1900]
        if band:
            top = min(c[2] for c in band)
            bottom = max(c[4] for c in band)
            print(f"band row: top={top}px ({dp(top)}dp) bottom={bottom}px ({dp(bottom)}dp) "
                  f"row_height={dp(bottom - top)}dp")
    if card_region_top is not None:
        print(f"card top (topmost clickable in the card): {card_region_top}px ({dp(card_region_top)}dp)")


if __name__ == "__main__":
    main()
