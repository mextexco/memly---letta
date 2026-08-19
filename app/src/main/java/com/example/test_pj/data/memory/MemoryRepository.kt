package com.example.test_pj.data.memory

import com.example.test_pj.BuildConfig
import com.example.test_pj.data.letta.AgentTurn
import com.example.test_pj.data.letta.CoreBlock
import com.example.test_pj.data.letta.LettaApi

/** 一覧表示用の記憶（archival memory の passage）。 */
data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: String = "",
)

/**
 * memly の記憶そのもの。
 *
 * 保存も想起もエージェントとの会話として扱い、何をどう覚えるかはエージェントに委ねる。
 * memly が直接触るのは「溜まった長期記憶を眺めて消す」ところだけ。
 */
class MemoryRepository(private val api: LettaApi) {

    /** 記憶させる。エージェントが統合・重複排除まで判断する。 */
    suspend fun remember(text: String): AgentTurn = api.sendMessage(text)

    /** 思い出させる。エージェントが自分の記憶を検索して答える。 */
    suspend fun recall(question: String): AgentTurn = api.sendMessage(question)

    /** エージェントが常に見ている記憶（human / persona など）。 */
    suspend fun coreMemory(): List<CoreBlock> = api.getCoreBlocks()

    suspend fun updateCoreMemory(label: String, value: String) = api.updateCoreBlock(label, value)

    /** 溜まった長期記憶の生データ。 */
    suspend fun archivedMemories(): List<MemoryItem> =
        api.listPassages().map { MemoryItem(id = it.id, text = it.text, createdAt = it.createdAt) }

    suspend fun deleteArchived(id: String) = api.deletePassage(id)

    companion object {
        /** HTTP クライアントを使い回すため、アプリ全体で 1 インスタンスを共有する。 */
        val instance: MemoryRepository by lazy {
            MemoryRepository(
                LettaApi(
                    baseUrl = BuildConfig.LETTA_BASE_URL,
                    agentId = BuildConfig.LETTA_AGENT_ID,
                    token = BuildConfig.LETTA_TOKEN,
                )
            )
        }
    }
}
