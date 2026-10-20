# Revert check — the guard notices a returning implicit qualifier target

The invariant is "a qualifier applied to an injected property names its use-site target". The guard holds its
absence; the revert check mutates the tree back to the form the guard exists for and records what notices it.

## The mutation

`app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`, one line, restored to
HEAD's text:

```kotlin
-    @param:ApplicationContext private val context: Context,
+    @ApplicationContext private val context: Context,
```

The tree was otherwise identical (nothing reverted, no test edited) — so the mutation is exactly the defect
the item names.

## What noticed it

```
$ ./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.QualifierUseSiteTargetTest' \
    -PforceTests --no-build-cache
> Task :app:testMobileDebugUnitTest FAILED
QualifierUseSiteTargetTest > no qualifier on an injected property relies on the implicit target FAILED
    java.lang.AssertionError: a qualifier applied to an injected property must name its use-site target —
      write `@param:ApplicationContext` instead of `@ApplicationContext`, because the unqualified form is
      applied to the value parameter only and a later Kotlin default will also apply it to the field
      expected:<[]> but was:<[:36: @ApplicationContext private val context: Context, in
      app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt]>
2 tests completed, 1 failed
```

XML: `revert-check-mutated.xml` — `tests="2" skipped="0" failures="1" errors="0"`,
`timestamp="2026-10-20T22:46:46.741Z"`, `system-out`: `sources=716 findings=1`; the finding names the file and
the line, so a returning site is attributable without reading the tree.

The other case, `the implicit target is still detectable`, passes in the mutated run: it falsifies the
detector, not the tree.

## Restoration

`@param:ApplicationContext` put back on the same line; the same invocation re-run forced:

```
BUILD SUCCESSFUL in 3s
```

XML: `green-run-test.xml` — `tests="2" failures="0"`, `timestamp="2026-10-20T22:46:53.028Z"`,
`system-out`: `sources=716 findings=0`.

## The wider falsification, run separately

The same mutation was also run under the detector's fixture case before the guard's scan surface was narrowed:
the three bare property lines are named, and `@param:`, `@field:`, a bare method parameter, a comment that
names the refused form, and an unqualified constructor are left alone (`the implicit target is still
detectable`, green in every run above and in the gate's two flavor XMLs).
