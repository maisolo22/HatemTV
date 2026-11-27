package com.nuvio.tv.ui.screens.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.IptvPlaylistStore
import com.nuvio.tv.data.local.IptvSettings
import com.nuvio.tv.ui.screens.livetv.LimitedTVInput
import com.nuvio.tv.ui.screens.livetv.LiveTVEpg
import com.nuvio.tv.ui.screens.livetv.LiveTVEpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class IptvState(
    val profileId: Int? = null,
    val settings: IptvSettings = IptvSettings(),
    val selected: IptvPlaylist? = null,
    val catalog: IptvCatalog? = null,
    val guide: LiveTVEpg = LiveTVEpg(),
    val profileLoading: Boolean = true,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val loadingGuide: Boolean = false,
    val playlistFailed: Boolean = false,
    val guideFailed: Boolean = false,
    val storageFailed: Boolean = false
) {
    val favorites: Set<String> get() = settings.favorites[selected?.id].orEmpty()
}

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val profiles: ProfileManager,
    private val store: IptvPlaylistStore,
    private val epgRepository: LiveTVEpgRepository
) : ViewModel() {
    private val httpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val state = MutableStateFlow(IptvState())
    internal val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            profiles.activeProfileId.collectLatest { profileId ->
                loadJob?.cancel()
                saveJob?.cancel()
                state.value = IptvState(profileId = profileId)
                if (profileId <= 0) return@collectLatest
                try {
                    val settings = store.load(profileId)
                    state.update { it.copy(settings = settings, profileLoading = false) }
                    settings.playlists.firstOrNull()?.let(::select)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    state.update { it.copy(profileLoading = false, storageFailed = true) }
                }
            }
        }
    }

    fun select(playlist: IptvPlaylist) {
        if (playlist != state.value.selected) load(playlist, refresh = false)
    }

    fun refresh() {
        state.value.selected?.let { load(it, refresh = true) }
    }

    fun save(playlist: IptvPlaylist) {
        val saved = state.value.settings.playlists
        val updated = if (saved.none { it.id == playlist.id }) saved + playlist
            else saved.map { if (it.id == playlist.id) playlist else it }
        saveSettings(state.value.settings.copy(playlists = updated)) { select(playlist) }
    }

    fun remove(playlist: IptvPlaylist) {
        val settings = state.value.settings
        val updated = settings.playlists.filterNot { it.id == playlist.id }
        saveSettings(settings.copy(playlists = updated, favorites = settings.favorites - playlist.id)) {
            if (state.value.selected?.id == playlist.id) {
                loadJob?.cancel()
                state.update { it.copy(selected = null, catalog = null, guide = LiveTVEpg(),
                    loading = false, loadingGuide = false, playlistFailed = false, guideFailed = false) }
                updated.firstOrNull()?.let(::select)
            }
        }
    }

    fun toggleFavorite(channel: IptvChannel) {
        val current = state.value
        val id = current.selected?.id ?: return
        val updated = if (channel.key in current.favorites) current.favorites - channel.key
            else current.favorites + channel.key
        saveSettings(current.settings.copy(favorites = current.settings.favorites + (id to updated)))
    }

    private fun saveSettings(settings: IptvSettings, onSaved: () -> Unit = {}) {
        val current = state.value
        val profileId = current.profileId?.takeIf { it > 0 } ?: return
        if (current.saving || current.profileLoading) return
        state.update { it.copy(saving = true, storageFailed = false) }
        saveJob = viewModelScope.launch {
            try {
                store.save(profileId, settings)
                if (profiles.activeProfileId.value != profileId) return@launch
                state.update { it.copy(settings = settings, saving = false) }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(saving = false, storageFailed = true) }
            }
        }
    }

    private fun load(playlist: IptvPlaylist, refresh: Boolean) {
        val profileId = state.value.profileId
        loadJob?.cancel()
        state.update {
            val sameSource = it.selected == playlist
            it.copy(selected = playlist, catalog = it.catalog.takeIf { sameSource },
                guide = if (sameSource) it.guide else LiveTVEpg(),
                loading = true, loadingGuide = false, playlistFailed = false, guideFailed = false)
        }
        loadJob = viewModelScope.launch {
            try {
                val catalog = download(playlist)
                if (profiles.activeProfileId.value != profileId) return@launch
                state.update { it.copy(catalog = catalog, loading = false, loadingGuide = catalog.guideUrls.isNotEmpty()) }
                if (catalog.guideUrls.isEmpty()) {
                    state.update { it.copy(guide = LiveTVEpg()) }
                    return@launch
                }
                try {
                    val result = epgRepository.loadSources(catalog.guideUrls, refresh, catalog.channels.map { it.guideChannel })
                    if (profiles.activeProfileId.value == profileId) {
                        state.update { it.copy(guide = result.data, loadingGuide = false) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    state.update { it.copy(loadingGuide = false, guideFailed = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(loading = false, playlistFailed = true) }
            }
        }
    }

    private suspend fun download(playlist: IptvPlaylist): IptvCatalog = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(playlist.url).build()
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val catalogue = response.use {
                        if (!it.isSuccessful) throw IOException("IPTV HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty IPTV response")
                        LimitedTVInput(body.byteStream(), 24L * 1024 * 1024).bufferedReader().use { reader ->
                            parseIptvPlaylist(reader, playlist, it.request.url) {
                                if (!continuation.isActive) throw CancellationException()
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resume(catalogue)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
