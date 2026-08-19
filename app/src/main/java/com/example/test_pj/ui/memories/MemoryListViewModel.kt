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
 * 検索は「記憶に聞く」画面でエージェントにやらせるのでここには置かない。
 * 編集も Letta に更新 API が無く、書き換えはエージェントに任せる方針なので持たない。
 */
class MemoryListViewModel : ViewModel() {

    private val memory = MemoryRepository.instance

    private val _uiState = MutableStateFlow<MemoryListUiState>(MemoryListUiState.Loading)
    val uiState: StateFlow<MemoryListUiState> = _uiState.asStateFlow()

    private val _deletingMemory = MutableStateFlow<MemoryItem?>(null)
    val deletingMemory: StateFlow<MemoryItem?> = _deletingMemory.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MemoryListUiState.Loading
            try {
                _uiState.value = MemoryListUiState.Success(memory.archivedMemories())
            } catch (e: Exception) {
                _uiState.value = MemoryListUiState.Error(e.message ?: "読み込み失敗")
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
