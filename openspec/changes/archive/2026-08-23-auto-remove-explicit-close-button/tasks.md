# Tasks: Remove explicit back button from Android Auto navigation

Spec: `specs/auto/navigation-view/spec.md` (MODIFIED "Leave navigation at any time"). Design: `design.md` (D1–D3).

## 1. Navigation screen changes

- [x] 1.1 Remove `.addAction(Action.BACK)` from the map action strip in `NavigationScreen.buildTemplate()` (keep the route-description action) and verify the navigation screen still compiles (`./gradlew :auto:compileDebugKotlin`)
- [x] 1.2 Keep the implicit leave mechanism intact — `backCallback { navigationViewModel.stopNavigation() }` in `NavigationScreen.init` unchanged — and verify `NavigationScreenTest` still passes (`./gradlew :auto:testDebugUnitTest --tests "*NavigationScreenTest"`)
- [x] 1.3 Update the `NavigationScreen` class KDoc (left map action strip no longer holds BACK; system back is the sole leave affordance per spec auto/navigation-view "Leave navigation at any time")

## 2. Docs and comments

- [x] 1.4 Update the doc comment in `NavigationScreenActions.kt` (map action strip now holds only the route-description action) and verify no stale "BACK" reference remains in `auto/src/main/java/com/naviveylin/auto/NavigationScreen*.kt` (grep for `Action.BACK` in the navigation screen files returns no match)

## 3. Tests

- [x] 3.1 Update `NavigationTemplateFactoryTest`: map action strip now has exactly one icon-only action (route-description), no `Action.BACK`; verify the updated assertions pass (`./gradlew :auto:testDebugUnitTest --tests "*NavigationTemplateFactoryTest"`)

## 4. Verification

- [x] 4.1 Run the full `:auto` unit test suite and verify all tests pass (`./gradlew :auto:testDebugUnitTest`)
- [x] 4.2 Run `openspec validate auto-remove-explicit-close-button` and verify the change validates (specs delta present, requirements have scenarios)

## 5. Stop action on navigation map (iteration 2)

- [x] 5.1 Rename `NavigationScreenActions.exitAction` → `stopAction` (shared X glyph, doc covers free-driving exit and navigation stop) and update the `FreeDrivingScreen` call site
- [x] 5.2 Wire the stop action into `NavigationScreen.buildTemplate()` map action strip: `stopAction { navigationViewModel.stopNavigation() }` before the route-description action; update the class KDoc
- [x] 5.3 Update tests: `NavigationScreenActionsTest` renames to `stopAction`, `NavigationTemplateFactoryTest` map strip expects two icon-only actions (stop + route-description), no `Action.BACK`
- [x] 5.4 Run the full `:auto` unit test suite and verify all tests pass (`./gradlew :auto:testDebugUnitTest`)
- [x] 5.5 Run `openspec validate auto-remove-explicit-close-button` and verify the change validates with the updated leave-navigation spec delta
