package com.naviveylin.ui.about

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The license inventory generated into the APK's assets at build time.
 *
 * Every field comes from the build's own SBOM, so what a user reads here is what
 * the license gate validated — there is no second hand-kept list.
 */
@Serializable
data class LicenseInventory(
    val appVersion: String? = null,
    val components: List<LicenseComponent> = emptyList(),
    val texts: List<LicenseTextSource> = emptyList(),
    val links: List<LicenseLink> = emptyList(),
)

/**
 * One bundled component.
 *
 * @param textFile license text file under `licenses/texts/`, or null when the
 *   text is not distributed with the application (then [licenseUrl] points at
 *   the canonical terms instead)
 * @param scope `shipped` for code distributed in this build, `buildTimeOnly` for
 *   build tooling that ships nothing
 * @param note an unresolved doubt about the license claim, shown to the user
 *   rather than hidden
 */
@Serializable
data class LicenseComponent(
    val name: String,
    val group: String? = null,
    val version: String? = null,
    val identifier: String,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val scope: String = "shipped",
    val noticeRequired: Boolean = false,
    val textFile: String? = null,
    val note: String? = null,
) {
    /** `group:name` where a group exists, matching how the SBOM names it. */
    val qualifiedName: String get() = if (group.isNullOrBlank()) name else "$group:$name"

    val isDistributed: Boolean get() = scope == SHIPPED_SCOPE

    companion object {
        const val SHIPPED_SCOPE = "shipped"
    }
}

/** Where one identifier's license text came from, for review. */
@Serializable
data class LicenseTextSource(
    val identifier: String,
    @SerialName("source") val sourceDescription: String,
)

/** A license whose terms live at a URL rather than in the application. */
@Serializable
data class LicenseLink(
    val identifier: String,
    val name: String,
    val url: String,
)

/**
 * Reads the generated license data.
 *
 * An interface so the ViewModel and the screen can be tested against fixtures
 * without an Android asset manager.
 */
interface LicenseInventorySource {
    /** The inventory packaged in this build. */
    suspend fun loadInventory(): LicenseInventory

    /** The full text of one component's license. */
    suspend fun loadText(fileName: String): String
}

/**
 * Reads the inventory from the APK's assets, where
 * `generateLicenseAssets<Variant>` wrote it.
 *
 * Asset reads are blocking IO, so every access runs on [ioDispatcher]
 * (`guidelines/Design.md` §4).
 */
class AssetLicenseInventorySource(
    private val context: Context,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : LicenseInventorySource {

    override suspend fun loadInventory(): LicenseInventory = withContext(ioDispatcher) {
        val json = context.assets.open(INVENTORY_ASSET).use { it.readBytes().decodeToString() }
        parseLicenseInventory(json)
    }

    override suspend fun loadText(fileName: String): String = withContext(ioDispatcher) {
        context.assets.open("$TEXT_ASSET_ROOT/$fileName").use { it.readBytes().decodeToString() }
    }

    companion object {
        const val INVENTORY_ASSET = "licenses/dependencies.json"
        const val TEXT_ASSET_ROOT = "licenses/texts"
    }
}

private val licenseJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a generated inventory document. Unknown keys are ignored so a newer
 * generator cannot break an older reader.
 */
fun parseLicenseInventory(json: String): LicenseInventory = licenseJson.decodeFromString(json)
