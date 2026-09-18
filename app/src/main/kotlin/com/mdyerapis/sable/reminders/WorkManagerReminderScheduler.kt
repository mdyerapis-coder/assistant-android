package com.mdyerapis.sable.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mdyerapis.sable.core.database.reminder.LocalReminder
import com.mdyerapis.sable.core.database.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {
    override fun schedule(reminder: LocalReminder) {
        val delay = (reminder.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<LocalReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    LocalReminderWork.KEY_ID to reminder.id,
                    LocalReminderWork.KEY_TEXT to reminder.text,
                ),
            )
            .addTag(LocalReminderWork.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LocalReminderWork.uniqueName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            work,
        )
        scheduleExactAlarm(reminder)
    }

    override fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LocalReminderWork.uniqueName(id))
        cancelExactAlarm(id)
    }

    private fun scheduleExactAlarm(reminder: LocalReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.dueAtMillis,
                exactPendingIntent(reminder.id),
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
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = LocalReminderWork.ACTION_DUE
            putExtra(LocalReminderWork.KEY_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
