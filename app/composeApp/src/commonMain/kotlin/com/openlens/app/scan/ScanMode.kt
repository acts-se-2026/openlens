package com.openlens.app.scan

/**
 * The quality/latency tier the user picks on the capture screen. [wire] is the exact string sent as
 * the `model` form field to /image_to_model; the backend owns the mapping from these keys to real
 * model ids (e.g. "deep" -> "gpt5.5"), so tiers can be retuned without an app release.
 *
 * [tokenCost] is what the scan spends from the user's wallet, and MUST mirror the server's
 * `MODEL_COSTS` (server/server.py): free = 0 (free stays free), fast = 1, deep = 3. It's shown as the
 * coin-cost cue above each tier in the mode strip; the backend remains the source of truth for the
 * actual charge, so if the two ever drift the app just displays a stale price — it can't overspend.
 *
 * Order is the left-to-right order in the mode strip. [Default] is what a fresh capture starts on.
 */
enum class ScanMode(val wire: String, val label: String, val tokenCost: Int) {
    Free("free", "Free", 0),
    Fast("fast", "Fast", 1),
    Deep("deep", "Deep", 3);

    companion object {
        val Default = Free
    }
}
