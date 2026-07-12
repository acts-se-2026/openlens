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
            label = "Coffee mug",
            detail = "Ceramic, ~350 ml. (Placeholder result — the model isn't wired up yet.)",
        )
    }
}
