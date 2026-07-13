package com.openlens.app.scan

import kotlinx.coroutines.delay

/**
 * The seam between UI and identification. The real impl (Ktor → backend/model) swaps in later
 * without touching the UI; for now [FakeScanRepository] returns canned data.
 */
interface ScanRepository {
    suspend fun identify(image: ByteArray): ScanResult
}

class FakeScanRepository : ScanRepository {
    override suspend fun identify(image: ByteArray): ScanResult {
        delay(1200) // simulate round-trip so the Scanning state is visible
        return ScanResult(
            label = "invisible fart",
            detail = "u cant see it but its there fr fr. likely silent, possibly deadly — the " +
                "classic SBD variant. appears to be lingering near the point of origin with a " +
                "faint warmth signature and a texture best described as 'ominous'. no visible " +
                "text or logo, though a single tear rolling down a nearby witness's cheek is " +
                "noted. distinctive notes of yesterday's burrito, with a lingering base of " +
                "regret. bystanders are advised not to inhale and to blame the dog immediately.",
        )
    }
}
