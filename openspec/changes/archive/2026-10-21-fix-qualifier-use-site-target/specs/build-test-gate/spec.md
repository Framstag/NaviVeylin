## ADDED Requirements

### Requirement: An injected property states its qualifier's use-site target

The Kotlin sources the gate compiles (`app/src/main/java`, `app/src/test/java`, `auto/src/main/java`,
`auto/src/test/java`, `core/src/main/java`, `core/src/test/java`) SHALL apply a qualifier annotation —
`@ApplicationContext`, `@ActivityContext` or `@Named` — to a property declaration only with an explicit
use-site target: `@param:` for the constructor value parameter the annotation means today.

#### Scenario: A qualifier on an injected property names `param:`

- **WHEN** the Kotlin sources are scanned for a qualifier applied to a `val`/`var` declaration without a
  use-site target
- **THEN** the scan reports no such site, so a gate run carries no `This annotation is currently applied to
  the value parameter only, but in the future it will also be applied to field` line, and the site that
  carried one (`NavigationNotificationController`'s constructor) names `@param:ApplicationContext` —
  exercised by `QualifierUseSiteTargetTest.no qualifier on an injected property relies on the implicit target`

#### Scenario: The implicit target is still detectable

- **WHEN** the scan is run over a bare `@ApplicationContext private val`, a bare `@Named("clock") val` and a
  bare `@ActivityContext var`, and over `@param:`, `@field:`, a bare annotation on a *method* parameter, a
  comment that names the refused form, and a constructor with no qualified parameter
- **THEN** it reports exactly the three bare property sites, one finding each, and nothing from the other
  five — so a passing scan means the form is absent, not that the scan cannot see it — exercised by
  `QualifierUseSiteTargetTest.the implicit target is still detectable`
