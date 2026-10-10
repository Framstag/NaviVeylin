# Proposal — fix-jni-override-parity

## Why

Root cause: The `:osmscout-client-java` module compiles the submodule's `libosmscout-client-java/java`
sources **except five overridden files**, and its hand-maintained `OSMScoutClient.java` override shadows
the submodule's copy — so when the submodule's favorites API grew, the override did not follow it, and the
app compiles against the override, leaving the two new file-format natives unreachable with no compiler
error and no failing test.
Evidence:    com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest#favoriteFileFormatNativesAreDeclaredOnTheOverride fails on HEAD — XML tests="1" failures="1" (`java.lang.NoSuchMethodException: com.framstag.libosmscout.client.OSMScoutClient.getFavoriteFileFormatVersion()`), 2026-10-10T07:45:58.051Z
Repro:       ./gradlew :osmscout-client-java:test --tests "com.framstag.libosmscout.client.OSMScoutClientFavoriteFileFormatApiTest"
Spec:        osmscout-jni / The Android override declares the submodule's favorite file-format natives   Guideline: guidelines/Design.md §5

The override at `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
declares 54 natives; the submodule source at
`app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`
declares `getFavoriteFileFormatVersion` (`:963`) and `isFavoriteFileFormatSupported` (`:978`) that the
override does not. The linked JNI implements both:
`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:7952` and `:7963`. The class the
app compiles against is the override, so a caller on Android cannot reach the two natives the native library
already provides — exactly the "mirror upstream APIs exactly so submodule syncs stay clean" rule of
`guidelines/Design.md` §5, broken by the one file the override mechanism is for.

The sibling favorites natives arrived the same way and *were* declared in the override when their features
landed (`moveFavoriteToGroup`, `moveGroup`, `moveStarredFavorite`/`getStarredFavorites`). Only the two
file-format ones were left behind, because nothing in the app reads the format version: the drift is silent
until a call site appears, and then it is a `NoSuchMethodError`, not a compile error.

## What Changes

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` gains the two
  missing native declarations, copied verbatim (with their KDoc) from the submodule source beside
  `getStarredFavorites`, so the override mirrors the submodule's favorites API. This is the whole fix.
- `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/OSMScoutClientFavoriteFileFormatApiTest.java`
  (new) pins the declarations by reflection, so the drift cannot return unnoticed: the red-on-HEAD case
  `favoriteFileFormatNativesAreDeclaredOnTheOverride` fails today and passes after the declarations land.

**Native/JNI placement**: this is a **local override in the bridge module**, not a submodule patch. The
submodule's Java source and `OSMScoutClient.cpp` already declare and implement both methods; no submodule file
is touched (`guidelines/Design.md` §5 — "patch the JNI bridge in one place, never both").

**Explicit non-goals** (recorded so they are not re-litigated during apply):

- No submodule edit (`libosmscout/**` untouched) and no C++ edit.
- No blanket "override declares every submodule native" buildSrc/CI gate. The measurement below shows the
  override deliberately diverges by more than these two, so a blanket subset check is false even after this
  fix; a correct drift gate needs its own family/expected-list decision and is filed as TODO.md §174, not
  smuggled into this one.
- No call site in `:app`/`:auto`: no requirement asks the app to read the favorites file-format version, and
  adding one would widen the change past its parity delta.

Additive: no existing declaration or signature changes; the observable change is that two already-implemented
natives become reachable through the override. **Rollback**: delete the two declarations (and the test).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `osmscout-jni`: adds requirement "The Android override declares the submodule's favorite file-format
  natives" (one requirement, one scenario) — the override's favorite file-format natives are reachable and
  match the submodule and the linked JNI. No existing requirement is modified.

## Impact

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — two native
  declarations plus their KDoc, in the submodule's order (after `getStarredFavorites`, before `setStarred`).
- `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/OSMScoutClientFavoriteFileFormatApiTest.java`
  — new; the red-on-HEAD case and its spec-reference KDoc.
- Specs: the delta lands in this change; the durable spec is synced at archive
  (`openspec/specs/osmscout-jni/spec.md`).
- Guidelines: `guidelines/Design.md` §5 already owns the rule (mirror upstream APIs; deviations live as
  overrides in the bridge module), and this change follows it — no guideline edit.
- Not touched: the submodule Java source, `OSMScoutClient.cpp`, the CMake/vcpkg native build, and the app and
  auto modules.

**Verification** — the added requirement's one scenario is exercised by `OSMScoutClientFavoriteFileFormatApiTest`
on the host JVM; one revert-check mutates a declaration; the forced both-flavor gate runs once. Details in
`design.md`, steps in `tasks.md`.
