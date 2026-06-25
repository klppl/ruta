package io.github.klppl.ruta.blocking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klppl.ruta.util.Hosts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single ABP/hostfile filter list the user can enable/disable. When [asset] is set the list
 * ships inside the app (read from `assets/`) instead of being downloaded from [url].
 */
@Serializable
data class FilterListEntry(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val asset: String? = null,
)

/**
 * Persists the set of filter lists (built-in defaults plus user-added custom lists) and their
 * enabled state. [BlocklistRepository] consumes this to decide what to download and parse.
 */
@Singleton
class FilterListRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("filter_lists")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FilterListEntry.serializer())

    val lists: Flow<List<FilterListEntry>> = dataStore.data.map { prefs ->
        val stored = decode(prefs[key])
        if (stored.isEmpty()) {
            DEFAULTS
        } else {
            // Ensure built-in defaults are always present even after an app update adds new ones.
            val ids = stored.mapTo(HashSet()) { it.id }
            stored + DEFAULTS.filter { it.id !in ids }
        }
    }

    suspend fun add(name: String, rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (Hosts.hostOf(url) == null) return false
        val entry = FilterListEntry(
            id = "custom:" + UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { Hosts.hostOf(url) ?: "Custom list" },
            url = url,
            enabled = true,
            builtIn = false,
        )
        mutate { it + entry }
        return true
    }

    suspend fun remove(id: String) = mutate { list -> list.filterNot { it.id == id && !it.builtIn } }

    suspend fun setEnabled(id: String, enabled: Boolean) =
        mutate { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    private suspend fun mutate(transform: (List<FilterListEntry>) -> List<FilterListEntry>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key]).ifEmpty { DEFAULTS }
            prefs[key] = json.encodeToString(serializer, transform(current))
        }
    }

    private fun decode(raw: String?): List<FilterListEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        val DEFAULTS = listOf(
            FilterListEntry(
                id = "ruta",
                name = "ruta filters",
                url = "",
                enabled = true,
                builtIn = true,
                asset = "filters/ruta.txt",
            ),
            FilterListEntry("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", true, true),
            FilterListEntry("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true, true),
        )
    }
}
