package com.openlens.app.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the /auth endpoints. `@SerialName` maps snake_case JSON to Kotlin names.
 * Internal — shared by [RemoteAuthRepository] and the refresh interceptor in HttpClients.kt.
 */

@Serializable
internal data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
internal data class LoginRequest(
    val identifier: String,
    val password: String,
)

@Serializable
internal data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class TokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)
