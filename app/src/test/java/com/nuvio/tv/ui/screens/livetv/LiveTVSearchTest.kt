package com.nuvio.tv.ui.screens.livetv

import org.junit.Assert.*
import org.junit.Test

class LiveTVSearchTest {
    @Test fun `matches only the start of a channel name`() {
        assertTrue(channelStartsWith("TF1 HD", "tf"))
        assertTrue(channelStartsWith("France 2", "FRANCE 2"))
        assertFalse(channelStartsWith("France 2", "2"))
        assertFalse(channelStartsWith("BFM TV", "TV"))
    }

    @Test fun `accents casing and surrounding spaces do not hide matches`() {
        assertTrue(channelStartsWith(" Événement TV", " eve "))
        assertTrue(channelStartsWith("İzmir TV", "iz"))
        assertTrue(channelStartsWith("قناة تونس", "قناة"))
    }

    @Test fun `empty search restores channels and arbitrary punctuation is literal`() {
        assertTrue(channelStartsWith("TF1", "  "))
        assertTrue(channelStartsWith("[FR] TF1", "[fr]"))
        assertFalse(channelStartsWith("TF1", ".*"))
    }
}
