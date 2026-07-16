package com.openlens.app.auth

import com.openlens.app.scan.Env
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private const val TIMEOUT_MS = 60_000L

/**
 * Client with NO bearer/refresh plugin, for the public auth endpoints (register/login/refresh/logout).
 * These endpoints use 401 to mean "bad credentials" — routing them through the refresh plugin would
 * wrongly interpret that as an expired access token and try to refresh.
 */
fun createPublicClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
}

/**
 * Client that attaches the access token to every request and, on a 401 from a protected endpoint,
 * calls /auth/refresh (via [publicClient], so the refresh call itself isn't intercepted → no
 * recursion), persists the **rotated** pair, and retries. Ktor's bearer provider serializes
 * concurrent refreshes, so N parallel 401s trigger a single refresh. Used for scans and /auth/me.
 */
fun createAuthedClient(
    tokenStorage: TokenStorage,
    publicClient: HttpClient,
    baseUrl: String = Env.ACTIVE.baseUrl,
): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    install(Auth) {
        bearer {
            loadTokens {
                val access = tokenStorage.accessToken()
                val refresh = tokenStorage.refreshToken()
                if (access != null && refresh != null) BearerTokens(access, refresh) else null
            }
            // Attach the token proactively to our backend rather than waiting for a 401 first.
            sendWithoutRequest { true }
            refreshTokens {
                val refresh = tokenStorage.refreshToken() ?: return@refreshTokens null
                val response = publicClient.post("$baseUrl/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refresh))
                }
                if (response.status == HttpStatusCode.OK) {
                    val pair: TokenPairDto = response.body()
                    tokenStorage.save(pair.accessToken, pair.refreshToken)
                    BearerTokens(pair.accessToken, pair.refreshToken)
                } else {
                    // Refresh token expired / rotated out / revoked — the session is dead.
                    tokenStorage.clear()
                    null
                }
            }
        }
    }
}
