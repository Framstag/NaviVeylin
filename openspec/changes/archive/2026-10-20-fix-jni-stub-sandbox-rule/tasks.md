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
  **Done:** `BUILD SUCCESSFUL in 3m 30s` (2026-10-20T13:40:55Z start, gate log retained as
  `evidence/green/gate-81.log`), 711 suite XMLs across the five test tasks (279 `:app` mobile, 279 `:app`
  automotive, 85 `:auto`, 60 `:core`, 8 `:osmscout-client-java`; re-derived from the gate's XMLs, because the
  round's focused runs later replaced the two `:app` result directories), `grep -c 'already loaded in another
  classloader'` = **0**. Tallies from the XML:
  `:app` mobile `tests=2071 skipped=0 failures=0 errors=0`; `:app` automotive `tests=2071 failures=0`;
  `:auto` `tests=866 failures=0`; `:core` `tests=642 failures=0`; `:osmscout-client-java` `tests=48 failures=0`
  — 5698 tests, 0 failures, 0 errors. The two changed classes in both `:app` flavours:
  `NavigationEngineAcquisitionTest tests="6" failures="0"` (mobile ts `2026-10-20T13:41:19.019Z`, automotive ts
  `2026-10-20T13:43:01.165Z`) and `JniStubSandboxRuleTest tests="5" failures="0"` (mobile ts
  `2026-10-20T13:41:01.814Z`, automotive ts `2026-10-20T13:43:09.343Z`), each with its automotive sibling.
  Retained: `evidence/green/{Mobile,Automotive}-com.naviveylin.*.xml`.

## 5. Evidence and archive

- [x] 5.1 Copy every cited XML into `evidence/green/` and into `.pi/issue-loop/items/81/`.
- [x] 5.2 `openspec validate fix-jni-stub-sandbox-rule --strict` and archive; confirm the delta merged into
  `openspec/specs/unit-test-suite-runtime/spec.md`.
  **Done:** `Change 'fix-jni-stub-sandbox-rule' is valid`; archived as `2026-10-20-fix-jni-stub-sandbox-rule`,
  `unit-test-suite-runtime: + 1 added`, and the requirement is at
  `openspec/specs/unit-test-suite-runtime/spec.md:374` with the six case names.

## 6. Round-2 correction: the second refused form gets a case

- [x] 6.1 The review round refused the change: the requirement refuses `@Config(… sdk …)` **and**
  `@GraphicsMode(…)`, but no case executed the detector's graphics branch (review `BLOCKING: 1`). Add one case,
  `JniStubSandboxRuleTest.aGraphicsModeOnAStubLoadingClassIsReported`: a fixture that imports the client package
  and carries `@GraphicsMode(GraphicsMode.Mode.NATIVE)` at line 5 is reported exactly once, with the line named.
  **Done:** the class runs six cases, all green in both `:app` flavours — mobile `tests="6" failures="0"` (ts
  `2026-10-20T13:48:04.022Z`), automotive `tests="6" failures="0"` (ts `2026-10-20T13:48:09.316Z`), with
  `system-out` `graphicsModeControl=[fixture/NativeGraphicsStubTest.kt:5: @GraphicsMode(GraphicsMode.Mode.NATIVE)]`.
  Retained: `evidence/green/round2-Mobile-…JniStubSandboxRuleTest.xml`, `evidence/green/round2-Automotive-…`.

- [x] 6.2 Falsify the new branch: with `val graphicsMode = false` in `sandboxPinHits` the new case is the only
  failure — `tests="6" failures="1" errors="0"`, ts `2026-10-20T13:47:53.932Z`, message `the detector must
  report a stub-loading class that pins a graphics mode`. Restored, the class is green again (6.1). No marker
  or mutation is left in the tree.
  **Done:** retained as `evidence/red/round2-jnistubsandbox-JniStubSandboxRuleTest.xml`.

- [x] 6.3 Say what the case does not cover, where the reader looks: the delta requirement and its
  `:auto`-only / properties-file / line-anchored scope (`unit-test-suite-runtime`), the class KDoc,
  `guidelines/Build.md` §6's pointer, and `design.md`'s "What the case does not cover".
  **Done:** the delta's scope paragraph names the `*.kt`-under-`src/test/java` scope and the out-of-scope
  properties form; Scenario 7's THEN claims only that the six cases pass, not which level the default sandbox
  resolves to.

- [x] 6.4 Repair this change's own record: task 4.1's "558 suite XMLs" was only the two `:app` flavour suites
  (279 + 279) where the gate's five test tasks produced 711, and its two timestamps were attributed to the
  wrong class and flavour. The gate log is copied into `evidence/green/` so the tallies outlive `/tmp`.
  **Done:** 4.1 corrected; `evidence/green/gate-81.log` retained.
