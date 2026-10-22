# fix-jni-native-parity-guard

## Why

`Nothing guards the override/submodule native parity` (issue #174, debt, found 2026-10-10 while applying
`fix-jni-override-parity`/§119). Verified against the tree today:

- `:osmscout-client-java` compiles the submodule's `libosmscout-client-java/java` sources **except five
  overridden files** (`guidelines/Design.md` §5, "The bridge's two sides"), so a native declared on one side
  only reaches the other side as a `NoSuchMethodError` at the first call — no compiler error, no failing test.
- Nothing compares the two sides. `grep -rln "NativeParity\|native-parity\|native parity" tools app/src/test
  auto/src/test core/src/test buildSrc/src .github guidelines osmscout-client-java/src` → **0 files**, and
  `grep -c 'tasks.register("check' app/build.gradle.kts` → 8 gates, none about the bridge's Java pair.
- The rule exists as prose only: `grep -rln "Mirror upstream APIs\|mirror upstream" --include=*.md .`
  (build trees, the submodule and the archives excluded) → 1 file, `guidelines/Design.md` §5, which states
  "Mirror upstream APIs exactly so submodule syncs stay clean" and "Android-specific deviations live as local
  overrides in a bridge module" — a statement, not an assertion.
- A blanket "submodule natives ⊆ override natives" check is **false**, measured on this tree, which is why no
  gate existed: of `OSMScoutClient.java`'s 59 natives per side, five exist only in the submodule
  (`calculateRouteWithObjectsAsync`, `getRegion`, `searchLocations(String,int,String,long)`, `setGpsMarker`,
  `startNavigation`) and five only in the override (`getAddressAt`, `getDatabaseBoundingBox`, `getMaxSpeedAt`,
  `reloadBasemap`, `searchLocationByForm`); the other four overridden files agree exactly. Measured with a
  declaration-level comparison (`native_parity_probe.py`, quoted in `evidence/surface.txt`).
- The item's own memory is stale in two details, and the tree is the fact: the submodule-only set no longer
  contains `cancelSearch` (the override declares it) nor the override-only set `renderInto` (both sides declare
  it, `ByteBuffer` imported differently) — 5 + 5, not 5 + 6.

Root cause, in one sentence: **the two Java sides of the JNI bridge are compared by nothing, so an API added to
one side is silent at the other — and the obvious blanket subset check cannot supply that comparison, because
the override diverges on purpose in both directions, so no one could write it as a pass/fail rule and the next
submodule API addition is again a silent `NoSuchMethodError` at the call site.**

### The red test

`app/src/test/java/com/naviveylin/build/NativeOverrideParityGuardTest.kt`, cases "the build refuses an
unrecorded deviation" and "the recorded deviations are the tree's own", on the base tree — no build gate and no
recorded list exist:

```
./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.NativeOverrideParityGuardTest" -PforceTests --no-build-cache
NativeOverrideParityGuardTest > the build refuses an unrecorded deviation FAILED
    java.lang.AssertionError: the app build must register the native parity gate (`tasks.register("checkNativeParity")`), which compares every overridden bridge file with the submodule's copy (issue #174, guidelines/Design.md §5)
NativeOverrideParityGuardTest > the recorded deviations are the tree's own FAILED
    java.lang.AssertionError: the deviation list must exist at …/osmscout-client-java/native-parity.txt — a blanket "submodule ⊆ override" check is false, so the intentional divergences are recorded here with a reason each (issue #174)
2 tests completed, 2 failed
```

JUnit XML `tests=2 failures=2`, timestamp `2026-10-22T11:30:13.144Z`
(`evidence/red-HEAD-NativeOverrideParityGuardTest.xml`). The item is a missing guard, so the assertion false on
HEAD is the one about the guard's own reach: the trees are correct today (the five-by-five divergence is
intentional), and what does not exist is the thing that would notice the next divergence.

The scan's own halves are `buildSrc`'s `NativeParityScannerTest` (five cases); they compile only with the
scanner, so the reach case above is the one that can be red on HEAD — the same bargain
`fix-versioned-gradle-java-home-guard` made with `VersionedGradleJavaHomeTest`.

Owning capability: `osmscout-jni` — requirement added by this change, "The override mirrors the submodule's
natives, except the recorded deviations". Owning guideline section: `guidelines/Design.md` §5, "The bridge's two
sides, the database-open asymmetry and the submodule discipline", which already lists the five overridden files
and now names the gate and the recorded list.

### Surface

```
git grep -lE "NativeParity|native-parity|native parity" HEAD -- tools app/src/test auto/src/test core/src/test buildSrc/src .github guidelines osmscout-client-java/src: 0 files on the base tree — nothing asserts the pair today
git show HEAD:app/build.gradle.kts | grep -c 'tasks.register("check': 8 on the base tree, 9 after (the new checkNativeParity)
git grep -lE "Mirror upstream APIs|mirror upstream" HEAD -- '*.md' ':!openspec/changes/archive/**' ':!app/src/main/cpp/**': 1 file, guidelines/Design.md §5
git grep -lE "overridden files" HEAD -- '*.md' ':!openspec/changes/archive/**': 3 files — guidelines/Design.md §5 (the sentence wraps, so the two-word phrase does not match it), AGENTS.md (which routes to §5 and states no rule of its own) and .pi/skills/native-bridge-signature-change/SKILL.md (which keeps the dead-copy warning, not the parity rule)
python3 evidence/native_parity_probe.py (declaration-level comparison, package qualifiers normalised): 59 natives per side in OSMScoutClient.java, 5 submodule-only + 5 override-only; the other four overridden files agree exactly (1/1, 0/0, 7/7, 0/0)
```

surface: 7 cases added (5 `buildSrc` fixture cases, 2 host cases), 0 cases rewritten, 0 fixtures realigned —
the rule had no assertion anywhere, so there was nothing to realign; the prose surface is 3 files
(`guidelines/Design.md` §5 the rule, `guidelines/Build.md` §2's configuration-cache measurement, the skill's
Notes naming the gate) and 1 new data file (`osmscout-client-java/native-parity.txt`, 10 recorded divergences)
— measured by the five commands above.

## What changes

1. `buildSrc/src/main/kotlin/com/naviveylin/build/jni/NativeParityScanner.kt` — the rule: per overridden file,
   the submodule's native declarations against the override's, compared with package qualifiers stripped, and
   the symmetric difference held to the recorded list. Both directions, plus stale and malformed recordings.
2. `buildSrc/src/test/kotlin/com/naviveylin/build/jni/NativeParityScannerTest.kt` — five fixture cases.
3. `osmscout-client-java/native-parity.txt` — the recorded divergences: five submodule-only, five
   override-only, one reason each, naming the caller or the reason the Android port drops it.
4. `app/build.gradle.kts` — `checkNativeParity`, wired into `preBuild` beside the other eight gates.
5. `app/src/test/java/com/naviveylin/build/NativeOverrideParityGuardTest.kt` — the two reach cases (the build
   wires the gate; the list records the tree's own divergences, five per direction).
6. `guidelines/Design.md` §5 — the paragraph that lists the five overridden files now names the gate, the list
   and what a new native must do; `guidelines/Build.md` §2's configuration-cache measurement gains the ninth
   task it names.

No application source, no native code, no manifest, no dependency, no release state. The capability delta is
one requirement with seven scenarios, each naming exactly one case.
