## Root cause

`app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt` walks the repository with

```kotlin
repoRoot.walkTopDown()
    .onEnter { it.name !in SKIPPED_DIRECTORIES }   // "build", ".git", ".gradle", ".idea", ".pi", "node_modules"
```

and the project keeps its own sources in package directories named `build`: `com.naviveylin.build` under
`app/src/test/java/` (9 `.kt` files, the guard's own among them) and under `buildSrc/src/main/kotlin/` and
`buildSrc/src/test/kotlin/` (28 more). A name is not a provenance: the prune was written for Gradle's output
directory, and it also removes 37 project sources from a scan whose requirement and guideline both say
"every `*.kt` the project owns" (`openspec/specs/build-jvm-toolchain/spec.md:108`,
`guidelines/Build.md:1207`).

Measured on base `3f972aec` — one mechanism, not two:

- the tree owns 805 `*.kt` files (`find . -name '*.kt' | wc -l` → `805`);
- the scan reads 768 (`read 768` in the red case's assertion, `evidence/TEST-ComposeApiDeprecationTest-red.xml`);
- the 37 it does not read are all under a `com/naviveylin/build` package directory
  (`find . -name '*.kt' | awk -F/ '{for(i=2;i<NF;i++) if($i=="build") {print; next}}' | wc -l` → `37`);
- none of them is Gradle output: `find app/build buildSrc/build build core/build auto/build -name '*.kt' |
  wc -l` → `0`, so nothing the prune was written for is lost by pruning by marker instead of by name;
- the deprecated spelling is absent from all 805 (`files=768 findings=0` on the pre-fix tree, so the 37 newly
  read files cannot turn the scan red).

## The single fix

Prune a `build` directory only when a Gradle project root writes into it — the marker its parent carries:

```kotlin
.onEnter { !it.isGradleOutput() && it.name !in SKIPPED_TOOL_DIRECTORIES }

private fun File.isGradleOutput(): Boolean =
    name == "build" && parentFile?.let {
        File(it, "build.gradle.kts").isFile || File(it, "build.gradle").isFile
    } == true
```

`./build`, `app/build`, `buildSrc/build`, `core/build` still prune — each has a `build.gradle.kts` beside it —
and `com/naviveylin/build/` is read, because no Gradle marker stands beside it.

The second half is the guard's own file, which the scan now reads: its probe literals and its failure message
carry the deprecated spelling, so they are assembled from parts
(`val DEPRECATED = "quadratic" + "Bezier" + "To"` and `path.$DEPRECATED(…)` / `relative$DEPRECATED`), and the
scan reports nothing for the file it lives in. This is what keeps the guard's promise true rather than
exempting it: a deprecated call *in that file* is still reported, a spelling inside a string literal is not.

The new case holds the delta at both ends: the two paths the name-prune hid are in the scan's set, and no path
under `app/build/` is.

## Blast radius

One test file (`app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`: the walk, the probes, and
one added case), one guideline paragraph (`guidelines/Build.md`, the "Code coverage" section, its count and the sentence naming the
prune), one capability requirement scenario (`openspec/specs/build-jvm-toolchain/spec.md`). No main source, no
native code, no manifest, no dependency, no release version state, no CI workflow: the guard runs inside the
gate's existing `:app` unit-test step, and `app/build`/`buildSrc/build` hold no `.kt` file, so the file count
the case prints moves 768 → 805 without moving the compile.

## What the evidence eliminated

- **Narrow the stated contract to the directories the scan reads** (the second route issue #223 names).
  Eliminated by measurement: the directories the old prune reads are "everything except a directory named
  `build`", which is not a contract a requirement can state, and the count it would have to quote drifts on
  every added file — the paragraph already quotes 731 (`guidelines/Build.md:1207`) while the tree carries 805
  and the scan read 768, so the narrowed sentence would be wrong today as well. The wide promise is also
  already merged (`openspec/specs/build-jvm-toolchain/spec.md:108`), and narrowing it would restore exactly
  the gap the reviewer refused round 2 for
  (`openspec/changes/archive/2026-10-20-fix-deprecated-quadratic-path-builder/evidence/review-failed-round-2.md`).
- **Exempt the guard's own file from the scan** (`if (it.name == "ComposeApiDeprecationTest.kt") return@…`).
  Eliminated on the same promise: the requirement says every `*.kt` the project owns, and a file-path exception
  is a second, unstated promise. Assembling the probes from parts costs two string concatenations and keeps the
  file inside the scan.
- **Turn Kotlin warnings into errors instead of scanning** — already eliminated by the refused change's own
  measurement (53 `w:` lines in its gate compile, 28 in its red run): the flag would fail the gate on warnings
  this baseline does not own.
- **A separate shell suite instead of an `:app` unit-test case.** Eliminated by wiring cost, not by a number:
  the case already runs in the gate's `:app` step in both flavours, and a new suite would need its own glob in
  `tools/loop/loop.conf` and its own gate entry.
