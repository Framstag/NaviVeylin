package com.naviveylin.ui.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naviveylin.R

/**
 * The native description vocabulary → the phone surface's string resources
 * (spec: i18n-l10n — Native description labels are localized at the frontend).
 *
 * libosmscout's `DescriptionService` publishes its section names, subsection
 * names and label keys as English constants (`SECTION_NAME_CONTACT` =
 * "Contact", `LABEL_KEY_CONTACT_PHONE` = "Phone", …) and localizes neither.
 * Only the *value* crosses the bridge — `DescriptionEntry.sectionKey`,
 * `.subsectionKey`, `.labelKey` — so this table is keyed on that value: never
 * on the constant name, which Kotlin cannot see, and never on text a row has
 * already rendered.
 *
 * The wording lives here, in `:app`: the shared `:core` module owns no wording
 * (spec: i18n-l10n — Shared module owns no wording), so the surface that
 * renders these rows supplies the strings. A key the table does not carry
 * renders as itself — the surface has no word of its own for it, and a row
 * must never vanish because the native vocabulary grew.
 */
internal object DescriptionLabels {

    /**
     * The string resource for one native key, or null when the table does not
     * carry it. Matching is exact on the trimmed value and on the raw key:
     * `DetailsResolver` and `ContactLinks` match the same values, so a label
     * that has been translated is still recognized by what it was.
     */
    @StringRes
    fun res(key: String?): Int? = when (key?.trim()) {
            "Access" -> R.string.description_label_access
            "AccessRestricted" -> R.string.description_label_access_restricted
            "Address" -> R.string.description_label_address
            "AdminLevel" -> R.string.description_label_admin_level
            "Bicycle" -> R.string.description_label_bicycle
            "BoundingBox" -> R.string.description_label_bounding_box
            "Brand" -> R.string.description_label_brand
            "Bridge" -> R.string.description_label_bridge
            "Capacity" -> R.string.description_label_capacity
            "Car" -> R.string.description_label_car
            "CarriagewayRef" -> R.string.description_label_carriageway_ref
            "CellLevel" -> R.string.description_label_cell_level
            "Center" -> R.string.description_label_center
            "ChargingStation" -> R.string.description_label_charging_station
            "Clockwise" -> R.string.description_label_clockwise
            "Commercial" -> R.string.description_label_commercial
            "Condition" -> R.string.description_label_condition
            "ConstructionYear" -> R.string.description_label_construction_year
            "Contact" -> R.string.description_label_contact
            "Coordinate" -> R.string.description_label_coordinate
            "CycleLane" -> R.string.description_label_cycle_lane
            "CycleTrack" -> R.string.description_label_cycle_track
            "Destination" -> R.string.description_label_destination
            "DestinationBackward" -> R.string.description_label_destination_backward
            "DestinationForward" -> R.string.description_label_destination_forward
            "Distance" -> R.string.description_label_distance
            "Embankment" -> R.string.description_label_embankment
            "Fee" -> R.string.description_label_fee
            "Foot" -> R.string.description_label_foot
            "From" -> R.string.description_label_from
            "General" -> R.string.description_label_general
            "Geometry" -> R.string.description_label_geometry
            "Grade" -> R.string.description_label_grade
            "HighwayMilestone" -> R.string.description_label_highway_milestone
            "Id" -> R.string.description_label_id
            "IsIn" -> R.string.description_label_is_in
            "Kind" -> R.string.description_label_kind
            "Lanes" -> R.string.description_label_lanes
            "LanesBackward" -> R.string.description_label_lanes_backward
            "LanesForward" -> R.string.description_label_lanes_forward
            "Layer" -> R.string.description_label_layer
            "Level" -> R.string.description_label_level
            "Location" -> R.string.description_label_location
            "Marker" -> R.string.description_label_marker
            "MaxSpeed" -> R.string.description_label_max_speed
            "MaxStay" -> R.string.description_label_max_stay
            "Name" -> R.string.description_label_name
            "NameAlt" -> R.string.description_label_name_alt
            "NameRef" -> R.string.description_label_name_ref
            "NameShort" -> R.string.description_label_name_short
            "Navigation" -> R.string.description_label_navigation
            "Network" -> R.string.description_label_network
            "Oneway" -> R.string.description_label_oneway
            "OpeningHours" -> R.string.description_label_opening_hours
            "Operator" -> R.string.description_label_operator
            "Output" -> R.string.description_label_output
            "Payment" -> R.string.description_label_payment
            "Phone" -> R.string.description_label_phone
            "PostalCode" -> R.string.description_label_postal_code
            "Presence" -> R.string.description_label_presence
            "Ref" -> R.string.description_label_ref
            "Roundabout" -> R.string.description_label_roundabout
            "Sideways" -> R.string.description_label_sideways
            "Socket" -> R.string.description_label_socket
            "To" -> R.string.description_label_to
            "Tunnel" -> R.string.description_label_tunnel
            "TurnBackward" -> R.string.description_label_turn_backward
            "TurnForward" -> R.string.description_label_turn_forward
            "Type" -> R.string.description_label_type
            "Value" -> R.string.description_label_value
            "WalkTrack" -> R.string.description_label_walk_track
            "Way" -> R.string.description_label_way
            "Website" -> R.string.description_label_website
            "Width" -> R.string.description_label_width
        else -> null
    }
}

/** The localized text of [key] — the native key itself when it is unmapped. */
@Composable
internal fun descriptionLabel(key: String?): String {
    val res = DescriptionLabels.res(key)
    return if (res == null) key.orEmpty() else stringResource(res)
}
