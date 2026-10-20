# Evidence — fix-qualifier-use-site-target

Every file here was produced in the change root (`.pi/worktrees/loop`) on base commit
`3211d4d8b395c61041dfccff9330e754dfb3499b`, on 2026-10-20/21.

| file | what it is |
|---|---|
| `head-compile-warnings.log` | `:app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin --rerun-tasks --no-build-cache -Pkotlin.incremental=false` on HEAD: the `value parameter only … field` family appears twice, both at `NavigationNotificationController.kt:36:5` — one per flavor, because both flavors compile the same `app/src/main/java` |
| `head-red-run.log` | the first red run of the guard (wider scan surface, superseded by `red-run-final.log`): `2 tests completed, 1 failed`, the failure naming the controller's line 36 |
| `red-run-final.log` | the red run of the guard's final revision: `QualifierUseSiteTargetTest > no qualifier on an injected property relies on the implicit target FAILED`, `2 tests completed, 1 failed` |
| `revert-check-mutated.xml` | the same run's XML (`@ApplicationContext` restored by the revert check — HEAD's exact line): `tests="2" failures="1"`, `sources=716 findings=1`, `2026-10-20T22:46:46.741Z`. `red-run-final.xml` is a copy under the red-run name |
| `green-run-test.xml` | the same two cases after `@param:`: `tests="2" failures="0"`, `sources=716 findings=0`, `2026-10-20T22:46:53.028Z` |
| `javap-constructor-bare.txt`, `javap-constructor-param.txt` | `javap -v -p` of `NavigationNotificationController.class` compiled from the bare form and from the `@param:` form; both carry `RuntimeVisibleParameterAnnotations: parameter 0: dagger.hilt.android.qualifiers.ApplicationContext` |
| `javap-diff.txt` | `diff -u` of the two dumps after the classfile header (`Last modified`, `SHA-256 checksum`): **empty** — the annotation's effect is unchanged, the classfile below its header is identical |
| `probe-qualifier-targets.py` | the standalone probe the guard's numbers were taken with before the case existed: `python3 probe-qualifier-targets.py .` on HEAD printed `scanned=718 findings_in_constructor=1 findings_whole_file=1` and named `NavigationNotificationController.kt:36` — the two variants agree, so no qualifier sits on a property outside a constructor |
| `probe-ctor-annotations.py` | the first probe, which counted every annotation on a constructor parameter (`findings=10`): it showed that restricting the family to the injection qualifiers is what separates this site from `@SerialName`'s property-targeted sites, which do not warn |
| `gate/gate-tally.txt` | the gate run's per-module tally: `tests=5704 failures=0 errors=0` over the five suites, `BUILD SUCCESSFUL in 3m 40s` |
| `gate/gate-app-mobile-QualifierUseSiteTargetTest.xml`, `gate/gate-app-automotive-QualifierUseSiteTargetTest.xml` | the guard case inside the two `:app` flavor suites of the gate run: `tests="2" failures="0"`, `sources=716 findings=0` in each |
| `gate/gate-console.log` | the gate run's console: `BUILD SUCCESSFUL in 3m 40s`, 189 tasks (25 executed), 0 lines matching `value parameter only` |

The red and green runs of the guard are the filtered invocation
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.QualifierUseSiteTargetTest"
-PforceTests --no-build-cache`, which is a pre-gate run, not gate evidence; the gate is
`./gradlew test -PforceTests --no-build-cache`, run once on the final tree (`gate/`). It was run once before
that as well (3m 39s, same `tests=5704 failures=0`); that run is superseded because the guard's scan surface
was narrowed afterwards from every `*.kt` under `app/`, `auto/`, `core/` to the six source sets the gate
compiles — the submodule's own Kotlin module is excluded because the project never patches the submodule.

The `values` in this change's prose (`sources=716`, `findings=1`/`0`, the two timestamps) are read out of the
XMLs above, not from a console that hides test stdout.
