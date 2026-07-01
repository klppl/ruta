package io.github.klppl.ruta.blocking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** A snapshot of blocked-request tallies surfaced on the dashboard. */
data class BlockStats(val total: Long = 0, val today: Long = 0, val week: Long = 0)

/**
 * Counts network requests actually blocked at runtime — distinct from the number of *rules*
 * loaded ([BlocklistStatus.networkRules]). [record] is called on the WebView worker thread from
 * `shouldInterceptRequest`, so it only touches in-memory counters; the counts are flushed to
 * DataStore on a debounce to keep the hot path off disk. Per-day buckets (local time) back the
 * "today" and "this week" figures; a rolling total survives across launches.
 */
@Singleton
class BlockStatsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val totalKey = longPreferencesKey("stats_blocked_total")
    private val daysKey = stringPreferencesKey("stats_blocked_days")
    private val json = Json { ignoreUnknownKeys = true }
    private val daySerializer = MapSerializer(Long.serializer(), Long.serializer())

    private val lock = Any()
    private var total = 0L
    private val days = HashMap<Long, Long>() // localEpochDay -> blocked count

    private val _stats = MutableStateFlow(BlockStats())
    val stats: StateFlow<BlockStats> = _stats.asStateFlow()

    // replay=1 so a record() that lands before observeFlushes() starts collecting still flushes.
    private val flushSignal = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Load before collecting flushes so an early flush can't persist pre-merge counts.
        scope.launch {
            load()
            observeFlushes()
        }
    }

    /** Record one blocked request. Thread-safe; safe to call from the WebView worker thread. */
    fun record() {
        val day = epochDay()
        synchronized(lock) {
            total += 1
            days[day] = (days[day] ?: 0) + 1
            pruneOld(day)
        }
        emit()
        flushSignal.tryEmit(Unit)
    }

    private fun pruneOld(today: Long) {
        val cutoff = today - RETENTION_DAYS
        days.keys.iterator().let { it ->
            while (it.hasNext()) if (it.next() < cutoff) it.remove()
        }
    }

    private fun emit() {
        val today = epochDay()
        val weekStart = today - 6
        val snapshot = synchronized(lock) {
            BlockStats(
                total = total,
                today = days[today] ?: 0,
                week = days.entries.sumOf { if (it.key >= weekStart) it.value else 0 },
            )
        }
        _stats.value = snapshot
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeFlushes() {
        flushSignal.debounce(FLUSH_DEBOUNCE_MS).collect { persist() }
    }

    private suspend fun load() {
        val prefs = dataStore.data.first()
        val storedTotal = prefs[totalKey] ?: 0L
        val storedDays = prefs[daysKey]?.let {
            runCatching { json.decodeFromString(daySerializer, it) }.getOrNull()
        } ?: emptyMap()
        // Merge (don't overwrite): record() may already have counted blocks in-memory while
        // this initial read was in flight; those deltas must survive the load.
        synchronized(lock) {
            total += storedTotal
            for ((day, count) in storedDays) days[day] = (days[day] ?: 0) + count
            pruneOld(epochDay())
        }
        emit()
    }

    private suspend fun persist() {
        val storedTotal: Long
        val storedDays: Map<Long, Long>
        synchronized(lock) {
            storedTotal = total
            storedDays = HashMap(days)
        }
        dataStore.edit { prefs ->
            prefs[totalKey] = storedTotal
            prefs[daysKey] = json.encodeToString(daySerializer, storedDays)
        }
    }

    /** Local-time day index. Offset (incl. DST) folds wall-clock midnight into the bucket. */
    private fun epochDay(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / DAY_MS
    }

    companion object {
        private const val RETENTION_DAYS = 14L
        private const val FLUSH_DEBOUNCE_MS = 3000L
        private const val DAY_MS = 86_400_000L
    }
}
