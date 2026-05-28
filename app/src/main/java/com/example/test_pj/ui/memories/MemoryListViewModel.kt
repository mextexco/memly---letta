package com.example.test_pj.ui.memories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.BuildConfig
import com.example.test_pj.data.mem0.Mem0Api
import com.example.test_pj.data.mem0.Memory
import com.example.test_pj.data.user.UserRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface MemoryListUiState {
    data object Loading : MemoryListUiState
    data class Success(val memories: List<Memory>) : MemoryListUiState
    data class Error(val message: String) : MemoryListUiState
}

@OptIn(FlowPreview::class)
class MemoryListViewModel(app: Application) : AndroidViewModel(app) {

    private val mem0Api = Mem0Api(BuildConfig.MEM0_API_KEY)
    private val userRepository = UserRepository(app)

    private val _uiState = MutableStateFlow<MemoryListUiState>(MemoryListUiState.Loading)
    val uiState: StateFlow<MemoryListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 編集・削除ダイアログ制御
    private val _editingMemory = MutableStateFlow<Memory?>(null)
    val editingMemory: StateFlow<Memory?> = _editingMemory.asStateFlow()

    private val _deletingMemory = MutableStateFlow<Memory?>(null)
    val deletingMemory: StateFlow<Memory?> = _deletingMemory.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        loadMemories()
        // drop(1) で初期値（空文字）をスキップし、ユーザー入力のみに反応する
        viewModelScope.launch {
            _searchQuery.drop(1).debounce(500).collectLatest { query ->
                if (query.isBlank()) loadMemories() else searchMemories(query)
            }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun refresh() {
        if (_searchQuery.value.isBlank()) loadMemories() else searchMemories(_searchQuery.value)
    }

    // ── 編集 ───────────────────────────────────────
    fun startEdit(memory: Memory) { _editingMemory.value = memory }
    fun cancelEdit() { _editingMemory.value = null }

    fun confirmEdit(id: String, newText: String) {
        viewModelScope.launch {
            try {
                mem0Api.updateMemory(id, newText)
                _editingMemory.value = null
                refresh()
            } catch (e: Exception) {
                _actionError.value = "編集失敗: ${e.message}"
            }
        }
    }

    // ── 削除 ───────────────────────────────────────
    fun startDelete(memory: Memory) { _deletingMemory.value = memory }
    fun cancelDelete() { _deletingMemory.value = null }

    fun confirmDelete(id: String) {
        viewModelScope.launch {
            try {
                mem0Api.deleteMemory(id)
                _deletingMemory.value = null
                refresh()
            } catch (e: Exception) {
                _actionError.value = "削除失敗: ${e.message}"
            }
        }
    }

    fun clearActionError() { _actionError.value = null }

    // ── プライベート ───────────────────────────────
    private fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = MemoryListUiState.Loading
            try {
                val userId = userRepository.userInfo.first().mem0UserId
                _uiState.value = MemoryListUiState.Success(mem0Api.getMemories(userId))
            } catch (e: Exception) {
                _uiState.value = MemoryListUiState.Error(e.message ?: "読み込み失敗")
            }
        }
    }

    private fun searchMemories(query: String) {
        viewModelScope.launch {
            _uiState.value = MemoryListUiState.Loading
            try {
                val userId = userRepository.userInfo.first().mem0UserId
                _uiState.value = MemoryListUiState.Success(mem0Api.searchMemories(query, userId))
            } catch (e: Exception) {
                _uiState.value = MemoryListUiState.Error(e.message ?: "検索失敗")
            }
        }
    }
}
