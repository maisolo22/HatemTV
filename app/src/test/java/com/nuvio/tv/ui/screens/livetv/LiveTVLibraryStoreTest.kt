package com.nuvio.tv.ui.screens.livetv

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.LiveTVLibraryStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LiveTVLibraryStoreTest {
    @Test fun `folders view and EPG mappings survive reopening and remain separate between profiles`() = runTest {
        val active = MutableStateFlow(1)
        val profile = mockk<ProfileManager>()
        every { profile.activeProfileId } returns active
        val stores = mutableMapOf<Int, MemoryPreferences>()
        val factory = mockk<ProfileDataStoreFactory>()
        every { factory.get(any(), "live_tv_library") } answers { stores.getOrPut(firstArg()) { MemoryPreferences() } }
        every { factory.isProfileDeleted(any()) } returns false
        val store = LiveTVLibraryStore(factory, profile)
        val channel = LiveTVChannel("id", "channel", "Sport TV", "https://provider.example", "Provider")
        store.update(1) { it.createFolder("Sport", channel, "sports").copy(viewMode = LiveTVViewMode.GUIDE,
            epgUrl = "https://example.com/tv.xml.gz", epgIds = mapOf(channel.key to "sport.fr")) }
        active.value = 2
        assertTrue(store.data.first().library.folders.isEmpty())
        store.update(2) { it.createFolder("Films", id = "films") }
        val reopened = LiveTVLibraryStore(factory, profile)
        active.value = 1
        val restored = reopened.data.first().library
        assertEquals("Sport", restored.folders.single().name)
        assertEquals(channel, restored.folders.single().channels.single())
        assertEquals(LiveTVViewMode.GUIDE, restored.viewMode)
        assertEquals("sport.fr", restored.epgIds[channel.key])
        active.value = 2
        assertEquals("Films", reopened.data.first().library.folders.single().name)
    }
    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state.asStateFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value); state.value = next; return next
        }
    }
}
