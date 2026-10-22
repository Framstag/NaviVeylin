# fix-retire-loading-screen

## Why

Issue #80 (§127). Verified on the run's base commit `9aac52b5d9778a6d5cc2a92cb95a35dc9c7eb00e`, in the loop's
worktree (`.pi/worktrees/loop`).

**The condition still holds.** `LoadingScreen` is declared at
`auto/src/main/java/com/naviveylin/auto/SafeScreen.kt:165` and has exactly one constructor site in the tree,
`auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:66` (`loadingScreenShowsLoadingState`). No
production file constructs it: `grep -rn "LoadingScreen" auto/src/main` returns the class declaration and
three comments (`NavigationSession.kt:66`, `:369`, `:1176`), nothing else, and `NavigationSession:369-373`
records that the root screen is deliberately *not* a transient one. Its two strings
(`auto/src/main/res/values/strings.xml:59-60`, `values-de/strings.xml:56-57`) have no other consumer. Since
the entry was filed, `RouteCalculatingScreen` landed as the car display's transient wait notice
(`guidelines/UI.md` §3c, spec `route-calculation-feedback`), so the dead screen now has a live sibling of the
same shape.

**Root cause, one sentence:** `LoadingScreen` survived the change that made the session serve the real root
screen immediately (`auto-startup-hardening`) because nothing removed it, so the module carries a second,
caller-less wait-notice screen whose only reader is its own test.

**Rule that owns the repair.** `guidelines/Design.md` §12: *"No dead code: dead code contradicts specs and
invites misuse; remove it and fix the spec in the same change."* The owning capability is
`route-calculation-feedback` (the car wait notice), and the owning guideline section is `guidelines/UI.md`
§3c "Car wait notices (a wait the driver can see and leave)", which names `RouteCalculatingScreen` as the
reference implementation — §3c needs no edit, because it never mentioned the dead screen.

**Red test on HEAD, before the fix.** Two cases, run against the base commit:

```
StartupScreensTest > retiredLoadingScreenClassIsGone FAILED
    java.lang.AssertionError: com.naviveylin.auto.LoadingScreen is still on the classpath
GermanTranslationCompletenessTest > retiredWaitNoticeStringsAreGone FAILED
```

`evidence/red-HEAD.log` (2026-10-22T10:33:39Z) and the JUnit XMLs beside it:
`TEST-com.naviveylin.auto.StartupScreensTest.xml` `tests="7" failures="1"`,
`TEST-com.naviveylin.auto.GermanTranslationCompletenessTest.xml` `tests="3" failures="1"` — the module's other
cases are green on the same run, so the two failures are the change's, not the tree's.

surface: 2 cases added, 1 case removed, 1 class (16 lines + 8 KDoc lines) deleted, 3 comments rewritten,
4 string-resource lines deleted in 2 files. No shell case, no fixture, no prompt and no shipped spec asserted
the retired screen, and the only surviving references are this change's own — the same search after the fix
(`grep -rn "LoadingScreen\|loading_map_data\|preparing_navigation" auto/src app/src core/src tools/loop/prompts
tools/loop/fixtures tools/*-cases.sh guidelines/ openspec/specs/`) matches 7 lines in 3 files: the two cases
and the requirement the delta adds (`evidence/surface-after.log`).

## What Changes

- `LoadingScreen` and its KDoc are deleted from `SafeScreen.kt`; `StartupScreensTest` loses
  `loadingScreenShowsLoadingState` and gains the absence case.
- Its two strings are deleted from `values/strings.xml` and `values-de/strings.xml`.
- The three `NavigationSession` comments that named it describe the mechanism ("a transient loading screen")
  instead of the removed class.
- `route-calculation-feedback` gains one requirement: one wait-notice implementation on the car display.

## Impact

- Affected capability: `route-calculation-feedback`.
- Affected code: `auto/src/main/java/com/naviveylin/auto/{SafeScreen,NavigationSession}.kt`,
  `auto/src/main/res/values{,-de}/strings.xml`, `auto/src/test/java/com/naviveylin/auto/{StartupScreensTest,
  GermanTranslationCompletenessTest}.kt`.
- No behaviour change: the class had no production caller, so no template the app can show changes.
