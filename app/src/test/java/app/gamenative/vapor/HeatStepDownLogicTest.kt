package app.gamenative.vapor

import app.gamenative.vapor.HeatStepDownLogic.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatStepDownLogicTest {
    @Test
    fun severeDropsTo45ThenCriticalTo30ThenRestores() {
        val h = HeatStepDownLogic()
        assertEquals(Result.None, h.onStatus(2, 60))
        assertEquals(Result.Cap(45), h.onStatus(3, 60))
        assertEquals(Result.None, h.onStatus(3, 60))
        assertEquals(Result.Cap(30), h.onStatus(4, 60))
        assertEquals(Result.None, h.onStatus(3, 60))
        assertEquals(Result.Restore, h.onStatus(1, 60))
    }

    @Test
    fun neverRaisesALowerUserCap() {
        val h = HeatStepDownLogic()
        assertEquals(Result.None, h.onStatus(3, 40))
        assertEquals(Result.Cap(30), h.onStatus(4, 40))
    }

    @Test
    fun uncappedGamesStillGetCapped() {
        assertEquals(Result.Cap(45), HeatStepDownLogic().onStatus(3, 0))
    }
}
