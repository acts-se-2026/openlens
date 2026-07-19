package com.openlens.app.scan

/**
 * Thrown by [ScanRepository.identify] when the backend rejects a scan with HTTP 402 because the
 * user's token wallet is empty for the chosen tier. Free scans never raise this (they cost nothing);
 * only Fast/Deep can. Callers turn it into a friendly "out of tokens" message instead of the generic
 * network-error card.
 */
class OutOfTokensException : Exception("Not enough tokens for this scan tier")
