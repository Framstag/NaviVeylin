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
        // The screens resolve plurals (`resources.getQuantityString`) as well as strings; both come
        // from the Robolectric application's real resources.
        every { resources } returns appContext.resources
        every { getString(any()) } answers { appContext.getString(firstArg()) }
        every { getString(any(), *anyVararg()) } answers {
            val resId = firstArg<Int>()
            // mockk exposes the vararg as a single Array element in `args`; the star-projection cast is
            // checked, so the elements are copied into the array `getString` spreads.
            val formatArgs: Array<Any?> =
                (args.getOrNull(1) as? Array<*>)?.map { it }?.toTypedArray() ?: emptyArray()
            appContext.getString(resId, *formatArgs)
        }
    }
}
