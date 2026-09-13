# dark-mode Specification (delta)

## ADDED Requirements

### Requirement: Android Auto template chrome is host-owned and host-themed

On Android Auto (projection and Android Automotive OS), the system SHALL NOT theme, color, or otherwise override the host-rendered template chrome (menu, dialog, list, and panel backgrounds; navigation banner; ETA card; headers). Template chrome day/night SHALL follow the host's own display theme control (`Android Auto > Settings > Display > Theme`, or the head-unit/AAOS system theme), which the app cannot influence. App-side color control SHALL be limited to the app-drawn map surface (the `daylight` stylesheet flag driven by the host's day/night state) and, optionally, per-element custom colors on `Action` elements (`CarColor.createCustom(light, dark)`).

#### Scenario: Host chrome switches theme without app code
- **WHEN** the host display theme is set to **Light** (or the head unit reports a light automotive theme)
- **THEN** the host renders template chrome (menus, dialogs, lists, panels, navigation banner) in a light scheme with no app-side change

#### Scenario: Host chrome stays dark
- **WHEN** the host display theme is set to **Dark** (or the head unit's Automotive App Host theme is dark-pinned)
- **THEN** template chrome remains dark regardless of any app-side attempt, and the app SHALL rely on the host setting (or host rollout) instead of overriding

#### Scenario: Map surface still switches independently
- **WHEN** the carrier host reports day state (e.g., `onCarConfigurationChanged` with night UI mode unset)
- **THEN** the app-drawn map surface SHALL render the daylight stylesheet variant (daylight flag set) even while chrome follows the host theme

#### Scenario: Day/night preference does not leak into host chrome
- **WHEN** the user changes the app's dark mode preference (On / Off / Automatic)
- **THEN** only the app-drawn surface and app controls are affected; host template chrome is unchanged
