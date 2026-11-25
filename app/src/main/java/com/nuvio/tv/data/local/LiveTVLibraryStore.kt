package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.ui.screens.livetv.LiveTVLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileLiveTVLibrary(val profileId: Int, val library: LiveTVLibrary)

@Singleton
class LiveTVLibraryStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profiles: ProfileManager
) {
    private val key = stringPreferencesKey("library_v1")
    private val gson = Gson()
    private fun decode(json: String?) = runCatching {
        gson.fromJson(json, LiveTVLibrary::class.java)
    }.getOrNull() ?: LiveTVLibrary()
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: Flow<ProfileLiveTVLibrary> = profiles.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, "live_tv_library").data.map { ProfileLiveTVLibrary(profileId, decode(it[key])) }
    }
    suspend fun update(profileId: Int, transform: (LiveTVLibrary) -> LiveTVLibrary) {
        // Capture profile at the UI action, not after a queued write or network response.
        if (factory.isProfileDeleted(profileId)) return
        factory.get(profileId, "live_tv_library").edit { prefs -> prefs[key] = gson.toJson(transform(decode(prefs[key]))) }
    }
}
