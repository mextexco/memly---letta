package com.example.test_pj.data.memory

import com.example.test_pj.data.letta.LettaApi

/**
 * セルフホスト Letta を記憶バックエンドとして使うアダプター。
 *
 * 記憶は archival memory（passages）に保存する。エージェント自身に取捨選択させる
 * メッセージ経由の書き込みは id が返らず一覧・削除と紐付かないため、通常の保存経路では使わない
 * （mem0 からの一括移行のみ `scripts/migrate_mem0_to_letta.py` で利用する）。
 *
 * userId は使用しない。1 エージェント = 1 ユーザーの記憶領域として運用する前提で、
 * ユーザーの出し分けは接続先エージェント（LETTA_AGENT_ID）の切り替えで行う。
 */
class LettaBackend(private val api: LettaApi) : MemoryBackend {

    override val name = "letta"

    /** Letta の archival memory には更新 API が無い（insert / delete のみ）。 */
    override val supportsEdit = false

    override suspend fun list(userId: String): List<MemoryItem> =
        api.listPassages().map { MemoryItem(id = it.id, text = it.text, createdAt = it.createdAt) }

    override suspend fun search(query: String, userId: String): List<MemoryItem> =
        api.searchPassages(query).map { MemoryItem(id = it.id, text = it.content, createdAt = it.timestamp) }

    /** passage として保存され、サーバー側で埋め込みまで生成される。分類の概念は無いので常に true。 */
    override suspend fun add(text: String, userId: String): Boolean {
        api.insertPassage(text)
        return true
    }

    override suspend fun update(id: String, text: String) =
        throw UnsupportedOperationException("Letta の archival memory は更新に対応していない")

    override suspend fun delete(id: String) = api.deletePassage(id)
}
