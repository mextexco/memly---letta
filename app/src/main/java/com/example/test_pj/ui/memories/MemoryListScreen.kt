package com.example.test_pj.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test_pj.data.mem0.Memory
import com.example.test_pj.theme.ErrorRed

@Composable
fun MemoryListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryListViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editingMemory by viewModel.editingMemory.collectAsStateWithLifecycle()
    val deletingMemory by viewModel.deletingMemory.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "記憶の記録",
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, "更新", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // 件数バッジ
        if (uiState is MemoryListUiState.Success) {
            val count = (uiState as MemoryListUiState.Success).memories.size
            Text(
                text = if (searchQuery.isBlank()) "合計 ${count}件" else "検索結果: ${count}件",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // 検索バー
        TextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            placeholder = {
                Text("記憶を検索...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            singleLine = true,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        // コンテンツ
        when (val s = uiState) {
            is MemoryListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("読み込み中...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            }

            is MemoryListUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("⚠ ${s.message}", color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
            }

            is MemoryListUiState.Success -> {
                if (s.memories.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isBlank()) "まだ記憶がありません" else "「$searchQuery」に一致する記憶がありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.memories, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                onEdit = { viewModel.startEdit(memory) },
                                onDelete = { viewModel.startDelete(memory) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 編集ダイアログ ─────────────────────────────
    editingMemory?.let { memory ->
        var editText by remember(memory.id) { mutableStateOf(memory.memory) }
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("記憶を編集", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmEdit(memory.id, editText) },
                    enabled = editText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("保存", color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEdit) {
                    Text("キャンセル", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            },
        )
    }

    // ── 削除確認ダイアログ ─────────────────────────
    deletingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("削除しますか？", fontFamily = FontFamily.Monospace, color = ErrorRed) },
            text = {
                Text(
                    "「${memory.memory.take(40)}${if (memory.memory.length > 40) "…" else ""}」\nを削除します。元に戻せません。",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete(memory.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) {
                    Text("削除", fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text("キャンセル", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            },
        )
    }

    // ── エラースナックバー代わり ───────────────────
    actionError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("エラー", color = ErrorRed, fontFamily = FontFamily.Monospace) },
            text = { Text(err, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionError) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                }
            },
        )
    }
}

@Composable
private fun MemoryCard(memory: Memory, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                text = memory.memory,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp, lineHeight = 22.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    if (memory.createdAt.isNotBlank()) {
                        Text(formatDate(memory.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    if (memory.score != null) {
                        Text("関連度: ${"%.0f".format(memory.score * 100)}%", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "編集", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "削除", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatDate(isoDate: String): String = try {
    isoDate.replace("T", " ").take(16)
} catch (_: Exception) { isoDate }
