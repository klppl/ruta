package io.github.klppl.ruta.blocking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BlocklistRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BlocklistRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // force = true: check every list on the daily run regardless of its "! Expires:"
        // directive. Conditional requests (ETag / If-Modified-Since) make an unchanged
        // list a cheap 304, so this stays light on data.
        val ok = repository.refresh(force = true)
        return if (ok) Result.success() else Result.retry()
    }
}
