package com.naviveylin.auto

import com.naviveylin.auto.R
import androidx.activity.OnBackPressedCallback
import androidx.car.app.Screen
import androidx.car.app.model.Action

/**
 * Back-navigation helpers.
 *
 * The Android Auto host normally renders its own back affordance for pushed
 * screens, but emulated hosts (headunit-revived + gearhead) often don't — so
 * every screen carries an explicit back affordance:
 *
 * - Headers get the standard icon action [Action.BACK] (headers only accept
 *   icon actions, a text-titled action violates the header constraints).
 * - [Action.BACK] and the host's back gesture are delivered through
 *   `CarContext.getOnBackPressedDispatcher()`, so [enableBackNavigation]
 *   registers the pop callback per screen.
 */
fun Screen.enableBackNavigation() {
    carContext.getOnBackPressedDispatcher().addCallback(
        this,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // The host's back gesture/affordance is dispatched on the app's main thread
                // and the library rethrows an app exception there: guard the pop like every
                // other screen-stack mutation (spec: car-host-fault-isolation — No fault
                // escapes into the host path).
                guardedHostCall("pop (back gesture)") { screenManager.pop() }
            }
        }
    )
}

/**
 * Text "Back" action for use in rows/action strips where a labelled button is
 * clearer than the icon-only header arrow. (Row actions allow custom titles;
 * header actions do not.)
 */
fun Screen.backAction(): Action = Action.Builder()
    .setTitle(carContext.getString(R.string.back))
    .setOnClickListener { guardedHostCall("pop (back action)") { screenManager.pop() } }
    .build()
