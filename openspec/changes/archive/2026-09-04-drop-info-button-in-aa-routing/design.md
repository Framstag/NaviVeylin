# Design: Drop info button from AA routing screen

## Context

The navigation screen (`NavigationScreen.buildTemplate`) adds
`MapStripActions.infoAction` to the right action strip, pushing
`AboutScreen`. The map screen already omits it: the host caps the strip at 4
actions (`ACTIONS_CONSTRAINTS_MAP`) and About stays reachable via the content
menu's About row (`MapTemplateFactory.buildMenuContent`). The routing screen
is the only remaining user of `infoAction` and the `CarGlyphs.info` glyph.

## Goals / Non-Goals

**Goals:**
- Remove the info button from the navigation screen action strip.
- Remove the now-dead `infoAction` factory and `CarGlyphs.info` glyph.
- Keep About/licence info reachable via the app menu (unchanged).

**Non-Goals:**
- No change to the About screen itself or the menu's About row.
- No spec requirement changes (`skip_specs: true`).

## Decisions

**Remove the strip action, not the About screen.** About stays reachable via
the menu, matching the map screen and the `auto-map-layout` spec ("WHEN the
user taps 'About' THEN the about screen opens").

**Delete dead code.** `infoAction` and `CarGlyphs.info` have no other call
sites after removal; leaving them would be dead code. Alternative — keeping
them for future use — rejected: unused glyphs/factories rot and the map
screen already documents why the strip does not carry licence info.

**Update tests.** `MapStripActionsTest.infoIsDrivingSafeAndIconOnly` tests the
removed factory and is deleted with it. No other test references the strip
info action (`NavigationScreenTest` only covers the back callback).

## Risks / Trade-offs

- None material. The navigation strip keeps zoom in/out; About remains one
  menu tap away. Host strip action count drops from 3 to 2, well under the
  cap.
