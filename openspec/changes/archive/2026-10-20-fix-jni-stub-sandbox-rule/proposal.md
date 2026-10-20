# Proposal

## Why

Root cause: the JNI-stub classloader rule lives only in prose — `guidelines/Build.md` §6 ("Any test class that
instantiates `FakeOSMScoutClient` … MUST run under `@RunWith(RobolectricTestRunner::class)` with the DEFAULT
sandbox config — do NOT set `@Config(sdk=...)` or `@GraphicsMode(...)`", the host stub in
`app/src/test/jniLibs/libosmscout_client_java.so` being loadable in one classloader only) and
`guidelines/Design.md` §11 ("one sandbox config per JVM") — and no artefact compared that rule to the sources.
A stub-loading class could therefore pin a second sandbox undetected: `app/src/test/…/NavigationEngineAcquisitionTest.kt:38`
carried `@Config(sdk = [34])` while instantiating `FakeOSMScoutClient` at `:108`, `:145`, `:161`, `:187`, `:219`
(item §128). Every other engine test in the same package states the default sandbox in its KDoc and sets
nothing. The suite is green today because the collision the rule describes ("already loaded in another
classloader") depends on which fork executes which class, so the pin is a latent suite-wide failure, not a
failing case.

Evidence: `com.naviveylin.test.JniStubSandboxRuleTest#noTestClassThatLoadsTheStubPinsTheSandbox` fails on the
base commit `3c1ecc7` — XML `tests="5" skipped="0" failures="1" errors="0"`,
`timestamp="2026-10-20T13:40:00.186Z"`,
`java.lang.AssertionError: a class that loads the host stub must run in the default Robolectric sandbox
(guidelines/Build.md §6) — these pin one: [app/src/test/java/com/naviveylin/navigation/NavigationEngineAcquisitionTest.kt:38:
@Config(sdk = [34])]`. The red XML is retained at
`.pi/issue-loop/items/81/red-jnistubsandbox-JniStubSandboxRuleTest.xml` and copied into this change's
`evidence/red/`; after the fix the same class is green (`tests="5" failures="0"`, ts
`2026-10-20T13:41:01.814Z`).

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.JniStubSandboxRuleTest"`.

Spec: `unit-test-suite-runtime` / "A test class that loads the JNI stub declares no sandbox" (added by this
change)   Guideline: `guidelines/Build.md` §6 "Test constraints" (the JNI stub / classloader rule),
`guidelines/Design.md` §11 "Testing" (one sandbox config per JVM)

## What Changes

- **`NavigationEngineAcquisitionTest` loses the pin**: `@Config(sdk = [34])` and its now-unused
  `org.robolectric.annotation.Config` import are deleted, so the class runs in the default sandbox like its
  siblings, and its KDoc states that rule. Its six cases are unchanged and stay green.
- **A host case holds the rule to the sources** (`app/src/test/java/com/naviveylin/test/JniStubSandboxRuleTest.kt`):
  it reads the test sources of both stub-shipping modules (`:app`, `:auto`), reports every line-anchored
  `@Config(…sdk…)` / `@GraphicsMode(…)` in a file that names the client package, and fails naming file and
  line. Controls prove the detector is neither vacuous nor over-reaching: a pinned stub-loading fixture is
  reported, a `@GraphicsMode(GraphicsMode.Mode.NATIVE)` stub-loading fixture is reported through the second
  refused form, a pinned class that loads no stub and a `@Config(qualifiers = …)` on a stub class are allowed,
  and a further case fails if a module root was not read.
- **`unit-test-suite-runtime` gains one requirement** with its scenarios, one per case above.
- Additive, not breaking: no production code, no API, no persisted state. Rollback is the reversion of two
  files, plus the delta spec.

## Capabilities

### New Capabilities
<!-- none: the delta lands in the existing unit-test-suite-runtime capability -->

### Modified Capabilities
- `unit-test-suite-runtime`: a requirement is ADDED — a test class that loads the host JNI stub declares no
  Robolectric sandbox pin, refused by a case that names the file and line. Every existing requirement of the
  capability is unchanged.

## Impact

- **Test code**: `app/src/test/java/com/naviveylin/navigation/NavigationEngineAcquisitionTest.kt` (annotation +
  import removed, KDoc extended), `app/src/test/java/com/naviveylin/test/JniStubSandboxRuleTest.kt` (new, 6
  cases — five from the fix, one added by the review round for the `@GraphicsMode` form).
- **No production code, no build script, no dependency.** The gate is `./gradlew test -PforceTests
  --no-build-cache`; the full `:app` suite is also the only measurement that can show the removed pin caused no
  "already loaded in another classloader" failure.
- **Specs**: `openspec/specs/unit-test-suite-runtime/spec.md` gains the requirement on archive.
