package com.mdyerapis.sable.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Exact-alarm wake-up that enqueues the same unique WorkManager job with
 * no delay. [LocalReminderFiring] de-dupes if the delayed work also runs.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(LocalReminderWork.KEY_ID) ?: return
        val work = OneTimeWorkRequestBuilder<LocalReminderWorker>()
            .setInputData(workDataOf(LocalReminderWork.KEY_ID to id))
            .addTag(LocalReminderWork.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LocalReminderWork.uniqueName(id),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }
}
