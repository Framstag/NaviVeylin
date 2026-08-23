package com.naviveylin

import android.content.Context
import android.content.pm.PackageManager

/**
 * Automotive device detection for the single-APK trampoline: on Android
 * Automotive OS hardware the phone [MainActivity] redirects to
 * `CarAppActivity` (which binds the AAOS template host); everywhere else the
 * phone UI stays the entry point.
 */
object AutomotiveDevice {

    /** True when running on automotive hardware (Android Automotive OS). */
    fun isAutomotive(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
}
