package com.naviveylin.auto

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
                screenManager.pop()
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
    .setTitle("Back")
    .setOnClickListener { screenManager.pop() }
    .build()
