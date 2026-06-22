package io.github.klppl.ruta.profile

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.klppl.ruta.model.Tab
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns multi-account isolation. When [WebViewFeature.MULTI_PROFILE] is available (API 33+)
 * each non-default profile gets its own [ProfileStore] profile with isolated cookies and
 * storage; otherwise everything falls back to the single default profile.
 */
@Singleton
class ProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    val multiProfileSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private val profilesKey = stringPreferencesKey("profiles")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredProfile.serializer())

    @Serializable
    private data class StoredProfile(val id: String, val name: String)

    /** All profiles the user explicitly created (excludes the default and auto site profiles). */
    val userProfiles: Flow<List<Profile>> = dataStore.data.map { prefs ->
        decode(prefs[profilesKey]).map { Profile(it.id, it.name) }
    }

    suspend fun createProfile(name: String): Profile {
        val id = "p:" + java.util.UUID.randomUUID().toString().take(8)
        val profile = Profile(id, name.ifBlank { "Account" })
        dataStore.edit { prefs ->
            val current = decode(prefs[profilesKey]).toMutableList()
            current.add(StoredProfile(profile.id, profile.name))
            prefs[profilesKey] = json.encodeToString(serializer, current)
        }
        if (multiProfileSupported) {
            runCatching { ProfileStore.getInstance().getOrCreateProfile(webViewProfileName(id)) }
        }
        return profile
    }

    suspend fun deleteProfile(id: String) {
        if (id == Tab.DEFAULT_PROFILE_ID) return
        wipe(id)
        dataStore.edit { prefs ->
            val current = decode(prefs[profilesKey]).filterNot { it.id == id }
            prefs[profilesKey] = json.encodeToString(serializer, current)
        }
        if (multiProfileSupported) {
            // A profile in use by a live WebView can't be deleted; the WebViews are torn down
            // by the caller first. Swallow failures so the metadata removal still sticks.
            runCatching { ProfileStore.getInstance().deleteProfile(webViewProfileName(id)) }
        }
    }

    /** Binds [webView] to the profile for [profileId]; must be called before loading a URL. */
    fun applyProfile(webView: WebView, profileId: String) {
        if (!multiProfileSupported || profileId == Tab.DEFAULT_PROFILE_ID) return
        val name = webViewProfileName(profileId)
        runCatching {
            ProfileStore.getInstance().getOrCreateProfile(name)
            WebViewCompat.setProfile(webView, name)
        }
    }

    /** Wipes cookies + web storage for the given profile. */
    fun wipe(profileId: String) {
        if (multiProfileSupported && profileId != Tab.DEFAULT_PROFILE_ID) {
            val store = ProfileStore.getInstance()
            val profile = runCatching {
                store.getProfile(webViewProfileName(profileId))
            }.getOrNull() ?: return
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    private fun webViewProfileName(profileId: String): String =
        "ruta_" + profileId.replace(Regex("[^A-Za-z0-9]"), "_")

    private fun decode(raw: String?): List<StoredProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
