package com.checklistboteco.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.checklistboteco.data.database.AndroidDatabaseDriverFactory
import com.checklistboteco.data.remote.SyncApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.database.ChecklistDatabase

class ChecklistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val apiClient = SyncApiClient.fromEnvironment() ?: return Result.success()
        val database = ChecklistDatabase(AndroidDatabaseDriverFactory(applicationContext).createDriver())
        val repository = ChecklistRepository(database)
        val coordinator = SyncCoordinator(
            repository = repository,
            syncApiClient = apiClient,
            scheduler = NoOpSyncScheduler
        )

        return runCatching {
            coordinator.syncOnce()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
