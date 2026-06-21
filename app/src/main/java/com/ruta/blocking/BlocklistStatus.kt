package com.ruta.blocking

data class BlocklistStatus(
    val ready: Boolean = false,
    val refreshing: Boolean = false,
    val networkRules: Int = 0,
    val cosmeticReady: Boolean = false,
    val lastUpdatedMillis: Long = 0,
    val error: String? = null,
)
