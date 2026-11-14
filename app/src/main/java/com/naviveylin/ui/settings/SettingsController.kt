package com.naviveylin.ui.settings

import com.naviveylin.data.AppSettings
import com.naviveylin.data.SettingsStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one writer of the persisted settings and the one document both hosts read (design D2; spec:
 * `settings-taxonomy` — "Every row declares the surface it belongs to").
 *
 * It wraps [SettingsStorage] rather than replacing it: the storage keeps the file, the lock and the
 * lenient decode ([SettingsStorage.update] — "Settings writes are serialized read-modify-write
 * transactions"), and this controller adds the single seam above it. Two things follow from being
 * one seam rather than one per host:
 *
 * - a write publishes the document the file now holds on [settings], so the phone surface, the start
 *   screen and the map screen cannot show two different settings files;
 * - the surface's own position ([SettingsSurfaceState]) is held here too, so "one settings surface at
 *   a time" is a property of the state rather than a convention each host re-implements.
 */
@Singleton
class SettingsController @Inject constructor(
    private val storage: SettingsStorage
) {
    private val _settings = MutableStateFlow(AppSettings())

    /**
     * The persisted document as both hosts see it. Its value is only ever a document the file
     * holds: [update] publishes the transform's result once the write completed, and a write that
     * changes nothing emits nothing.
     */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _surfaceState = MutableStateFlow<SettingsSurfaceState>(SettingsSurfaceState.Closed)

    /** Which page of the settings surface is showing. */
    val surfaceState: StateFlow<SettingsSurfaceState> = _surfaceState.asStateFlow()

    /**
     * Read the persisted document into [settings] and return it.
     *
     * A host calls this once on its start path; the map screen keeps its own in-flight-read wins rule
     * around the returned value (spec: `dark-mode` — A user change outranks an in-flight settings
     * read), so the value is returned as well as published.
     */
    suspend fun load(): AppSettings {
        val loaded = storage.load()
        _settings.value = loaded
        return loaded
    }

    /**
     * Apply [transform] to the document the file holds, as one write, and publish the result.
     *
     * The transform runs once and on the persisted document, not on the published one, so a value
     * another seam wrote in the meantime is never reverted; the document the write produced is what
     * [settings] then emits — one write, one emission, no in-memory copy ahead of the file.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var written = _settings.value
        storage.update { current -> transform(current).also { written = it } }
        _settings.value = written
    }

    /** Show the root page, whatever page was showing before. */
    fun openRoot() {
        _surfaceState.value = SettingsSurfaceState.Root
    }

    /** Show one topic's rows, whatever page was showing before. */
    fun openTopic(topic: SettingsTopic) {
        _surfaceState.value = SettingsSurfaceState.Topic(topic)
    }

    /** Leave the settings surface entirely. */
    fun close() {
        _surfaceState.value = SettingsSurfaceState.Closed
    }

    /**
     * Go up one level: a topic returns to the root, the root closes the surface, and a closed
     * surface stays closed (design D3 — back is level by level, and the base map keeps its own).
     */
    fun back() {
        _surfaceState.value = when (val state = _surfaceState.value) {
            SettingsSurfaceState.Closed -> SettingsSurfaceState.Closed
            SettingsSurfaceState.Root -> SettingsSurfaceState.Closed
            is SettingsSurfaceState.Topic -> SettingsSurfaceState.Root
        }
    }
}
