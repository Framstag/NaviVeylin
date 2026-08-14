## Context

NaviVeylin is a new Android navigation application built on the libosmscout ecosystem. The project starts from scratch — no existing Android codebase, no CI pipeline, no distribution channel.

**Key constraints:**
- No Google account — app distributed outside Play Store (APK sideloading or alternative stores)
- No Google Maps or Play Services dependency
- C++ core library (`libosmscout-client`) must be cross-compiled for Android via NDK
- Java/JNI bridge (`libosmscout-client-java`) wraps the C++ layer for Android consumption
- Android Auto support planned but deferred — module structure must accommodate it later
- Single `app` module handles phone, foldable, and tablet via resource qualifiers

## Goals / Non-Goals

**Goals:**
- Establish project scaffold with Gradle (Kotlin DSL) and AGP
- Integrate `libosmscout-client` as a Git submodule with in-tree CMake build via NDK
- Build `libosmscout-client-java` as an Android Library module producing an AAR
- Wire JNI bridge so Kotlin/Java code can call native map and routing functions
- Apply modern Android architecture: Jetpack Compose, Navigation, ViewModel, Hilt, Coroutines
- Support arm64-v8a, armeabi-v7a, x86_64 ABIs
- Prepare `auto` module placeholder for future Android Auto support

**Non-Goals:**
- Actual map rendering UI or routing logic (deferred to implementation tasks)
- Android Auto feature implementation (deferred to later change)
- Play Store listing or signing configuration
- CI/CD pipeline setup
- Performance optimization or battery profiling

## Decisions

### D1: Single `app` module for all form factors
**Decision:** One `app` module handles phone, foldable, and tablet.
**Rationale:** Android resource qualifiers (`layout-sw600dp`, `layout-w600dp`) and Jetpack WindowManager API cover all form factors without separate modules. Simpler build, less complexity.
**Alternatives considered:** Multi-module (`:phone`, `:tablet`) — rejected as premature; adds build complexity without benefit at this stage.

### D2: In-tree CMake via NDK for C++ library
**Decision:** `libosmscout-client` added as Git submodule under `app/src/main/cpp/`, built via CMake with the Android NDK.
**Rationale:** Single build step, tight integration, easy to iterate during development. Migrate to prebuilt `.so`/AAR later when the native interface stabilizes.
**Alternatives considered:** Prebuilt `.so` (option 2) — faster Android-only builds but requires separate native build pipeline. External build system (option 3) — more infra than needed now.

### D3: `osmscout-jni` as Android Library module
**Decision:** `libosmscout-client-java` lives in its own Gradle module (`:osmscout-jni`) producing an AAR. It depends on the native `.so` from `osmscout-native`.
**Rationale:** Clean separation — the JNI bridge is a reusable library with its own API surface. AAR encapsulates native libs and Java classes together.
**Alternatives considered:** Inlining JNI code in `:app` — rejected; couples app to native details and prevents reuse.

### D4: Jetpack Compose for UI
**Decision:** Use Jetpack Compose (not XML layouts) for all UI.
**Rationale:** Modern, declarative, less boilerplate. Better integration with Navigation, ViewModel, and state management. Industry standard for new Android projects.
**Alternatives considered:** XML + Fragments — mature but more verbose and harder to maintain for a navigation-heavy app.

### D5: Hilt for dependency injection
**Decision:** Hilt (Dagger-based) for DI.
**Rationale:** Official Android recommendation, integrates with ViewModel, Navigation, and Compose. Compile-time safety.
**Alternatives considered:** Koin — simpler but runtime DI, less safe for a complex app. Manual DI — not scalable.

### D6: Room for local persistence
**Decision:** Room for offline map metadata, favorites, search history.
**Rationale:** Official Android persistence library, works with Coroutines/Flow, compile-time query verification.
**Alternatives considered:** SQLite directly — too low-level. DataStore — good for key-value but not relational map data.

### D7: `auto` module as placeholder
**Decision:** Create `:auto` Gradle module with minimal manifest declarations and an empty `CarAppService` stub.
**Rationale:** Module structure exists from day one, preventing restructure later. No functional Auto code yet.
**Alternatives considered:** No module until Auto work starts — rejected; adding a module later is more disruptive than an empty one now.

### D8: No Google Play Services
**Decision:** Zero dependency on Google Play Services or Google Maps.
**Rationale:** No Google account, no Play Store distribution. Map rendering comes from libosmscout's native OpenGL renderer.
**Alternatives considered:** Google Maps via Play Services — impossible without account. OsmDroid — possible but adds another dependency; prefer direct libosmscout rendering.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **NDK build complexity** — libosmscout-client has many dependencies (protobuf, libmarisa, etc.) that must cross-compile for Android | Start with a minimal build config; add deps incrementally. Use `ExternalNativeBuild` with CMake presets. |
| **JNI performance overhead** — frequent calls across JNI boundary could be slow | Batch native calls where possible. Keep JNI surface small — high-frequency operations (rendering) stay in C++. |
| **No Play Services** — no location, maps, or push notifications from Google | Use Android `LocationManager` directly. Map rendering from native lib. No push needed initially. |
| **App distribution** — no Play Store means users must sideload or use F-Droid/alternative store | Document install instructions. Consider F-Droid or GitHub Releases for distribution. |
| **Android Auto deferred** — adding Auto later may require manifest or module changes | Placeholder module reduces risk. Android Auto uses `CarAppService` which is additive, not breaking. |
| **Single module for all form factors** — complex layouts could become unwieldy | Use Compose adaptive layouts (`WindowSizeClass`). Split into composable functions early. |
