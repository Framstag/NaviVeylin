package com.naviveylin.auto

import android.content.Context
import androidx.car.app.CarContext
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk

/**
 * Builds a mock [CarContext] whose `getString` resolves against the real
 * Robolectric application resources (default locale = English), so template
 * builders and screens can be tested with localized strings.
 */
fun testCarContext(): CarContext {
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    return mockk<CarContext>().apply {
        every { getOnBackPressedDispatcher() } returns mockk(relaxed = true)
        every { getString(any()) } answers { appContext.getString(firstArg()) }
        every { getString(any(), *anyVararg()) } answers {
            val resId = firstArg<Int>()
            // mockk exposes the vararg as a single Array element in `args`
            val formatArgs = if (args.size > 1 && args[1] is Array<*>) {
                args[1] as Array<Any?>
            } else {
                emptyArray()
            }
            appContext.getString(resId, *formatArgs)
        }
    }
}
