package com.mdyerapis.sable.automations

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mdyerapis.sable.core.database.automation.AutomationScheduler
import com.mdyerapis.sable.core.database.automation.LocalAutomationFiring
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalAutomationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firing: LocalAutomationFiring,
    private val scheduler: AutomationScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(LocalAutomationWork.KEY_ID) ?: return Result.failure()
        val updated = firing.fire(id)
        if (updated != null && updated.enabled) {
            scheduler.schedule(updated)
        }
        return Result.success()
    }
}
