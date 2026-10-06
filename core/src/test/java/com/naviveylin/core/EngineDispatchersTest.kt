package com.naviveylin.core

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dispatcher seam's production values are the process-wide dispatchers (spec: `navigation-engine`). */
class EngineDispatchersTest {

    @Test
    fun productionPairIsTheProcessWideDispatchers() {
        assertEquals(Dispatchers.Default, EngineDispatchers.Production.compute)
        assertEquals(Dispatchers.IO, EngineDispatchers.Production.io)
    }

    @Test
    fun holderIsReplaceableAsAValue() {
        val test = EngineDispatchers(compute = Dispatchers.Unconfined, io = Dispatchers.Unconfined)

        val replaced = EngineDispatchers.Production.copy(compute = test.compute)

        assertEquals(Dispatchers.Unconfined, replaced.compute)
        assertEquals(Dispatchers.Default, EngineDispatchers.Production.compute)
    }
}
