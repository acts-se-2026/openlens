package com.openlens.app.auth

import com.openlens.app.scan.Env
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * [AuthRepository] backed by Ory Kratos's self-service flows (see planning/kratos-integration.md).
 *
 * Kratos is a two-step protocol: first init a flow (`GET …/api`) to get its `ui.action` URL, then
 * POST the method payload there. A successful login/registration returns a session token, which we
 * persist to [tokenStorage]; FastAPI later validates that same token with Kratos on each scan.
 *
 * The client doesn't set `expectSuccess`, so non-2xx doesn't throw — we inspect `response.status`
 * and, on a 400, pull Kratos's own human-readable messages out of the re-rendered flow.
 */
class RemoteAuthRepository(
    private val tokenStorage: TokenStorage,
    private val publicClient: HttpClient,
    private val kratosUrl: String = Env.ACTIVE.kratosUrl,
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Native OIDC spans an app→browser→app round-trip. beginGoogleLogin() stashes the flow's
    // one-time "init code" here; completeGoogleLogin() pairs it with the code the browser returns.
    // In-memory is fine for the sandbox (a process death mid-browser just means restarting sign-in).
    private var googleInitCode: String? = null

    override suspend fun register(username: String, email: String, password: String): AuthResult =
        submitFlow(flowType = "registration") { action ->
            publicClient.post(action) {
                contentType(ContentType.Application.Json)
                setBody(
                    PasswordRegistration(
                        traits = RegistrationTraits(email = email.trim(), username = username.trim()),
                        password = password,
                    ),
                )
            }
        }

    override suspend fun login(identifier: String, password: String): AuthResult =
        submitFlow(flowType = "login") { action ->
            publicClient.post(action) {
                contentType(ContentType.Application.Json)
                setBody(PasswordLogin(identifier = identifier.trim(), password = password))
            }
        }

    override suspend fun beginGoogleLogin(): GoogleLoginStart {
        return try {
            // 1) Init an OIDC login flow asking Kratos for a native token-exchange code, and telling
            //    it to redirect back to our app deep link when the browser dance finishes.
            val flow: KratosFlow = publicClient.get("$kratosUrl/self-service/login/api") {
                parameter("return_session_token_exchange_code", "true")
                parameter("return_to", RETURN_TO)
            }.body()
            googleInitCode = flow.sessionTokenExchangeCode
                ?: return GoogleLoginStart.Error("Google sign-in isn't available right now.")

            // 2) Submit the oidc method. Kratos replies 422 + the Google URL to open in a browser.
            val response = publicClient.post(flow.ui.action) {
                contentType(ContentType.Application.Json)
                setBody(OidcSubmit(provider = "google"))
            }
            val redirect: BrowserLocationChange = response.body()
            GoogleLoginStart.OpenBrowser(redirect.redirectBrowserTo)
        } catch (_: Exception) {
            GoogleLoginStart.Error("Couldn't start Google sign-in. Check your connection and try again.")
        }
    }

    override suspend fun completeGoogleLogin(returnCode: String): AuthResult {
        val initCode = googleInitCode
            ?: return AuthResult.Error("Google sign-in expired. Please try again.")
        return try {
            // Swap the init code (ours) + the return code (from the deep link) for a session token.
            val response = publicClient.get("$kratosUrl/sessions/token-exchange") {
                parameter("init_code", initCode)
                parameter("return_to_code", returnCode)
            }
            if (response.status.isSuccess()) {
                val success: KratosSuccess = response.body()
                tokenStorage.save(success.sessionToken)
                googleInitCode = null
                AuthResult.Success
            } else {
                AuthResult.Error("Google sign-in didn't complete. Please try again.")
            }
        } catch (_: Exception) {
            AuthResult.Error("Couldn't finish Google sign-in. Check your connection and try again.")
        }
    }

    override suspend fun validateSession(): Boolean {
        val token = tokenStorage.sessionToken() ?: return false
        return try {
            val response = publicClient.get("$kratosUrl/sessions/whoami") {
                header("X-Session-Token", token)
            }
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            // A network error isn't proof the session is invalid, but we can't confirm it either.
            false
        }
    }

    override suspend fun logout() {
        val token = tokenStorage.sessionToken()
        if (token != null) {
            try {
                publicClient.post("$kratosUrl/self-service/logout/api") {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutBody(token))
                }
            } catch (_: Exception) {
                // Best-effort server-side revocation; we clear locally regardless.
            }
        }
        tokenStorage.clear()
    }

    /**
     * Run one self-service flow: init it to get the action URL, [submit] the method payload, then
     * map the result. On success the session token is persisted before returning.
     */
    private suspend fun submitFlow(
        flowType: String,
        submit: suspend (action: String) -> HttpResponse,
    ): AuthResult {
        val response = try {
            val flow: KratosFlow = publicClient.get("$kratosUrl/self-service/$flowType/api").body()
            submit(flow.ui.action)
        } catch (_: Exception) {
            return AuthResult.Error("Couldn't reach the server. Check your connection and try again.")
        }
        if (response.status.isSuccess()) {
            val success: KratosSuccess = response.body()
            tokenStorage.save(success.sessionToken)
            return AuthResult.Success
        }
        return AuthResult.Error(errorMessage(response))
    }

    private companion object {
        /** Deep link Kratos redirects to after OIDC; must match the Android manifest + allowed_return_urls. */
        const val RETURN_TO = "openlens://oidc-callback"
    }

    /** Pull Kratos's own messages out of a failed (re-rendered) flow, falling back to a generic line. */
    private suspend fun errorMessage(response: HttpResponse): String {
        val fallback = "Something went wrong (${response.status.value}). Try again."
        return try {
            val flow = json.decodeFromString(KratosFlow.serializer(), response.bodyAsText())
            val messages = flow.ui.messages.map { it.text } +
                flow.ui.nodes.flatMap { node -> node.messages.map { it.text } }
            messages.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
