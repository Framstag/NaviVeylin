# Proposal: AA Chrome Theme Is Host-Owned — Document the Constraint

## Why

A user report ("AA UI elements always black in daylight; map surface flips correctly") looked like an app-side bug but is actually upstream by design: on Android Auto (both projection and AAOS), template chrome — menus, dialogs, lists, panels, the navigation banner/ETA card — is rendered remotely by Google's host process. The car-app library (1.7.0) exposes **no** theme or wholesale-color API for chrome; the only app-side color lever is per-element `CarColor` on `Action` buttons. Day/night of the chrome is controlled by the host's own Display → Theme setting, not by the app. This change captures that constraint as durable documentation so future work does not chase a non-existent app lever.

## What Changes

- **New spec requirement** on the existing `dark-mode` capability: Android Auto template chrome is host-rendered and host-themed; the system SHALL NOT theme or override host chrome, SHALL document the host-side `Settings > Display > Theme` switch as the control for it, and SHALL limit app-side color control to the app-drawn map surface (daylight flag) plus optional `CarColor.createCustom(light, dark)` accents on `Action` elements.
- **guidelines/UI.md** §9 (Dark mode) updated: add the chrome/surface ownership split and point users at the host Theme setting; clarify that the existing "templates follow the host automatically" line means host-driven, with Google's 2019–2025 dark-only default and the late-2025 light-theme rollout as context.
- **Design record** (design.md): the layer-ownership analysis — two rendering layers (host chrome vs app surface), both host paths (projection gearhead, AAOS Automotive App Host), the API-surface verification (car-app 1.7.0 AAR: only `Action` carries `CarColor`; `CarAppActivity` binds remote `IRendererService`), and the settings map.

Additive, documentation-only. **No production code, no manifest, no assets change.** Rollback: revert the doc edits; no runtime behavior ever changes.

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**:
  - `openspec/specs/dark-mode/spec.md` — add one requirement pinning the AA chrome ownership constraint and the host-side switch. No existing requirement changes (phone-side controls rule stays as-is; the AA surface-variant rule stays as-is).

## Impact

- **Docs**: `guidelines/UI.md` (§9 dark mode — scope note), this change's `design.md`.
- **Spec**: `openspec/specs/dark-mode/spec.md` (one ADDED requirement → delta at `specs/dark-mode/spec.md`).
- **Code**: none (`:auto`, `:app`, `NaviVeylinCarAppService`, manifest untouched).
- **Dependencies**: none.
- **Guidelines affected**: `guidelines/UI.md` §9; `guidelines/MapRendering.md` §15 referenced for the daylight-flag flow (unchanged).
- **Scope**: Android Auto integration only (both projection and AAOS flavors). Phone behavior unaffected (OS theming already correct per report).

## Open Questions

None — answered during exploration (host ownership verified at API level; host Theme setting is the control; AAOS host theme is OEM-pinned on many head units, no app lever either way).
