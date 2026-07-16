package com.openlens.app.scan

/**
 * Which backend the app talks to. Flip [ACTIVE] to switch every request between the local dev server
 * and the deployed one — it's the single source of truth for [RemoteScanRepository]'s base URL.
 *
 * - [Dev] targets localhost. On a physical device that's the dev machine, reachable via
 *   `adb reverse tcp:8000 tcp:8000`.
 * - [Prod] targets the deployed server directly (no bridge needed).
 *
 * This is a manual, compile-time switch: change [ACTIVE] and rebuild.
 */
enum class Env(val baseUrl: String) {
    Dev("http://localhost:8000"),
    Prod("http://89.169.102.74:8000");

    companion object {
        /** The active environment. Flip this one line to switch dev/prod. */
        val ACTIVE = Dev
    }
}
