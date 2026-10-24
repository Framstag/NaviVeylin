# route-calculation-feedback — deltas

## ADDED Requirements

### Requirement: One wait-notice implementation on the car display

The car display SHALL have exactly one transient wait-notice screen, the route-calculation notice
(`RouteCalculatingScreen`). A retired wait screen SHALL leave no class, no caller and no user-facing string
behind, and a comment SHALL describe the mechanism it teaches rather than name the removed class.

#### Scenario: The retired wait screen is not on the classpath

- **WHEN** the `:auto` module's compiled classes are loaded
- **THEN** `com.naviveylin.auto.LoadingScreen` SHALL NOT resolve
- **Case**: `StartupScreensTest.retiredLoadingScreenClassIsGone`

#### Scenario: The retired wait screen's text is gone

- **WHEN** the `:auto` module's default string resources are read
- **THEN** the keys `loading_map_data` and `preparing_navigation` SHALL be absent
- **Case**: `GermanTranslationCompletenessTest.retiredWaitNoticeStringsAreGone`
