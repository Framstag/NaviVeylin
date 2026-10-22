# Design

## Root cause

`LoadingScreen` (`SafeScreen.kt:165`) lost its last production caller when `NavigationSession` started
serving the real root screen immediately (the `auto-startup-hardening` change); nothing in that change removed
the class, its strings or its one test case, so the module keeps a caller-less wait-notice screen beside the
live one.

## The single fix

Delete the dead screen end to end:

- `SafeScreen.kt` — the `LoadingScreen` class and its KDoc.
- `values/strings.xml` / `values-de/strings.xml` — `loading_map_data` and `preparing_navigation`, which this
  screen was the only consumer of (`grep -rn "loading_map_data\|preparing_navigation" auto/src/main` →
  `SafeScreen.kt:171-172` and the two resource files).
- `NavigationSession.kt:66`, `:369`, `:1176` — the comments keep the mechanism they teach (a transient screen
  may not be the stack root, because `ScreenManager` cannot pop the root) and stop naming the removed class.
- `StartupScreensTest` — `loadingScreenShowsLoadingState`, which constructed the class, becomes
  `retiredLoadingScreenClassIsGone`, and `GermanTranslationCompletenessTest` gains
  `retiredWaitNoticeStringsAreGone`; both read the absence and both are red on HEAD.

## Blast radius

No compiled behaviour changes: the deleted class is never constructed in `auto/src/main`. `RouteCalculatingScreen`
and `guidelines/UI.md` §3c are unchanged, because §3c never described the dead screen. The German-completeness
case only checks `values` ⊆ `values-de`, so deleting a key from both files cannot redden it.

surface: 2 cases added, 1 case removed, 1 class deleted, 3 comments rewritten, 4 resource lines deleted in 2
files — the same search after the fix (`grep -rn "LoadingScreen\|loading_map_data\|preparing_navigation"
auto/src app/src core/src tools/loop/prompts tools/loop/fixtures tools/*-cases.sh guidelines/ openspec/specs/`)
matches 7 lines in 3 files, all of them this change's own: the two cases and the requirement the delta adds
(`evidence/surface-after.log`).

## Rollback

`git revert` of one commit: a class, four resource lines, three comments and two test cases. Nothing depends on
the removal, so no state, no migration and no schema is involved.

## Alternatives the evidence eliminated

- **Make `LoadingScreen` the single wait-notice base that `RouteCalculatingScreen` builds on** (the item's
  second candidate). Eliminated by two recorded rules, not by taste: `Design.md` §12 *"One owner per concern:
  an abstraction … is justified by a real second consumer — not by structure"* — the only subclass would be
  the one screen that already works, and the base exists for no caller — and §12 *"No dead code: … remove
  it"*. An abstraction whose whole point is to keep the caller-less class alive is the defect spelled with
  more structure.
- **Keep the two strings for a future loading screen.** Eliminated by the same rule (`Design.md` §12): a
  string with no consumer is dead code with a translation cost, and re-adding two keys to two locale files is
  a smaller edit than carrying them.
- **Leave the `NavigationSession` comments alone.** Eliminated by the delta's own third clause and by
  `render-mode-switch`'s precedent ("Documentation and specs SHALL NOT reference the removed design"): the
  comments would name a class the module no longer has, which is exactly the "invites misuse" §12 warns
  about.
