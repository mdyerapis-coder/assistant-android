package com.mdyerapis.assistant.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
}