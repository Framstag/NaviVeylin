package com.naviveylin.test

import android.content.Context
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.EngineDispatchers
import com.naviveylin.core.EngineTimeSource
import com.naviveylin.location.LocationService
import com.naviveylin.navigation.NavigationEngine

/**
 * Build a [NavigationEngine] for a test with its two seams explicit.
 *
 * The engine's timing and threading are injected (spec: `navigation-engine` — Engine lifecycle and
 * threading), so a test states which clock and which dispatchers it uses instead of inheriting them
 * from a hidden default: a case that must not depend on real time passes a mutable [EngineTimeSource]
 * and a test-dispatcher [EngineDispatchers], and a case that only needs the engine to exist omits both
 * and gets the production pair.
 */
internal fun engineUnderTest(
    client: () -> OSMScoutClient,
    locationService: LocationService,
    context: Context,
    timeSource: EngineTimeSource = EngineTimeSource.System,
    dispatchers: EngineDispatchers = EngineDispatchers.Production
): NavigationEngine =
    NavigationEngine({ client() }, locationService, context, timeSource, dispatchers)
