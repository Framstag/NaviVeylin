# build-jvm-toolchain — deltas

## ADDED Requirements

### Requirement: The build's scripts use no construct Gradle 10 removes

The project's own Kotlin DSL build scripts SHALL register tasks by naming them — `tasks.register("name") { }` —
and SHALL use no `by registering`/`by getting`/`by creating`/`by existing` property delegate, which Gradle
9.6.1 deprecates on every configuration and Gradle 10 removes. A scan of every `*.gradle.kts` the project owns
SHALL report no such delegate, so a returning one is a failing case rather than a warning nobody reads.

#### Scenario: A project script registers its tasks by name

- **WHEN** a module script registers a check or an SBOM task
- **THEN** the registration names the task explicitly and the scan of the project's own `*.gradle.kts` files
  finds no delegate, while every task keeps the name its CI invocation already uses
- **Case**: `app: GradleScriptDeprecationTest` "no project build script uses a removed Gradle construct"

#### Scenario: The removal is still detectable

- **WHEN** the deprecated delegate form returns to a script, or a script names it only in a comment
- **THEN** the scan reports each offending line's number and text, and leaves the named form beside a comment
  alone — so a passing scan means the construct is absent, not that the scan cannot see it
- **Case**: `app: GradleScriptDeprecationTest` "the removal is still detectable"
