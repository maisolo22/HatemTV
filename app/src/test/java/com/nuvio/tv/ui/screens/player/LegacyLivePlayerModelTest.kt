package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.TVProgramme
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class LegacyLivePlayerModelTest {
    private val channel = LiveTVChannel("provider:tf1+é", "tv", "TF1 + Première", "https://tv.example/config?token=a+b&group=fr", "My addon", "https://tv.example/logo.png")

    @Test fun `live channel context survives both encoded navigation routes`() {
        val json = encodeLivePlayerChannel(channel)
        val stream = Screen.Stream.createRoute(channel.id, channel.type, channel.name, liveChannel = json)
        val encoded = stream.substringAfter("&liveChannel=")
        val restored = decodeLivePlayerChannel(URLDecoder.decode(encoded, "UTF-8"))!!
        assertEquals(channel.key, restored.key)
        assertEquals(channel.name, restored.name)
        val player = Screen.Player.createRoute("https://stream.example/live.m3u8?x=1&y=2", channel.name,
            launchStartedAtMs = 0, liveChannel = json)
        assertEquals(channel.key, decodeLivePlayerChannel(URLDecoder.decode(player.substringAfter("&liveChannel="), "UTF-8"))!!.key)
        assertNull(decodeLivePlayerChannel("broken"))
        assertNull(decodeLivePlayerChannel(encodeLivePlayerChannel(channel.copy(type = "movie"))))
    }

    @Test fun `film and series HLS keep VOD controls while explicit TV channels get the skin`() {
        assertFalse(useLegacyLivePlayer("movie", null, true))
        assertFalse(useLegacyLivePlayer("series", null, false))
        assertFalse(useLegacyLivePlayer("tv", null, false))
        assertTrue(useLegacyLivePlayer("tv", channel, false))
        assertTrue(useLegacyLivePlayer("tv", null, true))
        assertTrue(useLegacyLivePlayer("channel", null, false))
    }

    @Test fun `first OK reveals and consumes held repeats plus release before any pause button`() {
        val gate = LegacyLiveWakeGate()
        assertEquals(LegacyLiveKeyAction.REVEAL, gate.key(LegacyLiveRemoteKey.CONFIRM, true, true, false))
        assertEquals(LegacyLiveKeyAction.CONSUME, gate.key(LegacyLiveRemoteKey.CONFIRM, true, false, false))
        assertEquals(LegacyLiveKeyAction.CONSUME, gate.key(LegacyLiveRemoteKey.CONFIRM, false, false, false))
        assertEquals(LegacyLiveKeyAction.PASS, gate.key(LegacyLiveRemoteKey.CONFIRM, true, false, false))
    }

    @Test fun `hidden arrows reveal without seek while dialogs and hardware keys keep ownership`() {
        val gate = LegacyLiveWakeGate()
        assertEquals(LegacyLiveKeyAction.PASS, gate.key(LegacyLiveRemoteKey.LEFT, true, true, true))
        assertEquals(LegacyLiveKeyAction.PASS, gate.key(LegacyLiveRemoteKey.OTHER, true, true, false))
        assertEquals(LegacyLiveKeyAction.REVEAL, gate.key(LegacyLiveRemoteKey.RIGHT, true, true, false))
        assertEquals(LegacyLiveKeyAction.CONSUME, gate.key(LegacyLiveRemoteKey.RIGHT, false, false, false))
        gate.capture(LegacyLiveRemoteKey.INFO)
        assertEquals(LegacyLiveKeyAction.CONSUME, gate.key(LegacyLiveRemoteKey.INFO, false, false, true))
    }

    @Test fun `programme progress uses wall clock and overlapping events choose the latest start`() {
        val result = legacyLiveProgrammes(listOf(TVProgramme("Older", 0, 200), TVProgramme("Current", 50, 150),
            TVProgramme("Overlaps current", 120, 140), TVProgramme("Next", 150, 250)), 100)
        assertEquals("Current", result.current!!.title)
        assertEquals("Next", result.next!!.title)
        assertEquals(0.5f, result.progress)
        assertNull(legacyLiveProgrammes(emptyList(), 100).current)
        assertEquals(0f, legacyLiveProgrammes(listOf(TVProgramme("Invalid", 100, 100)), 100).progress)
    }

    @Test fun `quality labels reflect measured decoder formats`() {
        val labels = legacyLiveTechnicalLabels(PlayerMediaInfo(videoWidth = 1280, videoHeight = 720,
            videoCodec = "AVC", audioCodec = "AAC", audioChannels = 2), 50f)
        assertEquals(listOf("1280 × 720", "AVC", "50.00 fps", "AAC · 2 ch"), labels)
        assertTrue(legacyLiveTechnicalLabels(PlayerMediaInfo(), Float.NaN).isEmpty())
    }
}
