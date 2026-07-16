package com.openlens.app.scan

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real backend: POST the captured JPEG to the FastAPI server's /image_to_model endpoint and map
 * its {"Heading", "Body"} response onto [ScanResult].
 *
 * On a physical device over USB, run `adb reverse tcp:8000 tcp:8000` so localhost points at the
 * dev machine's server.
 */
class RemoteScanRepository(
    private val baseUrl: String = "http://localhost:8000",
    private val client: HttpClient = sharedHttpClient,
) : ScanRepository {

    @Serializable
    private data class ScanResponse(
        @SerialName("Heading") val heading: String,
        @SerialName("Body") val body: String,
    )

    override suspend fun identify(image: ByteArray, model: ScanMode): ScanResult {
        val response: ScanResponse = client.post("$baseUrl/image_to_model") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // Field name "file" + a filename so FastAPI treats it as an UploadFile.
                        append(
                            key = "file",
                            value = image,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"capture.jpg\"")
                            },
                        )
                        // Selected tier ("free"/"fast"/"deep"); backend maps it to a model id.
                        append(key = "model", value = model.wire)
                    },
                ),
            )
        }.body()
        return ScanResult(label = response.heading, detail = response.body)
    }
}

/**
 * One client for the whole app process. An [HttpClient] owns a connection pool and engine threads,
 * so creating one per composition would leak an engine on every Activity recreation (e.g. rotation).
 * Sharing a single lazy instance means it's built once and lives until the process dies — nothing to
 * close, nothing to leak.
 */
private val sharedHttpClient: HttpClient by lazy { defaultHttpClient() }

private fun defaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        // The model round-trip is a few seconds; give it generous headroom. The socket timeout
        // must be raised along with the request timeout — otherwise the engine default (~10s of
        // read inactivity on OkHttp) aborts slow scans long before the request timeout matters.
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
}
