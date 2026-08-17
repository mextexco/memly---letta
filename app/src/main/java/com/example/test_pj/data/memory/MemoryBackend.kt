package com.example.test_pj.data.memory

/** バックエンド非依存の記憶。mem0 の Memory / Letta の Passage の共通形。 */
data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: String = "",
    /** 検索結果の関連度。一覧取得時や、スコアを返さないバックエンドでは null。 */
    val score: Float? = null,
)

/**
 * 記憶バックエンドの共通インターフェース。
 *
 * mem0（クラウド）と Letta（セルフホスト）を差し替えられるようにする。
 * 両者で機能差があるものは [supportsEdit] のような capability で表明し、
 * UI 側が対応していない操作を出さないようにする。
 */
interface MemoryBackend {

    /** 表示・ログ用のバックエンド名。 */
    val name: String

    /**
     * 保存済みの記憶を id 指定で書き換えられるか。
     * Letta の archival memory は insert / delete しか無く更新 API が存在しないため false。
     */
    val supportsEdit: Boolean

    /** 全件取得（新しい順）。 */
    suspend fun list(userId: String): List<MemoryItem>

    /** セマンティック検索。 */
    suspend fun search(query: String, userId: String): List<MemoryItem>

    /**
     * 記憶を追加する。
     * @return バックエンドの AI 層による分類まで完了したか。false なら生テキストとして保存されている。
     */
    suspend fun add(text: String, userId: String): Boolean

    /** 記憶を書き換える。[supportsEdit] が false のバックエンドでは呼んではいけない。 */
    suspend fun update(id: String, text: String)

    /** 記憶を削除する。 */
    suspend fun delete(id: String)
}
