package com.ruta.blocking

/** Combined output of [AbpParser]. */
data class ParseResult(
    val network: NetworkRules,
    val cosmetic: CosmeticRules,
)
