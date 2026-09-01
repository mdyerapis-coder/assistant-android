package com.mdyerapis.sable.core.network

sealed class AppError : Exception() {
    data class Retryable(override val message: String?) : AppError()
    data class AuthExpired(override val message: String?) : AppError()
    data class Fatal(override val message: String?) : AppError()
    data class ToolExecutionFailed(override val message: String?) : AppError()
}