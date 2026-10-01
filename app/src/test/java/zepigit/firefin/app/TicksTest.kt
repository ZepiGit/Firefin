package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.util.Ticks

class TicksTest {

    @Test
    fun `round trip ms through ticks`() {
        val ms = 83_456_789L
        assertEquals(ms, Ticks.toMs(Ticks.fromMs(ms)))
    }

    @Test
    fun `one tick is a tenth of a millisecond`() {
        assertEquals(1L, Ticks.toMs(10_000L))
        assertEquals(10_000L, Ticks.fromMs(1L))
    }

    @Test
    fun `format renders hours and padding`() {
        assertEquals("1:02:03", Ticks.format(Ticks.fromMs(3_723_000L)))
        assertEquals("5:07", Ticks.format(Ticks.fromMs(307_000L)))
    }
}
