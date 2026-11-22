package com.nuvio.tv.ui.screens.livetv

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTVViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun addon(base: String = "https://a.example", enabled: Boolean = true, type: String = "channel") = Addon(
        id = "same-addon-id", name = "TV", version = "1", description = null, logo = null,
        baseUrl = base, catalogs = listOf(CatalogDescriptor(ContentType.fromString(type), type,
            "channels", "Channels", extra = listOf(CatalogExtra("skip")))),
        types = listOf(ContentType.fromString(type)), resources = emptyList(), enabled = enabled
    )
    private fun model(addons: MutableStateFlow<List<Addon>>, repo: CatalogRepository): LiveTVViewModel {
        val installed = mockk<AddonRepository>()
        every { installed.getInstalledAddons() } returns addons
        return LiveTVViewModel(installed, repo)
    }
    private fun row(type: String, vararg ids: String) = CatalogRow(
        "same-addon-id", "TV", "https://a.example", "channels", "Channels", ContentType.fromString(type),
        rawType = type, items = ids.map { id -> mockk<MetaPreview> { every { this@mockk.id } returns id; every { apiType } returns type } },
        hasMore = ids.isNotEmpty(), supportsSkip = true, nextSkip = ids.size
    )

    @Test fun `all legacy TV types are found with separate source URLs and disabled addons excluded`() {
        for (type in listOf("tv", "channel", "channels", "live", "iptv")) {
            val sources = liveTVSources(listOf(addon(type = type), addon("https://b.example", type = type), addon(enabled = false)))
            assertEquals(2, sources.size)
            assertEquals(type, sources.first().catalog.apiType)
        }
        assertTrue(liveTVSources(listOf(addon(type = "movie"))).isEmpty())
        val search = addon().let { it.copy(catalogs = it.catalogs.map { c -> c.copy(extra = listOf(CatalogExtra("search", true))) }) }
        assertTrue(liveTVSources(listOf(search)).isEmpty())
    }

    @Test fun `pagination preserves raw type and deduplicates channel identities`() = runTest {
        val repo = RecordingRepository { type, skip -> flowOf(NetworkResult.Success(
            if (skip == 0) row(type, "one", "two") else row(type, "two", "three")
        )) }
        val vm = model(MutableStateFlow(listOf(addon(type = "iptv"))), repo)
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("iptv" to 0, "iptv" to 2), repo.requests)
        assertEquals(listOf("one", "two", "three"), vm.uiState.value.row!!.items.map { it.id })
    }

    @Test fun `switching sources cancels the old catalog and disabling clears channels`() = runTest {
        val repo = RecordingRepository { type, _ -> flow {
            if (type == "tv") delay(1000)
            emit(NetworkResult.Success(row(type, type)))
        } }
        val installed = MutableStateFlow(listOf(addon(type = "tv"), addon("https://b.example", type = "live")))
        val vm = model(installed, repo)
        runCurrent()
        vm.select(vm.uiState.value.sources.last().key)
        advanceUntilIdle()
        assertEquals("live", vm.uiState.value.row!!.items.single().id)
        installed.value = installed.value.map { it.copy(enabled = false) }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sources.isEmpty())
        assertNull(vm.uiState.value.row)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun `failed catalog can be retried and loading always finishes`() = runTest {
        var fail = true
        val repo = RecordingRepository { type, _ -> flow {
            emit(NetworkResult.Loading)
            if (fail) emit(NetworkResult.Error("offline")) else emit(NetworkResult.Success(row(type, "one")))
        } }
        val vm = model(MutableStateFlow(listOf(addon())), repo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
        fail = false
        vm.retry()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.error)
        assertEquals("one", vm.uiState.value.row!!.items.single().id)
    }

    private class RecordingRepository(val respond: (String, Int) -> Flow<NetworkResult<CatalogRow>>) : CatalogRepository {
        val requests = mutableListOf<Pair<String, Int>>()
        override fun getCatalog(addonBaseUrl: String, addonId: String, addonName: String,
            catalogId: String, catalogName: String, type: String, skip: Int, skipStep: Int,
            extraArgs: Map<String, String>, supportsSkip: Boolean, posterScreen: CustomPosterScreen
        ): Flow<NetworkResult<CatalogRow>> {
            requests += type to skip
            return respond(type, skip)
        }
    }
}
