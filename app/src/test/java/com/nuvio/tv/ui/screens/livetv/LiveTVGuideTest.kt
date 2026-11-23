package com.nuvio.tv.ui.screens.livetv

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LiveTVGuideTest {
    private val now = xmltvTime("20261123060000 +0000")!!
    private fun channel(name: String = "TF1 HD", source: String = "https://one.example") =
        LiveTVChannel("addon:tf1", "tv", name, source, "My addon")

    @Test fun `folders keep channel identities across providers and support multiple memberships`() {
        val one = channel()
        val two = channel(source = "https://two.example")
        var library = LiveTVLibrary().createFolder(" Sport ", one, "sports").createFolder("Films", id = "films")
        library = library.toggleChannel("sports", two).toggleChannel("films", one)
        assertEquals(2, library.folders.first().channels.size)
        assertEquals(one, library.folders.last().channels.single())
        library = library.toggleChannel("sports", one).renameFolder("films", "Cinéma").deleteFolder("sports")
        assertEquals("Cinéma", library.folders.single().name)
        assertEquals(one, library.folders.single().channels.single())
        assertEquals(library, library.createFolder("cinéma"))
        assertEquals(library, library.renameFolder("films", "  "))
    }

    @Test fun `XMLTV offsets and date rollovers are respected`() {
        assertEquals(xmltvTime("20261122230000 +0000"), xmltvTime("20261123000000 +0100"))
        assertEquals(xmltvTime("20261123060000 +0000"), xmltvTime("20261123060000"))
        assertNull(xmltvTime("not-a-date"))
    }

    @Test fun `XMLTV schedules are real sorted events and names must be unambiguous`() {
        val xml = """<tv>
            <channel id="tf1.fr"><display-name>TF1</display-name></channel>
            <programme channel="tf1.fr" start="20261123080000 +0100" stop="20261123090000 +0100"><title>Next news</title></programme>
            <programme channel="tf1.fr" start="20261123060000 +0000" stop="20261123070000 +0000"><title>Morning &amp; weather</title><desc>Actual XMLTV description</desc></programme>
            <programme channel="tf1.fr" start="20261123080000 +0100" stop="20261123090000 +0100"><title>Next news</title></programme>
            <programme channel="tf1.fr" start="20261122010000 +0000" stop="20261122020000 +0000"><title>Expired</title></programme>
            </tv>"""
        val epg = XmlTVParser.parse(ByteArrayInputStream(xml.toByteArray()), now)
        val programmes = epg.schedule(channel())
        assertEquals(listOf("Morning & weather", "Next news"), programmes.map { it.title })
        assertEquals(now, programmes.first().start)
        assertTrue(epg.schedule(channel("Unknown")).isEmpty())
        val ambiguous = epg.copy(channels = epg.channels + XmlTVChannel("tf1.other", listOf("TF1")))
        assertNull(ambiguous.channelId(channel()))
        assertEquals("tf1.fr", ambiguous.channelId(channel(), "tf1.fr"))
        assertNull(ambiguous.channelId(channel(), "invalid"))
    }

    @Test fun `guide clips events to the window and preserves schedule gaps`() {
        val slots = tvTimelineSlots(listOf(TVProgramme("Earlier", -10, 25), TVProgramme("Later", 50, 110)), 0, 100)
        assertEquals(0f, slots[0].offset)
        assertEquals(0.25f, slots[0].width)
        assertEquals(0.5f, slots[1].offset)
        assertEquals(0.5f, slots[1].width)
        assertTrue(tvTimelineSlots(emptyList(), 100, 100).isEmpty())
    }

    @Test fun `decompressed XMLTV has a hard byte limit`() {
        val stream = LimitedTVInput(ByteArrayInputStream(ByteArray(30)), 10)
        try { stream.read(ByteArray(30)); fail("Must reject oversized XML") } catch (_: java.io.IOException) { }
    }
}
