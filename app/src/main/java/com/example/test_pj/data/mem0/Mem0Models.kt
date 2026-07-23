package com.example.test_pj.data.mem0

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryMessage(
    val role: String,
    val content: String,
)

@Serializable
data class AddMemoryRequest(
    val messages: List<MemoryMessage>,
    @SerialName("user_id") val userId: String,
    // mem0のadd APIはデフォルトinfer=true（LLMが記憶に値するか判定→短いメモは破棄され非同期でPENDINGのまま保存されない）。
    // memlyはユーザーが入力したメモをそのまま保存したいので、infer=falseで同期・生保存にする。
    val infer: Boolean = false,
)

@Serializable
data class SearchMemoryRequest(
    val query: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class UpdateMemoryRequest(
    val text: String,
)

@Serializable
data class Memory(
    val id: String = "",
    val memory: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val score: Float? = null,
)
