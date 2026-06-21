package com.ruta.blocking

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ListMeta(
    val etag: String? = null,
    val lastModified: String? = null,
    val fetchedAtMillis: Long = 0,
    val expiresHours: Int = BlocklistRepository.DEFAULT_EXPIRES_HOURS,
) {
    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        val ttl = expiresHours.coerceIn(1, 24 * 14) * 60L * 60L * 1000L
        return now - fetchedAtMillis > ttl
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
