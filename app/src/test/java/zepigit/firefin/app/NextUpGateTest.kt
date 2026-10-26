package zepigit.firefin.app

import org.junit.Assert.*
import org.junit.Test
import zepigit.firefin.app.playback.NextUpGate

class NextUpGateTest {
    @Test fun `ended is handled once per attempt and three automatic advances need confirmation`() {
        val gate = NextUpGate()
        assertTrue(gate.endOnce())
        assertFalse(gate.endOnce())
        gate.beginAttempt()
        assertTrue(gate.endOnce())
        assertFalse(gate.countdownEnabled(false))
        repeat(3) { assertTrue(gate.countdownEnabled(true)); gate.advancedAutomatically() }
        assertTrue(gate.needsConfirmation())
        assertFalse(gate.countdownEnabled(true))
        gate.input()
        assertFalse(gate.needsConfirmation())
        assertTrue(gate.countdownEnabled(true))
    }
}
