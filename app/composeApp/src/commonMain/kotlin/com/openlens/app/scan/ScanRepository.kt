package com.openlens.app.scan

import kotlinx.coroutines.delay

/**
 * The seam between UI and identification. The real impl (Ktor → backend/model) swaps in without
 * touching the UI; [FakeScanRepository] returns canned data so the interactive box flow can be built
 * and demoed with no server running.
 */
interface ScanRepository {
    /**
     * Fast, free detection pass run right after capture: the objects to draw as tappable boxes over
     * the frozen frame. Empty when nothing is found — the caller then falls back to a whole-image
     * scan.
     */
    suspend fun detect(image: ByteArray): List<DetectedBox>

    /**
     * Analyze the image and return the single best answer. When [region] is given, the backend crops
     * to that object (plus surrounding context) and analyzes only the crop — the "focus on this box"
     * re-scan. When null, the whole image is analyzed.
     */
    suspend fun identify(image: ByteArray, model: ScanMode, region: NormRect? = null): ScanResult

    /** Current wallet balance for the signed-in user; used to seed the on-screen coin counter. */
    suspend fun balance(): Int
}

class FakeScanRepository : ScanRepository {
    override suspend fun balance(): Int = 100

    override suspend fun detect(image: ByteArray): List<DetectedBox> {
        delay(600) // simulate the detection round-trip so the boxes appear a beat after capture
        return listOf(
            DetectedBox("box-0", "bottle", 0.92f, NormRect(0.12f, 0.18f, 0.44f, 0.86f)),
            DetectedBox("box-1", "cup", 0.81f, NormRect(0.55f, 0.40f, 0.80f, 0.74f)),
            DetectedBox("box-2", "book", 0.63f, NormRect(0.30f, 0.06f, 0.94f, 0.34f)),
        )
    }

    override suspend fun identify(image: ByteArray, model: ScanMode, region: NormRect?): ScanResult {
        delay(1200) // simulate round-trip so the Analyzing state is visible
        val focus = if (region != null) "the selected object" else "the whole frame"
        return ScanResult(
            label = "invisible fart",
            detail = "canned read of $focus — u cant see it but its there fr fr. likely silent, " +
                "possibly deadly, the classic SBD variant. distinctive notes of yesterday's " +
                "burrito with a lingering base of regret. bystanders advised not to inhale.",
        )
    }
}
