package com.nuvio.tv.ui.screens.livetv

import java.security.MessageDigest
import java.util.UUID
import com.nuvio.tv.domain.model.MetaPreview

/** A local channel reference, never a resolved (expiring) playback URL. */
data class LiveTVChannel(
    val id: String, val type: String, val name: String,
    val addonBaseUrl: String, val addonName: String,
    val logo: String? = null, val artwork: String? = null, val description: String? = null
) {
    val key: String get() = MessageDigest.getInstance("SHA-256")
        .digest("$addonBaseUrl\n$type\n$id".toByteArray()).joinToString("") { "%02x".format(it) }
    companion object {
        fun from(item: MetaPreview, source: LiveTVSource) = LiveTVChannel(
            item.id, source.catalog.apiType, item.name, source.addon.baseUrl,
            source.addon.displayName, item.logo ?: item.poster, item.backdropUrl, item.description
        )
    }
}

enum class LiveTVViewMode { LIST, MOSAIC, GUIDE }
data class LiveTVFolder(val id: String, val name: String, val channels: List<LiveTVChannel> = emptyList())
data class LiveTVLibrary(
    val viewMode: LiveTVViewMode = LiveTVViewMode.LIST,
    val folders: List<LiveTVFolder> = emptyList(),
    val epgUrl: String = "",
    val epgIds: Map<String, String> = emptyMap(),
    // Null means a library saved before multiple sources were supported.
    val epgUrls: List<String>? = null
) {
    val epgSources: List<String> get() = cleanEpgSources(epgUrls ?: listOf(epgUrl))
    fun withEpgSources(urls: List<String>): LiveTVLibrary {
        val clean = cleanEpgSources(urls)
        return copy(epgUrl = clean.firstOrNull().orEmpty(), epgUrls = clean)
    }
    fun createFolder(name: String, channel: LiveTVChannel? = null, id: String = UUID.randomUUID().toString()): LiveTVLibrary {
        val clean = name.trim().take(60)
        if (clean.isBlank() || folders.any { it.name.equals(clean, true) }) return this
        return copy(folders = folders + LiveTVFolder(id, clean, listOfNotNull(channel)))
    }
    fun renameFolder(id: String, name: String): LiveTVLibrary {
        val clean = name.trim().take(60)
        if (clean.isBlank() || folders.any { it.id != id && it.name.equals(clean, true) }) return this
        return copy(folders = folders.map { if (it.id == id) it.copy(name = clean) else it })
    }
    fun deleteFolder(id: String) = copy(folders = folders.filterNot { it.id == id })
    fun toggleChannel(folderId: String, channel: LiveTVChannel): LiveTVLibrary = copy(folders = folders.map { folder ->
        if (folder.id != folderId) folder else folder.copy(channels = if (folder.channels.any { it.key == channel.key })
            folder.channels.filterNot { it.key == channel.key } else folder.channels + channel)
    })
}

internal const val MAX_EPG_SOURCES = 8
internal fun cleanEpgSources(urls: List<String>): List<String> =
    urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_EPG_SOURCES)
