package com.naviveylin.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Copies bundled assets (stylesheets, icons) to internal storage on first launch. */
@Singleton
class AssetCopier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Ensure stylesheets are available on disk. Returns the stylesheets directory path. */
    fun ensureStylesheets(): String {
        val destDir = File(context.filesDir, "stylesheets")
        if (destDir.exists()) {
            Log.d(TAG, "stylesheets already at $destDir")
            return destDir.absolutePath
        }

        Log.d(TAG, "copying stylesheets from assets to $destDir")
        try {
            copyAssetDir("stylesheets", destDir)
            Log.d(TAG, "stylesheets copied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "failed to copy stylesheets", e)
        }
        return destDir.absolutePath
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        val list = context.assets.list(assetPath) ?: return
        destDir.mkdirs()

        for (name in list) {
            val subPath = "$assetPath/$name"
            val destFile = File(destDir, name)
            if (context.assets.list(subPath)?.isNotEmpty() == true) {
                // Subdirectory
                copyAssetDir(subPath, destFile)
            } else {
                context.assets.open(subPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AssetCopier"
    }
}
