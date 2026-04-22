package com.arslan.shizuwall.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ScreenLockModeWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Reconcile desired state and recover the monitor service if it was killed.
        ScreenLockMonitorService.sync(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "screen_lock_mode_watchdog"

        fun sync(context: Context, enabled: Boolean) {
            val appContext = context.applicationContext
            val workManager = WorkManager.getInstance(appContext)

            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<ScreenLockModeWatchdogWorker>(
                15,
                TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
