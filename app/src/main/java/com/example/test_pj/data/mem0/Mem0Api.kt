package com.example.test_pj.data.mem0

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // encodeDefaults=trueが無いと、infer=false(デフォルト値)がJSONから省略され、
    // mem0側のデフォルト(infer=true)にフォールバックしてメモが非同期破棄される。
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
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

    /** 記憶を追加。infer=trueはAIによるカテゴリ分類ありだが、mem0側の非同期処理が
     *  まれにPENDINGのまま完了せず記憶が失われることがある（addMemoryReliable参照）。 */
    suspend fun addMemory(content: String, userId: String, infer: Boolean = true) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            AddMemoryRequest(messages = listOf(MemoryMessage("user", content)), userId = userId, infer = infer)
        ).toRequestBody(jsonMediaType)

        Request.Builder().url("$baseUrl/v1/memories/").post(body).build()
            .execute { if (!it.isSuccessful) throw IOException("API error ${it.code}: ${it.body?.string()}") }
    }

    /** infer=trueで保存を試み、数秒待って反映を確認。反映されていなければinfer=falseで
     *  生保存し直し、確実に記憶が残るようにする（カテゴリは付かない）。
     *  戻り値はカテゴリ分類ありで保存できたか。 */
    suspend fun addMemoryReliable(content: String, userId: String): Boolean {
        addMemory(content, userId, infer = true)
        delay(4000)
        val landed = try {
            getMemories(userId).any { it.memory == content }
        } catch (_: Exception) {
            false
        }
        if (!landed) {
            addMemory(content, userId, infer = false)
        }
        return landed
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
