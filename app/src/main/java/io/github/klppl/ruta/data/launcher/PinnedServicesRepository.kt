package io.github.klppl.ruta.data.launcher

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The built-in services the user has chosen to keep on their dashboard, in display order. A
 * service is pinned the first time it's opened, so the launcher shows the sites you actually use
 * rather than the full catalog; the rest stay one tap away behind the "+" picker. Custom sites
 * live in [io.github.klppl.ruta.data.bookmarks.BookmarkRepository]; this tracks only built-ins.
 */
@Singleton
class PinnedServicesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("pinned_services")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    val pinnedIds: Flow<List<String>> = dataStore.data.map { decode(it[key]) }

    /** Appends [id] to the end if not already pinned (keeps existing order stable). */
    suspend fun pin(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            if (id !in current) prefs[key] = json.encodeToString(serializer, current + id)
        }
    }

    suspend fun unpin(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            if (id in current) prefs[key] = json.encodeToString(serializer, current - id)
        }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
