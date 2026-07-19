package com.openlens.app.scan

/**
 * A single "what is this?" answer. Single best result for MVP.
 *
 * [balance] is the user's wallet balance *after* this scan was charged, as reported by the backend
 * in the same response — so the on-screen counter updates without a separate /balance call. It's
 * null when the balance isn't known for this result (e.g. an error card, or the fake repository).
 */
data class ScanResult(
    val label: String,
    val detail: String,
    val balance: Int? = null,
)
