package com.windwidget

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    @Test
    fun `retries on network failure`() {
        val interceptor = RetryInterceptor()
        val request = Request.Builder().url("https://example.com").build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request

        var attempts = 0
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        every { chain.proceed(any()) } answers {
            attempts++
            if (attempts <= 3) throw IOException("fail") else response
        }

        val result = interceptor.intercept(chain)
        assertEquals(4, attempts)
        assertEquals(200, result.code)
    }

    @Test
    fun `does not retry on http error`() {
        val interceptor = RetryInterceptor()
        val request = Request.Builder().url("https://example.com").build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("".toResponseBody())
            .build()

        var attempts = 0
        every { chain.proceed(any()) } answers {
            attempts++
            response
        }

        val result = interceptor.intercept(chain)
        assertEquals(1, attempts)
        assertEquals(404, result.code)
    }

    @Test
    fun `uses exponential backoff timing`() {
        val delays = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleeper = { delays.add(it) })
        val request = Request.Builder().url("https://example.com").build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request

        var attempts = 0
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        every { chain.proceed(any()) } answers {
            attempts++
            if (attempts <= 3) throw IOException("fail") else response
        }

        interceptor.intercept(chain)
        assertEquals(listOf(1000L, 2000L, 4000L), delays)
    }
}
