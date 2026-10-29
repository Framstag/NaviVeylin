# Proposal — fix-nullability-warnings-in-two-test-sources

## Why

**The condition, verified in the tree at the run's base `cd09017` (2026-10-29).** Two unit-test sources carry
one nullability diagnostic each, and each is printed twice because `:app`'s mobile and automotive flavors
compile the same test tree:

```
app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt:131:78: Java type mismatch: inferred type is 'String?', but 'String' was expected.
app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt:51:20: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.
```

Measured with `./gradlew :app:compileMobileDebugUnitTestKotlin --rerun-tasks` and the same task for
`AutomotiveDebug`: each compile printed exactly those two `w:` lines. Four lines over two call sites, which is
the item's count; `evidence/compile-after-fix.log` is the same pair of compiles after the fix, and
`grep -c "^w: " evidence/compile-after-fix.log` reads `0`.

**Root cause, one sentence.** Both tests take a value whose Kotlin declaration is nullable
(`StartMapResolver.resolve(): String?` at `StartMapResolverTest.kt:125`; `java.io.File.parentFile: File?` at
`ShippedSpecClassificationTest.kt:51`) and hand it to a Java-declared non-null parameter
(`List<String>.contains`, `File.relativeTo`) without stating the non-null assumption the fixture already
guarantees, so the compiler warns that the argument may be null.

**Red test** (`evidence/red-on-head.xml`, run on the base tree at 2026-10-29T09:38:23.282Z): the new guard
`UnitTestSourceNullabilityBaselineTest` reports `tests="3" failures="2"` on HEAD, one finding per call site:

```
FINDING :51: .parentFile.r in app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt
FINDING :131: .contains(first) in app/src/test/java/com/naviveylin/data/StartMapResolverTest.kt
```

**Owning capability and guideline section.** The rule is owned by `openspec/specs/build-test-gate/spec.md`'s
warning family — the requirement this change adds, "The unit-test source sets name a nullable value's non-null
assumption" — and stated by `guidelines/Build.md` §4a "The verdict, the warnings and the failures", bullet
"Warnings: the build must have no warnings — report them, don't ignore", which `AGENTS.md`'s build row routes
a gate result to. (The package points at the code-coverage section of `Build.md`; the warnings rule lives in
§4a, cited above.)

**Surface.** The command

```
grep -rn "\.parentFile\.\|listOf([^)]*)\.contains(" --include=*.kt app/src/test auto/src/test core/src/test
```

reports exactly two lines on the base tree — the two call sites above — and none after the fix. No shipped
Gradle case asserts either spelling (the guard is new), no existing case is rewritten and no fixture is
realigned; the two files' own cases (9 and 1) keep passing and are re-run as the affected cases.

surface: 2 call sites fixed in 2 files, 1 guard class added (3 cases: 2 scans + 1 positive control), 0 existing cases rewritten, 0 fixtures realigned — `grep -rn "\.parentFile\.\|listOf([^)]*)\.contains(" --include=*.kt app/src/test auto/src/test core/src/test` (`2` matches in `2` files on the base tree, `0` on this change's tree).

## What Changes

- `StartMapResolverTest.kt`: bind the fallback case's first resolution through `requireNotNull(resolver().resolve())`
  with a message naming the fixture (two installed databases), so the value handed to `List<String>.contains`
  is non-null.
- `ShippedSpecClassificationTest.kt`: bind `requireNotNull(spec.parentFile)` with a message naming the walked
  `spec.md` before `relativeTo(specsDir)`.
- Add `app/src/test/java/com/naviveylin/build/UnitTestSourceNullabilityBaselineTest.kt`: two source-scan cases
  holding each family absent, plus a positive control that the scan refuses the unasserted forms and accepts
  the stated ones.
- Append one bullet to `guidelines/Build.md` §4a naming the guard and the measurement, beside the other
  source-set warning bullets.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-test-gate`: adds the requirement "The unit-test source sets name a nullable value's non-null
  assumption" with one scenario per warning family, each naming the guard case that holds it.
