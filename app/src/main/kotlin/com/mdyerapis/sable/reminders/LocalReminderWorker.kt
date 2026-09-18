package com.mdyerapis.sable.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mdyerapis.sable.core.database.reminder.LocalReminderFiring
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firing: LocalReminderFiring,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(LocalReminderWork.KEY_ID) ?: return Result.failure()
        firing.fire(id)
        return Result.success()
    }
}
