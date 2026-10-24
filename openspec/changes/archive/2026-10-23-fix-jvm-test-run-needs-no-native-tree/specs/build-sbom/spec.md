## ADDED Requirements

### Requirement: A JVM-only test run does not require the native tree

A unit-test invocation SHALL configure and run without a vcpkg checkout. The licence inventory SHALL stay the
variant asset source of every packaging invocation, a missing vcpkg tree SHALL fail the native SBOM task that
reads it rather than the configuration of the build, and an invocation that packages or checks the policy SHALL
be refused when it asks for a JVM-only test run.

#### Scenario: A test run configures without a vcpkg tree

- **WHEN** the unit-test tasks are requested with the JVM-only property and `VCPKG_ROOT` names a directory that
  holds no installed vcpkg tree
- **THEN** the invocation SHALL configure (exit 0) and its graph SHALL hold no licence-asset, SBOM or native-SBOM
  task
- **Case**: `buildGraph#aJvmOnlyTestRunNeedsNoNativeTree` (`tools/check-jvm-test-graph-selftest.sh`)

#### Scenario: The property is refused for a packaging invocation

- **WHEN** the JVM-only property is requested together with a task that packages the application or checks the
  licence policy
- **THEN** the build SHALL fail before any task runs, naming the property and the packaging task it refused
- **Case**: `buildGraph#aPackagingGraphRefusesTheProperty` (`tools/check-jvm-test-graph-selftest.sh`)

#### Scenario: A missing tree fails its own task

- **WHEN** the native SBOM task is requested without the JVM-only property in a tree that has no vcpkg status file
- **THEN** the configuration SHALL succeed and the task SHALL fail naming the missing installed-status file, not
  as a task-creation failure
- **Case**: `buildGraph#aMissingNativeTreeFailsItsOwnTask` (`tools/check-jvm-test-graph-selftest.sh`)

#### Scenario: The toggle is read like the other build switches

- **WHEN** the JVM-only property carries a value the build cannot read
- **THEN** the build SHALL refuse that value with a message naming the property and both accepted forms
- **Case**: `JvmOnlyTestRunTest#anUnreadableValueIsRefusedWithActionableText` (`buildSrc`)
