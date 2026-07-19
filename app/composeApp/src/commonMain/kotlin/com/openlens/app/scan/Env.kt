package com.openlens.app.scan

/**
 * Which backend the app talks to. Flip [ACTIVE] to switch every request between the local dev server
 * and the deployed one — the single source of truth for the FastAPI base URL ([baseUrl]) and the
 * Kratos identity server ([kratosUrl]).
 *
 * - [Dev] targets localhost. On a physical device that's the dev machine, reachable via
 *   `adb reverse tcp:8000 tcp:8000` (FastAPI) **and** `adb reverse tcp:4433 tcp:4433` (Kratos).
 * - [Prod] targets the deployed servers directly (no bridge needed).
 *
 * This is a manual, compile-time switch: change [ACTIVE] and rebuild.
 */
enum class Env(val baseUrl: String, val kratosUrl: String) {
    Dev(baseUrl = "http://localhost:8000", kratosUrl = "http://localhost:4433"),
    Prod(baseUrl = "http://89.169.102.74:8000", kratosUrl = "http://89.169.102.74:4433");

    companion object {
        /** The active environment. Flip this one line to switch dev/prod. */
        val ACTIVE = Dev
    }
}
