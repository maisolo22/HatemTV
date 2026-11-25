package com.nuvio.tv.ui.screens.livetv

import java.io.InputStream
import java.text.Normalizer
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** XMLTV timestamps have an explicit offset; a missing offset follows XMLTV's UTC default. */
private val XMLTV_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val EPG_SPACES = Regex("\\s+")
private val EPG_MARKS = Regex("\\p{M}+")
private val EPG_QUALITY = Regex("\\b(uhd|fhd|hd|sd|4k|1080p|720p)\\b")
private val EPG_NON_LETTERS = Regex("[^\\p{L}\\p{N}]")

internal fun xmltvTime(raw: String?): Long? = runCatching {
    val pieces = raw.orEmpty().trim().split(EPG_SPACES)
    val digits = pieces.first().padEnd(14, '0')
    val local = LocalDateTime.parse(digits.take(14), XMLTV_TIME_FORMAT)
    local.toInstant(if (pieces.size > 1) ZoneOffset.of(pieces[1]) else ZoneOffset.UTC).toEpochMilli()
}.getOrNull()

internal fun epgName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(EPG_MARKS, "").lowercase(Locale.ROOT)
    .replace(EPG_QUALITY, "")
    .replace(EPG_NON_LETTERS, "")

data class TVProgramme(val title: String, val start: Long, val end: Long, val description: String = "")
data class XmlTVChannel(val id: String, val names: List<String>)
data class LiveTVEpg(
    val channels: List<XmlTVChannel> = emptyList(),
    val programmes: Map<String, List<TVProgramme>> = emptyMap(),
    val sourceGuides: List<LiveTVEpg> = emptyList()
) {
    // Build once on the parsing dispatcher, rather than on first UI lookup.
    private val byId = channels.associateBy { it.id }
    private val byName = buildMap<String, MutableSet<String>> {
        channels.forEach { c -> c.names.forEach { getOrPut(epgName(it)) { mutableSetOf() }.add(c.id) } }
    }
    fun containsId(id: String): Boolean = id in byId
    fun channelId(channel: LiveTVChannel, explicitId: String? = null): String? {
        if (!explicitId.isNullOrBlank()) return explicitId.takeIf(::containsId)
        val candidates = listOf(channel.id, channel.id.substringAfterLast(':'))
        candidates.firstOrNull(::containsId)?.let { return it }
        // An ambiguous name must not silently show another country's schedule.
        val normalized = epgName(channel.name)
        if (normalized.isBlank()) return null
        return byName[normalized]?.singleOrNull()
    }
    fun schedule(channel: LiveTVChannel, explicitId: String? = null, now: Long = System.currentTimeMillis()): List<TVProgramme> {
        if (sourceGuides.isNotEmpty()) {
            for (guide in sourceGuides) {
                // IDs differ between publishers. Honour an override where it exists;
                // otherwise let this source use its normal unambiguous ID/name matching.
                val sourceId = explicitId?.takeIf { id -> guide.containsId(id) }
                val schedule = guide.schedule(channel, sourceId, now)
                if (schedule.any { it.end > now }) return schedule
            }
            return emptyList()
        }
        return channelId(channel, explicitId)?.let { programmes[it] }.orEmpty()
    }
}

/** Streaming parser: no XML document tree, bounded retained events and text. */
internal object XmlTVParser {
    fun parse(input: InputStream, now: Long, wantedNames: Set<String>? = null, wantedIds: Set<String> = emptySet()): LiveTVEpg {
        val allowed = mutableSetOf<String>()
        val channels = mutableListOf<XmlTVChannel>()
        val events = HashMap<String, MutableList<TVProgramme>>()
        var eventCount = 0
        val parser = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newSAXParser()
        val handler = object : DefaultHandler() {
            var channelId: String? = null
            var names = mutableListOf<String>()
            var programmeChannel: String? = null
            var keepProgramme = false
            var start: Long? = null
            var end: Long? = null
            var title = ""
            var description = ""
            var field: String? = null
            val text = StringBuilder()
            override fun resolveEntity(publicId: String?, systemId: String?) = org.xml.sax.InputSource(java.io.StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (qName) {
                    "channel" -> { channelId = attrs.getValue("id"); names = mutableListOf() }
                    "programme" -> {
                        programmeChannel = attrs.getValue("channel")
                        val id = programmeChannel
                        keepProgramme = eventCount < 60_000 && id != null &&
                            (wantedNames == null || id in allowed || id in wantedIds)
                        // Country-wide guides can contain hundreds of thousands of irrelevant events.
                        // Do not parse their dates or allocate their title/description strings.
                        start = if (keepProgramme) xmltvTime(attrs.getValue("start")) else null
                        end = if (keepProgramme) xmltvTime(attrs.getValue("stop")) else null
                        keepProgramme = keepProgramme && start != null && end != null &&
                            end!! > start!! && end!! > now - 6 * 3_600_000L && start!! < now + 48 * 3_600_000L
                        title = ""; description = ""
                    }
                    "display-name", "title", "desc" -> {
                        field = qName.takeIf { (qName == "display-name" && channelId != null) || keepProgramme }
                        text.setLength(0)
                    }
                }
            }
            override fun characters(ch: CharArray, offset: Int, length: Int) {
                if (field != null && text.length < 2048) text.append(ch, offset, minOf(length, 2048 - text.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (qName == field) {
                    when (qName) {
                        "display-name" -> if (names.size < 8) names.add(text.toString().trim())
                        "title" -> if (title.isEmpty()) title = text.toString().trim().take(192)
                        "desc" -> if (description.isEmpty()) description = text.toString().trim().take(384)
                    }
                    field = null
                }
                if (qName == "channel") {
                    channelId?.let { id ->
                        if (channels.size < 24_000) channels.add(XmlTVChannel(id, names.toList()))
                        if (wantedNames == null || id in wantedIds || names.any { epgName(it) in wantedNames }) allowed.add(id)
                    }
                    channelId = null
                }
                if (qName == "programme") {
                    val id = programmeChannel; val s = start; val e = end
                    if (id != null && (wantedNames == null || id in allowed || id in wantedIds) && s != null && e != null && e > s && title.isNotBlank() &&
                        e > now - 6 * 3_600_000L && s < now + 48 * 3_600_000L && eventCount < 60_000) {
                        events.getOrPut(id) { mutableListOf() }.add(TVProgramme(title, s, e, description))
                        eventCount++
                    }
                    programmeChannel = null
                    keepProgramme = false
                }
            }
        }
        parser.parse(input, handler)
        return LiveTVEpg(channels, events.mapValues { (_, v) -> v.distinctBy { it.start to it.title }.sortedBy { it.start } })
    }
}
