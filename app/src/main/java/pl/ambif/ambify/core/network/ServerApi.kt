package pl.ambif.ambify.core.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.ApiPaths
import pl.ambif.ambify.shared.DiagnosticsResponse
import pl.ambif.ambify.shared.HealthResponse

/** A server answer together with how long the request took. */
data class Timed<T>(val value: T, val millis: Long)

/**
 * Access to the AMBiFy server at the fixed address from the build.
 * Every request tells the server the app version, so it can report an outdated app.
 */
class ServerApi(private val baseUrl: String = BuildConfig.SERVER_URL) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun health(): Timed<HealthResponse> = get(ApiPaths.HEALTH)

    suspend fun diagnostics(): Timed<DiagnosticsResponse> = get(ApiPaths.DIAGNOSTICS)

    private suspend inline fun <reified T> get(path: String): Timed<T> = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty(ApiHeaders.APP_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
        connection.setRequestProperty(ApiHeaders.APP_VERSION_NAME, BuildConfig.VERSION_NAME)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Timed(json.decodeFromString<T>(body), (System.nanoTime() - started) / 1_000_000)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 4000
    }
}
