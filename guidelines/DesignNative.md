# Design Native boundary guidelines — the JNI bridge

What crosses the native boundary, split under the unchanged number §5 into §5a (the boundary's shape and its
JNI contract) and §5b (the bridge's two sides, the two database-open entries and the submodule discipline); the
rules are carried over unchanged from the `Design.md` document this cluster was cut out of. Why the two
database-open entries differ is in `guidelines/DesignNative.why.md` §5b.

**Rule naming** — a rule begins with a bold `Rule:` token, and the first bold span of its bullet is the rule's
name; a name is unique across `guidelines/*.md`, and another document references it by the rule-reference form
the route check resolves.

---

## 5. Native boundary

The boundary's rules, split under this unchanged number: §5a is the boundary's shape and its JNI contract, §5b
the bridge's two sides, the two database-open entries and the submodule discipline.

## 5a. The boundary's shape and its JNI contract

- **Live guidance publishes the step model, not instructions** (change `route-steps-live-guidance`,
  spec `osmscout-jni` — Live guidance publishes route steps): the navigation engine runs
  `RouteInstructionAgent<osmscout::client::RouteStep, RouteStepBuilder>`, so
  `NavigationListener.onRouteSteps(RouteStep[])` / `onNextRouteStep(RouteStep)` deliver the same record
  a calculated route publishes, extended with `remainingDistance`, `remainingTime` and a nested
  `nextNext` step. **No guidance text crosses the boundary** — not a description, not a short
  description, not a turn-type name; a surface words every manoeuvre from `:core/ManoeuvreLabels`.
  The patch is one fork commit on the submodule's `naviveylin-local` branch (`de37a2bd1`) plus the pin
  bump, and it is a compile-time break for a sibling consumer that read instruction callbacks
  (JavaScout; its adaptation landed in `999acc16f` and `6c8dc6f94`). The dispatch logs one coordinate-free line per
  publication (`[JNI] guidance dispatch: list|next steps=N us=M`) — that is the number a cache
  decision is taken from, so do not delete it without one.

- **A calculated route publishes steps, never prose** (change `retire-instruction-prose`, spec
  `osmscout-jni` — Per-step leg values on a calculated route): `RouteEntry` carries `latitudes`,
  `longitudes`, `distance`, `duration`, the structured `steps` and the database mapping, and nothing else.
  There is no `descriptions` array, no per-instruction position array, no per-step leg array and no
  `RouteInstruction` class, so **no consumer can read English guidance text from the bridge** — every
  client words a step from its values (`:core/ManoeuvreLabels` in the app, `NextTurnText` in JavaScout).
  The route's published length is read from the step list (`LastStepDistanceMeters`, the largest
  `distanceFromStart`), not from a second description walk; the geometry fallback stays for a route
  without a description. `osmscout::RouteDescription` and its postprocessor **remain** a libosmscout
  model — Qt and the other clients read them; only this bridge's Java projection of them went.

- **Rule: the native core stays platform-pure** **MUST**: the native core stays platform-pure — no Android dependencies
  outside the frozen upstream dir, enforced by a CI gate. Platform
  integration is an app-owned bridge (log sink, load order).
- **Rule: the JNI surface stays small and explicit** Keep the JNI surface small and explicit: integer handles for native
  objects, batched marshaling, hot paths stay in C++.
- **Rule: C++ exceptions convert to Java exceptions at the boundary** **MUST**: C++ exceptions convert to Java exceptions at the boundary; native
  code never aborts the process (asserts kill it).
- **Rule: native method contracts are API - fix behaviour in Kotlin** Native method contracts are API; fix behavioral issues in Kotlin, not by
  changing native semantics.
- **Rule: a defect inside the library behind an unchanged contract is fixed upstream** A defect *inside* the library behind an unchanged JNI contract (e.g. its
  transliteration or matching logic) is fixed upstream as a minimal,
  upstreamable patch instead of being worked around in Kotlin: a workaround
  would have to guess at the library's internal semantics and would leave
  every other caller of the same code broken.
- **Rule: mirror upstream APIs exactly; keep submodule patches minimal and upstreamable** Mirror upstream APIs exactly so submodule syncs stay clean; keep submodule
  patches minimal and upstreamable.
- **Rule: Android-specific deviations live as local overrides in a bridge module** Android-specific deviations live as local overrides in a bridge module,
  never patched into the submodule.
- **Rule: serialize writes in native shared services** Serialize writes in native shared services; a render mutex serializes
  concurrent renders.
- **Caller-owned pixel storage for a render** (change `reduce-render-peak-memory`): the render path
  supplies the frame's pixel storage (a pooled direct `ByteBuffer`) and the native entry point
  writes into it for the duration of one call, retaining no reference. Two surfaces render
  concurrently, so the *caller* owns the "never hand the same storage to two renders" rule — which
  the app already satisfies (a displayed frame is an independent copy, and the pool never hands out
  a target or buffer twice). The bridge keeps both entry points: the buffer-taking one and the
  unchanged allocating one, running one shared render body so the two cannot disagree. The format is
  a contract, not an implementation detail: `0xAARRGGBB` ints, stride `width*4`, native byte order.
- **No fault escapes a boundary** (same rule as `car-host-fault-isolation` and
  `MemoryPressureResponder`): a JNI entry point reports an unusable request (`false`/`null`/empty)
  instead of faulting, and an app-side coroutine on a process-wide dispatcher confines its own
  throwable (`runCatching` or a `CoroutineExceptionHandler`) — an escaping throwable reaches the
  thread's uncaught-exception handler and kills the app (and poisons an unrelated test's coroutine
  harness, closed 2026-09-27 by `bound-tile-data-retention`).
- Logging: native via `osmscout::log` + app-owned bridge; Kotlin via
  `android.util.Log` with per-class TAG. Stylesheets: submodule is the single
  source of truth, synced at build time. Mechanics: `AGENTS.md`.
- **A failure crosses the boundary as a kind, never as prose** (change `distinguish-routing-failures`, spec
  `routing-failure-reporting`): the routing entry point publishes a failure with a kind from a closed set
  (`RouteCallback.KIND_*`, mirrored by `naviveylin::kRouteFailure*` — the two lists are kept in step by
  hand, the number is what crosses JNI), plus a description for the log and, at a failing route end, the
  distance to the nearest road the router could use. The surface words it from its own resource, so the
  phone and the car name the same cause the same way and neither shows a sentence another layer composed.
- **The seam is additive and lives on one side** (same change): `RouteCallback.onErrorDetail` is a default
  method delegating to `onError(String)`, so a caller written before it keeps working and the native
  fallback to `onError` still exists; `RouteCallback.java` and `OSMScoutClient.cpp` are submodule files,
  while the app-facing `OSMScoutClient.java` is a local override — patched in one place, never both. The
  kind is extracted in **one** helper shared by both route entry points, which otherwise carry two copies
  of the same pipeline and would drift.
- **A router defect is fixed in the router, not described to the user** (same change): when the endpoint a
  calculation resolved cannot be attached to the road network, the fix is the candidate **selection**
  (`SimpleRoutingService::IsEndpointAttachable`, which mirrors the four direction-gated searches of
  `AbstractRoutingService` — keep the ranges in step), not a message that explains the failure. The rule is
  parameterized by the route end (`RouteEndpoint`), because the start and the target attach along opposite
  halves of a way: a one-way street can carry one end and not the other. The existing
  `GetClosestRoutableNode` signatures keep their behaviour and delegate with `RouteEndpoint::Unspecified`,
  so no existing caller changes.

- **JNI-bridge Java types are positional-argument only** (§40.23): no named arguments, read the constructor
  before writing a helper (`RouteInstruction` has 5- and 10-arg forms), and update EVERY call site in the
  same change when such a constructor changes — stale test bytecode surfaces as `NoSuchMethodError`, not a
  compile error.
- **Do not mock a Kotlin `object`'s `@JvmStatic` methods** (§40.24): mockk cannot intercept them. Stub the
  real `applicationContext` and the Java delegate the object calls (`dagger.hilt.EntryPoints`).

## 5b. The bridge's two sides, the database-open asymmetry and the submodule discipline


- **Rule: patch the JNI bridge in one place, never both** **Patch the JNI bridge in one place, never both.** The C++ side lives in the libosmscout submodule at
  `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`, built as
  `libosmscout_client_java.so` (CMake target `osmscout_client_java`) and depending on `OSMScout::OSMScout`,
  `OSMScout::Map`, `OSMScout::MapCairo` and `OSMScout::Client`. The Java side is the `:osmscout-client-java`
  Gradle module, which compiles the submodule's `libosmscout-client-java/java` sources **except five
  overridden files** — `OSMScoutClient`, `OSMScoutClientBuilder`, `BasemapManager`, `MapDownloadManager` and
  `AvailableMapEntry`, which come from `osmscout-client-java/src/main/java`, plus the local-only
  `InstalledMaps` — and produces `libosmscoutclientjava.jar`. Those overrides are Android ports:
  `HttpURLConnection` map downloads (no `java.net.http` desugaring), the debug-suffix library loading
  (`osmscout_client_java` → `osmscout_client_javad`), the `reloadBasemap` declaration, public constructors.
- **Both provenances reach the app, and the dex says so.** The module compiles the overrides and the submodule's
  client API into two classes directories, and the `jar` task joins them — but an Android consumer never reads that
  jar: AGP dexes a project dependency from `main`'s `classesDirs` collection, so the split left the APK with no
  `com.framstag.libosmscout.client` class and the app died at startup with `ClassNotFoundException: MapProvider`
  The vendored directory is therefore one of `main`'s classes directories
  (`sourceSets["main"].output.classesDirs`, not `output.dir(...)`, which does not reach the consumer), the
  provenance rows of the coverage report stay one directory each, and `:app:checkClientApiInDex<Variant>` — buildSrc
  `com.naviveylin.build.dex`, on every `assemble`/`bundle` — fails when the merged dex defines no class for a
  `*.java` file under either source root. A referenced type is not a defined one: the gate reads `class_defs`, not
  the bytes. Because a minified variant's only dex is R8's output (`minify<Variant>WithR8`, not the dex builder and
  the merge steps a debug variant runs), the gate depends on that task for the `release` variants;
  `tools/check-dex-gate-graph-selftest.sh` asserts the producer per variant, which is what caught the merge-only
  wiring that made `./gradlew release` fail on an empty dex tree (2026-10-28).
- **The two sides are compared, and their difference is recorded.** For those five files the override is the
  only Java copy the APK sees, so a native declared on one side only reaches the other as a `NoSuchMethodError`
  at the first call — no compiler error, no failing test. A
  blanket "submodule natives ⊆ override natives" check is false, because the port diverges on purpose in both
  directions, so the difference itself is the contract: `osmscout-client-java/native-parity.txt` records every
  divergence as `file | submodule-only|override-only | name(types) | reason`, and the build gate
  `:app:checkNativeParity` (buildSrc `NativeParityScanner`, in `preBuild`) fails on any drift the record does
  not carry, in either direction, and on a recorded line the trees no longer need. A new submodule native is
  therefore a build failure in the bump's own build: mirror it, or record why the Android port drops it.
- **A search request carries its generation.** The search entry points take the generation a caller got from
  `beginSearch(slot)` — `searchLocations(query, limit, adminRegionHandle, generation)`,
  `searchLocations(query, limit, defaultRegion, generation)`, `searchLocationByForm(…, generation)`,
  `searchPOIsByTypes(…, generation)` — and `cancelSearch(generation)` cancels exactly that request, reporting
  whether it was still running. The policy is `search_breaker.h` beside the JNI TU: one slot per search surface
  (`places`, `poi`, `route`, `car`), a request is aborted once a newer request of its slot began or it was
  cancelled, and an aborted search returns an empty result rather than a partial one. A cancel naming a
  superseded generation aborts nothing, which is what makes cancelling from a `finally` block safe
  (spec `search-request-lifecycle`, `osmscout-jni`).
- **Rule: the two database-open entries differ on purpose** **The two database-open entries differ on purpose.** `openDatabase(path)` accepts only an existing
  directory (a rejected path reports `false`, registers nothing and publishes no database-set change), while
  `openDatabases(paths[])` registers what it is handed — a directory that disappears between the app's scan
  and the call must not fail the batch — and only reports it, by directory name. Do not "unify" them
  (spec `native-database-open`).
- **Rule: the map scan publishes only what this build can read** The map scan publishes only what this build can read:
  `MapManager::LookupDatabases()` walks the registered lookup directories recursively and reads each
  candidate's data format version (`TypeConfig::GetDatabaseFileFormatVersion`) before it constructs the
  `MapDirectory`: a version other than `FILE_FORMAT_VERSION` keeps the directory out of the published set and is
  named once in `osmscout::log.Warn()` with both versions, while a version that **cannot** be read — a missing,
  short or unreadable `types.dat` — decides nothing and stays published, so the reader remains the authority.
  Do not replace this with a basemap path exemption: `DBThread`'s exemption keys on the basemap lookup
  directory, and the app unsets that very directory when it refuses the basemap, so such an exemption is dead
  code in the one case that needs it (issue #716, change `fix-basemap-scan-format`).
- **Rule: the submodule is pinned and kept clean** **The submodule is pinned and kept clean.** This repository pins its SHA in the gitlink and bumps it
  (commit) after every submodule commit; uncommitted submodule changes are not built by CI or by a fresh
  clone.
- **Rule: one session owns the submodule and its pushes** **One session owns the submodule and its pushes.** Run `git ls-remote origin <branch>` — the remote, not
  the local remote-tracking ref — immediately before the push: two sessions created the same JNI commits and
  force-rewrote `naviveylin-local`, reconciled afterwards by a merge (`TODO.md` §40.54).

