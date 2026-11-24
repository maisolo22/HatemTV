package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class LiveTVEpgRepositoryTest {
    @Test fun `multiple sources preserve priority tolerate failure and reuse all source caches`() = runBlocking {
        val root = Files.createTempDirectory("tv-multi-epg-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.cacheDir } returns root
            MockWebServer().use { server ->
                val now = System.currentTimeMillis()
                val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC)
                val start = format.format(java.time.Instant.ofEpochMilli(now - 3_600_000))
                val stop = format.format(java.time.Instant.ofEpochMilli(now + 3_600_000))
                fun xml(id: String, name: String, title: String) = """<tv><channel id="$id"><display-name>$name</display-name></channel><programme channel="$id" start="$start" stop="$stop"><title>$title</title></programme></tv>"""
                val tf1 = LiveTVChannel("provider:tf1", "tv", "TF1", "https://addon.example", "Addon")
                val rai = tf1.copy(id = "provider:rai", name = "Rai 1")
                // The same XMLTV ID is intentionally used by two independent publishers.
                server.enqueue(MockResponse().setBody(xml("same", "TF1", "France")))
                server.enqueue(MockResponse().setResponseCode(503))
                server.enqueue(MockResponse().setBody(xml("same", "Rai 1", "Italia")))
                val urls = listOf("fr", "down", "it").map { server.url("/$it.xml").toString() }
                val repo = LiveTVEpgRepository(context)
                val partials = mutableListOf<LiveTVEpgResult>()
                val first = repo.loadSourcesProgressively(urls, channels = listOf(tf1, rai), onPartial = { partials.add(it) })
                assertEquals(2, partials.size)
                assertEquals("France", partials.first().data.schedule(tf1).single().title)
                assertTrue(partials.first().data.schedule(rai).isEmpty())
                assertEquals("France", partials.last().data.schedule(tf1).single().title)
                assertEquals("Italia", partials.last().data.schedule(rai).single().title)
                assertEquals("France", first.data.schedule(tf1).single().title)
                assertEquals("Italia", first.data.schedule(rai).single().title)
                assertEquals(1, first.failedSources)
                assertEquals(3, server.requestCount)
                val reopened = LiveTVEpgRepository(context).loadSources(listOf(urls[0], urls[2]), channels = listOf(tf1, rai))
                assertEquals("Italia", reopened.data.schedule(rai).single().title)
                assertEquals(3, server.requestCount)
                // If the first guide covers every requested channel, no later request is made.
                repo.loadSources(urls, channels = listOf(tf1))
                assertEquals(3, server.requestCount)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `gzip XMLTV is cached and a failed refresh preserves a valid schedule`() = runBlocking {
        val root = Files.createTempDirectory("tv-epg-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.cacheDir } returns root
            MockWebServer().use { server ->
                val raw = "<tv><channel id=\"tf1\"><display-name>TF1</display-name></channel></tv>"
                val compressed = ByteArrayOutputStream().also { stream -> GZIPOutputStream(stream).use { it.write(raw.toByteArray()) } }.toByteArray()
                server.enqueue(MockResponse().setBody(Buffer().write(compressed)))
                val repo = LiveTVEpgRepository(context)
                val url = server.url("/guide.xml.gz").toString()
                val first = repo.load(url)
                assertEquals("tf1", first.data.channels.single().id)
                repo.load(url)
                assertEquals(1, server.requestCount)
                assertTrue(root.resolve("live_tv_epg").listFiles().orEmpty().any { it.extension == "parsed" })
                server.enqueue(MockResponse().setResponseCode(503))
                val fallback = repo.load(url, force = true)
                assertTrue(fallback.stale)
                assertEquals(first.data, fallback.data)
                // Reopening the repository reads the on-disk cache, without another request.
                LiveTVEpgRepository(context).load(url)
                assertEquals(2, server.requestCount)
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun `parsed subsets survive reopening and forced refresh invalidates old in-memory subsets`() = runBlocking {
        val root = Files.createTempDirectory("tv-parsed-cache-test").toFile()
        try {
            val context = mockk<Context>()
            every { context.cacheDir } returns root
            MockWebServer().use { server ->
                val now = System.currentTimeMillis()
                val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC)
                val start = format.format(java.time.Instant.ofEpochMilli(now - 1000))
                val stop = format.format(java.time.Instant.ofEpochMilli(now + 3_600_000))
                fun xml(title: String) = """<tv><channel id="tf1"><display-name>TF1</display-name></channel><channel id="rai"><display-name>Rai 1</display-name></channel><programme channel="tf1" start="$start" stop="$stop"><title>$title</title></programme><programme channel="rai" start="$start" stop="$stop"><title>Rai</title></programme></tv>"""
                val channel = LiveTVChannel("tf1", "tv", "TF1", "https://addon.example", "Addon")
                val rai = channel.copy(id = "rai", name = "Rai 1")
                server.enqueue(MockResponse().setBody(xml("Old")))
                val url = server.url("/guide.xml").toString()
                val repo = LiveTVEpgRepository(context)
                repo.load(url, channels = listOf(channel, rai))
                val subset = repo.load(url, channels = listOf(channel))
                assertEquals(setOf("tf1"), subset.data.programmes.keys)
                val reopened = LiveTVEpgRepository(context).load(url, channels = listOf(channel))
                assertEquals(subset.data, reopened.data)
                assertEquals(1, server.requestCount)
                server.enqueue(MockResponse().setBody(xml("Updated title")))
                repo.load(url, force = true, channels = listOf(channel, rai))
                assertEquals("Updated title", repo.load(url, channels = listOf(channel)).data.schedule(channel).single().title)
                assertEquals(2, server.requestCount)
            }
        } finally { root.deleteRecursively() }
    }

}
