package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import zepigit.firefin.app.ui.stableId

class StableIdTest {
    @Test fun `same key gives the same id and known 32-bit collisions stay apart`() {
        assertEquals(stableId("abc"), stableId("abc"))
        // "Aa" and "BB" share String.hashCode(); the 64-bit id must tell them apart.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(stableId("Aa"), stableId("BB"))
        assertNotEquals(stableId(""), stableId("a"))
    }
}
