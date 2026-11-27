package com.nuvio.tv.ui.screens.iptv

import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.cleanEpgSources
import java.io.BufferedReader
import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class IptvPlaylist(val id: String, val name: String, val url: String, val guideUrl: String = "") {
    override fun toString() = "IptvPlaylist(id=$id)"
}

data class IptvChannel(
    val key: String,
    val guideChannel: LiveTVChannel,
    val group: String,
    val streamUrl: String,
    val headers: Map<String, String>
) {
    override fun toString() = "IptvChannel(key=$key)"
}

internal data class IptvCatalog(val channels: List<IptvChannel>, val guideUrls: List<String>)

internal fun parseIptvPlaylist(
    reader: BufferedReader,
    playlist: IptvPlaylist,
    baseUrl: HttpUrl,
    checkActive: () -> Unit = {}
): IptvCatalog {
    val first = generateSequence { reader.readLine() }.map { it.trim().removePrefix("\uFEFF").trimStart() }
        .firstOrNull { it.isNotEmpty() }.orEmpty()
    if (first.takeWhile { !it.isWhitespace() } != "#EXTM3U") throw IOException("Invalid IPTV playlist")

    val attributes = m3uAttributes(first.removePrefix("#EXTM3U"))
    val embeddedGuides = sequenceOf(attributes["url-tvg"].orEmpty(), attributes["x-tvg-url"].orEmpty())
        .flatMap { it.split(',').asSequence() }
        .mapNotNull { url -> url.trim().takeIf(String::isNotEmpty)?.let { baseUrl.resolve(it)?.toString() } }
        .toList()
    val guideUrls = cleanEpgSources(listOf(playlist.guideUrl) + embeddedGuides)
    val channels = linkedMapOf<String, IptvChannel>()
    val digest = MessageDigest.getInstance("SHA-256")
    var entry: M3uEntry? = null
    reader.forEachLine { rawLine ->
        checkActive()
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF:", ignoreCase = true) -> entry = parseM3uEntry(line.substringAfter(':'))
            line.startsWith("#EXT-X-", ignoreCase = true) -> throw IOException("Expected an IPTV catalogue, received HLS")
            line.startsWith("#EXTGRP:", ignoreCase = true) -> entry?.let {
                if (it.group.isEmpty()) it.group = line.substringAfter(':').trim().take(160)
            }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> entry?.let { pending ->
                val option = line.substringAfter(':')
                val header = when (option.substringBefore('=').lowercase()) {
                    "http-user-agent" -> "User-Agent"
                    "http-referrer", "http-referer" -> "Referer"
                    else -> null
                }
                val value = option.substringAfter('=', "").trim()
                if (header != null && value.isNotEmpty() && value.length <= 2048 && value.all { it in ' '..'~' }) {
                    pending.headers[header] = value
                }
            }
            line.isNotEmpty() && !line.startsWith('#') -> {
                entry?.let { pending ->
                    val url = baseUrl.resolve(line)?.toString()
                    if (url != null && pending.name.isNotEmpty()) {
                        val key = digest.digest("${playlist.id}\n${pending.name}\n${pending.group}\n$url".toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        val info = LiveTVChannel(
                            id = pending.epgId.ifEmpty { key }, type = "iptv", name = pending.name,
                            addonBaseUrl = "iptv:${playlist.id}", addonName = playlist.name,
                            logo = pending.logo.takeIf(String::isNotEmpty)?.toHttpUrlOrNull()?.toString()
                        )
                        channels.putIfAbsent(key, IptvChannel(key, info, pending.group, url, pending.headers.toMap()))
                        if (channels.size > 30_000) throw IOException("IPTV channel limit exceeded")
                    }
                }
                entry = null
            }
        }
    }
    if (channels.isEmpty()) throw IOException("No IPTV channels")
    return IptvCatalog(channels.values.toList(), guideUrls)
}

private data class M3uEntry(
    val name: String,
    val epgId: String,
    val logo: String,
    var group: String,
    val headers: MutableMap<String, String> = mutableMapOf()
)

private val Attribute = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
private fun m3uAttributes(raw: String) = Attribute.findAll(raw).associate { match ->
    match.groupValues[1].lowercase() to (match.groups[2]?.value ?: match.groups[3]?.value ?: match.groupValues[4])
}

private fun parseM3uEntry(raw: String): M3uEntry {
    // Commas in quoted metadata and in the channel name are part of the values.
    var quote: Char? = null
    val divider = raw.indexOfFirst { char ->
        when {
            quote == char -> { quote = null; false }
            quote == null && (char == '"' || char == '\'') -> { quote = char; false }
            else -> quote == null && char == ','
        }
    }
    val attrs = m3uAttributes(if (divider < 0) raw else raw.substring(0, divider))
    val displayName = if (divider < 0) "" else raw.substring(divider + 1).trim()
    return M3uEntry(
        displayName.ifEmpty { attrs["tvg-name"].orEmpty().trim() }.take(256),
        attrs["tvg-id"].orEmpty().trim().take(256),
        attrs["tvg-logo"].orEmpty().trim().take(4096),
        attrs["group-title"].orEmpty().trim().take(160)
    )
}
