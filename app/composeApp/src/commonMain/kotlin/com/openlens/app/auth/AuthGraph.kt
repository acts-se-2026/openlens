package com.openlens.app.auth

import com.openlens.app.scan.RemoteScanRepository
import com.openlens.app.scan.ScanRepository
import io.ktor.client.HttpClient

/**
 * Process-level object graph for auth + networking. The [HttpClient]s own engine threads and a
 * connection pool, so they must live for the whole process — building them per composition would
 * leak an engine on every Activity recreation (rotation, dark-mode, locale change). This mirrors the
 * old `sharedHttpClient` lazy singleton in RemoteScanRepository.
 *
 * Manual DI (the app has no DI framework): [ensureInitialized] wires the platform-backed
 * [TokenStorage] once from `App()`, then the lazy singletons build on first use.
 */
object AuthGraph {
    private var storage: TokenStorage? = null

    private val publicClient: HttpClient by lazy { createPublicClient() }
    private val authedClient: HttpClient by lazy { createAuthedClient(requireStorage(), publicClient) }

    val authRepository: AuthRepository by lazy {
        RemoteAuthRepository(requireStorage(), publicClient, authedClient)
    }
    val scanRepository: ScanRepository by lazy {
        RemoteScanRepository(client = authedClient)
    }

    /** Wire the platform token store exactly once; safe to call on every (re)composition. */
    fun ensureInitialized(tokenStorage: TokenStorage) {
        if (storage == null) storage = tokenStorage
    }

    private fun requireStorage(): TokenStorage =
        storage ?: error("AuthGraph.ensureInitialized() must run before the graph is used")
}
