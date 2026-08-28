package com.mdyerapis.assistant.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String = "",
    val status: ToolCallStatus = ToolCallStatus.Started,
)

enum class ToolCallStatus { Started, Progress, Finished, Error }
