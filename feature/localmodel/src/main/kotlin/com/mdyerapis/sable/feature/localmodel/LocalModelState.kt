package com.mdyerapis.sable.feature.localmodel

data class LocalModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val defaultUrl: String,
    val sizeLabel: String,
    val expectedSha256: String? = null,
    val isUncensored: Boolean = false,
    val category: String = "GENERAL",
)

data class LocalModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val fileSizeBytes: Long,
    val isSelected: Boolean = false,
)

sealed interface LocalModelDownloadState {
    data object Idle : LocalModelDownloadState
    data class Downloading(val modelId: String, val progress: Float) : LocalModelDownloadState
    data class Completed(val modelId: String) : LocalModelDownloadState
    data class Error(val modelId: String, val message: String) : LocalModelDownloadState
}

sealed interface LocalModelState {
    data object NotInstalled : LocalModelState
    data class Downloading(val progress: Float) : LocalModelState
    data class Ready(val path: String, val activeModelId: String = "") : LocalModelState
    data class Error(val message: String) : LocalModelState
}
