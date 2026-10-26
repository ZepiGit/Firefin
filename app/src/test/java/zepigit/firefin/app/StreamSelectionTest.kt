package zepigit.firefin.app

import org.junit.Assert.*
import org.junit.Test
import zepigit.firefin.app.playback.SourceTrack
import zepigit.firefin.app.playback.StreamSelection

class StreamSelectionTest {
    @Test fun `language aliases and regional tags match with unknown languages preserved`() {
        val tracks = listOf(SourceTrack(1, "Audio", "fre", "French"), SourceTrack(3, "Subtitle", "pt-BR", "Portuguese"), SourceTrack(4, "Audio", "XYZ", "Unknown"))
        assertEquals(1, StreamSelection.index(tracks, "Audio", "fra"))
        assertEquals(1, StreamSelection.index(tracks, "Audio", "fr-CA"))
        assertEquals(3, StreamSelection.index(tracks, "Subtitle", "por"))
        assertEquals(4, StreamSelection.index(tracks, "Audio", "xyz"))
        assertNull(StreamSelection.index(tracks, "Audio", "nl"))
        assertEquals(-1, StreamSelection.index(tracks, "Subtitle", ""))
    }
}
