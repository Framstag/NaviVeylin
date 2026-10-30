# Task 5.1 — the pinned band on the device, 2026-10-30

Device: `emulator-5554`, `Pixel_8` AVD, 1080x2400 px at 420 dpi (2.625 px/dp), Android 16 (`sdk 37`),
routable region Andorra (`files/maps/andorra`), app `com.framstag.naviveylin` 1.0.0 built from this tree
(`:app:assembleMobileDebug -Pandroid.injected.build.abi=x86_64`, installed with `adb install -r -t`, package
data untouched). The route: `Current Location → Carrer de la Llacuna`, 650 m, calculated in the app; the
session card in **max** anchor unless a line says otherwise.

Measuring: `adb shell uiautomator dump` into the dumps beside this file, read by
`band-measure.py <dump>` (copied here so a reader can re-take the numbers without the session that took them).
A UI dump is the authority; no screenshot was read, and none is cited.

## Claim (a) — the labelled action is in the card at font scale 1.0 / 1.3 / 2.0

The card's own container node (the ancestor of the band) is `[0,1320][1080,2400]` in **all three** dumps —
1080 px = 411.43 dp, the 45 % share the archived record quotes. The font scale changes the band's inner
labels, not the card's bounds.

| font scale | dump | End action box (px) | End action height | inside `[0,1320][1080,2400]` |
|---|---|---|---|---|
| 1.0 | `dump-max-fs1.0.xml` | `[42,2128][1038,2254]` | 126 px = **48.00 dp** | yes — bottom 2254 < 2400 |
| 1.3 | `dump-max-fs1.3.xml` | `[42,2128][1038,2254]` | 126 px = **48.00 dp** | yes |
| 2.0 | `dump-max-fs2.0.xml` | `[42,2113][1038,2253]` | 140 px = **53.33 dp** | yes |

At font scale 2.0 the action is present in the dump, 53.33 dp tall — the same number the host case
`RoutePanelActionBandScaleTest` measured after the fix (42.67 dp → 53.33 dp). The device symptom that opened
§138 — the labelled action leaving the card and reaching no UI dump — does **not** reproduce on the fixed
tree. Its labels wrap (`Change start & target` 128.00 dp, `Start Navigation` 90.67 dp at 2.0) and the band
grows, which is the behaviour the fix intended.

## Claim (b) — the screen's report→inset join, executed by the app

The decisive pair is one session at font scale 1.0, where only the card's anchor changes, so the column's own
content is constant and any movement of it is the reported number:

| state | dump | card container | card height | zoom-out clickable box | column bottom above the card top |
|---|---|---|---|---|---|
| max | `dump-max-fs1.0.xml` | `[0,1320][1080,2400]` | 1080 px | `[923,1020][1049,1146]` | 1320 − 1146 = 174 px (66.29 dp) |
| min (collapsed) | `dump-min-fs1.0.xml` | `[0,2175][1080,2400]` | 225 px | (zoom-out bottom) 2001 | 2175 − 2001 = 174 px |

The card shrinks by `1080 − 225 = 855 px` and the chrome column moves **down by exactly 855 px**
(`2001 − 1146`), keeping the same 174 px gap. That is the join: the height the card reports reaches
`setOverlayCoveredPx`, the map's fit and the chrome band's `bottomInset`, and nothing sits between the two.
The 174 px itself is the column's own fixed padding (44 dp bottom + the 8 dp trailing spacer), not the inset.

At the two larger scales the column is not a usable witness, and that is a separate finding rather than a
weaker verdict here: only `Zoom in` / `Zoom out` remain in the tree (the compass, the drive toggle and the
location-options control are absent), the gap to the card is 526 px (1.3) and 493 px (2.0), and the zoom-in
clickable box is clipped to 36.19 dp. Reported on the tracker as its own item; it does not affect the card's
containment above.

## What is not verified here

- The exact equality `reported px == inset px` cannot be read off a dump: the column's own padding sits
  between the two, and the dumps carry no testTag on the card's surface. What the device shows is the
  *behaviour* — the column tracks the card's height change one-for-one (855 px), which is what the
  requirement asks for.
- One font scale per card state, one AVD, one session: the numbers above are single readings, not a sweep.
