package com.naviveylin.build.dex

import java.io.File

/**
 * The class descriptors a compiled Android artifact *defines* — read from the `.dex` files' `class_defs` table.
 *
 * A dex file names types in two places: `type_ids` holds every type the code mentions, `class_defs` only the ones
 * the file defines. The difference is the whole point of the gate this reads for: `MapProvider` was a *referenced*
 * type in the packaged APK — every caller of it carried its descriptor in `type_ids` — while no `class_defs` entry
 * defined it, and the app died on `ClassNotFoundException` at startup (issue #691). A search for the descriptor's
 * bytes therefore answers the wrong question, which is why this reads the table instead of grepping.
 *
 * The format is the one `dex-format` documents; the fields read here are the little-endian header words at fixed
 * offsets (56/60 string ids, 64/68 type ids, 96/100 class defs) and one `class_idx` per 32-byte `class_def_item`.
 * Strings are `.dex`'s modified UTF-8, whose only difference from UTF-8 in a class descriptor — ASCII — is the
 * NUL encoding, which no descriptor contains.
 */
object DexClassDescriptors {

    /** One `class_defs` entry's descriptor, e.g. `Lcom/framstag/libosmscout/client/MapProvider;`. */
    fun definedIn(file: File): Set<String> {
        val bytes = file.readBytes()
        require(bytes.size >= 112) { "$file is too short to be a dex file" }
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        require(magic == "dex\n") {
            "$file is not a standard dex file (magic '$magic'); a compact dex (`cdex`) lays its tables out " +
                "differently and is not read here"
        }

        val stringIdsSize = uint(bytes, 56)
        val stringIdsOff = uint(bytes, 60)
        val typeIdsOff = uint(bytes, 68)
        val classDefsSize = uint(bytes, 96)
        val classDefsOff = uint(bytes, 100)

        val descriptors = HashSet<String>(classDefsSize)
        for (index in 0 until classDefsSize) {
            val classIdx = uint(bytes, classDefsOff + index * 32)
            val descriptorIdx = uint(bytes, typeIdsOff + classIdx * 4)
            require(descriptorIdx < stringIdsSize) { "$file: type id $classIdx names string $descriptorIdx" }
            descriptors += stringData(bytes, uint(bytes, stringIdsOff + descriptorIdx * 4))
        }
        return descriptors
    }

    /** The union of the descriptors [files] define, reading only the ones with a `.dex` suffix. */
    fun definedIn(files: Iterable<File>): Set<String> = files
        .filter { it.isFile && it.name.endsWith(".dex") }
        .flatMap { definedIn(it) }
        .toSet()

    private fun uint(bytes: ByteArray, offset: Int): Int {
        require(offset + 4 <= bytes.size) { "dex read past its end at $offset" }
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun stringData(bytes: ByteArray, dataOff: Int): String {
        var offset = dataOff
        // uleb128 utf16 length, unused: the descriptor is read up to its NUL terminator.
        while (bytes[offset].toInt() and 0x80 != 0) {
            offset++
        }
        offset++
        val end = (offset until bytes.size).first { bytes[it] == 0.toByte() }
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }
}

/**
 * The classes a project module's Java sources declare — the "must be in the app" side of the gate, read from the
 * source roots rather than from a compiled artifact on purpose: a set derived from the jar or the classes directory
 * the app *already* receives shrinks with the defect it is supposed to catch (a jar built before the vendored
 * classes existed lists nothing, so every class is present). `Foo.java` declares `Foo` — javac's own rule for a
 * public type, and the shape both client source trees keep.
 */
object ClientApiClasses {

    /** `L<package>/<File>;` for every `*.java` file under [sourceRoot]. */
    fun descriptorsIn(sourceRoot: File): Set<String> {
        val root = sourceRoot.absoluteFile
        if (!root.isDirectory) {
            return emptySet()
        }
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".java")
                "L$relative;"
            }
            .toSet()
    }
}

/**
 * The gate over a packaged Android artifact: every class the JNI bridge's Java side compiles SHALL be defined by
 * the dex files the app ships.
 *
 * It exists because `:osmscout-client-java` compiles two provenances into two classes directories (#678) and an
 * Android consumer reads one of them: `main`'s `classesDirs` collection, not the jar that carries both. The app
 * therefore packaged the six local overrides alone and crashed at startup with
 * `ClassNotFoundException: com.framstag.libosmscout.client.MapProvider` — a green build, a complete jar in
 * `build/libs`, and no app (issue #691). Nothing else in the build looks at the dex, so the wiring repair and this
 * gate are one change.
 */
object ClientApiDexGate {

    /** The required descriptors [defined] does not carry, sorted for a stable report. */
    fun missing(required: Collection<String>, defined: Collection<String>): List<String> =
        required.filter { it !in defined }.sorted()

    /**
     * The message of a failing gate: what is absent, and why an absent class is not a packaging detail.
     *
     * @param missing the descriptors [missing] returned
     * @param dexPaths the scanned dex locations, relative to the project, so the reader can re-run the same scan
     */
    fun report(missing: List<String>, dexPaths: List<String>): String = buildString {
        appendLine("The packaged dex defines no class for ${missing.size} of the JNI bridge's Java classes:")
        missing.forEach { appendLine("  ${it.removePrefix("L").removeSuffix(";").replace('/', '.')}") }
        appendLine()
        appendLine("Scanned: ${dexPaths.joinToString(", ")}")
        appendLine()
        appendLine("Every caller of these classes survives compilation — they are referenced types, not missing")
        appendLine("symbols — and the app dies at the first one with ClassNotFoundException, before its first")
        appendLine("frame. The jar `:osmscout-client-java` publishes carries both provenances; an Android consumer")
        appendLine("dexes `main`'s classes directories, so the vendored half has to be one of them (issue #691,")
        appendLine("spec test-coverage — the vendored classes reach the packaged app).")
    }
}
