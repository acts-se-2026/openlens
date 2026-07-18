package com.openlens.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// encodeDefaults = true is essential: the flow payloads carry `method = "password"` as a default,
// and without this kotlinx.serialization drops it — Kratos then can't pick a strategy ("Could not
// find a strategy to sign you up with"). ignoreUnknownKeys lets us model only the flow fields we read.
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val TIMEOUT_MS = 60_000L

/**
 * Client for Kratos's self-service flows (register / login / logout / whoami). Sends
 * `Accept: application/json` so Kratos returns flow JSON instead of a browser redirect. It attaches
 * no auth header of its own — the repository sets `X-Session-Token` explicitly on the calls that
 * need it (whoami), so a stale token never rides along on a login/register attempt.
 */
fun createPublicClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}

/**
 * Client for our FastAPI backend (scans). Attaches the stored Kratos session token as
 * `Authorization: Bearer <token>` on every request — [tokenStorage] is read per call, so it always
 * reflects the current login. There's no refresh dance: Kratos session tokens don't rotate, so a
 * 401 just means the session is dead and the caller drops back to the login screen.
 */
fun createAuthedClient(tokenStorage: TokenStorage): HttpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        requestTimeoutMillis = TIMEOUT_MS
        socketTimeoutMillis = TIMEOUT_MS
    }
    defaultRequest {
        tokenStorage.sessionToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}
