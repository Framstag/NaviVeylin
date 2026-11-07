## ADDED Requirements

### Requirement: The unit-test source sets name a nullable value's non-null assumption

A unit-test source SHALL state a nullable value's non-null assumption where a Kotlin-nullable value meets a
Java-declared non-null parameter, or where a nullable receiver is dereferenced: through `requireNotNull(…)`
naming the fixture the case knows is present, a non-null assertion, or a safe call. The nullability
diagnostics the four unit-test compiles carried on 2026-10-29 SHALL stay absent from the unit-test source sets,
and a guard case SHALL hold each family absent.

#### Scenario: A resolved start map is named non-null before a membership check

- **WHEN** a unit test binds the result of a `…resolve()` call, whose declaration returns `String?`, and asks
  whether a `List<String>` contains it
- **THEN** the test binds it through `requireNotNull(…)` whose message names the fixture, so the compile emits
  no `Java type mismatch` line, and `UnitTestSourceNullabilityBaselineTest` — "no unit test feeds an
  unasserted start map resolution into a membership check" — fails if the nullable binding returns

#### Scenario: A nullable parent directory is dereferenced only behind a safe call or an assertion

- **WHEN** a unit test reads `File.parentFile`, whose declaration returns `File?`, and calls a member on it
- **THEN** the read is a safe call, a non-null assertion, or `requireNotNull(…)` whose message names the
  fixture, so the compile emits no `Only safe (?.) …` line, and `UnitTestSourceNullabilityBaselineTest` —
  "no unit test dereferences a nullable parent directory without a safe call or an assertion" — fails if the
  bare dereference returns
