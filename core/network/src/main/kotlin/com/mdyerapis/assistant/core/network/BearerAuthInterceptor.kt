package com.mdyerapis.assistant.core.network

import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val tokenProvider: () -> String?,
    private val allowedHosts: Set<String> = emptySet(),
) : Interceptor {

    constructor(tokenProvider: () -> String?) : this(tokenProvider, emptySet())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Do not attach our backend bearer token to third-party CDNs / model hosts
        val shouldAttach = allowedHosts.isEmpty() || allowedHosts.contains(host)
        val isExternalCdn = host.endsWith("googleapis.com") ||
                host.endsWith("huggingface.co") ||
                host.endsWith("github.com") ||
                host.endsWith("githubusercontent.com") ||
                host.endsWith("kaggle.com")

        if (!shouldAttach || isExternalCdn) {
            return chain.proceed(request)
        }

        val token = tokenProvider() ?: ""
        if (token.isBlank()) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        )
    }
}
