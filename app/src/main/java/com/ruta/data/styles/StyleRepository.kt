package com.ruta.data.styles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ruta.util.Hosts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** User-editable custom CSS: one global stylesheet plus optional per-host overrides. */
@Singleton
class StyleRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val globalKey = stringPreferencesKey("custom_css_global")
    private val perSiteKey = stringPreferencesKey("custom_css_per_site")
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    val globalCss: Flow<String> = dataStore.data.map { it[globalKey].orEmpty() }

    val perSiteCss: Flow<Map<String, String>> = dataStore.data.map { decode(it[perSiteKey]) }

    /** Resolves the CSS that applies to [url]: global plus any matching host override. */
    suspend fun cssFor(url: String?): String {
        val prefs = dataStore.data.first()
        val global = prefs[globalKey].orEmpty()
        val host = Hosts.hostOf(url) ?: return global
        val perSite = decode(prefs[perSiteKey])
        val siteCss = perSite.entries
            .firstOrNull { host == it.key || host.endsWith("." + it.key) }
            ?.value.orEmpty()
        return listOf(global, siteCss).filter { it.isNotBlank() }.joinToString("\n")
    }

    suspend fun setGlobalCss(css: String) = dataStore.edit { it[globalKey] = css }

    suspend fun setSiteCss(host: String, css: String) = dataStore.edit { prefs ->
        val current = decode(prefs[perSiteKey]).toMutableMap()
        if (css.isBlank()) current.remove(host) else current[host] = css
        prefs[perSiteKey] = json.encodeToString(mapSerializer, current)
    }

    private fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }
}
