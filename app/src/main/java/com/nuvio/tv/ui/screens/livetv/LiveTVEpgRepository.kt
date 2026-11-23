package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class LimitedTVInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L
    private fun account(n: Int): Int { if (n > 0) count += n; if (count > limit) throw IOException("XMLTV size limit"); return n }
    override fun read(): Int { val value = super.read(); account(if (value < 0) 0 else 1); return value }
    override fun read(b: ByteArray, off: Int, len: Int): Int = account(super.read(b, off, len))
}

data class LiveTVEpgResult(val data: LiveTVEpg, val fetchedAt: Long, val stale: Boolean, val failedSources: Int = 0)

@Singleton
class LiveTVEpgRepository @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, "live_tv_epg").apply { mkdirs() }
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    private val memory = linkedMapOf<String, LiveTVEpgResult>()

    suspend fun loadSources(urls: List<String>, force: Boolean = false, channels: List<LiveTVChannel> = emptyList(),
        epgIds: Map<String, String> = emptyMap()): LiveTVEpgResult {
        val results = mutableListOf<LiveTVEpgResult>()
        var failed = 0
        var remaining = channels
        for (url in cleanEpgSources(urls)) {
            try {
                val result = load(url, force, remaining, epgIds)
                results.add(result)
                val now = System.currentTimeMillis()
                remaining = remaining.filter { channel ->
                    val id = epgIds[channel.key]?.takeIf { id -> result.data.channels.any { it.id == id } }
                    result.data.schedule(channel, id, now).none { it.end > now }
                }
                // No later source is needed for any of the requested channels.
                if (channels.isNotEmpty() && remaining.isEmpty()) break
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed++ }
        }
        if (results.isEmpty()) throw IOException("No usable XMLTV source")
        return LiveTVEpgResult(LiveTVEpg(sourceGuides = results.map { it.data }),
            results.minOf { it.fetchedAt }, results.any { it.stale }, failed)
    }

    suspend fun load(rawUrl: String, force: Boolean = false, channels: List<LiveTVChannel> = emptyList(),
        epgIds: Map<String, String> = emptyMap()): LiveTVEpgResult = withContext(Dispatchers.IO) {
        val wantedNames = channels.takeIf { it.isNotEmpty() }?.map { epgName(it.name) }?.toSet()
        val wantedIds = channels.flatMap { listOfNotNull(it.id, it.id.substringAfterLast(':'), epgIds[it.key]) }.toSet()
        val cacheKey = rawUrl + "\u0000" + wantedNames.orEmpty().sorted().joinToString("\u0000") + "\u0001" + wantedIds.sorted().joinToString("\u0000")
        val url = rawUrl.toHttpUrlOrNull() ?: throw IOException("Invalid XMLTV URL")
        mutex.withLock {
            val now = System.currentTimeMillis()
            memory[cacheKey]?.takeIf { !force && !it.stale && now - it.fetchedAt < 12 * 3_600_000L }?.let { return@withLock it }
            val hash = MessageDigest.getInstance("SHA-256").digest(rawUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, "$hash.xmltv")
            var stale = false
            if (force || !file.exists() || now - file.lastModified() >= 12 * 3_600_000L) {
                val temporary = File.createTempFile("epg-", ".part", directory)
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("XMLTV HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty XMLTV response")
                        LimitedTVInput(body.byteStream(), 24L * 1024 * 1024).use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                }
                            }
                        }
                    }
                    // Validate before replacing a usable cache, including gzip signature detection.
                    val parsed = parse(temporary, now, wantedNames, wantedIds)
                    if (parsed.channels.isEmpty()) throw IOException("No XMLTV channels")
                    if (!temporary.renameTo(file)) { temporary.copyTo(file, overwrite = true); temporary.delete() }
                    file.setLastModified(now)
                    val result = LiveTVEpgResult(parsed, now, false)
                    remember(cacheKey, result)
                    directory.listFiles()?.filter { it != file && it.extension == "xmltv" }
                        ?.sortedByDescending { it.lastModified() }?.drop(MAX_EPG_SOURCES - 1)?.forEach { it.delete() }
                    return@withLock result
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (!file.exists()) throw e; stale = true }
                finally { temporary.delete() }
            }
            val result = LiveTVEpgResult(parse(file, now, wantedNames, wantedIds), file.lastModified(), stale)
            remember(cacheKey, result)
            result
        }
    }
    private fun remember(key: String, result: LiveTVEpgResult) {
        memory.remove(key)
        memory[key] = result
        while (memory.size > 4) memory.remove(memory.keys.first())
    }
    private fun parse(file: File, now: Long, names: Set<String>?, ids: Set<String>): LiveTVEpg = file.inputStream().buffered().use { input ->
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        val decoded = if (gzip) GZIPInputStream(input) else input
        LimitedTVInput(decoded, 160L * 1024 * 1024).use { XmlTVParser.parse(it, now, names, ids) }
    }
}
