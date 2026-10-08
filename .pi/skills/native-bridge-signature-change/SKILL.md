---
name: native-bridge-signature-change
description: Changes a JNI method signature in the NaviVeylin libosmscout bridge atomically — submodule C++ implementation, local Java override declaration, every Kotlin/Java call site and the gitlink in one main-repo commit. Use when a native method's parameters or return type must change (add, remove, reorder, rename), or when auditing that the compiled .so and the Java declaration still agree.
metadata:
  submodule: app/src/main/cpp/libosmscout
  branch: naviveylin-local
  cpp-impl: app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp
  java-decl: osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java
  dead-copy: app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java
  audit-script: .pi/skills/native-bridge-signature-change/scripts/bridge-signature-audit.sh
---

# Native bridge signature change

## When to use

- A `native` method declared in `:osmscout-client-java` needs a different parameter list or return type
- An OpenSpec apply task changes a bridge method's signature (e.g. `renderWithRouteAndPois` gaining a `dpi`)
- Auditing whether the compiled `.so` still matches the Java declaration (after a submodule merge, or when a call shows no effect)

## When NOT to use

- **Behaviour change behind an unchanged signature** — fix it in Kotlin, or upstream as a minimal patch (`guidelines/Design.md` §5). Changing the signature to fix behaviour is a contract change and needs a spec.
- **Adding a brand-new native method** — additive; no call-site sweep, but the split and the commit rules below still apply.
- **Non-JNI Kotlin/Java API changes** — nothing compiled here is native.

## Background: two halves, one compiled declaration

| role | path | notes |
|---|---|---|
| JNI implementation (C++) | `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` | submodule, branch `naviveylin-local`; also the forward declaration and any delegating symbol (e.g. `render` forwards to `renderWithRouteAndPois`) |
| Java declaration | `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` | **local override** — this is what compiles and produces `libosmscoutclientjava.jar` |
| ~~dead copy~~ | `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../OSMScoutClient.java` | **not compiled** (5 overridden files). Editing it changes nothing — its line numbers will mislead you if you cite them in a spec or task |

`AGENTS.md` states the rule as **"patch in one, never both"**: the Java declaration lives in the override module, the JNI body in the submodule. That split is the documented one; do not mirror one file into the other.

## Decide the failure mode **before** editing

The JVM resolves a native method by the *short* symbol name first, and only falls back to the descriptor-encoded long name. So the consequence of a mismatch depends on the method:

```
1 native declaration with that name    -> SHORT symbol   -> a signature change is SILENT:
   (measured: render, renderWithRouteAndPois)              the old library reads the request's
                                                           arguments in the OLD order; no
                                                           UnsatisfiedLinkError, wrong results
>1 native declaration with that name   -> LONG symbol    -> a signature change FAILS LOUDLY
   (descriptor is part of the name)                        (UnsatisfiedLinkError at first call)
1 native declaration, but the .cpp    -> LONG symbol    -> still LOUD: the JVM's long-name
   defines only a long symbol                               fallback resolves it, and the
   (measured: searchLocations,                              descriptor in the name catches the
    no short symbol at all)                                 change
```

The declaration count tells you what the class **expects**; the `.cpp` tells you what a mismatch
would actually **do**. Audit both — the script prints both, per method.

Two traps sit inside that rule:

1. **Symbol presence is not proof of a match.** `OSMScoutClient.cpp` carries *dead* long symbols left over from removed Java overloads — measured 2026-09-26: `Java_..._getDescription(` (short, live) at `:4982` **and** `Java_..._getDescription__DDI` (dead) at `:5011`, while the class declares exactly one native `getDescription(double, double, int)`. A "does the symbol exist?" audit therefore passes on a dead symbol. `searchLocations` likewise has long symbols only (`__Ljava_lang_String_2IJ`), which the long-name fallback still resolves.
2. **`javap` alone cannot prove the match.** It gives types and arity, not parameter names or order:
   `render` → `descriptor: (IIDDDDD)[I`, `renderWithRouteAndPois` → `(IIDDDDD[D[D[D[DDD[D[D)[I`. Cross-check those against the C++ parameter list by hand; only a real render (device) proves the semantics end to end.
Audit the shape with the bundled script before touching either side:

```bash
./.pi/skills/native-bridge-signature-change/scripts/bridge-signature-audit.sh            # all native methods
./.pi/skills/native-bridge-signature-change/scripts/bridge-signature-audit.sh render     # one method
./.pi/skills/native-bridge-signature-change/scripts/bridge-signature-audit.sh render --with-descriptor
```

## Procedure

1. **Patch the C++ side (submodule).** Update the forward declaration, the definition, and any delegating symbol together; the comment block above each function lists the parameter list — update it too. Keep the file's existing shape: `getClientData(env, self)` + null check at the top, invalid arguments reported like other invalid requests (`return nullptr` / `JNI_FALSE`, mirroring `projectToPixel` rejecting `dpi <= 0.0`), `osmscout::log` only (`android/log.h` is CI-gated forbidden outside the frozen `Android/` dir). Keep the patch minimal and upstreamable.

2. **Verify the native side for every ABI** (native-only, fast — measured ~10-20 s warm):
   ```bash
   ./gradlew ":app:buildCMakeDebug[arm64-v8a]" ":app:buildCMakeDebug[armeabi-v7a]" ":app:buildCMakeDebug[x86_64]"
   ```
   A freshly linked `libosmscout_client_javad.so` under `app/build/intermediates/cxx/Debug/*/obj/<abi>/` is the evidence (debug builds use the `d` suffix — the loader falls back from `osmscout_client_java`).

3. **Patch the Java declaration (override).** Match the C++ parameter order exactly; update the javadoc `@param`s; make any new parameter **required** (no default, no extra overload) so the compiler enumerates every call site instead of hiding them.

4. **Sweep call sites with the compiler, not with grep:**
   ```bash
   ./gradlew :core:compileDebugUnitTestKotlin :app:compileMobileDebugUnitTestKotlin :auto:compileDebugUnitTestKotlin
   ```
   The three test doubles extend `OSMScoutClient` and override native methods, so they break loudly — that is the signal, not a problem: `FakeOSMScoutClient.kt` (app tests), `FakeMapScreenClient.kt`, `FakeAutoRenderClient.kt` (auto tests). Keep their recorders aligned with the new request shape (they are how the change is asserted in tests).
   **Do not touch** `app/src/test/jniLibs` and `auto/src/test/jniLibs`: those stubs export no symbols, they only make `System.loadLibrary` succeed, and both committed name variants (`_java`, `_javad`) are deliberate.

5. **Commit atomically.** The gitlink, the Java declaration and every call site must be in **one main-repo commit**, with the submodule patch committed on `naviveylin-local` first:
   ```bash
   git -C app/src/main/cpp/libosmscout add libosmscout-client-java/src/OSMScoutClient.cpp
   git -C app/src/main/cpp/libosmscout commit -F -      # what changed, why, upstreamable
   git add app/src/main/cpp/libosmscout <java decl> <every changed Kotlin/test file>
   git diff --cached --submodule=log -- app/src/main/cpp/libosmscout   # must show old..new SHA
   git commit -F -
   ```
   A gitlink-only or declaration-only commit is not "partial work" — for a short-symbol method it is a silently wrong library, and the message should say so.

6. **Gate.** Full unit suite green (`run-tests` skill), new tests covering the new parameter's effect, and a **revert-check**: break the new plumbing, confirm the new test fails, restore it. An assertion that survives the revert is not evidence — see `ki_processing_failures.log` for how that looks. Native signature changes cannot be validated by host tests (the stub has no symbols); the change's own on-device check is the end-to-end proof.

## Pitfalls

1. **Editing the dead java copy in the submodule.** `grep -n setMapDpi <submodule>/libosmscout-client-java/java/...` finds a different file than the one that compiles. Always cite the override path.
2. **Assuming the loud failure.** Only overloaded methods fail at link time. Check the declaration count first (step "Decide the failure mode").
3. **Trusting symbol presence.** Dead long symbols from removed overloads make a symbol audit pass on code nobody calls.
4. **`--rerun` after several task names applies only to the last task** — a "verification" run can print `BUILD SUCCESSFUL in 9s` having executed no tests. Use `--rerun-tasks`, or one task + `--rerun`, and confirm with the test-result XML timestamps, never the verdict line alone.
5. **Robolectric classloader rule:** any test class that touches `OSMScoutClient` must run under the **default** sandbox (`@RunWith(RobolectricTestRunner::class)`, no `@Config(sdk=…)`/`@GraphicsMode(…)`), or the stub `.so` "already loaded in another classloader" failure takes the whole suite down.
6. **Leaving the submodule dirty.** Uncommitted submodule changes are not built by CI or a fresh clone. Commit the submodule, then bump the gitlink — the parent's pinned SHA must exist.
7. **Long gradle runs killed by the shell cap.** Run detached (`nohup … > /tmp/log 2>&1 &`), poll, and evaluate by the verdict line, not the shell exit code (`build-app` / `run-tests` skills).

## Notes

- Symbol resolution and the long-name fallback: JNI native method resolution; overload mangling is visible in this file (`Java_..._searchLocations__Ljava_lang_String_2IJ`).
- Cross-references: `AGENTS.md` "Native Integration" and "JNI stub for unit tests"; `guidelines/Design.md` §5 (native boundary, upstreamable patches); `guidelines/Build.md` (build/test skills); the `update-to-current-libosmscout-master` skill (its step 3 checks symbol *presence* — pair it with this audit).
- This skill is versioned with the project (`.pi/skills/native-bridge-signature-change/`); copy the directory to `~/.pi/agent/skills/native-bridge-signature-change/` to make it available across projects.
