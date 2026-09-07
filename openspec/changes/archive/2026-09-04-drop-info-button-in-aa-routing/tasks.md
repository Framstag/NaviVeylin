# Tasks: Drop info button from AA routing screen

## 1. Remove the info button

- [x] 1.1 Remove the `MapStripActions.infoAction` entry from the navigation
      action strip in `NavigationScreen.buildTemplate`
- [x] 1.2 Delete the `infoAction` factory from `MapStripActions.kt`
- [x] 1.3 Delete the unused `info` glyph from `CarGlyphs.kt`

## 2. Tests

- [x] 2.1 Delete `MapStripActionsTest.infoIsDrivingSafeAndIconOnly`
- [x] 2.2 Run `:auto:testDebugUnitTest` — passes
