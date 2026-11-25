package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.config.ServiceApi
import com.nuvio.tv.core.config.ServiceApiCredentials
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel
class ServiceApiCredentialsViewModel @Inject constructor(
    internal val credentials: ServiceApiCredentials,
    private val tmdbApi: TmdbApi
) : ViewModel() {
    internal suspend fun save(service: ServiceApi, fields: List<String>): Int? {
        if (service == ServiceApi.TMDB) {
            val response = tmdbApi.getMovieDetails(11, fields.single().trim(), "fr-FR")
            if (response.code() == 401 || response.code() == 403) return R.string.service_api_invalid_tmdb
            if (!response.isSuccessful) return R.string.service_api_unavailable
        }
        credentials.save(service, fields)
        return null
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ServiceApiCredentialsDialog(
    service: ServiceApi,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ServiceApiCredentialsViewModel = hiltViewModel()
) {
    var key by remember(service) { mutableStateOf("") }
    var secret by remember(service) { mutableStateOf("") }
    var saving by remember(service) { mutableStateOf(false) }
    var error by remember(service) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val fields = if (service == ServiceApi.TRAKT) listOf(key.trim(), secret.trim()) else listOf(key.trim())
    val hint = when (service) {
        ServiceApi.TMDB -> R.string.service_api_tmdb_help
        ServiceApi.TRAKT -> R.string.service_api_trakt_help
        ServiceApi.SIMKL -> R.string.service_api_simkl_help
    }
    val keyLabel = if (service == ServiceApi.TMDB) R.string.service_api_tmdb_key else R.string.service_api_client_id
    NuvioDialog(
        onDismiss = { if (!saving) onDismiss() },
        title = stringResource(R.string.service_api_title, service.name),
        subtitle = stringResource(hint),
        suppressFirstKeyUp = false
    ) {
        InputField(key, { key = it.take(512) }, stringResource(keyLabel), KeyboardType.Password, isPassword = true,
            modifier = Modifier.fillMaxWidth())
        if (service == ServiceApi.TRAKT) {
            InputField(secret, { secret = it.take(512) }, stringResource(R.string.service_api_client_secret),
                KeyboardType.Password, isPassword = true, modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(stringResource(it), color = NuvioTheme.colors.Error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
            Button(enabled = !saving && viewModel.credentials.valid(service, fields), onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        val failure = viewModel.save(service, fields)
                        if (failure == null) onSaved() else error = failure
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = R.string.service_api_unavailable
                    } finally {
                        saving = false
                    }
                }
            }) { Text(stringResource(if (saving) R.string.service_api_saving else R.string.action_save)) }
        }
    }
}
