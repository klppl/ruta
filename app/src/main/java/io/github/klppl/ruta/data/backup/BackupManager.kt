package io.github.klppl.ruta.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual backup of the app's single preferences DataStore — settings, custom sites, dashboard
 * order, filter-list config, custom CSS, block stats — as a typed JSON document the user saves
 * and restores via the system file picker. It's a generic dump of all preference entries, so new
 * settings are covered automatically. WebView state (cookies/logins) can't be exported this way
 * and is deliberately excluded; app backup stays off (`allowBackup=false`).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    /** Writes the backup JSON to [uri]. Returns false on any failure. */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first().asMap()
            val entries = buildJsonObject {
                for ((key, value) in prefs) {
                    if (key.name in EXCLUDED) continue
                    val entry = when (value) {
                        is Boolean -> buildJsonObject { put("t", "bool"); put("v", value) }
                        is Int -> buildJsonObject { put("t", "int"); put("v", value) }
                        is Long -> buildJsonObject { put("t", "long"); put("v", value) }
                        is Float -> buildJsonObject { put("t", "float"); put("v", value) }
                        is Double -> buildJsonObject { put("t", "double"); put("v", value) }
                        is String -> buildJsonObject { put("t", "string"); put("v", value) }
                        is Set<*> -> buildJsonObject {
                            put("t", "set")
                            put("v", JsonArray(value.map { JsonPrimitive(it.toString()) }))
                        }
                        else -> null
                    } ?: continue
                    put(key.name, entry)
                }
            }
            val root = buildJsonObject {
                put("app", "ruta")
                put("version", 1)
                put("prefs", entries)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root).toByteArray())
            } ?: error("cannot open $uri")
            true
        }.getOrDefault(false)
    }

    /**
     * Merges a backup produced by [exportTo] into the DataStore. Existing keys are overwritten,
     * keys absent from the backup are left alone. Returns false if [uri] isn't a ruta backup.
     */
    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("cannot open $uri")
            val root = Json.parseToJsonElement(text).jsonObject
            require(root["app"]?.jsonPrimitive?.content == "ruta") { "not a ruta backup" }
            val prefs = root["prefs"]?.jsonObject ?: error("no prefs object")
            dataStore.edit { mutable ->
                for ((name, element) in prefs) {
                    if (name in EXCLUDED) continue
                    val obj = element.jsonObject
                    val type = obj["t"]?.jsonPrimitive?.content ?: continue
                    val value = obj["v"] ?: continue
                    when (type) {
                        "bool" -> mutable[booleanPreferencesKey(name)] = value.jsonPrimitive.boolean
                        "int" -> mutable[intPreferencesKey(name)] = value.jsonPrimitive.int
                        "long" -> mutable[longPreferencesKey(name)] = value.jsonPrimitive.long
                        "float" -> mutable[floatPreferencesKey(name)] = value.jsonPrimitive.float
                        "double" -> mutable[doublePreferencesKey(name)] = value.jsonPrimitive.double
                        "string" -> mutable[stringPreferencesKey(name)] = value.jsonPrimitive.content
                        "set" -> mutable[stringSetPreferencesKey(name)] =
                            value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private companion object {
        // The open-tab session is device state, not configuration — restoring another
        // device's tab list would be confusing.
        val EXCLUDED = setOf("tab_session")
        val json = Json { prettyPrint = true }
    }
}
