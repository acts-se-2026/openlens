package com.openlens.app.auth

/** Outcome of a register/login call: success (token already persisted) or a user-facing message. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Outcome of *starting* a Google sign-in: either a URL the app must open in a browser to continue,
 * or an error. The session isn't established yet — that happens later via [AuthRepository.completeGoogleLogin]
 * once the browser redirects back with a code.
 */
sealed interface GoogleLoginStart {
    data class OpenBrowser(val url: String) : GoogleLoginStart
    data class Error(val message: String) : GoogleLoginStart
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

    /**
     * Step 1 of native Google sign-in: init an OIDC flow and return the Google URL to open in a
     * browser. The impl stashes the flow's exchange "init code" for [completeGoogleLogin].
     */
    suspend fun beginGoogleLogin(): GoogleLoginStart

    /**
     * Step 2: after the browser redirects to `openlens://oidc-callback?code=…`, swap that [returnCode]
     * (plus the stashed init code) for a session token via Kratos's token-exchange endpoint.
     */
    suspend fun completeGoogleLogin(returnCode: String): AuthResult

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

    override suspend fun beginGoogleLogin(): GoogleLoginStart =
        GoogleLoginStart.OpenBrowser("https://example.com/fake-oauth")

    override suspend fun completeGoogleLogin(returnCode: String): AuthResult = AuthResult.Success

    override suspend fun validateSession(): Boolean = true

    override suspend fun logout() {}
}
