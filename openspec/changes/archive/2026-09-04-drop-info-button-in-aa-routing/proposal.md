# Proposal: Drop info button from AA routing screen

## What Changes

The Android Auto navigation (routing) screen currently shows an info button
("i" in a circle) in the right action strip that opens the About screen. The
map screen already dropped this button — its comment notes the host caps the
strip at 4 actions and licence info stays reachable via the content menu's
About row. The routing screen kept the button, making the two screens
inconsistent.

This change removes the info button from the navigation screen's action strip.
About/licence info remains reachable via the app menu (About row), which the
`auto-map-layout` spec already pins ("WHEN the user taps 'About' THEN the
about screen opens").

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. No spec requirement changes: neither `auto-map-layout` nor
`auto/navigation-view` pins an info button in the navigation strip. The About
entry in the content menu is unchanged. This is a pure UI removal, so the
change sets `skip_specs: true`.

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — remove the
  `MapStripActions.infoAction` entry from the navigation action strip.
- `auto/src/main/java/com/naviveylin/auto/MapStripActions.kt` — remove the now
  unused `infoAction` factory.
- `auto/src/main/java/com/naviveylin/auto/CarGlyphs.kt` — remove the now unused
  `info` glyph.
- `auto/src/test/java/com/naviveylin/auto/MapStripActionsTest.kt` — remove the
  `infoIsDrivingSafeAndIconOnly` test.
- No API, dependency, or native changes. About screen itself stays.
