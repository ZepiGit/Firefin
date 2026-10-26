package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.ui.HomeRow
import zepigit.firefin.app.ui.HomeSections

class HomeSectionsTest {
    @Test fun `order preserves libraries and ignores unknown or repeated sections`() {
        val rows = listOf(HomeRow("latest", "A", emptyList()), HomeRow("resume", "R", emptyList()), HomeRow("latest", "B", emptyList()), HomeRow("favorites", "F", emptyList()))
        assertEquals(listOf("F", "A", "B", "R"), HomeSections.order(listOf("favorites", "latest", "unknown", "resume", "latest"), rows).map { it.title })
        assertEquals(listOf("R", "A", "B"), HomeSections.order(listOf("resume", "nextUp", "latest"), rows).map { it.title })
        assertEquals(emptyList<HomeRow>(), HomeSections.order(emptyList(), rows))
    }
}
