package com.openlens.app.scan

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Real backend: POST the captured JPEG to the FastAPI server. Detection ([detect] → /detect) returns
 * the per-object boxes to draw; analysis ([identify] → /image_to_model) returns the {"Heading",
 * "Body"} answer, optionally cropped to a selected box via the `region` field.
 *
 * The target server is chosen by [Env.ACTIVE] (dev = localhost, prod = deployed IP). On a physical
 * device pointed at [Env.Dev], run `adb reverse tcp:8000 tcp:8000` so localhost reaches the dev
 * machine's server.
 */
class RemoteScanRepository(
    private val baseUrl: String = Env.ACTIVE.baseUrl,
    private val client: HttpClient = sharedHttpClient,
) : ScanRepository {

    @Serializable
    private data class ScanResponse(
        @SerialName("Heading") val heading: String,
        @SerialName("Body") val body: String,
        // Post-charge wallet balance; lets the UI update its counter without a separate call.
        @SerialName("Balance") val balance: Int? = null,
    )

    @Serializable
    private data class BalanceResponse(val balance: Int)

    // /detect payload: a list of objects, each with a label, confidence, and normalized corners.
    // Only top_left + bottom_right are needed for an axis-aligned box; ignoreUnknownKeys drops the
    // other two corners the server also sends.
    @Serializable
    private data class DetectResponse(val objects: List<DetectedObjectDto> = emptyList())

    @Serializable
    private data class DetectedObjectDto(
        val label: String = "",
        val confidence: Float = 0f,
        val corners: CornersDto? = null,
    )

    @Serializable
    private data class CornersDto(
        @SerialName("top_left") val topLeft: PointDto,
        @SerialName("bottom_right") val bottomRight: PointDto,
    )

    @Serializable
    private data class PointDto(val x: Float, val y: Float)

    override suspend fun detect(image: ByteArray): List<DetectedBox> {
        val response: HttpResponse = client.post("$baseUrl/detect") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = image,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"capture.jpg\"")
                            },
                        )
                    },
                ),
            )
        }
        val parsed: DetectResponse = response.body()
        // Index-based ids are stable within a single detection result, which is all the UI needs to
        // track selection and cache per-box answers for one captured frame.
        return parsed.objects.mapIndexedNotNull { index, obj ->
            val corners = obj.corners ?: return@mapIndexedNotNull null
            DetectedBox(
                id = "box-$index",
                label = obj.label,
                confidence = obj.confidence,
                rect = NormRect(
                    left = corners.topLeft.x,
                    top = corners.topLeft.y,
                    right = corners.bottomRight.x,
                    bottom = corners.bottomRight.y,
                ),
            )
        }
    }

    override suspend fun identify(image: ByteArray, model: ScanMode, region: NormRect?): ScanResult {
        val response: HttpResponse = client.post("$baseUrl/image_to_model") {
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
                        // Focus the analysis on one object: a normalized [x1,y1,x2,y2] JSON array the
                        // server crops to (plus padding). Omitted → the whole image is analyzed.
                        if (region != null) {
                            append(
                                key = "region",
                                value = "[${region.left},${region.top}," +
                                    "${region.right},${region.bottom}]",
                            )
                        }
                    },
                ),
            )
        }
        // Empty wallet for a paid tier: surface a typed error the UI can explain, instead of letting
        // .body() choke trying to parse the {"detail": ...} error payload as a ScanResponse.
        if (response.status == HttpStatusCode.PaymentRequired) throw OutOfTokensException()
        val parsed: ScanResponse = response.body()
        return ScanResult(label = parsed.heading, detail = parsed.body, balance = parsed.balance)
    }

    override suspend fun balance(): Int =
        client.get("$baseUrl/balance").body<BalanceResponse>().balance
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
