## ADDED Requirements

### Requirement: A test source declares the opt-in its experimental time control needs

A unit-test or test-helper source that imports a `kotlinx.coroutines.test` declaration outside the pinned
library's stable set — `runTest`, `TestScope`, `TestDispatcher`, `StandardTestDispatcher`,
`TestCoroutineScheduler` — SHALL name `ExperimentalCoroutinesApi` in an `@OptIn` or `@file:OptIn`
declaration, which the compiler otherwise demands at every call site. A test case SHALL refuse such a source
and name it, with no exception list. The rule is the counterpart of `guidelines/Build.md` §4.

#### Scenario: A test source without the declaration is named

- **WHEN** a test source imports and calls the experimental time control without naming the opt-in
- **THEN** the case fails and names that source — exercised by
  `CoroutinesTestOptInGateTest.aTestSourceThatUsesTheExperimentalTimeControlWithoutTheOptInIsNamed`

#### Scenario: The test-bearing sources need no missing-opt-in warning

- **WHEN** the case is run against the test sources of the units the four unit-test compile tasks cover
  (`:app`, `:auto`, `:core`)
- **THEN** it reports no source — an imported experimental name that is never called is a finding too, so
  such an import is dropped rather than declared — exercised by
  `CoroutinesTestOptInGateTest.everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn`
