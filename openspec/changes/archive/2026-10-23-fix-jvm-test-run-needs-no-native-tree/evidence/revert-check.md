# Revert-check — one mutation per invariant, the named case red, restored, green

Change `fix-jvm-test-run-needs-no-native-tree`, base `f5f2a2a76e3adc73e377b0bf2747718ac17dca76`, 2026-10-23.
Each mutation is applied alone, the named case must go red **for the reason the invariant states**, the file is
restored from a byte copy (`sha256sum` compared), and the case must be green again.

## M1 — the refusal of a packaging invocation (case `buildGraph#aPackagingGraphRefusesTheProperty`)

Mutation, in `buildSrc/src/main/kotlin/com/naviveylin/build/testing/JvmOnlyTestRun.kt`: `packagingTasksIn` stops
matching, i.e. no requested task is ever a packaging task:

```
    fun packagingTasksIn(requestedTasks: List<String>): List<String> = requestedTasks.filter { task ->
+        if (true) return emptyList()
         val name = task.substringAfterLast(':')
```

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aPackagingGraphRefusesTheProperty
FAIL buildGraph#aPackagingGraphRefusesTheProperty: a packaging graph accepted -PjvmTestsOnly (exit 0)

check-jvm-test-graph-selftest: 1 of 1 cases failed — buildGraph#aPackagingGraphRefusesTheProperty
exit 1
```

Restored (`sha256sum` of the file equals the copy taken before the mutation), then:

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aPackagingGraphRefusesTheProperty
check-jvm-test-graph-selftest: all 1 case passed (one --dry-run configuration, no build, no network)
exit 0
```

## M2 — the inventory's asset source left unwired by the property (case `buildGraph#aJvmOnlyTestRunNeedsNoNativeTree`)

Mutation, in `app/build.gradle.kts`: the guarded `if (jvmTestsOnly) return@onVariants` is deleted, so the licence
asset source is wired for a JVM-only invocation too.

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aJvmOnlyTestRunNeedsNoNativeTree
FAIL buildGraph#aJvmOnlyTestRunNeedsNoNativeTree: the JVM-only graph still holds the SBOM/licence chain:
     :app:generateLicenseAssetsMobileDebug :app:generateSbomJvmMobileDebug :app:generateSbomMobileDebug
     :app:mergeNativeSbom

check-jvm-test-graph-selftest: 1 of 1 cases failed — buildGraph#aJvmOnlyTestRunNeedsNoNativeTree
exit 1
```

Restored (`sha256sum` equal), then:

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aJvmOnlyTestRunNeedsNoNativeTree
check-jvm-test-graph-selftest: all 1 case passed (one --dry-run configuration, no build, no network)
exit 0
```

## M3 — the vcpkg status file read lazily (case `buildGraph#aMissingNativeTreeFailsItsOwnTask`)

Mutation, in `app/build.gradle.kts`'s `mergeNativeSbom`: the guarded input and the `Provider` are replaced by the
eager declarations this change removed — the status file is an unconditional input and the port list is computed
while the task is created.

```
-    if (vcpkgStatusFile.isFile) inputs.file(vcpkgStatusFile)
-    shareDirs.forEach { if (it.isDirectory) inputs.dir(it) }
-    inputs.property("vcpkgPorts", providers.provider {
-        vcpkgTriplets.map { vcpkgPorts(vcpkgStatusFile, it) }
-    })
+    inputs.file(vcpkgStatusFile)
+    shareDirs.forEach { inputs.dir(it) }
+    inputs.property("vcpkgPorts", vcpkgTriplets.map { vcpkgPorts(vcpkgStatusFile, it) })
```

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aMissingNativeTreeFailsItsOwnTask
FAIL buildGraph#aMissingNativeTreeFailsItsOwnTask: the missing tree failed the task's creation (configuration),
     not its own action

check-jvm-test-graph-selftest: 1 of 1 cases failed — buildGraph#aMissingNativeTreeFailsItsOwnTask
exit 1
```

Restored (`sha256sum` equal), then:

```
$ bash tools/check-jvm-test-graph-selftest.sh --case buildGraph#aMissingNativeTreeFailsItsOwnTask
check-jvm-test-graph-selftest: all 1 case passed (one --dry-run configuration, no build, no network)
exit 0
```

## M4 — the property's read (case `JvmOnlyTestRunTest#anUnreadableValueIsRefusedWithActionableText`)

The unit cases carry the switch's own rule; the red run of the class is the `[x] 1.2` verification
(`evidence/unit-jvm-only-test-run.xml`, `tests=7 failures=0`). A mutation of `BuildFlags` would be a mutation of
another change's invariant (`-PforceTests`/`-PnoCoverage` share it), so this change does not mutate it: its own
case for the unreadable value is asserted on the value it passes (`yes`).
