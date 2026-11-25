package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.config.ServiceApiCredentials
import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeResponseDto
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRefreshTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRevokeRequestDto
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TraktAuthServiceTest {
    @Test
    fun `device sign-in requests and polls for a code using only a client ID`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        val code = TraktDeviceCodeResponseDto("device-code", "USERCODE", "https://trakt.tv/activate", 600, 5)
        coEvery { traktAuthDataStore.getCurrentState() } returnsMany listOf(
            TraktAuthState(),
            TraktAuthState(deviceCode = code.deviceCode)
        )
        coEvery { traktAuthDataStore.saveDeviceFlow(code) } returns Unit
        coEvery { traktApi.requestDeviceCode(match { it.clientId == "test-client-id" }) } returns Response.success(code)
        coEvery {
            traktApi.requestDeviceToken(TraktDeviceTokenRequestDto(code.deviceCode, "test-client-id", null))
        } returns Response.error(400, "authorization_pending".toResponseBody())
        val service = TraktAuthService(
            context = mockk(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = mockk(),
            apiCredentials = registeredApp(secret = "")
        )

        assertEquals(code, service.startDeviceAuth().getOrThrow())
        assertEquals(TraktTokenPollResult.Pending, service.pollDeviceToken())
        coVerify(exactly = 1) { traktAuthDataStore.saveDeviceFlow(code) }
    }

    @Test
    fun `OAuth JSON omits an absent client secret`() {
        val moshi = Moshi.Builder().build()
        val poll = moshi.adapter(TraktDeviceTokenRequestDto::class.java)
        val refresh = moshi.adapter(TraktRefreshTokenRequestDto::class.java)
        val revoke = moshi.adapter(TraktRevokeRequestDto::class.java)

        assertFalse(poll.toJson(TraktDeviceTokenRequestDto("code", "id", null)).contains("client_secret"))
        assertFalse(refresh.toJson(TraktRefreshTokenRequestDto("token", "id", null, "uri")).contains("client_secret"))
        assertFalse(revoke.toJson(TraktRevokeRequestDto("token", "id", null)).contains("client_secret"))
    }

    @Test
    fun `refresh token 400 clears credentials and prevents another refresh`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        val authSessionNoticeDataStore = mockk<AuthSessionNoticeDataStore>()
        var authState = authenticatedState()

        coEvery { traktAuthDataStore.getCurrentState() } answers { authState }
        coEvery { traktAuthDataStore.clearAuth() } answers { authState = TraktAuthState() }
        coEvery { authSessionNoticeDataStore.markTraktReconnectRequired() } returns Unit
        coEvery { traktApi.refreshToken(any()) } returns Response.error(400, "invalid_grant".toResponseBody())

        val service = TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = authSessionNoticeDataStore,
            apiCredentials = registeredApp()
        )

        assertFalse(service.refreshTokenIfNeeded(force = true))
        assertEquals(2_000L, currentTime)
        assertFalse(service.refreshTokenIfNeeded(force = true))

        // A rejected refresh is retried once, two seconds later, before the credentials are cleared.
        coVerify(exactly = 2) { traktApi.refreshToken(match {
            it.clientId == "test-client-id" && it.clientSecret == "test-client-secret"
        }) }
        coVerify(exactly = 1) { authSessionNoticeDataStore.markTraktReconnectRequired() }
        coVerify(exactly = 1) { traktAuthDataStore.clearAuth() }
    }

    @Test
    fun `a request without a Trakt account is skipped and the reason is kept once`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        coEvery { traktAuthDataStore.getCurrentState() } returns TraktAuthState()
        val service = TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = mockk(relaxed = true)
        )

        repeat(3) {
            assertNull(service.executeAuthorizedRequest { header -> traktApi.getLastActivities(header) })
        }

        assertEquals(setOf("not signed in"), service.reportedFailureKinds())
        coVerify(exactly = 0) { traktApi.getLastActivities(any()) }
    }

    @Test
    fun `refresh token server error keeps credentials and allows a later retry`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        val authSessionNoticeDataStore = mockk<AuthSessionNoticeDataStore>()
        val authState = authenticatedState()

        coEvery { traktAuthDataStore.getCurrentState() } returns authState
        coEvery { traktApi.refreshToken(any()) } returns Response.error(503, "unavailable".toResponseBody())

        val service = TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = authSessionNoticeDataStore,
            apiCredentials = registeredApp(secret = "")
        )

        assertFalse(service.refreshTokenIfNeeded(force = true))
        assertFalse(service.refreshTokenIfNeeded(force = true))

        coVerify(exactly = 2) { traktApi.refreshToken(match {
            it.clientId == "test-client-id" && it.clientSecret == null
        }) }
        coVerify(exactly = 0) { authSessionNoticeDataStore.markTraktReconnectRequired() }
        coVerify(exactly = 0) { traktAuthDataStore.clearAuth() }
    }

    private fun registeredApp(secret: String = "test-client-secret"): ServiceApiCredentials = mockk {
        every { traktId } returns "test-client-id"
        every { traktSecret } returns secret
    }

    private fun authenticatedState(): TraktAuthState {
        return TraktAuthState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "bearer",
            createdAt = 1L,
            expiresIn = 3600
        )
    }
}
