package com.openlens.app.scan

/**
 * The quality/latency tier the user picks on the capture screen. [wire] is the exact string sent as
 * the `model` form field to /image_to_model; the backend owns the mapping from these keys to real
 * model ids (e.g. "deep" -> "gpt5.5"), so tiers can be retuned without an app release.
 *
 * Order is the left-to-right order in the mode strip. [Default] is what a fresh capture starts on.
 */
enum class ScanMode(val wire: String, val label: String) {
    Free("free", "Free"),
    Fast("fast", "Fast"),
    Deep("deep", "Deep");

    companion object {
        val Default = Free
    }
}
