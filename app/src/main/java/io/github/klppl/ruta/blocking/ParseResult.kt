package io.github.klppl.ruta.blocking

/** Combined output of [AbpParser]. */
data class ParseResult(
    val network: NetworkRules,
    val cosmetic: CosmeticRules,
)
