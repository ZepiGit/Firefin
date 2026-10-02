package zepigit.firefin.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.data.SearchQueryGate

class SearchQueryGateTest {
    @Test fun `only newest generation may publish`() {
        val gate = SearchQueryGate()
        val first = gate.next()
        val second = gate.next()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
