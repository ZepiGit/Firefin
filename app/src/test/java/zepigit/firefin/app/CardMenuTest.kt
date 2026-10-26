package zepigit.firefin.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.ui.CardMenu
import zepigit.firefin.app.ui.CardMenu.Action

class CardMenuTest {
    private fun item(json: String) = MediaItem.from(JSONObject(json))

    @Test fun `a half-watched episode offers resume reset, marks, favorite and the series`() {
        val episode = item("""{"Id":"e1","Type":"Episode","SeriesId":"s1","UserData":{"PlaybackPositionTicks":500,"Played":false,"IsFavorite":true}}""")
        assertEquals(
            listOf(Action.REMOVE_FROM_RESUME, Action.MARK_PLAYED, Action.FAVORITE_REMOVE, Action.GO_TO_SERIES),
            CardMenu.entries(episode, legacyRoutes = false),
        )
    }

    @Test fun `a 10_8 server hides the resume reset and a watched movie offers the inverse marks`() {
        val movie = item("""{"Id":"m1","Type":"Movie","UserData":{"PlaybackPositionTicks":500,"Played":true,"IsFavorite":false}}""")
        assertEquals(listOf(Action.MARK_UNPLAYED, Action.FAVORITE_ADD), CardMenu.entries(movie, legacyRoutes = true))
    }
}
