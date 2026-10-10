# Design: fix-jni-override-parity

## Context and evidence

The defect reproduces in one host case. The Java side of the JNI bridge is the `:osmscout-client-java`
module, which compiles the submodule's `libosmscout-client-java/java` sources **except five overridden
files**, `OSMScoutClient` among them (`osmscout-client-java/build.gradle.kts:31-38`, the `exclude` list of
`compileJava`). The override at `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
is the class on the app's compile classpath.

| side | file:line | declares the two file-format natives |
|---|---|---|
| submodule Java source (excluded from the module) | `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java:963`, `:978` | yes |
| bridge override (compiled) | `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` | **no** |
| linked JNI | `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:7952` (`getFavoriteFileFormatVersion`), `:7963` (`isFavoriteFileFormatSupported`) | implements both |

The red case `OSMScoutClientFavoriteFileFormatApiTest#favoriteFileFormatNativesAreDeclaredOnTheOverride`
pins exactly this: `Class.getDeclaredMethod("getFavoriteFileFormatVersion")` throws
`NoSuchMethodException` (XML `tests="1" failures="1"`, 2026-10-10T07:45:58.051Z). The submodule commit
`530ac8768` ("client-java: expose the favorite ordering operations and the file version state") added the two
natives and the JNI entry points together; the override was updated for the ordering operations but not for
the file-format pair, because no app code reads the format version — the drift is silent until a call site
appears, and then it is a `NoSuchMethodError`.

**Are the natives C++-backed?** Yes. Both JNI entry points exist and are real bodies (not stubs):
`getFavoriteFileFormatVersion` reads `data->favoriteStore.GetFileFormatVersion()` and returns
`osmscout::FavoriteLocationService::UnknownFileFormatVersion` when no client data is loaded;
`isFavoriteFileFormatSupported` returns `data->favoriteStore.IsFileFormatSupported()`. The JNI symbol names
use the override's own package and class (`Java_com_framstag_libosmscout_client_OSMScoutClient_…`), so the
declarations bind to the same entry points. The parity fix therefore makes both natives **callable from
Java**; the host case asserts the declaration and return types, not an invocation, because the host test does
not load `libosmscout_client_java.so`.

## D1 — The one fix

Add the two declarations to the override, copied verbatim (KDoc included) from the submodule source, in the
submodule's order — after `getStarredFavorites`, before `setStarred`:

```java
public native int getFavoriteFileFormatVersion();
public native boolean isFavoriteFileFormatSupported();
```

`guidelines/Design.md` §5 states the rule the fix obeys: "Mirror upstream APIs exactly so submodule syncs
stay clean" and "Android-specific deviations live as local overrides in a bridge module, never patched into
the submodule." This is a **local override in the bridge module**; no submodule file is edited, so the "patch
one place, never both" discipline holds and the submodule stays clean for a future sync.

Alternatives the evidence eliminates:

| option | why the evidence eliminates it |
|---|---|
| Patch the submodule's Java source or `OSMScoutClient.cpp` | Both already declare/implement the natives; only the override is behind. `guidelines/Design.md` §5 forbids patching a deviation into the submodule and the submodule must stay clean for a sync — a commit there would be a no-op at best and a conflict at worst. |
| Add a blanket buildSrc/CI parity gate comparing the override's natives with the submodule's source | Measured today, the override deliberately diverges by more than these two: the submodule declares five natives the override omits (`calculateRouteWithObjectsAsync`, `cancelSearch`, `getRegion`, `setGpsMarker`, `startNavigation`) and the override declares six of its own (`getAddressAt`, `getDatabaseBoundingBox`, `getMaxSpeedAt`, `reloadBasemap`, `renderInto`, `searchLocationByForm`). A "submodule natives ⊆ override natives" check is therefore **false after the fix**, and a correct gate needs a family/expected-list definition — a decision this fix's evidence does not make for it. It is a separate change (filed as TODO.md §174), not part of this parity delta. |
| Copy the whole submodule file over the override | The other five overrides exist precisely because the Android port differs (debug-suffix library loading, `reloadBasemap`, `HttpURLConnection` downloads); replacing the file would regress those deviations. |
| Add a call site so the natives are not "dead" | No requirement asks the app to read the favorites file-format version; a call site widens the change past its parity delta and would need its own behaviour spec. The declaration alone restores reachability. |

## D2 — Blast radius, threading, rollback

- The change is two declarations in a class the module already compiles; no signature, field, initializer or
  JNI symbol changes. Nothing else in `:app`, `:auto` or `:core` references `OSMScoutClient`'s favorite
  file-format methods today (`grep -rn --exclude-dir=libosmscout … app/src/main auto/src/main core/src` prints
  nothing), so no call site is affected and no
  `when`/override is disturbed.
- No threading, dispatcher or lifecycle is involved: the additions are method declarations. The case is a
  pure host-JVM reflection assertion; it never enters a native frame.
- The submodule Java source, `OSMScoutClient.cpp`, the CMake/vcpkg native build and the spec
  `osmscout-jni`'s other requirements are untouched.

**Risk**: none observable — the two natives already exist in the linked library, so declaring them changes
only reachability. A caller that could not compile against them before still cannot without the declaration in
its own module's classpath, but the app uses this override.

**Rollback**: delete the two declarations (and the case). The override then compiles as before, and the
native library is unchanged.

## Verification

| scenario | case |
|---|---|
| The favorite file-format natives are declared on the override | `OSMScoutClientFavoriteFileFormatApiTest#favoriteFileFormatNativesAreDeclaredOnTheOverride` (red on HEAD, green after the fix) |

Revert-check: one mutation — remove the `getFavoriteFileFormatVersion` declaration from the override — must
fail the case at its `getDeclaredMethod` premise with `NoSuchMethodException`, not a compile error in the
test. Restore, then re-run the focused module suite forced. One forced both-flavor gate
(`./gradlew test -PforceTests --rerun-tasks`, production code changed) is the change's green evidence.
