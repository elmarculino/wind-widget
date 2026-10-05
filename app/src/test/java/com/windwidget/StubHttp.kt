package com.windwidget

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** OkHttp client that never touches the network: [answer] gives (code, body) for each request. */
internal fun stubHttp(
    seen: MutableList<Request>? = null,
    answer: (Request) -> Pair<Int, String>
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request()
        seen?.let { synchronized(it) { it.add(request) } }
        val (code, body) = answer(request)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("stub")
            .body(body.toResponseBody())
            .build()
    }
    .build()
