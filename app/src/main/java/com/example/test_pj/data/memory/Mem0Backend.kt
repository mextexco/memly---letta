package com.example.test_pj.data.memory

import com.example.test_pj.data.mem0.Mem0Api

/**
 * mem0 クラウドを記憶バックエンドとして使うアダプター。
 *
 * [Mem0Api] 側には infer フォールバック等の実運用で詰まった対処が入っているため、
 * ここでは移譲だけ行い、ロジックは持たせない。
 */
class Mem0Backend(private val api: Mem0Api) : MemoryBackend {

    override val name = "mem0"
    override val supportsEdit = true

    override suspend fun list(userId: String): List<MemoryItem> =
        api.getMemories(userId).map { it.toItem() }

    override suspend fun search(query: String, userId: String): List<MemoryItem> =
        api.searchMemories(query, userId).map { it.toItem() }

    override suspend fun add(text: String, userId: String): Boolean =
        api.addMemoryReliable(text, userId)

    override suspend fun update(id: String, text: String) = api.updateMemory(id, text)

    override suspend fun delete(id: String) = api.deleteMemory(id)

    private fun com.example.test_pj.data.mem0.Memory.toItem() =
        MemoryItem(id = id, text = memory, createdAt = createdAt, score = score)
}
