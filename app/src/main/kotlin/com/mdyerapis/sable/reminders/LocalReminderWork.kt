package com.mdyerapis.sable.reminders

internal object LocalReminderWork {
    const val TAG = "sable-local-reminder"
    const val KEY_ID = "reminder_id"
    const val KEY_TEXT = "reminder_text"
    const val ACTION_DUE = "com.mdyerapis.sable.reminders.ACTION_DUE"

    fun uniqueName(id: String): String = "local-reminder-$id"
}
