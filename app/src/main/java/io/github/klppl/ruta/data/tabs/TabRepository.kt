package io.github.klppl.ruta.data.tabs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klppl.ruta.model.Tab
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Persists lightweight tab state across process death (the WebViews themselves are not saved). */
@Singleton
class TabRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Serializable
    data class PersistedSession(val tabs: List<PersistedTab>, val activeId: String?)

    @Serializable
    data class PersistedTab(
        val id: String,
        val url: String,
        val title: String,
        val profileId: String,
        val desktopMode: Boolean,
    )

    private val key = stringPreferencesKey("tab_session")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = PersistedSession.serializer()

    suspend fun load(): PersistedSession? {
        val raw = dataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    suspend fun save(tabs: List<Tab>, activeId: String?) {
        val session = PersistedSession(
            tabs = tabs.filterNot { it.isNewTab }.map {
                PersistedTab(it.id, it.url, it.title, it.profileId, it.desktopMode)
            },
            activeId = activeId,
        )
        val raw = json.encodeToString(serializer, session)
        dataStore.edit { it[key] = raw }
    }

    @Suppress("unused")
    private val listSerializer = ListSerializer(PersistedTab.serializer())
}
