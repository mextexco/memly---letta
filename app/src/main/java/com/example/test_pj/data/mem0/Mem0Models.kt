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
    val infer: Boolean = true,
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
