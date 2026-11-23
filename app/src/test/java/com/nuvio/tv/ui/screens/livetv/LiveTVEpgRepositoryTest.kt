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
}
