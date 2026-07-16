package com.openlens.app.auth

/** The access + refresh token pair returned by the backend. */
data class Tokens(val accessToken: String, val refreshToken: String)

/** Outcome of a register/login call: either tokens (already persisted) or a user-facing message. */
sealed interface AuthResult {
    data class Success(val tokens: Tokens) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * The seam between the auth UI and the backend, mirroring [com.openlens.app.scan.ScanRepository].
 * Screens depend on this interface; [RemoteAuthRepository] is the Ktor-backed impl and
 * [FakeAuthRepository] returns canned data for previews. Implementations own the token lifecycle
 * (persist on success, clear on logout), so the UI just reacts to the [AuthResult].
 */
interface AuthRepository {
    suspend fun register(username: String, email: String, password: String): AuthResult
    suspend fun login(identifier: String, password: String): AuthResult

    /** True if the stored session still works (GET /auth/me; access auto-refreshes if expired). */
    suspend fun validateSession(): Boolean

    /** Revoke the refresh token server-side (best-effort) and clear local storage. */
    suspend fun logout()
}

/** Canned impl for @Preview / tests — every call "succeeds". */
class FakeAuthRepository : AuthRepository {
    override suspend fun register(username: String, email: String, password: String): AuthResult =
        AuthResult.Success(Tokens("fake-access", "fake-refresh"))

    override suspend fun login(identifier: String, password: String): AuthResult =
        AuthResult.Success(Tokens("fake-access", "fake-refresh"))

    override suspend fun validateSession(): Boolean = true

    override suspend fun logout() {}
}
