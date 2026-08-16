# map-render Specification (Delta)

## ADDED Requirements

### Requirement: Stylesheets sourced from submodule at build time

The system SHALL package the stylesheets from the libosmscout submodule stylesheet directory (`app/src/main/cpp/libosmscout/stylesheets/`) into the APK as they exist at build time — the current submodule checkout state. The system SHALL NOT package a committed stylesheet snapshot. The APK asset root `stylesheets/` SHALL contain exactly the files of the submodule stylesheet directory (all `*.ost`, `*.oss`, and `include/` files).

- The build SHALL read the stylesheets from the submodule working tree, not from `app/src/main/assets/stylesheets/`
- A submodule bump SHALL change the APK stylesheet content without any additional copy step
- If the submodule stylesheet directory is missing, the build SHALL fail with an actionable error message

#### Scenario: Submodule bump changes APK content

- **WHEN** the submodule is updated to a commit whose `stylesheets/` content differs
- **THEN** the next build SHALL package the new content without any manual snapshot copy

#### Scenario: No committed snapshot involved

- **WHEN** the build runs with no `app/src/main/assets/stylesheets/` directory present
- **THEN** the APK SHALL still contain the full stylesheet set from the submodule

#### Scenario: Missing submodule fails the build

- **WHEN** the submodule stylesheet directory does not exist (fresh clone, submodule not initialized)
- **THEN** the build SHALL fail before compilation
- **THEN** the failure message SHALL instruct the user to run `git submodule update --init --recursive`

### Requirement: Stylesheet refresh on device

The system SHALL refresh the internal-storage stylesheet copy from bundled assets on every app start, so app updates deliver stylesheet changes without requiring data clearing or reinstall.

- Each bundled stylesheet SHALL be compared against the internal-storage copy (size + content hash) and copied when different
- Bundled stylesheet files missing from internal storage SHALL be copied (first launch and after file loss)
- Internal-storage stylesheet files no longer bundled SHALL be deleted (mirror semantics)
- Unchanged files SHALL NOT be rewritten (no unnecessary I/O)

#### Scenario: Existing install receives upstream changes

- **WHEN** the app is updated to an APK whose bundled stylesheets differ from the internal-storage copy
- **THEN** on next app start the changed stylesheets SHALL be copied to internal storage
- **THEN** the map SHALL render with the updated styles without any user action

#### Scenario: No-change startup is a no-op

- **WHEN** the app starts and the internal-storage stylesheets match the bundled assets
- **THEN** no stylesheet file SHALL be rewritten
- **THEN** the existing stylesheet directory path SHALL be returned unchanged

#### Scenario: Stylesheet file removed upstream

- **WHEN** a stylesheet file is removed from the bundled set after an app update
- **THEN** the corresponding internal-storage file SHALL be deleted on next app start
