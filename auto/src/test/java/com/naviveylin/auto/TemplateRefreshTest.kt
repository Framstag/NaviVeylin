package com.naviveylin.auto

import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests [postTemplateRefresh] (spec: car-host-fault-isolation — Template invalidation
 * is main-thread only; design D4): the map renderer reports a surface failure from its
 * render thread, and the car-app library requires the resulting
 * `Screen.invalidate()` on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
class TemplateRefreshTest {

    @Test
    fun runsInlineWhenAlreadyOnTheMainThread() {
        var ran = false
        var thread: Thread? = null

        postTemplateRefresh {
            ran = true
            thread = Thread.currentThread()
        }

        assertTrue("the refresh must run", ran)
        assertSame("on the main thread", Thread.currentThread(), thread)
    }

    @Test
    fun postsToTheMainThreadWhenCalledFromABackgroundThread() {
        val latch = CountDownLatch(1)
        var ranOnMain = false
        var callerThread: Thread? = null

        val background = Thread {
            postTemplateRefresh {
                ranOnMain = Looper.myLooper() === Looper.getMainLooper()
                latch.countDown()
            }
            callerThread = Thread.currentThread()
        }
        background.start()
        background.join()

        // Not run yet: it was posted, not executed on the calling thread.
        assertTrue("the refresh must be posted, not run inline", latch.count == 1L)

        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("the posted refresh must run", latch.await(1, TimeUnit.SECONDS))
        assertTrue("on the main thread", ranOnMain)
        assertTrue("not on the caller thread", callerThread !== Thread.currentThread())
    }
}
