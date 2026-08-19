package com.example.test_pj.data.letta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /v1/agents/{id}/archival-memory のリクエスト。 */
@Serializable
data class CreateArchivalMemory(
    val text: String,
    val tags: List<String>? = null,
)

/**
 * GET /v1/agents/{id}/archival-memory の要素。
 *
 * 注意: このエンドポイントは埋め込みベクトル（1536次元）も含めて返す。除外パラメータが無いため
 * 転送量・パース負荷が大きく、[LettaApi.listPassages] では limit を必ず指定する。
 * `embedding` は宣言しないことで ignoreUnknownKeys により読み飛ばす。
 */
@Serializable
data class Passage(
    val id: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/** GET /v1/agents/{id}/archival-memory/search の結果要素。一覧とはフィールド名が異なる。 */
@Serializable
data class SearchedPassage(
    val id: String = "",
    val content: String = "",
    val timestamp: String = "",
)

@Serializable
data class ArchivalSearchResponse(
    val results: List<SearchedPassage> = emptyList(),
    val count: Int = 0,
)

/** POST /v1/agents/{id}/messages のリクエスト。 */
@Serializable
data class SendMessageRequest(
    val messages: List<LettaMessage>,
)

@Serializable
data class LettaMessage(
    val role: String,
    val content: String,
)

/**
 * core memory のブロック。`human`（ユーザー像）や `persona`（エージェント自身の性格）など。
 * ラベルが安定した識別子なので、passage と違い id を気にせず読み書きできる。
 */
@Serializable
data class CoreBlock(
    val label: String = "",
    val value: String = "",
    val limit: Int = 0,
    val description: String? = null,
)

/** core memory ブロックの更新（PATCH .../core-memory/blocks/{label}）。 */
@Serializable
data class UpdateCoreBlock(
    val value: String,
)

// ── エージェントとの1往復 ───────────────────────────────

/**
 * エージェントが記憶に対して実際に行った操作。
 * memly が命令したのではなく、エージェントが自分で選んだツール呼び出しを表す。
 */
data class MemoryAction(
    val tool: String,
    val result: String,
) {
    /** 画面表示用の日本語ラベル。 */
    val label: String
        get() = when (tool) {
            "memory_replace" -> "記憶を書き換えた"
            "memory_insert" -> "記憶に書き足した"
            "archival_memory_insert" -> "長期記憶に保存した"
            "archival_memory_search" -> "長期記憶を検索した"
            "conversation_search" -> "会話履歴を検索した"
            else -> tool
        }
}

/**
 * POST /messages の結果。エージェントの返事と、その過程で行った記憶操作。
 *
 * mem0 には無かった情報で、「保存した」ではなく「エージェントが何をどう判断したか」を
 * そのまま画面に出せる。
 */
data class AgentTurn(
    val reply: String,
    val memoryActions: List<MemoryAction> = emptyList(),
)
