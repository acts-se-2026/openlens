package com.openlens.app.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for Kratos's self-service flows. We model only the fields we read — the client's
 * `ignoreUnknownKeys = true` drops the rest of Kratos's (large) flow JSON.
 *
 * A "flow" is Kratos's stateful form object: init one (GET), read its [KratosUi.action] URL, then
 * POST the method payload there. On success Kratos returns a [KratosSuccess] with a session token;
 * on a bad submit it re-renders the flow (HTTP 400) with human-readable [KratosMessage]s.
 */

@Serializable
internal data class KratosFlow(
    val id: String,
    val ui: KratosUi,
)

@Serializable
internal data class KratosUi(
    /** Absolute URL to POST this flow's payload to (already carries `?flow=<id>`). */
    val action: String,
    /** Flow-level messages (e.g. "The provided credentials are invalid"). */
    val messages: List<KratosMessage> = emptyList(),
    /** Per-field nodes; each may carry its own validation messages. */
    val nodes: List<KratosNode> = emptyList(),
)

@Serializable
internal data class KratosNode(
    val messages: List<KratosMessage> = emptyList(),
)

@Serializable
internal data class KratosMessage(
    val text: String,
    val type: String? = null,
)

/** Returned by a successful login/registration submit (registration carries the `session` hook). */
@Serializable
internal data class KratosSuccess(
    @SerialName("session_token") val sessionToken: String,
)

/** Payload for a password-method registration submit. */
@Serializable
internal data class PasswordRegistration(
    val traits: RegistrationTraits,
    val password: String,
    val method: String = "password",
)

@Serializable
internal data class RegistrationTraits(
    val email: String,
    val name: String,
)

/** Payload for a password-method login submit. */
@Serializable
internal data class PasswordLogin(
    val identifier: String,
    val password: String,
    val method: String = "password",
)

/** Payload for API-flow logout. */
@Serializable
internal data class LogoutBody(
    @SerialName("session_token") val sessionToken: String,
)
