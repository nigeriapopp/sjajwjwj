package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Test

class PadNoticeLogicTest {
    private val xbox = PadInfo("a", "Xbox Wireless Controller", 0, 80)

    @Test
    fun connectedMessageHasNameSlotAndBattery() {
        assertEquals("Xbox controller connected · Player 1 · 80% battery", PadNoticeLogic.connectedText(xbox))
    }

    @Test
    fun unknownBatteryIsLeftOut() {
        assertEquals("DualSense connected · Player 2", PadNoticeLogic.connectedText(PadInfo("b", "DualSense Wireless Controller", 1, null)))
    }

    @Test
    fun diffReportsNewAndRemoved() {
        val msgs = PadNoticeLogic.diff(mapOf("old" to "Pro Controller"), listOf(xbox))
        assertEquals(listOf("Xbox controller connected · Player 1 · 80% battery", "Switch Pro controller disconnected"), msgs)
    }

    @Test
    fun unchangedDevicesAreQuiet() {
        assertEquals(emptyList<String>(), PadNoticeLogic.diff(mapOf("a" to "Xbox Wireless Controller"), listOf(xbox)))
    }
}
