package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

object ApiPaths {
    const val HEALTH = "/health"
    const val DIAGNOSTICS = "/diagnostics"
}

object ApiHeaders {
    /** The app's versionCode, used by the server to check compatibility. */
    const val APP_VERSION_CODE = "X-AMBiFy-App-Version-Code"
    const val APP_VERSION_NAME = "X-AMBiFy-App-Version-Name"
    /** Id of the community the request is about; without it the server uses the person's first community. */
    const val COMMUNITY = "X-AMBiFy-Community"
}

object HealthStatus {
    const val OK = "ok"
    /** The server answers, but at least one diagnostic check reports a problem. */
    const val DEGRADED = "degraded"
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val database: String,
)

@Serializable
enum class CheckStatus { OK, WARNING, ERROR, NOT_AVAILABLE }

@Serializable
data class DiagnosticResult(
    val id: String,
    val name: String,
    val status: CheckStatus,
    val detail: String,
)

@Serializable
data class DiagnosticsResponse(
    val serverVersion: String,
    val checks: List<DiagnosticResult>,
)
