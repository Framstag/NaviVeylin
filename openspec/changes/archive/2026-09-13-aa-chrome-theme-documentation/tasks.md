# Tasks

## 1. Spec delta

- [x] 1.1 Add the ADDED requirement "Android Auto template chrome is host-owned and host-themed" to `specs/dark-mode/spec.md` in this change (done above — verify present).

## 2. Guideline update

- [x] 2.1 Update `guidelines/UI.md` §9 (Dark mode): document the chrome/surface ownership split (host-rendered chrome follows the host's `Display > Theme` setting; app surface follows the host day/night signal via the daylight flag), and note the host-side switch as the control for chrome day/night (dark-only default 2019–2025, light theme rolling out from late 2025).
- [x] 2.2 Keep the existing wording that templates "follow the host automatically" — clarify it means host-driven, never app-driven.

## 3. Verification

- [x] 3.1 Run `openspec validate --change aa-chrome-theme-documentation` and fix any schema violations.
- [x] 3.2 Review pass: read the delta spec, proposal, and UI.md §9 diff for consistency with the layer-ownership diagram in design.md.

No build or on-device verification required — documentation-only change (no code, no manifest, no assets). Rollback = revert doc edits.
