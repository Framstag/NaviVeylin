# build-jvm-toolchain — deltas

## ADDED Requirements

### Requirement: The project's own Kotlin sources call no deprecated quadratic path builder

The project's own Kotlin sources SHALL build a quadratic Bézier by the name the pinned Compose ui release
keeps — `quadraticTo`/`relativeQuadraticTo` — and SHALL use no `quadraticBezierTo`/`relativeQuadraticBezierTo`,
which Compose ui 1.7.6 deprecates and a later release removes. A scan of every `*.kt` the project owns SHALL
report no such call, so a returning one is a failing case rather than one warning among the compile's others.

#### Scenario: A marker outline is built with the kept builder

- **WHEN** the location marker builds the path of its rounded tip
- **THEN** the scan of the project's own `*.kt` files reports zero deprecated quadratic spellings, and its
  printed measurement names the file count it read
- **Case**: `app: ComposeApiDeprecationTest` "no project Kotlin source calls a deprecated quadratic path builder"

#### Scenario: The scan is still detectable

- **WHEN** a source uses one of the deprecated spellings, or names one only in a comment
- **THEN** the scan reports that line's file, number and text, and leaves the kept spelling and a comment that
  names the deprecated one alone — so a passing scan means the spelling is absent, not that the scan cannot
  see it
- **Case**: `app: ComposeApiDeprecationTest` "the scan is still detectable"
