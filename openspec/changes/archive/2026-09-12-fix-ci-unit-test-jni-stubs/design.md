## Context

See proposal.md - Why. Empirical facts gathered during verification of `fix-vcpkg-ci-cache`:

- CI run log: every `AutoMapRendererTest` test failed at `AutoMapRendererTest.kt:30` (`client = FakeAutoRenderClient()`), app tests at `AddressBookResolverTest.kt:48` — all `java.lang.UnsatisfiedLinkError`.
- `FakeAutoRenderClient : OSMScoutClient()` — subclass instantiation triggers `OSMScoutClient`'s static block (`System.loadLibrary("osmscout_client_java")` → fallback `osmscout_client_javad`).
- A probe test under Robolectric printed `java.library.path` — AGP injects the module's own `src/test/jniLibs` (and `src/testDebug/jniLibs`) dirs; loading `osmscout_client_java` succeeded locally, `_javad` failed (missing variant in `:auto`).
- `git ls-files app/src/test/jniLibs` → empty: the `.so` files are gitignored (`*.so` rule) and exist only on the dev machine.
- Local runs on both JDK 17 and JDK 21 pass — JDK version is not a factor.

## Goals / Non-Goals

**Goals:**
- CI unit tests run green (no `UnsatisfiedLinkError`) on fresh checkouts.
- Stubs versioned, test-only, both loader-name variants present per module that needs them.
- Zero production-code changes.

**Non-Goals:**
- No change to `OSMScoutClient`'s loader logic (keep `loadLibrary` + `_javad` fallback).
- No change to how fakes override native methods.
- No CI-time stub compilation (committed binary is simpler and the artifact is tiny, deterministic, and already documented).
- No change to app/auto source build outputs.

## Decisions

### Decision 1: Commit the prebuilt stub binaries, un-ignore them

Keep the three existing `.so` files versioned and add the missing `_javad` variant to `:auto`. `.gitignore` gains re-include rules scoped to those two directories:

```
# Test-only JNI stubs (host-compiled, OSMScoutClient loadLibrary) — committed so CI works
!app/src/test/jniLibs/*.so
!auto/src/test/jniLibs/*.so
```

**Alternatives considered:**

1. **Compile the stub in CI** (host `gcc -shared` step before tests). Rejected — extra moving part, runner toolchain drift, and the stub is a purpose-built static artifact already present on dev machines; committing it keeps local and CI byte-identical.
2. **Load stubs from a versioned non-`.so` resource and extract at test time** (custom JUnit rule / `java.library.path` injection in `build.gradle.kts`). Rejected — more infrastructure than the problem needs; AGP's built-in `src/test/jniLibs` → `java.library.path` wiring already exists and is verified.
3. **Drop `loadLibrary` behind a test flag in `OSMScoutClient`**. Rejected — touches shipped code and changes class-init semantics for tests; the stub approach keeps prod code untouched.

### Decision 2: Both name variants in every module that triggers the load

`OSMScoutClient` tries `osmscout_client_java`, then `osmscout_client_javad`. `:auto` currently holds only `_java`; per AGENTS.md the missing fallback makes full-suite runs flaky (e.g., if one module's load binds the name and another resolves differently). Copy the `_javad` stub so both modules carry both variants.

**Alternative:** ship only `_java` where the first load succeeds. Rejected — the fallback exists precisely because partial/parallel loads can need it; parity is free.

## Risks / Trade-offs

- **Committed binary bloat / review** — ~15 KB × 4 files of host ELF. → Mitigation: files are documented in AGENTS.md, gitignore rules scoped narrowly; binary is an empty-ish symbol-less stub whose content change is easily reviewed by diff.
- **Architecture coupling** — stubs are x86-64 host ELF; unit tests run on the host JVM, and CI runners are x86-64. → Mitigation: consistent with existing local setup; Android device ABIs (`arm64-v8a` etc.) are irrelevant to JVM unit tests.
- **Stale stub vs. loader evolution** — if `OSMScoutClient` changes its loader names, stubs would need renaming. → Mitigation: loader logic is frozen by this change's non-goals; any future change bumps it.

## Migration Plan

1. Edit `.gitignore` (re-include rules).
2. Copy `_javad` stub into `auto/src/test/jniLibs/`; force-add all four `.so` files.
3. Update AGENTS.md stub section (stubs now committed).
4. Run full unit-test suite locally (`./gradlew test`) — must be green.
5. Push; CI run restores vcpkg deps from the NuGet feed and must pass unit tests.
6. Rollback: revert the commit — CI tests fail again exactly as before.

## Open Questions

None.
