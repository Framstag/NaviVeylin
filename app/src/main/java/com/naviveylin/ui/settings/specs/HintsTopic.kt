package com.naviveylin.ui.settings.specs

import com.naviveylin.R
import com.naviveylin.ui.settings.SettingControl
import com.naviveylin.ui.settings.SettingControlPayload
import com.naviveylin.ui.settings.SettingScope
import com.naviveylin.ui.settings.SettingSpec
import com.naviveylin.ui.settings.SettingsSection
import com.naviveylin.ui.settings.SettingsTopic

/** The spoken guidance rows of the topic (spec: `voice-guidance`). */
internal val hintsSpeechSection = SettingsSection(SettingsTopic.HINTS_AND_LANGUAGE, R.string.settings_section_speech)

/** The rows that announce something on the map or in a session. */
internal val hintsAnnouncementsSection =
    SettingsSection(SettingsTopic.HINTS_AND_LANGUAGE, R.string.settings_section_announcements)

/**
 * The rows of the "Hinweise & Sprache" topic (spec: `settings-taxonomy`, spec: `voice-guidance`).
 *
 * Each row is one persisted value, read from and written to [com.naviveylin.data.AppSettings]; the
 * surface renders them and the controller persists the write (design D1, D2). The phone's own voice
 * switch is phone-only, the car's switch is marked for Android Auto and writes only the car's value,
 * and the announcement rows are shared — one value both surfaces read (spec: `voice-guidance`).
 */
internal val hintsTopicRows: List<SettingSpec> = listOf(
    SettingSpec(
        key = "voice-guidance-phone",
        topic = SettingsTopic.HINTS_AND_LANGUAGE,
        section = hintsSpeechSection,
        scope = SettingScope.PHONE,
        labelRes = R.string.voice_guidance_switch,
        helpShortRes = R.string.settings_help_voice_phone,
        control = SettingControl.SWITCH,
        surfaceContentDescriptionRes = R.string.settings_scope_phone,
        read = { settings -> if (settings.voiceGuidanceEnabled) "on" else "off" },
        write = { settings, value -> settings.copy(voiceGuidanceEnabled = value == "on") }
    ),
    SettingSpec(
        key = "voice-guidance-car",
        topic = SettingsTopic.HINTS_AND_LANGUAGE,
        section = hintsSpeechSection,
        scope = SettingScope.CAR,
        labelRes = R.string.settings_voice_guidance_car,
        helpShortRes = R.string.settings_help_voice_car,
        helpLongRes = R.string.settings_help_voice_car_long,
        control = SettingControl.SWITCH,
        surfaceContentDescriptionRes = R.string.settings_scope_car,
        read = { settings -> if (settings.autoVoiceGuidanceEnabled) "on" else "off" },
        write = { settings, value -> settings.copy(autoVoiceGuidanceEnabled = value == "on") }
    ),
    SettingSpec(
        key = "gps-announcements",
        topic = SettingsTopic.HINTS_AND_LANGUAGE,
        section = hintsAnnouncementsSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.gps_announcements_switch,
        helpShortRes = R.string.settings_help_gps_announcements,
        control = SettingControl.SWITCH,
        read = { settings -> if (settings.announceGpsState) "on" else "off" },
        write = { settings, value -> settings.copy(announceGpsState = value == "on") }
    ),
    SettingSpec(
        key = "lane-hints",
        topic = SettingsTopic.HINTS_AND_LANGUAGE,
        section = hintsAnnouncementsSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.lane_instructions,
        helpShortRes = R.string.settings_help_lane_hints,
        control = SettingControl.SWITCH,
        read = { settings -> if (settings.laneHintsEnabled) "on" else "off" },
        write = { settings, value -> settings.copy(laneHintsEnabled = value == "on") }
    ),
    SettingSpec(
        key = "overspeed-warning",
        topic = SettingsTopic.HINTS_AND_LANGUAGE,
        section = hintsAnnouncementsSection,
        scope = SettingScope.SHARED,
        labelRes = R.string.overspeed_warning_delta,
        helpShortRes = R.string.settings_help_overspeed,
        helpLongRes = R.string.settings_help_overspeed_long,
        control = SettingControl.SLIDER,
        // 0-30 km/h in whole km/h, the default 5 the setting carries (spec: `settings-surface` — "The
        // overspeed delta is a bounded slider"; spec: `map-speed-widget`).
        payload = SettingControlPayload.Slider(min = 0, max = 30, step = 1),
        valueFormatRes = R.string.overspeed_delta_value,
        read = { settings -> settings.overspeedWarningDeltaKmh.toString() },
        write = { settings, value ->
            settings.copy(overspeedWarningDeltaKmh = value.toInt().coerceIn(0, 30))
        }
    )
)
