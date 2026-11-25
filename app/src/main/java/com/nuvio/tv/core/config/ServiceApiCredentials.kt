package com.nuvio.tv.core.config

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ServiceApi(val slot: Int, val fieldCount: Int) {
    TMDB(0, 1), TRAKT(1, 2), SIMKL(2, 1)
}

/** Installation credentials, separate from account tokens and profile synchronization. */
@Singleton
class ServiceApiCredentials @Inject constructor(@ApplicationContext context: Context) {
    private val protected = ProtectedProfilePreferences(context, "service_api_credentials", "nuvio.service_api_credentials")
    private val lock = Any()
    private val saved = mutableMapOf<ServiceApi, List<String>>()

    internal fun canConfigure(service: ServiceApi): Boolean = when (service) {
        ServiceApi.TMDB -> BuildConfig.TMDB_API_KEY.isBlank()
        ServiceApi.TRAKT -> BuildConfig.TRAKT_CLIENT_ID.isBlank() || BuildConfig.TRAKT_CLIENT_SECRET.isBlank()
        ServiceApi.SIMKL -> BuildConfig.SIMKL_CLIENT_ID.isBlank()
    }

    internal fun isConfigured(service: ServiceApi): Boolean =
        !canConfigure(service) || read(service).size == service.fieldCount

    val tmdbKey: String get() = BuildConfig.TMDB_API_KEY.ifBlank { read(ServiceApi.TMDB).firstOrNull().orEmpty() }
    val simklId: String get() = BuildConfig.SIMKL_CLIENT_ID.ifBlank { read(ServiceApi.SIMKL).firstOrNull().orEmpty() }
    val traktId: String get() = if (canConfigure(ServiceApi.TRAKT)) read(ServiceApi.TRAKT).firstOrNull().orEmpty() else BuildConfig.TRAKT_CLIENT_ID
    val traktSecret: String get() = if (canConfigure(ServiceApi.TRAKT)) read(ServiceApi.TRAKT).getOrNull(1).orEmpty() else BuildConfig.TRAKT_CLIENT_SECRET

    private fun read(service: ServiceApi): List<String> = synchronized(lock) {
        saved.getOrPut(service) {
            protected.read(service.slot)?.lines()?.takeIf { valid(service, it) }.orEmpty()
        }
    }

    internal suspend fun save(service: ServiceApi, fields: List<String>) = withContext(Dispatchers.IO) {
        val clean = fields.map(String::trim)
        require(canConfigure(service) && valid(service, clean))
        synchronized(lock) {
            protected.write(service.slot, clean.joinToString("\n"))
            saved[service] = clean
        }
    }

    internal fun valid(service: ServiceApi, fields: List<String>): Boolean =
        fields.size == service.fieldCount && fields.all { value ->
            value.length in 1..512 && value.all { it.isLetterOrDigit() || it in "-_." }
        }
}
