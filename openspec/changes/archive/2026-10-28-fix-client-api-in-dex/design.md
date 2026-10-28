# Design — the vendored classes directory, and the gate that reads the dex

## The artifact an Android consumer reads is `classesDirs`

Measured, not read off a document. With the split as #678 left it, and the build cache kept out of the answer
(`rm -rf app/build/intermediates/dex/mobileDebug`, then `--no-build-cache`), the built APK defined 0 of the
module's 48 compiled classes (`buildSrc` reader, `class_defs` only). The jar in `build/libs` carried all 48.

Two wiring candidates were tried in the order of least surprise:

| candidate | result |
|---|---|
| `sourceSets["main"].output.dir(sourceSets["vendored"].output.classesDirs)` | **did not work** — the dex still defined none of them. `output.dir(...)` feeds `SourceSetOutput.files`; the Android consumer resolves `classesDirs`, a separate collection |
| `(sourceSets["main"].output.classesDirs as ConfigurableFileCollection).from(sourceSets["vendored"].output.classesDirs)` | works — the APK defines 48/48 |

The dependency's `runtimeElements` variant is already `libraryelements=jar`
(`./gradlew :app:dependencyInsight --configuration mobileDebugRuntimeClasspath --dependency osmscout-client-java`),
so "ask for the jar" is not a knob: the jar is what the Java runtime classpath gets, and AGP's project-dex path
takes the classes directories regardless. The cast is the collection Gradle returns
(`DefaultConfigurableFileCollection`), and the comment in the build file says why the obvious call does not work,
so the next reader does not undo it.

## The coverage split survives, and it was re-measured

`jacocoTestReport` used `main`'s `classesDirs` collection, which now carries the vendored directory too — the own
row would have measured both provenances. It reads the override compile's own destination instead
(`compileJava.flatMap { it.destinationDirectory }`), which is the directory `main` compiled into and no other.
Re-measured after the change: the own row lists **10** classes (the six override files and their inner classes),
the vendored row **38**. That is #678's split, unchanged.

## The gate answers from `class_defs`, not from the bytes

`MapProvider` was *referenced* by the packaged code, so its descriptor was present in the APK's `type_ids` — a
byte search or a `strings | grep` finds it and reports green. The gate therefore parses the dex header and reads
one descriptor per `class_def_item`: 112-byte header, string ids at 56/60, type ids at 64/68, class defs at
96/100, one `class_idx` per 32-byte item. The parser is ~40 lines and takes the magic `dex\n` only — a compact dex
lays its tables out differently, and Android's packaging does not produce one here.

The **required** side is read from the two source roots (`app/src/main/cpp/libosmscout/libosmscout-client-java/java`
and `osmscout-client-java/src/main/java`), not from the jar or the classes directory the app already receives: a set
derived from an artifact the defect also empties (`a jar built before the vendored classes existed lists nothing,
so nothing is missing`) can never fail. `Foo.java` declares `Foo` — javac's rule for the public type, and the shape
both client trees keep (checked: 29 + 6 files, every one a top-level type of its own name).

## Where the gate runs, and what it costs

`:app:checkClientApiInDex<MobileDebug|MobileRelease|AutomotiveDebug|AutomotiveRelease>` reads the variant's whole
`intermediates/dex/<variant>` tree and depends on that variant's `dexBuilder`/`merge*Dex` tasks; each
`assemble<variant>` and `bundle<variant>` depends on its own gate. That is the step that packages the artifact, and
it is what CI runs (`Build debug APK` → `:app:assembleMobileDebug`), so no workflow change is needed and no new
step can be forgotten. Cost: 25 dex files read in well under a second beside a packaging step that takes minutes.

It is a typed task in buildSrc, not a script-level `tasks.register {}` closure, because the project's script-level
gates cost configuration-cache problems (`guidelines/Build.md` §7). It is `@InputFiles`-annotated and has no
outputs, so it runs whenever it is in the graph and never answers from a cache entry that described a different
tree — which is how the defect stayed invisible: the APK in the tree had been restored from the build cache.
