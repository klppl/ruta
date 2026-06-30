package io.github.klppl.ruta.data.bookmarks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.ui.theme.accentForProfile
import io.github.klppl.ruta.util.Hosts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** User-added custom sites shown on the launcher alongside the built-in services. */
@Singleton
class BookmarkRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Serializable
    private data class StoredBookmark(val id: String, val name: String, val url: String, val host: String)

    private val key = stringPreferencesKey("bookmarks")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredBookmark.serializer())

    val bookmarks: Flow<List<AppService>> = dataStore.data.map { prefs ->
        decode(prefs[key]).map { it.toService() }
    }

    /** Adds a custom site. Returns false if the URL can't be parsed into a host. */
    suspend fun add(name: String, rawUrl: String): Boolean {
        val url = ensureScheme(rawUrl.trim())
        val host = Hosts.hostOf(url) ?: return false
        val displayName = name.trim().ifBlank { host.removePrefix("www.") }
        dataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            current.add(StoredBookmark(UUID.randomUUID().toString().take(8), displayName, url, host))
            prefs[key] = json.encodeToString(serializer, current)
        }
        return true
    }

    suspend fun remove(id: String) {
        val storedId = id.removePrefix("bookmark:")
        dataStore.edit { prefs ->
            val current = decode(prefs[key]).filterNot { it.id == storedId }
            prefs[key] = json.encodeToString(serializer, current)
        }
    }

    private fun StoredBookmark.toService() = AppService(
        id = "bookmark:$id",
        name = name,
        url = url,
        host = host,
        brandColor = accentForProfile(host),
        monogram = name.take(1).uppercase(),
        isCustom = true,
    )

    private fun ensureScheme(input: String): String =
        if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"

    private fun decode(raw: String?): List<StoredBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
