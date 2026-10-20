# Design

## Root cause

The JNI-stub classloader rule is prose only. `guidelines/Build.md` §6 forbids `@Config(sdk=…)`/`@GraphicsMode(…)`
on any test class that loads the host stub, `guidelines/Design.md` §11 states it as a MUST, and about twenty
`:app` test classes restate it in their own KDoc — but no artefact read the sources against it, so one class,
`NavigationEngineAcquisitionTest.kt:38`, kept a `@Config(sdk = [34])` while instantiating `FakeOSMScoutClient`
(§128). Because a second sandbox only fails when the same JVM loads the stub in both classloaders, the pin was
latent and green, and the suite's verdict depended on fork order rather than on the rule.

## The single fix

1. Delete `@Config(sdk = [34])` and the then-unused `org.robolectric.annotation.Config` import from
   `NavigationEngineAcquisitionTest`; state the default-sandbox rule in its KDoc, as its siblings do.
2. Add `app/src/test/java/com/naviveylin/test/JniStubSandboxRuleTest.kt`, a plain JUnit case that reads the
   test sources of the two modules that ship the stub (`:app`, `:auto`), reports every line-anchored
   `@Config(…sdk…)`/`@GraphicsMode(…)` in a file that names the `com.framstag.libosmscout.client` package, and
   fails naming file and line. Five cases at round 1: the tree scan, a module-root presence check (no vacuous
   pass), and three controls (a pinned stub-loading fixture is reported; a pinned non-stub fixture and a
   `@Config(qualifiers=…)` on a stub fixture are not); the round-2 correction below adds the sixth.

One fix, no option left open: the annotation goes, and the rule becomes mechanical in the same artefact the gate
already runs.

## Round-2 correction (review refusal, item #81)

Round 1 measured only the `@Config(… sdk …)` half of the contract: the requirement refused two syntactic forms,
but the red case, the positive control and both negative controls used `@Config`, so
`sandboxPinHits`'s `GRAPHICS_PIN` branch never ran and Scenario 1's graphics trigger rested on the sdk trigger.
The branch is live, not hypothetical — `ui/map/CompassNeedleStrokeTest.kt:44` and
`ui/navigation/NavigationStatusRingRasterTest.kt:55` carry `@GraphicsMode(GraphicsMode.Mode.NATIVE)` today (they
name no client package, which is why the tree scan stays green).

Correction, one case and no production change: `aGraphicsModeOnAStubLoadingClassIsReported` — a fixture that
imports the client package and carries `@GraphicsMode(GraphicsMode.Mode.NATIVE)` at its fifth line — asserts one
finding ending in `5: @GraphicsMode(GraphicsMode.Mode.NATIVE)`. The delta gains that scenario, so each refused
form now has exactly one case. Falsified by mutating the branch (`val graphicsMode = false`): the new case fails
alone (`tests="6" failures="1"`), and it is green again with the branch restored, in both `:app` flavours.

## Alternatives the evidence eliminated

- **A buildSrc scanner with a Gradle `check…` task** (`checkNoWallClockWaits`'s shape): needs a scanner class and
  its fixtures in `buildSrc`, plus one wiring per module — the wall-clock task's own comment records that
  `:auto` and `:core` each needed their own build-file edit for the same scan. Its tests do not run in the
  project's gate (`./gradlew test`), and a Gradle task emits no JUnit result, so the rule's evidence would leave
  the gate's XML. Rejected as strictly larger for one annotation, and not falsifiable by a case.
- **Deleting the annotation alone**: leaves no red case (the defect is a rule the tree does not check), so the
  item's own mechanism — an undetected pin — survives, and the next pin is invisible again.
- **Keeping the pin and declaring a fork/sandbox exception** (the item's own "if it fails" branch): no collision
  was observed. The class is green with its six cases on the default sandbox in the focused run, and the gate
  runs both `:app` flavors over the whole class set — so there is no failure to convert into a fork-policy rule,
  and inventing one would contradict `guidelines/Build.md` §6's "do NOT set `@Config(sdk=...)`".
- **Moving the scanner into `buildSrc`'s `com.naviveylin.build.testing` beside `WallClockWaitScanner`**: the
  scanner would then be unreachable from the gate's test run for the same reason as the first alternative.

## Blast radius

- Two test files: one annotation + one import deleted (plus a KDoc paragraph), one new 6-case test class.
- No production source, no build script, no dependency, no native input, no flavour definition — the same class
  set runs in both `:app` flavours.
- The only behaviour that changes is which Robolectric sandbox the acquisition class gets: default (targetSdk 36
  under Robolectric 4.16) instead of SDK 34. Its six cases drive `LocationService`, `NavigationEngine` and
  `FakeOSMScoutClient` through shadows that exist on both levels.
- Suite-level risk: the removed pin is exactly what could have caused "already loaded in another classloader" in
  a full-suite run; the gate is the measurement that it does not.

## What the case does not cover (stated, not implied)

- **The properties form**: `auto/src/test/resources/robolectric.properties` sets `sdk=34` for the whole `:auto`
  module, and 33 `:auto` test files name the client package. The scan reads `*.kt` under `src/test/java` only, so
  that module-wide pin is out of the requirement's scope — deliberately, because widening the detector to
  `.properties` is a second mechanism and a second fix, not this one.
- **A `:auto`-only run**: the case lives in `:app`, so `./gradlew :auto:test` alone enforces nothing on `:auto`
  sources. The gate (`./gradlew test`) runs `:app` and `:auto`, which is the run that matters.
- **The default sandbox's SDK level**: the engine class's six green cases measure that removing the annotation
  broke nothing; they do not measure which level the default resolves to.
- **A multi-line `@Config(\n sdk = …)` and a stub reached without naming the client package**: outside the
  line-anchored, package-named form the requirement states.

## Rollback

Re-add `@Config(sdk = [34])` + the import, delete `JniStubSandboxRuleTest.kt`, drop the delta spec from
`unit-test-suite-runtime`. No state, schema or artefact depends on either file.
