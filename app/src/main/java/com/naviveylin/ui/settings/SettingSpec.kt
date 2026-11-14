package com.naviveylin.ui.settings

import androidx.annotation.StringRes
import com.naviveylin.data.AppSettings

/**
 * Which surface a settings row belongs to (design D1; spec: `settings-taxonomy` — "Every row
 * declares the surface it belongs to"). [SHARED] is one stored value that both surfaces read and
 * either may edit; [PHONE] and [CAR] are values only their own surface shows.
 */
enum class SettingScope {
    SHARED,
    PHONE,
    CAR
}

/**
 * How a row presents and edits its value (design D5). Cycling a value by repeated taps is not a
 * control kind: a value with more than one option is a [CHOICE] inline or a [SELECT] sub-surface.
 */
enum class SettingControl {
    SWITCH,
    SLIDER,
    CHOICE,
    SELECT,
    LINK
}

/** One value a [SettingControl.CHOICE] or [SettingControl.SELECT] row offers. */
data class SettingChoice(
    /** The value as it is persisted, so a row never renames what the settings file holds. */
    val key: String,
    @param:StringRes val labelRes: Int
)

/** The payload a control kind needs beside its key; [SettingControl.SWITCH] and `LINK` carry none. */
sealed interface SettingControlPayload {
    /** A slider's endpoints and step, in the value's own unit. */
    data class Slider(val min: Int, val max: Int, val step: Int) : SettingControlPayload

    /** The ≤ 4 inline options of a [SettingControl.CHOICE] row. */
    data class Choice(val choices: List<SettingChoice>) : SettingControlPayload

    /** The options a [SettingControl.SELECT] row lists on its own sub-surface. */
    data class Select(val choices: List<SettingChoice>) : SettingControlPayload
}

/**
 * One settings row, declared once and rendered by both surfaces (design D1; spec:
 * `settings-taxonomy`). A row reads and writes [AppSettings], so it never touches a ViewModel and
 * the two renderers cannot drift: adding a setting is one entry in a topic table, not a composable
 * plus two call-site edits.
 *
 * @property key the stable identity of the row, unique across the whole registry
 *   ([SettingsRegistry]).
 * @property scope the one surface the row belongs to; [surfaceContentDescriptionRes] names that
 *   surface for a screen reader and is null exactly when [scope] is [SettingScope.SHARED] (spec:
 *   `settings-taxonomy` — "A surface-specific row is marked, a shared row is not").
 * @property read the row's display value, derived from the whole document.
 * @property write the transform a [SettingControl] applies; null for a `LINK` row, which changes no
 *   value.
 * @property enabled false when the row does not apply to the current document (design D1,
 *   conditional rows); a disabled row is omitted from its section rather than shown greyed.
 */
data class SettingSpec(
    val key: String,
    val topic: SettingsTopic,
    val section: SettingsSection,
    val scope: SettingScope,
    @param:StringRes val labelRes: Int,
    @param:StringRes val helpShortRes: Int? = null,
    @param:StringRes val helpLongRes: Int? = null,
    val control: SettingControl,
    val payload: SettingControlPayload? = null,
    @param:StringRes val surfaceContentDescriptionRes: Int? = null,
    val enabled: (AppSettings) -> Boolean = { true },
    val read: (AppSettings) -> String,
    val write: ((AppSettings, String) -> AppSettings)? = null
)
