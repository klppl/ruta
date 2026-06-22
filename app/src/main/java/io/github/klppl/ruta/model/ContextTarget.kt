package io.github.klppl.ruta.model

/** A long-press target captured from the page (link/image/video under the finger). */
data class ContextTarget(
    val tabId: String,
    val link: String? = null,
    val image: String? = null,
    val video: String? = null,
    val text: String? = null,
) {
    val isEmpty: Boolean get() = link == null && image == null && video == null
}
