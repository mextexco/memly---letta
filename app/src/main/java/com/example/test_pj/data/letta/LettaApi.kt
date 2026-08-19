package com.example.test_pj.data.letta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * memly は記憶を自分で操作せず、エージェントに話しかけて任せる方針を取る。
 * 中心になるのは [sendMessage]（保存も想起もこれ）で、passage の直接操作は
 * 一覧表示と削除のためだけに使う。
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

    // エージェントが LLM を呼ぶ経路は数十秒かかることがあるため、既定より長めに取る。
    private val client = OkHttpClient.Builder()
        .readTimeout(180, TimeUnit.SECONDS)
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

    // ── エージェントとの対話 ────────────────────────────

    /**
     * エージェントにメッセージを送り、返事と記憶操作を受け取る。
     *
     * 何を覚えるか・既存の記憶をどう書き換えるかはエージェントが判断する。memly 側からは
     * 保存先（core memory か archival memory か）を指定しない。
     */
    suspend fun sendMessage(content: String): AgentTurn = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SendMessageRequest(listOf(LettaMessage("user", content))))
            .toRequestBody(jsonMediaType)

        Request.Builder().url(url("/v1/agents/$agentId/messages")).post(body).build()
            .execute { response ->
                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) throw IOException("Letta API error ${response.code}: $raw")
                parseTurn(raw)
            }
    }

    /**
     * 返ってくる messages は種類ごとに形が違うため、型付けせず必要な部分だけ拾う。
     * assistant_message の content は文字列のこともパーツ配列のこともある。
     */
    private fun parseTurn(raw: String): AgentTurn {
        val root = json.parseToJsonElement(raw).jsonObject
        val messages = (root["messages"] as? JsonArray).orEmpty()

        val reply = StringBuilder()
        val actions = mutableListOf<MemoryAction>()

        messages.forEach { element ->
            val m = element as? JsonObject ?: return@forEach
            when (m["message_type"]?.jsonPrimitive?.contentOrNull) {
                "assistant_message" -> flattenContent(m["content"])?.let { if (it.isNotBlank()) reply.appendLine(it) }
                "tool_call_message" -> {
                    val name = (m["tool_call"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                    if (name != null) actions += MemoryAction(tool = name, result = "")
                }
                "tool_return_message" -> {
                    val name = m["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val result = m["tool_return"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    // 直前の同名 tool_call に結果を紐付ける
                    val idx = actions.indexOfLast { it.tool == name && it.result.isEmpty() }
                    if (idx >= 0) actions[idx] = actions[idx].copy(result = result)
                }
            }
        }

        return AgentTurn(reply = reply.toString().trim(), memoryActions = actions)
    }

    /** content は文字列か、`{type:"text", text:...}` の配列で返る。 */
    private fun flattenContent(element: kotlinx.serialization.json.JsonElement?): String? = when (element) {
        null -> null
        is JsonArray -> element.mapNotNull {
            (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        }.joinToString("\n").ifBlank { null }
        else -> element.jsonPrimitive.contentOrNull
    }

    // ── core memory（エージェントが常に見ている記憶） ──────

    /** `human` / `persona` などのブロックを取得。 */
    suspend fun getCoreBlocks(): List<CoreBlock> = withContext(Dispatchers.IO) {
        Request.Builder().url(url("/v1/agents/$agentId/core-memory/blocks")).get().build()
            .execute { response ->
                if (!response.isSuccessful) throw IOException("Letta API error ${response.code}: ${response.body?.string()}")
                json.decodeFromString<List<CoreBlock>>(response.body?.string() ?: "[]")
            }
    }

    /** ブロックを直接書き換える。エージェントに任せず人間が直す場合のみ使う。 */
    suspend fun updateCoreBlock(label: String, value: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(UpdateCoreBlock(value)).toRequestBody(jsonMediaType)
        Request.Builder().url(url("/v1/agents/$agentId/core-memory/blocks/$label")).patch(body).build()
            .execute { if (!it.isSuccessful) throw IOException("Letta API error ${it.code}: ${it.body?.string()}") }
    }

    // ── archival memory（長期記憶の生データ） ─────────────

    /**
     * archival memory を新しい順に取得する。
     *
     * このエンドポイントは 1 件あたり 1536 次元の埋め込みベクトルも返し、除外パラメータが無い。
     * 実測で **1 件およそ 40KB** あるため、全件取得すると 92 件で 3.7MB に達しモバイルでは実用にならない。
     * 必ず [limit] で区切り、続きは前ページ末尾の id を [before] に渡して取得する。
     *
     * @param before このIDより古いものを返す。降順のページングはこれで行う（`after` は降順では機能しない）。
     */
    suspend fun listPassages(limit: Int = PAGE_SIZE, before: String? = null): List<Passage> = withContext(Dispatchers.IO) {
        val url = url("/v1/agents/$agentId/archival-memory").newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("ascending", "false")
            .apply { if (before != null) addQueryParameter("before", before) }
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

    /** archival memory を削除。Letta には更新 API が無いため、書き換えはエージェントに任せる。 */
    suspend fun deletePassage(passageId: String) = withContext(Dispatchers.IO) {
        Request.Builder().url(url("/v1/agents/$agentId/archival-memory/$passageId")).delete().build()
            .execute { if (!it.isSuccessful) throw IOException("Letta API error ${it.code}: ${it.body?.string()}") }
    }

    private fun <T> Request.execute(block: (okhttp3.Response) -> T): T =
        client.newCall(this).execute().use(block)

    companion object {
        /** 1 ページの件数。1 件約 40KB なので増やすと転送量が線形に増える。 */
        const val PAGE_SIZE = 20
    }
}
