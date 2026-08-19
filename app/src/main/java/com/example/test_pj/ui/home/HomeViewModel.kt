package com.example.test_pj.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.data.letta.MemoryAction
import com.example.test_pj.data.memory.MemoryRepository
import com.example.test_pj.data.user.UserInfo
import com.example.test_pj.data.user.UserRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Idle : HomeUiState
    /** エージェントが考えている間。数秒かかるので専用の状態にしている。 */
    data object Thinking : HomeUiState
    /** エージェントの返事と、その過程で自分で決めた記憶操作。 */
    data class Answered(val reply: String, val actions: List<MemoryAction>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val memory = MemoryRepository.instance
    private val userRepository = UserRepository(app)

    val userInfo: StateFlow<UserInfo> = userRepository.userInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserInfo())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private var voiceBaseText = ""

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

    // ── 記憶させる ────────────────────────────────
    /**
     * エージェントに話しかける。何をどう覚えるかはエージェントが決めるので、
     * memly 側は結果を受け取って伝えるだけ。
     */
    fun remember() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = HomeUiState.Thinking
            try {
                val turn = memory.remember(text)
                _inputText.value = ""
                _uiState.value = HomeUiState.Answered(turn.reply, turn.memoryActions)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "エラーが発生しました")
            }
        }
    }

    fun dismissResult() { _uiState.value = HomeUiState.Idle }

    // ── ユーザー ──────────────────────────────────
    fun onGoogleSignInSuccess(credential: GoogleIdTokenCredential) {
        viewModelScope.launch { userRepository.saveGoogleUser(credential) }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.value = HomeUiState.Error("Google サインイン失敗: $message")
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch { userRepository.updateDisplayName(name) }
    }

    fun signOut() {
        viewModelScope.launch { userRepository.signOut() }
    }
}
