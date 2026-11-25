package com.nuvio.tv.ui.screens.livetv

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LiveTVMultiSourceTest {
    private val channel = LiveTVChannel("addon:tf1", "tv", "TF1 HD", "https://addon.example", "Addon")
    private val now = 10_000L
    private fun guide(id: String, title: String, end: Long = 20_000L) = LiveTVEpg(
        listOf(XmlTVChannel(id, listOf("TF1"))), mapOf(id to listOf(TVProgramme(title, 0, end))))

    @Test fun `first usable guide wins without mixing competing schedules`() {
        val first = guide("one", "First")
        val second = guide("two", "Second")
        assertEquals("First", LiveTVEpg(sourceGuides = listOf(first, second)).schedule(channel, now = now).single().title)
        assertEquals("Second", LiveTVEpg(sourceGuides = listOf(second, first)).schedule(channel, now = now).single().title)
    }
    @Test fun `missing empty and expired guides fall back and source IDs cannot collide`() {
        val missing = LiveTVEpg(listOf(XmlTVChannel("other", listOf("Other"))))
        val empty = LiveTVEpg(listOf(XmlTVChannel("same", listOf("TF1"))))
        val expired = guide("same", "Old", now - 1)
        val valid = guide("same", "Current")
        assertEquals("Current", LiveTVEpg(sourceGuides = listOf(missing, empty, expired, valid))
            .schedule(channel, now = now).single().title)
        assertTrue(LiveTVEpg(sourceGuides = listOf(expired)).schedule(channel, now = now).isEmpty())
    }
    @Test fun `future coverage is usable and absent override falls back to automatic matching`() {
        val future = LiveTVEpg(listOf(XmlTVChannel("future", listOf("TF1"))),
            mapOf("future" to listOf(TVProgramme("Later", now + 1000, now + 2000))))
        assertEquals("Later", LiveTVEpg(sourceGuides = listOf(future, guide("old-id", "Lower priority")))
            .schedule(channel, "old-id", now).single().title)
    }
    @Test fun `manual mapping still resolves a different name in its source`() {
        val mapped = LiveTVEpg(listOf(XmlTVChannel("manual", listOf("A different name"))),
            mapOf("manual" to listOf(TVProgramme("Mapped", 0, 20_000))))
        assertEquals("Mapped", LiveTVEpg(sourceGuides = listOf(mapped)).schedule(channel, "manual", now).single().title)
    }
    @Test fun `old JSON migrates single URL and reordered sources survive serialization and disabling`() {
        val gson = Gson()
        val old = gson.fromJson("""{"epgUrl":"https://first.example/guide.xml"}""", LiveTVLibrary::class.java)
        assertEquals(listOf("https://first.example/guide.xml"), old.epgSources)
        val next = old.withEpgSources(listOf(" https://second.example/tv.xml ", "", old.epgUrl, old.epgUrl))
        val restored = gson.fromJson(gson.toJson(next), LiveTVLibrary::class.java)
        assertEquals(listOf("https://second.example/tv.xml", old.epgUrl), restored.epgSources)
        assertTrue(restored.withEpgSources(emptyList()).epgSources.isEmpty())
        assertEquals(8, old.withEpgSources((1..20).map { "https://epg$it.example/tv.xml" }).epgSources.size)
    }
}
