package com.zwiedzak.data.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VisitHistoryTest {
    @Test
    fun loadsAllPagesInOrder() = runBlocking {
        val offsets = mutableListOf<String?>()
        val api = api { offset ->
            offsets.add(offset)
            if (offset == "0") {
                """{"items":[{"id":"a","visitedAt":"2026-11-16T14:30:25.123456Z"}],"limit":50,"offset":0,"hasMore":true}"""
            } else {
                """{"items":[{"id":"b","visitedAt":"2026-11-15T14:30:25Z"}],"limit":50,"offset":50,"hasMore":false}"""
            }
        }
        assertEquals(listOf("a", "b"), loadVisitPages(api, "place").map { it.id })
        assertEquals(listOf("0", "50"), offsets)
    }

    @Test(expected = IOException::class)
    fun rejectsNonAdvancingPage() = runBlocking {
        loadVisitPages(api {
            """{"items":[],"limit":50,"offset":0,"hasMore":true}"""
        }, "place")
        Unit
    }

    private fun api(body: (String?) -> String): ApiService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body(request.url.queryParameter("offset")).toResponseBody()).build()
        }.build()
        return Retrofit.Builder().baseUrl("http://localhost/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
