package com.example.test_pj.ui.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.data.letta.CoreBlock
import com.example.test_pj.data.memory.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CoreMemoryUiState {
    data object Loading : CoreMemoryUiState
    data class Success(val blocks: List<CoreBlock>) : CoreMemoryUiState
    data class Error(val message: String) : CoreMemoryUiState
}

/**
 * エージェントが常に見ている記憶（core memory）を表示する。
 *
 * `human` はエージェントが持つユーザー像、`persona` はエージェント自身の性格。
 * 会話のたびにエージェント自身が書き換えていく部分で、memly から見えなかったもの。
 */
class CoreMemoryViewModel : ViewModel() {

    private val memory = MemoryRepository.instance

    private val _uiState = MutableStateFlow<CoreMemoryUiState>(CoreMemoryUiState.Loading)
    val uiState: StateFlow<CoreMemoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CoreMemoryUiState.Loading
            try {
                _uiState.value = CoreMemoryUiState.Success(memory.coreMemory())
            } catch (e: Exception) {
                _uiState.value = CoreMemoryUiState.Error(e.message ?: "読み込み失敗")
            }
        }
    }
}
