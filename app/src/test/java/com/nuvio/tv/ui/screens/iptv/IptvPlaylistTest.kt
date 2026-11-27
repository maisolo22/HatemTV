package com.nuvio.tv.ui.screens.iptv

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IptvPlaylistTest {
    private val source = IptvPlaylist("test", "TV", "https://example.test/lists/live.m3u", "https://epg.test/primary.xml")

    @Test
    fun `reads grouped channels, TVG identifiers, stream headers and priority for the configured guide`() {
        val input = """
            #EXTM3U x-tvg-url="https://epg.test/primary.xml,guide.xml.gz"
            #EXTINF:-1 group-title="France, sports" tvg-id="fr.sport" tvg-logo="https://img.test/logo.png",Sport, HD
            #EXTVLCOPT:http-referrer=https://example.test/
            #EXTVLCOPT:http-user-agent=Test player
            ../streams/sport.ts
            #EXTINF:-1 tvg-name="Rail channel",
            #EXTGRP:Italy
            https://tv.test/live/rai.ts
        """.trimIndent()
        val catalog = parse(input)
        assertEquals(listOf(source.guideUrl, "https://example.test/lists/guide.xml.gz"), catalog.guideUrls)
        val (sport, rai) = catalog.channels
        assertEquals("France, sports", sport.group)
        assertEquals("Sport, HD", sport.guideChannel.name)
        assertEquals("fr.sport", sport.guideChannel.id)
        assertEquals("https://example.test/streams/sport.ts", sport.streamUrl)
        assertEquals(mapOf("Referer" to "https://example.test/", "User-Agent" to "Test player"), sport.headers)
        assertEquals("Rail channel", rai.guideChannel.name)
        assertEquals("Italy", rai.group)
        assertFalse(sport.toString().contains(sport.streamUrl))
    }

    @Test
    fun `identical entries are deduplicated while two streams for one TV channel keep distinct keys`() {
        val input = """#EXTM3U
            |#EXTINF:-1 tvg-id="news",News
            |first.ts
            |#EXTINF:-1 tvg-id="news",News
            |first.ts
            |#EXTINF:-1 tvg-id="news",News
            |backup.ts
        """.trimMargin()
        val rows = parse(input).channels
        assertEquals(2, rows.size)
        assertNotEquals(rows[0].key, rows[1].key)
        assertEquals(rows[0].guideChannel.id, rows[1].guideChannel.id)
        assertEquals(rows.map { it.key }, parse(input).channels.map { it.key })
    }

    @Test(expected = IOException::class)
    fun `a media HLS playlist is rejected as a channel list`() {
        parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts")
    }

    private fun parse(input: String) =
        parseIptvPlaylist(input.reader().buffered(), source, source.url.toHttpUrl())
}
