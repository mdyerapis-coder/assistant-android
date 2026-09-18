package com.mdyerapis.sable.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mdyerapis.sable.core.database.automation.AutomationScheduler
import com.mdyerapis.sable.core.database.automation.LocalAutomation
import com.mdyerapis.sable.core.model.LocalAutomationSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerAutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationScheduler {
    override fun schedule(automation: LocalAutomation) {
        val schedule = LocalAutomationSchedule.fromStored(
            kind = automation.kind,
            intervalMillis = automation.intervalMillis,
            hour = automation.hour,
            minute = automation.minute,
            daysOfWeek = automation.daysOfWeek,
            expression = automation.expression,
        )
        val now = System.currentTimeMillis()
        val next = schedule.nextFireMillis(now, ZoneId.systemDefault(), automation.lastFiredAtMillis)
        val delay = (next - now).coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<LocalAutomationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(LocalAutomationWork.KEY_ID to automation.id))
            .addTag(LocalAutomationWork.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LocalAutomationWork.uniqueName(automation.id),
            ExistingWorkPolicy.REPLACE,
            work,
        )
        scheduleExactAlarm(automation.id, next)
    }

    override fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LocalAutomationWork.uniqueName(id))
        cancelExactAlarm(id)
    }

    private fun scheduleExactAlarm(id: String, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                atMillis,
                exactPendingIntent(id),
            )
        } catch (_: SecurityException) {
            // Approximate WorkManager delay remains scheduled.
        }
    }

    private fun cancelExactAlarm(id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(exactPendingIntent(id))
    }

    private fun exactPendingIntent(id: String): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = LocalAutomationWork.ACTION_DUE
            putExtra(LocalAutomationWork.KEY_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
