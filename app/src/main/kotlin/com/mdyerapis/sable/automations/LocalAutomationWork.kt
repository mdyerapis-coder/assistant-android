package com.mdyerapis.sable.automations

internal object LocalAutomationWork {
    const val TAG = "sable-local-automation"
    const val KEY_ID = "automation_id"
    const val ACTION_DUE = "com.mdyerapis.sable.automations.ACTION_DUE"

    fun uniqueName(id: String): String = "local-automation-$id"
}
