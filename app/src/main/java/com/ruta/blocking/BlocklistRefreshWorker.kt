package com.ruta.blocking

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
        val ok = repository.refresh(force = false)
        return if (ok) Result.success() else Result.retry()
    }
}
