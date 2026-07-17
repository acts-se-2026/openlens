package com.openlens.app.auth

/** Outcome of a register/login call: success (token already persisted) or a user-facing message. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * The seam between the auth UI and Kratos, mirroring [com.openlens.app.scan.ScanRepository].
 * Screens depend on this interface; [RemoteAuthRepository] is the Kratos-backed impl and
 * [FakeAuthRepository] returns canned data for previews. Implementations own the token lifecycle
 * (persist the session token on success, clear on logout), so the UI just reacts to the [AuthResult].
 */
interface AuthRepository {
    suspend fun register(username: String, email: String, password: String): AuthResult
    suspend fun login(identifier: String, password: String): AuthResult

    /** True if the stored session token is still valid (confirmed via Kratos /sessions/whoami). */
    suspend fun validateSession(): Boolean

    /** Revoke the session server-side (best-effort) and clear local storage. */
    suspend fun logout()
}

/** Canned impl for @Preview / tests — every call "succeeds". */
class FakeAuthRepository : AuthRepository {
    override suspend fun register(username: String, email: String, password: String): AuthResult =
        AuthResult.Success

    override suspend fun login(identifier: String, password: String): AuthResult =
        AuthResult.Success

    override suspend fun validateSession(): Boolean = true

    override suspend fun logout() {}
}
