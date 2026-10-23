package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

object ApiPaths {
    const val HEALTH = "/health"
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val database: String,
)
