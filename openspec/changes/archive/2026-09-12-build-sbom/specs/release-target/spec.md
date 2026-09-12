## ADDED Requirements

### Requirement: Release target emits SBOM per distribution flavor
The release target SHALL emit one CycloneDX JSON SBOM per distribution flavor
into the app module's output directory alongside the corresponding AAB.

#### Scenario: Release run produces SBOMs next to AABs
- **WHEN** the release target completes
- **THEN** the app module's output directory SHALL contain one SBOM next to the mobile AAB and one SBOM next to the automotive AAB

#### Scenario: SBOM output path is deterministic
- **GIVEN** two sequential release runs
- **WHEN** each run completes
- **THEN** the SBOM outputs SHALL appear at the same stable path per flavor, differing only in the version they report
