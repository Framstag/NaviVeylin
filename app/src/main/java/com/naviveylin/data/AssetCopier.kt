package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies bundled assets (stylesheets, raster POI icons) to internal storage and keeps
 * the on-device copy in sync with the APK on every app start.
 *
 * Both trees are packaged from the libosmscout submodule at build time, so the bundled
 * set changes whenever the submodule bumps. A plain "copy once" strategy would leave
 * existing installs on stale assets forever (app updates preserve [Context.filesDir]).
 * Instead each start mirrors the bundle: changed or missing files are copied, files no
 * longer bundled are deleted.
 *
 * Note on [android.content.res.AssetManager.list]: real Android returns only
 * direct children of a directory; some implementations (Robolectric) flatten
 * nested paths into the parent listing ("include/roads.oss" listed at the
 * "stylesheets" level). The traversal below handles both forms.
 */
@Singleton
class AssetCopier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** The assets root containing the stylesheet tree. */
    private val stylesheetsAssetRoot = "stylesheets"

    /**
     * Ensure stylesheets are available on disk, refreshed from the bundled assets.
     * Returns the stylesheets directory path.
     */
    fun ensureStylesheets(): String =
        ensureAssetTree(stylesheetsAssetRoot, File(context.filesDir, stylesheetsAssetRoot)).absolutePath

    /**
     * Ensure the raster POI icon set is available on disk, refreshed from the bundled assets.
     *
     * Returns the directory the native renderer must be configured with
     * ([IconAssets.clientDirectory] — absolute, with the trailing separator the loader's
     * `path + name + ".png"` construction needs). Same mirror semantics as the stylesheets, so an
     * app update delivers icon changes without clearing data (spec `map-render`).
     */
    fun ensureIcons(): String {
        ensureAssetTree(IconAssets.ASSET_ROOT, IconAssets.deviceRoot(context.filesDir))
        return IconAssets.clientDirectory(context.filesDir)
    }

    /**
     * Mirrors the packaged asset tree [assetRoot] onto [destDir]: copy what is missing or changed,
     * delete what is no longer bundled. Failure is reported and not fatal — a stale but present tree
     * keeps the map usable (spec `map-render` — refresh contracts).
     */
    private fun ensureAssetTree(assetRoot: String, destDir: File): File {
        Log.d(TAG, "refreshing $assetRoot from assets to $destDir")
        try {
            val bundled = mutableListOf<String>()
            listAssetFiles(assetRoot, bundled)
            val bundledSet = bundled.toSet()

            // 1. Copy missing or changed files (creating parent dirs).
            for (assetPath in bundled) {
                val relPath = assetPath.removePrefix("$assetRoot/")
                val destFile = File(destDir, relPath)
                destFile.parentFile?.mkdirs()
                if (!destFile.exists() || contentDiffers(assetPath, destFile)) {
                    context.assets.open(assetPath).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "copied $assetPath")
                }
            }

            // 2. Mirror semantics: drop local files no longer bundled.
            deleteStale(destDir, destDir, assetRoot, bundledSet)
        } catch (e: Exception) {
            Log.e(TAG, "failed to refresh $assetRoot", e)
        }
        return destDir
    }

    /** Collects every file asset under [assetPath], handling flattened listings. */
    private fun listAssetFiles(assetPath: String, out: MutableList<String>) {
        val names = context.assets.list(assetPath) ?: return
        for (name in names) {
            if (name.contains('/')) {
                // Flattened listing (Robolectric): "include/roads.oss" is a file.
                out += "$assetPath/$name"
                continue
            }
            val subPath = "$assetPath/$name"
            val isDirectory = context.assets.list(subPath)?.isNotEmpty() == true
            if (isDirectory) {
                listAssetFiles(subPath, out)
            } else {
                out += subPath
            }
        }
    }

    /** Deletes files under [dir] whose relative path is not part of [bundled]. */
    private fun deleteStale(dir: File, root: File, assetRoot: String, bundled: Set<String>) {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteStale(child, root, assetRoot, bundled)
                if (child.list()?.isEmpty() == true) child.delete()
            } else {
                val relPath = root.toURI().relativize(child.toURI()).path
                if ("$assetRoot/$relPath" !in bundled) {
                    child.delete()
                    Log.d(TAG, "removed stale $relPath")
                }
            }
        }
    }

    /** True when the asset content differs from [destFile] (size or SHA-256). */
    private fun contentDiffers(assetPath: String, destFile: File): Boolean {
        val assetBytes = context.assets.open(assetPath).use { it.readBytes() }
        if (destFile.length() != assetBytes.size.toLong()) return true
        val destBytes = destFile.readBytes()
        if (destBytes.size != assetBytes.size) return true
        return !MessageDigest.isEqual(sha256(assetBytes), sha256(destBytes))
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private const val TAG = "AssetCopier"
    }
}
