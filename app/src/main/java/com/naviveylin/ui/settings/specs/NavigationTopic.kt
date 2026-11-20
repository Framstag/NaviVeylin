package com.naviveylin.ui.settings.specs

import com.naviveylin.R
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AppSettings
import com.naviveylin.ui.settings.SettingChoice
import com.naviveylin.ui.settings.SettingControl
import com.naviveylin.ui.settings.SettingControlPayload
import com.naviveylin.ui.settings.SettingScope
import com.naviveylin.ui.settings.SettingSpec
import com.naviveylin.ui.settings.SettingsSection
import com.naviveylin.ui.settings.SettingsTopic

/**
 * The rows of the "Navigation & Route" topic (spec: `settings-surface` — "Navigation & Route carries
 * routing values"; spec: `routing-max-speed`; spec: `routing-avoidance`).
 *
 * The values are the ones the quick sheet used to edit, moved here unchanged: the three stored
 * maximum speeds with their ranges and defaults, the three avoidance choices, and the vehicle
 * anchors. The phone's two anchors are [SettingScope.SHARED] rows; the car's two are
 * [SettingScope.CAR] rows over its own fields and carry the third value a car anchor has — it
 * inherits the phone's until someone sets it (spec: `settings-surface` — "The phone edits the
 * settings Android Auto owns").
 *
 * The ranges are the payload's, not a test's: `R.string.max_speed_value` renders the value and both
 * endpoint labels (`SliderControl`), so a row cannot show a range its slider does not offer.
 */

/** The three routing values' section: the speeds the router calculates with. */
private val speedSection =
    SettingsSection(SettingsTopic.NAVIGATION_AND_ROUTE, R.string.max_speed_section)

/** The three avoidance choices' section, beside the speeds they belong with. */
private val avoidanceSection =
    SettingsSection(SettingsTopic.NAVIGATION_AND_ROUTE, R.string.avoidance_section)

/** The anchor rows' section. The 5×3 picker's labels come from the shared enum. */
private val anchorSection =
    SettingsSection(SettingsTopic.NAVIGATION_AND_ROUTE, R.string.vehicle_position)

/** Every anchor preset as a picker option, laid out in the five columns the picker's grid has. */
private val anchorChoices: List<SettingChoice> =
    VehicleAnchorPosition.entries.map { SettingChoice(key = it.id, label = it.label) }

/**
 * The car anchors' third value: the empty key, which is what the stored field holds while the car
 * has no anchor of its own (spec: `settings-surface` — "Returning to the phone value").
 */
private val inheritingChoice = SettingChoice(key = "", labelRes = R.string.settings_anchor_inherit)

/**
 * One maximum-speed row: the stored value as the bare number its row formats
 * ([SettingSpec.valueFormatRes]), and the write that persists a slider's choice (spec:
 * `routing-max-speed` — A per-vehicle maximum speed is a persisted setting).
 */
private fun speedRow(
    key: String,
    labelRes: Int,
    minKmh: Int,
    maxKmh: Int,
    read: (AppSettings) -> Int,
    write: (AppSettings, Int) -> AppSettings
): SettingSpec = SettingSpec(
    key = key,
    topic = SettingsTopic.NAVIGATION_AND_ROUTE,
    section = speedSection,
    scope = SettingScope.SHARED,
    labelRes = labelRes,
    helpShortRes = R.string.settings_help_max_speed,
    control = SettingControl.SLIDER,
    payload = SettingControlPayload.Slider(min = minKmh, max = maxKmh, step = 1),
    valueFormatRes = R.string.max_speed_value,
    read = { settings -> read(settings).toString() },
    write = { settings, value -> write(settings, value.toInt().coerceIn(minKmh, maxKmh)) }
)

/**
 * One avoidance row: a switch whose two values are `"on"` and `"off"`. Both surfaces read the stored
 * choice through the one routing-profile builder (spec: `routing-avoidance` — One builder produces the
 * profile every calculation uses).
 */
private fun avoidanceRow(
    key: String,
    labelRes: Int,
    read: (AppSettings) -> Boolean,
    write: (AppSettings, Boolean) -> AppSettings
): SettingSpec = SettingSpec(
    key = key,
    topic = SettingsTopic.NAVIGATION_AND_ROUTE,
    section = avoidanceSection,
    scope = SettingScope.SHARED,
    labelRes = labelRes,
    helpShortRes = R.string.settings_help_avoidance,
    control = SettingControl.SWITCH,
    read = { settings -> if (read(settings)) "on" else "off" },
    write = { settings, value -> write(settings, value == "on") }
)

/**
 * One phone anchor row: the preset's shared label as its value, and the preset's stable id as what
 * it persists, so an unknown id in a hand-edited file falls back to the same preset the map frames
 * with (spec: `smooth-follow` — Vehicle position anchor in follow mode).
 */
private fun anchorRow(
    key: String,
    labelRes: Int,
    read: (AppSettings) -> String,
    write: (AppSettings, String) -> AppSettings
): SettingSpec = SettingSpec(
    key = key,
    topic = SettingsTopic.NAVIGATION_AND_ROUTE,
    section = anchorSection,
    scope = SettingScope.SHARED,
    labelRes = labelRes,
    helpShortRes = R.string.settings_help_anchor,
    control = SettingControl.SELECT,
    payload = SettingControlPayload.Select(choices = anchorChoices, columns = 5),
    read = { settings -> VehicleAnchorPosition.fromId(read(settings)).id },
    write = { settings, value -> write(settings, value) }
)

/**
 * One car anchor row: the phone edits the car's own field, and a field that is null still means
 * "inherit the phone's value", so the row offers that state first and writes null back when it is
 * chosen (spec: `settings-surface` — "An inheriting anchor is shown as inheriting", "Returning to the
 * phone value").
 */
private fun carAnchorRow(
    key: String,
    labelRes: Int,
    read: (AppSettings) -> String?,
    write: (AppSettings, String?) -> AppSettings
): SettingSpec = SettingSpec(
    key = key,
    topic = SettingsTopic.NAVIGATION_AND_ROUTE,
    section = anchorSection,
    scope = SettingScope.CAR,
    labelRes = labelRes,
    helpShortRes = R.string.settings_help_car_anchor,
    control = SettingControl.SELECT,
    payload = SettingControlPayload.Select(
        choices = listOf(inheritingChoice) + anchorChoices,
        columns = 5
    ),
    surfaceContentDescriptionRes = R.string.settings_scope_car,
    read = { settings -> read(settings).orEmpty() },
    write = { settings, value -> write(settings, value.ifEmpty { null }) }
)

internal val navigationTopicRows: List<SettingSpec> = listOf(
    speedRow(
        key = "car-max-speed",
        labelRes = R.string.vehicle_car,
        minKmh = 30,
        maxKmh = 160,
        read = { settings -> settings.carMaxSpeedKmh },
        write = { settings, kmh -> settings.copy(carMaxSpeedKmh = kmh) }
    ),
    speedRow(
        key = "bicycle-max-speed",
        labelRes = R.string.vehicle_bicycle,
        minKmh = 5,
        maxKmh = 40,
        read = { settings -> settings.bicycleMaxSpeedKmh },
        write = { settings, kmh -> settings.copy(bicycleMaxSpeedKmh = kmh) }
    ),
    speedRow(
        key = "foot-max-speed",
        labelRes = R.string.vehicle_pedestrian,
        minKmh = 2,
        maxKmh = 15,
        read = { settings -> settings.footMaxSpeedKmh },
        write = { settings, kmh -> settings.copy(footMaxSpeedKmh = kmh) }
    ),
    avoidanceRow(
        key = "avoid-tolls",
        labelRes = R.string.avoid_tolls,
        read = { settings -> settings.avoidTolls },
        write = { settings, avoid -> settings.copy(avoidTolls = avoid) }
    ),
    avoidanceRow(
        key = "avoid-ferries",
        labelRes = R.string.avoid_ferries,
        read = { settings -> settings.avoidFerries },
        write = { settings, avoid -> settings.copy(avoidFerries = avoid) }
    ),
    avoidanceRow(
        key = "avoid-unpaved",
        labelRes = R.string.avoid_unpaved,
        read = { settings -> settings.avoidUnpaved },
        write = { settings, avoid -> settings.copy(avoidUnpaved = avoid) }
    ),
    anchorRow(
        key = "routing-anchor",
        labelRes = R.string.vehicle_position_routing,
        read = { settings -> settings.routingAnchorId },
        write = { settings, id -> settings.copy(routingAnchorId = id) }
    ),
    anchorRow(
        key = "free-driving-anchor",
        labelRes = R.string.vehicle_position_free_driving,
        read = { settings -> settings.freeDrivingAnchorId },
        write = { settings, id -> settings.copy(freeDrivingAnchorId = id) }
    ),
    carAnchorRow(
        key = "car-routing-anchor",
        labelRes = R.string.vehicle_position_routing,
        read = { settings -> settings.autoRoutingAnchorId },
        write = { settings, id -> settings.copy(autoRoutingAnchorId = id) }
    ),
    carAnchorRow(
        key = "car-free-driving-anchor",
        labelRes = R.string.vehicle_position_free_driving,
        read = { settings -> settings.autoFreeDrivingAnchorId },
        write = { settings, id -> settings.copy(autoFreeDrivingAnchorId = id) }
    )
)
