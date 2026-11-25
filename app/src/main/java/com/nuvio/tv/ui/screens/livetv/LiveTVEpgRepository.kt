package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import com.google.gson.Gson
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

internal class LimitedTVInput(input: InputStream, private val limit: Long, private val checkActive: () -> Unit = {}) : FilterInputStream(input) {
    private var count = 0L
    private fun account(n: Int): Int { checkActive(); if (n > 0) count += n; if (count > limit) throw IOException("XMLTV size limit"); return n }
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
    private val gson = Gson()
    // Persist only relevant parsed data, never reflection-serialize the EPG lookup indexes.
    private data class ParsedCache(val version: Int, val modified: Long, val size: Long,
        val channels: List<XmlTVChannel>, val programmes: Map<String, List<TVProgramme>>)
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    // Preserve the original API for the player and existing clients.
    suspend fun loadSources(urls: List<String>, force: Boolean = false, channels: List<LiveTVChannel> = emptyList(),
        epgIds: Map<String, String> = emptyMap()): LiveTVEpgResult =
        loadSourcesProgressively(urls, force, channels, epgIds) {}

    suspend fun loadSourcesProgressively(urls: List<String>, force: Boolean = false, channels: List<LiveTVChannel> = emptyList(),
        epgIds: Map<String, String> = emptyMap(),
        onPartial: suspend (LiveTVEpgResult) -> Unit): LiveTVEpgResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<LiveTVEpgResult>()
        var failed = 0
        var remaining = channels
        for (url in cleanEpgSources(urls)) {
            try {
                val result = load(url, force, remaining, epgIds)
                results.add(result)
                val now = System.currentTimeMillis()
                remaining = remaining.filter { channel ->
                    val id = epgIds[channel.key]?.takeIf { id -> result.data.containsId(id) }
                    result.data.schedule(channel, id, now).none { it.end > now }
                }
                onPartial(LiveTVEpgResult(LiveTVEpg(sourceGuides = results.map { it.data }),
                    results.minOf { it.fetchedAt }, results.any { it.stale }, failed))
                // No later source is needed for any of the requested channels.
                if (channels.isNotEmpty() && remaining.isEmpty()) break
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed++ }
        }
        if (results.isEmpty()) throw IOException("No usable XMLTV source")
        LiveTVEpgResult(LiveTVEpg(sourceGuides = results.map { it.data }),
            results.minOf { it.fetchedAt }, results.any { it.stale }, failed)
    }

    suspend fun load(rawUrl: String, force: Boolean = false, channels: List<LiveTVChannel> = emptyList(),
        epgIds: Map<String, String> = emptyMap()): LiveTVEpgResult = withContext(Dispatchers.IO) {
        val parseContext = currentCoroutineContext()
        val currentCoroutineContextCheck = { parseContext.ensureActive() }
        val wantedNames = channels.takeIf { it.isNotEmpty() }?.map { epgName(it.name) }?.toSet()
        val wantedIds = channels.flatMap { listOfNotNull(it.id, it.id.substringAfterLast(':'), epgIds[it.key]) }.toSet()
        val cacheKey = rawUrl + "\u0000" + wantedNames.orEmpty().sorted().joinToString("\u0000") + "\u0001" + wantedIds.sorted().joinToString("\u0000")
        val url = rawUrl.toHttpUrlOrNull() ?: throw IOException("Invalid XMLTV URL")
        mutex.withLock {
            val now = System.currentTimeMillis()
            memory[cacheKey]?.takeIf { !force && !it.stale && now - it.fetchedAt < 12 * 3_600_000L }?.let { return@withLock it }
            val file = File(directory, "${hash(rawUrl)}.xmltv")
            val compact = File(directory, "${hash(cacheKey)}.parsed")
            var stale = false
            if (force || !file.exists() || now - file.lastModified() >= 12 * 3_600_000L) {
                val temporary = File.createTempFile("epg-", ".part", directory)
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("XMLTV HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty XMLTV response")
                        LimitedTVInput(body.byteStream(), 24L * 1024 * 1024) { currentCoroutineContextCheck() }.use { input ->
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
                    val parsed = parse(temporary, now, wantedNames, wantedIds, currentCoroutineContextCheck)
                    if (parsed.channels.isEmpty()) throw IOException("No XMLTV channels")
                    if (!temporary.renameTo(file)) { temporary.copyTo(file, overwrite = true); temporary.delete() }
                    file.setLastModified(now)
                    memory.keys.removeAll { it.startsWith(rawUrl + "\u0000") }
                    writeParsed(compact, file, parsed)
                    val result = LiveTVEpgResult(parsed, now, false)
                    remember(cacheKey, result)
                    directory.listFiles()?.filter { it != file && it.extension == "xmltv" }
                        ?.sortedByDescending { it.lastModified() }?.drop(MAX_EPG_SOURCES - 1)?.forEach { it.delete() }
                    return@withLock result
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (!file.exists()) throw e; stale = true }
                finally { temporary.delete() }
            }
            val result = LiveTVEpgResult((readParsed(compact, file) ?: parse(file, now, wantedNames, wantedIds, currentCoroutineContextCheck)
                .also { writeParsed(compact, file, it) }), file.lastModified(), stale)
            remember(cacheKey, result)
            result
        }
    }
    private fun remember(key: String, result: LiveTVEpgResult) {
        memory.remove(key)
        memory[key] = result
        while (memory.size > 4) memory.remove(memory.keys.first())
    }
    private fun readParsed(compact: File, source: File): LiveTVEpg? = runCatching {
        if (!compact.exists() || compact.length() > 24L * 1024 * 1024) return@runCatching null
        val cached = compact.reader().buffered().use { gson.fromJson(it, ParsedCache::class.java) }
        if (cached.version != 1 || cached.modified != source.lastModified() || cached.size != source.length()) return@runCatching null
        LiveTVEpg(cached.channels, cached.programmes)
    }.getOrNull()

    private fun writeParsed(compact: File, source: File, data: LiveTVEpg) {
        // An optional acceleration cache must never make a valid guide fail to load.
        runCatching {
            val temporary = File.createTempFile("parsed-", ".part", directory)
            try {
                temporary.writer().buffered().use { gson.toJson(ParsedCache(1, source.lastModified(),
                    source.length(), data.channels, data.programmes), it) }
                if (temporary.length() <= 24L * 1024 * 1024) {
                    if (!temporary.renameTo(compact)) temporary.copyTo(compact, overwrite = true)
                }
            } finally { temporary.delete() }
            var retainedBytes = 0L
            directory.listFiles()?.filter { it.extension == "parsed" }?.sortedByDescending { it.lastModified() }
                ?.forEachIndexed { index, entry ->
                    retainedBytes += entry.length()
                    if (index >= 12 || retainedBytes > 48L * 1024 * 1024) entry.delete()
                }
        }
    }
    private fun parse(file: File, now: Long, names: Set<String>?, ids: Set<String>, checkActive: () -> Unit): LiveTVEpg = file.inputStream().buffered().use { input ->
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        val decoded = if (gzip) GZIPInputStream(input, 32 * 1024) else input
        LimitedTVInput(decoded, 160L * 1024 * 1024, checkActive).use { XmlTVParser.parse(it, now, names, ids) }
    }
}
