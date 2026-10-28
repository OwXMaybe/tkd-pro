package com.tkdpro.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.BackendError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sync soon (after a local change, or when the app comes to the foreground). */
    fun schedule(delaySeconds: Long = 5) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Background pull so changes made on the computer arrive even when the app stays closed. */
    fun schedulePeriodic(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "tkd_sync"
        const val PERIODIC_NAME = "tkd_sync_periodic"
    }
}

/** Pushes queued local changes, then pulls changes made on the owner's other devices. Failures retry with backoff. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val client: BackendClient,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.current().syncEnabled || !client.isConfigured()) return Result.success()
        val outcome = engine.push().mapCatching { engine.pull().getOrThrow() }
        outcome.onSuccess { settings.setLastSync(System.currentTimeMillis(), null) }
        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                settings.setLastSync(settings.current().lastSyncAt, e.message ?: e.javaClass.simpleName)
                when (e) {
                    is BackendError.Unauthorized, is BackendError.NotConfigured -> Result.failure()
                    else -> Result.retry()
                }
            },
        )
    }
}
