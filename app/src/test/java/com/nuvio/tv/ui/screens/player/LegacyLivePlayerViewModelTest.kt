package com.nuvio.tv.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import com.nuvio.tv.data.local.LiveTVLibraryStore
import com.nuvio.tv.data.local.ProfileLiveTVLibrary
import com.nuvio.tv.ui.screens.livetv.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyLivePlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun `player reuses the exact per-profile XMLTV mapping and clears it on profile changes`() = runTest {
        val channel = LiveTVChannel("provider:one", "tv", "FR | My Channel", "https://addon.example", "Addon")
        val programme = TVProgramme("News", 100, 200)
        val library = LiveTVLibrary(epgUrl = "https://epg.example/tv.xml.gz", epgIds = mapOf(channel.key to "xml-id"))
            .createFolder("Sport", channel, "sports")
        val scoped = MutableStateFlow(ProfileLiveTVLibrary(1, library))
        val store = mockk<LiveTVLibraryStore>()
        every { store.data } returns scoped
        val repository = mockk<LiveTVEpgRepository>()
        coEvery { repository.load(any(), any(), any(), any()) } returns LiveTVEpgResult(
            LiveTVEpg(listOf(XmlTVChannel("xml-id", listOf("Different source name"))), mapOf("xml-id" to listOf(programme))), 0, false)
        val vm = LegacyLivePlayerViewModel(SavedStateHandle(mapOf("profileId" to "1", "liveChannel" to encodeLivePlayerChannel(channel))), store, repository)
        vm.bind(channel)
        advanceUntilIdle()
        assertEquals(listOf(programme), vm.uiState.value.programmes)
        assertEquals(listOf("Sport"), vm.uiState.value.folders)
        coVerify { repository.load(library.epgUrl, false, listOf(channel), library.epgIds) }
        scoped.value = ProfileLiveTVLibrary(2, library.copy(epgUrl = "https://private-profile.example/epg.xml"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.programmes.isEmpty())
        coVerify(exactly = 0) { repository.load("https://private-profile.example/epg.xml", any(), any(), any()) }
    }
}
