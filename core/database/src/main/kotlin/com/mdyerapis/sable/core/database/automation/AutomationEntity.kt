package com.mdyerapis.sable.core.database.automation

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val expression: String,
    val actionText: String,
    val kind: String,
    val intervalMillis: Long?,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String?,
    val createdAt: Long,
    val lastFiredAt: Long? = null,
    val enabled: Boolean,
)

data class LocalAutomation(
    val id: String,
    val name: String,
    val expression: String,
    val actionText: String,
    val kind: String,
    val intervalMillis: Long?,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String?,
    val createdAtMillis: Long,
    val lastFiredAtMillis: Long? = null,
    val enabled: Boolean,
)

fun AutomationEntity.toModel(): LocalAutomation = LocalAutomation(
    id = id,
    name = name,
    expression = expression,
    actionText = actionText,
    kind = kind,
    intervalMillis = intervalMillis,
    hour = hour,
    minute = minute,
    daysOfWeek = daysOfWeek,
    createdAtMillis = createdAt,
    lastFiredAtMillis = lastFiredAt,
    enabled = enabled,
)
