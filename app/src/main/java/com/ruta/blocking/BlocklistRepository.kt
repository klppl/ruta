package com.ruta.blocking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ruta.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads, caches and parses the EasyList / EasyPrivacy filter lists, then publishes the
 * resulting [RequestBlocker] rules and [CosmeticRules]. Lists are data assets fetched at
 * runtime (never bundled); cached to the app files dir with conditional requests and
 * refreshed periodically via [BlocklistRefreshWorker].
 */
@Singleton
class BlocklistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val requestBlocker: RequestBlocker,
    settingsRepository: SettingsRepository,
) {
    data class ListSource(val name: String, val url: String)

    private val sources = listOf(
        ListSource("easylist", "https://easylist.to/easylist/easylist.txt"),
        ListSource("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val initialized = AtomicBoolean(false)
    private val cacheDir: File by lazy { File(context.filesDir, "blocklists").apply { mkdirs() } }

    private val _cosmetic = MutableStateFlow(CosmeticRules.EMPTY)
    val cosmetic: StateFlow<CosmeticRules> = _cosmetic.asStateFlow()

    private val _status = MutableStateFlow(BlocklistStatus())
    val status: StateFlow<BlocklistStatus> = _status.asStateFlow()

    init {
        // Keep network blocking in sync with the user's setting.
        scope.launch {
            settingsRepository.settings
                .map { it.adBlockEnabled }
                .distinctUntilChanged()
                .collect { requestBlocker.enabled = it }
        }
    }

    /** Called once from Application.onCreate. Loads cache immediately, then refreshes. */
    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch {
            loadFromCache()
            refresh(force = false)
        }
        schedulePeriodicRefresh(context)
    }

    private suspend fun loadFromCache() {
        val cached = sources.mapNotNull { src ->
            val f = listFile(src.name)
            if (f.exists() && f.length() > 0) f.readText() else null
        }
        if (cached.isEmpty()) return
        applyParsed(cached)
    }

    suspend fun refresh(force: Boolean): Boolean = refreshMutex.withLock {
        _status.value = _status.value.copy(refreshing = true, error = null)
        var changed = false
        var error: String? = null
        try {
            for (src in sources) {
                val meta = readMeta(src.name)
                if (!force && meta != null && !meta.isStale() && listFile(src.name).exists()) continue
                if (downloadOne(src)) changed = true
            }
        } catch (t: Throwable) {
            error = t.message ?: "refresh failed"
        }

        if (changed) {
            val texts = sources.mapNotNull { src ->
                val f = listFile(src.name)
                if (f.exists()) f.readText() else null
            }
            applyParsed(texts)
        }
        _status.value = _status.value.copy(
            refreshing = false,
            error = error,
            lastUpdatedMillis = if (changed) nowOrPrevious() else _status.value.lastUpdatedMillis,
        )
        error == null
    }

    /** Returns true if the cached content was replaced (200), false on 304/no-op. */
    private fun downloadOne(src: ListSource): Boolean {
        val prev = readMeta(src.name)
        val builder = Request.Builder().url(src.url).header("Accept", "text/plain")
        prev?.etag?.let { builder.header("If-None-Match", it) }
        prev?.lastModified?.let { builder.header("If-Modified-Since", it) }

        httpClient.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 304) return false
            if (!resp.isSuccessful) error("HTTP ${resp.code} for ${src.url}")
            val body = resp.body?.string() ?: error("empty body for ${src.url}")
            listFile(src.name).writeText(body)
            writeMeta(
                src.name,
                ListMeta(
                    etag = resp.header("ETag"),
                    lastModified = resp.header("Last-Modified"),
                    fetchedAtMillis = System.currentTimeMillis(),
                    expiresHours = parseExpiresHours(body),
                ),
            )
            return true
        }
    }

    private suspend fun applyParsed(texts: List<String>) = withContext(Dispatchers.Default) {
        val acc = AbpParser.Accumulator()
        for (text in texts) {
            AbpParser.parseInto(text, acc)
        }
        val result = acc.build()
        requestBlocker.update(result.network)
        _cosmetic.value = result.cosmetic
        _status.value = _status.value.copy(
            ready = true,
            networkRules = result.network.blocked.size,
            cosmeticReady = !result.cosmetic.isEmpty,
        )
    }

    private fun nowOrPrevious(): Long = System.currentTimeMillis()

    private fun listFile(name: String) = File(cacheDir, "$name.txt")
    private fun metaFile(name: String) = File(cacheDir, "$name.meta.json")

    private fun readMeta(name: String): ListMeta? {
        val f = metaFile(name)
        if (!f.exists()) return null
        return runCatching { ListMeta.json.decodeFromString(ListMeta.serializer(), f.readText()) }.getOrNull()
    }

    private fun writeMeta(name: String, meta: ListMeta) {
        runCatching { metaFile(name).writeText(ListMeta.json.encodeToString(ListMeta.serializer(), meta)) }
    }

    /** Reads the `! Expires:` directive; defaults to 96h (4 days) when absent. */
    private fun parseExpiresHours(content: String): Int {
        val head = content.take(4096)
        val match = Regex("""(?im)^!\s*Expires:\s*(\d+)\s*(day|days|hour|hours|h|d)""").find(head)
            ?: return DEFAULT_EXPIRES_HOURS
        val value = match.groupValues[1].toIntOrNull() ?: return DEFAULT_EXPIRES_HOURS
        val unit = match.groupValues[2].lowercase()
        return if (unit.startsWith("h")) value else value * 24
    }

    companion object {
        const val DEFAULT_EXPIRES_HOURS = 96
        private const val WORK_NAME = "blocklist-refresh"

        fun schedulePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<BlocklistRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
