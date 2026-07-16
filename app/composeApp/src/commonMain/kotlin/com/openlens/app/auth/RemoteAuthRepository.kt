package com.openlens.app.auth

import com.openlens.app.scan.Env
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Ktor-backed [AuthRepository] talking to the FastAPI backend (contract in planning/auth.md).
 * Register/login/logout go through [publicClient]; session validation uses [authedClient] so an
 * expired access token is transparently refreshed. Tokens are persisted to [tokenStorage] on success.
 *
 * The app's Ktor client doesn't set `expectSuccess`, so non-2xx does NOT throw — we inspect
 * `response.status` explicitly and map it to a friendly [AuthResult.Error].
 */
class RemoteAuthRepository(
    private val tokenStorage: TokenStorage,
    private val publicClient: HttpClient,
    private val authedClient: HttpClient,
    private val baseUrl: String = Env.ACTIVE.baseUrl,
) : AuthRepository {

    override suspend fun register(username: String, email: String, password: String): AuthResult =
        tokenCall {
            publicClient.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        username = username.trim(),
                        email = email.trim(),
                        password = password,
                    ),
                )
            }
        }

    override suspend fun login(identifier: String, password: String): AuthResult =
        tokenCall {
            publicClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(identifier = identifier.trim(), password = password))
            }
        }

    override suspend fun validateSession(): Boolean {
        if (tokenStorage.refreshToken() == null) return false
        return try {
            authedClient.get("$baseUrl/auth/me").status == HttpStatusCode.OK
        } catch (_: Exception) {
            // A network error isn't a proof of invalid session, but we can't confirm it either.
            false
        }
    }

    override suspend fun logout() {
        val refresh = tokenStorage.refreshToken()
        if (refresh != null) {
            try {
                publicClient.post("$baseUrl/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refresh))
                }
            } catch (_: Exception) {
                // Best-effort revocation; we clear locally regardless.
            }
        }
        tokenStorage.clear()
    }

    /** Run a register/login request and map status → [AuthResult], persisting tokens on success. */
    private suspend fun tokenCall(request: suspend () -> HttpResponse): AuthResult {
        val response = try {
            request()
        } catch (_: Exception) {
            return AuthResult.Error("Couldn't reach the server. Check your connection and try again.")
        }
        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> {
                val pair: TokenPairDto = response.body()
                tokenStorage.save(pair.accessToken, pair.refreshToken)
                AuthResult.Success(Tokens(pair.accessToken, pair.refreshToken))
            }
            HttpStatusCode.Unauthorized -> AuthResult.Error("Invalid credentials.")
            HttpStatusCode.Conflict -> AuthResult.Error("That username or email is already taken.")
            HttpStatusCode.UnprocessableEntity ->
                AuthResult.Error("Check your details — username 3+ chars, valid email, password 8+ chars.")
            else -> AuthResult.Error("Something went wrong (${response.status.value}). Try again.")
        }
    }
}
