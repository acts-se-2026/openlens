package com.openlens.app.scan

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
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

    // DuckDuckGo image-search JSON (the i.js endpoint). Only the fields we render are modelled.
    @Serializable
    private data class DdgImageResponse(val results: List<DdgImageResult> = emptyList())

    @Serializable
    private data class DdgImageResult(
        val title: String = "",
        val image: String = "",
        val thumbnail: String = "",
        val url: String = "",
    )

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

    // Client-side image search, keyed on the answer's heading (which the scan already produced), so we
    // skip the backend's slow /related path (a vision call just to invent a query + per-page scraping).
    // Uses DuckDuckGo's public image endpoint over the no-auth [imageFetchClient]; returns empty on any
    // failure so the UI just shows "none found" instead of hanging. The endpoint is undocumented and
    // may need occasional maintenance if DuckDuckGo changes it.
    override suspend fun searchImages(query: String): List<RelatedImage> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        return runCatching {
            val encoded = trimmed.encodeURLParameter()
            val vqd = fetchVqd(encoded) ?: return emptyList()

            val body = imageFetchClient
                .get("https://duckduckgo.com/i.js?l=us-en&o=json&q=$encoded&vqd=$vqd&f=,,,&p=1") {
                    header(HttpHeaders.UserAgent, DDG_USER_AGENT)
                    header(HttpHeaders.Referrer, "https://duckduckgo.com/")
                }
                .bodyAsText()

            // A rate-limit / error page is HTML, not JSON — bail rather than throw on the parse.
            if (!body.trimStart().startsWith("{")) return emptyList()

            ddgJson.decodeFromString<DdgImageResponse>(body).results
                .asSequence()
                .mapNotNull { result ->
                    // Prefer the (already-sized, fast-loading) thumbnail; fall back to the full image.
                    val imageUrl = result.thumbnail.ifBlank { result.image }
                    if (imageUrl.isBlank()) null
                    else RelatedImage(
                        title = result.title,
                        imageUrl = imageUrl,
                        sourceUrl = result.url.ifBlank { imageUrl },
                    )
                }
                .take(MAX_RELATED_IMAGES)
                .toList()
        }.getOrDefault(emptyList())
    }

    /** First half of the DuckDuckGo image search: scrape the one-time `vqd` token the i.js call needs. */
    private suspend fun fetchVqd(encodedQuery: String): String? {
        val html = imageFetchClient
            .get("https://duckduckgo.com/?q=$encodedQuery&iax=images&ia=images") {
                header(HttpHeaders.UserAgent, DDG_USER_AGENT)
            }
            .bodyAsText()

        return VQD_QUOTED.find(html)?.groupValues?.get(1)
            ?: VQD_BARE.find(html)?.groupValues?.get(1)
    }

    // Fetched over [imageFetchClient] (no auth header) — never over [client], which would leak the
    // OpenLens bearer to the third-party host serving the image.
    override suspend fun loadImageBytes(url: String): ByteArray? = runCatching {
        val response = imageFetchClient.get(url)
        if (response.status.isSuccess()) response.body<ByteArray>() else null
    }.getOrNull()
}

/**
 * A separate, auth-free client for the similar-images feature: both the DuckDuckGo image search and
 * pulling the resulting image bytes off arbitrary third-party hosts. It deliberately does NOT install
 * the bearer-attaching [defaultRequest] the scan client uses — sending our Kratos session token to a
 * random website would leak it. Process-lived (owns an engine) for the same reason as
 * [sharedHttpClient]. No content negotiation: we read raw bytes / text and parse search JSON by hand.
 */
private val imageFetchClient: HttpClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }
}

private const val MAX_RELATED_IMAGES = 8

// A desktop browser UA — DuckDuckGo serves the token page + i.js JSON we parse to a browser client.
private const val DDG_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Safari/537.36"

// The vqd token appears either quoted (vqd="4-123…") or bare in a URL (vqd=4-123…&) depending on the
// page variant DuckDuckGo returns; try both.
private val VQD_QUOTED = Regex("""vqd=["']([^"']+)["']""")
private val VQD_BARE = Regex("""vqd=([-\d.]+)&""")

private val ddgJson = Json { ignoreUnknownKeys = true }

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
