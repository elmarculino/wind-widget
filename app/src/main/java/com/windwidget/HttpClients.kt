package com.windwidget

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One OkHttp client (and connection pool) shared by every fetcher. */
object HttpClients {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
