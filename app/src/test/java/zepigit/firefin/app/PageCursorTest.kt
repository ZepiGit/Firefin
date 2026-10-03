package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.data.PageCursor

class PageCursorTest {

    /** Loads a list of [total] entries page by page and returns the loaded indices and request count. */
    private fun drain(total: Int, pageSize: Int = 60): Pair<List<Int>, Int> {
        val cursor = PageCursor()
        val loaded = mutableListOf<Int>()
        var requests = 0
        while (true) {
            val start = cursor.next() ?: break
            assertNull("only one page may be in flight", cursor.next())
            requests++
            val page = (start until minOf(start + pageSize, total)).toList()
            cursor.received(page.size, total)
            loaded += page
        }
        return loaded to requests
    }

    @Test fun `lists of 0, 1, 60, 61 and 125 entries load every entry once`() {
        for (total in listOf(0, 1, 60, 61, 125)) {
            val (loaded, requests) = drain(total)
            assertEquals("entries for $total", (0 until total).toList(), loaded)
            assertEquals("requests for $total", maxOf(1, (total + 59) / 60), requests)
        }
    }

    @Test fun `a failed follow-up page keeps the loaded entries and is requested again`() {
        val cursor = PageCursor()
        assertEquals(0, cursor.next())
        cursor.received(60, 125)
        assertEquals(60, cursor.next())
        cursor.failed()
        assertEquals(60, cursor.loaded)
        assertEquals(60, cursor.next())
    }

    @Test fun `prefetch triggers near the loaded end only while more entries exist`() {
        val cursor = PageCursor(prefetch = 8)
        cursor.next(); cursor.received(60, 125)
        assertFalse(cursor.nearEnd(51))
        assertTrue(cursor.nearEnd(52))
        cursor.next(); cursor.received(60, 125)
        cursor.next(); cursor.received(5, 125)
        assertFalse(cursor.hasMore)
        assertFalse(cursor.nearEnd(124))
    }

    @Test fun `an empty page ends a list whose server total is inconsistent`() {
        val cursor = PageCursor()
        cursor.next(); cursor.received(0, 50)
        assertNull(cursor.next())
    }
}
