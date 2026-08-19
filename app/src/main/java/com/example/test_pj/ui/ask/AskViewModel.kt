package com.example.test_pj.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_pj.data.letta.MemoryAction
import com.example.test_pj.data.memory.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 会話の1行。エージェントとのやり取りをそのまま並べる。 */
data class ChatLine(
    val fromUser: Boolean,
    val text: String,
    val actions: List<MemoryAction> = emptyList(),
)

/**
 * 「記憶に聞く」画面。
 *
 * 検索窓でベクトル検索を叩くのではなく、エージェントに質問して自分の記憶
 * （core memory / archival memory / 会話履歴）を調べさせて答えてもらう。
 */
class AskViewModel : ViewModel() {

    private val memory = MemoryRepository.instance

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    fun onQuestionChange(text: String) { _question.value = text }

    fun ask() {
        val q = _question.value.trim()
        if (q.isEmpty() || _thinking.value) return
        _question.value = ""
        _lines.value = _lines.value + ChatLine(fromUser = true, text = q)
        viewModelScope.launch {
            _thinking.value = true
            try {
                val turn = memory.recall(q)
                _lines.value = _lines.value + ChatLine(false, turn.reply, turn.memoryActions)
            } catch (e: Exception) {
                _lines.value = _lines.value + ChatLine(false, "⚠ ${e.message ?: "エラー"}")
            } finally {
                _thinking.value = false
            }
        }
    }
}
