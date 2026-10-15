package com.naviveylin.auto

import android.content.Context
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.hasMapManagerAction
import com.naviveylin.core.navigationFailureText

/**
 * The text a car error notice shows for [failure] (spec: `routing-failure-reporting` — *Each reason is
 * worded by the surface*, *A reason whose cause the user can act on offers the action*).
 *
 * The wording comes from the failure's reason, so the car names a cause exactly as the phone does — the
 * same shared resource with the same substituted values. The distance the wording may state is formatted
 * with this surface's own unit resources, which is why they are read here rather than in `:core`.
 *
 * Where the cause is something the map manager can fix, the notice **states** that the maps belong to the
 * phone instead of offering the action: a car session must not launch a phone screen
 * (`guidelines/UI.md` §1, the parity table's precise-location precedent), and the driver still has to be
 * told what to do. @param mapsOnPhoneMessage the car's own resource for that statement, read by the
 * caller, so this function can be asserted without a host session.
 */
internal fun failureNoticeText(
    context: Context,
    failure: NavigationFailure,
    mapsOnPhoneMessage: String
): String {
    val wording = navigationFailureText(
        context = context,
        failure = failure,
        unitMetersRes = R.string.distance_unit_m,
        unitKilometersRes = R.string.distance_unit_km
    )

    // A symbols-only separator: the two sentences are each a resource of their own owner (the shared cause
    // wording and the car's statement), and no display word is assembled here.
    return if (failure.hasMapManagerAction()) {
        wording + " " + mapsOnPhoneMessage
    } else {
        wording
    }
}
