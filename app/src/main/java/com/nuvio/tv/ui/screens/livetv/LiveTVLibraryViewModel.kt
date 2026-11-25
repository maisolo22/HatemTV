package com.nuvio.tv.ui.screens.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.LiveTVLibraryStore
import com.nuvio.tv.data.local.ProfileLiveTVLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LiveTVLibraryViewModel @Inject constructor(
    private val store: LiveTVLibraryStore,
    private val epgRepository: LiveTVEpgRepository
) : ViewModel() {
    private val state = MutableStateFlow(ProfileLiveTVLibrary(0, LiveTVLibrary()))
    val settings = state.asStateFlow()
    val epg = MutableStateFlow(LiveTVEpg())
    val epgLoading = MutableStateFlow(false)
    val epgFailed = MutableStateFlow(false)
    val epgStale = MutableStateFlow(false)
    val epgPartial = MutableStateFlow(false)
    val saveFailed = MutableStateFlow(false)
    private var epgJob: Job? = null
    private var targets = emptyList<LiveTVChannel>()
    private var generation = 0
    init {
        viewModelScope.launch {
            store.data.collect { value ->
                val profileChanged = value.profileId != state.value.profileId
                val reload = profileChanged || value.library.epgSources != state.value.library.epgSources || value.library.epgIds != state.value.library.epgIds
                if (profileChanged) targets = emptyList()
                state.value = value
                if (reload) { generation++; epgJob?.cancel(); epg.value = LiveTVEpg(); refreshEpg() }
            }
        }
    }
    private fun update(change: (LiveTVLibrary) -> LiveTVLibrary) {
        val profile = state.value.profileId
        if (profile == 0) return
        viewModelScope.launch {
            try { store.update(profile, change); saveFailed.value = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveFailed.value = true }
        }
    }
    fun mode(mode: LiveTVViewMode) = update { it.copy(viewMode = mode) }
    fun menuOrder(keys: List<String>) = update { it.withMenuOrder(keys) }
    fun create(name: String, channel: LiveTVChannel? = null) = update { it.createFolder(name, channel) }
    fun rename(id: String, name: String) = update { it.renameFolder(id, name) }
    fun delete(id: String) = update { it.deleteFolder(id) }
    fun toggle(folderId: String, channel: LiveTVChannel) = update { it.toggleChannel(folderId, channel) }
    fun epgSources(urls: List<String>) = update { it.withEpgSources(urls) }
    fun channelEpgId(channel: LiveTVChannel, id: String) = update {
        it.copy(epgIds = it.epgIds.toMutableMap().apply { if (id.isBlank()) remove(channel.key) else put(channel.key, id.trim()) })
    }
    fun targets(profileId: Int, channels: List<LiveTVChannel>) {
        if (state.value.profileId != profileId) return
        val distinct = channels.distinctBy { it.key }
        if (targets != distinct) { targets = distinct; refreshEpg() }
    }
    fun refreshEpg(force: Boolean = false) {
        generation++
        val token = generation
        epgJob?.cancel()
        val urls = state.value.library.epgSources
        epgFailed.value = false; epgLoading.value = urls.isNotEmpty() && targets.isNotEmpty(); epgStale.value = false; epgPartial.value = false
        if (urls.isEmpty() || targets.isEmpty()) return
        val channels = targets
        val ids = state.value.library.epgIds
        epgJob = viewModelScope.launch {
            try {
                if (!force) delay(450) // Coalesce catalogue pagination and quick folder navigation.
                val result = epgRepository.loadSourcesProgressively(urls, force, channels, ids) { partial ->
                    withContext(Dispatchers.Main.immediate) {
                        if (token == generation) {
                            epg.value = partial.data; epgStale.value = partial.stale
                            epgPartial.value = partial.failedSources > 0
                        }
                    }
                }
                if (token == generation) { epg.value = result.data; epgStale.value = result.stale; epgPartial.value = result.failedSources > 0 }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) epgFailed.value = true }
            finally { if (token == generation) epgLoading.value = false }
        }
    }
}
