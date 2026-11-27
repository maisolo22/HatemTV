package com.nuvio.tv.ui.screens.player

import android.view.KeyEvent
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.TVProgramme
import java.util.Locale

private val liveChannelTypes = setOf("tv", "channel", "channels", "live", "iptv")
private val liveContextGson = Gson()
private const val MaxIptvPlayerProgrammes = 6

internal fun encodeLivePlayerChannel(channel: LiveTVChannel): String =
    liveContextGson.toJson(channel.copy(artwork = null, description = null))

internal fun encodeIptvPlayerChannel(channel: LiveTVChannel, programmes: List<TVProgramme>): String {
    val objectWithGuide = liveContextGson.toJsonTree(channel.copy(artwork = null, description = null)).asJsonObject
    objectWithGuide.add("iptvGuide", liveContextGson.toJsonTree(programmes.take(MaxIptvPlayerProgrammes)))
    return liveContextGson.toJson(objectWithGuide)
}

internal fun decodeIptvPlayerGuide(json: String?): List<TVProgramme>? = runCatching {
    if (json.isNullOrBlank() || json.length > 32_768) return@runCatching null
    val guide = JsonParser.parseString(json).asJsonObject.getAsJsonArray("iptvGuide") ?: return@runCatching null
    guide.take(MaxIptvPlayerProgrammes).map { liveContextGson.fromJson(it, TVProgramme::class.java) }
}.getOrNull()

internal fun decodeLivePlayerChannel(json: String?): LiveTVChannel? = runCatching {
    if (json.isNullOrBlank() || json.length > 32_768) return@runCatching null
    liveContextGson.fromJson(json, LiveTVChannel::class.java)?.takeIf {
        it.id.isNotBlank() && it.name.isNotBlank() && it.type.lowercase(Locale.ROOT) in liveChannelTypes
    }
}.getOrNull()

internal fun useLegacyLivePlayer(type: String?, explicitChannel: LiveTVChannel?, reportsLive: Boolean): Boolean =
    explicitChannel != null || type?.lowercase(Locale.ROOT) in setOf("channel", "channels", "live", "iptv") ||
        (type.equals("tv", true) && reportsLive)

internal data class LegacyLiveProgrammes(val current: TVProgramme?, val next: TVProgramme?, val progress: Float)
internal fun legacyLiveProgrammes(programmes: List<TVProgramme>, now: Long): LegacyLiveProgrammes {
    val valid = programmes.filter { it.end > it.start }
    val current = valid.filter { it.start <= now && it.end > now }.maxByOrNull { it.start }
    val next = valid.filter { it.start >= (current?.end ?: (now + 1)) }.minByOrNull { it.start }
    return LegacyLiveProgrammes(current, next, current?.let {
        ((now - it.start).toFloat() / (it.end - it.start)).coerceIn(0f, 1f)
    } ?: 0f)
}

internal enum class LegacyLiveRemoteKey { LEFT, RIGHT, UP, DOWN, CONFIRM, INFO, MENU, OTHER }
internal enum class LegacyLiveKeyAction { PASS, REVEAL, CONSUME }

/** Consume both halves (and held repeats) of the key that reveals the controls. */
internal class LegacyLiveWakeGate {
    private var opening: LegacyLiveRemoteKey? = null
    fun capture(key: LegacyLiveRemoteKey) { opening = key }
    fun key(key: LegacyLiveRemoteKey, down: Boolean, hidden: Boolean, blocked: Boolean): LegacyLiveKeyAction {
        if (opening == key) {
            if (!down) opening = null
            return LegacyLiveKeyAction.CONSUME
        }
        if (blocked || key == LegacyLiveRemoteKey.OTHER) return LegacyLiveKeyAction.PASS
        if (down && hidden) { opening = key; return LegacyLiveKeyAction.REVEAL }
        return LegacyLiveKeyAction.PASS
    }
}

/** Only decoder measurements become quality labels. Addon advertising is not a measurement. */
internal fun legacyLiveTechnicalLabels(media: PlayerMediaInfo, fps: Float): List<String> = buildList {
    val width = media.videoWidth; val height = media.videoHeight
    if (width != null && height != null && width > 0 && height > 0) add("$width × $height")
    media.videoCodec?.takeIf { it.isNotBlank() }?.let(::add)
    if (fps.isFinite() && fps > 0) add(String.format(Locale.ROOT, "%.2f fps", fps))
    media.audioCodec?.takeIf { it.isNotBlank() }?.let { codec ->
        add(codec + (media.audioChannels?.takeIf { it > 0 }?.let { " · $it ch" } ?: ""))
    }
    addAll(media.hdr.distinct())
}

internal fun iptvZapDirection(keyCode: Int, controlsVisible: Boolean): Int? = when (keyCode) {
    KeyEvent.KEYCODE_CHANNEL_UP -> 1
    KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
    KeyEvent.KEYCODE_DPAD_UP -> if (controlsVisible) null else 1
    KeyEvent.KEYCODE_DPAD_DOWN -> if (controlsVisible) null else -1
    else -> null
}

internal fun legacyLiveInputBlocked(s: PlayerUiState, allowWhileLoading: Boolean = false): Boolean =
    s.error != null || (!allowWhileLoading && s.showLoadingOverlay) ||
    s.showAudioOverlay || s.showSubtitleOverlay || s.showSubtitleStylePanel || s.showSubtitleTimingDialog ||
    s.showSubtitleDelayOverlay || s.showSpeedDialog || s.showMoreDialog || s.showSourcesPanel ||
    s.showEpisodesPanel || s.showChaptersPanel || s.showPartyPanel || s.showStreamInfoOverlay ||
    s.postPlayMode is PostPlayMode.StillWatching
