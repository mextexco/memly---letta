package com.example.test_pj.data.mem0

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class Mem0Api(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.mem0.ai"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Token $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    /** 記憶を追加 */
    suspend fun addMemory(content: String, userId: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            AddMemoryRequest(messages = listOf(MemoryMessage("user", content)), userId = userId)
        ).toRequestBody(jsonMediaType)

        Request.Builder().url("$baseUrl/v1/memories/").post(body).build()
            .execute { if (!it.isSuccessful) throw IOException("API error ${it.code}: ${it.body?.string()}") }
    }

    /** 全記憶を取得 */
    suspend fun getMemories(userId: String): List<Memory> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/memories/?user_id=$userId")
            .get().build()

        request.execute { response ->
            if (!response.isSuccessful) throw IOException("API error ${response.code}")
            json.decodeFromString<List<Memory>>(response.body?.string() ?: "[]")
        }
    }

    /** 記憶を検索 */
    suspend fun searchMemories(query: String, userId: String): List<Memory> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            SearchMemoryRequest(query = query, userId = userId)
        ).toRequestBody(jsonMediaType)

        Request.Builder().url("$baseUrl/v1/memories/search/").post(body).build()
            .execute { response ->
                if (!response.isSuccessful) throw IOException("API error ${response.code}")
                json.decodeFromString<List<Memory>>(response.body?.string() ?: "[]")
            }
    }

    /** 記憶を編集 */
    suspend fun updateMemory(id: String, content: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(UpdateMemoryRequest(text = content)).toRequestBody(jsonMediaType)
        Request.Builder().url("$baseUrl/v1/memories/$id/")
            .put(body).build()
            .execute { if (!it.isSuccessful) throw IOException("API error ${it.code}: ${it.body?.string()}") }
    }

    /** 記憶を削除 */
    suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) {
        Request.Builder().url("$baseUrl/v1/memories/$id/")
            .delete().build()
            .execute { if (!it.isSuccessful) throw IOException("API error ${it.code}: ${it.body?.string()}") }
    }

    private fun <T> Request.execute(block: (okhttp3.Response) -> T): T =
        client.newCall(this).execute().use(block)
}
