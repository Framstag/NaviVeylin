# Tasks — fix-client-api-in-dex

## 1. Measure before changing anything

- [x] 1.1 Reproduce with the cache out of the answer: `rm -rf app/build/intermediates/dex/mobileDebug` then
      `./gradlew :app:assembleMobileDebug --no-build-cache`; the packaged APK defined **0** of the module's 48
      classes (a reader over `class_defs`, then over the APK's `classes*.dex`)
- [x] 1.2 Confirm the jar is not the defect: `osmscout-client-java/build/libs/libosmscoutclientjava-1.0.0.jar`
      carries all 48
- [x] 1.3 Confirm the variant is what the note says: `:app:dependencyInsight --configuration
      mobileDebugRuntimeClasspath --dependency osmscout-client-java` selects `runtimeElements` / `libraryelements=jar`

## 2. Repair the wiring

- [x] 2.1 `output.dir(...)` does **not** reach the Android consumer (measured: still 0/48)
- [x] 2.2 Add the vendored classes directory to `main`'s `classesDirs`; APK defines 48/48
- [x] 2.3 Pin `jacocoTestReport.classDirectories` to `compileJava`'s destination so the own row stays the overrides
      (re-measured: 10 own / 38 vendored)

## 3. The gate

- [x] 3.1 `com.naviveylin.build.dex.DexClassDescriptors` — `class_defs` reader, `dex\n` only, compact dex refused by
      name
- [x] 3.2 `com.naviveylin.build.dex.ClientApiClasses` / `ClientApiDexGate` — required descriptors from the source
      roots, the missing set, the report text
- [x] 3.3 `CheckClientApiInDex` typed task (inputs, no outputs, config-cache friendly)
- [x] 3.4 `:app:checkClientApiInDex<variant>` for the four variants, wired into `assemble<variant>`/`bundle<variant>`
- [x] 3.5 `buildSrc: ClientApiDexGateTest` — 7 cases, 0 failures (hand-built dex fixture for the reader)

## 4. Falsify, then verify

- [x] 4.1 Remove the classes-directory repair for one run, `--no-build-cache`: the gate fails and names 35 classes
      (e.g. `com.framstag.libosmscout.client.Vehicle`); restore, green
- [x] 4.2 `./gradlew -p buildSrc test` green (7 new cases)
- [x] 4.3 `./gradlew :osmscout-client-java:jacocoTestReport :osmscout-client-java:jacocoVendoredTestReport` — the two
      rows stay two rows
- [x] 4.4 `./gradlew :app:checkClientApiInDexMobileDebug` green on the repaired tree

## 5. Document

- [x] 5.1 `guidelines/Design.md` §5 — the wiring and the gate beside the bridge's two sides
- [x] 5.2 `openspec validate fix-client-api-in-dex --strict`
