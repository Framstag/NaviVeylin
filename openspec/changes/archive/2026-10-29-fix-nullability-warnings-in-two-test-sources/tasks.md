# Tasks — fix-nullability-warnings-in-two-test-sources

## 1. Red on HEAD

- [x] 1.1 Record the two diagnostic lines on the base tree: run
      `./gradlew :app:compileMobileDebugUnitTestKotlin --rerun-tasks` and the same task for
      `AutomotiveDebug`, requiring one `w:` line per family in each compile, at
      `StartMapResolverTest.kt:131:78` and `ShippedSpecClassificationTest.kt:51:20`.
- [x] 1.2 Add `app/src/test/java/com/naviveylin/build/UnitTestSourceNullabilityBaselineTest.kt` with the two
      scan cases and the positive control, then
      `./gradlew :app:testMobileDebugUnitTest -PforceTests -PnoCoverage --no-build-cache --tests "com.naviveylin.build.UnitTestSourceNullabilityBaselineTest"`.
      Requires `3 tests completed, 2 failed`, a FINDING at `:51` and one at `:131`, and the XML
      `tests="3" failures="2"` at `2026-10-29T09:38:23.282Z` (`evidence/red-on-head.xml`).
      *(spec: `build-test-gate` — A resolved start map is named non-null before a membership check; A
      nullable parent directory is dereferenced only behind a safe call or an assertion)*

## 2. The fix

- [x] 2.1 `StartMapResolverTest.kt`: bind the fallback case's first resolution through
      `requireNotNull(resolver().resolve())` with a message naming the fixture (two installed databases), and
      leave the existing membership assertion reading that non-null value.
- [x] 2.2 `ShippedSpecClassificationTest.kt`: bind `requireNotNull(spec.parentFile)` with a message naming the
      walked `spec.md` before `relativeTo(specsDir)`.

## 3. Verification

- [x] 3.1 Re-run the red guard and the two affected cases in one invocation — same Gradle call plus
      `--tests "com.naviveylin.data.StartMapResolverTest" --tests "com.naviveylin.featurelist.ShippedSpecClassificationTest"`.
      Requires `BUILD SUCCESSFUL` and the three XMLs `tests="3" failures="0"`, `tests="9" failures="0"`,
      `tests="1" failures="0"` (`evidence/green-guard.xml`, `evidence/green-start-map-resolver.xml`,
      `evidence/green-shipped-spec.xml`).
      *(spec: `build-test-gate` — the two scenarios above)*
- [x] 3.2 Confirm the families are gone from the compile: re-run both `compile…UnitTestKotlin` tasks with
      `--rerun-tasks` and require `grep -c "^w: "` to read `0` over the two logs
      (`evidence/compile-after-fix.log`).
- [x] 3.3 Append one bullet to `guidelines/Build.md` §4a beside the other source-set warning bullets, naming
      `UnitTestSourceNullabilityBaselineTest` and the measured four lines over two call sites, and run
      `tools/check-doc-routes.sh` to require the pointer resolves.
- [x] 3.4 Run `bash tools/loop/evidence-check.sh openspec/changes/fix-nullability-warnings-in-two-test-sources --root .`
      and `openspec validate fix-nullability-warnings-in-two-test-sources --strict`; require no dangling
      pointer and a valid change.
- [x] 3.5 Run the project's gate once from the change root, `./tools/loop/gate.sh`, because the change touches
      `*/src/*` and `*.md`; quote its `LOOP-GATE` line and copy each cited tally into `evidence/`.

## 4. Archive

- [x] 4.1 Archive with `openspec archive fix-nullability-warnings-in-two-test-sources`, confirm the delta
      merged into `openspec/specs/build-test-gate/spec.md`, and re-run the evidence check against the archived
      path.
