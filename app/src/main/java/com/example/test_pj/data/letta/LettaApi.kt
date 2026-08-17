package com.example.test_pj.data.letta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * セルフホスト Letta サーバーの REST クライアント。
 *
 * 記憶は「エージェントの archival memory（passages）」として保存する。
 * passages は id が安定していて一覧・検索・削除ができるため、memly の記憶一覧画面と対応が取れる。
 *
 * @param baseUrl 例 `http://100.x.x.x:8283`（Tailscale 経由）
 * @param agentId 記憶を持たせるエージェント。1エージェント = 1ユーザーの記憶領域として扱う。
 * @param token Letta Cloud 利用時のみ必要。セルフホストでは空でよい。
 */
class LettaApi(
    private val baseUrl: String,
    private val agentId: String,
    private val token: String = "",
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val jsonMediaType = "application/json".toMediaType()

    // エージェントが LLM を呼ぶ経路（sendMessage）は数十秒かかるため、既定より長めに取る。
    private val client = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }
                .build()
            chain.proceed(req)
        }
        .build()

    /**
     * 設定漏れはそのままだと `/v1/agents//...` への 404 になり原因が分からないため、
     * ここで読めるメッセージにして落とす。IOException にしておくと ViewModel の
     * catch に拾われ、クラッシュではなく画面上のエラー表示になる。
     */
    private fun url(path: String): okhttp3.HttpUrl {
        if (agentId.isBlank()) throw IOException("LETTA_AGENT_ID が未設定（local.properties に設定する）")
        if (baseUrl.isBlank()) throw IOException("LETTA_BASE_URL が未設定（local.properties に設定する）")
        return "${baseUrl.trimEnd('/')}$path".toHttpUrl()
    }

    /** archival memory を新しい順に取得。埋め込みも返ってくるため limit 必須。 */
    suspend fun listPassages(limit: Int = 100): List<Passage> = withContext(Dispatchers.IO) {
        val url = url("/v1/agents/$agentId/archival-memory").newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("ascending", "false")
            .build()

        Request.Builder().url(url).get().build().execute { response ->
            if (!response.isSuccessful) throw IOException("Letta API error ${response.code}: ${response.body?.string()}")
            json.decodeFromString<List<Passage>>(response.body?.string() ?: "[]")
        }
    }

    /** archival memory をセマンティック検索。埋め込みは含まれない軽量レスポンス。 */
    suspend fun searchPassages(query: String, topK: Int = 50): List<SearchedPassage> = withContext(Dispatchers.IO) {
        val url = url("/v1/agents/$agentId/archival-memory/search").newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("top_k", topK.toString())
            .build()

        Request.Builder().url(url).get().build().execute { response ->
            if (!response.isSuccessful) throw IOException("Letta API error ${response.code}: ${response.body?.string()}")
            json.decodeFromString<ArchivalSearchResponse>(response.body?.string() ?: "{}").results
        }
    }

    /** archival memory に追記。埋め込み生成が走るため OpenAI キーがサーバー側に必要。 */
    suspend fun insertPassage(text: String, tags: List<String>? = null) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CreateArchivalMemory(text = text, tags = tags))
            .toRequestBody(jsonMediaType)

        Request.Builder().url(url("/v1/agents/$agentId/archival-memory")).post(body).build()
            .execute { if (!it.isSuccessful) throw IOException("Letta API error ${it.code}: ${it.body?.string()}") }
    }

    /** archival memory を削除。Letta には更新 API が無いため、書き換えは呼び出し側で扱う。 */
    suspend fun deletePassage(passageId: String) = withContext(Dispatchers.IO) {
        Request.Builder().url(url("/v1/agents/$agentId/archival-memory/$passageId")).delete().build()
            .execute { if (!it.isSuccessful) throw IOException("Letta API error ${it.code}: ${it.body?.string()}") }
    }

    /**
     * エージェントに通常メッセージとして送り、記憶に残すかどうかを本人に判断させる。
     * passages に直接書かないので id が返らず一覧と紐付かない。mem0 からの移行用。
     */
    suspend fun sendMessage(content: String): String = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SendMessageRequest(listOf(LettaMessage("user", content))))
            .toRequestBody(jsonMediaType)

        Request.Builder().url(url("/v1/agents/$agentId/messages")).post(body).build()
            .execute { response ->
                if (!response.isSuccessful) throw IOException("Letta API error ${response.code}: ${response.body?.string()}")
                response.body?.string() ?: ""
            }
    }

    private fun <T> Request.execute(block: (okhttp3.Response) -> T): T =
        client.newCall(this).execute().use(block)
}
