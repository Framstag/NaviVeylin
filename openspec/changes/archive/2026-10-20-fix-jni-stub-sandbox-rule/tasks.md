# Tasks

## 1. Red case: the rule becomes mechanical

- [x] 1.1 Add `app/src/test/java/com/naviveylin/test/JniStubSandboxRuleTest.kt` (plain JUnit, no JNI) with the
  five cases of the delta: the tree scan, the module-root presence check and the three controls.
  **Done:** the case `noTestClassThatLoadsTheStubPinsTheSandbox` fails on the base commit `3c1ecc7` while
  `NavigationEngineAcquisitionTest` still carries the pin — `./gradlew :app:testMobileDebugUnitTest --tests
  "com.naviveylin.test.JniStubSandboxRuleTest" -PforceTests --no-build-cache`, XML
  `tests="5" skipped="0" failures="1" errors="0"` (ts `2026-10-20T13:40:00.186Z`), message
  `…these pin one: [app/src/test/java/com/naviveylin/navigation/NavigationEngineAcquisitionTest.kt:38: @Config(sdk = [34])]`.
  Retained: `evidence/red/jnistubsandbox-JniStubSandboxRuleTest.xml`.

## 2. Remove the sandbox pin

- [x] 2.1 Delete `@Config(sdk = [34])` and the unused `org.robolectric.annotation.Config` import from
  `NavigationEngineAcquisitionTest`, and state the default-sandbox rule in its KDoc.
  **Done:** the class keeps six cases, unchanged.

- [x] 2.2 Run the unpinned class together with the guard.
  **Done:** `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.JniStubSandboxRuleTest"
  --tests "com.naviveylin.navigation.NavigationEngineAcquisitionTest" -PforceTests --no-build-cache`,
  `BUILD SUCCESSFUL`; XML `NavigationEngineAcquisitionTest tests="6" failures="0"`,
  `JniStubSandboxRuleTest tests="5" failures="0"`.

- [x] 2.3 `guidelines/Build.md` §6 (the section that owns the rule) points at the case, so a reader of the rule
  finds the artefact that enforces it.
  **Done:** four lines added to the JNI stub / classloader bullet.

## 3. Falsification of the new invariant

- [x] 3.1 Re-insert the pin once (the case must fail), then restore it and re-run (the case must pass) —
  `revert-check`.
  **Done:** with the pin back, the same case fails and names the new line:
  `…NavigationEngineAcquisitionTest.kt:42: @Config(sdk = [34])`, XML `tests="5" failures="1"`; with the pin
  removed again the guard is green (`tests="5" failures="0"`). Retained:
  `evidence/red/jnistubsandbox-revert-check-JniStubSandboxRuleTest.xml`.

## 4. Gate

- [x] 4.1 Run the project's gate once — `./gradlew test -PforceTests --no-build-cache` — and quote the per-suite
  XML tallies: both `:app` flavours (the removed pin's only suite-level risk is an "already loaded in another
  classloader" failure, which only a full-suite run can show), `:auto`, `:core` and
  `:osmscout-client-java`. Grep the log for `already loaded in another classloader` and record the count.
  **Done:** `BUILD SUCCESSFUL in 3m 30s` (2026-10-20T13:40:55Z start, gate log `/tmp/gate-81.log`), 558 suite
  XMLs, `grep -c 'already loaded in another classloader'` = **0**. Tallies from the XML:
  `:app` mobile `tests=2071 skipped=0 failures=0 errors=0`; `:app` automotive `tests=2071 failures=0`;
  `:auto` `tests=866 failures=0`; `:core` `tests=642 failures=0`; `:osmscout-client-java` `tests=48 failures=0`
  — 5698 tests, 0 failures, 0 errors. The two changed classes in both `:app` flavours:
  `JniStubSandboxRuleTest tests="5" failures="0"` (mobile ts `2026-10-20T13:43:01.165Z`) and
  `NavigationEngineAcquisitionTest tests="6" failures="0"` (mobile ts `2026-10-20T13:43:09.343Z`), each with its
  automotive sibling. Retained: `evidence/green/{Mobile,Automotive}-com.naviveylin.*.xml`.

## 5. Evidence and archive

- [x] 5.1 Copy every cited XML into `evidence/green/` and into `.pi/issue-loop/items/81/`.
- [x] 5.2 `openspec validate fix-jni-stub-sandbox-rule --strict` and archive; confirm the delta merged into
  `openspec/specs/unit-test-suite-runtime/spec.md`.
  **Done:** `Change 'fix-jni-stub-sandbox-rule' is valid`; archived as `2026-10-20-fix-jni-stub-sandbox-rule`,
  `unit-test-suite-runtime: + 1 added`, and the requirement is at
  `openspec/specs/unit-test-suite-runtime/spec.md:373` with the five case names.
