package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import com.nuvio.tv.ui.screens.iptv.IptvPlaylist
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IptvSettings(
    val playlists: List<IptvPlaylist> = emptyList(),
    val favorites: Map<String, Set<String>> = emptyMap()
)

@Singleton
class IptvPlaylistStore @Inject constructor(@ApplicationContext context: Context) : ProfileScopedCredentialStore {
    private val storage = ProtectedProfilePreferences(context, "iptv_playlists", "com.nuvio.tv.iptv.playlists.v1")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(profileId: Int): IptvSettings = withContext(Dispatchers.IO) {
        storage.read(profileId)?.let { json.decodeFromString<IptvSettings>(it) } ?: IptvSettings()
    }

    suspend fun save(profileId: Int, value: IptvSettings) = withContext(Dispatchers.IO) {
        storage.write(profileId, json.encodeToString(IptvSettings.serializer(), value))
    }

    override fun removeProfile(profileId: Int) = storage.write(profileId, null)
    override fun clearAllProfiles() = storage.clear()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IptvStoreModule {
    @Binds
    @IntoSet
    abstract fun bindIptvCredentials(store: IptvPlaylistStore): ProfileScopedCredentialStore
}
