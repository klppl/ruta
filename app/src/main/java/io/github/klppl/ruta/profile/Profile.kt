package io.github.klppl.ruta.profile

import androidx.compose.ui.graphics.Color
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.ui.theme.accentForProfile

/**
 * A multi-account identity. [id] is the stable key carried by tabs; the empty id is the
 * default profile (singleton CookieManager). Ids of the form "site:<domain>" are created
 * automatically by the per-site isolation mode.
 */
data class Profile(
    val id: String,
    val name: String,
) {
    val isDefault: Boolean get() = id == Tab.DEFAULT_PROFILE_ID
    val accent: Color get() = accentForProfile(id)

    companion object {
        val Default = Profile(Tab.DEFAULT_PROFILE_ID, "Default")

        fun siteId(host: String): String = "site:$host"
    }
}
