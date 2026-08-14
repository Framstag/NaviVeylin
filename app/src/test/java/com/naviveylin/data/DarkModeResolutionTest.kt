package com.naviveylin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkModeResolutionTest {

    @Test
    fun onIsAlwaysDark() {
        assertEquals(true, resolveDarkPresentation(DarkModePreference.ON, environmentDark = true))
        assertEquals(true, resolveDarkPresentation(DarkModePreference.ON, environmentDark = false))
    }

    @Test
    fun offIsAlwaysLight() {
        assertEquals(false, resolveDarkPresentation(DarkModePreference.OFF, environmentDark = true))
        assertEquals(false, resolveDarkPresentation(DarkModePreference.OFF, environmentDark = false))
    }

    @Test
    fun automaticFollowsEnvironment() {
        assertEquals(true, resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = true))
        assertEquals(false, resolveDarkPresentation(DarkModePreference.AUTOMATIC, environmentDark = false))
    }
}
