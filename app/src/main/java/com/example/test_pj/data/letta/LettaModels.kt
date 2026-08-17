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

/** POST /v1/agents/{id}/messages のリクエスト（mem0 からの移行でエージェントに判断させる用）。 */
@Serializable
data class SendMessageRequest(
    val messages: List<LettaMessage>,
)

@Serializable
data class LettaMessage(
    val role: String,
    val content: String,
)
