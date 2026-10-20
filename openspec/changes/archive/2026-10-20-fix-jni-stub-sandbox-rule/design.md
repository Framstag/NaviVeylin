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
   fails naming file and line. Five cases: the tree scan, a module-root presence check (no vacuous pass), and
   three controls (a pinned stub-loading fixture is reported; a pinned non-stub fixture and a
   `@Config(qualifiers=…)` on a stub fixture are not).

One fix, no option left open: the annotation goes, and the rule becomes mechanical in the same artefact the gate
already runs.

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

- Two test files: one annotation + one import deleted (plus a KDoc paragraph), one new 5-case test class.
- No production source, no build script, no dependency, no native input, no flavour definition — the same class
  set runs in both `:app` flavours.
- The only behaviour that changes is which Robolectric sandbox the acquisition class gets: default (targetSdk 36
  under Robolectric 4.16) instead of SDK 34. Its six cases drive `LocationService`, `NavigationEngine` and
  `FakeOSMScoutClient` through shadows that exist on both levels.
- Suite-level risk: the removed pin is exactly what could have caused "already loaded in another classloader" in
  a full-suite run; the gate is the measurement that it does not.

## Rollback

Re-add `@Config(sdk = [34])` + the import, delete `JniStubSandboxRuleTest.kt`, drop the delta spec from
`unit-test-suite-runtime`. No state, schema or artefact depends on either file.
