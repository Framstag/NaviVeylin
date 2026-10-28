# Proposal — the vendored client classes reach the packaged app

## Why

`osmscout-client-java` was split into two source sets by `4184f97a` (#678): `main` holds the six local
overrides, `vendored` holds the submodule's Java client API, and only the `jar` task joins them
(`from(sourceSets["vendored"].output)`).

An Android consumer does not read that jar. AGP dexes a project dependency from the source set's
**`classesDirs` collection** — `main`'s alone — so `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`
packaged the six overrides and no `com.framstag.libosmscout.client` class at all, while
`osmscout-client-java/build/libs/libosmscoutclientjava-1.0.0.jar` carried every class. Installing it on
`emulator-5554` and starting `com.naviveylin.MainActivity` died before the first frame:

```
FATAL EXCEPTION: main
Caused by: java.lang.ClassNotFoundException: Didn't find class
  "com.framstag.libosmscout.client.MapProvider" on path: DexPathList[[zip file base.apk]]
```

Measured on `main` with the cache out of the answer, so the reading is the wiring and not a stale output:
`mergeProjectDexMobileDebug` defined **0** classes of the module's **48** compiled ones; with
`./gradlew :app:assembleMobileDebug --no-build-cache` after removing
`app/build/intermediates/dex/mobileDebug` the packaged APK defined none of them either (the earlier APK in
the tree *did* — a restored cache entry, which is why the defect read as intermittent).

Impact: every on-device verification of a phone change is blocked, and any debug APK built from this tree
cannot start. The build is green throughout — a `NoClassDefFoundError`/`ClassNotFoundException` is not a
compile error, and nothing in the build read the dex.

## What Changes

- `osmscout-client-java/build.gradle.kts`: the vendored classes directory becomes a classes directory of
  `main` too, which is what the Android consumer resolves. `jacocoTestReport` reads the override compile's
  own directory instead of `main`'s `classesDirs` collection, so the two provenance rows of #678 stay two
  rows (measured: 10 override classes / 38 vendored classes).
- `buildSrc`: a dex reader and a gate (`com.naviveylin.build.dex`). The reader answers from `class_defs`,
  not from `type_ids` — a referenced type carries its descriptor in the file that calls it, so searching the
  bytes is what let the defect through.
- `:app:checkClientApiInDex<Variant>` for the four variants, each `assemble<variant>`/`bundle<variant>`
  depending on its own gate. Required classes are read from the two **source roots**, so the set cannot
  shrink with the defect it guards.
- `guidelines/Design.md` §5 records the wiring and the gate beside the bridge's two sides.

## Impact

- Affected specs: `test-coverage` (one requirement added — the split must not cost a consumer a provenance).
- Affected code: `osmscout-client-java/build.gradle.kts`, `app/build.gradle.kts`, `buildSrc/**`,
  `guidelines/Design.md`.
- No product behaviour changes; the APK gains the classes it always claimed to ship.
