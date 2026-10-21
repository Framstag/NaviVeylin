## ADDED Requirements

### Requirement: The `:auto` unit-test source set carries no predetermined type, constant condition or unchecked cast

The `:auto` unit-test source set the gate compiles SHALL carry no construct whose value the Kotlin compiler
proves at the call site, so a gate run reports no `Type … is final, so the value of the type parameter is
predetermined`, `Condition is always 'true'` or `Unchecked cast of …` diagnostic from
`auto/src/test/java`. Each construct is named by the scenario that refuses it.

#### Scenario: A type parameter's bound leaves its value open

- **WHEN** the `:auto` unit-test sources are scanned for a type-parameter bound whose named class is declared
  final in a main source root (`auto/src/main/java`, `app/src/main/java`, `core/src/main/java`)
- **THEN** no bound names such a class, and the same finder reports the sample `T : AutoMapRenderer` — exercised
  by `AutoTestSourceWarningBaselineTest.no type parameter is bounded by a class that cannot be extended`

#### Scenario: A block that only throws is compared against null with its type named

- **WHEN** the `:auto` unit-test sources are scanned for a `runIteration` call whose block throws
  unconditionally and whose result is compared against `null`
- **THEN** every such call names a type argument, so the comparison asks what the test means, and the same
  finder reports the unnamed sample and clears the `<Unit>` one — exercised by
  `AutoTestSourceWarningBaselineTest.a runIteration comparison against null names the block type`

#### Scenario: A cast to a parameterized type is checked

- **WHEN** the `:auto` unit-test sources are scanned for a cast to a parameterized type
- **THEN** no cast carries a concrete type argument (`as Array<Any?>`), while a star projection
  (`as Array<*>`) and an `as?` cast pass, and the finder reports the first and clears the second — exercised
  by `AutoTestSourceWarningBaselineTest.no auto test source casts to a parameterized type unchecked`
