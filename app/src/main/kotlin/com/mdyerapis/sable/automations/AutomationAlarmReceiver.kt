package com.mdyerapis.sable.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Exact-alarm wake-up that enqueues the same unique WorkManager job with
 * no delay. [LocalAutomationFiring] de-dupes if the delayed work also runs.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(LocalAutomationWork.KEY_ID) ?: return
        val work = OneTimeWorkRequestBuilder<LocalAutomationWorker>()
            .setInputData(workDataOf(LocalAutomationWork.KEY_ID to id))
            .addTag(LocalAutomationWork.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LocalAutomationWork.uniqueName(id),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }
}
