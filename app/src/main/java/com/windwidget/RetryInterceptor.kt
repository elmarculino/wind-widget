package com.windwidget

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val backoffDelaysMs: List<Long> = listOf(1000L, 2000L, 4000L),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= maxRetries) {
                    throw e
                }
                val delay = backoffDelaysMs.getOrNull(attempt) ?: backoffDelaysMs.lastOrNull() ?: 0L
                if (delay > 0) {
                    sleeper(delay)
                }
                attempt++
            }
        }
    }
}
