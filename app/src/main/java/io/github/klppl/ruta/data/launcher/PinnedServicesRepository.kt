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
 * The dashboard's display order: built-in service ids and custom-bookmark ids (`bookmark:*`),
 * in the order the user arranged them. A built-in is pinned the first time it's opened, so the
 * launcher shows the sites you actually use rather than the full catalog; the rest stay one tap
 * away behind the "+" picker. Custom sites' data lives in
 * [io.github.klppl.ruta.data.bookmarks.BookmarkRepository]; only their placement is tracked here
 * (bookmarks predating the order list are appended at the end until the first reorder).
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

    /** Replaces the saved order wholesale — the commit of a drag-reorder. */
    suspend fun setOrder(ids: List<String>) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(serializer, ids) }
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
