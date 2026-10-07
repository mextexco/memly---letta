package com.example.test_pj.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.data.memory.MemoryItem
import com.example.test_pj.data.memory.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MemoryListUiState {
    data object Loading : MemoryListUiState
    data class Success(val memories: List<MemoryItem>) : MemoryListUiState
    data class Error(val message: String) : MemoryListUiState
}

/**
 * 溜まった長期記憶（archival memory）を眺めて消すだけの画面。
 *
 * 一覧は 1 ページずつ読む。Letta は passage に埋め込みベクトルを含めて返し除外できないため、
 * 1 件約 40KB あり、全件取得すると 92 件で 3.7MB になってモバイルでは実用にならない。
 *
 * 絞り込みは読み込み済みの分だけを端末内で行う。意味検索は「記憶に聞く」画面でエージェントにやらせる。
 * 編集も Letta に更新 API が無く、書き換えはエージェントに任せる方針なので持たない。
 */
class MemoryListViewModel : ViewModel() {

    private val memory = MemoryRepository.instance

    private val _uiState = MutableStateFlow<MemoryListUiState>(MemoryListUiState.Loading)
    val uiState: StateFlow<MemoryListUiState> = _uiState.asStateFlow()

    /** 追加読み込み中か。ボタンの二重押しを防ぐ。 */
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /** まだ続きがあるか。最後のページが 1 ページ分に満たなければ終端とみなす。 */
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** 絞り込み語。読み込み済みの本文とタグだけを対象にする（サーバー検索はしない）。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    private val _deletingMemory = MutableStateFlow<MemoryItem?>(null)
    val deletingMemory: StateFlow<MemoryItem?> = _deletingMemory.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init { refresh() }

    /** 先頭ページを読み直す。追加読み込み済みの分は破棄される。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MemoryListUiState.Loading
            try {
                val page = memory.archivedMemories()
                _hasMore.value = page.size >= memory.pageSize
                _uiState.value = MemoryListUiState.Success(page)
            } catch (e: Exception) {
                _uiState.value = MemoryListUiState.Error(e.message ?: "読み込み失敗")
            }
        }
    }

    /** 表示済みの末尾より古い分を 1 ページ追記する。 */
    fun loadMore() {
        val current = (_uiState.value as? MemoryListUiState.Success)?.memories ?: return
        if (_loadingMore.value || !_hasMore.value || current.isEmpty()) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val page = memory.archivedMemories(before = current.last().id)
                _hasMore.value = page.size >= memory.pageSize
                _uiState.value = MemoryListUiState.Success(current + page)
            } catch (e: Exception) {
                _actionError.value = "追加読み込み失敗: ${e.message}"
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun startDelete(item: MemoryItem) { _deletingMemory.value = item }
    fun cancelDelete() { _deletingMemory.value = null }

    fun confirmDelete(id: String) {
        viewModelScope.launch {
            try {
                memory.deleteArchived(id)
                _deletingMemory.value = null
                refresh()
            } catch (e: Exception) {
                _actionError.value = "削除失敗: ${e.message}"
            }
        }
    }

    fun clearActionError() { _actionError.value = null }
}
