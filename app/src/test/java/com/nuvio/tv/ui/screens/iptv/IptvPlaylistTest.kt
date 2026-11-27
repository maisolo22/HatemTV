package com.nuvio.tv.ui.screens.iptv

import android.view.KeyEvent
import com.nuvio.tv.ui.screens.player.iptvZapDirection
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf(source.guideUrl, "https://example.test/lists/guide.xml.gz"), source.resolveGuides(catalog.embeddedGuideUrls))
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

    @Test
    fun `older sources migrate and new manual guide order takes priority over embedded guides`() {
        val legacy = Json.decodeFromString<IptvPlaylist>(
            """{"id":"test","name":"TV","url":"https://example.test/live.m3u","guideUrl":"https://epg.test/old.xml"}"""
        )
        assertEquals(listOf("https://epg.test/old.xml"), legacy.epgSources)
        val first = "https://epg.test/new.xml"
        val auto = "https://epg.test/automatic.xml"
        val updated = legacy.withEpgSources(listOf(first, legacy.guideUrl))
        assertEquals(listOf(first, legacy.guideUrl, auto), updated.resolveGuides(listOf(auto, first)))
        assertEquals(listOf(auto), updated.withEpgSources(emptyList()).resolveGuides(listOf(auto)))
    }

    @Test
    fun `zapping stays in the playback order and wraps at each end`() {
        val channels = parse("#EXTM3U\n#EXTINF:-1,One\none.ts\n#EXTINF:-1,Two\ntwo.ts\n#EXTINF:-1,Three\nthree.ts").channels
        val (first, second, last) = channels
        assertEquals(second, adjacentIptvChannel(channels, first.key, 1))
        assertEquals(first, adjacentIptvChannel(channels, last.key, 1))
        assertEquals(last, adjacentIptvChannel(channels, first.key, -1))
        assertNull(adjacentIptvChannel(listOf(first), first.key, 1))
        assertNull(adjacentIptvChannel(channels, "unknown", 1))
    }

    @Test
    fun `channel buttons zap while arrow keys only zap with hidden controls`() {
        assertEquals(1, iptvZapDirection(KeyEvent.KEYCODE_CHANNEL_UP, controlsVisible = true))
        assertEquals(-1, iptvZapDirection(KeyEvent.KEYCODE_CHANNEL_DOWN, controlsVisible = true))
        assertEquals(1, iptvZapDirection(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = false))
        assertEquals(-1, iptvZapDirection(KeyEvent.KEYCODE_DPAD_DOWN, controlsVisible = false))
        assertNull(iptvZapDirection(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = true))
        assertNull(iptvZapDirection(KeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = false))
    }

    private fun parse(input: String) =
        parseIptvPlaylist(input.reader().buffered(), source, source.url.toHttpUrl())
}
