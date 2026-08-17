package com.example.test_pj.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.data.memory.MemoryBackends
import com.example.test_pj.data.user.UserInfo
import com.example.test_pj.data.user.UserRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Saving : HomeUiState
    data class Saved(val message: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val memories = MemoryBackends.current
    private val userRepository = UserRepository(app)

    val userInfo: StateFlow<UserInfo> = userRepository.userInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserInfo())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _memoryCount = MutableStateFlow<Int?>(null)
    val memoryCount: StateFlow<Int?> = _memoryCount.asStateFlow()

    private var voiceBaseText = ""

    init {
        // userId が確定してから（DataStore 読み込み後）カウントを取得。
        // userId 変化（サインイン・サインアウト）にも自動対応。
        viewModelScope.launch {
            userRepository.userInfo
                .map { it.mem0UserId }
                .distinctUntilChanged()
                .collect { userId ->
                    try {
                        _memoryCount.value = memories.list(userId).size
                    } catch (_: Exception) {}
                }
        }
    }

    // ── テキスト入力 ──────────────────────────────
    fun onTextChange(text: String) {
        _inputText.value = text
        if (_uiState.value is HomeUiState.Error) _uiState.value = HomeUiState.Idle
    }

    // ── 音声入力 ──────────────────────────────────
    fun onVoiceStart() { voiceBaseText = _inputText.value }

    fun onVoicePartialResult(partial: String) {
        _inputText.value = if (voiceBaseText.isBlank()) partial else "$voiceBaseText $partial"
    }

    fun onVoiceResult(text: String) {
        _inputText.value = if (voiceBaseText.isBlank()) text else "$voiceBaseText $text"
        voiceBaseText = ""
    }

    fun onVoiceCancel() {
        _inputText.value = voiceBaseText
        voiceBaseText = ""
    }

    // ── 記憶保存 ──────────────────────────────────
    fun saveMemory() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        val userId = userInfo.value.mem0UserId
        viewModelScope.launch {
            _uiState.value = HomeUiState.Saving
            try {
                val categorized = memories.add(text, userId)
                _inputText.value = ""
                // カウントを即時更新（FlowはuserIdが変化しないと再発火しないため手動で取得）
                try { _memoryCount.value = memories.list(userId).size } catch (_: Exception) {}
                _uiState.value = HomeUiState.Saved(if (categorized) "記憶しました ✓" else "記憶しました ✓（分類は未対応）")
                delay(2500)
                _uiState.value = HomeUiState.Idle
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "エラーが発生しました")
            }
        }
    }

    // ── ユーザー ──────────────────────────────────
    fun onGoogleSignInSuccess(credential: GoogleIdTokenCredential) {
        viewModelScope.launch {
            userRepository.saveGoogleUser(credential)
            // カウント更新は userId Flow の変化で自動トリガーされる
        }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.value = HomeUiState.Error("Google サインイン失敗: $message")
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch { userRepository.updateDisplayName(name) }
    }

    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
            // カウント更新は userId Flow の変化で自動トリガーされる
        }
    }
}
