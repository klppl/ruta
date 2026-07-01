package io.github.klppl.ruta.model

import android.net.Uri

/** A selectable address-bar search engine. [queryUrl] is the prefix the query is appended to. */
data class SearchEngine(val id: String, val name: String, val queryUrl: String)

object SearchEngines {
    val all = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
        SearchEngine("brave", "Brave", "https://search.brave.com/search?q="),
        SearchEngine("google", "Google", "https://www.google.com/search?q="),
    )

    fun byId(id: String?): SearchEngine = all.firstOrNull { it.id == id } ?: all.first()

    fun searchUrl(engineId: String?, query: String): String =
        byId(engineId).queryUrl + Uri.encode(query)
}
