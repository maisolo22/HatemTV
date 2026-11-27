package com.nuvio.tv.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.LiveTVLibraryStore
import com.nuvio.tv.ui.screens.livetv.LiveTVChannel
import com.nuvio.tv.ui.screens.livetv.LiveTVEpgRepository
import com.nuvio.tv.ui.screens.livetv.TVProgramme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LegacyLivePlayerInfo(
    val channel: LiveTVChannel? = null,
    val programmes: List<TVProgramme> = emptyList(),
    val folders: List<String> = emptyList(),
    val configured: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val stale: Boolean = false
)

@HiltViewModel
class LegacyLivePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    store: LiveTVLibraryStore,
    private val epgRepository: LiveTVEpgRepository
) : ViewModel() {
    val routeChannel = decodeLivePlayerChannel(savedStateHandle["liveChannel"])
    private val iptvGuide = decodeIptvPlayerGuide(savedStateHandle["liveChannel"])
    val routePoster: String? = savedStateHandle["poster"]
    private var playbackProfile = savedStateHandle.get<String>("profileId")?.toIntOrNull()
    private val channel = MutableStateFlow<LiveTVChannel?>(null)
    private val refresh = MutableStateFlow(0L)
    private val state = MutableStateFlow(LegacyLivePlayerInfo())
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(store.data, channel, refresh) { scoped, value, _ -> scoped to value }
                .catch { state.value = LegacyLivePlayerInfo(failed = true) }
                .collectLatest { (scoped, value) ->
                    if (playbackProfile == null) playbackProfile = scoped.profileId
                    if (value == null || scoped.profileId != playbackProfile) {
                        state.value = LegacyLivePlayerInfo(); return@collectLatest
                    }
                    if (iptvGuide != null) {
                        state.value = LegacyLivePlayerInfo(value, programmes = iptvGuide, configured = iptvGuide.isNotEmpty())
                        return@collectLatest
                    }
                    val library = scoped.library
                    val folders = library.folders.filter { folder -> folder.channels.any { it.key == value.key } }.map { it.name }
                    state.value = LegacyLivePlayerInfo(value, folders = folders,
                        configured = library.epgSources.isNotEmpty(), loading = library.epgSources.isNotEmpty())
                    if (library.epgSources.isEmpty()) return@collectLatest
                    try {
                        val result = epgRepository.loadSources(library.epgSources, channels = listOf(value), epgIds = library.epgIds)
                        state.value = state.value.copy(programmes = result.data.schedule(value, library.epgIds[value.key]),
                            loading = false, stale = result.stale)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { state.value = state.value.copy(loading = false, failed = true) }
                }
        }
    }
    fun bind(value: LiveTVChannel?) { channel.value = value }
    fun refresh() { refresh.value++ }
}
