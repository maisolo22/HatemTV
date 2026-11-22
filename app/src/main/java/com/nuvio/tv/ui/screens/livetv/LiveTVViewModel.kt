package com.nuvio.tv.ui.screens.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Keep the manifest's exact type: custom channel types must survive catalog and stream calls. */
data class LiveTVSource(val addon: Addon, val catalog: CatalogDescriptor) {
    val key: String get() = "${addon.baseUrl}|${catalog.apiType}|${catalog.id}"
    val label: String get() = "${addon.displayName} · ${catalog.name}"
}

internal fun liveTVSources(addons: List<Addon>): List<LiveTVSource> = addons
    .filter { it.enabled }
    .flatMap { addon ->
        addon.catalogs.filter { catalog ->
            catalog.apiType.lowercase() in setOf("tv", "channel", "channels", "live", "iptv") &&
                !catalog.isSearchOnlyCatalog()
        }.map { LiveTVSource(addon, it) }
    }.distinctBy { it.key }

data class LiveTVState(
    val sources: List<LiveTVSource> = emptyList(),
    val selectedKey: String? = null,
    val row: CatalogRow? = null,
    val loading: Boolean = true,
    val error: Boolean = false
) {
    val selected: LiveTVSource? get() = sources.firstOrNull { it.key == selectedKey }
}

@HiltViewModel
class LiveTVViewModel @Inject constructor(
    addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository
) : ViewModel() {
    private val state = MutableStateFlow(LiveTVState())
    val uiState = state.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            addonRepository.getInstalledAddons().map(::liveTVSources).distinctUntilChanged().collect { sources ->
                val selected = sources.firstOrNull { it.key == state.value.selectedKey } ?: sources.firstOrNull()
                state.update { it.copy(sources = sources) }
                if (selected == null) {
                    generation++
                    request?.cancel()
                    state.update { it.copy(selectedKey = null, row = null, loading = false, error = false) }
                } else {
                    select(selected.key)
                }
            }
        }
    }

    fun select(key: String) {
        val source = state.value.sources.firstOrNull { it.key == key } ?: return
        generation++
        request?.cancel()
        state.update { it.copy(selectedKey = key, row = null, error = false, loading = true) }
        load(source, append = false)
    }

    fun retry() { state.value.selectedKey?.let(::select) }

    fun loadMore() {
        val current = state.value
        if (current.loading || current.row?.hasMore != true) return
        current.selected?.let { load(it, append = true) }
    }

    private fun load(source: LiveTVSource, append: Boolean) {
        val token = generation
        val previous = if (append) state.value.row else null
        state.update { it.copy(loading = true, error = false) }
        request = viewModelScope.launch {
            val catalog = source.catalog
            val supportsSkip = catalog.extra.any { it.name == "skip" } || "skip" in catalog.extraSupported
            val args = catalog.extra.filter { it.isRequired && it.name != "skip" }
                .mapNotNull { extra -> (extra.defaultValue ?: extra.options?.firstOrNull())?.let { extra.name to it } }.toMap()
            try {
                val result = withTimeout(30_000) {
                    catalogRepository.getCatalog(
                        addonBaseUrl = source.addon.baseUrl,
                        addonId = source.addon.id, addonName = source.addon.displayName,
                        catalogId = catalog.id, catalogName = catalog.name, type = catalog.apiType,
                        skip = previous?.nextCatalogSkip() ?: 0,
                        skipStep = catalog.pageSize ?: 100,
                        supportsSkip = supportsSkip, extraArgs = args
                    ).first { it !is NetworkResult.Loading }
                }
                if (token != generation) return@launch
                when (result) {
                    is NetworkResult.Success -> {
                        val page = result.data.copy(items = result.data.items.distinctBy { item -> item.id })
                        state.update { it.copy(row = previous?.mergeCatalogPage(page) ?: page, loading = false, error = false) }
                    }
                    else -> state.update { it.copy(loading = false, error = true) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException && token == generation) {
                    state.update { it.copy(loading = false, error = true) }
                } else throw e
            } catch (_: Exception) {
                if (token == generation) state.update { it.copy(loading = false, error = true) }
            }
        }
    }
}
