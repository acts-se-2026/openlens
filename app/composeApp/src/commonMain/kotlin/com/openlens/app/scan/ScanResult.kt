package com.openlens.app.scan

/** A single "what is this?" answer. Single best result for MVP. */
data class ScanResult(
    val label: String,
    val detail: String,
)
